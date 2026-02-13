package com.bitchat.android.noise

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoiseSessionManagerTests {
    @Test
    fun addAndRemoveSessionTracksState() {
        val keys = ByteArray(32) { 1 }
        val manager = NoiseSessionManager(keys, keys)

        val session = NoiseSession(
            peerID = "peer-a",
            isInitiator = true,
            localStaticPrivateKey = keys,
            localStaticPublicKey = keys
        )

        manager.addSession("peer-a", session)

        assertEquals(session, manager.getSession("peer-a"))
        assertFalse(manager.hasEstablishedSession("peer-a"))

        manager.removeSession("peer-a")

        assertNull(manager.getSession("peer-a"))
    }
}
