package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TLVPacketTests {
    private class ByteArrayBuilder {
        private val data = ArrayList<Byte>()

        fun append(value: Int) = data.add(value.toByte())
        fun appendBytes(bytes: ByteArray) = data.addAll(bytes.toList())
        fun toByteArray(): ByteArray = data.toByteArray()
    }

    private fun assertByteArrayListEquals(expected: List<ByteArray>?, actual: List<ByteArray>?) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual)
            return
        }
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { idx ->
            assertArrayEquals(expected[idx], actual[idx])
        }
    }

    @Test
    fun testAnnouncementPacketEncodeDecodeRoundTrip() {
        val packet = AnnouncementPacket(
            nickname = "Alice",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            directNeighbors = listOf(
                ByteArray(8) { 0xAA.toByte() },
                ByteArray(8) { 0xBB.toByte() }
            )
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = AnnouncementPacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.nickname, decoded!!.nickname)
        assertArrayEquals(packet.noisePublicKey, decoded.noisePublicKey)
        assertArrayEquals(packet.signingPublicKey, decoded.signingPublicKey)
        assertByteArrayListEquals(packet.directNeighbors, decoded.directNeighbors)
    }

    @Test
    fun testAnnouncementPacketEncodeRejectsLongNickname() {
        val nickname = "a".repeat(256)
        val packet = AnnouncementPacket(
            nickname = nickname,
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            directNeighbors = null
        )

        assertNull(packet.encode())
    }

    @Test
    fun testAnnouncementPacketEncodeRejectsEmptyNickname() {
        val packet = AnnouncementPacket(
            nickname = "",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            directNeighbors = null
        )

        assertNull(packet.encode())
    }

    @Test
    fun testAnnouncementPacketTruncatesNeighborsToLimit() {
        val neighbors = (0 until 12).map { i -> ByteArray(8) { (i + 1).toByte() } }
        val packet = AnnouncementPacket(
            nickname = "Bob",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            directNeighbors = neighbors
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = AnnouncementPacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(10, decoded!!.directNeighbors?.size)
        assertByteArrayListEquals(neighbors.take(10), decoded.directNeighbors)
    }

    @Test
    fun testAnnouncementPacketOmitsEmptyNeighbors() {
        val packet = AnnouncementPacket(
            nickname = "Eve",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            directNeighbors = emptyList()
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = AnnouncementPacket.decode(encoded!!)
        assertNotNull(decoded)

        assertNull(decoded!!.directNeighbors)
    }

    @Test
    fun testPrivateMessagePacketEncodeDecodeRoundTrip() {
        val packet = PrivateMessagePacket(messageID = "msg-1", content = "hello")
        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = PrivateMessagePacket.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.messageID, decoded!!.messageID)
        assertEquals(packet.content, decoded.content)
    }

    @Test
    fun testPrivateMessagePacketDecodeRejectsUnknownTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x99)
        builder.append(0x01)
        builder.append(0x00)

        assertNull(PrivateMessagePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testAnnouncementPacketDecodeRejectsMalformedLength() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes(byteArrayOf(0x41, 0x42))

        assertNull(AnnouncementPacket.decode(builder.toByteArray()))
    }

    @Test
    fun testAnnouncementPacketDecodeSkipsUnknownTLVs() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Bob".toByteArray(Charsets.UTF_8))
        builder.append(0x7F)
        builder.append(0x02)
        builder.appendBytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.nickname)
        assertArrayEquals(ByteArray(32) { 0x11 }, decoded.noisePublicKey)
        assertArrayEquals(ByteArray(32) { 0x22 }, decoded.signingPublicKey)
    }

    @Test
    fun testAnnouncementPacketDecodeSkipsUnknownZeroLengthTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Bob".toByteArray(Charsets.UTF_8))
        builder.append(0x7F)
        builder.append(0x00)
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.nickname)
        assertArrayEquals(ByteArray(32) { 0x11 }, decoded.noisePublicKey)
        assertArrayEquals(ByteArray(32) { 0x22 }, decoded.signingPublicKey)
    }

    @Test
    fun testAnnouncementPacketDecodeSkipsUnknownTrailingTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Bob".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })
        builder.append(0x7F)
        builder.append(0x02)
        builder.appendBytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.nickname)
        assertArrayEquals(ByteArray(32) { 0x11 }, decoded.noisePublicKey)
        assertArrayEquals(ByteArray(32) { 0x22 }, decoded.signingPublicKey)
    }

    @Test
    fun testAnnouncementPacketDecodeIgnoresInvalidNeighborLength() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })
        builder.append(0x04)
        builder.append(0x07)
        builder.appendBytes(ByteArray(7) { 0xFF.toByte() })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertNull(decoded!!.directNeighbors)
    }

    @Test
    fun testAnnouncementPacketDecodeUsesLastDuplicateTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Old".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("New".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("New", decoded!!.nickname)
    }

    @Test
    fun testAnnouncementPacketDecodeUsesLastDuplicateKeys() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x33 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x44 })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertArrayEquals(ByteArray(32) { 0x33 }, decoded!!.noisePublicKey)
        assertArrayEquals(ByteArray(32) { 0x44 }, decoded.signingPublicKey)
    }

    @Test
    fun testAnnouncementPacketDecodeUsesLastDuplicateNeighbors() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })
        builder.append(0x04)
        builder.append(0x08)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.append(0x04)
        builder.append(0x10)
        builder.appendBytes(ByteArray(16) { 0xBB.toByte() })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        val expected = listOf(ByteArray(8) { 0xBB.toByte() }, ByteArray(8) { 0xBB.toByte() })
        assertByteArrayListEquals(expected, decoded!!.directNeighbors)
    }

    @Test
    fun testAnnouncementPacketDecodeKeepsNeighborsWhenDuplicateEmpty() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })
        builder.append(0x04)
        builder.append(0x08)
        builder.appendBytes(ByteArray(8) { 0xAA.toByte() })
        builder.append(0x04)
        builder.append(0x00)

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        val expected = listOf(ByteArray(8) { 0xAA.toByte() })
        assertByteArrayListEquals(expected, decoded!!.directNeighbors)
    }

    @Test
    fun testAnnouncementPacketDecodeIgnoresEmptyDuplicateTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.append(0x00)
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        val decoded = AnnouncementPacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("Ann", decoded!!.nickname)
    }

    @Test
    fun testAnnouncementPacketDecodeRejectsMissingNickname() {
        val builder = ByteArrayBuilder()
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        assertNull(AnnouncementPacket.decode(builder.toByteArray()))
    }

    @Test
    fun testAnnouncementPacketDecodeRejectsMissingNoisePublicKey() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x03)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x22 })

        assertNull(AnnouncementPacket.decode(builder.toByteArray()))
    }

    @Test
    fun testAnnouncementPacketDecodeRejectsMissingSigningPublicKey() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x03)
        builder.appendBytes("Ann".toByteArray(Charsets.UTF_8))
        builder.append(0x02)
        builder.append(0x20)
        builder.appendBytes(ByteArray(32) { 0x11 })

        assertNull(AnnouncementPacket.decode(builder.toByteArray()))
    }

    @Test
    fun testAnnouncementPacketEncodeRejectsEmptyKeys() {
        val packet = AnnouncementPacket(
            nickname = "Ann",
            noisePublicKey = ByteArray(0),
            signingPublicKey = ByteArray(0),
            directNeighbors = null
        )

        assertNull(packet.encode())
    }

    @Test
    fun testPrivateMessagePacketDecodeRejectsMalformedLength() {
        val builder = ByteArrayBuilder()
        builder.append(0x00)
        builder.append(0x04)
        builder.appendBytes(byteArrayOf(0x6D, 0x73))

        assertNull(PrivateMessagePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testPrivateMessagePacketDecodeRejectsMalformedUtf8() {
        val builder = ByteArrayBuilder()
        builder.append(0x00)
        builder.append(0x02)
        builder.appendBytes(byteArrayOf(0xC3.toByte(), 0x28))
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes("hello".toByteArray(Charsets.UTF_8))

        assertNull(PrivateMessagePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testPrivateMessagePacketDecodeUsesLastDuplicateTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x00)
        builder.append(0x05)
        builder.appendBytes("first".toByteArray(Charsets.UTF_8))
        builder.append(0x00)
        builder.append(0x06)
        builder.appendBytes("second".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes("hello".toByteArray(Charsets.UTF_8))

        val decoded = PrivateMessagePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("second", decoded!!.messageID)
        assertEquals("hello", decoded.content)
    }

    @Test
    fun testPrivateMessagePacketDecodeUsesLastDuplicateContentTLV() {
        val builder = ByteArrayBuilder()
        builder.append(0x00)
        builder.append(0x05)
        builder.appendBytes("msg-1".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes("first".toByteArray(Charsets.UTF_8))
        builder.append(0x01)
        builder.append(0x06)
        builder.appendBytes("second".toByteArray(Charsets.UTF_8))

        val decoded = PrivateMessagePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("second", decoded!!.content)
    }

    @Test
    fun testPrivateMessagePacketDecodeHandlesOutOfOrderTLVs() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes("hello".toByteArray(Charsets.UTF_8))
        builder.append(0x00)
        builder.append(0x05)
        builder.appendBytes("msg-1".toByteArray(Charsets.UTF_8))

        val decoded = PrivateMessagePacket.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("msg-1", decoded!!.messageID)
        assertEquals("hello", decoded.content)
    }

    @Test
    fun testPrivateMessagePacketDecodeRejectsMissingMessageID() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes("hello".toByteArray(Charsets.UTF_8))

        assertNull(PrivateMessagePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testPrivateMessagePacketDecodeRejectsMissingContent() {
        val builder = ByteArrayBuilder()
        builder.append(0x00)
        builder.append(0x05)
        builder.appendBytes("msg-1".toByteArray(Charsets.UTF_8))

        assertNull(PrivateMessagePacket.decode(builder.toByteArray()))
    }

    @Test
    fun testPrivateMessagePacketEncodeRejectsEmptyMessageID() {
        val packet = PrivateMessagePacket(messageID = "", content = "hello")
        assertNull(packet.encode())
    }

    @Test
    fun testPrivateMessagePacketEncodeRejectsEmptyContent() {
        val packet = PrivateMessagePacket(messageID = "msg-1", content = "")
        assertNull(packet.encode())
    }
}
