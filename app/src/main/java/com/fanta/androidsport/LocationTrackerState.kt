package com.fanta.androidsport

import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocationTrackerState {
    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()

    private val _distance = MutableStateFlow(0.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()

    fun startNewRun() {
        _points.value = emptyList()
        _currentSpeed.value = 0.0
        _distance.value = 0.0
    }

    fun addPoint(point: Point, speedMps: Float) {
        val currentList = _points.value.toMutableList()
        val prevPoint = currentList.lastOrNull()
        if (prevPoint == null || prevPoint != point) {
            if (prevPoint != null) {
                _distance.value += calculateDistance(prevPoint, point)
            }
            currentList.add(point)
            _points.value = currentList
        }
        _currentSpeed.value = speedMps * 3.6 // km/h
    }

    private fun calculateDistance(p1: Point, p2: Point): Double {
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
}
