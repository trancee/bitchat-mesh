package com.bitchat.android.services

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeenMessageStoreInstrumentationTests {
    private lateinit var store: SeenMessageStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = SeenMessageStore.getInstance(context)
        store.clear()
    }

    @Test
    fun markDeliveredAndReadRoundTrip() {
        assertFalse(store.hasDelivered("msg-1"))
        assertFalse(store.hasRead("msg-1"))

        store.markDelivered("msg-1")
        store.markRead("msg-1")

        assertTrue(store.hasDelivered("msg-1"))
        assertTrue(store.hasRead("msg-1"))
    }

    @Test
    fun clearRemovesAllEntries() {
        store.markDelivered("msg-2")
        store.markRead("msg-2")
        store.clear()

        assertFalse(store.hasDelivered("msg-2"))
        assertFalse(store.hasRead("msg-2"))
    }
}
