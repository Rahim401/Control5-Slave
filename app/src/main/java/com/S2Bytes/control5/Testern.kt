package com.S2Bytes.control5

import java.io.DataInputStream
import java.lang.Exception
import kotlin.concurrent.thread

fun main(){
    thread {
        try {
            WorkerBridge.startListening(
                object: TaskHandler{
                    override fun handleTask(data: ByteArray) {
                        WorkerBridge.replayInt(data.getShort(), data.getInt(2))
                    }

                    override fun handleExTask(dStm: DataInputStream) {
                        val taskId = dStm.readShort()
                        val msg = dStm.readUTF()
                        WorkerBridge.replayStream(taskId){
                            it.writeUTF(msg)
                        }
                    }
                }
            )
        }
        catch (_:Exception){}
    }
}