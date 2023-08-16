package com.S2Bytes.control5

import android.os.Bundle
import android.os.Handler
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

class MainActivity : ComponentActivity() {
    var bridgeState by mutableStateOf(States.Idle)
    var connectedTo:Master? by mutableStateOf(null)
    val logMessages = mutableStateListOf<LogMsg>()

    var uiHandler:Handler? = null

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
                                WorkerBridge.listenForWork()
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
        WorkerBridge.setCallback(
            object: BridgeStCallback{
                override fun onStateChanged(state: States) {
                    bridgeState = state
                }

                override fun onJoinedWork(to: Master) {
                    connectedTo = to
//                    uiHandler?.post {
//                        for(i in 0 until 400){
//                            WorkerBridge.sendDI(20,i)
////                            uiHandler?.postDelayed({ WorkerBridge.sendDI(20,i) },i*50L)
//                        }
//                    }
                }

                override fun onLeftWork(by: String) {
                    connectedTo = null
                    logMessages.clear()
                }

                override fun logMessage(msg: LogMsg) {
                    logMessages.add(msg)
                }

            },
            uiHandler
        )
        super.onStart()
    }

    override fun onStop() {
        WorkerBridge.stopListening()
        super.onStop()
    }
}