package com.bitchat.android.mesh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeerManagerTests {
    @Test
    fun updatePeerInfoAddsVerifiedPeer() {
        val manager = PeerManager()
        manager.clearAllPeers()
        manager.isPeerDirectlyConnected = { it == "peer-1" }

        val added = manager.updatePeerInfo(
            peerID = "peer-1",
            nickname = "alice",
            noisePublicKey = ByteArray(32) { 0x01 },
            signingPublicKey = ByteArray(32) { 0x02 },
            isVerified = true
        )

        assertTrue(added)
        val info = manager.getPeerInfo("peer-1")
        assertNotNull(info)
        assertTrue(info!!.isVerifiedNickname)
        assertTrue(info.isDirectConnection)
        assertEquals(1, manager.getVerifiedPeers().size)
    }

    @Test
    fun addOrUpdatePeerTracksAnnouncements() {
        val manager = PeerManager()
        manager.clearAllPeers()

        val first = manager.addOrUpdatePeer("peer-1", "alice")
        val second = manager.addOrUpdatePeer("peer-1", "alice")

        assertTrue(first)
        assertTrue(!second)

        assertTrue(!manager.hasAnnouncedToPeer("peer-1"))
        manager.markPeerAsAnnouncedTo("peer-1")
        assertTrue(manager.hasAnnouncedToPeer("peer-1"))
    }

    @Test
    fun removePeerNotifiesDelegate() {
        val manager = PeerManager()
        manager.clearAllPeers()
        val delegate = FakeDelegate()
        manager.delegate = delegate

        manager.addOrUpdatePeer("peer-1", "alice")
        manager.removePeer("peer-1")

        assertEquals(listOf("peer-1"), delegate.removedPeers)
        assertEquals(listOf<String>(), manager.getActivePeerIDs())
    }

    private class FakeDelegate : PeerManagerDelegate {
        val removedPeers = mutableListOf<String>()
        val updates = mutableListOf<List<String>>()

        override fun onPeerListUpdated(peerIDs: List<String>) {
            updates.add(peerIDs)
        }

        override fun onPeerRemoved(peerID: String) {
            removedPeers.add(peerID)
        }
    }
}
