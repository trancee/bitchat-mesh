package com.bitchat.android.mesh

import com.bitchat.android.util.AppConstants
import org.junit.jupiter.api.Assertions.assertEquals
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
}
