package com.bitchat.android.mesh

import android.content.ContextWrapper
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatFilePacket
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.NoisePayload
import com.bitchat.android.model.NoisePayloadType
import com.bitchat.android.model.PrivateMessagePacket
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.android.util.AppConstants
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class MessageHandlerTests {
    private class TestDelegate : MessageHandlerDelegate {
        var lastDeliveryAck: Pair<String, String>? = null
        var lastReadReceipt: Pair<String, String>? = null
        var lastVerifyChallenge: Pair<String, ByteArray>? = null
        var lastVerifyResponse: Pair<String, ByteArray>? = null
        var lastMessage: BitchatMessage? = null
        var lastChannelLeave: Pair<String, String>? = null
        var lastRemovedPeer: String? = null
        var sentPackets = mutableListOf<BitchatPacket>()
        var lastUpdatePeerInfo: Boolean = false
        var peerInfo: PeerInfo? = null
        var decryptCalls: Int = 0
        var decryptOverride: ByteArray? = null
        var decryptReturnsNull: Boolean = false
        var verifySignatureOverride: Boolean? = null
        var lastFilePeer: String? = null
        var lastFileName: String? = null
        var lastFileSize: Long? = null
        var lastFileMime: String? = null
        var lastFilePath: String? = null

        override fun addOrUpdatePeer(peerID: String, nickname: String): Boolean = true
        override fun removePeer(peerID: String) { lastRemovedPeer = peerID }
        override fun updatePeerNickname(peerID: String, nickname: String) {}
        override fun getPeerNickname(peerID: String): String? = "peer-$peerID"
        override fun getNetworkSize(): Int = 1
        override fun getMyNickname(): String? = "me"
        override fun getPeerInfo(peerID: String): PeerInfo? = peerInfo
        override fun updatePeerInfo(
            peerID: String,
            nickname: String,
            noisePublicKey: ByteArray,
            signingPublicKey: ByteArray,
            isVerified: Boolean
        ): Boolean {
            lastUpdatePeerInfo = true
            return true
        }

        override fun sendPacket(packet: BitchatPacket) { sentPackets.add(packet) }
        override fun relayPacket(routed: RoutedPacket) {}
        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun verifySignature(packet: BitchatPacket, peerID: String): Boolean =
            verifySignatureOverride ?: true
        override fun encryptForPeer(data: ByteArray, recipientPeerID: String): ByteArray? = byteArrayOf(0x01)
        override fun decryptFromPeer(encryptedData: ByteArray, senderPeerID: String): ByteArray? {
            decryptCalls += 1
            if (decryptReturnsNull) {
                return null
            }
            return decryptOverride ?: encryptedData
        }
        override fun verifyEd25519Signature(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean = true

        override fun hasNoiseSession(peerID: String): Boolean = true
        override fun initiateNoiseHandshake(peerID: String) {}
        override fun processNoiseHandshakeMessage(payload: ByteArray, peerID: String): ByteArray? = byteArrayOf(0x02)
        override fun updatePeerIDBinding(newPeerID: String, nickname: String, publicKey: ByteArray, previousPeerID: String?) {}

        override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null

        override fun onMessageReceived(message: BitchatMessage) { lastMessage = message }
        override fun onChannelLeave(channel: String, fromPeer: String) { lastChannelLeave = channel to fromPeer }
        override fun onDeliveryAckReceived(messageID: String, peerID: String) { lastDeliveryAck = messageID to peerID }
        override fun onReadReceiptReceived(messageID: String, peerID: String) { lastReadReceipt = messageID to peerID }
        override fun onVerifyChallengeReceived(peerID: String, payload: ByteArray, timestampMs: Long) {
            lastVerifyChallenge = peerID to payload
        }
        override fun onVerifyResponseReceived(peerID: String, payload: ByteArray, timestampMs: Long) {
            lastVerifyResponse = peerID to payload
        }
        override fun onFileReceived(peerID: String, fileName: String, fileSize: Long, mimeType: String, localPath: String) {
            lastFilePeer = peerID
            lastFileName = fileName
            lastFileSize = fileSize
            lastFileMime = mimeType
            lastFilePath = localPath
        }
    }

    @Test
    fun handleNoiseEncryptedDeliversAck() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val payload = NoisePayload(NoisePayloadType.DELIVERED, "msg-1".toByteArray(Charsets.UTF_8)).encode()
        val packet = noiseEncryptedPacket(payload)

        handler.handleNoiseEncrypted(RoutedPacket(packet, peerID = "peer-1"))

        assertEquals("msg-1", delegate.lastDeliveryAck?.first)
        assertEquals("peer-1", delegate.lastDeliveryAck?.second)
    }

    @Test
    fun handleNoiseEncryptedPrivateMessageSendsAck() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val privateMessage = PrivateMessagePacket("msg-2", "hello")
        val payload = NoisePayload(NoisePayloadType.PRIVATE_MESSAGE, privateMessage.encode()!!).encode()
        val packet = noiseEncryptedPacket(payload)

        handler.handleNoiseEncrypted(RoutedPacket(packet, peerID = "peer-2"))

        assertNotNull(delegate.lastMessage)
        assertTrue(delegate.sentPackets.any { it.type == MessageType.NOISE_ENCRYPTED.value })
    }

    @Test
    fun handleNoiseEncryptedHandlesReceiptsAndVerify() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val readPayload = NoisePayload(NoisePayloadType.READ_RECEIPT, "msg-3".toByteArray(Charsets.UTF_8)).encode()
        handler.handleNoiseEncrypted(RoutedPacket(noiseEncryptedPacket(readPayload), peerID = "peer-3"))
        assertEquals("msg-3", delegate.lastReadReceipt?.first)

        val verifyChallenge = NoisePayload(NoisePayloadType.VERIFY_CHALLENGE, byteArrayOf(0x01)).encode()
        handler.handleNoiseEncrypted(RoutedPacket(noiseEncryptedPacket(verifyChallenge), peerID = "peer-4"))
        assertEquals("peer-4", delegate.lastVerifyChallenge?.first)

        val verifyResponse = NoisePayload(NoisePayloadType.VERIFY_RESPONSE, byteArrayOf(0x02)).encode()
        handler.handleNoiseEncrypted(RoutedPacket(noiseEncryptedPacket(verifyResponse), peerID = "peer-5"))
        assertEquals("peer-5", delegate.lastVerifyResponse?.first)
    }

    @Test
    fun handleNoiseHandshakeSendsResponse() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("aaaaaaaaaaaaaaaa"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        handler.handleNoiseHandshake(RoutedPacket(packet, peerID = "peer-6"))

        assertTrue(delegate.sentPackets.any { it.type == MessageType.NOISE_HANDSHAKE.value })
    }

    @Test
    fun handleAnnounceProcessesVerifiedAnnouncement() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val announcement = IdentityAnnouncement(
            nickname = "Alice",
            noisePublicKey = ByteArray(32) { 1 },
            signingPublicKey = ByteArray(32) { 2 }
        )
        val packet = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = announcement.encode()!!,
            signature = ByteArray(64) { 3 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        val result = handler.handleAnnounce(RoutedPacket(packet, peerID = "peer-7"))

        assertTrue(result)
        assertTrue(delegate.lastUpdatePeerInfo)
    }

    @Test
    fun handleLeaveRoutesChannelAndPeer() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val leaveChannel = BitchatPacket(
            type = MessageType.LEAVE.value,
            senderID = hexToBytes("cccccccccccccccc"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = "#general".toByteArray(Charsets.UTF_8),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        handler.handleLeave(RoutedPacket(leaveChannel, peerID = "peer-8"))
        assertEquals("#general", delegate.lastChannelLeave?.first)

        val leavePeer = BitchatPacket(
            type = MessageType.LEAVE.value,
            senderID = hexToBytes("cccccccccccccccc"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = "bye".toByteArray(Charsets.UTF_8),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        handler.handleLeave(RoutedPacket(leavePeer, peerID = "peer-9"))
        assertEquals("peer-9", delegate.lastRemovedPeer)
    }

    @Test
    fun handleMessageProcessesBroadcastAndPrivate() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.peerInfo = PeerInfo(
            id = "peer-10",
            nickname = "peer-10",
            isConnected = true,
            isDirectConnection = true,
            noisePublicKey = null,
            signingPublicKey = null,
            isVerifiedNickname = true,
            lastSeen = System.currentTimeMillis()
        )
        handler.delegate = delegate

        val broadcast = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("eeeeeeeeeeeeeeee"),
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = System.currentTimeMillis().toULong(),
            payload = "hello".toByteArray(Charsets.UTF_8),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        handler.handleMessage(RoutedPacket(broadcast, peerID = "peer-10"))
        assertNotNull(delegate.lastMessage)

        val privatePacket = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("ffffffffffffffff"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = "secret".toByteArray(Charsets.UTF_8),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        handler.handleMessage(RoutedPacket(privatePacket, peerID = "peer-11"))
        assertEquals("secret", delegate.lastMessage?.content)
    }

    @Test
    fun handleMessageDropsUnverifiedBroadcast() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.peerInfo = PeerInfo(
            id = "peer-12",
            nickname = "peer-12",
            isConnected = true,
            isDirectConnection = false,
            noisePublicKey = null,
            signingPublicKey = null,
            isVerifiedNickname = false,
            lastSeen = System.currentTimeMillis()
        )
        handler.delegate = delegate

        val broadcast = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("abababababababab"),
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = System.currentTimeMillis().toULong(),
            payload = "hello".toByteArray(Charsets.UTF_8),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        handler.handleMessage(RoutedPacket(broadcast, peerID = "peer-12"))

        assertTrue(delegate.lastMessage == null)
    }

    @Test
    fun handleAnnounceRejectsStaleOrInvalid() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.peerInfo = PeerInfo(
            id = "peer-13",
            nickname = "peer-13",
            isConnected = true,
            isDirectConnection = true,
            noisePublicKey = ByteArray(32) { 9 },
            signingPublicKey = ByteArray(32) { 2 },
            isVerifiedNickname = true,
            lastSeen = System.currentTimeMillis()
        )
        handler.delegate = delegate

        val stalePacket = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("abababababababab"),
            recipientID = null,
            timestamp = (System.currentTimeMillis() - AppConstants.Mesh.STALE_PEER_TIMEOUT_MS - 1).toULong(),
            payload = IdentityAnnouncement("Old", ByteArray(32) { 1 }, ByteArray(32) { 2 }).encode()!!,
            signature = ByteArray(64) { 1 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        assertTrue(!handler.handleAnnounce(RoutedPacket(stalePacket, peerID = "peer-13")))

        val badPacket = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("cdcdcdcdcdcdcdcd"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01, 0x02),
            signature = ByteArray(64) { 1 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        assertTrue(!handler.handleAnnounce(RoutedPacket(badPacket, peerID = "peer-14")))
    }

    @Test
    fun handleNoiseHandshakeSkipsNonRecipient() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val packet = BitchatPacket(
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = hexToBytes("ffffffffffffffff"),
            recipientID = hexToBytes("1111111111111111"),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        handler.handleNoiseHandshake(RoutedPacket(packet, peerID = "peer-15"))

        assertTrue(delegate.sentPackets.isEmpty())
    }

    @Test
    fun handleNoiseEncryptedSkipsNonRecipient() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        handler.delegate = delegate

        val packet = BitchatPacket(
            type = MessageType.NOISE_ENCRYPTED.value,
            senderID = hexToBytes("eeeeeeeeeeeeeeee"),
            recipientID = hexToBytes("1111111111111111"),
            timestamp = System.currentTimeMillis().toULong(),
            payload = byteArrayOf(0x01, 0x02),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        handler.handleNoiseEncrypted(RoutedPacket(packet, peerID = "peer-16"))

        assertEquals(0, delegate.decryptCalls)
    }

    @Test
    fun handleNoiseEncryptedDropsWhenDecryptFails() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.decryptReturnsNull = true
        handler.delegate = delegate

        val payload = NoisePayload(NoisePayloadType.DELIVERED, "msg-4".toByteArray(Charsets.UTF_8)).encode()
        val packet = noiseEncryptedPacket(payload)

        handler.handleNoiseEncrypted(RoutedPacket(packet, peerID = "peer-17"))

        assertTrue(delegate.lastDeliveryAck == null)
    }

    @Test
    fun handlePrivateMessageRejectsInvalidSignature() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.verifySignatureOverride = false
        handler.delegate = delegate

        val packet = BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("abababababababab"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = "secret".toByteArray(Charsets.UTF_8),
            signature = ByteArray(64) { 1 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        handler.handleMessage(RoutedPacket(packet, peerID = "peer-18"))

        assertTrue(delegate.lastMessage == null)
    }

    @Test
    fun handleAnnounceRejectsKeyMismatch() = runBlocking {
        val handler = newHandler()
        val delegate = TestDelegate()
        delegate.peerInfo = PeerInfo(
            id = "peer-19",
            nickname = "peer-19",
            isConnected = true,
            isDirectConnection = true,
            noisePublicKey = ByteArray(32) { 9 },
            signingPublicKey = ByteArray(32) { 2 },
            isVerifiedNickname = true,
            lastSeen = System.currentTimeMillis()
        )
        handler.delegate = delegate

        val announcement = IdentityAnnouncement(
            nickname = "Bob",
            noisePublicKey = ByteArray(32) { 1 },
            signingPublicKey = ByteArray(32) { 2 }
        )
        val packet = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes("abababababababab"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = announcement.encode()!!,
            signature = ByteArray(64) { 3 },
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(!handler.handleAnnounce(RoutedPacket(packet, peerID = "peer-19")))
    }

    @Test
    fun handleBroadcastFileTransferSavesFile() = runBlocking {
        val tempDir = createTempDir()
        try {
            val handler = newHandler(tempDir)
            val delegate = TestDelegate()
            delegate.peerInfo = PeerInfo(
                id = "peer-20",
                nickname = "peer-20",
                isConnected = true,
                isDirectConnection = true,
                noisePublicKey = null,
                signingPublicKey = null,
                isVerifiedNickname = true,
                lastSeen = System.currentTimeMillis()
            )
            handler.delegate = delegate

            val content = "file-data".toByteArray(Charsets.UTF_8)
            val filePacket = BitchatFilePacket("note.txt", content.size.toLong(), "text/plain", content)
            val packet = BitchatPacket(
                type = MessageType.FILE_TRANSFER.value,
                senderID = hexToBytes("abababababababab"),
                recipientID = SpecialRecipients.BROADCAST,
                timestamp = System.currentTimeMillis().toULong(),
                payload = filePacket.encode()!!,
                ttl = AppConstants.MESSAGE_TTL_HOPS
            )

            handler.handleMessage(RoutedPacket(packet, peerID = "peer-20"))

            assertEquals("peer-20", delegate.lastFilePeer)
            assertEquals("note.txt", delegate.lastFileName)
            assertEquals(content.size.toLong(), delegate.lastFileSize)
            assertEquals("text/plain", delegate.lastFileMime)
            val savedPath = delegate.lastFilePath
            assertTrue(savedPath != null && File(savedPath).exists())
            assertEquals(savedPath, delegate.lastMessage?.content)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun handlePrivateFileTransferSavesFile() = runBlocking {
        val tempDir = createTempDir()
        try {
            val handler = newHandler(tempDir)
            val delegate = TestDelegate()
            handler.delegate = delegate

            val content = "secret-file".toByteArray(Charsets.UTF_8)
            val filePacket = BitchatFilePacket("secret.bin", content.size.toLong(), "application/octet-stream", content)
            val packet = BitchatPacket(
                type = MessageType.FILE_TRANSFER.value,
                senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
                recipientID = hexToBytes(MY_PEER_ID),
                timestamp = System.currentTimeMillis().toULong(),
                payload = filePacket.encode()!!,
                signature = null,
                ttl = AppConstants.MESSAGE_TTL_HOPS
            )

            handler.handleMessage(RoutedPacket(packet, peerID = "peer-21"))

            assertEquals("peer-21", delegate.lastFilePeer)
            val savedPath = delegate.lastFilePath
            assertTrue(savedPath != null && File(savedPath).exists())
            assertTrue(delegate.lastMessage?.isPrivate == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun handleNoiseEncryptedFileTransferSavesFileAndAcks() = runBlocking {
        val tempDir = createTempDir()
        try {
            val handler = newHandler(tempDir)
            val delegate = TestDelegate()
            handler.delegate = delegate

            val content = "encrypted".toByteArray(Charsets.UTF_8)
            val filePacket = BitchatFilePacket("enc.txt", content.size.toLong(), "text/plain", content)
            val noisePayload = NoisePayload(NoisePayloadType.FILE_TRANSFER, filePacket.encode()!!).encode()
            val packet = noiseEncryptedPacket(noisePayload)

            handler.handleNoiseEncrypted(RoutedPacket(packet, peerID = "peer-22"))

            val savedPath = delegate.lastFilePath
            assertTrue(savedPath != null && File(savedPath).exists())
            assertTrue(delegate.sentPackets.any { it.type == MessageType.NOISE_ENCRYPTED.value })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun newHandler(cacheDir: File? = null): MessageHandler {
        val context = if (cacheDir == null) {
            ContextWrapper(null)
        } else {
            object : ContextWrapper(null) {
                override fun getCacheDir(): File = cacheDir
                override fun getFilesDir(): File = cacheDir
            }
        }
        return MessageHandler(MY_PEER_ID, context)
    }

    private fun createTempDir(): File {
        return Files.createTempDirectory("bitchat-mesh").toFile()
    }

    private fun noiseEncryptedPacket(payload: ByteArray): BitchatPacket {
        return BitchatPacket(
            type = MessageType.NOISE_ENCRYPTED.value,
            senderID = hexToBytes("dddddddddddddddd"),
            recipientID = hexToBytes(MY_PEER_ID),
            timestamp = System.currentTimeMillis().toULong(),
            payload = payload,
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
