package com.bitchat.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ByteArrayWrapperTests {
    @Test
    fun equalsAndHashCodeUseContent() {
        val a1 = ByteArrayWrapper(byteArrayOf(0x01, 0x02))
        val a2 = ByteArrayWrapper(byteArrayOf(0x01, 0x02))
        val b = ByteArrayWrapper(byteArrayOf(0x02, 0x03))

        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
        assertNotEquals(a1, b)
    }

    @Test
    fun toHexStringFormatsBytes() {
        val wrapper = ByteArrayWrapper(byteArrayOf(0x0A, 0x0B, 0x0C))
        assertEquals("0a0b0c", wrapper.toHexString())
        assertTrue(wrapper.toHexString().matches(Regex("[0-9a-f]+")))
    }
}
