package com.bitchat.android.noise.southernstorm.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ChaChaCoreTests {
    @Test
    fun initKey128SetsConstants() {
        val output = IntArray(16)
        val key = ByteArray(16) { it.toByte() }

        ChaChaCore.initKey128(output, key, 0)

        assertEquals(0x61707865, output[0]) // "expa"
        assertEquals(0x3120646e, output[1]) // "nd 1"
    }

    @Test
    fun initKey256SetsConstants() {
        val output = IntArray(16)
        val key = ByteArray(32) { it.toByte() }

        ChaChaCore.initKey256(output, key, 0)

        assertEquals(0x61707865, output[0]) // "expa"
        assertEquals(0x3320646e, output[1]) // "nd 3"
    }

    @Test
    fun hashProducesNonTrivialOutput() {
        val input = IntArray(16) { it }
        val output = IntArray(16)

        ChaChaCore.hash(output, input)

        assertNotEquals(input.toList(), output.toList())
    }
}
