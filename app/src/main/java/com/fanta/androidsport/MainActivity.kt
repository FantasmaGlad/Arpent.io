package com.fanta.androidsport

import android.os.Bundle
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fanta.androidsport.ui.theme.ActiveOrange
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.NeonVolt
import com.fanta.androidsport.ui.theme.SportAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.Polygon
import org.maplibre.android.annotations.PolygonOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MapLibre Engine
        MapLibre.getInstance(this)

        setContent {
            SportAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArpentMainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArpentMainScreen() {
    var navigationIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Required permissions depending on Android version
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // Allow starting the map if at least one location permission is granted
        if (fineGranted || coarseGranted) {
            permissionsGranted = true
        } else {
            Toast.makeText(context, "L'accès à la localisation est obligatoire pour utiliser Arpent.io.", Toast.LENGTH_LONG).show()
        }
    }

    if (permissionsGranted) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ARPENT",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = ".IO",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = NeonVolt
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Glowing status indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeonVolt)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = navigationIndex == 0,
                        onClick = { navigationIndex = 0 },
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Conquête") },
                        label = { Text("Conquête") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonVolt,
                            selectedTextColor = NeonVolt,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 1,
                        onClick = { navigationIndex = 1 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Classement") },
                        label = { Text("Classement") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            selectedTextColor = ElectricBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 2,
                        onClick = { navigationIndex = 2 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ActiveOrange,
                            selectedTextColor = ActiveOrange,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (navigationIndex) {
                    0 -> ConquestMapScreen()
                    1 -> LeaderboardScreen()
                    2 -> ProfileScreen()
                }
            }
        }
    } else {
        PermissionRequestScreen(
            onRequestPermissions = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // A beautiful glowing radar/location graphic
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(NeonVolt.copy(alpha = 0.15f))
                    .border(2.dp, NeonVolt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = NeonVolt,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ARPENT.IO",
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Autorisation Requise",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pour conquérir les territoires, enregistrer vos courses et interagir avec la carte 3D, Arpent.io a besoin de vos autorisations système.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonVolt,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "AUTORISER L'ACCÈS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ==========================================
// CONQUEST / MAP SCREEN
// ==========================================

@Composable
fun ConquestMapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Map instances & simulation state variables
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var isSimulatingRun by remember { mutableStateOf(false) }
    var simulationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Live statistics
    var currentArea by remember { mutableStateOf(4.12) }
    var sessionGainedArea by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0.0) }

    // Paris starting coordinates
    val parisCenter = LatLng(48.8566, 2.3522)
    var currentPosition by remember { mutableStateOf(parisCenter) }

    // Store references to drawn objects to clear them later
    var activePolyline by remember { mutableStateOf<Polyline?>(null) }
    val completedPolygons = remember { mutableStateListOf<Polygon>() }

    // Build the Mapbox style URL (HTTPS, MapLibre-compatible)
    val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
    val styleUrl = remember {
        if (token.isNotEmpty()) {
            // Mapbox dark-v11 is a classic style fully compatible with MapLibre
            // Note: custom style cmqe0myj4002c01qr2jd549n8 uses Mapbox Standard imports
            // which MapLibre does not support, so we use dark-v11 as the base
            "https://api.mapbox.com/styles/v1/mapbox/dark-v11?access_token=$token"
        } else {
            "https://tiles.openfreemap.org/styles/dark"
        }
    }

    // MapView Lifecycle Holder — created once, style set in factory
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                maplibreMap = map

                // Hide MapLibre logo and attribution
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false

                // Load the dark style
                map.setStyle(styleUrl) { style ->
                    // Initial Camera layout tilted at 60 degrees for a beautiful 3D view
                    val cameraPosition = CameraPosition.Builder()
                        .target(currentPosition)
                        .zoom(16.2)
                        .tilt(60.0) // 3D Tilt perspective angle
                        .bearing(30.0)
                        .build()
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1200)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            simulationJob?.cancel()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // MapView Interop Container
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // --- OVERLAYS ---

        // 1. Top Conquest Status Indicator
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .widthIn(max = 340.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ),
            border = BorderStroke(1.dp, if (isSimulatingRun) NeonVolt.copy(alpha = 0.6f) else Color.Transparent)
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
                            .background(
                                if (isSimulatingRun) Color.Red.copy(alpha = pulseAlpha)
                                else NeonVolt.copy(alpha = pulseAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSimulatingRun) "Enregistrement Course" else "Mode Exploration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (isSimulatingRun) "${"%.1f".format(currentSpeed)} km/h" else "GPS Actif",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSimulatingRun) ElectricBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
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
                    text = "${"%.2f".format(currentArea)} km²",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (isSimulatingRun || sessionGainedArea > 0.0) {
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
                    maplibreMap?.let { map ->
                        val cameraPosition = CameraPosition.Builder()
                            .target(currentPosition)
                            .zoom(16.5)
                            .bearing(0.0)
                            .build()
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 800)
                    }
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
                    maplibreMap?.let { map ->
                        val currentCam = map.cameraPosition
                        val newCam = CameraPosition.Builder(currentCam)
                            .tilt(if (is3D) 60.0 else 0.0)
                            .build()
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCam), 1000)
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

            // Start capturing button (Core loop demo)
            val buttonColor by animateColorAsState(
                targetValue = if (isSimulatingRun) Color.Red else NeonVolt,
                label = "btn_color"
            )
            FloatingActionButton(
                onClick = {
                    if (isSimulatingRun) {
                        // Stop simulation manually
                        simulationJob?.cancel()
                        isSimulatingRun = false
                        currentSpeed = 0.0

                        // Draw mock final polygon captured
                        maplibreMap?.let { map ->
                            // Remove temp drawing line
                            activePolyline?.let { map.removePolyline(it) }
                            activePolyline = null

                            // Draw a filled polygon showing captured zone
                            val capturedPoints = listOf(
                                parisCenter,
                                LatLng(parisCenter.latitude + 0.002, parisCenter.longitude + 0.001),
                                LatLng(parisCenter.latitude + 0.0015, parisCenter.longitude + 0.003),
                                LatLng(parisCenter.latitude - 0.0005, parisCenter.longitude + 0.0025),
                                parisCenter
                            )
                            val polyOpt = PolygonOptions()
                                .addAll(capturedPoints)
                                .fillColor(0x55CCFF00.toInt()) // Neon volt with transparency
                                .strokeColor(0xFFCCFF00.toInt())
                            
                            val poly = map.addPolygon(polyOpt)
                            completedPolygons.add(poly)

                            currentArea += 0.14
                            sessionGainedArea = 0.14
                        }
                        Toast.makeText(context, "Parcelle bouclée : +0.14 km² !", Toast.LENGTH_LONG).show()
                    } else {
                        // Start simulation
                        isSimulatingRun = true
                        sessionGainedArea = 0.0
                        
                        // Clear past visual lines
                        maplibreMap?.let { map ->
                            activePolyline?.let { map.removePolyline(it) }
                            activePolyline = null
                            completedPolygons.forEach { map.removePolygon(it) }
                            completedPolygons.clear()
                        }

                        // Simulation coroutine loop
                        simulationJob = scope.launch {
                            val pathPoints = mutableListOf<LatLng>()
                            pathPoints.add(parisCenter)

                            // 10 simulated steps in a loop around center
                            val stepDelta = listOf(
                                Pair(0.0005, 0.0003),
                                Pair(0.0012, 0.0005),
                                Pair(0.0020, 0.0010),
                                Pair(0.0018, 0.0022),
                                Pair(0.0015, 0.0030),
                                Pair(0.0005, 0.0028),
                                Pair(-0.0002, 0.0020),
                                Pair(-0.0005, 0.0010),
                                Pair(-0.0003, 0.0002),
                                Pair(0.0, 0.0) // Back to start (closing loop)
                            )

                            var stepIndex = 0
                            while (stepIndex < stepDelta.size && isSimulatingRun) {
                                currentSpeed = 12.0 + (Math.random() * 3.0)
                                val delta = stepDelta[stepIndex]
                                val newPos = LatLng(parisCenter.latitude + delta.first, parisCenter.longitude + delta.second)
                                currentPosition = newPos
                                pathPoints.add(newPos)

                                // Update camera to follow simulated user
                                maplibreMap?.let { map ->
                                    val cam = CameraPosition.Builder(map.cameraPosition)
                                        .target(newPos)
                                        .build()
                                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 400)

                                    // Remove old polyline and draw updated line
                                    activePolyline?.let { map.removePolyline(it) }
                                    val lineOpt = PolylineOptions()
                                        .addAll(pathPoints)
                                        .color(0xFF00E5FF.toInt()) // Electric blue line
                                        .width(6f)
                                    activePolyline = map.addPolyline(lineOpt)
                                }

                                sessionGainedArea += 0.012
                                stepIndex++
                                delay(1200)
                            }

                            // Simulation reached the end (closed loop)
                            if (isSimulatingRun) {
                                maplibreMap?.let { map ->
                                    activePolyline?.let { map.removePolyline(it) }
                                    activePolyline = null

                                    val polyOpt = PolygonOptions()
                                        .addAll(pathPoints)
                                        .fillColor(0x55CCFF00.toInt()) // Volt color
                                        .strokeColor(0xFFCCFF00.toInt())
                                    
                                    val poly = map.addPolygon(polyOpt)
                                    completedPolygons.add(poly)
                                }
                                currentArea += 0.12
                                sessionGainedArea = 0.12
                                isSimulatingRun = false
                                currentSpeed = 0.0
                                Toast.makeText(context, "Boucle complétée avec succès ! Territoire capturé !", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                containerColor = buttonColor,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isSimulatingRun) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Simuler course",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ==========================================
// LEADERBOARD SCREEN (Guilds ranking)
// ==========================================

data class GuildRank(
    val rank: Int,
    val name: String,
    val territorySqKm: Double,
    val color: Color,
    val isUserGuild: Boolean = false
)

@Composable
fun LeaderboardScreen() {
    val guilds = remember { emptyList<GuildRank>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (guilds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Aucune guilde enregistrée",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rejoignez ou créez une guilde pour commencer la conquête du territoire !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NeonVolt.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NeonVolt)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "VOTRE GUILDE EST 3ème",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NeonVolt
                        )
                        Text(
                            text = "Les Arpenteurs • 34.98 km²",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Classement Régional",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(guilds) { guild ->
                    val cardBg = if (guild.isUserGuild) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = if (guild.isUserGuild) BorderStroke(1.dp, NeonVolt) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${guild.rank}",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.width(36.dp),
                                    color = if (guild.rank == 1) ActiveOrange else if (guild.rank == 2) ElectricBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(guild.color)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = guild.name,
                                    fontWeight = if (guild.isUserGuild) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${guild.territorySqKm} km²",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (guild.isUserGuild) NeonVolt else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PROFILE SCREEN
// ==========================================

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Premium Avatar Card for Guest
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(Color.Gray, Color.DarkGray, Color.LightGray, Color.Gray)
                    )
                )
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Visiteur",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Non connecté",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Level details (Empty/Initial State)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Niveau 1",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "0 / 100 XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 0.0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Grid (Zeroed out)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("0.0 km", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
            Card(
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Boucles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Guest Action Prompt
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Rejoignez l'aventure",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connectez-vous pour enregistrer votre progression et conquérir des territoires.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings option card list
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Notifications de capture") },
                    supportingContent = { Text("Alertes en cas de vol de territoire") },
                    trailingContent = {
                        var checked by remember { mutableStateOf(true) }
                        Switch(checked = checked, onCheckedChange = { checked = it })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                ListItem(
                    headlineContent = { Text("Mode éco-énergie") },
                    supportingContent = { Text("Réduit la précision GPS pour préserver la batterie") },
                    trailingContent = {
                        var checked by remember { mutableStateOf(false) }
                        Switch(checked = checked, onCheckedChange = { checked = it })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
