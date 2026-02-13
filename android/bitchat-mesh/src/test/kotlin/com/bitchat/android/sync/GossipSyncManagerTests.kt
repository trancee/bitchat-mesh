package com.bitchat.android.sync

import com.bitchat.android.model.RequestSyncPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import com.bitchat.android.util.AppConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GossipSyncManagerTests {
    private class TestDelegate : GossipSyncManager.Delegate {
        val sent = mutableListOf<BitchatPacket>()
        val sentToPeer = mutableListOf<Pair<String, BitchatPacket>>()

        override fun sendPacket(packet: BitchatPacket) {
            sent.add(packet)
        }

        override fun sendPacketToPeer(peerID: String, packet: BitchatPacket) {
            sentToPeer.add(peerID to packet)
        }

        override fun signPacketForBroadcast(packet: BitchatPacket): BitchatPacket {
            return packet
        }
    }

    private class TestConfigProvider(
        private val seenCapacity: Int = 2,
        private val maxBytes: Int = 256,
        private val fpr: Double = 0.1
    ) : GossipSyncManager.ConfigProvider {
        override fun seenCapacity(): Int = seenCapacity
        override fun gcsMaxBytes(): Int = maxBytes
        override fun gcsTargetFpr(): Double = fpr
    }

    @Test
    fun broadcastMessagesTrimToCapacity() {
        val manager = newManager()
        val delegate = TestDelegate()
        manager.delegate = delegate

        val sender = "0102030405060708"
        val baseTime = System.currentTimeMillis()
        val packets = listOf(
            messagePacket(sender, baseTime + 1),
            messagePacket(sender, baseTime + 2),
            messagePacket(sender, baseTime + 3)
        )

        packets.forEach { manager.onPublicPacketSeen(it) }

        manager.handleRequestSync("a1a2a3a4a5a6a7a8", emptyRequest())

        assertEquals(2, delegate.sentToPeer.size)
    }

    @Test
    fun staleAnnouncementIsIgnored() {
        val manager = newManager()
        val delegate = TestDelegate()
        manager.delegate = delegate

        val staleTime = System.currentTimeMillis() - AppConstants.Mesh.STALE_PEER_TIMEOUT_MS - 1
        val announcement = announcePacket("1111111111111111", staleTime)

        manager.onPublicPacketSeen(announcement)
        manager.handleRequestSync("b1b2b3b4b5b6b7b8", emptyRequest())

        assertTrue(delegate.sentToPeer.isEmpty())
    }

    @Test
    fun removeAnnouncementForPeerAlsoPrunesMessages() {
        val manager = newManager()
        val delegate = TestDelegate()
        manager.delegate = delegate

        val peer = "2222222222222222"
        manager.onPublicPacketSeen(announcePacket(peer, System.currentTimeMillis()))
        manager.onPublicPacketSeen(messagePacket(peer, System.currentTimeMillis() + 1))

        manager.removeAnnouncementForPeer(peer)
        manager.handleRequestSync("c1c2c3c4c5c6c7c8", emptyRequest())

        assertTrue(delegate.sentToPeer.isEmpty())
    }

    @Test
    fun handleRequestSyncSkipsPacketsAlreadyInFilter() {
        val manager = newManager()
        val delegate = TestDelegate()
        manager.delegate = delegate

        val sender = "3333333333333333"
        val announcement = announcePacket(sender, System.currentTimeMillis())
        val message = messagePacket(sender, System.currentTimeMillis() + 1)
        manager.onPublicPacketSeen(announcement)
        manager.onPublicPacketSeen(message)

        val ids = listOf(
            PacketIdUtil.computeIdBytes(announcement),
            PacketIdUtil.computeIdBytes(message)
        )
        val params = GCSFilter.buildFilter(ids, 256, 0.1)
        val request = RequestSyncPacket(p = params.p, m = params.m, data = params.data)

        manager.handleRequestSync("d1d2d3d4d5d6d7d8", request)

        assertTrue(delegate.sentToPeer.isEmpty())
    }

    private fun newManager(): GossipSyncManager {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        return GossipSyncManager("0102030405060708", scope, TestConfigProvider())
    }

    private fun emptyRequest(): RequestSyncPacket {
        val p = GCSFilter.deriveP(0.1)
        return RequestSyncPacket(p = p, m = 1, data = ByteArray(0))
    }

    private fun messagePacket(senderHex: String, timestampMs: Long): BitchatPacket {
        return BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes(senderHex),
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = timestampMs.toULong(),
            payload = byteArrayOf(0x01),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
    }

    private fun announcePacket(senderHex: String, timestampMs: Long): BitchatPacket {
        return BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            senderID = hexToBytes(senderHex),
            recipientID = null,
            timestamp = timestampMs.toULong(),
            payload = byteArrayOf(0x02),
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
}
