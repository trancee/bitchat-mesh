package com.bitchat.android.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessagePaddingTests {
    @Test
    fun optimalBlockSizeSelectsSmallestFittingBlock() {
        assertEquals(256, MessagePadding.optimalBlockSize(1))
        assertEquals(256, MessagePadding.optimalBlockSize(240))
        assertEquals(512, MessagePadding.optimalBlockSize(241))
        assertEquals(2040, MessagePadding.optimalBlockSize(2040))
    }

    @Test
    fun optimalBlockSizeReturnsDataSizeForLargePayloads() {
        assertEquals(4096, MessagePadding.optimalBlockSize(4096))
        assertEquals(3000, MessagePadding.optimalBlockSize(3000))
    }

    @Test
    fun padAndUnpadRoundTripForValidPadding() {
        val data = "hello".toByteArray(Charsets.UTF_8)
        val padded = MessagePadding.pad(data, 16)
        assertEquals(16, padded.size)

        val unpadded = MessagePadding.unpad(padded)
        assertArrayEquals(data, unpadded)
    }

    @Test
    fun padReturnsOriginalWhenTargetIsTooSmallOrPadTooLarge() {
        val data = ByteArray(10) { 0x01 }
        assertArrayEquals(data, MessagePadding.pad(data, 5))
        assertArrayEquals(data, MessagePadding.pad(data, 400))
    }

    @Test
    fun unpadReturnsOriginalOnInvalidPadding() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val invalid = data + byteArrayOf(0x02, 0x03)
        val unpadded = MessagePadding.unpad(invalid)
        assertArrayEquals(invalid, unpadded)

        val empty = ByteArray(0)
        assertTrue(MessagePadding.unpad(empty).isEmpty())
    }
}
