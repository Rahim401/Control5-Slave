package com.S2Bytes.control5

import android.os.Build
import android.os.Handler
import com.S2Bytes.control5.Master.Companion.getMaster
import com.S2Bytes.control5.ui.composable.LogMsg
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

interface BridgeStCallback{
    fun onStateChanged(state:WorkerBridge.States)
    fun onJoinedWork(to:Master)
    fun onLeftWork(by:String)
    fun logMessage(msg:LogMsg)
}

object WorkerBridge {
    enum class States{
        Idle,
        Listening,
        JoiningWork,
        Working,
        LeavingWork
    }

    const val MainPort = 32654
    const val DataPort = MainPort+1
    private const val InitPkSize = 64
    private const val DataPkSize = 6
    private const val deviceName = "Rahim's Droid"
    private const val Inter = 1000L
    private const val InterBy4 = Inter/4

    private const val BufSize = DataPkSize*20

    private var currentState = States.Idle
        set(value) {
            if(value==field)
                return

            field = value
            uiHand?.post {
                stateCB?.onStateChanged(field)
            }
        }
    private var workingFor:Master? = null

    private val mainDSkLane = DatagramSocket(MainPort)
    private val srvSSk = ServerSocket().apply {
        soTimeout = 100
        reuseAddress = true
        bind(InetSocketAddress(DataPort))
    }
    private val recvBuf = ByteArray(BufSize)
    private val sndBuf = ByteArray(BufSize)
    private val recvPk = DatagramPacket(recvBuf, DataPkSize)
    private val sndPk = DatagramPacket(sndBuf, DataPkSize)

    private var dataSSkLane:Socket? = null
        set(value) {
            if(field==value)
                return

            field = value
            if(value!=null){
                dataInStream = DataInputStream(value.getInputStream())
                dataOutStream = DataOutputStream(value.getOutputStream())
            }
            else{
                dataInStream = null
                dataOutStream = null
            }
        }

    private var dataInStream:DataInputStream? = null
    private var dataOutStream:DataOutputStream? = null
    private val extraSSkLanes:List<Socket> = ArrayList()

    private var sndThr:Thread? = null
    private var stateCB: BridgeStCallback? = null
    private var uiHand: Handler? = null
    fun setCallback(cb:BridgeStCallback?, hand: Handler?){
        stateCB = cb
        uiHand = hand

        uiHand?.post {
            stateCB?.onStateChanged(currentState)
            if(currentState==States.Working && workingFor!=null)
                stateCB?.onJoinedWork(workingFor!!)
        }
    }

    fun isListening() = currentState!=States.Idle
    fun listenForWork(){
        if(currentState!=States.Idle)
            return

        currentState = States.Listening
        thread {
            mainDSkLane.soTimeout = 100
            var lastScanPackId:Byte = 0

            while(currentState==States.Listening){
                try{ mainDSkLane.receive(recvPk) }
                catch (e:SocketTimeoutException){ continue }

                val requestId = recvPk.data.getShort()
                when(requestId.toInt()){
                    0 -> if(recvPk.data[2]!=lastScanPackId){
                        lastScanPackId = recvPk.data[2]
                        sndPk.data.apply{
                            putShort(0,0)
                            putBytes(lastScanPackId,0,0,0, from = 2)
                        }
                        sndPk.socketAddress = recvPk.socketAddress
                        mainDSkLane.send(sndPk)
                    }
                    8 ->{
                        if(recvPk.data[4]==0.toByte())
                            joinAndWork()
                    }
                }
            }
        }
    }

    //Connect
    private fun joinAndWork() {
        if (currentState != States.Listening)
            return
        val requestFrom = recvPk.getMaster() ?: return

        currentState = States.JoiningWork
        try {
            sndPk.socketAddress = requestFrom.getSockAddress()
            sndBuf.putShort(0,8)
            sndBuf.putBytes(recvBuf[2], 1, 0, 0,from = 2)
            sndPk.setData(sndBuf,0, DataPkSize)
            mainDSkLane.send(sndPk)

            val waitTill = System.currentTimeMillis()+100
            while (dataSSkLane?.inetAddress != sndPk.address) {
                if(System.currentTimeMillis()>waitTill)
                    throw SocketTimeoutException("No response from Server")
                dataSSkLane = srvSSk.accept()
            }

            sndBuf[3] = 2
            dataOutStream?.write(sndBuf,0, 4)
            dataOutStream?.writeInt(-1)
            dataOutStream?.writeInt(Build.VERSION.SDK_INT)

            dataSSkLane?.soTimeout = 100
            if(dataInStream?.read(recvBuf,0,12)!=12)
                throw ConnectException("No proper response from Server")
            else for(i in 0 until 4){
                if(recvBuf[i]!=sndBuf[i])
                    throw ConnectException("No proper response from Server")
            }

            currentState = States.Working
            workingFor = requestFrom
            println("\nConnected to $workingFor")
            uiHand?.post {
                stateCB?.onJoinedWork(requestFrom)
                stateCB?.logMessage(LogMsg("Network","Connected to ${workingFor?.name}"))
            }

            sndThr = Thread(SendLooper)
            sndThr?.start()
            RecvLooper.run()
        }
        catch (e: IOException) { leaveWork("WrongConn"); e.printStackTrace() }
        catch (_: SocketTimeoutException) { leaveWork("WrongConn2") }
        catch (_: ConnectException) { leaveWork("WrongConn3") }
        sndPk.length = DataPkSize
    }

