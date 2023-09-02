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
import com.S2Bytes.control5.WorkerBridge.States
import com.S2Bytes.control5.ui.composable.LogMsg
import com.S2Bytes.control5.ui.composable.MyApp
import com.S2Bytes.control5.ui.theme.Control5Theme
import java.io.DataInputStream
import java.net.SocketException

class MainActivity : ComponentActivity() {
    var bridgeState by mutableStateOf(States.Idle)
    var connectedTo:Master? by mutableStateOf(null)
    val logMessages = mutableStateListOf<LogMsg>()

    var uiHandler:Handler? = null
    private val taskManager = object :TaskCallback{
        val taskThread = HandlerThread("TaskHandler").apply{ start() }
        var taskHandler:Handler = Handler(taskThread.looper)
        override fun handleTask(id: Short, dArr: ByteArray) {
            uiHandler?.post {
                println("Got(ui) at ${System.currentTimeMillis()}")
                logMessages.add(
                    LogMsg(
                        "Task($id)",
                        "D1:${dArr[0]}, D2:${dArr[1]}, D3:${dArr[2]}, D4:${dArr[3]}"
                    )
                )
            }
            taskHandler.post {
                WorkerBridge.sendDB(id,dArr[0],dArr[1],dArr[2],dArr[3])
            }
        }


        override fun handleExTask(dStream: DataInputStream) {
            taskHandler.post {
                synchronized(dStream) {
                    try {
                        val id = dStream.readShort()
                        val strMsg = dStream.readArray(dStream.readShort().toInt()).decodeToString()
                        uiHandler?.post {
                            logMessages.add(
                                LogMsg(
                                    "ETask($id)",
                                    strMsg
                                )
                            )
                        }
                    }
                    catch (_:SocketException){}
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
                            States.Idle ->
                                WorkerBridge.listenForWork(taskManager)
                            States.Listening,States.Working ->
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
                override fun onStateChanged(state: States) {
                    bridgeState = state
                }

                override fun onJoinedWork(to: Master) {
                    logMessages.clear()
                    connectedTo = to
                }

                override fun onLeftWork(by: String) {
                    connectedTo = null
                }
            },
            uiHandler
        )
        WorkerBridge.listenForWork(taskManager)
        super.onStart()
    }

    override fun onStop() {
        WorkerBridge.stopListening()
        WorkerBridge.setStateCallback(null,null)
        super.onStop()
    }
}