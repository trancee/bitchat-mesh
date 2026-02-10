package com.bitchat.android.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeohashTests {
    @Test
    fun testGeohashEncodeDecodeBoundsContainPoint() {
        val lat = 37.7749
        val lon = -122.4194
        val precision = 8

        val geohash = Geohash.encode(latitude = lat, longitude = lon, precision = precision)
        assertEquals(precision, geohash.length)
        assertTrue(Geohash.isValidBuildingGeohash(geohash))

        val bounds = Geohash.decodeBounds(geohash)
        assertTrue(lat >= bounds.latMin)
        assertTrue(lat <= bounds.latMax)
        assertTrue(lon >= bounds.lonMin)
        assertTrue(lon <= bounds.lonMax)
    }

    @Test
    fun testGeohashNeighborsAreValidAndDistinct() {
        val geohash = Geohash.encode(latitude = 37.7749, longitude = -122.4194, precision = 8)
        val neighbors = Geohash.neighbors(geohash)

        assertEquals(8, neighbors.size)
        assertFalse(neighbors.contains(geohash))
        assertEquals(8, neighbors.toSet().size)
        assertTrue(neighbors.all { it.length == 8 })
        assertTrue(neighbors.all { Geohash.isValidBuildingGeohash(it) })
    }

    @Test
    fun testGeohashValidationRejectsInvalidLengthAndCharacters() {
        assertFalse(Geohash.isValidBuildingGeohash(""))
        assertFalse(Geohash.isValidBuildingGeohash("abc"))
        assertFalse(Geohash.isValidBuildingGeohash("abc123"))
        assertFalse(Geohash.isValidBuildingGeohash("abcd123!"))
        assertFalse(Geohash.isValidBuildingGeohash("abcd123o"))
        assertFalse(Geohash.isValidBuildingGeohash("abcd123i"))
    }

    @Test
    fun testGeohashEncodeReturnsEmptyWhenPrecisionIsNonPositive() {
        assertEquals("", Geohash.encode(latitude = 0.0, longitude = 0.0, precision = 0))
        assertEquals("", Geohash.encode(latitude = 0.0, longitude = 0.0, precision = -3))
    }

    @Test
    fun testGeohashDecodeCenterReencodesToSameCell() {
        val precision = 8
        val geohash = Geohash.encode(latitude = 37.7749, longitude = -122.4194, precision = precision)
        val center = Geohash.decodeCenter(geohash)
        val reencoded = Geohash.encode(latitude = center.lat, longitude = center.lon, precision = precision)

        assertEquals(geohash, reencoded)
    }

    @Test
    fun testGeohashNeighborsNearPoleSkipOutOfBoundsCells() {
        val geohash = Geohash.encode(latitude = 89.9999, longitude = 0.0, precision = 8)
        val neighbors = Geohash.neighbors(geohash)

        assertTrue(neighbors.isNotEmpty())
        assertTrue(neighbors.size < 8)
        assertEquals(neighbors.toSet().size, neighbors.size)
        assertTrue(neighbors.all { it.length == 8 })
        assertTrue(neighbors.all { Geohash.isValidBuildingGeohash(it) })
    }
}
