package com.bitchat.android.mesh

import com.bitchat.android.crypto.EncryptionService
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SecurityManagerMoreTests {
    @Test
    fun signEncryptDecryptReturnNullOnErrors() {
        val encryption = mock<EncryptionService>()
        whenever(encryption.sign(any())).thenThrow(RuntimeException("sign"))
        whenever(encryption.encrypt(any(), any())).thenThrow(RuntimeException("enc"))
        whenever(encryption.decrypt(any(), any())).thenThrow(RuntimeException("dec"))
        val manager = SecurityManager(encryption, MY_PEER_ID)

        assertNull(manager.signPacket(byteArrayOf(0x01)))
        assertNull(manager.encryptForPeer(byteArrayOf(0x01), "peer"))
        assertNull(manager.decryptFromPeer(byteArrayOf(0x01), "peer"))
    }

    @Test
    fun verifySignaturePassesWhenNoSignature() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("aaaaaaaaaaaaaaaa"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(manager.verifySignature(packet, "peer"))
    }

    @Test
    fun validatePacketSkipsSignatureForNonSignedTypes() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(manager.validatePacket(packet, "peer"))
    }

    @Test
    fun validatePacketRejectsDuplicateNonAnnounce() {
        val manager = SecurityManager(mock<EncryptionService>(), MY_PEER_ID)
        val packet = BitchatPacket(
            type = MessageType.FRAGMENT.value,
            senderID = byteArrayOf(0x01),
            recipientID = null,
            timestamp = 42uL,
            payload = byteArrayOf(0x01, 0x02),
            ttl = 1u
        )

        assertTrue(manager.validatePacket(packet, "peer"))
        assertTrue(!manager.validatePacket(packet, "peer"))
    }

    @Test
    fun validatePacketRejectsMissingSignature() {
        val manager = SecurityManager(mock<EncryptionService>(), MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = byteArrayOf(0x22),
            recipientID = null,
            timestamp = 10uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 1u
        )

        assertTrue(!manager.validatePacket(packet, "peer"))
    }

    @Test
    fun validatePacketRejectsUnknownSigningKey() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)
        manager.delegate = mock<SecurityManagerDelegate>()

        val packet = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = byteArrayOf(0x22),
            recipientID = null,
            timestamp = 10uL,
            payload = byteArrayOf(0x01),
            signature = ByteArray(64) { 1 },
            ttl = 1u
        )

        assertTrue(!manager.validatePacket(packet, "peer"))
    }

    @Test
    fun validatePacketRejectsAnnounceWithInvalidPayload() {
        val encryption = mock<EncryptionService>()
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = byteArrayOf(0x23),
            recipientID = null,
            timestamp = 10uL,
            payload = byteArrayOf(0x01),
            signature = ByteArray(64) { 1 },
            ttl = 1u
        )

        assertTrue(!manager.validatePacket(packet, "peer"))
    }

    @Test
    fun validatePacketSkipsOwnPeer() {
        val manager = SecurityManager(mock<EncryptionService>(), MY_PEER_ID)
        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("aaaa"),
            recipientID = null,
            timestamp = 10uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 1u
        )

        assertTrue(!manager.validatePacket(packet, MY_PEER_ID))
    }

    @Test
    fun validatePacketAllowsDuplicateAnnounceWithMaxTtl() {
        val encryption = mock<EncryptionService>()
        whenever(encryption.verifyEd25519Signature(any(), any(), any())).thenReturn(true)
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val announcement = IdentityAnnouncement(
            nickname = "Alice",
            noisePublicKey = ByteArray(32) { 1 },
            signingPublicKey = ByteArray(32) { 2 }
        )
        val packet = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
            recipientID = null,
            timestamp = 99uL,
            payload = announcement.encode()!!,
            signature = ByteArray(64) { 3 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(manager.validatePacket(packet, "peer"))
        assertTrue(manager.validatePacket(packet, "peer"))
    }

    @Test
    fun getDebugInfoIncludesKeyExchangeHistory() = runBlocking {
        val encryption = mock<EncryptionService>()
        whenever(encryption.processHandshakeMessage(any(), any())).thenReturn(null)
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("cccccccccccccccc"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01, 0x02),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(manager.handleNoiseHandshake(RoutedPacket(packet, peerID = "peer")))
        val debugInfo = manager.getDebugInfo()
        assertTrue(debugInfo.contains("Key Exchange History:"))
    }

    @Test
    fun clearAllDataResetsDebugCounts() {
        val manager = SecurityManager(mock<EncryptionService>(), MY_PEER_ID)
        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("dddddddddddddddd"),
            recipientID = null,
            timestamp = 11uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 1u
        )

        assertTrue(manager.validatePacket(packet, "peer"))
        assertTrue(manager.getDebugInfo().contains("Processed Messages: 1"))
        manager.clearAllData()
        assertTrue(manager.getDebugInfo().contains("Processed Messages: 0"))
    }

    @Test
    fun handleNoiseHandshakeIgnoresDuplicateExchange() = runBlocking {
        val encryption = mock<EncryptionService>()
        whenever(encryption.processHandshakeMessage(any(), any())).thenReturn(null)
        val manager = SecurityManager(encryption, MY_PEER_ID)

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("cccccccccccccccc"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01, 0x02),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        val routed = RoutedPacket(packet, peerID = "peer-dup")
        assertTrue(manager.handleNoiseHandshake(routed))
        assertFalse(manager.handleNoiseHandshake(routed))
    }

    @Test
    fun hasKeysForPeerUsesEncryptionService() {
        val encryption = mock<EncryptionService>()
        whenever(encryption.hasEstablishedSession("peer")).thenReturn(true)
        val manager = SecurityManager(encryption, MY_PEER_ID)

        assertTrue(manager.hasKeysForPeer("peer"))
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
