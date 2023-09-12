package com.S2Bytes.control5

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.S2Bytes.control5.ui.composable.LogMsg
import com.S2Bytes.control5.ui.composable.MyApp
import com.S2Bytes.control5.ui.theme.Control5Theme
import java.io.DataInputStream
import java.net.SocketException
import kotlin.concurrent.thread

//val WorkerBridge = WorkerBridge()
class MainActivity : ComponentActivity() {
    private var bridgeState by mutableStateOf(WbState.Idle)
    private var connectedTo:Master? by mutableStateOf(null)
    private val logMessages = mutableStateListOf<LogMsg>()

    private lateinit var uiHandler:Handler
    private val taskManager = object :TaskHandler{
        val taskThread = HandlerThread("TaskHandler").apply{ start() }
        var taskHandler:Handler = Handler(taskThread.looper)
        override fun handleTask(data: ByteArray) {
            val taskId = data.getShort()
            uiHandler.post {
                println("Got(ui) at ${System.currentTimeMillis()}")
                logMessages.add(
                    LogMsg(
                        "Task(${taskId})",
                        "D1:${data[2]}, D2:${data[3]}, D3:${data[4]}, D4:${data[5]}"
                    )
                )
            }
        }

        override fun handleExTask(dStm: DataInputStream) {
            taskHandler.post {
                var id:Short = -1
                var strMsg = ""
                synchronized(dStm) {
                    try {
                        id = dStm.readShort()
                        strMsg = dStm.readArray(dStm.readShort().toInt()).decodeToString()
                    }
                    catch (_:SocketException){}
                }
                if(id>=0) {
                    uiHandler.post {
                        logMessages.add(
                            LogMsg(
                                "ETask($id)",
                                strMsg
                            )
                        )
                    }
                    WorkerBridge.replayStream(id){
                        it.writeUTF(strMsg)
                    }
                }
            }
        }
    }
    private lateinit var taskManager2:ControlTaskMaster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiHandler =  Handler(mainLooper)
        taskManager2 = ControlTaskMaster(this,uiHandler)
        setContent {
            Control5Theme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MyApp(bridgeState,connectedTo,logMessages){
                        when(bridgeState){
                            WbState.Idle -> thread { WorkerBridge.startListening(taskManager2) }
                            WbState.Listening,WbState.Working ->
                                WorkerBridge.stopListening()
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private var stateCb = object: StateCallback{
        override fun onStateChanged(state: WbState) {
            bridgeState = state
        }

        override fun onJoinedWork(to: Master) {
            logMessages.clear()
            connectedTo = to
        }

        override fun onLeftWork() {
            connectedTo = null
        }
    }

    override fun onStart() {
        WorkerBridge.registerStateCallback(
            stateCb,
            uiHandler
        )
        thread {
            WorkerBridge.startListening(
                taskManager2
            )
        }
        super.onStart()
    }

    override fun onStop() {
        WorkerBridge.stopListening()
        WorkerBridge.unregisterStateCallback(
            stateCb,
            uiHandler
        )
        super.onStop()
    }
}