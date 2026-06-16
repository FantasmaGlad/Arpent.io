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
    if (points.size < 3) return 0.0
    var area = 0.0
    val refLat = points[0].latitude()
    val cosLat = Math.cos(Math.toRadians(refLat))
    val xy = points.map { p ->
        val x = (p.longitude() - points[0].longitude()) * 111000.0 * cosLat
        val y = (p.latitude() - points[0].latitude()) * 111000.0
        Pair(x, y)
    }
    var j = xy.size - 1
    for (i in xy.indices) {
        area += (xy[j].first + xy[i].first) * (xy[j].second - xy[i].second)
        j = i
    }
    val areaM2 = Math.abs(area) / 2.0
    return areaM2 / 1_000_000.0 // convert m² to km²
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
    var area = 0.0
    val n = points.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        area += points[i].longitude() * points[j].latitude()
        area -= points[j].longitude() * points[i].latitude()
    }
    return Math.abs(area) / 2.0
}

fun splitIntoClosedPolygons(flatPoints: List<Point>): List<List<Point>> {
    val polygons = mutableListOf<List<Point>>()
    var currentRing = mutableListOf<Point>()
    for (pt in flatPoints) {
        currentRing.add(pt)
        if (currentRing.size >= 3 && pt.longitude() == currentRing[0].longitude() && pt.latitude() == currentRing[0].latitude()) {
            polygons.add(currentRing)
            currentRing = mutableListOf()
        }
    }
    if (currentRing.size >= 3) {
        polygons.add(currentRing)
    }
    return polygons
}
