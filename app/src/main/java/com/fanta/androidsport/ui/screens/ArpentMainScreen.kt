package com.fanta.androidsport.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
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
import com.fanta.androidsport.ui.icons.*
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
import kotlinx.serialization.Serializable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArpentMainScreen(userId: String) {
    var navigationIndex by remember { mutableStateOf(0) }
    var isFooterExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current

    var userPseudo by remember { mutableStateOf("Visiteur") }
    var totalDistanceKm by remember { mutableStateOf(0.0) }
    var allTimeAreaKm2 by remember { mutableStateOf(0.0) }
    var currentAreaKm2 by remember { mutableStateOf(0.0) }
    var userEmpireColor by remember { mutableStateOf("#00E676") }
    var userShareLocation by remember { mutableStateOf(true) }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }
    var userBannerUrl by remember { mutableStateOf<String?>(null) }
    var userXp by remember { mutableStateOf(0) }
    var userLevel by remember { mutableStateOf(1) }
    var userLoopCount by remember { mutableStateOf(0) }
    var userMaxLoopDistanceKm by remember { mutableStateOf(0.0) }
    var userMaxAreaKm2 by remember { mutableStateOf(0.0) }
    var userAreaLostKm2 by remember { mutableStateOf(0.0) }
    var userGuildId by remember { mutableStateOf<String?>(null) }
    var userGuildNom by remember { mutableStateOf<String?>(null) }
    var userGuildCouleur by remember { mutableStateOf<String?>(null) }
    var mapTargetPosition by remember { mutableStateOf<Point?>(null) }
    var userStreak by remember { mutableStateOf(0) }
    var userChange24hPct by remember { mutableStateOf(0.0) }
    var clanAreaKm2 by remember { mutableStateOf(0.0) }
    var clanChange24hPct by remember { mutableStateOf(0.0) }

    var notificationsList by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var showNotificationsModal by remember { mutableStateOf(false) }

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
                val streakDeferred = async {
                    try {
                        val params = kotlinx.serialization.json.buildJsonObject {
                            put("p_user_id", kotlinx.serialization.json.JsonPrimitive(userId))
                        }
                        supabase.postgrest.rpc("get_user_streak", params)
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to fetch user streak", e)
                        null
                    }
                }
                val empireStatsDeferred = async {
                    try {
                        val params = kotlinx.serialization.json.buildJsonObject {
                            put("p_user_id", kotlinx.serialization.json.JsonPrimitive(userId))
                        }
                        supabase.postgrest.rpc("get_empire_stats", params)
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to fetch empire stats", e)
                        null
                    }
                }

                val profileRes = profileDeferred.await()
                val coursesRes = coursesDeferred.await()
                val streakRes = streakDeferred.await()
                val empireStatsRes = empireStatsDeferred.await()

                val streak = if (streakRes != null) {
                    try {
                        Json.parseToJsonElement(streakRes.data).jsonPrimitive.intOrNull ?: 0
                    } catch (e: Exception) {
                        0
                    }
                } else {
                    0
                }

                // Parse profile info first
                val profileArray = Json.parseToJsonElement(profileRes.data) as? JsonArray
                val profileObj = profileArray?.firstOrNull() as? JsonObject
                val pseudo = profileObj?.get("pseudonyme")?.jsonPrimitive?.contentOrNull ?: "Joueur_${userId.take(8)}"
                val color = profileObj?.get("empire_color")?.jsonPrimitive?.contentOrNull ?: "#00E676"
                val shareLoc = profileObj?.get("share_location")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
                val avatarUrl = profileObj?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                val bannerUrl = profileObj?.get("banner_url")?.jsonPrimitive?.contentOrNull
                val guildeId = profileObj?.get("guilde_id")?.jsonPrimitive?.contentOrNull
                val totalAreaM2 = profileObj?.get("total_area_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val allTimeAreaM2 = profileObj?.get("all_time_area_m2")?.jsonPrimitive?.doubleOrNull ?: totalAreaM2
                val xp = profileObj?.get("xp")?.jsonPrimitive?.intOrNull ?: 0
                val level = profileObj?.get("level")?.jsonPrimitive?.intOrNull ?: 1
                val loopCount = profileObj?.get("loop_count")?.jsonPrimitive?.intOrNull ?: 0
                val maxLoopDistanceKm = profileObj?.get("max_loop_distance_km")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val maxAreaM2 = profileObj?.get("max_area_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val areaLostM2 = profileObj?.get("area_lost_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0

                // Cache user info locally for receiver access
                val prefs = context.getSharedPreferences("arpent_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("user_id", userId)
                    putString("user_pseudonyme", pseudo)
                    putInt("user_xp", xp)
                    putFloat("current_area", (totalAreaM2 / 1_000_000.0).toFloat())
                }.apply()

                // Fetch guild details if present
                var gNom: String? = null
                var gColor: String? = null
                if (guildeId != null) {
                    try {
                        val guildRes = supabase.postgrest["guildes"].select {
                            filter { eq("id", guildeId) }
                        }
                        val guildArray = Json.parseToJsonElement(guildRes.data) as? JsonArray
                        val guildObj = guildArray?.firstOrNull() as? JsonObject
                        gNom = guildObj?.get("nom")?.jsonPrimitive?.contentOrNull
                        gColor = guildObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to fetch guild info", e)
                    }
                }

                val parsed = withContext(Dispatchers.Default) {
                    val coursesArray = Json.parseToJsonElement(coursesRes.data) as? JsonArray
                    var totalDist = 0.0
                    coursesArray?.forEach {
                        val obj = it as? JsonObject
                        totalDist += obj?.get("distance_totale")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    }

                    var userChange24h = 0.0
                    var clanArea = 0.0
                    var clanChange24h = 0.0
                    if (empireStatsRes != null) {
                        try {
                            val statsArray = Json.parseToJsonElement(empireStatsRes.data) as? JsonArray
                            val statsObj = statsArray?.firstOrNull() as? JsonObject
                            userChange24h = statsObj?.get("user_change_24h_pct")?.jsonPrimitive?.doubleOrNull ?: 0.0
                            val clanAreaM2 = statsObj?.get("clan_current_area_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0
                            clanArea = clanAreaM2 / 1_000_000.0
                            clanChange24h = statsObj?.get("clan_change_24h_pct")?.jsonPrimitive?.doubleOrNull ?: 0.0
                        } catch (e: Exception) {
                            android.util.Log.e("Arpent", "Failed to parse empire stats", e)
                        }
                    }

                    mapOf(
                        "pseudo" to pseudo,
                        "color" to color,
                        "shareLoc" to shareLoc,
                        "totalDist" to totalDist,
                        "totalAreaM2" to totalAreaM2,
                        "allTimeAreaM2" to allTimeAreaM2,
                        "xp" to xp,
                        "level" to level,
                        "loopCount" to loopCount,
                        "maxLoopDistanceKm" to maxLoopDistanceKm,
                        "maxAreaM2" to maxAreaM2,
                        "areaLostM2" to areaLostM2,
                        "userChange24h" to userChange24h,
                        "clanArea" to clanArea,
                        "clanChange24h" to clanChange24h
                    )
                }

                withContext(Dispatchers.Main) {
                    userPseudo = parsed["pseudo"] as String
                    userEmpireColor = parsed["color"] as String
                    userShareLocation = parsed["shareLoc"] as Boolean
                    totalDistanceKm = parsed["totalDist"] as Double
                    currentAreaKm2 = (parsed["totalAreaM2"] as Double) / 1_000_000.0
                    allTimeAreaKm2 = (parsed["allTimeAreaM2"] as Double) / 1_000_000.0
                    userXp = parsed["xp"] as Int
                    userLevel = parsed["level"] as Int
                    userLoopCount = parsed["loopCount"] as Int
                    userMaxLoopDistanceKm = parsed["maxLoopDistanceKm"] as Double
                    userMaxAreaKm2 = (parsed["maxAreaM2"] as Double) / 1_000_000.0
                    userAreaLostKm2 = (parsed["areaLostM2"] as Double) / 1_000_000.0
                    userAvatarUrl = avatarUrl
                    userBannerUrl = bannerUrl
                    userGuildId = guildeId
                    userGuildNom = gNom
                    userGuildCouleur = gColor
                    userStreak = streak
                    userChange24hPct = parsed["userChange24h"] as Double
                    clanAreaKm2 = parsed["clanArea"] as Double
                    clanChange24hPct = parsed["clanChange24h"] as Double
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error fetching stats", e)
            }
        }
    }

    fun refreshNotifications() {
        if (!isNetworkAvailable(context)) return
        scope.launch(Dispatchers.IO) {
            try {
                val res = supabase.postgrest["notifications"].select {
                    filter { eq("utilisateur_id", userId) }
                }
                val list = Json.decodeFromString<List<NotificationItem>>(res.data)
                val sorted = list.sortedByDescending { it.date_creation }
                withContext(Dispatchers.Main) {
                    notificationsList = sorted
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error fetching notifications", e)
            }
        }
    }

    LaunchedEffect(userId) {
        // Load from local storage immediately so there is zero delay/blank screen
        val localPolys = loadTerritoriesLocally(context)
        completedPolygons.clear()
        completedPolygons.addAll(localPolys)

        refreshStats()
        refreshNotifications()
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
        val list = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            list.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
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
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Glowing status indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showNotificationsModal = true
                            refreshNotifications()
                        }) {
                            val unreadCount = notificationsList.count { !it.lu }
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text(unreadCount.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = com.fanta.androidsport.R.drawable.ic_notification),
                                    contentDescription = "Notifications",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. Render map in background for conquest, leaderboard, guild and courses tabs (never destroyed)
                val isMapVisible = navigationIndex != 2
                val mapBottomPadding = when (navigationIndex) {
                    0 -> if (isFooterExpanded) 80.dp else 0.dp
                    else -> 80.dp
                }
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
                        onRunSaved = { refreshStats() },
                        bottomPadding = mapBottomPadding,
                        userChange24hPct = userChange24hPct,
                        clanAreaKm2 = clanAreaKm2,
                        clanChange24hPct = clanChange24hPct
                    )
                }

                // 2. Overlay Leaderboard screen on top of the map when on Leaderboard tab (kept in composition tree)
                val isLeaderboardVisible = navigationIndex == 1
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
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
                        .padding(bottom = 80.dp)
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
                        userAvatarUrl = userAvatarUrl,
                        userBannerUrl = userBannerUrl,
                        xp = userXp,
                        level = userLevel,
                        loopCount = userLoopCount,
                        maxLoopDistanceKm = userMaxLoopDistanceKm,
                        maxAreaKm2 = userMaxAreaKm2,
                        areaLostKm2 = userAreaLostKm2,
                        userStreak = userStreak,
                        userGuildNom = userGuildNom,
                        userGuildCouleur = userGuildCouleur,
                        completedPolygons = completedPolygons,
                        onStatsUpdated = { refreshStats() },
                        onNavigateToTerritory = { point ->
                            mapTargetPosition = point
                            navigationIndex = 0
                        },
                        isActive = isProfileVisible
                    )
                }

                // 4. Render Guilde screen (kept in composition tree)
                val isGuildVisible = navigationIndex == 3
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
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
                        .padding(bottom = 80.dp)
                        .background(Color.Transparent)
                        .offset(x = if (isCoursesVisible) 0.dp else 10000.dp)
                        .alpha(if (isCoursesVisible) 1f else 0f)
                ) {
                    CoursesScreen(
                        userId = userId,
                        isActive = isCoursesVisible,
                        onNavigateToTerritory = { point ->
                            mapTargetPosition = point
                            navigationIndex = 0
                        }
                    )
                }

                // Collapsible/Expandable Navigation Bar overlay
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

                // Only allow folding on the home screen (conquest, navigationIndex == 0)
                val canFold = navigationIndex == 0
                val bottomBarHeight = 80.dp
                val footerOffset by animateDpAsState(
                    targetValue = if (canFold && !isFooterExpanded) bottomBarHeight else 0.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "footer_slide_offset"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = footerOffset)
                        .fillMaxWidth()
                ) {
                    // Protruding central tab (languette)
                    if (canFold) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp)
                                .size(width = 64.dp, height = 28.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
                                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .clickable {
                                    isFooterExpanded = !isFooterExpanded
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Icon(
                                imageVector = if (isFooterExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isFooterExpanded) "Réduire le menu" else "Déplier le menu",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                            )
                        }
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBarItem(
                            selected = navigationIndex == 0,
                            onClick = { navigationIndex = 0 },
                            icon = { Icon(map_search, contentDescription = "Carte", modifier = Modifier.size(footerIconSize)) },
                            label = { Text("Carte", fontSize = footerFontSize, fontWeight = FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = navigationIndex == 1,
                            onClick = { navigationIndex = 1 },
                            icon = { Icon(social_leaderboard, contentDescription = "Classement", modifier = Modifier.size(footerIconSize)) },
                            label = { Text("Classement", fontSize = footerFontSize, fontWeight = FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = navigationIndex == 4,
                            onClick = { navigationIndex = 4 },
                            icon = { Icon(aod_watch, contentDescription = "Courses", modifier = Modifier.size(footerIconSize)) },
                            label = { Text("Courses", fontSize = footerFontSize, fontWeight = FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = navigationIndex == 3,
                            onClick = { navigationIndex = 3 },
                            icon = { Icon(groups, contentDescription = "Social", modifier = Modifier.size(footerIconSize)) },
                            label = { Text("Social", fontSize = footerFontSize, fontWeight = FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = navigationIndex == 2,
                            onClick = { navigationIndex = 2 },
                            icon = { Icon(raven, contentDescription = "Profil", modifier = Modifier.size(footerIconSize)) },
                            label = { Text("Profil", fontSize = footerFontSize, fontWeight = FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        if (showNotificationsModal) {
            val unreadCount = notificationsList.count { !it.lu }
            AlertDialog(
                onDismissRequest = { showNotificationsModal = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (unreadCount > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            supabase.postgrest["notifications"].update(
                                                mapOf("lu" to true)
                                            ) {
                                                filter { eq("utilisateur_id", userId) }
                                            }
                                            refreshNotifications()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            ) {
                                Text("Tout lire", fontSize = 12.sp)
                            }
                        }
                    }
                },
                text = {
                    if (notificationsList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Aucune notification",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notificationsList) { item ->
                                NotificationRow(
                                    item = item,
                                    onClick = {
                                        if (!item.lu) {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    supabase.postgrest["notifications"].update(
                                                        mapOf("lu" to true)
                                                    ) {
                                                        filter { eq("id", item.id) }
                                                    }
                                                    refreshNotifications()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        when (item.type) {
                                            1 -> navigationIndex = 0
                                            2 -> navigationIndex = 4
                                            3 -> navigationIndex = 4
                                            4 -> navigationIndex = 3
                                        }
                                        showNotificationsModal = false
                                    },
                                    onDelete = {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                supabase.postgrest["notifications"].delete {
                                                    filter { eq("id", item.id) }
                                                }
                                                refreshNotifications()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotificationsModal = false }) {
                        Text("Fermer")
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            )
        }
    } else {
        PermissionRequestScreen(
            onRequestPermissions = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
    }
}

@Serializable
data class NotificationItem(
    val id: String,
    val utilisateur_id: String,
    val type: Int,
    val titre: String,
    val message: String,
    val lu: Boolean,
    val date_creation: String,
    val metadata: JsonElement? = null
)

@Composable
fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val unreadColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val readColor = Color.Transparent
    val backgroundColor = if (item.lu) readColor else unreadColor
    
    val icon = when (item.type) {
        1 -> Icons.Default.Warning
        2 -> Icons.Default.Favorite
        3 -> Icons.Default.Comment
        4 -> Icons.Default.PersonAdd
        5 -> Icons.Default.WbSunny
        6 -> Icons.Default.Star
        else -> Icons.Default.Notifications
    }
    
    val iconTint = when (item.type) {
        1 -> Color(0xFFE53935)
        2 -> Color(0xFFEC407A)
        3 -> Color(0xFF1E88E5)
        4 -> Color(0xFF43A047)
        5 -> Color(0xFFFFB300)
        6 -> Color(0xFFFDD835)
        else -> MaterialTheme.colorScheme.primary
    }

    val relativeTime = remember(item.date_creation) {
        try {
            val parser = java.time.format.DateTimeFormatter.ISO_DATE_TIME
            val date = java.time.ZonedDateTime.parse(item.date_creation, parser)
            val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"))
            val diffSeconds = java.time.Duration.between(date, now).seconds
            
            when {
                diffSeconds < 60 -> "À l'instant"
                diffSeconds < 3600 -> "Il y a ${diffSeconds / 60} min"
                diffSeconds < 86400 -> "Il y a ${diffSeconds / 3600} h"
                diffSeconds < 172800 -> "Hier"
                else -> "Il y a ${diffSeconds / 86400} j"
            }
        } catch (e: Exception) {
            item.date_creation.take(10)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(
            1.dp, 
            if (!item.lu) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.titre,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = relativeTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (!item.lu) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = item.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
