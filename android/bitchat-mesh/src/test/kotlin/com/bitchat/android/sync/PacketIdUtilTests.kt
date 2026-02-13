package com.bitchat.android.sync

import com.bitchat.android.protocol.BitchatPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PacketIdUtilTests {
    private fun basePacket(): BitchatPacket {
        return BitchatPacket(
            version = 2u,
            type = 0x02u,
            senderID = ByteArray(8) { it.toByte() },
            recipientID = ByteArray(8) { (0x10 + it).toByte() },
            timestamp = 1_700_000_000_000uL,
            payload = "hello".toByteArray(Charsets.UTF_8),
            signature = ByteArray(64) { 0x01 },
            ttl = 7u,
            route = listOf(ByteArray(8) { 0x55.toByte() })
        )
    }

    @Test
    fun computeIdIgnoresNonCanonicalFields() {
        val packet = basePacket()
        val variant = packet.copy(
            recipientID = ByteArray(8) { 0x22.toByte() },
            signature = ByteArray(64) { 0x77.toByte() },
            ttl = 2u,
            route = null
        )

        val idA = PacketIdUtil.computeIdHex(packet)
        val idB = PacketIdUtil.computeIdHex(variant)
        assertEquals(idA, idB)
    }

    @Test
    fun computeIdChangesWhenPayloadChanges() {
        val packet = basePacket()
        val variant = packet.copy(payload = "hello2".toByteArray(Charsets.UTF_8))

        assertNotEquals(PacketIdUtil.computeIdHex(packet), PacketIdUtil.computeIdHex(variant))
    }

    @Test
    fun computeIdChangesWhenTimestampChanges() {
        val packet = basePacket()
        val variant = packet.copy(timestamp = 1_700_000_000_001uL)

        assertNotEquals(PacketIdUtil.computeIdHex(packet), PacketIdUtil.computeIdHex(variant))
    }

    @Test
    fun computeIdHasExpectedLengthAndFormat() {
        val packet = basePacket()
        val bytes = PacketIdUtil.computeIdBytes(packet)
        val hex = PacketIdUtil.computeIdHex(packet)

        assertEquals(16, bytes.size)
        assertEquals(32, hex.length)
        assertTrue(hex.matches(Regex("[0-9a-f]{32}")))
    }
}
