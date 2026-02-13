package com.bitchat.android.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdentityAnnouncementTests {
    @Test
    fun encodeDecodeRoundTrip() {
        val announcement = IdentityAnnouncement(
            nickname = "Alice",
            noisePublicKey = byteArrayOf(1, 2, 3),
            signingPublicKey = byteArrayOf(4, 5, 6)
        )

        val encoded = announcement.encode()
        val decoded = IdentityAnnouncement.decode(encoded!!)

        assertEquals(announcement, decoded)
    }

    @Test
    fun encodeRejectsOversizedFields() {
        val announcement = IdentityAnnouncement(
            nickname = "A".repeat(300),
            noisePublicKey = ByteArray(2),
            signingPublicKey = ByteArray(2)
        )

        assertNull(announcement.encode())
    }

    @Test
    fun decodeSkipsUnknownTlvTypes() {
        val base = IdentityAnnouncement(
            nickname = "Bob",
            noisePublicKey = byteArrayOf(9),
            signingPublicKey = byteArrayOf(8)
        ).encode()!!

        val withUnknown = base.toMutableList().apply {
            add(0, 0x0Au.toByte())
            add(1, 0x01)
            add(2, 0x7F)
        }.toByteArray()

        val decoded = IdentityAnnouncement.decode(withUnknown)

        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.nickname)
    }

    @Test
    fun decodeFailsWhenRequiredFieldsMissing() {
        val data = byteArrayOf(0x01, 0x01, 0x41)

        assertNull(IdentityAnnouncement.decode(data))
    }

    @Test
    fun toStringIncludesNickname() {
        val announcement = IdentityAnnouncement(
            nickname = "Carol",
            noisePublicKey = byteArrayOf(1, 2, 3),
            signingPublicKey = byteArrayOf(4, 5, 6)
        )

        assertTrue(announcement.toString().contains("Carol"))
    }

    @Test
    fun equalsUsesByteArrays() {
        val first = IdentityAnnouncement("Nick", byteArrayOf(1), byteArrayOf(2))
        val second = IdentityAnnouncement("Nick", byteArrayOf(1), byteArrayOf(3))

        assertNotEquals(first, second)
    }
}
