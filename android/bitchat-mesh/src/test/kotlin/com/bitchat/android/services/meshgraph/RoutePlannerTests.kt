package com.bitchat.android.services.meshgraph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoutePlannerTests {
    @Test
    fun shortestPathUsesConfirmedEdges() {
        MeshGraphService.resetForTesting()
        val service = MeshGraphService.getInstance()

        service.updateFromAnnouncement("a", null, listOf("b"), 1u)
        service.updateFromAnnouncement("b", null, listOf("a", "c"), 1u)
        service.updateFromAnnouncement("c", null, listOf("b"), 1u)

        val path = RoutePlanner.shortestPath("a", "c")
        assertEquals(listOf("a", "b", "c"), path)
    }

    @Test
    fun shortestPathReturnsNullWhenUnreachableOrUnconfirmed() {
        MeshGraphService.resetForTesting()
        val service = MeshGraphService.getInstance()

        service.updateFromAnnouncement("a", null, listOf("b"), 1u)
        service.updateFromAnnouncement("b", null, emptyList(), 1u)

        assertNull(RoutePlanner.shortestPath("a", "b"))
        assertNull(RoutePlanner.shortestPath("a", "z"))
    }

    @Test
    fun shortestPathReturnsSingleNodeWhenSameSourceAndDest() {
        MeshGraphService.resetForTesting()
        MeshGraphService.getInstance().updateFromAnnouncement("a", null, emptyList(), 1u)

        assertEquals(listOf("a"), RoutePlanner.shortestPath("a", "a"))
    }
}
