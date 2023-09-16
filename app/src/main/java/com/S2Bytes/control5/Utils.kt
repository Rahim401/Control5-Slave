package com.S2Bytes.control5

import java.io.InputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.Enumeration
import kotlin.math.min


fun Byte.toPInt() = toInt() and 0xFF

fun ByteArray.putBytes(vararg vls:Byte, from:Int=0){
    val loopTill = size-from
    vls.forEachIndexed { index, byte ->
        if(index>=loopTill)
            return@forEachIndexed
        set(from+index, byte)
    }
}

fun ByteArray.putShort(idx:Int=0, value: Short){
    set(idx,(value.toInt() shr 8).toByte())
    set(idx+1,value.toByte())
}
fun ByteArray.putInt(idx:Int=0, value: Int){
    set(idx,(value shr 24).toByte())
    set(idx+1,(value shr 16).toByte())
    set(idx+2,(value shr 8).toByte())
    set(idx+3,value.toByte())
}
fun ByteArray.putLong(idx:Int=0, value: Long){
    set(idx,(value shr 56).toByte())
    set(idx+1,(value shr 48).toByte())
    set(idx+2,(value shr 40).toByte())
    set(idx+3,(value shr 32).toByte())
    set(idx+4,(value shr 24).toByte())
    set(idx+5,(value shr 16).toByte())
    set(idx+6,(value shr 8).toByte())
    set(idx+7,value.toByte())
}
fun ByteArray.putString(idx:Int=0, value: String, len:Int=value.length){
    value.encodeToByteArray()
        .copyInto(this,idx,0,len)
}
fun ByteArray.putBString(idx:Int=0, value: String, maxLen:Int=value.length):Byte{
    val len = min(maxLen,size-idx-1).toByte()
    set(idx,len)
    putString(idx+1,value,len.toInt())
    return len
}
fun ByteArray.putSString(idx:Int=0, value: String, maxLen:Int=value.length):Short{
    val len = min(maxLen,size-idx-2).toShort()
    putShort(idx,len)
    putString(idx+2,value,len.toInt())
    return len
}
fun ByteArray.pad(idx: Int=0,length:Int){
    for(i in idx until (idx+length))
        set(i,0)
}


fun ByteArray.getBInt(idx: Int) = get(idx).toInt().and(0xFF)
fun ByteArray.getBLong(idx: Int) = get(idx).toLong().and(0xFF)
fun ByteArray.getShort(idx:Int=0):Short =
    getBInt(idx).shl(8)
        .or(getBInt(idx+1))
        .toShort()

fun ByteArray.getInt(idx:Int=0):Int =
    getBInt(idx).shl(24)
        .or(getBInt(idx+1).shl(16))
        .or(getBInt(idx+2).shl(8))
        .or(getBInt(idx+3))

fun ByteArray.getLong(idx:Int=0):Long =
    getBLong(idx).shl(56)
        .or(getBLong(idx+1).shl(48))
        .or(getBLong(idx+2).shl(40))
        .or(getBLong(idx+3).shl(32))
        .or(getBLong(idx+4).shl(24))
        .or(getBLong(idx+5).shl(16))
        .or(getBLong(idx+6).shl(8))
        .or(getBLong(idx+7))

fun ByteArray.getString(idx:Int=0,length: Int=size)
    = decodeToString(idx,idx+length)

data class Master(
    val addr:SocketAddress,
    val name:String,
    val version:Int
){
    fun getSockAddress() = addr

    companion object{
        fun DatagramPacket.getMaster():Master?{
            if(data.getShort()!=ConnectRequestTaskId || data[3]!=0.toByte())
                return null
            return Master(
                socketAddress,
                "RahimsComp",
                5
            )
        }
    }

}

fun getIpAddress(): List<String>{
    val ipAddresses = mutableListOf<String>()
    try {
        val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intF: NetworkInterface = en.nextElement()
            if (!intF.isLoopback) {
                for (addr in intF.interfaceAddresses) {
                    if(addr.broadcast!=null && addr.address is Inet4Address)
                        ipAddresses.add(addr.address.hostAddress ?: "0.0.0.0")
                }
            }
        }
    } catch (e: SocketException) {
        e.printStackTrace()
    }
    return ipAddresses
}

fun InputStream.readArray(size:Int):ByteArray {
    val retArr = ByteArray(size)
    var readBytes = 0

    while (readBytes<size) {
        readBytes += read(
            retArr,
            readBytes,
            size-readBytes
        )
    }
    return retArr
}

fun ByteBuffer.putUTF(str:String){
    val strArray = str.encodeToByteArray()
    putShort(strArray.size.toShort())
    put(strArray)
}