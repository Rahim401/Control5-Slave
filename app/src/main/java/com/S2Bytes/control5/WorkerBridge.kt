package com.S2Bytes.control5

import android.os.Build
import android.os.Handler
import com.S2Bytes.control5.Master.Companion.getMaster
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

enum class WbState{
    Idle,
    StartingListen,
    Listening,
    JoiningWork,
    Working,
    LeavingWork,
    StopingListen
}
interface TaskManager{
    fun handleTask(id: Short,data: ByteArray)
    fun handleExTask(dStm:DataInputStream)
}
interface StateCallback{
    fun onStateChanged(state:WbState)
    fun onJoinedWork(to:Master)
    fun onLeftWork()
}

const val ScanRequestTaskId = 0.toShort()
const val ConnectRequestTaskId = 63.toShort()

const val ConnectionTaskId = 64.toShort()
const val BeatTaskId = 65.toShort()
const val ExtendedTaskId = 66.toShort()
const val DisconnectTaskId = 255.toShort()


object WorkerBridge {
    const val MainPort = 32654
    const val DataPort = MainPort + 1

    private const val Inter = 1000L
    private const val InterBy4 = Inter / 4
    private const val LCInter = 100

    private const val InitPkSize = 64
    private const val DataPkSize = 6
    private const val BufSize = DataPkSize * 20

    private var mainDSkLane:DatagramSocket? = null
    private val recvBuf = ByteArray(BufSize)
    private val sndBuf = ByteArray(BufSize)
    private val recvPk = DatagramPacket(recvBuf, DataPkSize)
    private val sndPk = DatagramPacket(sndBuf, DataPkSize)

    private var srvSSk: ServerSocket? = null
    private var dataInStream: DataInputStream? = null
    private var dataOutStream: DataOutputStream? = null
    private var dataSSkLane: Socket? = null
        set(value) {
            if(field==value)
                return

            field = value
            if(value!=null){
                dataInStream = DataInputStream(value.getInputStream())
                dataOutStream = DataOutputStream(value.getOutputStream())
            }
            else {
                dataInStream?.close()
                dataInStream = null
                dataOutStream?.close()
                dataOutStream = null
            }
        }

    private var stateCB: Pair<Handler,StateCallback>? = null
    fun setStateCallback(callback:StateCallback?, handler: Handler?){
        if(callback==null || handler==null){
            stateCB = null
            return
        }

        stateCB = Pair(handler,callback)
        stateCB?.first?.post {
            stateCB?.second?.onStateChanged(currentState)
            if(currentState == WbState.Working && workingFor !=null)
                stateCB?.second?.onJoinedWork(workingFor!!)
        }
    }

    private var workingFor:Master? = null
    private var currentState = WbState.Idle
        set(value) {
            if (field==value)
                return
//            when (field){
//                WbState.Idle -> if(value!=WbState.StartingListen) return
//                WbState.StartingListen -> if(value!=WbState.Idle && value!=WbState.Listening) return
//                WbState.Listening -> if(value!=WbState.StopingListen && value!=WbState.JoiningWork) return
//                WbState.JoiningWork -> if(value!=WbState.Listening && value!=WbState.Working) return
//                WbState.Working -> if(value!=WbState.LeavingWork && value!=WbState.StopingListen) return
//                WbState.LeavingWork -> if(value!=WbState.Working && value!=WbState.Listening) return
//                WbState.StopingListen -> if(value!=WbState.Listening && value!=WbState.Idle) return
//            }
//            println("State from $field -> $value at ${System.currentTimeMillis()}")
            field = value
            stateCB?.first?.post {
                stateCB?.second?.onStateChanged(field)
            }
        }

