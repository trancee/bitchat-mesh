package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class BitchatMessageTests {
    @Test
    fun toBinaryPayloadRoundTripWithOptionalFields() {
        val timestamp = Date(1_700_000_000_000L)
        val message = BitchatMessage(
            id = "msg-1",
            sender = "alice",
            content = "hello",
            type = BitchatMessageType.Message,
            timestamp = timestamp,
            isRelay = true,
            originalSender = "bob",
            isPrivate = true,
            recipientNickname = "carol",
            senderPeerID = "peer-123",
            mentions = listOf("dave", "erin"),
            channel = "general",
            encryptedContent = null,
            isEncrypted = false
        )

        val encoded = message.toBinaryPayload()
        assertNotNull(encoded)

        val decoded = BitchatMessage.fromBinaryPayload(encoded!!)
        assertNotNull(decoded)
        assertEquals(message.id, decoded!!.id)
        assertEquals(message.sender, decoded.sender)
        assertEquals(message.content, decoded.content)
        assertEquals(message.timestamp, decoded.timestamp)
        assertTrue(decoded.isRelay)
        assertTrue(decoded.isPrivate)
        assertEquals(message.originalSender, decoded.originalSender)
        assertEquals(message.recipientNickname, decoded.recipientNickname)
        assertEquals(message.senderPeerID, decoded.senderPeerID)
        assertEquals(message.mentions, decoded.mentions)
        assertEquals(message.channel, decoded.channel)
    }

    @Test
    fun toBinaryPayloadRoundTripEncrypted() {
        val timestamp = Date(1_700_000_000_123L)
        val encrypted = byteArrayOf(0x01, 0x02, 0x03)
        val message = BitchatMessage(
            id = "msg-2",
            sender = "alice",
            content = "",
            type = BitchatMessageType.Message,
            timestamp = timestamp,
            isRelay = false,
            originalSender = null,
            isPrivate = true,
            recipientNickname = null,
            senderPeerID = null,
            mentions = null,
            channel = null,
            encryptedContent = encrypted,
            isEncrypted = true
        )

        val encoded = message.toBinaryPayload()
        assertNotNull(encoded)

        val decoded = BitchatMessage.fromBinaryPayload(encoded!!)
        assertNotNull(decoded)
        assertTrue(decoded!!.isEncrypted)
        assertArrayEquals(encrypted, decoded.encryptedContent)
        assertEquals("", decoded.content)
    }

    @Test
    fun fromBinaryPayloadRejectsTruncatedData() {
        val data = ByteArray(12)
        assertNull(BitchatMessage.fromBinaryPayload(data))
    }

    @Test
    fun deliveryStatusDisplayText() {
        val delivered = DeliveryStatus.Delivered("alice", Date(0))
        val read = DeliveryStatus.Read("bob", Date(0))
        val failed = DeliveryStatus.Failed("oops")
        val partial = DeliveryStatus.PartiallyDelivered(1, 2)

        assertEquals("Sending...", DeliveryStatus.Sending.getDisplayText())
        assertEquals("Sent", DeliveryStatus.Sent.getDisplayText())
        assertEquals("Delivered to alice", delivered.getDisplayText())
        assertEquals("Read by bob", read.getDisplayText())
        assertEquals("Failed: oops", failed.getDisplayText())
        assertEquals("Delivered to 1/2", partial.getDisplayText())
    }
}
