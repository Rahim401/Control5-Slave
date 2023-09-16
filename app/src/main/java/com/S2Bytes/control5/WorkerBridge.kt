package com.S2Bytes.control5

import android.net.rtp.RtpStream
import android.os.Build
import android.os.Handler
import com.S2Bytes.control5.Master.Companion.getMaster
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.experimental.or
import kotlin.system.measureNanoTime

enum class WbState{
    Idle,
    StartingListen,
    Listening,
    JoiningWork,
    Working,
    LeavingWork,
    StopingListen
}
interface TaskHandler{
    fun handleTask(data: ByteArray)
//    fun handleExTask(dStm:DataInputStream)
}
interface StateCallback{
    fun onStateChanged(state:WbState)
    fun onJoinedWork(to:Master)
    fun onLeftWork()
}


const val MainPort = 32654
const val DataPort = MainPort + 1

const val Inter = 1000L
const val InterBy4 = Inter / 4
const val LCInter = 100

const val InitPkSize = 64
const val CtrlPkSize = 6
const val BufSize = CtrlPkSize * 20

const val ScanRequestTaskId = 0.toShort()
const val ConnectRequestTaskId = 63.toShort()

const val ConnectionTaskId = 64.toShort()
const val BeatTaskId = 65.toShort()
//const val ExtendedTaskId = 66.toShort()
const val DisconnectTaskId = 255.toShort()

object WorkerBridge {
    private var mainDSkLane:DatagramSocket? = null
    private val recvBuf = ByteBuffer.allocate(BufSize)
    private val sndBuf = ByteBuffer.allocate(BufSize)
    private val recvPk = DatagramPacket(recvBuf.array(), InitPkSize)
    private val sndPk = DatagramPacket(sndBuf.array(), InitPkSize)

    private var workingFor:Master? = null
    private var currentState = WbState.Idle
        set(value) {
            if (field==value)
                return
            field = value
            stateCallbacks.forEach {
                it.second.post {
                    it.first.onStateChanged(
                        field
                    )
                }
            }
        }

    private var senderThread:Thread? = null

    private val stateCallbacks:ArrayList<Pair<StateCallback,Handler>> = ArrayList()
    fun registerStateCallback(callback:StateCallback, handler:Handler){
        stateCallbacks.add(
            Pair(
                callback, handler
            )
        )
    }
    fun unregisterStateCallback(callback:StateCallback, handler:Handler){
        stateCallbacks.remove(
            Pair(
                callback, handler
            )
        )
    }


