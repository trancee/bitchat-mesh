package com.bitchat.android.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompressionUtilTests {
    @Test
    fun shouldCompressRejectsSmallOrHighEntropyData() {
        val small = ByteArray(10) { 0x11 }
        assertFalse(CompressionUtil.shouldCompress(small))

        val highEntropy = ByteArray(200) { it.toByte() }
        assertFalse(CompressionUtil.shouldCompress(highEntropy))
    }

    @Test
    fun shouldCompressAcceptsLowEntropyData() {
        val lowEntropy = ByteArray(200) { 0x41 }
        assertTrue(CompressionUtil.shouldCompress(lowEntropy))
    }

    @Test
    fun compressReturnsNullWhenNotBeneficialOrTooSmall() {
        val small = ByteArray(10) { 0x22 }
        assertNull(CompressionUtil.compress(small))

        val randomLike = ByteArray(200) { it.toByte() }
        val compressed = CompressionUtil.compress(randomLike)
        if (compressed != null) {
            assertTrue(compressed.size < randomLike.size)
        }
    }

    @Test
    fun compressAndDecompressRoundTrip() {
        val data = "This should compress well. ".repeat(10).toByteArray(Charsets.UTF_8)
        val compressed = CompressionUtil.compress(data)
        assertNotNull(compressed)

        val decompressed = CompressionUtil.decompress(compressed!!, data.size)
        assertNotNull(decompressed)
        assertArrayEquals(data, decompressed)
    }

    @Test
    fun decompressReturnsNullForInvalidData() {
        val invalid = ByteArray(50) { 0x00 }
        val result = CompressionUtil.decompress(invalid, 100)
        assertNull(result)
    }
}
