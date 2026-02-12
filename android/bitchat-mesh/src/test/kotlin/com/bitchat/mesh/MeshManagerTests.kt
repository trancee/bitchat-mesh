package com.bitchat.mesh

import android.content.Context
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.MeshTransport
import com.bitchat.android.model.BitchatFilePacket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MeshManagerTests {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun startStopUsesTransport() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        val meshManager = MeshManager(mockContext(), transport)

        meshManager.setListener(listener)
        meshManager.start("  alice  ")

        assertEquals(1, transport.startCalls)
        assertNotNull(transport.delegate)
        assertTrue(meshManager.isStarted())
        assertTrue(meshManager.isRunning())
        assertEquals("peer-1", meshManager.myPeerId())

        meshManager.stop()

        assertEquals(1, transport.stopCalls)
        assertFalse(meshManager.isStarted())
        assertFalse(meshManager.isRunning())
        assertTrue(listener.stopped)
    }

    @Test
    fun sendEndpointsCallTransportAndListener() {
        val transport = FakeTransport()
        val listener = RecordingListener()
        val meshManager = MeshManager(mockContext(), transport)
        meshManager.setListener(listener)
        meshManager.start()

        meshManager.sendBroadcastMessage("hi", listOf("bob"), "room")
        meshManager.sendPrivateMessage("secret", "peer-2", "bob", "msg-1")

        assertEquals("hi", transport.lastBroadcastContent)
        assertEquals(listOf("bob"), transport.lastBroadcastMentions)
        assertEquals("room", transport.lastBroadcastChannel)
        assertEquals("secret", transport.lastPrivateContent)
        assertEquals("peer-2", transport.lastPrivatePeer)
        assertEquals("bob", transport.lastPrivateNickname)
        assertEquals("msg-1", transport.lastPrivateMessageId)
        assertEquals(listOf(null to null, "msg-1" to "peer-2"), listener.sent)
    }

    @Test
    fun sessionAndFileEndpoints() {
        val transport = FakeTransport()
        transport.establishedPeers.add("peer-3")
        transport.cancelResults["t1"] = true
        transport.nicknames["peer-3"] = "carol"
        transport.rssi["peer-3"] = -45

        val meshManager = MeshManager(mockContext(), transport)
        meshManager.start()

        meshManager.establish("peer-3")
        assertEquals("peer-3", transport.lastHandshakePeer)
        assertTrue(meshManager.isEstablished("peer-3"))
        assertFalse(meshManager.isEstablished("peer-x"))

        val packet = BitchatFilePacket("file.txt", 12L, "text/plain", "hi".toByteArray())
        meshManager.sendFileBroadcast(packet)
        meshManager.sendFilePrivate("peer-3", packet)

        assertEquals(packet, transport.lastBroadcastFile)
        assertEquals(packet, transport.lastPrivateFile)
        assertEquals("peer-3", transport.lastPrivateFilePeer)
        assertTrue(meshManager.cancelFileTransfer("t1"))
        assertFalse(meshManager.cancelFileTransfer("t2"))

        assertEquals(mapOf("peer-3" to "carol"), meshManager.peerNicknames())
        assertEquals(mapOf("peer-3" to -45), meshManager.peerRssi())
    }

    private fun mockContext(): Context {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        return context
    }

    private class FakeTransport : MeshTransport {
        override var delegate: BluetoothMeshDelegate? = null
        override val myPeerID: String = "peer-1"

        var startCalls = 0
        var stopCalls = 0
        var reusable = true

        var lastBroadcastContent: String? = null
        var lastBroadcastMentions: List<String> = emptyList()
        var lastBroadcastChannel: String? = null
        var lastPrivateContent: String? = null
        var lastPrivatePeer: String? = null
        var lastPrivateNickname: String? = null
        var lastPrivateMessageId: String? = null
        var lastHandshakePeer: String? = null
        var lastBroadcastFile: BitchatFilePacket? = null
        var lastPrivateFile: BitchatFilePacket? = null
        var lastPrivateFilePeer: String? = null
        val cancelResults: MutableMap<String, Boolean> = mutableMapOf()
        val establishedPeers: MutableSet<String> = mutableSetOf()
        val nicknames: MutableMap<String, String> = mutableMapOf()
        val rssi: MutableMap<String, Int> = mutableMapOf()

        override fun startServices() {
            startCalls += 1
        }

        override fun stopServices() {
            stopCalls += 1
        }

        override fun isReusable(): Boolean {
            return reusable
        }

        override fun sendMessage(content: String, mentions: List<String>, channel: String?) {
            lastBroadcastContent = content
            lastBroadcastMentions = mentions
            lastBroadcastChannel = channel
        }

        override fun sendPrivateMessage(
            content: String,
            recipientPeerID: String,
            recipientNickname: String,
            messageID: String?
        ) {
            lastPrivateContent = content
            lastPrivatePeer = recipientPeerID
            lastPrivateNickname = recipientNickname
            lastPrivateMessageId = messageID
        }

        override fun initiateNoiseHandshake(peerID: String) {
            lastHandshakePeer = peerID
        }

        override fun hasEstablishedSession(peerID: String): Boolean {
            return establishedPeers.contains(peerID)
        }

        override fun sendFileBroadcast(file: BitchatFilePacket) {
            lastBroadcastFile = file
        }

        override fun sendFilePrivate(recipientPeerID: String, file: BitchatFilePacket) {
            lastPrivateFilePeer = recipientPeerID
            lastPrivateFile = file
        }

        override fun cancelFileTransfer(transferId: String): Boolean {
            return cancelResults[transferId] == true
        }

        override fun getPeerNicknames(): Map<String, String> {
            return nicknames
        }

        override fun getPeerRSSI(): Map<String, Int> {
            return rssi
        }
    }

    private class RecordingListener : MeshListener {
        val sent: MutableList<Pair<String?, String?>> = mutableListOf()
        var stopped: Boolean = false

        override fun onMessageReceived(message: com.bitchat.android.model.BitchatMessage) {
            // no-op
        }

        override fun onPeerListUpdated(peers: List<String>) {
            // no-op
        }

        override fun onDeliveryAck(messageID: String, recipientPeerID: String) {
            // no-op
        }

        override fun onReadReceipt(messageID: String, recipientPeerID: String) {
            // no-op
        }

        override fun onVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) {
            // no-op
        }

        override fun onVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) {
            // no-op
        }

        override fun onSent(messageID: String?, recipientPeerID: String?) {
            sent.add(messageID to recipientPeerID)
        }

        override fun onStopped() {
            stopped = true
        }
    }
}