    fun startListening(taskManager:TaskHandler){
        if(currentState!=WbState.Idle)
            return

        currentState = WbState.StartingListen
        try {
            mainDSkLane = DatagramSocket(MainPort)
            mainDSkLane!!.soTimeout = LCInter

            currentState = WbState.Listening
            println("Started Listening")
            var lastScanPackId: Byte = 0
            while (currentState == WbState.Listening) {
                try { mainDSkLane!!.receive(recvPk) }
                catch (_: SocketTimeoutException) { continue }
                catch (_: SocketException){ break }
                println("Got something ${recvPk.socketAddress} ${recvPk.data.getShort()}")

                when (recvPk.data.getShort()) {
                    ScanRequestTaskId -> if (recvPk.data[2] != lastScanPackId) {
                        lastScanPackId = recvPk.data[2]
                        sndPk.data.apply {
                            putShort(0, 0)
                            putBytes(lastScanPackId, 0, 0, 0, from = 2)
                        }
                        sndPk.socketAddress = recvPk.socketAddress
                        mainDSkLane!!.send(sndPk)
                    }
                    ConnectRequestTaskId -> {
                        val fromMaster = recvPk.getMaster()
                        if (fromMaster!=null)
                            joinWorking(fromMaster,taskManager)
                    }
                }
            }
            println("Stopped Listening")
        }
        catch (_: SocketException){}

        if(currentState!=WbState.StopingListen){
            currentState = WbState.StopingListen
            mainDSkLane?.close()
        }
        currentState = WbState.Idle
    }
    private fun joinWorking(master:Master,taskManager:TaskHandler){
        if(currentState!=WbState.Listening)
            return

        currentState = WbState.JoiningWork
        try {
            val connId = recvBuf[2]
            sndPk.socketAddress = master.getSockAddress()
            sndBuf.apply {
                position(0)
                putShort(ConnectionTaskId)
                put(connId); put(1);
                putInt(-1); putInt(Build.VERSION.SDK_INT);
            }
            sndPk.length = InitPkSize

            mainDSkLane?.send(sndPk)
            mainDSkLane?.soTimeout = 100
            do{ mainDSkLane?.receive(recvPk); } while(
                recvPk.socketAddress != master.getSockAddress()
                || recvPk.length != CtrlPkSize || recvBuf.getShort(0) != ConnectionTaskId
                || recvBuf.get(2) != connId || recvBuf.get(3) != 2.toByte()
            )

            workingFor = master
            currentState = WbState.Working
            println("Connected to $workingFor")
            stateCallbacks.forEach {
                it.second.post {
                    it.first.onJoinedWork(
                        master
                    )
                }
            }
            senderThread = thread(name="SendLooper"){ sendLooper() }
            recvLooper(taskManager)
            println("Disconnected from work at ${System.currentTimeMillis()}")
        }
        catch (_:IOException){}
        catch (_:SocketException){}
        catch (_:SocketTimeoutException){}

        if(currentState!=WbState.StopingListen)
            currentState = WbState.LeavingWork

        senderThread?.interrupt()
        workingFor = null
        if(currentState!=WbState.StopingListen)
            currentState = WbState.Listening
        stateCallbacks.forEach {
            it.second.post {
                it.first.onLeftWork()
            }
        }
    }
    private fun leaveWork(){
        if(currentState!=WbState.Working)
            return
        currentState = WbState.LeavingWork
    }
    fun stopListening(){
        if(currentState!=WbState.Listening && currentState!=WbState.Working)
            return
        currentState = WbState.StopingListen
        mainDSkLane?.close()
    }

    private var nextBeatAt = 0L
    private fun sendLooper(){
        if(currentState != WbState.Working)
            return

        val beatPk = DatagramPacket(
            byteArrayOf(
                0, BeatTaskId.toByte(),
                0, 0, 0, 0
            ), 6,
            sndPk.socketAddress
        )
        try {
            mainDSkLane!!.send(beatPk)
            while (currentState == WbState.Working) {
                try { Thread.sleep(InterBy4) }
                catch (_: InterruptedException) {}

                val now = System.currentTimeMillis()
                if(now >= nextBeatAt){
                    mainDSkLane?.send(beatPk)
                    nextBeatAt = now + InterBy4
                }
            }
        }
        catch (_: IOException) {}
        catch (_: SocketException) {}
        leaveWork()
    }
    private fun recvLooper(taskManager:TaskHandler){
        if(currentState != WbState.Working)
            return

        try {
            mainDSkLane!!.soTimeout = Inter.toInt()
            while (currentState == WbState.Working) {
                mainDSkLane!!.receive(recvPk)
                if (recvPk.socketAddress != workingFor?.addr)
                    continue

                when(val taskId = recvPk.data.getShort()){
                    BeatTaskId -> {}
                    DisconnectTaskId -> break
                    else -> if(taskId < 0 || taskId >= 256){
                        val timeTook = measureNanoTime {
                            taskManager.handleTask(
                                recvPk.data,
                            )
                        }
//                        println("task{$taskId} took ${timeTook}ns")
                    }
                }
            }
        }
        catch (_:SocketException){}
        catch (_:SocketTimeoutException){}
        mainDSkLane!!.soTimeout = LCInter
    }

    fun sendReplay(taskId:Short, dataWriter:(buf:ByteBuffer)->Int){
        if(currentState != WbState.Working) return
        synchronized(sndBuf){
            sndBuf.position(0)
            sndBuf.putShort(taskId)
            sndPk.length = dataWriter(sndBuf)
            mainDSkLane?.send(sndPk)
//            println("Sent ${taskId+65536} len=${sndPk.length}")
        }
    }
}