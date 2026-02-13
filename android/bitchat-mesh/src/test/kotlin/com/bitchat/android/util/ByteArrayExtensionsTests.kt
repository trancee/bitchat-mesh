package com.bitchat.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteArrayExtensionsTests {
    @Test
    fun toHexStringFormatsBytes() {
        val data = byteArrayOf(0x00, 0x0F, 0x10, 0x7F)

        assertEquals("000f107f", data.toHexString())
    }
}
