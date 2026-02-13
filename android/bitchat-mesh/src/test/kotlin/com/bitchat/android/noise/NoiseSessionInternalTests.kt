package com.bitchat.android.noise

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoiseSessionInternalTests {
    @Test
    fun needsRekeyReturnsTrueWhenMessageLimitExceeded() {
        val session = newSession()
        setPrivateField(session, "state", NoiseSession.NoiseSessionState.Established)
        setPrivateField(session, "messagesSent", 10_001L)

        assertTrue(session.needsRekey())
    }

    @Test
    fun needsRekeyReturnsTrueWhenTimeLimitExceeded() {
        val session = newSession()
        setPrivateField(session, "state", NoiseSession.NoiseSessionState.Established)
        val oldTime = System.currentTimeMillis() - com.bitchat.android.util.AppConstants.Noise.REKEY_TIME_LIMIT_MS - 1
        setPrivateField(session, "creationTime", oldTime)

        assertTrue(session.needsRekey())
    }

    @Test
    fun resetClearsSessionState() {
        val session = newSession()
        setPrivateField(session, "state", NoiseSession.NoiseSessionState.Established)
        setPrivateField(session, "messagesSent", 5L)
        setPrivateField(session, "messagesReceived", 3L)
        setPrivateField(session, "highestReceivedNonce", 9L)
        setPrivateField(session, "replayWindow", ByteArray(128) { 1 })
        setPrivateField(session, "remoteStaticPublicKey", ByteArray(32) { 2 })
        setPrivateField(session, "handshakeHash", ByteArray(32) { 3 })

        session.reset()

        assertTrue(session.getState() is NoiseSession.NoiseSessionState.Uninitialized)
        assertEquals(0L, getPrivateField<Long>(session, "messagesSent"))
        assertEquals(0L, getPrivateField<Long>(session, "messagesReceived"))
        assertEquals(0L, getPrivateField<Long>(session, "highestReceivedNonce"))
        assertArrayEquals(ByteArray(128), getPrivateField(session, "replayWindow"))
        assertNull(session.getRemoteStaticPublicKey())
        assertNull(session.getHandshakeHash())
    }

    @Test
    fun companionNonceHelpersRoundTrip() {
        val nonceToBytes = findPrivateMethod("nonceToBytes")
        val extractNonce = findPrivateMethod("extractNonceFromCiphertextPayload")

        val bytes = invokeMethod(nonceToBytes, 0x01020304L) as ByteArray
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), bytes)

        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x41)
        val pair = invokeMethod(extractNonce, payload)
        assertNotNull(pair)
        val result = pair as Pair<*, *>
        assertEquals(0x01020304L, result.first)
        assertArrayEquals(byteArrayOf(0x41), result.second as ByteArray)
    }

    private fun newSession(): NoiseSession {
        val keys = ByteArray(32) { 1 }
        return NoiseSession(
            peerID = "peer-c",
            isInitiator = true,
            localStaticPrivateKey = keys,
            localStaticPublicKey = keys
        )
    }

    private fun findPrivateMethod(name: String): java.lang.reflect.Method {
        val companionClass = NoiseSession::class.java.declaredClasses.firstOrNull { it.simpleName == "Companion" }
        val companionMethods = companionClass?.declaredMethods.orEmpty()
        val directMethods = NoiseSession::class.java.declaredMethods
        val method = companionMethods.firstOrNull { it.name == name }
            ?: companionMethods.firstOrNull { it.name.contains(name) }
            ?: directMethods.firstOrNull { it.name == name }
            ?: directMethods.firstOrNull { it.name.contains(name) }
        requireNotNull(method) { "Missing method $name" }
        method.isAccessible = true
        return method
    }

    private fun invokeMethod(method: java.lang.reflect.Method, vararg args: Any?): Any? {
        val target = if (method.declaringClass.simpleName == "Companion") {
            NoiseSession.Companion
        } else {
            null
        }
        return method.invoke(target, *args)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(target: Any, fieldName: String): T {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }
}
