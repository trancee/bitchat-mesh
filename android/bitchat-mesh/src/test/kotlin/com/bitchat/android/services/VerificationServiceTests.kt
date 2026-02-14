package com.bitchat.android.services

import com.bitchat.android.crypto.EncryptionService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VerificationServiceTests {
    @Test
    fun canonicalBytesLowercasesKeysAndEncodesFields() {
        val qr = VerificationService.VerificationQR(
            v = 1,
            noiseKeyHex = "AABBCC",
            signKeyHex = "DDEE",
            npub = null,
            nickname = "Alice",
            ts = 1234L,
            nonceB64 = "nonce",
            sigHex = "sig"
        )

        val fields = decodeCanonicalFields(qr.canonicalBytes())

        assertEquals("bitchat-verify-v1", fields[0])
        assertEquals("1", fields[1])
        assertEquals("aabbcc", fields[2])
        assertEquals("ddee", fields[3])
        assertEquals("", fields[4])
        assertEquals("Alice", fields[5])
        assertEquals("1234", fields[6])
        assertEquals("nonce", fields[7])
    }

    @Test
    fun buildAndParseVerifyChallengeRoundTrip() {
        val nonce = byteArrayOf(1, 2, 3, 4)
        val challenge = VerificationService.buildVerifyChallenge("noise-key", nonce)

        val parsed = VerificationService.parseVerifyChallenge(challenge)

        assertNotNull(parsed)
        assertEquals("noise-key", parsed!!.first)
        assertArrayEquals(nonce, parsed.second)
    }

    @Test
    fun parseVerifyChallengeRejectsInvalidTag() {
        val invalid = byteArrayOf(0x02, 0x01, 0x41)

        val parsed = VerificationService.parseVerifyChallenge(invalid)

        assertNull(parsed)
    }

    @Test
    fun buildAndParseVerifyResponseRoundTrip() {
        val encryptionService = mock<EncryptionService>()
        val signature = byteArrayOf(9, 8, 7)
        whenever(encryptionService.signData(any())).thenReturn(signature)
        VerificationService.configure(encryptionService)

        val nonce = byteArrayOf(4, 3, 2, 1)
        val response = VerificationService.buildVerifyResponse("noise-key", nonce)

        assertNotNull(response)
        val parsed = VerificationService.parseVerifyResponse(response!!)

        assertNotNull(parsed)
        assertEquals("noise-key", parsed!!.noiseKeyHex)
        assertArrayEquals(nonce, parsed.nonceA)
        assertArrayEquals(signature, parsed.signature)
    }


    @Test
    fun buildVerifyResponseReturnsNullWhenSignFails() {
        val encryptionService = mock<EncryptionService>()
        whenever(encryptionService.signData(any())).thenReturn(null)
        VerificationService.configure(encryptionService)

        val response = VerificationService.buildVerifyResponse("noise-key", byteArrayOf(1, 2))

        assertNull(response)
    }

    @Test
    fun verifyResponseSignatureRejectsInvalidSignerKey() {
        val encryptionService = mock<EncryptionService>()
        VerificationService.configure(encryptionService)

        val ok = VerificationService.verifyResponseSignature(
            noiseKeyHex = "noise",
            nonceA = byteArrayOf(1, 2),
            signature = byteArrayOf(3, 4),
            signerPublicKeyHex = "zz"
        )

        assertEquals(false, ok)
    }

    @Test
    fun parseVerifyResponseRejectsTruncatedData() {
        val truncated = byteArrayOf(0x01, 0x01, 0x41, 0x02)

        val parsed = VerificationService.parseVerifyResponse(truncated)

        assertNull(parsed)
    }

    private fun decodeCanonicalFields(data: ByteArray): List<String> {
        var index = 0
        val out = mutableListOf<String>()
        while (index < data.size) {
            val length = data[index].toInt() and 0xFF
            index += 1
            val end = (index + length).coerceAtMost(data.size)
            out.add(String(data, index, end - index, Charsets.UTF_8))
            index = end
        }
        return out
    }
}
