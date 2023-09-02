package com.S2Bytes.control5

import java.io.DataInputStream
import java.lang.Exception
import kotlin.concurrent.thread

fun main(){
    thread {
        try {
            WorkerBridge2.startListening(
                object: TaskManager{
                    override fun handleTask(id: Short, data: ByteArray) {
                        WorkerBridge2.replayI(id, data.getInt())
                    }

                    override fun handleExTask(dStm: DataInputStream) {
                        val taskId = dStm.readShort()
                        val msg = dStm.readUTF()
                        WorkerBridge2.replayStream(taskId){
                            it.writeUTF(msg)
                        }
                    }
                }
            )
        }
        catch (_:Exception){}
    }
}