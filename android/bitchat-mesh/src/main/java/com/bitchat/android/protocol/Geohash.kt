package com.bitchat.android.protocol

/**
 * Lightweight Geohash encoder used for Location Channels.
 * Ported from the iOS implementation for compatibility.
 */
object Geohash {
    private val base32Chars = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray()
    private val base32Map: Map<Char, Int> = base32Chars.withIndex().associate { it.value to it.index }

    data class Center(val lat: Double, val lon: Double)
    data class Bounds(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double)

    fun isValidBuildingGeohash(geohash: String): Boolean {
        if (geohash.length != 8) return false
        return geohash.lowercase().all { base32Map.containsKey(it) }
    }

    fun encode(latitude: Double, longitude: Double, precision: Int): String {
        if (precision <= 0) return ""

        var latInterval = -90.0 to 90.0
        var lonInterval = -180.0 to 180.0

        var isEven = true
        var bit = 0
        var ch = 0
        val geohash = StringBuilder()

        val lat = latitude.coerceIn(-90.0, 90.0)
        val lon = longitude.coerceIn(-180.0, 180.0)

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lonInterval.first + lonInterval.second) / 2
                if (lon >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    lonInterval = mid to lonInterval.second
                } else {
                    lonInterval = lonInterval.first to mid
                }
            } else {
                val mid = (latInterval.first + latInterval.second) / 2
                if (lat >= mid) {
                    ch = ch or (1 shl (4 - bit))
                    latInterval = mid to latInterval.second
                } else {
                    latInterval = latInterval.first to mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit += 1
            } else {
                geohash.append(base32Chars[ch])
                bit = 0
                ch = 0
            }
        }

        return geohash.toString()
    }

    fun decodeCenter(geohash: String): Center {
        val bounds = decodeBounds(geohash)
        val lat = (bounds.latMin + bounds.latMax) / 2
        val lon = (bounds.lonMin + bounds.lonMax) / 2
        return Center(lat = lat, lon = lon)
    }

    fun decodeBounds(geohash: String): Bounds {
        var latInterval = -90.0 to 90.0
        var lonInterval = -180.0 to 180.0

        var isEven = true
        for (ch in geohash.lowercase()) {
            val cd = base32Map[ch] ?: continue
            for (mask in intArrayOf(16, 8, 4, 2, 1)) {
                if (isEven) {
                    val mid = (lonInterval.first + lonInterval.second) / 2
                    if ((cd and mask) != 0) {
                        lonInterval = mid to lonInterval.second
                    } else {
                        lonInterval = lonInterval.first to mid
                    }
                } else {
                    val mid = (latInterval.first + latInterval.second) / 2
                    if ((cd and mask) != 0) {
                        latInterval = mid to latInterval.second
                    } else {
                        latInterval = latInterval.first to mid
                    }
                }
                isEven = !isEven
            }
        }

        return Bounds(
            latMin = latInterval.first,
            latMax = latInterval.second,
            lonMin = lonInterval.first,
            lonMax = lonInterval.second
        )
    }

    fun neighbors(geohash: String): List<String> {
        if (geohash.isEmpty()) return emptyList()

        val precision = geohash.length
        val bounds = decodeBounds(geohash)
        val center = decodeCenter(geohash)

        val latHeight = bounds.latMax - bounds.latMin
        val lonWidth = bounds.lonMax - bounds.lonMin

        fun wrapLongitude(lon: Double): Double {
            var wrapped = lon
            while (wrapped > 180.0) wrapped -= 360.0
            while (wrapped < -180.0) wrapped += 360.0
            return wrapped
        }

        fun clampLatitude(lat: Double): Double = lat.coerceIn(-90.0, 90.0)

        val neighbors = listOf(
            Center(center.lat + latHeight, center.lon),
            Center(center.lat + latHeight, center.lon + lonWidth),
            Center(center.lat, center.lon + lonWidth),
            Center(center.lat - latHeight, center.lon + lonWidth),
            Center(center.lat - latHeight, center.lon),
            Center(center.lat - latHeight, center.lon - lonWidth),
            Center(center.lat, center.lon - lonWidth),
            Center(center.lat + latHeight, center.lon - lonWidth)
        )

        return neighbors.mapNotNull { neighbor ->
            if (neighbor.lat > 90.0 || neighbor.lat < -90.0) return@mapNotNull null
            val lat = clampLatitude(neighbor.lat)
            val lon = wrapLongitude(neighbor.lon)
            encode(latitude = lat, longitude = lon, precision = precision)
        }
    }
}
