package com.bitchat.android.noise

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bitchat.android.noise.southernstorm.protocol.Noise
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoiseSessionManagerInstrumentationTests {
    @Test
    fun handshakeEstablishesSessionAndEncrypts() {
        val (privA, pubA) = generateKeyPair()
        val (privB, pubB) = generateKeyPair()

        val managerA = NoiseSessionManager(privA, pubA)
        val managerB = NoiseSessionManager(privB, pubB)

        assertEquals(NoiseSession.NoiseSessionState.Uninitialized, managerA.getSessionState("peerB"))

        val msg1 = managerA.initiateHandshake("peerB")
        val msg2 = managerB.processHandshakeMessage("peerA", msg1)
        assertNotNull(msg2)

        val msg3 = managerA.processHandshakeMessage("peerB", msg2!!)
        assertNotNull(msg3)

        managerB.processHandshakeMessage("peerA", msg3!!)

        assertTrue(managerA.hasEstablishedSession("peerB"))
        assertTrue(managerB.hasEstablishedSession("peerA"))

        val plaintext = "hello".toByteArray(Charsets.UTF_8)
        val encrypted = managerA.encrypt(plaintext, "peerB")
        val decrypted = managerB.decrypt(encrypted, "peerA")

        assertArrayEquals(plaintext, decrypted)

        managerA.shutdown()
        managerB.shutdown()
    }

    private fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val dh = Noise.createDH("25519")
        dh.generateKeyPair()
        val privateKey = ByteArray(32)
        val publicKey = ByteArray(32)
        dh.getPrivateKey(privateKey, 0)
        dh.getPublicKey(publicKey, 0)
        dh.destroy()
        return privateKey to publicKey
    }
}
