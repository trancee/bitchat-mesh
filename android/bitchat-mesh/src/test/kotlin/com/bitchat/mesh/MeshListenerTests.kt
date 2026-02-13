package com.bitchat.mesh

import android.content.Context
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.TransferProgressEvent
import com.bitchat.android.mesh.TransferProgressManager
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MeshListenerTests {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun delegateForwardsMeshEvents() {
        val meshManager = MeshManager(mockContext())
        val listener = RecordingListener()
        meshManager.setListener(listener)

        val delegate = buildDelegate(meshManager)
        val message = BitchatMessage(
            sender = "alice",
            content = "hi",
            timestamp = Date(),
            senderPeerID = "peerA"
        )

        delegate.didReceiveMessage(message)
        delegate.didEstablishSession("peerA")
        delegate.didUpdateRSSI("peerA", -42)
        delegate.didReceiveDeliveryAck("msg-1", "peerA")
        delegate.didReceiveReadReceipt("msg-2", "peerA")
        delegate.didReceiveVerifyChallenge("peerA", byteArrayOf(0x01), 123L)
        delegate.didReceiveVerifyResponse("peerA", byteArrayOf(0x02), 124L)
        delegate.didReceiveFileTransfer("peerA", "file.txt", 10L, "text/plain", "/tmp/file.txt")

        assertEquals(1, listener.receivedMessages.size)
        assertEquals(listOf("peerA"), listener.established)
        assertEquals(listOf("peerA" to -42), listener.rssiUpdates)
        assertEquals(listOf("msg-1" to "peerA"), listener.deliveryAcks)
        assertEquals(listOf("msg-2" to "peerA"), listener.readReceipts)
        assertEquals(listOf("peerA"), listener.verifyChallenges)
        assertEquals(listOf("peerA"), listener.verifyResponses)
        assertEquals(listOf("peerA" to "file.txt"), listener.filesReceived)
    }

    @Test
    fun delegateForwardsPeerListChanges() {
        val meshManager = MeshManager(mockContext())
        val listener = RecordingListener()
        meshManager.setListener(listener)
        val delegate = buildDelegate(meshManager)

        delegate.didUpdatePeerList(listOf("peerA", "peerB"))
        delegate.didUpdatePeerList(listOf("peerB", "peerC"))

        assertEquals(listOf("peerA", "peerB", "peerC"), listener.found)
        assertEquals(listOf("peerA", "peerB", "peerC"), listener.connected)
        assertEquals(listOf("peerA"), listener.lost)
        assertEquals(listOf("peerA"), listener.disconnected)
        assertEquals(2, listener.peerListUpdates)
    }

    @Test
    fun sendCallsInvokeOnSent() {
        val meshManager = MeshManager(mockContext())
        val listener = RecordingListener()
        meshManager.setListener(listener)

        meshManager.sendBroadcastMessage("hi")
        meshManager.sendPrivateMessage("secret", "peerA", "alice", "msg-123")

        assertEquals(2, listener.sent.size)
        assertEquals(null to null, listener.sent.first())
        assertEquals("msg-123" to "peerA", listener.sent.last())
    }

    @Test
    fun startStopEndpoints() {
        val listener = RecordingListener()

        listener.onStarted()
        listener.onStopped()

        assertTrue(listener.started)
        assertTrue(listener.stopped)
    }

    @Test
    fun transferProgressIsForwarded() = runBlocking {
        val meshManager = MeshManager(mockContext())
        val listener = RecordingListener()
        meshManager.setListener(listener)

        TransferProgressManager.start("transfer-1", 2)
        TransferProgressManager.progress("transfer-1", 1, 2)
        TransferProgressManager.complete("transfer-1", 2)

        assertTrue(listener.transferLatch.await(2, TimeUnit.SECONDS))
        val ordered = listener.transferEvents.sortedBy { it.sent }
        assertEquals(
            listOf(
                TransferProgressEvent("transfer-1", 0, 2, false),
                TransferProgressEvent("transfer-1", 1, 2, false),
                TransferProgressEvent("transfer-1", 2, 2, true)
            ),
            ordered
        )
    }

    private fun buildDelegate(meshManager: MeshManager): BluetoothMeshDelegate {
        val method = MeshManager::class.java.getDeclaredMethod("buildDelegate")
        method.isAccessible = true
        return method.invoke(meshManager) as BluetoothMeshDelegate
    }

    private fun mockContext(): Context {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        return context
    }

    private class RecordingListener : MeshListener {
        val receivedMessages: MutableList<BitchatMessage> = mutableListOf()
        val established: MutableList<String> = mutableListOf()
        val rssiUpdates: MutableList<Pair<String, Int>> = mutableListOf()
        val deliveryAcks: MutableList<Pair<String, String>> = mutableListOf()
        val readReceipts: MutableList<Pair<String, String>> = mutableListOf()
        val verifyChallenges: MutableList<String> = mutableListOf()
        val verifyResponses: MutableList<String> = mutableListOf()
        val filesReceived: MutableList<Pair<String, String>> = mutableListOf()
        val found: MutableList<String> = mutableListOf()
        val lost: MutableList<String> = mutableListOf()
        val connected: MutableList<String> = mutableListOf()
        val disconnected: MutableList<String> = mutableListOf()
        var peerListUpdates: Int = 0
        val sent: MutableList<Pair<String?, String?>> = mutableListOf()
        var started = false
        var stopped = false
        val transferEvents: MutableList<TransferProgressEvent> = mutableListOf()
        val transferLatch = CountDownLatch(3)

        override fun onMessageReceived(message: BitchatMessage) {
            receivedMessages.add(message)
        }

        override fun onPeerListUpdated(peers: List<String>) {
            peerListUpdates += 1
        }

        override fun onFound(peerID: String) {
            found.add(peerID)
        }

        override fun onLost(peerID: String) {
            lost.add(peerID)
        }

        override fun onConnected(peerID: String) {
            connected.add(peerID)
        }

        override fun onDisconnected(peerID: String) {
            disconnected.add(peerID)
        }

        override fun onEstablished(peerID: String) {
            established.add(peerID)
        }

        override fun onRSSIUpdated(peerID: String, rssi: Int) {
            rssiUpdates.add(peerID to rssi)
        }

        override fun onStarted() {
            started = true
        }

        override fun onStopped() {
            stopped = true
        }

        override fun onSent(messageID: String?, recipientPeerID: String?) {
            sent.add(messageID to recipientPeerID)
        }

        override fun onDeliveryAck(messageID: String, recipientPeerID: String) {
            deliveryAcks.add(messageID to recipientPeerID)
        }

        override fun onReadReceipt(messageID: String, recipientPeerID: String) {
            readReceipts.add(messageID to recipientPeerID)
        }

        override fun onVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) {
            verifyChallenges.add(peerID)
        }

        override fun onVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) {
            verifyResponses.add(peerID)
        }

        override fun onTransferProgress(transferId: String, sent: Int, total: Int, completed: Boolean) {
            transferEvents.add(TransferProgressEvent(transferId, sent, total, completed))
            transferLatch.countDown()
        }

        override fun onFileReceived(peerID: String, fileName: String, fileSize: Long, mimeType: String, localPath: String) {
            filesReceived.add(peerID to fileName)
        }
    }
}
