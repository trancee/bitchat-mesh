package com.bitchat.android.noise

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoiseEncryptionServiceInstrumentationTests {
    @Test
    fun staticKeysAndFingerprintHaveExpectedLengths() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = NoiseEncryptionService(context)

        assertEquals(32, service.getStaticPublicKeyData().size)
        assertEquals(32, service.getSigningPublicKeyData().size)
        assertEquals(64, service.getIdentityFingerprint().length)

        service.shutdown()
    }

    @Test
    fun encryptWithoutSessionRequestsHandshake() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = NoiseEncryptionService(context)

        var requestedPeer: String? = null
        service.onHandshakeRequired = { peerID -> requestedPeer = peerID }

        val result = service.encrypt("hello".toByteArray(Charsets.UTF_8), "peer-1")

        assertNull(result)
        assertEquals("peer-1", requestedPeer)

        service.shutdown()
    }

    @Test
    fun clearPersistentIdentityRotatesFingerprint() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = NoiseEncryptionService(context)

        val before = service.getIdentityFingerprint()
        service.clearPersistentIdentity()
        val after = service.getIdentityFingerprint()

        assertNotEquals(before, after)
        assertEquals(64, after.length)

        service.shutdown()
    }
}
