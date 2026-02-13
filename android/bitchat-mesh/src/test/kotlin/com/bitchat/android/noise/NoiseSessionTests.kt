package com.bitchat.android.noise

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoiseSessionTests {
    @Test
    fun invalidKeysPutSessionInFailedState() {
        val zeroKeys = ByteArray(32)
        val session = NoiseSession(
            peerID = "peer-b",
            isInitiator = true,
            localStaticPrivateKey = zeroKeys,
            localStaticPublicKey = zeroKeys
        )

        assertTrue(session.getState() is NoiseSession.NoiseSessionState.Failed)
    }
}
