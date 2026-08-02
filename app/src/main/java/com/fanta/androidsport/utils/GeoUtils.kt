package com.fanta.androidsport.utils

import com.mapbox.geojson.Point
import kotlin.math.*

fun calculateDistance(p1: Point, p2: Point): Double {
    val r = 6371000.0 // Earth radius in meters
    val lat1 = Math.toRadians(p1.latitude())
    val lat2 = Math.toRadians(p2.latitude())
    val dLat = Math.toRadians(p2.latitude() - p1.latitude())
    val dLng = Math.toRadians(p2.longitude() - p1.longitude())
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c // in meters
}

fun estimateAreaKm2(points: List<Point>): Double {
    return getPolygonArea(points) / 1_000_000.0
}

fun getPolygonCentroid(points: List<Point>): Point {
    if (points.isEmpty()) return Point.fromLngLat(0.0, 0.0)
    var sumLng = 0.0
    var sumLat = 0.0
    var count = 0
    points.forEach { pt ->
        sumLng += pt.longitude()
        sumLat += pt.latitude()
        count++
    }
    return Point.fromLngLat(sumLng / count, sumLat / count)
}

// Signed spherical-excess area: positive for a counter-clockwise ring (exterior
// boundary, per OGC/PostGIS convention), negative for clockwise (an interior ring,
// i.e. a hole carved out by ST_Difference). getPolygonArea keeps returning the
// unsigned value so existing "biggest polygon" / display-area call sites are unaffected.
fun getSignedPolygonArea(points: List<Point>): Double {
    if (points.size < 3) return 0.0
    var totalArea = 0.0
    val r = 6378137.0 // WGS84 Semi-major axis in meters
    val n = points.size
    for (i in 0 until n) {
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val lambda1 = Math.toRadians(p1.longitude())
        val lambda2 = Math.toRadians(p2.longitude())
        val phi1 = Math.toRadians(p1.latitude())
        val phi2 = Math.toRadians(p2.latitude())
        totalArea += (lambda2 - lambda1) * (2.0 + Math.sin(phi1) + Math.sin(phi2))
    }
    return totalArea * r * r / 2.0
}

fun getPolygonArea(points: List<Point>): Double = Math.abs(getSignedPolygonArea(points))

// Appends the first point at the end when missing, as Mapbox's PolygonAnnotation
// requires an explicitly closed ring.
fun closeRing(points: List<Point>): List<Point> {
    if (points.isEmpty()) return points
    val first = points.first()
    val last = points.last()
    return if (first.longitude() != last.longitude() || first.latitude() != last.latitude()) {
        points + first
    } else {
        points
    }
}

fun isPointInPolygon(point: Point, polygon: List<Point>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].longitude(); val yi = polygon[i].latitude()
        val xj = polygon[j].longitude(); val yj = polygon[j].latitude()
        if ((yi > point.latitude()) != (yj > point.latitude()) &&
            point.longitude() < (xj - xi) * (point.latitude() - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// A territoire row (or a stack of them for one player) is stored/synced as a flat list
// of closed rings — see fetchPlayerTerritoryPolygons/splitIntoClosedPolygons. When a
// player's loop cuts entirely through the MIDDLE of an existing territory, PostGIS'
// ST_Difference produces a polygon with an interior ring (a hole), not a second
// disjoint exterior ring. Rendering every ring as its own filled polygon (as if it were
// an independent territory) turns that hole into a solid phantom "extra base" instead of
// a gap. This groups rings back into [exterior, hole, hole, ...] sets — by PostGIS/OGC
// convention exterior rings wind counter-clockwise (positive signed area) and holes wind
// clockwise (negative) — so each hole can be attached to the exterior ring that contains
// it and rendered as an actual hole via Mapbox's multi-ring PolygonAnnotation.
fun groupRingsIntoPolygons(rings: List<List<Point>>): List<List<List<Point>>> {
    val validRings = rings.filter { it.size >= 3 }
    if (validRings.isEmpty()) return emptyList()

    val outers = mutableListOf<List<Point>>()
    val holes = mutableListOf<List<Point>>()
    for (ring in validRings) {
        if (getSignedPolygonArea(ring) >= 0.0) outers.add(ring) else holes.add(ring)
    }
    // Defensive fallback: if winding conventions aren't respected for some reason,
    // fall back to drawing every ring as its own polygon rather than losing territory.
    if (outers.isEmpty()) return validRings.map { listOf(it) }

    val groups = outers.map { mutableListOf(it) }.toMutableList()
    for (hole in holes) {
        val holeCentroid = getPolygonCentroid(hole)
        val ownerIndex = outers.indexOfFirst { isPointInPolygon(holeCentroid, it) }
        if (ownerIndex >= 0) {
            groups[ownerIndex].add(hole)
        } else {
            // No exterior ring actually contains it: treat it as its own shape instead
            // of silently dropping area from the map.
            groups.add(mutableListOf(hole))
        }
    }
    return groups
}

fun splitIntoClosedPolygons(flatPoints: List<Point>): List<List<Point>> {
    val polygons = mutableListOf<List<Point>>()
    var currentRing = mutableListOf<Point>()
    for (pt in flatPoints) {
        currentRing.add(pt)
        if (currentRing.size >= 3 && 
            Math.abs(pt.longitude() - currentRing[0].longitude()) < 1e-11 && 
            Math.abs(pt.latitude() - currentRing[0].latitude()) < 1e-11) {
            polygons.add(currentRing)
            currentRing = mutableListOf()
        }
    }
    if (currentRing.size >= 3) {
        val first = currentRing.first()
        val last = currentRing.last()
        if (Math.abs(first.longitude() - last.longitude()) >= 1e-11 || 
            Math.abs(first.latitude() - last.latitude()) >= 1e-11) {
            currentRing.add(first)
        }
        polygons.add(currentRing)
    }
    return polygons
}

fun smoothAltitudes(altitudes: List<Double>): List<Double> {
    if (altitudes.size < 5) return altitudes
    val windowSize = 11
    val halfWindow = windowSize / 2
    return altitudes.mapIndexed { index, _ ->
        val start = (index - halfWindow).coerceAtLeast(0)
        val end = (index + halfWindow).coerceAtMost(altitudes.size - 1)
        var sum = 0.0
        var count = 0
        for (j in start..end) {
            sum += altitudes[j]
            count++
        }
        sum / count
    }
}
