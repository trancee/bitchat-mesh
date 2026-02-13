package com.bitchat.android.mesh

import com.bitchat.android.crypto.EncryptionService
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SecurityManagerTests {
    private class TestDelegate : SecurityManagerDelegate {
        var lastHandshakeResponse: Pair<String, ByteArray>? = null
        var lastKeyExchange: Pair<String, ByteArray>? = null

        override fun onKeyExchangeCompleted(peerID: String, peerPublicKeyData: ByteArray) {
            lastKeyExchange = peerID to peerPublicKeyData
        }

        override fun sendHandshakeResponse(peerID: String, response: ByteArray) {
            lastHandshakeResponse = peerID to response
        }

        override fun getPeerInfo(peerID: String): PeerInfo? = null
    }

    @Test
    fun validatePacketSkipsOwnPackets() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = messagePacket(signature = ByteArray(64) { 1 })

        assertFalse(manager.validatePacket(packet, MY_PEER_ID))
    }

    @Test
    fun validatePacketRejectsMissingSignature() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = messagePacket(signature = null)

        assertFalse(manager.validatePacket(packet, "peer-1"))
    }

    @Test
    fun validatePacketAllowsFreshAnnounceDuplicate() {
        val encryption = mock<EncryptionService>()
        whenever(encryption.verifyEd25519Signature(any(), any(), any())).thenReturn(true)
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val announcement = IdentityAnnouncement(
            nickname = "Alice",
            noisePublicKey = ByteArray(32) { 1 },
            signingPublicKey = ByteArray(32) { 2 }
        )
        val packet = announcePacket(announcement.encode()!!, ByteArray(64) { 3 })

        assertTrue(manager.validatePacket(packet, "peer-2"))
        assertTrue(manager.validatePacket(packet, "peer-2"))
    }

    @Test
    fun handleNoiseHandshakeSendsResponseAndCompletion() = runBlocking {
        val encryption = mock<EncryptionService>()
        whenever(encryption.processHandshakeMessage(any(), any())).thenReturn(byteArrayOf(0x01))
        whenever(encryption.hasEstablishedSession(any())).thenReturn(true)
        val manager = SecurityManager(encryption, MY_PEER_ID)
        val delegate = TestDelegate()
        manager.delegate = delegate

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("aaaaaaaaaaaaaaaa"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        val result = manager.handleNoiseHandshake(RoutedPacket(packet, peerID = "peer-3"))

        assertTrue(result)
        assertTrue(delegate.lastHandshakeResponse != null)
        assertTrue(delegate.lastKeyExchange != null)
    }

    private fun messagePacket(signature: ByteArray?): BitchatPacket {
        return BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = "hello".toByteArray(Charsets.UTF_8),
            signature = signature,
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
    }

    private fun announcePacket(payload: ByteArray, signature: ByteArray): BitchatPacket {
        return BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("cccccccccccccccc"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = payload,
            signature = signature,
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    companion object {
        private const val MY_PEER_ID = "0102030405060708"
    }
}
