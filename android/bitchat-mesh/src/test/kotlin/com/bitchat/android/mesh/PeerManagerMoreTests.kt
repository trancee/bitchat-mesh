package com.bitchat.android.mesh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeerManagerMoreTests {
    @Test
    fun updatesLastSeenAndRssi() {
        val manager = PeerManager()
        manager.clearAllPeers()

        manager.addOrUpdatePeer("peer-1", "alice")
        val before = manager.getPeerInfo("peer-1")!!.lastSeen

        manager.updatePeerLastSeen("peer-1")
        manager.updatePeerRSSI("peer-1", -42)

        val updated = manager.getPeerInfo("peer-1")
        assertTrue(updated!!.lastSeen >= before)
        assertEquals(-42, manager.getAllPeerRSSI()["peer-1"])
    }

    @Test
    fun debugInfoIncludesPeerAndDeviceMapping() {
        val manager = PeerManager()
        manager.clearAllPeers()
        manager.addOrUpdatePeer("peer-2", "bob")

        val debug = manager.getDebugInfo(mapOf("AA:BB" to "peer-2"))
        val deviceDebug = manager.getDebugInfoWithDeviceAddresses(mapOf("AA:BB" to "peer-2"))

        assertTrue(debug.contains("peer-2"))
        assertTrue(deviceDebug.contains("AA:BB"))
    }

    @Test
    fun fingerprintMappingRoundTrip() {
        val manager = PeerManager()
        manager.clearAllPeers()

        val fingerprint = manager.storeFingerprintForPeer("peer-3", ByteArray(32) { 1 })
        assertNotNull(fingerprint)
        assertTrue(manager.hasFingerprintForPeer("peer-3"))

        manager.updatePeerIDMapping("peer-3", "peer-4", fingerprint)
        assertEquals("peer-4", manager.getPeerIDForFingerprint(fingerprint))

        manager.clearAllFingerprints()
        assertTrue(manager.getAllPeerFingerprints().isEmpty())
    }

    @Test
    fun removesStalePeerWithDuplicateNickname() {
        val manager = PeerManager()
        manager.clearAllPeers()

        manager.addOrUpdatePeer("peer-old", "dup")
        val peersField = PeerManager::class.java.getDeclaredField("peers")
        peersField.isAccessible = true
        val peers = peersField.get(manager) as MutableMap<String, PeerInfo>
        val stale = peers["peer-old"]!!.copy(lastSeen = System.currentTimeMillis() - 60_000L)
        peers["peer-old"] = stale

        manager.addOrUpdatePeer("peer-new", "dup")

        assertTrue(manager.getPeerInfo("peer-old") == null)
        assertNotNull(manager.getPeerInfo("peer-new"))
    }

    @Test
    fun refreshPeerListNotifiesDelegate() {
        val manager = PeerManager()
        val updates = mutableListOf<List<String>>()
        manager.delegate = object : PeerManagerDelegate {
            override fun onPeerListUpdated(peerIDs: List<String>) { updates.add(peerIDs) }
            override fun onPeerRemoved(peerID: String) {}
        }

        manager.addOrUpdatePeer("peer-x", "x")
        manager.refreshPeerList()

        assertTrue(updates.isNotEmpty())
    }
}
