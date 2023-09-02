package com.S2Bytes.control5

import android.os.Build
import android.os.Handler
import com.S2Bytes.control5.Master.Companion.getMaster
import com.S2Bytes.control5.ui.composable.LogMsg
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.NullPointerException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

interface StateCallback{
    fun onStateChanged(state:WorkerBridge.States)
    fun onJoinedWork(to:Master)
    fun onLeftWork(by:String)
}

interface TaskCallback{
    fun handleTask(id:Short, dArr:ByteArray)
    fun handleExTask(dStream:DataInputStream)
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
    const val DataPort = MainPort + 1

    private const val deviceName = "Rahim's Droid"
    private const val Inter = 1000L
    private const val InterBy4 = Inter / 4

    private const val InitPkSize = 64
    private const val DataPkSize = 6
    private const val BufSize = DataPkSize * 20


    const val ScanRequestTaskId = 0.toShort()
    const val ConnectRequestTaskId = 63.toShort()

    const val ConnectionTaskId = 64.toShort()
    const val BeatTaskId = 65.toShort()
    const val ExTaskInTaskId = 66.toShort()
    const val ExTaskOutTaskId = 67.toShort()
    const val DisconnectTaskId = 255.toShort()

    private var currentState = States.Idle
        set(value) {
            if(value==field)
                return

            field = value
            stateCB?.first?.post {
                stateCB?.second?.onStateChanged(field)
            }
        }
    private var workingFor:Master? = null

    private var mainDSkLane:DatagramSocket? = null
    private val recvBuf = ByteArray(BufSize)
    private val sndBuf = ByteArray(BufSize)
    private val recvPk = DatagramPacket(recvBuf, DataPkSize)
    private val sndPk = DatagramPacket(sndBuf, DataPkSize)

    private var srvSSk:ServerSocket? = null
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

    private var stateCB: Pair<Handler,StateCallback>? = null
    fun setStateCallback(callback:StateCallback?, handler: Handler?){
        if(callback==null || handler==null){
            stateCB = null
            return
        }

        stateCB = Pair(handler,callback)
        stateCB?.first?.post {
            stateCB?.second?.onStateChanged(currentState)
            if(currentState==States.Working && workingFor!=null)
                stateCB?.second?.onJoinedWork(workingFor!!)
        }
    }


    fun listenForWork(taskCb:TaskCallback){
        if(currentState!=States.Idle)
            return

        currentState = States.Listening
        thread {
            mainDSkLane = DatagramSocket(MainPort)
            mainDSkLane!!.soTimeout = 100
            try {
                var lastScanPackId: Byte = 0

                while (currentState == States.Listening) {
                    try { mainDSkLane!!.receive(recvPk) }
                    catch (e: SocketTimeoutException) { continue }

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
                            if (recvPk.data[3] == 0.toByte())
                                joinAndWork(taskCb)
                        }
                    }
                }
            }
            catch (_: SocketException){ stopListening() }
        }
    }
    private fun joinAndWork(taskCb:TaskCallback) {
        if (currentState != States.Listening)
            return
        val requestFrom = recvPk.getMaster() ?: return

        currentState = States.JoiningWork
        try {
            srvSSk = ServerSocket().apply {
                soTimeout = 100
                reuseAddress = true
                bind(InetSocketAddress(DataPort))
            }

            sndPk.socketAddress = requestFrom.getSockAddress()
            sndBuf.putShort(0, ConnectionTaskId)
            sndBuf.putBytes(recvBuf[2], 1, 0, 0,from = 2)
            sndPk.setData(sndBuf,0, DataPkSize)
            mainDSkLane!!.send(sndPk)

            val waitTill = System.currentTimeMillis()+100
            while (dataSSkLane?.inetAddress != sndPk.address) {
                if(System.currentTimeMillis()>waitTill)
                    throw SocketTimeoutException("No response from Server")
                dataSSkLane = srvSSk!!.accept()
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
            stateCB?.first?.post {
                stateCB?.second
                    ?.onJoinedWork(requestFrom)
            }

            sndThr = thread(name = "SendLooper"){ sendLooper() }
            recvLooper(taskCb)
        }
        catch (e: IOException) { leaveWork("WrongConn"); e.printStackTrace() }
        catch (_: NullPointerException) { leaveWork("WrongConn1") }
        catch (_: SocketTimeoutException) { leaveWork("WrongConn2") }
        catch (_: ConnectException) { leaveWork("WrongConn3") }
        sndPk.length = DataPkSize
    }
    private fun leaveFromWork(){
        if(currentState==States.Idle || currentState==States.Listening)
            return

        sendDI(DisconnectTaskId)
        leaveWork("User")
    }
    private fun leaveWork(by:String="Network"){
        if(currentState!=States.JoiningWork && currentState!=States.Working)
            return

        currentState = States.LeavingWork
        dataSSkLane?.close()
        dataSSkLane = null

        srvSSk?.close()
        srvSSk = null

        workingFor = null
        sndThr?.join()

        currentState = States.Listening
        stateCB?.first?.post {
            stateCB?.second
                ?.onLeftWork(by)
        }
        println("Disconnected by $by")
    }
    fun stopListening(){
        if(currentState==States.Idle)
            return

        leaveFromWork()
        mainDSkLane?.close()
        mainDSkLane = null
        currentState = States.Idle
    }

    private var ETaskLeft = 0
    private var lastMsgRecvAt = -1L
    private fun recvLooper(taskCb:TaskCallback){
        if(currentState != States.Working)
            return

        ETaskLeft = 0
        try {
            mainDSkLane!!.soTimeout = Inter.toInt()
            while (currentState == States.Working) {
                mainDSkLane!!.receive(recvPk)
                if (recvPk.socketAddress != workingFor?.addr)
                    continue

                lastMsgRecvAt = System.currentTimeMillis()
                when(val taskId = recvPk.data.getShort()){
                    BeatTaskId -> {}
                    DisconnectTaskId -> break
                    ExTaskInTaskId -> {
                        println("Got msgFrm $taskId")
                        taskCb.handleExTask(dataInStream!!)
                    }
                    else -> {
                        if (taskId >= 256) {
                            println("Got at ${System.currentTimeMillis()}")
                            taskCb.handleTask(taskId,recvPk.data.copyOfRange(2,6))
                        }
                    }
                }
            }
        }
        catch (_:SocketTimeoutException){ leaveWork("Network")}
        mainDSkLane!!.soTimeout = 100
    }

    private var sndPkWrote = 0
    private var sndPkRead = 0
    private var sndThr:Thread? = null
    private fun sendLooper() {
        if(currentState != States.Working)
            return

        sndPkRead = 0
        sndPkWrote = 0
        val beatPk = DatagramPacket(byteArrayOf(0,BeatTaskId.toByte(),0,0,0,0),6, sndPk.socketAddress)
        mainDSkLane!!.send(beatPk)
        while(currentState==States.Working){
            try{ Thread.sleep(InterBy4) }
            catch (_:InterruptedException){}

            if(sndPkRead == sndPkWrote)
                mainDSkLane!!.send(beatPk)
            else while(sndPkRead < sndPkWrote){
                sndPk.setData(sndBuf, sndPkRead % BufSize, DataPkSize)
                mainDSkLane!!.send(sndPk)
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
}