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

fun getPolygonArea(points: List<Point>): Double {
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
    return Math.abs(totalArea * r * r / 2.0)
}

fun splitIntoClosedPolygons(flatPoints: List<Point>): List<List<Point>> {
    val polygons = mutableListOf<List<Point>>()
    var currentRing = mutableListOf<Point>()
    for (pt in flatPoints) {
        currentRing.add(pt)
        if (currentRing.size >= 3 && 
            Math.abs(pt.longitude() - currentRing[0].longitude()) < 1e-7 && 
            Math.abs(pt.latitude() - currentRing[0].latitude()) < 1e-7) {
            polygons.add(currentRing)
            currentRing = mutableListOf()
        }
    }
    if (currentRing.size >= 3) {
        val first = currentRing.first()
        val last = currentRing.last()
        if (Math.abs(first.longitude() - last.longitude()) >= 1e-7 || 
            Math.abs(first.latitude() - last.latitude()) >= 1e-7) {
            currentRing.add(first)
        }
        polygons.add(currentRing)
    }
    return polygons
}
