package com.fanta.androidsport.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import android.widget.Toast
import com.fanta.androidsport.BuildConfig
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanta.androidsport.LocationTrackerState
import com.fanta.androidsport.LocationTrackingService
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.theme.ActiveOrange
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.NeonVolt
import com.fanta.androidsport.utils.calculateDistance
import com.fanta.androidsport.utils.getPolygonArea
import com.fanta.androidsport.utils.getPolygonCentroid
import com.fanta.androidsport.utils.saveRunToDatabase
import com.fanta.androidsport.utils.saveTerritoriesLocally
import com.fanta.androidsport.utils.splitIntoClosedPolygons
import com.fanta.androidsport.utils.syncTerritoriesFromDatabase
import com.fanta.androidsport.utils.estimateAreaKm2
import com.fanta.androidsport.utils.smoothAltitudes
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import io.github.jan.supabase.storage.storage
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotationState
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ConquestMapScreen(
    userId: String,
    userPseudo: String,
    initialArea: Double,
    completedPolygons: androidx.compose.runtime.snapshots.SnapshotStateList<List<Point>>,
    userEmpireColor: String,
    userAvatarUrl: String?,
    userGuildCouleur: String?,
    userGuildNom: String?,
    mapTargetPosition: Point?,
    onMapTargetPositionHandled: () -> Unit,
    onRunSaved: () -> Unit,
    bottomPadding: Dp = 0.dp,
    userChange24hPct: Double = 0.0,
    clanAreaKm2: Double = 0.0,
    clanChange24hPct: Double = 0.0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    selectedImageBytes = inputStream.readBytes()
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to read picked image bytes", e)
                Toast.makeText(context, "Erreur de lecture de l'image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Real run tracking states collected from LocationTrackerState flow
    val isRealRunActiveFlow by LocationTrackerState.isRealRunActive.collectAsStateWithLifecycle()
    val gpsStartTime by LocationTrackerState.runStartTime.collectAsStateWithLifecycle()
    val gpsPoints by LocationTrackerState.points.collectAsStateWithLifecycle()
    val gpsDistance by LocationTrackerState.distance.collectAsStateWithLifecycle()
    val gpsSpeed by LocationTrackerState.currentSpeed.collectAsStateWithLifecycle()
    val isSpeedLimitExceeded by LocationTrackerState.isSpeedLimitExceeded.collectAsStateWithLifecycle()

    var isRealRunActive by remember { mutableStateOf(false) }
    var runStartTime by remember { mutableStateOf<Long?>(null) }
    var runDistance by remember { mutableStateOf(0.0) }

    var showRunSaveDialog by remember { mutableStateOf(false) }
    var runSaveName by remember { mutableStateOf("") }
    var runSaveDescription by remember { mutableStateOf("") }
    var pendingRunDataToSave by remember { mutableStateOf<PendingRunSaveData?>(null) }

    // Live statistics
    var currentArea by remember { mutableStateOf(initialArea) }
    var sessionGainedArea by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0.0) }
    var isEmpireCardExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(isSpeedLimitExceeded) {
        if (isSpeedLimitExceeded) {
            Toast.makeText(context, "Vitesse maximale autorisée (12 m/s) dépassée ! Vos points actuels sont rejetés.", Toast.LENGTH_LONG).show()
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                }
            } catch (e: Exception) {
                Log.e("ConquestMapScreen", "Failed to vibrate", e)
            }
        }
    }

    LaunchedEffect(initialArea) {
        currentArea = initialArea
    }

    // Parse user empire color
    val parsedUserColor = remember(userEmpireColor) {
        try { Color(android.graphics.Color.parseColor(userEmpireColor)) } catch (_: Exception) { Color(0xFF00E676) }
    }

    // Paris starting coordinates
    val parisCenter = Point.fromLngLat(2.3522, 48.8566)
    var currentPosition by remember { mutableStateOf(parisCenter) }

    // First location update flag
    var isFirstLocationUpdate by remember { mutableStateOf(true) }

    // Mapbox Viewport State
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(parisCenter)
            zoom(16.2)
            pitch(60.0) // 3D Tilt perspective angle
            bearing(30.0)
        }
    }

    // Store references to drawn objects
    val activePathPoints = remember { mutableStateListOf<Point>() }

    // Sync flow states to compose states
    LaunchedEffect(isRealRunActiveFlow) {
        isRealRunActive = isRealRunActiveFlow
        if (isRealRunActiveFlow) {
            // Restore from state flow on recreation
            runStartTime = gpsStartTime
            runDistance = gpsDistance
            activePathPoints.clear()
            activePathPoints.addAll(gpsPoints)
            gpsPoints.lastOrNull()?.let {
                currentPosition = it
            }
        }
    }

    // Auto-restart foreground service if session is active (e.g. after process death)
    LaunchedEffect(isRealRunActive) {
        if (isRealRunActive) {
            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    LaunchedEffect(gpsPoints) {
        if (isRealRunActive) {
            activePathPoints.clear()
            activePathPoints.addAll(gpsPoints)
            gpsPoints.lastOrNull()?.let {
                currentPosition = it
                mapViewportState.flyTo(
                    CameraOptions.Builder()
                        .center(it)
                        .build(),
                    mapAnimationOptions { duration(800L) }
                )
            }
        }
    }

    LaunchedEffect(gpsDistance) {
        if (isRealRunActive) {
            runDistance = gpsDistance
        }
    }

    LaunchedEffect(gpsSpeed) {
        if (isRealRunActive) {
            currentSpeed = gpsSpeed
        }
    }



    // --- Other players data ---
    data class OtherPlayerTerritory(
        val playerId: String,
        val pseudo: String,
        val empireColor: Color,
        val polygons: List<List<Point>>,
        val markerPosition: Point?,
        val avatarUrl: String?,
        val guildeNom: String?,
        val guildeCouleur: String?,
        val totalAreaM2: Double
    )
    var otherPlayersTerritories by remember { mutableStateOf<List<OtherPlayerTerritory>>(emptyList()) }
    var lastQueryCenter by remember { mutableStateOf<Point?>(null) }
    var lastQueryZoom by remember { mutableStateOf<Double?>(null) }
    var selectedPlayerStats by remember { mutableStateOf<OtherPlayerTerritory?>(null) }
    var selectedPlayerRunsCount by remember { mutableStateOf<Int?>(null) }
    var selectedPlayerTotalDistance by remember { mutableStateOf<Double?>(null) }
    var loadingPlayerStats by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPlayerStats) {
        val player = selectedPlayerStats
        if (player != null) {
            loadingPlayerStats = true
            selectedPlayerRunsCount = null
            selectedPlayerTotalDistance = null
            try {
                val response = withContext(Dispatchers.IO) {
                    supabase.postgrest["courses"].select {
                        filter { eq("utilisateur_id", player.playerId) }
                    }
                }
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray
                var totalDist = 0.0
                var count = 0
                if (jsonArray != null) {
                    count = jsonArray.size
                    for (element in jsonArray) {
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: continue
                        val distanceTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        totalDist += distanceTotale
                    }
                }
                selectedPlayerRunsCount = count
                selectedPlayerTotalDistance = totalDist / 1000.0
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingPlayerStats = false
            }
        } else {
            selectedPlayerRunsCount = null
            selectedPlayerTotalDistance = null
            loadingPlayerStats = false
        }
    }

    // Fetch other players' territories dynamically based on visible bounding box
    LaunchedEffect(userId) {
        // Sync current user's own territories first to handle fresh installs / reinstalls
        try {
            syncTerritoriesFromDatabase(userId, context, completedPolygons)
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to sync own territories in map screen", e)
        }
    }

    LaunchedEffect(userId, mapViewportState.cameraState) {
        val cam = mapViewportState.cameraState ?: return@LaunchedEffect
        val currentZoom = cam.zoom
        
        if (currentZoom < 12.0) {
            if (otherPlayersTerritories.isNotEmpty()) {
                otherPlayersTerritories = emptyList()
            }
            lastQueryCenter = null
            lastQueryZoom = null
            return@LaunchedEffect
        }
        
        val centerPoint = cam.center
        delay(400) // Debounce viewport updates to avoid database spamming
        
        // Throttling: skip database fetch if movement since last query is insignificant
        val lastCenter = lastQueryCenter
        val lastZoom = lastQueryZoom
        if (lastCenter != null && lastZoom != null) {
            val dist = calculateDistance(centerPoint, lastCenter)
            val zoomDiff = Math.abs(currentZoom - lastZoom)
            val threshold = (360.0 / Math.pow(2.0, currentZoom)) * 111000.0 * 0.15
            if (dist < threshold && zoomDiff < 0.3) {
                return@LaunchedEffect
            }
        }
        
        val lat = centerPoint.latitude()
        val lng = centerPoint.longitude()
        
        val multiplier = 2.5 
        val dLng = (360.0 / Math.pow(2.0, currentZoom)) * multiplier
        val dLat = (dLng * Math.cos(Math.toRadians(lat))) * multiplier
        
        val minLng = lng - dLng
        val maxLng = lng + dLng
        val minLat = lat - dLat
        val maxLat = lat + dLat
        
        try {
            val params = kotlinx.serialization.json.buildJsonObject {
                put("min_lng", kotlinx.serialization.json.JsonPrimitive(minLng))
                put("min_lat", kotlinx.serialization.json.JsonPrimitive(minLat))
                put("max_lng", kotlinx.serialization.json.JsonPrimitive(maxLng))
                put("max_lat", kotlinx.serialization.json.JsonPrimitive(maxLat))
            }
            val response = withContext(Dispatchers.IO) {
                supabase.postgrest.rpc("get_territoires_in_bbox", params)
            }
            
            val territories = withContext(Dispatchers.Default) {
                val array = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList<OtherPlayerTerritory>()
                val terrByUser = mutableMapOf<String, MutableList<List<Point>>>()
                
                data class PlayerDetails(
                    val pseudo: String,
                    val colorStr: String,
                    val avatarUrl: String?,
                    val guildeNom: String?,
                    val guildeCouleur: String?,
                    val totalAreaM2: Double
                )
                val userDetails = mutableMapOf<String, PlayerDetails>()
                val userLocations = mutableMapOf<String, Point>()
                
                array.forEach { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val uId = obj["utilisateur_id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                    val colorStr = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                    val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val guildeNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                    val guildeCouleur = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                    val totalAreaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    
                    userDetails[uId] = PlayerDetails(pseudo, colorStr, avatarUrl, guildeNom, guildeCouleur, totalAreaM2)
                    
                    val latVal = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    val lonVal = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    if (latVal != null && lonVal != null) {
                        userLocations[uId] = Point.fromLngLat(lonVal, latVal)
                    }
                    
                    val pointsArr = obj["points"] as? kotlinx.serialization.json.JsonArray
                    if (pointsArr != null) {
                        val polygon = pointsArr.mapNotNull { pt ->
                            val coords = pt.jsonPrimitive.contentOrNull?.split(" ") ?: return@mapNotNull null
                            if (coords.size >= 2) {
                                val lon = coords[0].toDoubleOrNull() ?: return@mapNotNull null
                                val lat = coords[1].toDoubleOrNull() ?: return@mapNotNull null
                                Point.fromLngLat(lon, lat)
                            } else null
                        }
                        if (polygon.isNotEmpty()) {
                            terrByUser.getOrPut(uId) { mutableListOf() }.addAll(splitIntoClosedPolygons(polygon))
                        }
                    }
                }
                
                userDetails.keys.filter { it != userId }.map { pId ->
                    val detail = userDetails[pId]!!
                    val empColor = try { Color(android.graphics.Color.parseColor(detail.colorStr)) } catch (_: Exception) { Color(0xFF00E676) }
                    val marker = userLocations[pId]
                    val polys = terrByUser[pId] ?: emptyList()
                    OtherPlayerTerritory(
                        playerId = pId,
                        pseudo = detail.pseudo,
                        empireColor = empColor,
                        polygons = polys,
                        markerPosition = marker,
                        avatarUrl = detail.avatarUrl,
                        guildeNom = detail.guildeNom,
                        guildeCouleur = detail.guildeCouleur,
                        totalAreaM2 = detail.totalAreaM2
                    )
                }
            }
            
            withContext(Dispatchers.Main) {
                otherPlayersTerritories = territories
                lastQueryCenter = centerPoint
                lastQueryZoom = currentZoom
            }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to fetch territories in bounding box", e)
        }
    }

    // Handle map target position from leaderboard click
    LaunchedEffect(mapTargetPosition) {
        if (mapTargetPosition != null) {
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(mapTargetPosition)
                    .zoom(15.0)
                    .pitch(60.0)
                    .bearing(0.0)
                    .build(),
                mapAnimationOptions { duration(2000L) }
            )
            onMapTargetPositionHandled()
        }
    }

    // GPS Location client using FusedLocationProviderClient
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(lifecycleOwner, isRealRunActive) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    currentPosition = point
                    if (isFirstLocationUpdate) {
                        isFirstLocationUpdate = false
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(point)
                                .zoom(16.5)
                                .pitch(60.0)
                                .bearing(30.0)
                                .build(),
                            mapAnimationOptions { duration(1000L) }
                        )
                    }
                }
            }
        }

        try {
            if (!isRealRunActive && (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val point = Point.fromLngLat(it.longitude, it.latitude)
                        currentPosition = point
                        if (isFirstLocationUpdate) {
                            isFirstLocationUpdate = false
                            mapViewportState.setCameraOptions {
                                center(point)
                                zoom(16.5)
                                pitch(60.0)
                                bearing(30.0)
                            }
                        }
                    }
                }

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
            // Permission not granted or disallowed
        }

        onDispose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // MapboxMap Composable
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            logo = {},
            attribution = {}
        ) {
            MapStyle(style = "mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8")

            // Draw active polyline
            if (activePathPoints.isNotEmpty()) {
                val polylineState = remember {
                    PolylineAnnotationState().apply {
                        lineColor = Color(0xFF00E5FF)
                        lineWidth = 6.0
                    }
                }
                PolylineAnnotation(
                    points = activePathPoints.toList(),
                    polylineAnnotationState = polylineState
                )
            }

            // Draw completed polygons (user's own territories)
            completedPolygons.forEachIndexed { index, polygonPoints ->
                key("user_poly_$index") {
                    val closedPoints = remember(polygonPoints) {
                        if (polygonPoints.isNotEmpty() && 
                            (polygonPoints.first().longitude() != polygonPoints.last().longitude() || 
                             polygonPoints.first().latitude() != polygonPoints.last().latitude())) {
                            polygonPoints + polygonPoints.first()
                        } else {
                            polygonPoints
                        }
                    }
                    val polygonState = remember(closedPoints, parsedUserColor) {
                        PolygonAnnotationState().apply {
                            fillColor = parsedUserColor.copy(alpha = 0.50f)
                            fillOutlineColor = parsedUserColor
                        }
                    }
                    PolygonAnnotation(
                        points = listOf(closedPoints),
                        polygonAnnotationState = polygonState
                    )
                }
            }

            // Draw user avatar exactly once at centroid of largest polygon
            val userLargestPolygon = remember(completedPolygons.size) {
                completedPolygons.maxByOrNull { getPolygonArea(it) }
            }
            val userCentroid = remember(userLargestPolygon) {
                userLargestPolygon?.let { getPolygonCentroid(it) }
            }
            if (userCentroid != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(userCentroid)
                        allowOverlap(true)
                    }
                ) {
                    AvatarImage(
                        avatarUrl = userAvatarUrl,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, parsedUserColor, CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                selectedPlayerStats = OtherPlayerTerritory(
                                    playerId = userId,
                                    pseudo = userPseudo,
                                    empireColor = parsedUserColor,
                                    polygons = completedPolygons.toList(),
                                    markerPosition = null,
                                    avatarUrl = userAvatarUrl,
                                    guildeNom = userGuildNom,
                                    guildeCouleur = userGuildCouleur,
                                    totalAreaM2 = currentArea * 1_000_000.0
                                )
                            }
                    )
                }
            }

            // Draw other players' territories
            otherPlayersTerritories.forEach { player ->
                key(player.playerId) {
                    player.polygons.forEachIndexed { index, polygonPoints ->
                        key(player.playerId + "_poly_$index") {
                            val closedPoints = remember(polygonPoints) {
                                if (polygonPoints.isNotEmpty() && 
                                    (polygonPoints.first().longitude() != polygonPoints.last().longitude() || 
                                     polygonPoints.first().latitude() != polygonPoints.last().latitude())) {
                                    polygonPoints + polygonPoints.first()
                                } else {
                                    polygonPoints
                                }
                            }
                            val polygonState = remember(closedPoints, player.empireColor) {
                                PolygonAnnotationState().apply {
                                    fillColor = player.empireColor.copy(alpha = 0.50f)
                                    fillOutlineColor = player.empireColor
                                }
                            }
                            PolygonAnnotation(
                                points = listOf(closedPoints),
                                polygonAnnotationState = polygonState
                            )
                        }
                    }
                }

                // Draw player avatar exactly once at centroid of largest polygon
                val playerLargestPolygon = remember(player.polygons) {
                    player.polygons.maxByOrNull { getPolygonArea(it) }
                }
                val playerCentroid = remember(playerLargestPolygon) {
                    playerLargestPolygon?.let { getPolygonCentroid(it) }
                }
                if (playerCentroid != null) {
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(playerCentroid)
                            allowOverlap(true)
                        }
                    ) {
                        AvatarImage(
                            avatarUrl = player.avatarUrl,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(2.dp, player.empireColor, CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    selectedPlayerStats = player
                                }
                        )
                    }
                }

                // Draw marker for each player with territory
                if (player.markerPosition != null) {
                    val circleState = remember(player.playerId) {
                        CircleAnnotationState().apply {
                            circleRadius = 10.0
                            circleColor = player.empireColor
                            circleStrokeWidth = 3.0
                            circleStrokeColor = Color.White
                        }
                    }
                    CircleAnnotation(
                        point = player.markerPosition,
                        circleAnnotationState = circleState
                    )
                }
            }
        }

        // --- OVERLAYS ---

        // Collapsible "Votre Empire" Stats Banner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 130.dp, end = 0.dp)
        ) {
            if (isEmpireCardExpanded) {
                val clanHeaderColor = remember(userGuildCouleur) {
                    try {
                        if (!userGuildCouleur.isNullOrEmpty()) Color(android.graphics.Color.parseColor(userGuildCouleur))
                        else null
                    } catch (_: Exception) {
                        null
                    }
                } ?: MaterialTheme.colorScheme.secondary

                Card(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .width(250.dp)
                        .height(150.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = { isEmpireCardExpanded = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Collapse",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Mon Empire Section
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Mon Empire",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.3f km²", currentArea),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                    val isPos = userChange24hPct >= 0.0
                                    val pctText = if (isPos) String.format(java.util.Locale.US, "+%.1f%%", userChange24hPct) else String.format(java.util.Locale.US, "%.1f%%", userChange24hPct)
                                    Text(
                                        text = pctText,
                                        color = if (isPos) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.1f),
                                thickness = 1.dp
                            )

                            // Mon clan Section
                            if (!userGuildNom.isNullOrEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Mon clan ($userGuildNom)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = clanHeaderColor,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.3f km²", clanAreaKm2),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                        val isClanPos = clanChange24hPct >= 0.0
                                        val clanPctText = if (isClanPos) String.format(java.util.Locale.US, "+%.1f%%", clanChange24hPct) else String.format(java.util.Locale.US, "%.1f%%", clanChange24hPct)
                                        Text(
                                            text = clanPctText,
                                            color = if (isClanPos) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Mon clan",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Aucun clan",
                                            fontWeight = FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "—%",
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 64.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
                            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                        .clickable { isEmpireCardExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Déplier l'empire",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 1. Top Conquest Status Indicator
        if (isRealRunActive) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .widthIn(max = 340.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSpeedLimitExceeded) Color(0x33FF3D00) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                border = BorderStroke(1.5.dp, if (isSpeedLimitExceeded) Color.Red else NeonVolt.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pulseTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isSpeedLimitExceeded) {
                            Text(
                                text = "⚠️ HORS-JEU (Max 12m/s)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red
                            )
                        } else {
                            Text(
                                text = "Enregistrement Course",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = "${"%.1f".format(currentSpeed)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSpeedLimitExceeded) Color.Red else ElectricBlue
                    )
                }
            }
        }

        // (Stats Overlay supprimé — les métriques sont dans la carte Empire en haut à droite)

        // 3. Control Actions Overlay (Floating Column on Right)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Camera Centering Fab
            FloatingActionButton(
                onClick = {
                    mapViewportState.flyTo(
                        CameraOptions.Builder()
                            .center(currentPosition)
                            .zoom(16.5)
                            .bearing(0.0)
                            .build(),
                        mapAnimationOptions { duration(800L) }
                    )
                    Toast.makeText(context, "Recentré sur votre position", Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Ma position", modifier = Modifier.size(24.dp))
            }

            // Camera Tilt toggle
            var is3D by remember { mutableStateOf(true) }
            FloatingActionButton(
                onClick = {
                    is3D = !is3D
                    val currentCamera = mapViewportState.cameraState
                    if (currentCamera != null) {
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(currentCamera.center)
                                .zoom(currentCamera.zoom)
                                .bearing(currentCamera.bearing)
                                .pitch(if (is3D) 60.0 else 0.0)
                                .build(),
                            mapAnimationOptions { duration(1000L) }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (is3D) ElectricBlue else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = if (is3D) Icons.Default.Layers else Icons.Default.LayersClear,
                    contentDescription = "Activer 3D",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Real run capturing button (GPS active tracking)
            val themePrimary = MaterialTheme.colorScheme.primary
            val realButtonColor by animateColorAsState(
                targetValue = if (isRealRunActive) Color.Red else themePrimary,
                label = "real_btn_color"
            )
            FloatingActionButton(
                onClick = {
                    if (isRealRunActive) {
                        // Stop real run
                        isRealRunActive = false
                        currentSpeed = 0.0

                        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_STOP
                        }
                        context.startService(serviceIntent)

                        val isLoop = activePathPoints.size >= 3 && calculateDistance(activePathPoints.first(), activePathPoints.last()) <= 35.0
                        val closedPoints = if (isLoop && activePathPoints.first() != activePathPoints.last()) {
                            activePathPoints.toList() + activePathPoints[0]
                        } else {
                            activePathPoints.toList()
                        }

                        if (activePathPoints.isNotEmpty()) {
                            val endTime = System.currentTimeMillis()
                            val durationSec = ((endTime - (runStartTime ?: endTime)) / 1000.0).coerceAtLeast(0.0)
                            pendingRunDataToSave = PendingRunSaveData(
                                runStartTime = runStartTime ?: System.currentTimeMillis(),
                                runDurationSec = durationSec,
                                runDistance = runDistance,
                                isLoop = isLoop,
                                closedPoints = closedPoints,
                                rawPoints = LocationTrackerState.pointsDetails.value.toList()
                            )
                            runSaveName = ""
                            runSaveDescription = ""
                            selectedImageUri = null
                            selectedImageBytes = null
                            isUploadingImage = false
                            showRunSaveDialog = true
                        } else {
                            Toast.makeText(context, "Course annulée (aucun point GPS enregistré).", Toast.LENGTH_SHORT).show()
                            activePathPoints.clear()
                            runDistance = 0.0
                            runStartTime = null
                            LocationTrackerState.stopRun(context)
                        }
                    } else {
                        // Start real run
                        val startTime = System.currentTimeMillis()
                        LocationTrackerState.startNewRun(context, startTime)
                        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }

                        isRealRunActive = true
                        sessionGainedArea = 0.0
                        runDistance = 0.0
                        runStartTime = System.currentTimeMillis()

                        activePathPoints.clear()

                        // Initialize the first point if we have one
                        activePathPoints.add(currentPosition)
                        Toast.makeText(context, "Course réelle démarrée !", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = realButtonColor,
                contentColor = if (isRealRunActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isRealRunActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Démarrer course",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        if (selectedPlayerStats != null) {
            val player = selectedPlayerStats!!
            PlayerProfileDialog(
                playerId = player.playerId,
                currentUserId = userId,
                onDismissRequest = { selectedPlayerStats = null },
                onNavigateToTerritory = { point ->
                    selectedPlayerStats = null
                    scope.launch {
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(point)
                                .zoom(15.0)
                                .pitch(60.0)
                                .bearing(0.0)
                                .build(),
                            mapAnimationOptions { duration(2000L) }
                        )
                    }
                }
            )
        }

        if (showRunSaveDialog && pendingRunDataToSave != null) {
            val data = pendingRunDataToSave!!
            Dialog(
                onDismissRequest = {
                    showRunSaveDialog = false
                    pendingRunDataToSave = null
                    selectedImageUri = null
                    selectedImageBytes = null
                    isUploadingImage = false
                    activePathPoints.clear()
                    runDistance = 0.0
                    runStartTime = null
                    LocationTrackerState.stopRun(context)
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title
                        Text(
                            text = "Enregistrer ma Course",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Main content row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left Column: Importer une image & Entrer une description
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Image Import Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedImageUri != null) {
                                        AsyncImage(
                                            model = selectedImageUri,
                                            contentDescription = "Course image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Modifier l'image",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = "Add image",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Importer une image",
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                // Description Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(12.dp)
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = runSaveDescription,
                                        onValueChange = { runSaveDescription = it },
                                        modifier = Modifier.fillMaxSize(),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                                        decorationBox = { innerTextField ->
                                            if (runSaveDescription.isEmpty()) {
                                                Text(
                                                    text = "Entrer une description",
                                                    color = Color.Gray,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            // Right Column: Stats & Route trace preview
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Stats Section
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val distanceKm = data.runDistance / 1000.0
                                    val durationSec = data.runDurationSec
                                    val durationMin = (durationSec / 60.0).toInt()
                                    
                                    val paceMinPerKm = if (distanceKm > 0) (durationSec / 60.0) / distanceKm else 0.0
                                    val paceMin = paceMinPerKm.toInt()
                                    val paceSec = ((paceMinPerKm - paceMin) * 60).toInt().coerceIn(0, 59)

                                    var denivelePos = 0.0
                                    val validAltitudes = data.rawPoints.mapNotNull { it.altitude }
                                    if (validAltitudes.size > 1) {
                                        val smoothed = smoothAltitudes(validAltitudes)
                                        var prevAlt = smoothed.first()
                                        for (i in 1 until smoothed.size) {
                                            val currAlt = smoothed[i]
                                            val diff = currAlt - prevAlt
                                            if (diff > 2.0) {
                                                denivelePos += diff
                                                prevAlt = currAlt
                                            } else if (diff < -2.0) {
                                                prevAlt = currAlt
                                            }
                                        }
                                    }

                                    val areaGainedKm2 = if (data.isLoop) estimateAreaKm2(data.closedPoints) else 0.0

                                    Text(
                                        text = String.format(java.util.Locale.US, "%.2f km", distanceKm),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "$durationMin mins",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%d:%02d mins/km", paceMin, paceSec),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "+ %d m d +", denivelePos.toInt()),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "+ %.3f km2", areaGainedKm2),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Route trace Preview Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(118.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (data.closedPoints.isNotEmpty()) {
                                        RouteTraceView(
                                            points = data.closedPoints,
                                            modifier = Modifier.fillMaxSize(),
                                            lineColor = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = "Vue du tracé",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isUploadingImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NeonVolt,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Enregistrement...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        showRunSaveDialog = false
                                        pendingRunDataToSave = null
                                        selectedImageUri = null
                                        selectedImageBytes = null
                                        isUploadingImage = false
                                        activePathPoints.clear()
                                        runDistance = 0.0
                                        runStartTime = null
                                        LocationTrackerState.stopRun(context)
                                    }
                                ) {
                                    Text("ANNULER", color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                TextButton(
                                    onClick = {
                                        isUploadingImage = true
                                        scope.launch {
                                            var finalImageUrl: String? = null
                                                 if (selectedImageUri != null && selectedImageBytes != null) {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        val imageBytes = selectedImageBytes!!
                                                        val bucket = supabase.storage.from("course-photos")
                                                        val filename = "course_${userId}_${System.currentTimeMillis()}.jpg"
                                                        Log.d("Arpent", "Upload image: $filename (${imageBytes.size} bytes)")
                                                        bucket.upload(filename, imageBytes) {
                                                            upsert = true
                                                        }
                                                        val publicUrl = bucket.publicUrl(filename)
                                                        finalImageUrl = publicUrl
                                                        Log.d("Arpent", "Image uploadée : $publicUrl")
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("Arpent", "Erreur upload image: ${e.message}", e)
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Erreur d'envoi de l'image : ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }

                                            saveRunToDatabase(
                                                userId = userId,
                                                scope = scope,
                                                context = context,
                                                runStartTime = data.runStartTime,
                                                durationSec = data.runDurationSec,
                                                runDistance = data.runDistance,
                                                isLoop = data.isLoop,
                                                closedPoints = data.closedPoints,
                                                rawPoints = data.rawPoints,
                                                completedPolygons = completedPolygons,
                                                nom = runSaveName.ifBlank { "Course Arpent.io" },
                                                legende = runSaveDescription.ifBlank { "" },
                                                imageUrl = finalImageUrl,
                                                onSuccess = { areaKm2 ->
                                                    if (data.isLoop) {
                                                        completedPolygons.add(data.closedPoints)
                                                        saveTerritoriesLocally(context, completedPolygons)
                                                        currentArea += areaKm2
                                                        sessionGainedArea = areaKm2
                                                        Toast.makeText(context, "Course enregistrée ! Territoire conquis (+${"%.3f".format(areaKm2)} km²)", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Course enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                                                    }
                                                    onRunSaved()
                                                },
                                                onSyncComplete = {
                                                    onRunSaved()
                                                }
                                            )

                                            isUploadingImage = false
                                            showRunSaveDialog = false
                                            pendingRunDataToSave = null
                                            selectedImageUri = null
                                            selectedImageBytes = null
                                            activePathPoints.clear()
                                            runDistance = 0.0
                                            runStartTime = null
                                            LocationTrackerState.stopRun(context)
                                        }
                                    }
                                ) {
                                    Text("ENREGISTRER", color = NeonVolt, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class PendingRunSaveData(
    val runStartTime: Long,
    val runDurationSec: Double,
    val runDistance: Double,
    val isLoop: Boolean,
    val closedPoints: List<Point>,
    val rawPoints: List<com.fanta.androidsport.TrackerPoint>
)

@Composable
fun RouteTraceView(
    points: List<Point>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFFF4F5F7), shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Pas de tracé GPS", color = Color(0xFF6E6E73), fontSize = 12.sp)
        }
        return
    }

    val hexColor = remember(lineColor) {
        try {
            val argb = lineColor.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            String.format("#%02x%02x%02x", r, g, b)
        } catch (e: Exception) {
            "#00E5FF"
        }
    }

    val mapboxToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
    val staticMapUrl = remember(points, hexColor) {
        val step = if (points.size > 80) points.size / 80 else 1
        val sampled = points.filterIndexed { i, _ -> i % step == 0 || i == points.size - 1 }
        val pathCoords = sampled.joinToString(",") { "[${it.longitude()},${it.latitude()}]" }
        val geoJsonPath = "{\"type\":\"Feature\",\"properties\":{\"stroke\":\"$hexColor\",\"stroke-width\":5,\"stroke-opacity\":0.9},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$pathCoords]}}"
        val encodedGeoJson = java.net.URLEncoder.encode(geoJsonPath, "UTF-8").replace("+", "%20")

        "https://api.mapbox.com/styles/v1/fantasmaglad/cmqe0myj4002c01qr2jd549n8/static/" +
            "geojson($encodedGeoJson)/" +
            "auto/600x260@2x" +
            "?access_token=$mapboxToken&attribution=false&logo=false"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            model = staticMapUrl,
            contentDescription = "Carte du tracé",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
    }
}
