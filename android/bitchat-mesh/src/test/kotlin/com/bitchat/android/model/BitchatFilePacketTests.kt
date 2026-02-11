package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BitchatFilePacketTests {
    private class ByteArrayBuilder {
        private val data = ArrayList<Byte>()

        fun append(value: Int) = data.add(value.toByte())
        fun appendBytes(bytes: ByteArray) = data.addAll(bytes.toList())

        fun appendUInt16(value: Int) {
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun appendUInt32(value: Int) {
            data.add(((value shr 24) and 0xFF).toByte())
            data.add(((value shr 16) and 0xFF).toByte())
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }

    @Test
    fun testBitchatFilePacketEncodeDecodeRoundTrip() {
        val content = "hello".toByteArray(Charsets.UTF_8)
        val packet = BitchatFilePacket(
            fileName = "note.txt",
            fileSize = content.size.toLong(),
            mimeType = "text/plain",
            content = content
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = BitchatFilePacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.fileName, decoded!!.fileName)
        assertEquals(packet.fileSize, decoded.fileSize)
        assertEquals(packet.mimeType, decoded.mimeType)
        assertArrayEquals(packet.content, decoded.content)
    }

    @Test
    fun testBitchatFilePacketDecodeRejectsMissingContent() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.appendUInt16(3)
        builder.appendBytes(byteArrayOf(0x66, 0x6F, 0x6F))

        assertNull(BitchatFilePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testBitchatFilePacketDecodeRejectsMalformedContentLength() {
        val builder = ByteArrayBuilder()
        builder.append(0x04)
        builder.appendUInt32(2)
        builder.append(0xAB)

        assertNull(BitchatFilePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testBitchatFilePacketEncodeRejectsTooLongNameOrMime() {
        val content = byteArrayOf(0x01)
        val tooLong = "a".repeat(0x10000)
        val nameTooLong = BitchatFilePacket(
            fileName = tooLong,
            fileSize = content.size.toLong(),
            mimeType = "text/plain",
            content = content
        )
        val mimeTooLong = BitchatFilePacket(
            fileName = "ok.txt",
            fileSize = content.size.toLong(),
            mimeType = tooLong,
            content = content
        )

        assertNull(nameTooLong.encode())
        assertNull(mimeTooLong.encode())
    }

    @Test
    fun testBitchatFilePacketDecodeDefaultsSizeAndMime() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.appendUInt16(8)
        builder.appendBytes("note.txt".toByteArray(Charsets.UTF_8))
        builder.append(0x04)
        builder.appendUInt32(2)
        builder.appendBytes(byteArrayOf(0x68, 0x69))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("note.txt", decoded!!.fileName)
        assertEquals(2L, decoded.fileSize)
        assertEquals("application/octet-stream", decoded.mimeType)
        assertArrayEquals(byteArrayOf(0x68, 0x69), decoded.content)
    }

    @Test
    fun testBitchatFilePacketDecodeConcatenatesContentTLVs() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.appendUInt16(8)
        builder.appendBytes("note.txt".toByteArray(Charsets.UTF_8))
        builder.append(0x04)
        builder.appendUInt32(1)
        builder.appendBytes(byteArrayOf(0x68))
        builder.append(0x04)
        builder.appendUInt32(1)
        builder.appendBytes(byteArrayOf(0x69))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertArrayEquals(byteArrayOf(0x68, 0x69), decoded!!.content)
    }

    @Test
    fun testBitchatFilePacketDecodeRejectsUnknownTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x7F)
        builder.appendUInt16(1)
        builder.append(0x00)

        assertNull(BitchatFilePacket.decode(builder.toByteArray()))
    }
}
