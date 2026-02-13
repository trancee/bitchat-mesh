package com.bitchat.android.noise

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoiseChannelEncryptionTests {
    @Test
    fun setChannelPasswordStoresKeyAndPassword() {
        val encryption = NoiseChannelEncryption()

        encryption.setChannelPassword("secret", "channel-1")

        assertTrue(encryption.hasChannelKey("channel-1"))
        assertEquals("secret", encryption.getChannelPassword("channel-1"))
    }

    @Test
    fun setChannelPasswordIgnoresEmptyPassword() {
        val encryption = NoiseChannelEncryption()

        encryption.setChannelPassword("", "channel-2")

        assertFalse(encryption.hasChannelKey("channel-2"))
    }

    @Test
    fun removeChannelPasswordClearsKey() {
        val encryption = NoiseChannelEncryption()
        encryption.setChannelPassword("secret", "channel-3")

        encryption.removeChannelPassword("channel-3")

        assertFalse(encryption.hasChannelKey("channel-3"))
        assertNull(encryption.getChannelPassword("channel-3"))
    }

    @Test
    fun encryptAndDecryptRoundTrip() {
        val encryption = NoiseChannelEncryption()
        encryption.setChannelPassword("secret", "channel-4")

        val encrypted = encryption.encryptChannelMessage("hello", "channel-4")
        val decrypted = encryption.decryptChannelMessage(encrypted, "channel-4")

        assertEquals("hello", decrypted)
    }

    @Test
    fun encryptThrowsWhenNoKeyAvailable() {
        val encryption = NoiseChannelEncryption()

        assertThrows(IllegalStateException::class.java) {
            encryption.encryptChannelMessage("hi", "missing")
        }
    }

    @Test
    fun decryptThrowsWhenTooShort() {
        val encryption = NoiseChannelEncryption()
        encryption.setChannelPassword("secret", "channel-5")

        assertThrows(IllegalArgumentException::class.java) {
            encryption.decryptChannelMessage(ByteArray(8), "channel-5")
        }
    }

    @Test
    fun keyCommitmentVerifiesCaseInsensitive() {
        val encryption = NoiseChannelEncryption()
        encryption.setChannelPassword("secret", "channel-6")

        val commitment = encryption.calculateKeyCommitment("channel-6")

        assertNotNull(commitment)
        assertTrue(encryption.verifyKeyCommitment("channel-6", commitment!!.uppercase()))
    }

    @Test
    fun channelKeyPacketRoundTrip() {
        val encryption = NoiseChannelEncryption()

        val packet = encryption.createChannelKeyPacket("secret", "chan")
        val parsed = encryption.processChannelKeyPacket(packet!!)

        assertNotNull(parsed)
        assertEquals("chan", parsed!!.first)
        assertEquals("secret", parsed.second)
    }

    @Test
    fun processChannelKeyPacketRejectsInvalidData() {
        val encryption = NoiseChannelEncryption()

        val parsed = encryption.processChannelKeyPacket("not-json".toByteArray(Charsets.UTF_8))

        assertNull(parsed)
    }
}
