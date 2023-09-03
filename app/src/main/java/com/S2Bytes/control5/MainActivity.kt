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

class MainActivity : ComponentActivity() {
    var bridgeState by mutableStateOf(WbState.Idle)
    var connectedTo:Master? by mutableStateOf(null)
    val logMessages = mutableStateListOf<LogMsg>()

    var uiHandler:Handler? = null
    private val taskManager = object :TaskManager{
        val taskThread = HandlerThread("TaskHandler").apply{ start() }
        var taskHandler:Handler = Handler(taskThread.looper)
        override fun handleTask(id: Short, data: ByteArray) {
            uiHandler?.post {
                println("Got(ui) at ${System.currentTimeMillis()}")
                logMessages.add(
                    LogMsg(
                        "Task($id)",
                        "D1:${data[0]}, D2:${data[1]}, D3:${data[2]}, D4:${data[3]}"
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
                    uiHandler?.post {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiHandler =  Handler(mainLooper)
        setContent {
            Control5Theme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MyApp(bridgeState,connectedTo,logMessages){
                        when(bridgeState){
                            WbState.Idle -> thread { WorkerBridge.startListening(taskManager) }
                            WbState.Listening,WbState.Working ->
                                WorkerBridge.stopListening()
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        WorkerBridge.setStateCallback(
            object: StateCallback{
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
            },
            uiHandler
        )
        thread {
            WorkerBridge.startListening(taskManager)
        }
        super.onStart()
    }

    override fun onStop() {
        WorkerBridge.stopListening()
        WorkerBridge.setStateCallback(null,null)
        super.onStop()
    }
}