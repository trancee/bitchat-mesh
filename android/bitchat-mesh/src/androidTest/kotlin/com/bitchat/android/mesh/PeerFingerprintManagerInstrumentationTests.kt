package com.bitchat.android.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerFingerprintManagerInstrumentationTests {
    private lateinit var manager: PeerFingerprintManager

    @Before
    fun setUp() {
        manager = PeerFingerprintManager.getInstance()
        manager.clearAllFingerprints()
    }

    @Test
    fun storeFingerprintCreatesBidirectionalMapping() {
        val publicKey = ByteArray(32) { 0x01 }
        val fingerprint = manager.storeFingerprintForPeer("peer-1", publicKey)

        assertEquals(fingerprint, manager.getFingerprintForPeer("peer-1"))
        assertEquals("peer-1", manager.getPeerIDForFingerprint(fingerprint))
        assertTrue(manager.hasFingerprintForPeer("peer-1"))
    }

    @Test
    fun updatePeerIdMappingReplacesOld() {
        val publicKey = ByteArray(32) { 0x02 }
        val fingerprint = manager.storeFingerprintForPeer("peer-old", publicKey)

        manager.updatePeerIDMapping("peer-old", "peer-new", fingerprint)

        assertNull(manager.getFingerprintForPeer("peer-old"))
        assertEquals(fingerprint, manager.getFingerprintForPeer("peer-new"))
        assertEquals("peer-new", manager.getPeerIDForFingerprint(fingerprint))
    }

    @Test
    fun removePeerAndFingerprintClearMappings() {
        val publicKey = ByteArray(32) { 0x03 }
        val fingerprint = manager.storeFingerprintForPeer("peer-2", publicKey)

        manager.removePeer("peer-2")
        assertNull(manager.getFingerprintForPeer("peer-2"))
        assertNull(manager.getPeerIDForFingerprint(fingerprint))

        val fingerprint2 = manager.storeFingerprintForPeer("peer-3", ByteArray(32) { 0x04 })
        manager.removeFingerprint(fingerprint2)
        assertNull(manager.getPeerIDForFingerprint(fingerprint2))
        assertNull(manager.getFingerprintForPeer("peer-3"))
    }

    @Test
    fun getDebugInfoMentionsMappings() {
        manager.storeFingerprintForPeer("peer-4", ByteArray(32) { 0x05 })

        val info = manager.getDebugInfo()

        assertNotNull(info)
        assertTrue(info.contains("Total mappings"))
    }

    @Test
    fun clearAllFingerprintsEmptiesMaps() {
        manager.storeFingerprintForPeer("peer-5", ByteArray(32) { 0x06 })
        manager.clearAllFingerprints()

        assertFalse(manager.hasFingerprintForPeer("peer-5"))
    }
}
