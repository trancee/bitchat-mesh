package com.bitchat.android.protocol

import com.bitchat.android.util.AppConstants
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitchatPacketTests {
    @Test
    fun equalsAndHashCodeConsiderRouteAndRecipient() {
        val sender = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val recipient = byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17)
        val route = listOf(byteArrayOf(0x20, 0x21), byteArrayOf(0x30, 0x31))

        val packetA = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = sender,
            recipientID = recipient,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 2u,
            route = route
        )
        val packetB = packetA.copy()
        val packetC = packetA.copy(route = listOf(byteArrayOf(0x20, 0x21)))

        assertEquals(packetA, packetB)
        assertEquals(packetA.hashCode(), packetB.hashCode())
        assertFalse(packetA == packetC)
    }

    @Test
    fun toBinaryDataForSigningUsesSyncTtl() {
        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            recipientID = null,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 5u,
            route = null
        )

        val encoded = packet.toBinaryDataForSigning()
        val decoded = BinaryProtocol.decode(encoded!!)

        assertEquals(AppConstants.SYNC_TTL_HOPS, decoded?.ttl)
    }

    @Test
    fun stringConstructorEncodesSenderId() {
        val packet = BitchatPacket(
            type = MessageType.MESSAGE.value,
            ttl = 1u,
            senderID = "0102030405060708",
            payload = byteArrayOf(0x01)
        )

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08), packet.senderID)
    }
}
