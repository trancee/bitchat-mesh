package com.bitchat.android.model

import com.bitchat.android.util.FileTransferLimits
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

        fun appendUInt32(value: Long) {
            data.add(((value shr 24) and 0xFF).toByte())
            data.add(((value shr 16) and 0xFF).toByte())
            data.add(((value shr 8) and 0xFF).toByte())
            data.add((value and 0xFF).toByte())
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }

    @Test
    fun testBitchatFilePacketEncodeDecodeRoundTrip() {
        val packet = BitchatFilePacket(
            fileName = "note.txt",
            fileSize = null,
            mimeType = "text/plain",
            content = "hello".toByteArray(Charsets.UTF_8)
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = BitchatFilePacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.fileName, decoded!!.fileName)
        assertEquals(packet.content.size.toLong(), decoded.fileSize)
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
    fun testBitchatFilePacketDecodeRejectsOversizedFileSize() {
        val builder = ByteArrayBuilder()
        builder.append(0x02)
        builder.appendUInt16(4)
        val oversized = FileTransferLimits.maxPayloadBytes + 1L
        builder.appendUInt32(oversized)
        builder.append(0x04)
        builder.appendUInt32(1)
        builder.append(0x00)

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
    fun testBitchatFilePacketDecodeSupportsLegacyLengths() {
        val builder = ByteArrayBuilder()
        builder.append(0x02)
        builder.appendUInt16(8)
        builder.appendBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02))
        builder.append(0x04)
        builder.appendUInt16(2)
        builder.appendBytes(byteArrayOf(0x68, 0x69))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals(2L, decoded!!.fileSize)
        assertArrayEquals(byteArrayOf(0x68, 0x69), decoded.content)
    }

    @Test
    fun testBitchatFilePacketEncodeRejectsOversizedFileSize() {
        val packet = BitchatFilePacket(
            fileName = "too-big.bin",
            fileSize = (FileTransferLimits.maxPayloadBytes + 1).toLong(),
            mimeType = "application/octet-stream",
            content = byteArrayOf(0x00)
        )

        assertNull(packet.encode())
    }

    @Test
    fun testBitchatFilePacketEncodeRejectsOversizedContent() {
        val packet = BitchatFilePacket(
            fileName = "too-big.bin",
            fileSize = null,
            mimeType = "application/octet-stream",
            content = ByteArray(FileTransferLimits.maxPayloadBytes + 1)
        )

        assertNull(packet.encode())
    }

    @Test
    fun testBitchatFilePacketEncodeAcceptsMaxLengthFileNameAndMimeType() {
        val maxName = "a".repeat(UShort.MAX_VALUE.toInt())
        val maxMime = "b".repeat(UShort.MAX_VALUE.toInt())
        val packet = BitchatFilePacket(
            fileName = maxName,
            fileSize = null,
            mimeType = maxMime,
            content = byteArrayOf(0x01)
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = BitchatFilePacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(maxName, decoded!!.fileName)
        assertEquals(maxMime, decoded.mimeType)
    }

    @Test
    fun testBitchatFilePacketEncodeOmitsTooLongOptionalFields() {
        val tooLong = "c".repeat(UShort.MAX_VALUE.toInt() + 1)
        val packet = BitchatFilePacket(
            fileName = tooLong,
            fileSize = null,
            mimeType = tooLong,
            content = byteArrayOf(0x02)
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = BitchatFilePacket.decode(encoded!!)
        assertNotNull(decoded)

        assertNull(decoded!!.fileName)
        assertNull(decoded.mimeType)
        assertArrayEquals(byteArrayOf(0x02), decoded.content)
    }

    @Test
    fun testBitchatFilePacketDecodeHandlesMixedTLVsAndSplitContent() {
        val builder = ByteArrayBuilder()
        builder.append(0x04)
        builder.appendUInt32(2)
        builder.appendBytes(byteArrayOf(0x68, 0x65))
        builder.append(0x01)
        builder.appendUInt16(8)
        builder.appendBytes("note.txt".toByteArray(Charsets.UTF_8))
        builder.append(0x03)
        builder.appendUInt16(10)
        builder.appendBytes("text/plain".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.appendUInt16(4)
        builder.appendBytes(byteArrayOf(0x00, 0x00, 0x00, 0x05))
        builder.append(0x04)
        builder.appendUInt32(3)
        builder.appendBytes(byteArrayOf(0x6C, 0x6C, 0x6F))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)

        assertEquals("note.txt", decoded!!.fileName)
        assertEquals("text/plain", decoded.mimeType)
        assertEquals(5L, decoded.fileSize)
        assertArrayEquals("hello".toByteArray(Charsets.UTF_8), decoded.content)
    }

    @Test
    fun testBitchatFilePacketDecodeHandlesOutOfOrderOptionalTLVs() {
        val builder = ByteArrayBuilder()
        builder.append(0x03)
        builder.appendUInt16(10)
        builder.appendBytes("text/plain".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.appendUInt16(8)
        builder.appendBytes("note.txt".toByteArray(Charsets.UTF_8))
        builder.append(0x04)
        builder.appendUInt32(3)
        builder.appendBytes(byteArrayOf(0x66, 0x6F, 0x6F))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)

        assertEquals("note.txt", decoded!!.fileName)
        assertEquals("text/plain", decoded.mimeType)
        assertArrayEquals(byteArrayOf(0x66, 0x6F, 0x6F), decoded.content)
    }

    @Test
    fun testBitchatFilePacketDecodeSkipsUnknownTLVs() {
        val builder = ByteArrayBuilder()
        builder.append(0x7F)
        builder.appendUInt16(2)
        builder.appendBytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        builder.append(0x04)
        builder.appendUInt32(3)
        builder.appendBytes(byteArrayOf(0x62, 0x61, 0x72))

        val decoded = BitchatFilePacket.decode(builder.toByteArray())
        assertNotNull(decoded)

        assertNull(decoded!!.fileName)
        assertNull(decoded.mimeType)
        assertArrayEquals("bar".toByteArray(Charsets.UTF_8), decoded.content)
    }
}
