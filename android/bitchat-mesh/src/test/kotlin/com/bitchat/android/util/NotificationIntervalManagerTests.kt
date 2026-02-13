package com.bitchat.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationIntervalManagerTests {
    @Test
    fun updatesLastNetworkNotificationTime() {
        val manager = NotificationIntervalManager()

        manager.setLastNetworkNotificationTime(1234L)

        assertEquals(1234L, manager.lastNetworkNotificationTime)
    }

    @Test
    fun tracksRecentlySeenPeers() {
        val manager = NotificationIntervalManager()

        manager.recentlySeenPeers.add("peer-1")
        manager.recentlySeenPeers.add("peer-2")

        assertEquals(2, manager.recentlySeenPeers.size)
        assertTrue(manager.recentlySeenPeers.contains("peer-1"))
    }
}
