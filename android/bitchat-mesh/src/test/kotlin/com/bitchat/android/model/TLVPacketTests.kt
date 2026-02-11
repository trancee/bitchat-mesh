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

    @Test
    fun testIdentityAnnouncementEncodeDecodeRoundTrip() {
        val packet = IdentityAnnouncement(
            nickname = "Alice",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 }
        )

        val encoded = packet.encode()
        assertNotNull(encoded)
        val decoded = IdentityAnnouncement.decode(encoded!!)
        assertNotNull(decoded)

        assertEquals(packet.nickname, decoded!!.nickname)
        assertArrayEquals(packet.noisePublicKey, decoded.noisePublicKey)
        assertArrayEquals(packet.signingPublicKey, decoded.signingPublicKey)
    }

    @Test
    fun testIdentityAnnouncementEncodeRejectsOversizedFields() {
        val nickname = "a".repeat(256)
        val packet = IdentityAnnouncement(
            nickname = nickname,
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 }
        )

        assertNull(packet.encode())
    }

    @Test
    fun testIdentityAnnouncementDecodeSkipsUnknownTLVs() {
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

        val decoded = IdentityAnnouncement.decode(builder.toByteArray())
        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.nickname)
        assertArrayEquals(ByteArray(32) { 0x11 }, decoded.noisePublicKey)
        assertArrayEquals(ByteArray(32) { 0x22 }, decoded.signingPublicKey)
    }

    @Test
    fun testIdentityAnnouncementDecodeRejectsMalformedLength() {
        val builder = ByteArrayBuilder()
        builder.append(0x01)
        builder.append(0x05)
        builder.appendBytes(byteArrayOf(0x41, 0x42))

        assertNull(IdentityAnnouncement.decode(builder.toByteArray()))
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
}
