package com.bitchat.android.mesh

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeerFingerprintManagerTests {
    @Test
    fun storeAndUpdateMappings() {
        val manager = PeerFingerprintManager.getInstance()
        manager.clearAllFingerprints()

        val publicKey = ByteArray(32) { 0x01 }
        val fingerprint = manager.storeFingerprintForPeer("peer-a", publicKey)

        assertEquals(fingerprint, manager.getFingerprintForPeer("peer-a"))
        assertEquals("peer-a", manager.getPeerIDForFingerprint(fingerprint))

        manager.updatePeerIDMapping("peer-a", "peer-b", fingerprint)
        assertNull(manager.getFingerprintForPeer("peer-a"))
        assertEquals(fingerprint, manager.getFingerprintForPeer("peer-b"))
        assertEquals("peer-b", manager.getPeerIDForFingerprint(fingerprint))
    }

    @Test
    fun removePeerAndFingerprint() {
        val manager = PeerFingerprintManager.getInstance()
        manager.clearAllFingerprints()

        val publicKey = ByteArray(32) { 0x02 }
        val fingerprint = manager.storeFingerprintForPeer("peer-a", publicKey)

        manager.removePeer("peer-a")
        assertNull(manager.getFingerprintForPeer("peer-a"))
        assertNull(manager.getPeerIDForFingerprint(fingerprint))

        manager.storeFingerprintForPeer("peer-b", publicKey)
        manager.removeFingerprint(fingerprint)
        assertNull(manager.getFingerprintForPeer("peer-b"))
        assertNull(manager.getPeerIDForFingerprint(fingerprint))
    }

    @Test
    fun debugInfoIncludesMappingCount() {
        val manager = PeerFingerprintManager.getInstance()
        manager.clearAllFingerprints()
        manager.storeFingerprintForPeer("peer-a", ByteArray(32) { 0x03 })

        val info = manager.getDebugInfo()
        assertTrue(info.contains("Total mappings"))
    }
}
