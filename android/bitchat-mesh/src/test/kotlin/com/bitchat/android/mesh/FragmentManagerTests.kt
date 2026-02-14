package com.bitchat.android.mesh

import com.bitchat.android.model.FragmentPayload
import com.bitchat.android.util.AppConstants
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FragmentManagerTests {
    @Test
    fun cleanupOldFragmentsRemovesStaleEntries() {
        val manager = FragmentManager()
        val incoming = getIncomingFragments(manager)
        val metadata = getFragmentMetadata(manager)
        val now = System.currentTimeMillis()
        val staleId = "stale-frag"
        val freshId = "fresh-frag"

        incoming[staleId] = mutableMapOf(0 to byteArrayOf(0x01))
        metadata[staleId] = Triple(0x01u, 2, now - AppConstants.Fragmentation.FRAGMENT_TIMEOUT_MS - 1)

        incoming[freshId] = mutableMapOf(0 to byteArrayOf(0x02))
        metadata[freshId] = Triple(0x02u, 2, now)

        invokeCleanup(manager)

        assertTrue(!incoming.containsKey(staleId))
        assertTrue(!metadata.containsKey(staleId))
        assertTrue(incoming.containsKey(freshId))
        assertTrue(metadata.containsKey(freshId))
    }

    @Test
    fun clearAllFragmentsEmptiesMaps() {
        val manager = FragmentManager()
        val incoming = getIncomingFragments(manager)
        val metadata = getFragmentMetadata(manager)

        incoming["frag-1"] = mutableMapOf(0 to byteArrayOf(0x01))
        metadata["frag-1"] = Triple(0x01u, 1, System.currentTimeMillis())

        manager.clearAllFragments()

        assertEquals(0, incoming.size)
        assertEquals(0, metadata.size)
    }

    @Test
    fun handleFragmentReassemblesAndSuppressesTtl() {
        val manager = FragmentManager()
        val packet = largeMessagePacket("0102030405060708")
        val fragments = manager.createFragments(packet)

        assertTrue(fragments.size > 1)

        var reassembled: BitchatPacket? = null
        fragments.forEach { fragment ->
            val result = manager.handleFragment(fragment)
            if (result != null) {
                reassembled = result
            }
        }

        assertNotNull(reassembled)
        assertEquals(0u.toUByte(), reassembled!!.ttl)
        assertEquals(packet.type, reassembled!!.type)
        assertEquals(packet.payload.size, reassembled!!.payload.size)
    }

    @Test
    fun handleFragmentReassemblesOutOfOrder() {
        val manager = FragmentManager()
        val packet = largeMessagePacket("0102030405060708")
        val fragments = manager.createFragments(packet).reversed()

        assertTrue(fragments.size > 1)

        var reassembled: BitchatPacket? = null
        fragments.forEach { fragment ->
            val result = manager.handleFragment(fragment)
            if (result != null) {
                reassembled = result
            }
        }

        assertNotNull(reassembled)
        assertEquals(packet.payload.size, reassembled!!.payload.size)
    }

    @Test
    fun handleFragmentMissingPieceDoesNotReassemble() {
        val manager = FragmentManager()
        val packet = largeMessagePacket("0102030405060708")
        val fragments = manager.createFragments(packet)

        assertTrue(fragments.size > 1)

        val missing = fragments.last()
        var reassembled: BitchatPacket? = null
        fragments.dropLast(1).forEach { fragment ->
            val result = manager.handleFragment(fragment)
            if (result != null) {
                reassembled = result
            }
        }

        assertTrue(reassembled == null)

        val finalResult = manager.handleFragment(missing)
        assertNotNull(finalResult)
    }

    @Test
    fun handleFragmentIgnoresDuplicateUntilComplete() {
        val manager = FragmentManager()
        val packet = largeMessagePacket("0102030405060708")
        val fragments = manager.createFragments(packet)

        assertTrue(fragments.size > 1)

        val first = fragments.first()
        assertTrue(manager.handleFragment(first) == null)
        assertTrue(manager.handleFragment(first) == null)

        var reassembled: BitchatPacket? = null
        fragments.drop(1).forEach { fragment ->
            val result = manager.handleFragment(fragment)
            if (result != null) {
                reassembled = result
            }
        }

        assertNotNull(reassembled)
    }

    @Test
    fun handleFragmentRejectsTooSmallPayload() {
        val manager = FragmentManager()
        val packet = BitchatPacket(
            type = MessageType.FRAGMENT.value,
            senderID = hexToBytes("0102030405060708"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = ByteArray(FragmentPayload.HEADER_SIZE - 1),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )

        assertTrue(manager.handleFragment(packet) == null)
    }

    @Test
    fun handleFragmentRejectsInvalidPayload() {
        val manager = FragmentManager()
        val fragmentId = ByteArray(FragmentPayload.FRAGMENT_ID_SIZE) { 0x01 }

        val emptyData = FragmentPayload(
            fragmentID = fragmentId,
            index = 0,
            total = 1,
            originalType = MessageType.MESSAGE.value,
            data = ByteArray(0)
        )
        val emptyPacket = BitchatPacket(
            type = MessageType.FRAGMENT.value,
            senderID = hexToBytes("0102030405060708"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = emptyData.encode(),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        assertTrue(manager.handleFragment(emptyPacket) == null)

        val badIndex = FragmentPayload(
            fragmentID = fragmentId,
            index = 2,
            total = 2,
            originalType = MessageType.MESSAGE.value,
            data = byteArrayOf(0x01)
        )
        val badIndexPacket = BitchatPacket(
            type = MessageType.FRAGMENT.value,
            senderID = hexToBytes("0102030405060708"),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = badIndex.encode(),
            ttl = AppConstants.MESSAGE_TTL_HOPS
        )
        assertTrue(manager.handleFragment(badIndexPacket) == null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getIncomingFragments(manager: FragmentManager): MutableMap<String, MutableMap<Int, ByteArray>> {
        val field = FragmentManager::class.java.getDeclaredField("incomingFragments")
        field.isAccessible = true
        return field.get(manager) as MutableMap<String, MutableMap<Int, ByteArray>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getFragmentMetadata(manager: FragmentManager): MutableMap<String, Triple<UByte, Int, Long>> {
        val field = FragmentManager::class.java.getDeclaredField("fragmentMetadata")
        field.isAccessible = true
        return field.get(manager) as MutableMap<String, Triple<UByte, Int, Long>>
    }

    private fun invokeCleanup(manager: FragmentManager) {
        val method = FragmentManager::class.java.getDeclaredMethod("cleanupOldFragments")
        method.isAccessible = true
        method.invoke(manager)
    }

    private fun largeMessagePacket(senderHex: String): BitchatPacket {
        val payload = ByteArray(800) { index -> (index % 256).toByte() }
        return BitchatPacket(
            type = MessageType.MESSAGE.value,
            senderID = hexToBytes(senderHex),
            recipientID = null,
            timestamp = System.currentTimeMillis().toULong(),
            payload = payload,
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
