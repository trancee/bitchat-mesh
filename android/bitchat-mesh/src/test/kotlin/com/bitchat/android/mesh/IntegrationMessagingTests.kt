package com.bitchat.android.mesh

import android.content.Context
import com.bitchat.android.model.BitchatFilePacket
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.mesh.MeshManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationMessagingTests {
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
    fun directMessageUsesTransport() {
        val transport = FakeTransport()
        val meshManager = MeshManager(mockContext(), transport)

        meshManager.start("alice")
        meshManager.sendPrivateMessage("secret", "peer-2", "bob", "msg-1")

        assertEquals("secret", transport.lastPrivateContent)
        assertEquals("peer-2", transport.lastPrivatePeer)
        assertEquals("bob", transport.lastPrivateNickname)
        assertEquals("msg-1", transport.lastPrivateMessageId)
    }

    @Test
    fun routedMessageForwardsToNextHopOnly() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeRelayDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val nextHop = "0a0b0c0d0e0f1011"
        delegate.sendResults[nextHop] = true

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = hexToBytes("2222222222222222"),
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = listOf(hexToBytes(myPeerID), hexToBytes(nextHop))
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(listOf(nextHop), delegate.sendToPeerCalls)
        assertEquals(0, delegate.broadcastCount)
        assertEquals(2u.toUByte(), delegate.lastSentRouted?.packet?.ttl)
    }

    private fun mockContext(): Context {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        return context
    }

    private class FakeTransport : MeshTransport {
        override var delegate: BluetoothMeshDelegate? = null
        override val myPeerID: String = "peer-1"

        var lastPrivateContent: String? = null
        var lastPrivatePeer: String? = null
        var lastPrivateNickname: String? = null
        var lastPrivateMessageId: String? = null

        override fun startServices() {}

        override fun stopServices() {}

        override fun isReusable(): Boolean = true

        override fun sendMessage(content: String, mentions: List<String>, channel: String?) {}

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

        override fun initiateNoiseHandshake(peerID: String) {}

        override fun hasEstablishedSession(peerID: String): Boolean = false

        override fun sendFileBroadcast(file: BitchatFilePacket) {}

        override fun sendFilePrivate(recipientPeerID: String, file: BitchatFilePacket) {}

        override fun cancelFileTransfer(transferId: String): Boolean = false

        override fun getPeerNicknames(): Map<String, String> = emptyMap()

        override fun getPeerRSSI(): Map<String, Int> = emptyMap()
    }

    private class FakeRelayDelegate : PacketRelayManagerDelegate {
        var broadcastCount = 0
        val sendToPeerCalls = mutableListOf<String>()
        val sendResults = mutableMapOf<String, Boolean>()
        var lastSentRouted: RoutedPacket? = null

        override fun getNetworkSize(): Int = 5

        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun broadcastPacket(routed: RoutedPacket) {
            broadcastCount += 1
        }

        override fun sendToPeer(peerID: String, routed: RoutedPacket): Boolean {
            sendToPeerCalls.add(peerID)
            lastSentRouted = routed
            return sendResults[peerID] ?: false
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val output = ByteArray(8)
        var index = 0
        var outIndex = 0
        while (index + 1 < hex.length && outIndex < 8) {
            val byteValue = hex.substring(index, index + 2).toInt(16).toByte()
            output[outIndex] = byteValue
            index += 2
            outIndex += 1
        }
        return output
    }
}
