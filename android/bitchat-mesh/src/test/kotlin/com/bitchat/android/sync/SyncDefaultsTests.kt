package com.bitchat.android.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncDefaultsTests {
    @Test
    fun exposesDefaultValues() {
        assertEquals(256, SyncDefaults.DEFAULT_FILTER_BYTES)
        assertEquals(1.0, SyncDefaults.DEFAULT_FPR_PERCENT)
        assertEquals(1024, SyncDefaults.MAX_ACCEPT_FILTER_BYTES)
    }
}
