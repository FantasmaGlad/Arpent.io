package com.fanta.androidsport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null

    private var currentAccelX: Double? = null
    private var currentAccelY: Double? = null
    private var currentAccelZ: Double? = null

    private var runStartStepCount = -1
    private var lastRecordedStepCount = -1
    private var totalStepsInRun = 0
    private val stepTimeHistory = mutableListOf<Long>()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    currentAccelX = event.values[0].toDouble()
                    currentAccelY = event.values[1].toDouble()
                    currentAccelZ = event.values[2].toDouble()
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    totalStepsInRun++
                    val now = System.currentTimeMillis()
                    stepTimeHistory.add(now)
                    stepTimeHistory.removeAll { now - it > 15000 }
                }
                Sensor.TYPE_STEP_COUNTER -> {
                    val stepsSinceBoot = event.values[0].toInt()
                    if (runStartStepCount < 0) {
                        runStartStepCount = stepsSinceBoot
                        lastRecordedStepCount = stepsSinceBoot
                    }
                    if (stepDetector == null) {
                        val delta = stepsSinceBoot - lastRecordedStepCount
                        if (delta > 0) {
                            totalStepsInRun += delta
                            val now = System.currentTimeMillis()
                            repeat(delta) {
                                stepTimeHistory.add(now)
                            }
                            stepTimeHistory.removeAll { now - it > 15000 }
                            lastRecordedStepCount = stepsSinceBoot
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun getCurrentCadence(): Int {
        val now = System.currentTimeMillis()
        stepTimeHistory.removeAll { now - it > 15000 }
        return if (stepTimeHistory.isNotEmpty()) {
            (stepTimeHistory.size * 4)
        } else {
            0
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    LocationTrackerState.addPoint(
                        context = applicationContext,
                        point = point,
                        speedMps = location.speed,
                        accuracy = location.accuracy,
                        timeMs = location.time,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accelX = currentAccelX,
                        accelY = currentAccelY,
                        accelZ = currentAccelZ,
                        steps = totalStepsInRun,
                        cadence = getCurrentCadence()
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            startTrackingForeground()
        } else if (action == ACTION_STOP) {
            stopTrackingForeground()
        }
        return START_NOT_STICKY
    }

    private fun startTrackingForeground() {
        createNotificationChannel()
        
        // Dynamic build of main activity intent
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arpent.io - Course en cours")
            .setContentText("Enregistrement de votre parcours en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        // Type de service requis pour Android 14 (API 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                    .setMinUpdateDistanceMeters(2f)
                    .build()

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            android.util.Log.e("Arpent", "Permission GPS manquante pour le service", e)
        }

        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

            // Reset run states
            totalStepsInRun = 0
            runStartStepCount = -1
            lastRecordedStepCount = -1
            stepTimeHistory.clear()

            accelerometer?.let {
                sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepDetector?.let {
                sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepCounter?.let {
                sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Error registering sensors", e)
        }
    }

    private fun stopTrackingForeground() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {
            // Ignore
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suivi de course Arpent.io",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "arpent_gps_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
    }
}
