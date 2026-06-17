package com.fanta.androidsport.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fanta.androidsport.PendingRunsQueue
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.theme.NeonVolt
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.ActiveOrange
import com.fanta.androidsport.utils.loadTerritoriesLocally
import com.fanta.androidsport.utils.syncTerritoriesFromDatabase
import com.fanta.androidsport.utils.isNetworkAvailable
import com.mapbox.geojson.Point
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArpentMainScreen(userId: String) {
    var navigationIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    var userPseudo by remember { mutableStateOf("Visiteur") }
    var totalDistanceKm by remember { mutableStateOf(0.0) }
    var allTimeAreaKm2 by remember { mutableStateOf(0.0) }
    var currentAreaKm2 by remember { mutableStateOf(0.0) }
    var userEmpireColor by remember { mutableStateOf("#00E676") }
    var userShareLocation by remember { mutableStateOf(true) }
    var userGhostMode by remember { mutableStateOf(false) }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }
    var userGuildId by remember { mutableStateOf<String?>(null) }
    var userGuildNom by remember { mutableStateOf<String?>(null) }
    var userGuildCouleur by remember { mutableStateOf<String?>(null) }
    var mapTargetPosition by remember { mutableStateOf<Point?>(null) }

    val completedPolygons = remember { mutableStateListOf<List<Point>>() }

    val scope = rememberCoroutineScope()

    fun refreshStats() {
        if (!isNetworkAvailable(context)) return
        scope.launch(Dispatchers.IO) {
            try {
                val profileDeferred = async {
                    supabase.postgrest["profiles"].select {
                        filter { eq("id", userId) }
                    }
                }
                val coursesDeferred = async {
                    supabase.postgrest["courses"].select {
                        filter { eq("utilisateur_id", userId) }
                    }
                }

                val profileRes = profileDeferred.await()
                val coursesRes = coursesDeferred.await()

                // Parse profile info first
                val profileArray = kotlinx.serialization.json.Json.parseToJsonElement(profileRes.data) as? kotlinx.serialization.json.JsonArray
                val profileObj = profileArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val pseudo = profileObj?.get("pseudonyme")?.jsonPrimitive?.contentOrNull ?: "Joueur_${userId.take(8)}"
                val color = profileObj?.get("empire_color")?.jsonPrimitive?.contentOrNull ?: "#00E676"
                val shareLoc = profileObj?.get("share_location")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
                val ghostMode = profileObj?.get("ghost_mode")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val avatarUrl = profileObj?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                val guildeId = profileObj?.get("guilde_id")?.jsonPrimitive?.contentOrNull
                val totalAreaM2 = profileObj?.get("total_area_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val allTimeAreaM2 = profileObj?.get("all_time_area_m2")?.jsonPrimitive?.doubleOrNull ?: totalAreaM2

                // Fetch guild details if present
                var gNom: String? = null
                var gColor: String? = null
                if (guildeId != null) {
                    try {
                        val guildRes = supabase.postgrest["guildes"].select {
                            filter { eq("id", guildeId) }
                        }
                        val guildArray = kotlinx.serialization.json.Json.parseToJsonElement(guildRes.data) as? kotlinx.serialization.json.JsonArray
                        val guildObj = guildArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                        gNom = guildObj?.get("nom")?.jsonPrimitive?.contentOrNull
                        gColor = guildObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to fetch guild info", e)
                    }
                }

                val parsed = withContext(Dispatchers.Default) {
                    val coursesArray = kotlinx.serialization.json.Json.parseToJsonElement(coursesRes.data) as? kotlinx.serialization.json.JsonArray
                    var totalDist = 0.0
                    coursesArray?.forEach {
                        val obj = it as? kotlinx.serialization.json.JsonObject
                        totalDist += obj?.get("distance_totale")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    }

                    mapOf(
                        "pseudo" to pseudo,
                        "color" to color,
                        "shareLoc" to shareLoc,
                        "ghostMode" to ghostMode,
                        "totalDist" to totalDist,
                        "totalAreaM2" to totalAreaM2,
                        "allTimeAreaM2" to allTimeAreaM2
                    )
                }

                withContext(Dispatchers.Main) {
                    userPseudo = parsed["pseudo"] as String
                    userEmpireColor = parsed["color"] as String
                    userShareLocation = parsed["shareLoc"] as Boolean
                    userGhostMode = parsed["ghostMode"] as Boolean
                    totalDistanceKm = parsed["totalDist"] as Double
                    currentAreaKm2 = (parsed["totalAreaM2"] as Double) / 1_000_000.0
                    allTimeAreaKm2 = (parsed["allTimeAreaM2"] as Double) / 1_000_000.0
                    userAvatarUrl = avatarUrl
                    userGuildId = guildeId
                    userGuildNom = gNom
                    userGuildCouleur = gColor
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error fetching stats", e)
            }
        }
    }

    LaunchedEffect(userId) {
        // Load from local storage immediately so there is zero delay/blank screen
        val localPolys = loadTerritoriesLocally(context)
        completedPolygons.clear()
        completedPolygons.addAll(localPolys)

        refreshStats()
        syncTerritoriesFromDatabase(userId, context, completedPolygons)
        
        // Sync pending offline runs immediately on startup/auth
        scope.launch {
            PendingRunsQueue.syncPendingRuns(context, supabase)
        }
    }

    androidx.compose.runtime.DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                scope.launch {
                    PendingRunsQueue.syncPendingRuns(context, supabase)
                }
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to register network callback", e)
        }
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

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
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp
                val footerFontSize = when {
                    screenWidth < 360 -> 9.sp
                    screenWidth < 400 -> 10.sp
                    else -> 11.sp
                }
                val footerIconSize = when {
                    screenWidth < 360 -> 18.dp
                    screenWidth < 400 -> 20.dp
                    else -> 22.dp
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = navigationIndex == 0,
                        onClick = { navigationIndex = 0 },
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Conquête", modifier = Modifier.size(footerIconSize)) },
                        label = { Text("Conquête", fontSize = footerFontSize, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonVolt,
                            selectedTextColor = NeonVolt,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 1,
                        onClick = { navigationIndex = 1 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Classement", modifier = Modifier.size(footerIconSize)) },
                        label = { Text("Classement", fontSize = footerFontSize, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            selectedTextColor = ElectricBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 4,
                        onClick = { navigationIndex = 4 },
                        icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Courses", modifier = Modifier.size(footerIconSize)) },
                        label = { Text("Courses", fontSize = footerFontSize, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ActiveOrange,
                            selectedTextColor = ActiveOrange,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 3,
                        onClick = { navigationIndex = 3 },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Guilde", modifier = Modifier.size(footerIconSize)) },
                        label = { Text("Guilde", fontSize = footerFontSize, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonVolt,
                            selectedTextColor = NeonVolt,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 2,
                        onClick = { navigationIndex = 2 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil", modifier = Modifier.size(footerIconSize)) },
                        label = { Text("Profil", fontSize = footerFontSize, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ActiveOrange,
                            selectedTextColor = ActiveOrange,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = Color.Transparent
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
                // 1. Render map in background for conquest, leaderboard and guild tabs (never destroyed)
                val isMapVisible = navigationIndex != 2 && navigationIndex != 4
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = if (isMapVisible) 0.dp else 10000.dp)
                        .alpha(if (isMapVisible) 1f else 0f)
                ) {
                    ConquestMapScreen(
                        userId = userId,
                        userPseudo = userPseudo,
                        initialArea = currentAreaKm2,
                        completedPolygons = completedPolygons,
                        userEmpireColor = userEmpireColor,
                        userAvatarUrl = userAvatarUrl,
                        userGuildCouleur = userGuildCouleur,
                        userGuildNom = userGuildNom,
                        mapTargetPosition = mapTargetPosition,
                        onMapTargetPositionHandled = { mapTargetPosition = null },
                        onRunSaved = { refreshStats() }
                    )
                }

                // 2. Overlay Leaderboard screen on top of the map when on Leaderboard tab (kept in composition tree)
                val isLeaderboardVisible = navigationIndex == 1
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = if (isLeaderboardVisible) 0.dp else 10000.dp)
                        .alpha(if (isLeaderboardVisible) 1f else 0f)
                ) {
                    LeaderboardScreen(
                        isActive = isLeaderboardVisible,
                        userId = userId,
                        userGuildId = userGuildId,
                        onPlayerClick = { point ->
                            mapTargetPosition = point
                            navigationIndex = 0
                        }
                    )
                }

                // 3. Render Profile screen (kept in composition tree)
                val isProfileVisible = navigationIndex == 2
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isProfileVisible) MaterialTheme.colorScheme.background else Color.Transparent)
                        .offset(x = if (isProfileVisible) 0.dp else 10000.dp)
                        .alpha(if (isProfileVisible) 1f else 0f)
                ) {
                    ProfileScreen(
                        userId = userId,
                        userPseudo = userPseudo,
                        totalDistance = totalDistanceKm,
                        allTimeArea = allTimeAreaKm2,
                        currentArea = currentAreaKm2,
                        userEmpireColor = userEmpireColor,
                        userShareLocation = userShareLocation,
                        userGhostMode = userGhostMode,
                        userAvatarUrl = userAvatarUrl,
                        onStatsUpdated = { refreshStats() }
                    )
                }

                // 4. Render Guilde screen (kept in composition tree)
                val isGuildVisible = navigationIndex == 3
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = if (isGuildVisible) 0.dp else 10000.dp)
                        .alpha(if (isGuildVisible) 1f else 0f)
                ) {
                    GuildeScreen(
                        isActive = isGuildVisible,
                        userId = userId,
                        onBackToLogin = {
                            scope.launch {
                                try {
                                    supabase.auth.signOut()
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    )
                }

                // 5. Render Courses screen (kept in composition tree)
                val isCoursesVisible = navigationIndex == 4
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isCoursesVisible) MaterialTheme.colorScheme.background else Color.Transparent)
                        .offset(x = if (isCoursesVisible) 0.dp else 10000.dp)
                        .alpha(if (isCoursesVisible) 1f else 0f)
                ) {
                    CoursesScreen(
                        userId = userId,
                        isActive = isCoursesVisible
                    )
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
