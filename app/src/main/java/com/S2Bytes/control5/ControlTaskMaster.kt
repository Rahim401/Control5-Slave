package com.S2Bytes.control5

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlin.experimental.and
import kotlin.experimental.or

interface Task: TaskHandler{
    val name:String
    override fun handleTask(data: ByteArray) {}
//    override fun handleExTask(dStm: DataInputStream) {}
}

const val TaskIdMask:Short = 4095

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
            @SuppressLint("NewApi")
            override fun handleTask(data: ByteArray) {
                val taskId = data.getShort()
                val infoCode = data[2].toInt()
                workerScope.launch {
                    val info = if (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        && infoCode in 13..16
                    ) "Api31Required"
                    else when(infoCode){
                        0 -> Build.USER; 1 -> Build.HOST; 2 -> Build.BOARD; 3 -> Build.ID
                        4 -> Build.BOOTLOADER; 5 -> Build.BRAND; 6 -> Build.DEVICE;
                        7 -> Build.DISPLAY; 8 -> Build.FINGERPRINT; 9 -> Build.HARDWARE;
                        10 -> Build.MANUFACTURER; 11 -> Build.MODEL; 12 -> Build.PRODUCT;
                        13 -> Build.ODM_SKU; 14 -> Build.SKU; 15 -> Build.SOC_MANUFACTURER;
                        16 -> Build.SOC_MODEL; 17 -> Build.TAGS;
                        18 -> Build.TIME.toString(); 19 -> Build.TYPE
                        else -> "InvalidCode"
                    }
                    WorkerBridge.sendReplay(taskId){
                        it.putUTF(info)
                        return@sendReplay it.position()
                    }
                }
            }
        }

    }

    override fun handleTask(data: ByteArray) {
        val taskId = data.getShort().and(TaskIdMask)
        taskMap[taskId]?.handleTask(data)
    }
}