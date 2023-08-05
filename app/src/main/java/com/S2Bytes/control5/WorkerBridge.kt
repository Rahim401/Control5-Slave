package com.S2Bytes.control5

import android.os.Build
import com.S2Bytes.control5.Master.Companion.getMaster
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

object WorkerBridge {
    enum class States{
        Ready,
        Listening,
        Working,
        Closed
    }

    const val MainPort = 32654
    private const val InitSize = 64
    private const val deviceName = "Rahim's Droid"
    private const val Inter = 1000L
    private const val InterBy4 = Inter/4


    private var currentState = States.Ready
    private val mainSkLane = DatagramSocket(MainPort)
    private val recvPack = DatagramPacket(ByteArray(InitSize),InitSize)
    private val sndPack = DatagramPacket(ByteArray(InitSize),InitSize)

    private var workingFor:Master? = null
    private var sendThr:Thread? = null
    private val streamSkLanes:List<Socket> = ArrayList()

    fun makeConnectBuf(buffer:ByteArray){
        buffer.apply {
            putShort(0,8)


        }
    }

    fun searchForWork(){
        if(currentState!=States.Ready)
            return
        currentState = States.Listening
        thread {
            sndPack.length = 64
            mainSkLane.soTimeout = 100
            var lastScanPackId:Byte = 0

            while(currentState==States.Listening){
                try{ mainSkLane.receive(recvPack) }
                catch (e:SocketTimeoutException){ continue }

                val requestId = recvPack.data.getShort()
                when(requestId.toInt()){
                    0 -> if(recvPack.data[2]!=lastScanPackId){
                        lastScanPackId = recvPack.data[2]
                        sndPack.data.apply{
                            putShort(0,0)
                            set(2, lastScanPackId)
                        }
                        sndPack.length = 3
                        sndPack.socketAddress = recvPack.socketAddress
                        mainSkLane.send(sndPack)
                    }
                    8 ->{
                        if(recvPack.data[4]==0.toByte())
                            joinWork()
                    }
                }
            }
        }
    }

    //Connect
    private fun joinWork(){
        if(currentState!=States.Listening)
            return

        workingFor = recvPack.getMaster()
        if(workingFor==null) return

        currentState = States.Working
        sndPack.socketAddress = workingFor!!.getSockAddress()
        sndPack.data.apply {
            putShort(0,8)
            set(2, recvPack.data[2])
            set(3, 1)
            putInt(4,-1)
            putInt(8,Build.VERSION.SDK_INT)
        }
        sndPack.length = 12

        try{
            mainSkLane.send(sndPack)
            println("\nConnected to $workingFor")

            sndPack.data.putShort(0,9)
            sndPack.data.putBytes(0,0,0,from = 2)
            sndPack.length = 5
            Thread(SendLooper2).start()
            RecvLooper.run()
        }
        catch (_:IOException){
            leaveWork("WrongConn")
        }
    }

    private fun leaveFromWork(){
        if(currentState!=States.Working)
            return

        sndPack.data.putShort(0,10)
        sndPack.length = 5

        leaveWork("ByUser")
    }

    private fun leaveWork(by:String="Network"){
        if(currentState!=States.Working)
            return

        println("Disconnected by $by")
        currentState = States.Listening
        workingFor = null

        mainSkLane.soTimeout = 100
    }


    private var nextBeatAt = -1L
    private val SendLooper = Runnable {
        if(currentState != States.Working)
            return@Runnable

        try{
            while(currentState==States.Working){
                Thread.sleep(InterBy4)
                val now = System.currentTimeMillis()
                if(now > nextBeatAt){
                    mainSkLane.send(sndPack)
                    nextBeatAt = now + InterBy4
                }
            }
        }catch (_:SocketException){
            leaveWork("Send Error")
        }
    }

    private val SndArr = ByteArray(256*10)
    private val SndBuf = ByteBuffer.wrap(SndArr)
    private val DSndPk = DatagramPacket(SndArr, SndArr.size)
    private var atSndPos = 0
    private var nextBeatAt2 = -1L
    private val SendLooper2 = Runnable {
        if(currentState != States.Working)
            return@Runnable

        DSndPk.socketAddress = sndPack.socketAddress
        while(currentState==States.Working){
            try{ Thread.sleep(InterBy4) }
            catch (_:InterruptedException){}

            var pkSize = SndArr[atSndPos].toPInt()
            if(pkSize==0)
                mainSkLane.send(sndPack)
            else while (pkSize!=0) {
                DSndPk.setData(SndArr, atSndPos, pkSize)
                mainSkLane.send(DSndPk)
                pkSize = SndArr[atSndPos].toPInt()
            }
        }
    }




    private var lastMsgRecvAt = -1L
    private val RecvLooper = Runnable {
        if(currentState != States.Working)
            return@Runnable

        mainSkLane.soTimeout = Inter.toInt()
        var disConnBy = "Master"
        try {
            while (currentState == States.Working) {
                mainSkLane.receive(recvPack)
                if (recvPack.socketAddress == workingFor?.addr) {
                    val now = System.currentTimeMillis()
//                    println("Time Between ${now- lastMsgRecvAt}")
                    lastMsgRecvAt = now

                    val taskId = recvPack.data.getShort().toInt()
                    when(taskId){
                        9 -> {}
                        10 -> break
                    }
                }
            }
        }
        catch (_:SocketTimeoutException){ disConnBy = "Network"}
        leaveWork(disConnBy)
    }


    fun stopListening(){
        leaveWork("User")
        currentState = States.Ready
    }
}