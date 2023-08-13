package com.S2Bytes.control5

class CBuffer(val arr:ByteArray) {
    val capacity = arr.size

    private var readPos = 0
        set(value) {
            if(field==value)
                return
            field = value%capacity
        }

    private var writePos = 0
        set(value) {
            if(field==value)
                return
            field = value%capacity
        }

    fun readFrom(pos:Int){ readPos = pos }
    fun writeFrom(pos:Int){ writePos = pos }

    private fun writeAtPos(at:Int, toDo:()->Unit){
        val pos = writePos
        writePos = at
        toDo()
        writePos = pos
    }

    private fun readAtPos(at:Int, toDo:()->Any){
        val pos = writePos
        writePos = at
        toDo()
        writePos = pos
    }

    fun putByte(value:Byte){ arr[writePos++] = value }
    fun putByte(value:Byte, at:Int) = writeAtPos(at){ putByte(value) }

    fun putBytes(vararg values:Byte) = values.forEach { putByte(it) }
    fun putBytes(vararg values:Byte, at:Int) = writeAtPos(at){ putBytes(*values) }

    fun putShort(value:Short){
        putByte((value.toInt() shr 8).toByte())
        putByte(value.toByte())
    }
    fun putShort(value:Short, at:Int) = writeAtPos(at){ putShort(value); }

    fun putInt(value:Int){
        putByte((value shr 24).toByte())
        putByte((value shr 16).toByte())
        putByte((value shr 8).toByte())
        putByte(value.toByte())
    }
    fun putInt(value:Int, at:Int) = writeAtPos(at){ putInt(value) }

    fun putLong(value:Long){
        putByte((value shr 56).toByte())
        putByte((value shr 48).toByte())
        putByte((value shr 40).toByte())
        putByte((value shr 32).toByte())
        putByte((value shr 24).toByte())
        putByte((value shr 16).toByte())
        putByte((value shr 8).toByte())
        putByte(value.toByte())
    }
    fun putLong(value:Long, at:Int) = writeAtPos(at){ putLong(value) }

    fun putByteArray(array:ByteArray) = array.forEach { putByte(it) }
    fun putByteArray(array:ByteArray, at:Int) = writeAtPos(at){ putByteArray(array) }

    fun putString(value:String){
        val encArr = value.encodeToByteArray()
        putByte(encArr.size.toByte())
        putByteArray(encArr)
    }


    fun getByte() = arr[readPos++]
    fun getShort() =
        getByte().toInt().shl(8)
            .or(getByte().toInt())
            .toShort()
    fun getInt() =
        getByte().toInt().shl(24)
            .or(getByte().toInt().shl(16))
            .or(getByte().toInt().shl(8))
            .or(getByte().toInt())
    fun getLong() =
        getByte().toInt().shl(56)
            .or(getByte().toInt().shl(48))
            .or(getByte().toInt().shl(40))
            .or(getByte().toInt().shl(32))
            .or(getByte().toInt().shl(24))
            .or(getByte().toInt().shl(16))
            .or(getByte().toInt().shl(8))
            .or(getByte().toInt())
    fun getString():String{
        val size = getByte().toInt()
        return ByteArray(size){
            getByte()
        }.decodeToString()
    }
}