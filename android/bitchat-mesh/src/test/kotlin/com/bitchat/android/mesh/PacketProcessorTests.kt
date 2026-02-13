package com.bitchat.android.mesh

import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PacketProcessorTests {
    @Test
    fun processPacketSkipsWhenPeerIdMissing() = runBlocking {
        val delegate = FakeDelegate()
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = basePacket(MessageType.ANNOUNCE.value)
            processor.processPacket(RoutedPacket(packet, peerID = null))

            delay(50)
            assertEquals(0, delegate.announceCount.get())
            assertEquals(0, delegate.relayCount.get())
        } finally {
            processor.shutdown()
        }
    }

    @Test
    fun processPacketStopsOnSecurityFailure() = runBlocking {
        val delegate = FakeDelegate(validateResult = false)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = basePacket(MessageType.ANNOUNCE.value)
            processor.processPacket(RoutedPacket(packet, peerID = "bbbbbbbbbbbbbbbb"))

            delay(50)
            assertEquals(0, delegate.announceCount.get())
            assertEquals(0, delegate.relayCount.get())
        } finally {
            processor.shutdown()
        }
    }

    @Test
    fun announceRelaysForSmallNetwork() = runBlocking {
        val delegate = FakeDelegate(networkSize = 2)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = basePacket(MessageType.ANNOUNCE.value, ttl = 4u)
            processor.processPacket(RoutedPacket(packet, peerID = "bbbbbbbbbbbbbbbb"))

            withTimeout(1000) {
                while (delegate.announceCount.get() == 0 || delegate.relayCount.get() == 0) {
                    delay(5)
                }
            }
            assertEquals(1, delegate.announceCount.get())
            assertEquals(1, delegate.relayCount.get())
        } finally {
            processor.shutdown()
        }
    }

    @Test
    fun noiseHandshakeAddressedToMeDoesNotRelay() = runBlocking {
        val delegate = FakeDelegate(networkSize = 2)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = basePacket(
                MessageType.NOISE_HANDSHAKE.value,
                ttl = 4u,
                recipient = hexToBytes(MY_PEER_ID)
            )
            processor.processPacket(RoutedPacket(packet, peerID = "bbbbbbbbbbbbbbbb"))

            withTimeout(1000) {
                while (delegate.handshakeCount.get() == 0) {
                    delay(5)
                }
            }
            assertEquals(1, delegate.handshakeCount.get())
            assertEquals(0, delegate.relayCount.get())
        } finally {
            processor.shutdown()
        }
    }

    @Test
    fun noiseHandshakeNotAddressedRelays() = runBlocking {
        val delegate = FakeDelegate(networkSize = 2)
        val processor = PacketProcessor(MY_PEER_ID).also { it.delegate = delegate }
        try {
            val packet = basePacket(
                MessageType.NOISE_HANDSHAKE.value,
                ttl = 4u,
                recipient = hexToBytes("1111111111111111")
            )
            processor.processPacket(RoutedPacket(packet, peerID = "bbbbbbbbbbbbbbbb"))

            withTimeout(1000) {
                while (delegate.relayCount.get() == 0) {
                    delay(5)
                }
            }
            assertEquals(0, delegate.handshakeCount.get())
            assertEquals(1, delegate.relayCount.get())
        } finally {
            processor.shutdown()
        }
    }

    private fun basePacket(
        type: UByte,
        ttl: UByte = 2u,
        recipient: ByteArray? = null
    ): BitchatPacket {
        return BitchatPacket(
            version = 2u,
            type = type,
            senderID = hexToBytes("aaaaaaaaaaaaaaaa"),
            recipientID = recipient,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = ttl,
            route = null
        )
    }

    private class FakeDelegate(
        private val networkSize: Int = 1,
        private val validateResult: Boolean = true
    ) : PacketProcessorDelegate {
        val announceCount = AtomicInteger(0)
        val handshakeCount = AtomicInteger(0)
        val relayCount = AtomicInteger(0)

        override fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean = validateResult
        override fun updatePeerLastSeen(peerID: String) {}
        override fun getPeerNickname(peerID: String): String? = null
        override fun getNetworkSize(): Int = networkSize
        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun handleNoiseHandshake(routed: RoutedPacket): Boolean {
            handshakeCount.incrementAndGet()
            return true
        }

        override fun handleNoiseEncrypted(routed: RoutedPacket) {}

        override fun handleAnnounce(routed: RoutedPacket) {
            announceCount.incrementAndGet()
        }

        override fun handleMessage(routed: RoutedPacket) {}
        override fun handleLeave(routed: RoutedPacket) {}
        override fun handleFragment(packet: BitchatPacket): BitchatPacket? = null
        override fun handleRequestSync(routed: RoutedPacket) {}
        override fun sendAnnouncementToPeer(peerID: String) {}
        override fun sendCachedMessages(peerID: String) {}

        override fun relayPacket(routed: RoutedPacket) {
            relayCount.incrementAndGet()
        }

        override fun sendToPeer(peerID: String, routed: RoutedPacket): Boolean = true
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
