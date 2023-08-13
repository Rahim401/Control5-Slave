package com.S2Bytes.control5

import org.junit.Test

class UtilsKtTest {

    @Test
    fun setEle() {
        val testArray = byteArrayOf(1,4,73,62,1,6,1,8)
//        testArray.put(3,64,3,2)
        assert(testArray.contentEquals(byteArrayOf(3,64,3,2,1,6,1,8))){
            "${testArray.toList()} is wrong"
        }

//        testArray.put(3,64,3,2, from = 5)
        assert(testArray.contentEquals(byteArrayOf(3,64,3,2,1,3,64,3))){
            "${testArray.toList()} is wrong"
        }
    }

    @Test
    fun getShort() {
        val testArray = byteArrayOf(1,4,73,62,1,6,-1,8)
        assert(testArray.getShort(0)==260.toShort()){
            "${testArray.getShort(0)} is wrong"
        }
        assert(testArray.getShort(6)==(-248).toShort()){
            "${testArray.getShort(6)} is wrong"
        }
    }

    @Test
    fun putTest() {
        val testArray = byteArrayOf(0,0,0,0,0,0,0,0)
//        testArray.put(0,-1)
        assert(testArray.getShort()==(-1).toShort())
//        testArray.put(0,-1)
        assert(testArray.getInt()==-1)
//        testArray.put(0,-1)
        assert(testArray.getLong()==-1L)
//        testArray.put(0,"Hello bro how are you")
        println(testArray.getString())
        assert(testArray.getString(0)=="Hello")
    }
}