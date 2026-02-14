package com.bitchat.android.mesh

import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PacketRelayManagerTests {
    private class NetworkDelegate(private val size: Int) : PacketRelayManagerDelegate {
        var broadcastCount = 0
        val sendToPeerCalls = mutableListOf<String>()
        val sendResults = mutableMapOf<String, Boolean>()

        override fun getNetworkSize(): Int = size

        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun broadcastPacket(routed: RoutedPacket) {
            broadcastCount += 1
        }

        override fun sendToPeer(peerID: String, routed: RoutedPacket): Boolean {
            sendToPeerCalls.add(peerID)
            return sendResults[peerID] ?: false
        }
    }

    private class FakeDelegate : PacketRelayManagerDelegate {
        var broadcastCount = 0
        val sendToPeerCalls = mutableListOf<String>()
        val sendResults = mutableMapOf<String, Boolean>()

        override fun getNetworkSize(): Int = 5

        override fun getBroadcastRecipient(): ByteArray = SpecialRecipients.BROADCAST

        override fun broadcastPacket(routed: RoutedPacket) {
            broadcastCount += 1
        }

        override fun sendToPeer(peerID: String, routed: RoutedPacket): Boolean {
            sendToPeerCalls.add(peerID)
            return sendResults[peerID] ?: false
        }
    }

    @Test
    fun dropsRoutedPacketWhenNotInRoute() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = hexToBytes("2222222222222222"),
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = listOf(hexToBytes("9999999999999999"))
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertTrue(delegate.sendToPeerCalls.isEmpty())
        assertEquals(0, delegate.broadcastCount)
    }

    @Test
    fun skipsRelayWhenAddressedToMe() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = hexToBytes(myPeerID),
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = null
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertTrue(delegate.sendToPeerCalls.isEmpty())
        assertEquals(0, delegate.broadcastCount)
    }

    @Test
    fun skipsRelayWhenFromSelf() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = null,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = null
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = myPeerID))

        assertTrue(delegate.sendToPeerCalls.isEmpty())
        assertEquals(0, delegate.broadcastCount)
    }

    @Test
    fun skipsRelayWhenTtlExpired() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = null,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 0u,
            route = null
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(0, delegate.broadcastCount)
    }

    @Test
    fun dropsRoutedPacketWithDuplicateHops() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val hop = hexToBytes(myPeerID)
        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = hexToBytes("2222222222222222"),
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = listOf(hop, hop)
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(0, delegate.broadcastCount)
        assertTrue(delegate.sendToPeerCalls.isEmpty())
    }

    @Test
    fun relaysInSmallNetwork() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = NetworkDelegate(size = 2)
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = null,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 1u,
            route = null
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(1, delegate.broadcastCount)
    }

    @Test
    fun relaysWhenTtlHigh() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = NetworkDelegate(size = 100)
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val packet = BitchatPacket(
            version = 1u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = null,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 4u,
            route = null
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(1, delegate.broadcastCount)
    }

    @Test
    fun forwardsRoutedPacketToNextHop() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
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
    }

    @Test
    fun forwardsRoutedPacketToRecipientWhenLastIntermediate() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val recipient = "2222222222222222"
        delegate.sendResults[recipient] = true

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes("1111111111111111"),
            recipientID = hexToBytes(recipient),
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = listOf(hexToBytes(myPeerID))
        )

        relay.handlePacketRelay(RoutedPacket(packet, peerID = "1111111111111111"))

        assertEquals(listOf(recipient), delegate.sendToPeerCalls)
        assertEquals(0, delegate.broadcastCount)
    }

    @Test
    fun dropsRoutedPacketWhenNextHopUnavailable() = runBlocking {
        val myPeerID = "0102030405060708"
        val delegate = FakeDelegate()
        val relay = PacketRelayManager(myPeerID)
        relay.delegate = delegate

        val nextHop = "0a0b0c0d0e0f1011"
        delegate.sendResults[nextHop] = false

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
