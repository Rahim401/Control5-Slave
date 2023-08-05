package com.S2Bytes.control5

import java.net.DatagramPacket
import java.net.SocketAddress
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



fun ByteArray.getShort(idx:Int=0):Short =
    get(idx).toInt().shl(8)
        .or(get(idx+1).toInt())
        .toShort()

fun ByteArray.getInt(idx:Int=0):Int =
    get(idx).toInt().shl(24)
        .or(get(idx+1).toInt().shl(16))
        .or(get(idx+2).toInt().shl(8))
        .or(get(idx).toInt())

fun ByteArray.getLong(idx:Int=0):Long =
    get(idx).toLong().shl(56)
        .or(get(idx+1).toLong().shl(48))
        .or(get(idx+2).toLong().shl(40))
        .or(get(idx+2).toLong().shl(32))
        .or(get(idx+2).toLong().shl(24))
        .or(get(idx+2).toLong().shl(16))
        .or(get(idx+2).toLong().shl(8))
        .or(get(idx).toLong())

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
            if(data.getShort().toInt()!=8)
                return null
            return Master(
                socketAddress,
                "Test",
                5
            )
        }
    }

}
