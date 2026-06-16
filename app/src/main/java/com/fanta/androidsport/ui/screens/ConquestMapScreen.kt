package com.fanta.androidsport.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
    onRunSaved: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Real run tracking states collected from LocationTrackerState flow
    val isRealRunActiveFlow by LocationTrackerState.isRealRunActive.collectAsStateWithLifecycle()
    val gpsStartTime by LocationTrackerState.runStartTime.collectAsStateWithLifecycle()
    val gpsPoints by LocationTrackerState.points.collectAsStateWithLifecycle()
    val gpsDistance by LocationTrackerState.distance.collectAsStateWithLifecycle()
    val gpsSpeed by LocationTrackerState.currentSpeed.collectAsStateWithLifecycle()

    var isRealRunActive by remember { mutableStateOf(false) }
    var runStartTime by remember { mutableStateOf<Long?>(null) }
    var runDistance by remember { mutableStateOf(0.0) }

    // Live statistics
    var currentArea by remember { mutableStateOf(initialArea) }
    var sessionGainedArea by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0.0) }

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

    // Mapbox Viewport State
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(parisCenter)
            zoom(16.2)
            pitch(60.0) // 3D Tilt perspective angle
            bearing(30.0)
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
        val centerPoint = cam.center
        val currentZoom = cam.zoom
        
        delay(400) // Debounce viewport updates to avoid database spamming
        
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
            val parsedUserGuildColor = remember(userGuildCouleur) {
                if (userGuildCouleur != null) {
                    try { Color(android.graphics.Color.parseColor(userGuildCouleur)) } catch (_: Exception) { null }
                } else null
            }
            val userTerritoryColor = parsedUserGuildColor ?: parsedUserColor

            completedPolygons.forEach { polygonPoints ->
                val polygonState = remember(polygonPoints, userTerritoryColor, parsedUserColor) {
                    PolygonAnnotationState().apply {
                        fillColor = userTerritoryColor.copy(alpha = 0.25f)
                        fillOutlineColor = parsedUserColor
                    }
                }
                PolygonAnnotation(
                    points = listOf(polygonPoints),
                    polygonAnnotationState = polygonState
                )
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
                val parsedGuildColor = remember(player.guildeCouleur) {
                    if (player.guildeCouleur != null) {
                        try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { null }
                    } else null
                }
                val territoryColor = parsedGuildColor ?: player.empireColor

                player.polygons.forEach { polygonPoints ->
                    val polygonState = remember(polygonPoints, territoryColor, player.empireColor) {
                        PolygonAnnotationState().apply {
                            fillColor = territoryColor.copy(alpha = 0.20f)
                            fillOutlineColor = player.empireColor
                        }
                    }
                    PolygonAnnotation(
                        points = listOf(polygonPoints),
                        polygonAnnotationState = polygonState
                    )
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

        // 1. Top Conquest Status Indicator
        if (isRealRunActive) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .widthIn(max = 340.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                border = BorderStroke(1.dp, NeonVolt.copy(alpha = 0.6f))
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
                        Text(
                            text = "Enregistrement Course",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${"%.1f".format(currentSpeed)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricBlue
                    )
                }
            }
        }

        // 2. Stats Overlay (Bottom-Left)
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .width(220.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VOTRE EMPIRE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${"%.3f".format(currentArea)} km²",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (sessionGainedArea > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Capture",
                            tint = NeonVolt,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${"%.3f".format(sessionGainedArea)} km²",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonVolt
                        )
                    }
                }
            }
        }

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
            val realButtonColor by animateColorAsState(
                targetValue = if (isRealRunActive) Color.Red else NeonVolt,
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
                            saveRunToDatabase(
                                userId = userId,
                                scope = scope,
                                context = context,
                                runStartTime = runStartTime ?: System.currentTimeMillis(),
                                runDistance = runDistance,
                                isLoop = isLoop,
                                closedPoints = closedPoints
                            ) { areaKm2 ->
                                if (isLoop) {
                                    completedPolygons.add(closedPoints)
                                    saveTerritoriesLocally(context, completedPolygons)
                                    currentArea += areaKm2
                                    sessionGainedArea = areaKm2
                                    Toast.makeText(context, "Course enregistrée ! Territoire conquis (+${"%.3f".format(areaKm2)} km²)", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Course enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                                }
                                onRunSaved()
                            }
                        } else {
                            Toast.makeText(context, "Course annulée (aucun point GPS enregistré).", Toast.LENGTH_SHORT).show()
                        }
                        activePathPoints.clear()
                        runDistance = 0.0
                        runStartTime = null
                        LocationTrackerState.stopRun(context)
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
                contentColor = Color.Black,
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
        
        // Show player details stats dialog when clicked
        if (selectedPlayerStats != null) {
            val player = selectedPlayerStats!!
            AlertDialog(
                onDismissRequest = { selectedPlayerStats = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val parsedColor = player.empireColor
                        AvatarImage(
                            avatarUrl = player.avatarUrl,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(2.dp, parsedColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = player.pseudo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                            if (player.guildeNom != null) {
                                val gColor = try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { Color.Gray }
                                Text(text = "Clan: ${player.guildeNom}", color = gColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "Statistiques de l'Empire",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Couleur de l'Empire :", color = Color.Black)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(player.empireColor)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Superficie conquise :", color = Color.Black)
                            val areaStr = "%.3f"
                                .format(player.totalAreaM2 / 1_000_000.0) + " km²"
                            Text(
                                text = areaStr,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (loadingPlayerStats) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = NeonVolt,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Nombre de courses :", color = Color.Black)
                                Text(
                                    text = "${selectedPlayerRunsCount ?: 0}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Distance totale courue :", color = Color.Black)
                                val distStr = "%.2f km".format(selectedPlayerTotalDistance ?: 0.0)
                                Text(
                                    text = distStr,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPlayerStats = null }) {
                        Text("FERMER", color = NeonVolt, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color.White
            )
        }
    }
}
