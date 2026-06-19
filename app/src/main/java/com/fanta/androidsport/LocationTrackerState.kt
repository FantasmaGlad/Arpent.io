package com.fanta.androidsport

import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedPoint(val longitude: Double, val latitude: Double)

@Serializable
data class TrackerPoint(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
    val speed: Double = 0.0,
    val time: Long,
    val accelX: Double? = null,
    val accelY: Double? = null,
    val accelZ: Double? = null,
    val steps: Int? = null,
    val cadence: Int? = null
)

@Serializable
data class ActiveSessionState(
    val isRealRunActive: Boolean,
    val runStartTime: Long?,
    val distance: Double,
    val points: List<CachedPoint>,
    val lastPointTime: Long,
    val pointsDetails: List<TrackerPoint> = emptyList()
)

object LocationTrackerState {
    private val _points = MutableStateFlow<List<Point>>(emptyList())
    val points: StateFlow<List<Point>> = _points.asStateFlow()

    private val _pointsDetails = MutableStateFlow<List<TrackerPoint>>(emptyList())
    val pointsDetails: StateFlow<List<TrackerPoint>> = _pointsDetails.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0.0)
    val currentSpeed: StateFlow<Double> = _currentSpeed.asStateFlow()

    private val _distance = MutableStateFlow(0.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()

    private val _isRealRunActive = MutableStateFlow(false)
    val isRealRunActive: StateFlow<Boolean> = _isRealRunActive.asStateFlow()

    private val _runStartTime = MutableStateFlow<Long?>(null)
    val runStartTime: StateFlow<Long?> = _runStartTime.asStateFlow()

    private val _isSpeedLimitExceeded = MutableStateFlow(false)
    val isSpeedLimitExceeded: StateFlow<Boolean> = _isSpeedLimitExceeded.asStateFlow()

    private var lastPointTime: Long = 0L

    private val json = Json { ignoreUnknownKeys = true }
    private const val SESSION_FILE_NAME = "active_run_session.json"

    private fun saveStateToDisk(context: Context) {
        try {
            val pts = _points.value.map { CachedPoint(it.longitude(), it.latitude()) }
            val stateObj = ActiveSessionState(
                isRealRunActive = _isRealRunActive.value,
                runStartTime = _runStartTime.value,
                distance = _distance.value,
                points = pts,
                lastPointTime = lastPointTime,
                pointsDetails = _pointsDetails.value
            )
            val jsonStr = json.encodeToString(stateObj)
            val file = File(context.filesDir, SESSION_FILE_NAME)
            file.writeText(jsonStr)
        } catch (e: Exception) {
            Log.e("LocationTrackerState", "Error saving session to disk", e)
        }
    }

    fun restoreState(context: Context) {
        try {
            val file = File(context.filesDir, SESSION_FILE_NAME)
            if (file.exists()) {
                val jsonStr = file.readText()
                val stateObj = json.decodeFromString<ActiveSessionState>(jsonStr)
                _isRealRunActive.value = stateObj.isRealRunActive
                _runStartTime.value = stateObj.runStartTime
                _distance.value = stateObj.distance
                _points.value = stateObj.points.map { Point.fromLngLat(it.longitude, it.latitude) }
                _pointsDetails.value = stateObj.pointsDetails
                lastPointTime = stateObj.lastPointTime
                Log.d("LocationTrackerState", "Restored session state from disk. Active: ${stateObj.isRealRunActive}, Points: ${stateObj.points.size}, Details: ${stateObj.pointsDetails.size}")
            }
        } catch (e: Exception) {
            Log.e("LocationTrackerState", "Error restoring session from disk", e)
        }
    }

    fun clearSavedState(context: Context) {
        try {
            val file = File(context.filesDir, SESSION_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("LocationTrackerState", "Error clearing session from disk", e)
        }
    }

    fun startNewRun(context: Context, startTime: Long) {
        _points.value = emptyList()
        _pointsDetails.value = emptyList()
        _currentSpeed.value = 0.0
        _distance.value = 0.0
        _isRealRunActive.value = true
        _runStartTime.value = startTime
        _isSpeedLimitExceeded.value = false
        lastPointTime = startTime
        saveStateToDisk(context)
    }

    fun stopRun(context: Context) {
        _isRealRunActive.value = false
        _runStartTime.value = null
        _points.value = emptyList()
        _pointsDetails.value = emptyList()
        _distance.value = 0.0
        _currentSpeed.value = 0.0
        _isSpeedLimitExceeded.value = false
        lastPointTime = 0L
        clearSavedState(context)
    }

    fun addPoint(
        context: Context,
        point: Point,
        speedMps: Float,
        accuracy: Float,
        timeMs: Long,
        altitude: Double? = null,
        accelX: Double? = null,
        accelY: Double? = null,
        accelZ: Double? = null,
        steps: Int? = null,
        cadence: Int? = null
    ) {
        if (accuracy > 20.0f) {
            Log.d("LocationTrackerState", "Point rejected due to accuracy: $accuracy")
            return
        }

        if (speedMps > 12.0f) {
            Log.d("LocationTrackerState", "Point rejected due to speed limit: $speedMps m/s")
            _isSpeedLimitExceeded.value = true
            return
        }

        val currentList = _points.value.toMutableList()
        val currentDetails = _pointsDetails.value.toMutableList()
        val prevPoint = currentList.lastOrNull()

        if (prevPoint == null) {
            currentList.add(point)
            _points.value = currentList

            val trackerPoint = TrackerPoint(
                longitude = point.longitude(),
                latitude = point.latitude(),
                altitude = altitude,
                speed = speedMps * 3.6,
                time = timeMs,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                steps = steps,
                cadence = cadence
            )
            currentDetails.add(trackerPoint)
            _pointsDetails.value = currentDetails

            lastPointTime = timeMs
            _currentSpeed.value = speedMps * 3.6 // km/h
            _isSpeedLimitExceeded.value = false
            saveStateToDisk(context)
        } else if (prevPoint != point) {
            val dist = calculateDistance(prevPoint, point)
            val timeDiffSec = (timeMs - lastPointTime) / 1000.0

            if (timeDiffSec > 0.1) {
                val speedBetweenPoints = dist / timeDiffSec
                if (speedBetweenPoints > 12.0) { // > 43.2 km/h
                    Log.d("LocationTrackerState", "Point rejected due to speed anomaly: $speedBetweenPoints m/s")
                    _isSpeedLimitExceeded.value = true
                    return
                }
            }

            _isSpeedLimitExceeded.value = false
            _distance.value += dist
            currentList.add(point)
            _points.value = currentList

            val trackerPoint = TrackerPoint(
                longitude = point.longitude(),
                latitude = point.latitude(),
                altitude = altitude,
                speed = speedMps * 3.6,
                time = timeMs,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                steps = steps,
                cadence = cadence
            )
            currentDetails.add(trackerPoint)
            _pointsDetails.value = currentDetails

            lastPointTime = timeMs
            _currentSpeed.value = speedMps * 3.6 // km/h
            saveStateToDisk(context)
        }
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
