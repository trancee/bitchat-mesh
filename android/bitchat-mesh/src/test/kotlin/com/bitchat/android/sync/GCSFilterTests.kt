package com.bitchat.android.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GCSFilterTests {
    private fun makeId(seed: Int): ByteArray {
        val bytes = ByteArray(16)
        for (i in bytes.indices) {
            bytes[i] = ((seed + i) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun derivePClampsToRange() {
        assertEquals(20, GCSFilter.deriveP(1e-9))
        assertEquals(2, GCSFilter.deriveP(0.25))
        assertTrue(GCSFilter.deriveP(0.01) >= 1)
    }

    @Test
    fun estimateMaxElementsUsesBitBudget() {
        assertEquals(2, GCSFilter.estimateMaxElementsForSize(1, 1))
        assertTrue(GCSFilter.estimateMaxElementsForSize(32, 5) > 0)
    }

    @Test
    fun buildFilterRespectsMaxBytesAndDecodes() {
        val ids = (0 until 200).map { makeId(it) }
        val params = GCSFilter.buildFilter(ids, maxBytes = 32, targetFpr = 0.01)

        assertTrue(params.data.size <= 32)
        val n = params.m ushr params.p
        assertTrue(n in 1..ids.size)

        val values = GCSFilter.decodeToSortedSet(params.p, params.m, params.data)
        assertTrue(values.isNotEmpty())
        for (i in 1 until values.size) {
            assertTrue(values[i] > values[i - 1])
        }

        for (value in values) {
            assertTrue(GCSFilter.contains(values, value))
        }

        val outside = params.m + 1
        assertFalse(GCSFilter.contains(values, outside))
    }
}
