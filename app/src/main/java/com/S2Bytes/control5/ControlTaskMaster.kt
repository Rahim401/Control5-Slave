package com.S2Bytes.control5

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.DataInputStream

interface Task: TaskHandler{
    val name:String
    override fun handleTask(data: ByteArray) {}
    override fun handleExTask(dStm: DataInputStream) {}
}


@OptIn(DelicateCoroutinesApi::class)
class ControlTaskMaster(
    private val ctx:Context,
    private val uiHand:Handler
): TaskHandler {
    private val mHandThr = HandlerThread("TaskMaster")
        .apply { start() }
    private val mHand = Handler(mHandThr.looper)
    private val workerScope = CoroutineScope(newSingleThreadContext("workerThread"))

    private val dStmLock = Mutex()
    private val taskMap = HashMap<Short,Task>(20)

    init{
        //256-512 for System info
        taskMap[256] = object: Task{
            override val name: String = "SystemInfo"
            override fun handleTask(data: ByteArray) {
                val taskId = data.getShort()
                val infoCode = data[2].toInt()
                workerScope.launch {
                    if (infoCode in 13..16 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                        return@launch
                    val info = when(infoCode){
                        0 -> Build.USER
                        1 -> Build.HOST
                        2 -> Build.BOARD
                        3 -> Build.ID
                        4 -> Build.BOOTLOADER
                        5 -> Build.BRAND
                        6 -> Build.DEVICE
                        7 -> Build.DISPLAY
                        8 -> Build.FINGERPRINT
                        9 -> Build.HARDWARE
                        10 -> Build.MANUFACTURER
                        11 -> Build.MODEL
                        12 -> Build.PRODUCT
                        13 -> Build.ODM_SKU
                        14 -> Build.SKU
                        15 -> Build.SOC_MANUFACTURER
                        16 -> Build.SOC_MODEL
                        17 -> Build.TAGS
                        18 -> Build.TIME.toString()
                        19 -> Build.TYPE
                        else -> "InvalidCode"
                    }
                    WorkerBridge.replayStream(taskId){
                        it.writeUTF(info)
                    }
                }
            }
        }

        taskMap[257] = object: Task{
            override val name: String = "SystemInfo2"
            override fun handleTask(data: ByteArray) {
                val taskId = data.getShort()
                val infoCode = data[2].toInt()
                if (infoCode in 13..16 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                    return
                val info = when(infoCode){
                    0 -> Build.USER
                    1 -> Build.HOST
                    2 -> Build.BOARD
                    3 -> Build.ID
                    4 -> Build.BOOTLOADER
                    5 -> Build.BRAND
                    6 -> Build.DEVICE
                    7 -> Build.DISPLAY
                    8 -> Build.FINGERPRINT
                    9 -> Build.HARDWARE
                    10 -> Build.MANUFACTURER
                    11 -> Build.MODEL
                    12 -> Build.PRODUCT
                    13 -> Build.ODM_SKU
                    14 -> Build.SKU
                    15 -> Build.SOC_MANUFACTURER
                    16 -> Build.SOC_MODEL
                    17 -> Build.TAGS
                    18 -> Build.TIME.toString()
                    19 -> Build.TYPE
                    else -> "InvalidCode"
                }
                Thread.sleep(500)
                WorkerBridge.replayStream(taskId){
                    it.writeUTF(info)
                }
            }
        }

    }

    override fun handleTask(data: ByteArray) {
        val taskId = data.getShort()
        val task = taskMap[taskId]
        task?.handleTask(data)
    }

    override fun handleExTask(dStm: DataInputStream) {

    }
}