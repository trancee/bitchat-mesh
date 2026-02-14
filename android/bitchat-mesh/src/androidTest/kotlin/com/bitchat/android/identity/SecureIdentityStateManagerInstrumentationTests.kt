package com.bitchat.android.identity

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureIdentityStateManagerInstrumentationTests {
    private lateinit var manager: SecureIdentityStateManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        manager = SecureIdentityStateManager(context)
        manager.clearIdentityData()
    }

    @Test
    fun saveAndLoadStaticKeyRoundTrip() {
        val privateKey = ByteArray(32) { 0x01 }
        val publicKey = ByteArray(32) { 0x02 }

        manager.saveStaticKey(privateKey, publicKey)
        val loaded = manager.loadStaticKey()

        assertNotNull(loaded)
        assertArrayEquals(privateKey, loaded!!.first)
        assertArrayEquals(publicKey, loaded.second)
        assertTrue(manager.hasIdentityData())
    }

    @Test
    fun saveAndLoadSigningKeyRoundTrip() {
        val privateKey = ByteArray(32) { 0x03 }
        val publicKey = ByteArray(32) { 0x04 }

        manager.saveSigningKey(privateKey, publicKey)
        val loaded = manager.loadSigningKey()

        assertNotNull(loaded)
        assertArrayEquals(privateKey, loaded!!.first)
        assertArrayEquals(publicKey, loaded.second)
    }

    @Test
    fun fingerprintValidationAndCaching() {
        val publicKey = ByteArray(32) { 0x05 }
        val fingerprint = manager.generateFingerprint(publicKey)

        assertEquals(64, fingerprint.length)
        assertTrue(manager.isValidFingerprint(fingerprint))
        assertFalse(manager.isValidFingerprint("invalid"))

        manager.setVerifiedFingerprint(fingerprint, true)
        assertTrue(manager.isVerifiedFingerprint(fingerprint))

        manager.setVerifiedFingerprint(fingerprint, false)
        assertFalse(manager.isVerifiedFingerprint(fingerprint))
    }

    @Test
    fun cachePeerDataRoundTrip() {
        val fingerprint = "a".repeat(64)
        manager.cachePeerFingerprint("Peer", fingerprint)
        assertEquals(fingerprint, manager.getCachedPeerFingerprint("peer"))

        val noiseKey = "b".repeat(64)
        manager.cachePeerNoiseKey("Peer", noiseKey)
        assertEquals(noiseKey, manager.getCachedNoiseKey("peer"))

        val noiseFingerprint = "c".repeat(64)
        manager.cacheNoiseFingerprint(noiseKey, noiseFingerprint)
        assertEquals(noiseFingerprint, manager.getCachedNoiseFingerprint(noiseKey))

        manager.cacheFingerprintNickname(fingerprint, "Alice")
        assertEquals("Alice", manager.getCachedFingerprintNickname(fingerprint))
    }

    @Test
    fun validateKeysRejectsInvalidPoints() {
        val zeros = ByteArray(32)
        val ones = ByteArray(32) { 0xFF.toByte() }
        val randomKey = ByteArray(32) { it.toByte() }

        assertFalse(manager.validatePublicKey(zeros))
        assertFalse(manager.validatePublicKey(ones))
        assertTrue(manager.validatePublicKey(randomKey))

        assertFalse(manager.validatePrivateKey(zeros))
        assertTrue(manager.validatePrivateKey(randomKey))
    }

    @Test
    fun secureValueHelpersRoundTrip() {
        manager.storeSecureValue("test-key", "value")
        assertTrue(manager.hasSecureValue("test-key"))
        assertEquals("value", manager.getSecureValue("test-key"))

        manager.removeSecureValue("test-key")
        assertFalse(manager.hasSecureValue("test-key"))
        assertNull(manager.getSecureValue("test-key"))
    }
}
