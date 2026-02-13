package com.bitchat.android.services.meshgraph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GossipTLVTests {
    @Test
    fun encodeNeighborsDeduplicatesAndLimitsToTen() {
        val ids = (0 until 12).map { "%016x".format(it) } + listOf("0000000000000001")
        val encoded = GossipTLV.encodeNeighbors(ids)

        assertEquals(GossipTLV.DIRECT_NEIGHBORS_TYPE.toByte(), encoded[0])
        val len = encoded[1].toInt() and 0xFF
        assertEquals(10 * 8, len)
        assertEquals(2 + len, encoded.size)
    }

    @Test
    fun decodeNeighborsReturnsListWhenPresent() {
        val ids = listOf("0102030405060708", "1111111111111111")
        val encoded = GossipTLV.encodeNeighbors(ids)
        val decoded = GossipTLV.decodeNeighborsFromAnnouncementPayload(encoded)

        assertNotNull(decoded)
        assertEquals(ids.map { it.lowercase() }, decoded)
    }

    @Test
    fun decodeNeighborsReturnsEmptyListWhenPresentWithZeroLength() {
        val payload = byteArrayOf(GossipTLV.DIRECT_NEIGHBORS_TYPE.toByte(), 0x00)
        val decoded = GossipTLV.decodeNeighborsFromAnnouncementPayload(payload)
        assertNotNull(decoded)
        assertTrue(decoded!!.isEmpty())
    }

    @Test
    fun decodeNeighborsReturnsNullWhenMissingOrTruncated() {
        val otherTlv = byteArrayOf(0x09, 0x02, 0x01, 0x02)
        assertNull(GossipTLV.decodeNeighborsFromAnnouncementPayload(otherTlv))

        val truncated = byteArrayOf(GossipTLV.DIRECT_NEIGHBORS_TYPE.toByte(), 0x05, 0x01)
        assertNull(GossipTLV.decodeNeighborsFromAnnouncementPayload(truncated))
    }
}