    private fun leaveFromWork(){
        if(currentState==States.Idle || currentState==States.Listening)
            return

        sendDI(10)
        leaveWork("User")
    }

    private fun leaveWork(by:String="Network"){
        if(currentState==States.Idle || currentState==States.Listening)
            return

        currentState = States.LeavingWork
        dataSSkLane?.close()
        dataSSkLane = null

        workingFor = null
        sndThr?.join()

        currentState = States.Listening
        uiHand?.post {
            stateCB?.onLeftWork(by)
//            stateCB?.logMessage(LogMsg("Network","Disconnected by $by"))
        }
        println("Disconnected by $by")
    }


    private var lastMsgRecvAt = -1L
    private val RecvLooper = Runnable {
        if(currentState != States.Working)
            return@Runnable

        mainDSkLane.soTimeout = Inter.toInt()
        var disConnBy = "Master"
        try {
            while (currentState == States.Working) {
                mainDSkLane.receive(recvPk)
                if (recvPk.socketAddress == workingFor?.addr) {
                    val now = System.currentTimeMillis()
                    lastMsgRecvAt = now

                    val taskId = recvPk.data.getShort().toInt()
                    when(taskId){
                        9 -> {}
                        10 -> break
                        else -> uiHand?.post {
                            println(recvBuf.toList())
                            stateCB?.logMessage(
                                LogMsg(
                                    "Master:$taskId",
                                    "Data sent is ${recvBuf.getInt(2)}"
                                )
                            )
                        }
                    }
                }
            }
        }
        catch (_:SocketTimeoutException){ disConnBy = "Network"}
        mainDSkLane.soTimeout = 100
        leaveWork(disConnBy)
    }


    private var sndPkWrote = 0
    private var sndPkRead = 0
    private val SendLooper = Runnable {
        if(currentState != States.Working)
            return@Runnable

        val beatPk = DatagramPacket(byteArrayOf(0,9,0,0,0,0),6, sndPk.socketAddress)
        mainDSkLane.send(beatPk)
        while(currentState==States.Working){
            try{ Thread.sleep(InterBy4) }
            catch (_:InterruptedException){}

            if(sndPkRead == sndPkWrote)
                mainDSkLane.send(beatPk)
            else while(sndPkRead < sndPkWrote){
                sndPk.setData(sndBuf, sndPkRead % BufSize, DataPkSize)
                mainDSkLane.send(sndPk)
                sndPkRead += DataPkSize
            }
        }
    }

    fun sendDI(id:Short, d1:Int=0){
        if(currentState!=States.Working) return
        synchronized(sndBuf){
            val idx = sndPkWrote % BufSize
            sndBuf.putShort(idx,id)
            sndBuf.putInt(idx+2,d1)
//            println("Sending at pos-$sndPkWrote idx-$idx")
            sndPkWrote += DataPkSize
        }
    }

    fun sendDSS(id:Short, d1:Short=0, d2:Short=0){
        if(currentState!=States.Working) return
        synchronized(sndBuf){
            val idx = sndPkWrote % BufSize
            sndBuf.putShort(idx,id)
            sndBuf.putShort(idx+2,d1)
            sndBuf.putShort(idx+4,d2)
            sndPkWrote += DataPkSize
        }
    }

    fun sendDBS(id:Short, d1:Byte=0, d2:Byte=0, d3:Short=0){
        if(currentState!=States.Working) return
        synchronized(sndBuf){
            val idx = sndPkWrote % BufSize
            sndBuf.putShort(idx,id)
            sndBuf.putBytes(d1,d2, from = idx+2)
            sndBuf.putShort(idx+4,d3)
            sndPkWrote += DataPkSize
        }
    }

    fun sendDB(id:Short, d1:Byte=0, d2:Byte=0, d3:Byte=0, d4:Byte=0){
        if(currentState!=States.Working) return
        synchronized(sndBuf){
            val idx = sndPkWrote % BufSize
            sndBuf.putShort(idx,id)
            sndBuf.putBytes(d1,d2,d3,d4, from = idx+2)
            sndPkWrote += DataPkSize
        }
    }

    fun stopListening(){
        leaveFromWork()
        currentState = States.Idle
    }
}