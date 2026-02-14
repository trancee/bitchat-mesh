package com.bitchat.android.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionServiceInstrumentationTests {
    @Test
    fun signAndVerifyRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = EncryptionService(context)

        val data = "payload".toByteArray(Charsets.UTF_8)
        val signature = service.signData(data)
        val publicKey = service.getSigningPublicKey()

        assertNotNull(signature)
        assertNotNull(publicKey)

        val verified = service.verifyEd25519Signature(signature!!, data, publicKey!!)
        assertTrue(verified)

        service.shutdown()
    }

    @Test
    fun clearPersistentIdentityRotatesFingerprint() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = EncryptionService(context)

        val before = service.getIdentityFingerprint()
        service.clearPersistentIdentity()
        val after = service.getIdentityFingerprint()

        assertNotEquals(before, after)

        service.shutdown()
    }

    @Test
    fun debugInfoContainsFingerprint() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = EncryptionService(context)

        val info = service.getDebugInfo()
        assertTrue(info.contains("EncryptionService Debug"))
        assertTrue(info.contains("Fingerprint"))

        service.shutdown()
    }
}
