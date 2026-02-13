package com.bitchat.android.mesh

import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeshEndToEndTests {
    @Test
    fun directPacketDeliversAndRelaysBroadcast() = runBlocking {
        val delegate = FakePacketDelegate(networkSize = 2)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = BitchatPacket(
                version = 2u,
                type = MessageType.MESSAGE.value,
                senderID = hexToBytes("bbbbbbbbbbbbbbbb"),
                recipientID = null,
                timestamp = 1uL,
                payload = byteArrayOf(0x01),
                signature = null,
                ttl = 3u,
                route = null
            )

            processor.processPacket(RoutedPacket(packet, peerID = "bbbbbbbbbbbbbbbb"))

            withTimeout(1000) { delegate.messageHandled.await() }
            val relayed = withTimeout(1000) { delegate.relayed.await() }
            assertEquals(2u.toUByte(), relayed.packet.ttl)
        } finally {
            processor.shutdown()
        }
    }

    @Test
    fun routedPacketForwardsToNextHop() = runBlocking {
        val delegate = FakePacketDelegate(networkSize = 5)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val nextHop = "0a0b0c0d0e0f1011"
            val packet = BitchatPacket(
                version = 2u,
                type = MessageType.MESSAGE.value,
                senderID = hexToBytes("1111111111111111"),
                recipientID = hexToBytes("2222222222222222"),
                timestamp = 2uL,
                payload = byteArrayOf(0x02),
                signature = null,
                ttl = 3u,
                route = listOf(hexToBytes(MY_PEER_ID), hexToBytes(nextHop))
            )

            processor.processPacket(RoutedPacket(packet, peerID = "1111111111111111"))

            withTimeout(1000) { delegate.messageHandled.await() }
            val forwarded = withTimeout(1000) { delegate.sentToPeer.await() }
            assertEquals(nextHop, forwarded.first)
            assertEquals(2u.toUByte(), forwarded.second.packet.ttl)
            assertTrue(delegate.relayCalls.isEmpty())
        } finally {
            processor.shutdown()
        }
    }

    private class FakePacketDelegate(private val networkSize: Int) : PacketProcessorDelegate {
        val messageHandled = CompletableDeferred<Unit>()
        val relayed = CompletableDeferred<RoutedPacket>()
        val sentToPeer = CompletableDeferred<Pair<String, RoutedPacket>>()
        val relayCalls = mutableListOf<RoutedPacket>()

        override fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean = true
        override fun updatePeerLastSeen(peerID: String) {}
        override fun getPeerNickname(peerID: String): String? = null
        override fun getNetworkSize(): Int = networkSize
        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun handleNoiseHandshake(routed: RoutedPacket): Boolean = true
        override fun handleNoiseEncrypted(routed: RoutedPacket) {}
        override fun handleAnnounce(routed: RoutedPacket) {}
        override fun handleMessage(routed: RoutedPacket) {
            if (!messageHandled.isCompleted) {
                messageHandled.complete(Unit)
            }
        }
        override fun handleLeave(routed: RoutedPacket) {}
        override fun handleFragment(packet: BitchatPacket): BitchatPacket? = null
        override fun handleRequestSync(routed: RoutedPacket) {}
        override fun sendAnnouncementToPeer(peerID: String) {}
        override fun sendCachedMessages(peerID: String) {}

        override fun relayPacket(routed: RoutedPacket) {
            relayCalls.add(routed)
            if (!relayed.isCompleted) {
                relayed.complete(routed)
            }
        }

        override fun sendToPeer(peerID: String, routed: RoutedPacket): Boolean {
            if (!sentToPeer.isCompleted) {
                sentToPeer.complete(peerID to routed)
            }
            return true
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

    private companion object {
        const val MY_PEER_ID = "0102030405060708"
    }
}
