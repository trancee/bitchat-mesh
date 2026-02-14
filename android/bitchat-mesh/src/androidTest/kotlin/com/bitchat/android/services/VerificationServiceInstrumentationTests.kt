package com.bitchat.android.services

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bitchat.android.crypto.EncryptionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationServiceInstrumentationTests {
    @Test
    fun buildMyQRStringAndVerifyRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val encryptionService = EncryptionService(context)
        VerificationService.configure(encryptionService)

        val qrString = VerificationService.buildMyQRString("Alice", "npub")

        assertNotNull(qrString)
        val verified = VerificationService.verifyScannedQR(qrString!!)
        assertNotNull(verified)
        assertEquals("Alice", verified!!.nickname)

        encryptionService.shutdown()
    }

    @Test
    fun buildMyQRStringUsesCacheWithinWindow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val encryptionService = EncryptionService(context)
        VerificationService.configure(encryptionService)

        val first = VerificationService.buildMyQRString("Bob", null)
        val second = VerificationService.buildMyQRString("Bob", null)

        assertNotNull(first)
        assertEquals(first, second)

        encryptionService.shutdown()
    }

    @Test
    fun verifyScannedQRRejectsExpired() {
        val expired = VerificationService.VerificationQR(
            v = 1,
            noiseKeyHex = "aa",
            signKeyHex = "bb",
            npub = null,
            nickname = "Old",
            ts = 1L,
            nonceB64 = "nonce",
            sigHex = "00"
        ).toUrlString()

        val verified = VerificationService.verifyScannedQR(expired, maxAgeSeconds = 1)

        assertNull(verified)
    }
}
