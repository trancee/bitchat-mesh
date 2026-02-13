package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoiseEncryptedTests {
    @Test
    fun noisePayloadEncodeDecodeRoundTrip() {
        val payload = NoisePayload(
            type = NoisePayloadType.PRIVATE_MESSAGE,
            data = "secret".toByteArray(Charsets.UTF_8)
        )

        val encoded = payload.encode()
        val decoded = NoisePayload.decode(encoded)
        assertNotNull(decoded)
        assertEquals(payload.type, decoded!!.type)
        assertArrayEquals(payload.data, decoded.data)
    }

    @Test
    fun noisePayloadDecodeRejectsUnknownType() {
        val encoded = byteArrayOf(0x7F.toByte(), 0x01, 0x02)
        assertNull(NoisePayload.decode(encoded))
    }

    @Test
    fun privateMessagePacketEncodeDecodeRoundTrip() {
        val packet = PrivateMessagePacket("msg-1", "hello")
        val encoded = packet.encode()
        assertNotNull(encoded)

        val decoded = PrivateMessagePacket.decode(encoded!!)
        assertNotNull(decoded)
        assertEquals(packet.messageID, decoded!!.messageID)
        assertEquals(packet.content, decoded.content)
    }

    @Test
    fun privateMessagePacketEncodeRejectsOversizedFields() {
        val messageId = "a".repeat(256)
        val content = "b".repeat(10)
        assertNull(PrivateMessagePacket(messageId, content).encode())

        val contentTooLong = "c".repeat(256)
        assertNull(PrivateMessagePacket("ok", contentTooLong).encode())
    }

    @Test
    fun privateMessagePacketDecodeRejectsInvalidTlv() {
        val invalidType = byteArrayOf(0x05, 0x01, 0x41)
        assertNull(PrivateMessagePacket.decode(invalidType))

        val truncated = byteArrayOf(0x00, 0x05, 0x41)
        assertNull(PrivateMessagePacket.decode(truncated))

        val missingContent = byteArrayOf(0x00, 0x02, 0x41, 0x42)
        assertNull(PrivateMessagePacket.decode(missingContent))
    }
}