    fun startListening(taskManager:TaskManager){
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
    private var sndThr:Thread? = null
    private fun joinWorking(master:Master,taskManager:TaskManager){
        if(currentState!=WbState.Listening)
            return

        currentState = WbState.JoiningWork
        try {
            srvSSk = ServerSocket().apply {
                soTimeout = LCInter
                reuseAddress = true
                bind(InetSocketAddress(DataPort))
            }

            sndPk.socketAddress = master.getSockAddress()
            sndBuf.putShort(0, ConnectionTaskId)
            sndBuf.putBytes(recvBuf[2], 1, 0, 0,from = 2)
            sndPk.setData(sndBuf,0, DataPkSize)
            mainDSkLane!!.send(sndPk)

            val waitTill = System.currentTimeMillis() + LCInter
            do{
                if(System.currentTimeMillis()>waitTill)
                    throw SocketTimeoutException("No response from Server")
                dataSSkLane = srvSSk!!.accept()
            }
            while (dataSSkLane?.inetAddress != sndPk.address)

            sndBuf[3] = 2
            dataOutStream?.write(sndBuf,0, 4)
            dataOutStream?.writeInt(-1)
            dataOutStream?.writeInt(Build.VERSION.SDK_INT)

            dataSSkLane?.soTimeout = LCInter
            if(dataInStream?.read(recvBuf,0,12)!=12)
                throw ConnectException("No proper response from Server")
            else for(i in 0 until 4){
                if(recvBuf[i]!= sndBuf[i])
                    throw ConnectException("No proper response from Server")
            }

            workingFor = master
            currentState = WbState.Working
            println("Connected to $workingFor")
            stateCB?.first?.post {
                stateCB?.second
                    ?.onJoinedWork(master)
            }

            sndThr = thread(name="SendLooper"){ sendLooper() }
            recvLooper(taskManager)
            println("Disconnected from work at ${System.currentTimeMillis()}")
        }
        catch (_:IOException){}
        catch (_:SocketException){}
        catch (_:SocketTimeoutException){}

        if(currentState!=WbState.StopingListen)
            currentState = WbState.LeavingWork
        dataSSkLane?.close()
        dataSSkLane = null

        srvSSk?.close()
        sndThr?.interrupt()
        workingFor = null
        if(currentState!=WbState.StopingListen)
            currentState = WbState.Listening
        stateCB?.first?.post {
            stateCB?.second
                ?.onLeftWork()
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

    private var sndPkRead = 0
    private var sndPkWrote = 0
    private fun sendLooper(){
        if(currentState != WbState.Working)
            return

        sndPkRead = 0
        sndPkWrote = 0
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

                if (sndPkRead == sndPkWrote)
                    mainDSkLane!!.send(beatPk)
                else while (sndPkRead < sndPkWrote) {
                    sndPk.setData(
                        sndBuf, sndPkRead % BufSize,
                        DataPkSize
                    )
                    mainDSkLane!!.send(sndPk)
                    sndPkRead += DataPkSize
                }
            }
        }
        catch (_: IOException) {}
        catch (_: SocketException) {}
        leaveWork()
    }
    private fun recvLooper(taskManager:TaskManager){
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
                    ExtendedTaskId -> {
                        taskManager.handleExTask(dataInStream!!)
                    }
                    else -> {
                        if (taskId >= 256)
                            taskManager.handleTask(
                                taskId,
                                recvPk.data.copyOfRange(2,6)
                            )
                    }
                }
            }
        }
        catch (_:SocketException){}
        catch (_:SocketTimeoutException){}
        mainDSkLane!!.soTimeout = LCInter
    }

    fun replayI(id:Short, d1:Int=0){
        if(currentState != WbState.Working) return
        synchronized(sndBuf){
            val idx = sndPkWrote % BufSize
            sndBuf.putShort(idx,id)
            sndBuf.putInt(idx+2,d1)
            sndPkWrote += DataPkSize
        }
        sndThr?.interrupt()
    }
    fun replayStream(id:Short, sendReplay:(DataOutputStream)->Unit){
        if(currentState != WbState.Working) return
        synchronized(dataOutStream!!){
            dataOutStream?.writeShort(id.toInt())
            sendReplay(dataOutStream!!)
        }
        replayI(ExtendedTaskId)
    }
}