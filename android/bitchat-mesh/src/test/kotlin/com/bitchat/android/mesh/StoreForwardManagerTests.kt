package com.bitchat.android.mesh

import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.protocol.SpecialRecipients
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoreForwardManagerTests {
    @Test
    fun cacheMessageSkipsBroadcastAndNoise() {
        val manager = StoreForwardManager()
        manager.clearAllCache()

        val broadcast = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = SpecialRecipients.BROADCAST,
            timestamp = 1uL,
            payload = byteArrayOf(0x01),
            signature = null,
            ttl = 3u,
            route = null
        )
        manager.cacheMessage(broadcast, "msg-1")
        assertEquals(0, manager.getCachedMessageCount("peer-1"))

        val noise = BitchatPacket(
            version = 2u,
            type = MessageType.NOISE_HANDSHAKE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = "peer-1".toByteArray(),
            timestamp = 1uL,
            payload = byteArrayOf(0x02),
            signature = null,
            ttl = 3u,
            route = null
        )
        manager.cacheMessage(noise, "msg-2")
        assertEquals(0, manager.getCachedMessageCount("peer-1"))
    }

    @Test
    fun cacheAndSendFavoriteMessages() = runBlocking {
        val manager = StoreForwardManager()
        manager.clearAllCache()
        val delegate = FakeDelegate(isFavorite = true, isOnline = false)
        manager.delegate = delegate

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = "peer-1".toByteArray(),
            timestamp = 1uL,
            payload = byteArrayOf(0x02),
            signature = null,
            ttl = 3u,
            route = null
        )
        manager.cacheMessage(packet, "msg-1")
        assertEquals(1, manager.getCachedMessageCount("peer-1"))

        manager.sendCachedMessages("peer-1")

        withTimeout(1000) {
            while (delegate.sentPackets.isEmpty()) {
                delay(5)
            }
        }
        assertEquals(1, delegate.sentPackets.size)

        manager.sendCachedMessages("peer-1")
        delay(50)
        assertEquals(1, delegate.sentPackets.size)
    }

    @Test
    fun shouldCacheForPeerDependsOnOnlineAndFavorite() {
        val manager = StoreForwardManager()
        manager.delegate = FakeDelegate(isFavorite = true, isOnline = false)
        assertTrue(manager.shouldCacheForPeer("peer-1"))

        manager.delegate = FakeDelegate(isFavorite = true, isOnline = true)
        assertFalse(manager.shouldCacheForPeer("peer-1"))

        manager.delegate = FakeDelegate(isFavorite = false, isOnline = false)
        assertFalse(manager.shouldCacheForPeer("peer-1"))
    }

    @Test
    fun cacheMessageStoresRegularForNonFavorite() {
        val manager = StoreForwardManager()
        manager.clearAllCache()
        manager.delegate = FakeDelegate(isFavorite = false, isOnline = false)

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = "peer-2".toByteArray(),
            timestamp = 1uL,
            payload = byteArrayOf(0x02),
            signature = null,
            ttl = 3u,
            route = null
        )
        manager.cacheMessage(packet, "msg-regular")

        assertEquals(1, manager.getCachedMessageCount("peer-2"))
    }

    @Test
    fun sendCachedMessagesSkipsDelivered() = runBlocking {
        val manager = StoreForwardManager()
        manager.clearAllCache()
        val delegate = FakeDelegate(isFavorite = false, isOnline = false)
        manager.delegate = delegate

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = "peer-3".toByteArray(),
            timestamp = 1uL,
            payload = byteArrayOf(0x02),
            signature = null,
            ttl = 3u,
            route = null
        )
        manager.cacheMessage(packet, "msg-delivered")
        manager.markMessageAsDelivered("msg-delivered")

        manager.sendCachedMessages("peer-3")
        delay(50)

        assertTrue(delegate.sentPackets.isEmpty())
    }

    @Test
    fun forceCleanupRemovesExpiredMessages() {
        val manager = StoreForwardManager()
        manager.clearAllCache()
        manager.delegate = FakeDelegate(isFavorite = false, isOnline = false)

        val packet = BitchatPacket(
            version = 2u,
            type = MessageType.MESSAGE.value,
            senderID = ByteArray(8) { 0x01 },
            recipientID = "peer-4".toByteArray(),
            timestamp = 1uL,
            payload = byteArrayOf(0x02),
            signature = null,
            ttl = 3u,
            route = null
        )

        val storedClass = Class.forName("com.bitchat.android.mesh.StoreForwardManager\$StoredMessage")
        val constructor = storedClass.getDeclaredConstructor(
            BitchatPacket::class.java,
            Long::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        val stored = constructor.newInstance(
            packet,
            System.currentTimeMillis() - com.bitchat.android.util.AppConstants.StoreForward.MESSAGE_CACHE_TIMEOUT_MS - 1,
            "msg-old",
            false
        )

        val cacheField = StoreForwardManager::class.java.getDeclaredField("messageCache")
        cacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(manager) as MutableList<Any>
        cache.add(stored)

        manager.forceCleanup()

        assertEquals(0, manager.getCachedMessageCount("peer-4"))
    }

    private class FakeDelegate(
        private val isFavorite: Boolean,
        private val isOnline: Boolean
    ) : StoreForwardManagerDelegate {
        val sentPackets = mutableListOf<BitchatPacket>()

        override fun isFavorite(peerID: String): Boolean = isFavorite
        override fun isPeerOnline(peerID: String): Boolean = isOnline
        override fun sendPacket(packet: BitchatPacket) {
            sentPackets.add(packet)
        }
    }
}
