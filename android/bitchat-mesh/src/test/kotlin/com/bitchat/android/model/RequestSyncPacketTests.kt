package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RequestSyncPacketTests {
    @Test
    fun encodeDecodeRoundTrip() {
        val packet = RequestSyncPacket(
            p = 6,
            m = 1024,
            data = byteArrayOf(0x01, 0x02, 0x03)
        )

        val encoded = packet.encode()
        val decoded = RequestSyncPacket.decode(encoded)
        assertNotNull(decoded)
        assertEquals(packet.p, decoded!!.p)
        assertEquals(packet.m, decoded.m)
        assertArrayEquals(packet.data, decoded.data)
    }

    @Test
    fun decodeRejectsMissingFields() {
        val onlyP = byteArrayOf(0x01, 0x00, 0x01, 0x05)
        assertNull(RequestSyncPacket.decode(onlyP))

        val onlyM = byteArrayOf(0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x10)
        assertNull(RequestSyncPacket.decode(onlyM))
    }

    @Test
    fun decodeRejectsInvalidValues() {
        val invalidP = byteArrayOf(
            0x01, 0x00, 0x01, 0x00,
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x10,
            0x03, 0x00, 0x01, 0x01
        )
        assertNull(RequestSyncPacket.decode(invalidP))

        val invalidM = byteArrayOf(
            0x01, 0x00, 0x01, 0x01,
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x01, 0x01
        )
        assertNull(RequestSyncPacket.decode(invalidM))
    }

    @Test
    fun decodeRejectsOversizedPayload() {
        val tooLarge = ByteArray(RequestSyncPacket.MAX_ACCEPT_FILTER_BYTES + 1) { 0x01 }
        val encoded = RequestSyncPacket(1, 2, tooLarge).encode()
        assertNull(RequestSyncPacket.decode(encoded))
    }
}
