package com.bitchat.android.services.meshgraph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeshGraphServiceTests {
    @Test
    fun updateFromAnnouncementTracksConfirmedAndUnconfirmedEdges() {
        MeshGraphService.resetForTesting()
        val service = MeshGraphService.getInstance()

        service.updateFromAnnouncement("a", "A", listOf("b"), 1u)
        service.updateFromAnnouncement("b", "B", emptyList(), 1u)

        var snapshot = service.graphState.value
        assertEquals(2, snapshot.nodes.size)
        assertEquals(1, snapshot.edges.size)
        assertFalse(snapshot.edges[0].isConfirmed)
        assertEquals("a", snapshot.edges[0].confirmedBy)

        service.updateFromAnnouncement("b", "B", listOf("a"), 2u)
        snapshot = service.graphState.value
        assertEquals(1, snapshot.edges.size)
        assertTrue(snapshot.edges[0].isConfirmed)
        assertNull(snapshot.edges[0].confirmedBy)
    }

    @Test
    fun updateFromAnnouncementIgnoresOlderTimestamps() {
        MeshGraphService.resetForTesting()
        val service = MeshGraphService.getInstance()

        service.updateFromAnnouncement("a", null, listOf("b"), 5u)
        service.updateFromAnnouncement("a", null, emptyList(), 4u)

        val snapshot = service.graphState.value
        assertEquals(1, snapshot.edges.size)
    }

    @Test
    fun removePeerClearsFromGraph() {
        MeshGraphService.resetForTesting()
        val service = MeshGraphService.getInstance()

        service.updateFromAnnouncement("a", "A", listOf("b"), 1u)
        service.updateFromAnnouncement("b", "B", listOf("a"), 1u)
        assertEquals(1, service.graphState.value.edges.size)

        service.removePeer("b")
        val snapshot = service.graphState.value
        assertEquals(1, snapshot.edges.size)
        assertFalse(snapshot.edges[0].isConfirmed)

        service.updateFromAnnouncement("a", "A", emptyList(), 2u)
        val updated = service.graphState.value
        assertEquals(0, updated.edges.size)
        assertTrue(updated.nodes.any { it.peerID == "a" })
        assertTrue(updated.nodes.none { it.peerID == "b" })
    }
}
