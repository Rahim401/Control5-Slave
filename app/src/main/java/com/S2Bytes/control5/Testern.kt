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
//                        WorkerBridge.replaInt(data.getShort(), data.getInt(2))
                    }
                }
            )
        }
        catch (_:Exception){}
    }
}