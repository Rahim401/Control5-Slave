package com.S2Bytes.control5.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.S2Bytes.control5.Master
import com.S2Bytes.control5.WorkerBridge
import com.S2Bytes.control5.getIpAddress
import com.S2Bytes.control5.ui.theme.Control5Theme
import com.S2Bytes.control5.ui.theme.Negative
import com.S2Bytes.control5.ui.theme.Neutral1
import com.S2Bytes.control5.ui.theme.Positive
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun MyApp(state: WorkerBridge.States, connectedTo: Master?, logLst:List<LogMsg>, onStateChanged:()->Unit={}) {
    Column(
        modifier = Modifier
            .padding(15.dp).fillMaxHeight(0.5f),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        SSButton(state, onStateChanged, Modifier.padding(top = 20.dp))
        StatusText(state, modifier = Modifier.padding(top = 30.dp))

        if((state==WorkerBridge.States.JoiningWork || state==WorkerBridge.States.Working || state==WorkerBridge.States.LeavingWork) && connectedTo!=null){
            MasterDetails(connectedTo = connectedTo, Modifier.padding(top = 30.dp))
            LogList(messages = logLst, Modifier.weight(1f).padding(top = 30.dp))
        }else Spacer(modifier = Modifier.weight(1f))
        NetworkText(modifier = Modifier.padding(top = 10.dp))
    }
}


@Composable
fun SSButton(state: WorkerBridge.States, onStateChanged:()->Unit, modifier: Modifier = Modifier){
    Button(
        onClick = { onStateChanged() },
        shape = ShapeDefaults.Small,
        modifier = modifier.fillMaxWidth(0.9f),
    ) {
        Text(
            text = when(state) {
                WorkerBridge.States.Idle -> "Start"
                else -> "Stop"
            },
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
fun StatusText(state: WorkerBridge.States, modifier: Modifier = Modifier){
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("Status: ")

                val statusColor = when (state) {
                    WorkerBridge.States.Idle -> Negative
                    WorkerBridge.States.Listening -> Neutral1
                    WorkerBridge.States.JoiningWork -> Neutral1
                    WorkerBridge.States.Working -> Positive
                    WorkerBridge.States.LeavingWork -> Neutral1
                }

                withStyle(SpanStyle(color = statusColor)) {
                    append(state.toString())
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun MasterDetails(connectedTo: Master, modifier: Modifier = Modifier){
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)){
                withStyle(SpanStyle(color = Positive, fontSize = TextUnit(22f, TextUnitType.Sp))) {
                    appendLine("Connected to Master")
                }
                append("${connectedTo.name}: ")
            }
            append((connectedTo.addr as InetSocketAddress).hostString)
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            lineHeight = TextUnit(28f, TextUnitType.Sp)
        ),
        modifier = modifier
    )
}

@Composable
fun NetworkText(modifier: Modifier = Modifier){
    val ipAddr = getIpAddress()
    Text(
        text = buildAnnotatedString {
            ipAddr.forEachIndexed { i, it ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)){
                    append(it)
                }
                if(i!=(ipAddr.size-1))
                    append(", ")
            }
            append(
                when(ipAddr.size){
                    0 -> "Not in an network"
                    1 -> " is the Ip Address\n of this worker"
                    else -> " are the Ip Addresses\n of this worker"
                }
            )
        },
        style = MaterialTheme.typography.bodySmall.copy(
            textAlign = TextAlign.Center
        ),
        modifier = modifier
    )
}


data class LogMsg(val from:String, val msg:String){
    val timeStamp = System.currentTimeMillis()
}
val timeFormat = SimpleDateFormat("hh:mma", Locale.US)

@Composable
fun LogMessage(message:LogMsg, modifier: Modifier=Modifier){
    Row(modifier = modifier){
        Text(
            text = buildAnnotatedString {
                append(
                    timeFormat.format(
                        Date(message.timeStamp)
                    )
                )
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("  ${message.from}   ")
                }
            },
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = message.msg,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun LogList(messages: List<LogMsg>, modifier: Modifier= Modifier){
    LazyColumn(modifier = modifier.fillMaxWidth()){
        items(messages){
            LogMessage(message = it)
            Spacer(modifier = Modifier.padding(top = 7.dp))
        }
    }
}


@Preview(showSystemUi = true)
@Composable
fun AppPreview(){
    var bridgeState by remember {
        mutableStateOf(WorkerBridge.States.Working)
    }
    val msgList = List(20){
        LogMsg("Control","Message for $it")
    }
    val connectedTo = Master(InetSocketAddress(3),"TestMaster",21)
    Control5Theme{
        MyApp(bridgeState, connectedTo, msgList){
            bridgeState = if(bridgeState== WorkerBridge.States.Idle)
                WorkerBridge.States.Working
            else WorkerBridge.States.Idle
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LogPreview(){
    Control5Theme{
        val msgList = listOf(
            LogMsg("Control", "Master requests permission for Voice"),
            LogMsg("Control", "Got beat from the Master"),
            LogMsg("Cont", "Got beat from the Master"),
            LogMsg("Control", "Got beat from the Master")
        )
        LogList(messages = msgList)
//        LogMessage(message = LogMsg("Control", "Got beat from the Master"))
    }
}