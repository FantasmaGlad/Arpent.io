package com.fanta.androidsport.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.ColorWheel
import com.fanta.androidsport.ui.components.TerritoryMapBackground
import com.fanta.androidsport.ui.theme.ThemeManager
import com.fanta.androidsport.utils.assetPresetLabel
import com.fanta.androidsport.utils.fetchPlayerTerritoryPolygons
import com.fanta.androidsport.utils.getPolygonArea
import com.fanta.androidsport.utils.getPolygonCentroid
import com.fanta.androidsport.utils.listAssetPresetImages
import com.fanta.androidsport.utils.map_search
import com.mapbox.geojson.Point
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

// Shared defaults used until a player uploads or picks their own — see assets/Profils and assets/bannieres
private const val DEFAULT_BANNER_ASSET = "file:///android_asset/bannieres/Baamix_Simpsons.png"

data class FullFriendItem(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?,
    val empireColor: String,
    val level: Int,
    val guildNom: String?
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    userPseudo: String,
    totalDistance: Double,
    allTimeArea: Double,
    currentArea: Double,
    userEmpireColor: String,
    userShareLocation: Boolean,
    userAvatarUrl: String?,
    userBannerUrl: String? = null,
    xp: Int,
    level: Int,
    loopCount: Int,
    maxLoopDistanceKm: Double,
    maxAreaKm2: Double,
    areaLostKm2: Double,
    userStreak: Int,
    userGuildNom: String?,
    userGuildCouleur: String?,
    completedPolygons: List<List<Point>>,
    onStatsUpdated: () -> Unit,
    onNavigateToTerritory: ((Point) -> Unit)? = null,
    isActive: Boolean = true,
    settingsOpenSignal: Int = 0
) {
    PlayerProfileContent(
        isMe = true,
        userId = userId,
        userPseudo = userPseudo,
        totalDistance = totalDistance,
        allTimeArea = allTimeArea,
        currentArea = currentArea,
        userEmpireColor = userEmpireColor,
        userShareLocation = userShareLocation,
        userAvatarUrl = userAvatarUrl,
        userBannerUrl = userBannerUrl,
        xp = xp,
        level = level,
        loopCount = loopCount,
        maxLoopDistanceKm = maxLoopDistanceKm,
        maxAreaKm2 = maxAreaKm2,
        areaLostKm2 = areaLostKm2,
        userStreak = userStreak,
        userGuildNom = userGuildNom,
        userGuildCouleur = userGuildCouleur,
        completedPolygons = completedPolygons,
        onStatsUpdated = onStatsUpdated,
        onNavigateToTerritory = onNavigateToTerritory,
        isActive = isActive,
        settingsOpenSignal = settingsOpenSignal
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileContent(
    isMe: Boolean,
    userId: String,
    userPseudo: String,
    totalDistance: Double,
    allTimeArea: Double,
    currentArea: Double,
    userEmpireColor: String,
    userShareLocation: Boolean = true,
    userAvatarUrl: String?,
    userBannerUrl: String? = null,
    xp: Int = 0,
    level: Int = 1,
    loopCount: Int = 0,
    maxLoopDistanceKm: Double = 0.0,
    maxAreaKm2: Double = 0.0,
    areaLostKm2: Double = 0.0,
    userStreak: Int = 0,
    userGuildNom: String?,
    userGuildCouleur: String? = null,
    completedPolygons: List<List<Point>> = emptyList(),
    onStatsUpdated: () -> Unit = {},
    onCloseClick: (() -> Unit)? = null,
    onNavigateToTerritory: ((Point) -> Unit)? = null,
    isActive: Boolean = true,
    settingsOpenSignal: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Preferences for user biography / description
    val prefs = remember(userId) {
        context.getSharedPreferences("arpent_profile_prefs_${userId}", android.content.Context.MODE_PRIVATE)
    }
    var localDescription by remember(userId) {
        mutableStateOf(prefs.getString("description", "") ?: "")
    }

    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showEditPseudoDialog by remember { mutableStateOf(false) }

    // States for colors & theme settings
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val parsedUserColor = remember(userEmpireColor, defaultPrimary) {
        try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            defaultPrimary
        }
    }

    var localColor by remember(userEmpireColor, defaultPrimary) {
        val parsed = try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            defaultPrimary
        }
        mutableStateOf(parsed)
    }

    var shareLocationEnabled by remember(userShareLocation) {
        mutableStateOf(userShareLocation)
    }

    var showColorPicker by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }

    // Settings sheet can be opened remotely (e.g. from the app's main header on the Profile tab)
    LaunchedEffect(settingsOpenSignal) {
        if (settingsOpenSignal > 0) showSettingsSheet = true
    }

    // Accent color: theme primary for own profile, empire color for other players
    val activeColor = if (isMe) MaterialTheme.colorScheme.primary else parsedUserColor

    // Card style: flat black at 30% opacity for every profile, own or other player's —
    // no longer tinted by theme or empire color.
    val cardColor = Color.Black.copy(alpha = 0.30f)
    val onCardColor = Color.White
    val onCardMuted = onCardColor.copy(alpha = 0.65f)
    val onBackground = MaterialTheme.colorScheme.onBackground

    val parsedGuildColor = remember(userGuildCouleur, onCardMuted) {
        try {
            userGuildCouleur?.let { Color(android.graphics.Color.parseColor(it)) }
        } catch (e: Exception) {
            null
        }
    }

    // Friend list loading for social card
    var friendsList by remember { mutableStateOf<List<FullFriendItem>>(emptyList()) }
    var isFriendsLoading by remember { mutableStateOf(false) }

    fun loadFriends() {
        if (!isMe) return
        scope.launch(Dispatchers.IO) {
            try {
                isFriendsLoading = true
                val res = supabase.postgrest["amis"].select {
                    filter {
                        and {
                            eq("statut", "accepte")
                            or {
                                eq("demandeur_id", userId)
                                eq("destinataire_id", userId)
                            }
                        }
                    }
                }
                val array = Json.parseToJsonElement(res.data) as? JsonArray
                val otherIds = mutableListOf<String>()
                array?.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val demId = obj["demandeur_id"]?.jsonPrimitive?.content ?: return@forEach
                    val destId = obj["destinataire_id"]?.jsonPrimitive?.content ?: return@forEach
                    if (demId == userId) otherIds.add(destId) else otherIds.add(demId)
                }
                if (otherIds.isNotEmpty()) {
                    val profilesRes = supabase.postgrest["profiles"].select {
                        filter {
                            isIn("id", otherIds)
                        }
                    }
                    val profArray = Json.parseToJsonElement(profilesRes.data) as? JsonArray
                    val list = mutableListOf<FullFriendItem>()
                    val guildIds = profArray?.mapNotNull {
                        (it as? JsonObject)?.get("guilde_id")?.jsonPrimitive?.contentOrNull
                    }?.distinct() ?: emptyList()

                    val guildMap = mutableMapOf<String, String>()
                    if (guildIds.isNotEmpty()) {
                        val guildRes = supabase.postgrest["guildes"].select {
                            filter {
                                isIn("id", guildIds)
                            }
                        }
                        val gArray = Json.parseToJsonElement(guildRes.data) as? JsonArray
                        gArray?.forEach {
                            val o = it as? JsonObject
                            val gId = o?.get("id")?.jsonPrimitive?.content ?: return@forEach
                            val gNom = o["nom"]?.jsonPrimitive?.contentOrNull ?: "Guilde"
                            guildMap[gId] = gNom
                        }
                    }

                    profArray?.forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        val pId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                        val lvl = obj["level"]?.jsonPrimitive?.intOrNull ?: 1
                        val gId = obj["guilde_id"]?.jsonPrimitive?.contentOrNull
                        val gNom = gId?.let { guildMap[it] }
                        list.add(FullFriendItem(pId, pseudo, avatar, color, lvl, gNom))
                    }
                    friendsList = list
                } else {
                    friendsList = emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to fetch friends in ProfileScreen", e)
            } finally {
                isFriendsLoading = false
            }
        }
    }

    LaunchedEffect(userId) {
        loadFriends()
    }

    LaunchedEffect(shareLocationEnabled) {
        if (shareLocationEnabled == userShareLocation) return@LaunchedEffect
        delay(300)
        try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["profiles"].update(
                    mapOf("share_location" to shareLocationEnabled)
                ) {
                    filter { eq("id", userId) }
                }
            }
            onStatsUpdated()
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to update share_location", e)
        }
    }

    LaunchedEffect(localColor) {
        val localHex = String.format("#%06X", 0xFFFFFF and localColor.toArgb())
        if (localHex.equals(userEmpireColor, ignoreCase = true)) return@LaunchedEffect
        delay(500)
        try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["profiles"].update(
                    mapOf("empire_color" to localHex)
                ) {
                    filter { eq("id", userId) }
                }
            }
            onStatsUpdated()
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to update empire color", e)
        }
    }

    // Local state to track the banner URL with cache-busting
    var localBannerUrl by remember(userId, userBannerUrl) { mutableStateOf(userBannerUrl) }
    var showBannerSelectionDialog by remember { mutableStateOf(false) }
    var showAvatarSelectionDialog by remember { mutableStateOf(false) }

    val uploadAvatarBytes: (ByteArray) -> Unit = { bytes ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bucket = supabase.storage.from("Images")
                    val filename = "${userId}.jpg"
                    bucket.upload(filename, bytes) {
                        upsert = true
                    }
                    val publicUrl = bucket.publicUrl(filename)
                    val finalUrl = "$publicUrl?t=${System.currentTimeMillis()}"

                    supabase.postgrest["profiles"].update(
                        mapOf("avatar_url" to finalUrl)
                    ) {
                        filter { eq("id", userId) }
                    }
                }
                onStatsUpdated()
                Toast.makeText(context, "Avatar mis à jour !", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to update avatar", e)
            }
        }
    }

    val uploadAvatarFromAsset: (String) -> Unit = { assetPath ->
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.assets.open(assetPath).use { it.readBytes() }
                }
                uploadAvatarBytes(bytes)
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to read avatar from asset: $assetPath", e)
            }
        }
    }

    // Supabase avatar photo upload picker (gallery)
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        uploadAvatarBytes(bytes)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Arpent", "Failed to read URI for avatar", e)
                }
            }
        }
    }

    val uploadBannerBytes: (ByteArray) -> Unit = { bytes ->
        scope.launch {
            try {
                val publicUrl = withContext(Dispatchers.IO) {
                    val bucket = supabase.storage.from("Images")
                    val filename = "${userId}_banner.jpg"
                    bucket.upload(filename, bytes) {
                        upsert = true
                    }
                    bucket.publicUrl(filename)
                }
                val finalUrl = "$publicUrl?t=${System.currentTimeMillis()}"

                withContext(Dispatchers.IO) {
                    supabase.postgrest["profiles"].update(
                        mapOf("banner_url" to finalUrl)
                    ) {
                        filter { eq("id", userId) }
                    }
                }
                withContext(Dispatchers.Main) {
                    localBannerUrl = finalUrl
                }
                onStatsUpdated()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bannière mise à jour !", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to update banner", e)
            }
        }
    }

    val uploadBannerFromAsset: (String) -> Unit = { assetName ->
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.assets.open(assetName).use { it.readBytes() }
                }
                uploadBannerBytes(bytes)
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to read banner from asset: $assetName", e)
            }
        }
    }

    val bannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                    }
                    if (bytes != null) {
                        uploadBannerBytes(bytes)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Arpent", "Failed to read URI for banner", e)
                }
            }
        }
    }

    // MAIN CONTAINER — the "Carte" Mapbox map as background, without any button
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isActive) {
            TerritoryMapBackground(
                polygons = completedPolygons,
                empireColor = if (isMe) localColor else parsedUserColor,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Readability scrim over the map
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.60f),
                        0.4f to MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.60f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Slim action header (back / map-search / settings) over the map.
            // On the main Profile tab (onCloseClick == null) these actions live in the
            // app's top header bar instead — see ArpentMainScreen.
            if (onCloseClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = onBackground
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onNavigateToTerritory != null) {
                        IconButton(
                            onClick = {
                                if (completedPolygons.isNotEmpty()) {
                                    val largest = completedPolygons.maxByOrNull { polygon ->
                                        getPolygonArea(polygon)
                                    }
                                    val centroid = largest?.let { getPolygonCentroid(it) }
                                    if (centroid != null) onNavigateToTerritory(centroid)
                                } else if (!isMe) {
                                    // Fetch territories from Supabase for other players
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val polygons = fetchPlayerTerritoryPolygons(userId)
                                            val largest = polygons.maxByOrNull { getPolygonArea(it) }
                                            val centroid = largest?.let { getPolygonCentroid(it) }
                                            if (centroid != null) {
                                                withContext(Dispatchers.Main) {
                                                    onNavigateToTerritory(centroid)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("Arpent", "Failed to fetch territories for map nav", e)
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = map_search,
                                contentDescription = "Voir le territoire",
                                tint = onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (isMe) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Paramètres",
                                tint = onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 1. BANNER — full screen width (w350 h150), tap to import an image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .clickable(enabled = isMe) {
                        showBannerSelectionDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                // Fall back to the shared default banner until a custom one is set
                AsyncImage(
                    model = localBannerUrl ?: DEFAULT_BANNER_ASSET,
                    contentDescription = "Bannière",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 1f
                )
                if (isMe) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier la bannière",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. PROFILE ROW — avatar (no halo), pseudo, guild, level, pseudo edit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .clickable(enabled = isMe) {
                            showAvatarSelectionDialog = true
                        }
                ) {
                    AvatarImage(
                        avatarUrl = userAvatarUrl,
                        modifier = Modifier.fillMaxSize(),
                        placeholderColor = activeColor,
                        placeholderIcon = Icons.Default.Person
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = userPseudo,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userGuildNom ?: "Sans Guilde",
                        fontSize = 13.sp,
                        color = parsedGuildColor ?: onBackground.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Niveau $level",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = onBackground
                    )
                    val (xpIntoLevel, xpStepForLevel) = remember(xp, level) { xpProgressInLevel(xp, level) }
                    Text(
                        text = "$xpIntoLevel / $xpStepForLevel XP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = onBackground.copy(alpha = 0.6f)
                    )
                    if (isMe) {
                        IconButton(
                            onClick = { showEditPseudoDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Modifier le pseudo",
                                tint = onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. DESCRIPTION CARD — full width, edge-to-edge like the course cards
            ProfileSectionCard(
                title = "Description",
                cardColor = cardColor,
                onCardColor = onCardColor,
                onClick = if (isMe) { { showEditDescriptionDialog = true } } else null,
                horizontalPadding = 0.dp
            ) {
                Text(
                    text = localDescription.ifEmpty {
                        if (isMe) "Appuyez pour ajouter une description."
                        else "Ce conquérant n'a pas rédigé de description."
                    },
                    color = if (localDescription.isEmpty()) onCardMuted else onCardColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. SUCCÈS CARD — emptied, new achievements will come later
            ProfileSectionCard(
                title = "Succès",
                cardColor = cardColor,
                onCardColor = onCardColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "De nouveaux succès arrivent bientôt…",
                        color = onCardMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. SOCIAL SECTION — temporarily hidden ahead of a future redesign
            if (isMe) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "file:///android_asset/EnConstruction.png",
                        contentDescription = "Section en construction",
                        modifier = Modifier.fillMaxSize(0.6f),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 6. STATISTIQUES CARD — inline stats, always visible
            ProfileSectionCard(
                title = "Statistiques",
                cardColor = cardColor,
                onCardColor = onCardColor
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = "Distance totale",
                        value = "${"%.2f".format(totalDistance)} km",
                        icon = Icons.Default.DirectionsRun,
                        tint = activeColor,
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = "Max boucle",
                        value = "${"%.2f".format(maxLoopDistanceKm)} km",
                        icon = Icons.Default.TrendingUp,
                        tint = Color(0xFFF59E0B),
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = "Territoire actuel",
                        value = "${"%.3f".format(currentArea)} km²",
                        icon = Icons.Default.Map,
                        tint = activeColor,
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = "Conquis all-time",
                        value = "${"%.3f".format(allTimeArea)} km²",
                        icon = Icons.Default.Public,
                        tint = Color(0xFF06B6D4),
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = "Territoire volé",
                        value = "${"%.3f".format(areaLostKm2)} km²",
                        icon = Icons.Default.TrendingDown,
                        tint = Color(0xFFEF4444),
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = "Superficie max",
                        value = "${"%.3f".format(maxAreaKm2)} km²",
                        icon = Icons.Default.PhotoSizeSelectActual,
                        tint = Color(0xFF8B5CF6),
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = "Boucles fermées",
                        value = "$loopCount",
                        icon = Icons.Default.Loop,
                        tint = activeColor,
                        onCardColor = onCardColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (isMe) {
                        StatTile(
                            title = "Série",
                            value = "$userStreak j",
                            icon = Icons.Default.LocalFireDepartment,
                            tint = Color(0xFFFF6D00),
                            onCardColor = onCardColor,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Modal Bottom Sheet: Settings Panel (Only if isMe)
    if (showSettingsSheet && isMe) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val onSurface = MaterialTheme.colorScheme.onSurface
            val dividerColor = onSurface.copy(alpha = 0.12f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "PARAMÈTRES DE L'APPLICATION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = onSurface,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = dividerColor)

                // Empire Color selector
                Column {
                    ListItem(
                        headlineContent = { Text("Couleur de l'Empire", color = onSurface, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Personnalisez votre couleur sur la carte", color = onSurface.copy(alpha = 0.6f)) },
                        leadingContent = {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = activeColor)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(localColor)
                                        .border(1.dp, dividerColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (showColorPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        modifier = Modifier.clickable { showColorPicker = !showColorPicker },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    AnimatedVisibility(
                        visible = showColorPicker,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ColorWheel(
                                selectedColor = localColor,
                                onColorSelected = { localColor = it },
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // Theme selection
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Thème visuel", fontWeight = FontWeight.SemiBold, color = onSurface, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("forest" to "Forêt", "orchid" to "Orchidée", "blue_sky" to "Ciel").forEach { (themeId, label) ->
                            val isSelected = ThemeManager.themeState.value == themeId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    ThemeManager.themeState.value = themeId
                                    // Save selection locally
                                    try {
                                        val themePrefs = context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                                        themePrefs.edit().putString("active_theme", themeId).apply()
                                    } catch (_: Exception) {}
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // Share Location
                ListItem(
                    headlineContent = { Text("Partager ma position (Temps réel)", color = onSurface, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Permet aux autres joueurs de voir votre position sur la carte", color = onSurface.copy(alpha = 0.6f)) },
                    leadingContent = {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = onSurface.copy(alpha = 0.6f))
                    },
                    trailingContent = {
                        Switch(
                            checked = shareLocationEnabled,
                            onCheckedChange = { shareLocationEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = activeColor
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = dividerColor)

                // Logout button
                ListItem(
                    headlineContent = { Text("Se déconnecter", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Déconnexion", tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            try {
                                supabase.auth.signOut()
                                val file = File(context.filesDir, "local_territories.json")
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erreur de déconnexion", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialogue: Modifier le pseudo
    if (showEditPseudoDialog && isMe) {
        var pseudoText by remember { mutableStateOf(userPseudo) }
        AlertDialog(
            onDismissRequest = { showEditPseudoDialog = false },
            title = { Text("Modifier le pseudo") },
            text = {
                OutlinedTextField(
                    value = pseudoText,
                    onValueChange = { pseudoText = it },
                    label = { Text("Nom d'utilisateur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pseudoText.trim().isEmpty()) {
                        Toast.makeText(context, "Pseudo vide", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                supabase.postgrest["profiles"].update(
                                    mapOf("pseudonyme" to pseudoText.trim())
                                ) {
                                    filter { eq("id", userId) }
                                }
                            }
                            onStatsUpdated()
                            Toast.makeText(context, "Pseudo enregistré !", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erreur de mise à jour", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showEditPseudoDialog = false
                }) {
                    Text("Enregistrer", color = activeColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPseudoDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue: Modifier la description
    if (showEditDescriptionDialog && isMe) {
        var editingText by remember { mutableStateOf(localDescription) }
        AlertDialog(
            onDismissRequest = { showEditDescriptionDialog = false },
            title = { Text("Modifier la description") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("Votre biographie") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    localDescription = editingText
                    prefs.edit().putString("description", editingText).apply()
                    showEditDescriptionDialog = false
                    Toast.makeText(context, "Description mise à jour !", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Enregistrer", color = activeColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDescriptionDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue: Choisir une bannière prédéfinie (dynamique depuis assets/bannieres) ou galerie
    if (showBannerSelectionDialog && isMe) {
        val presetBanners = remember { listAssetPresetImages(context, "bannieres") }
        MediaPresetSelectionDialog(
            title = "Personnaliser la bannière",
            presets = presetBanners,
            tileShape = MediaTileShape.BANNER,
            onPresetSelected = { assetPath ->
                showBannerSelectionDialog = false
                uploadBannerFromAsset(assetPath)
            },
            onGalleryClick = {
                showBannerSelectionDialog = false
                bannerLauncher.launch("image/*")
            },
            onDismiss = { showBannerSelectionDialog = false }
        )
    }

    // Dialogue: Choisir un avatar prédéfini (dynamique depuis assets/Profils) ou galerie
    if (showAvatarSelectionDialog && isMe) {
        val presetAvatars = remember { listAssetPresetImages(context, "Profils") }
        MediaPresetSelectionDialog(
            title = "Personnaliser la photo de profil",
            presets = presetAvatars,
            tileShape = MediaTileShape.AVATAR,
            onPresetSelected = { assetPath ->
                showAvatarSelectionDialog = false
                uploadAvatarFromAsset(assetPath)
            },
            onGalleryClick = {
                showAvatarSelectionDialog = false
                imageLauncher.launch("image/*")
            },
            onDismiss = { showAvatarSelectionDialog = false }
        )
    }

    // Dialogue: Add friend search (Social card)
    if (showAddFriendDialog && isMe) {
        var queryText by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("Ajouter un ami") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        label = { Text("Pseudonyme du joueur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (queryText.trim().isEmpty()) return@Button
                            isSearching = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val res = supabase.postgrest["profiles"].select {
                                        filter {
                                            ilike("pseudonyme", "%${queryText.trim()}%")
                                            neq("id", userId) // Do not search self
                                        }
                                    }
                                    val array = Json.parseToJsonElement(res.data) as? JsonArray
                                    searchResults = array?.mapNotNull { it as? JsonObject } ?: emptyList()
                                } catch (e: Exception) {
                                    android.util.Log.e("Arpent", "Search profiles failed", e)
                                } finally {
                                    isSearching = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Rechercher")
                    }

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = activeColor)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchResults.take(3).forEach { userObj ->
                                val pId = userObj["id"]?.jsonPrimitive?.content ?: ""
                                val pseudo = userObj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                                val avatar = userObj["avatar_url"]?.jsonPrimitive?.contentOrNull
                                val userColorHex = userObj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                                val userColor = try { Color(android.graphics.Color.parseColor(userColorHex)) } catch (_: Exception) { activeColor }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = avatar,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, userColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(pseudo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    supabase.postgrest["amis"].insert(
                                                        mapOf(
                                                            "demandeur_id" to userId,
                                                            "destinataire_id" to pId,
                                                            "statut" to "en_attente"
                                                        )
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Demande envoyée !", Toast.LENGTH_SHORT).show()
                                                        showAddFriendDialog = false
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Déjà demandé / erreur", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Ajouter", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (queryText.isNotEmpty()) {
                        Text("Aucun joueur trouvé", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Fermer", color = activeColor)
                }
            }
        )
    }
}

// Mirrors the SQL xp_to_level() curve (100 XP for level 1, x1.15 per level) so the
// UI can show progress within the current level without a server round-trip.
private fun xpProgressInLevel(xp: Int, level: Int): Pair<Int, Int> {
    var cumulative = 0.0
    var step = 100.0
    for (i in 1 until level) {
        cumulative += step
        step *= 1.15
    }
    val intoLevel = (xp - cumulative).toInt().coerceAtLeast(0)
    return intoLevel to step.toInt()
}

// Rounded translucent section container (theme or empire color at 70% opacity)
@Composable
private fun ProfileSectionCard(
    title: String,
    cardColor: Color,
    onCardColor: Color,
    onClick: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    horizontalPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = onCardColor,
                modifier = Modifier.align(Alignment.Center)
            )
            if (trailingIcon != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    trailingIcon()
                }
            }
        }
        content()
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onCardColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(onCardColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 10.sp,
                color = onCardColor.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = onCardColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class MediaTileShape { BANNER, AVATAR }

// Dialog listing preset images discovered at runtime in an assets subfolder (see
// utils/listAssetPresetImages), plus a "pick from gallery" option. Dropping a new
// PNG/JPG into that folder is enough to add a new preset — no code change needed.
@Composable
private fun MediaPresetSelectionDialog(
    title: String,
    presets: List<String>,
    tileShape: MediaTileShape,
    onPresetSelected: (String) -> Unit,
    onGalleryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    presets.forEach { assetPath ->
                        when (tileShape) {
                            MediaTileShape.BANNER -> BannerPresetTile(
                                assetPath = assetPath,
                                label = assetPresetLabel(assetPath),
                                onClick = { onPresetSelected(assetPath) }
                            )
                            MediaTileShape.AVATAR -> AvatarPresetRow(
                                assetPath = assetPath,
                                label = assetPresetLabel(assetPath),
                                onClick = { onPresetSelected(assetPath) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), RoundedCornerShape(16.dp))
                        .clickable { onGalleryClick() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Importer",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Importer depuis la galerie",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Annuler",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// Full-width rectangular preview tile used for banner presets
@Composable
private fun BannerPresetTile(
    assetPath: String,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = "file:///android_asset/$assetPath",
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

// Compact circular preview row used for avatar presets (matches how they'll actually render)
@Composable
private fun AvatarPresetRow(
    assetPath: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "file:///android_asset/$assetPath",
            contentDescription = label,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileDialog(
    playerId: String,
    currentUserId: String,
    onDismissRequest: () -> Unit,
    onNavigateToTerritory: ((Point) -> Unit)? = null
) {
    var playerProfileDetail by remember(playerId) { mutableStateOf<ProfileDetails?>(null) }
    var playerPolygons by remember(playerId) { mutableStateOf<List<List<Point>>>(emptyList()) }
    var isLoading by remember(playerId) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadPlayerProfile() {
        scope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                val res = supabase.postgrest["profiles"].select {
                    filter { eq("id", playerId) }
                }
                val array = Json.parseToJsonElement(res.data) as? JsonArray
                val obj = array?.firstOrNull() as? JsonObject
                if (obj != null) {
                    val id = obj["id"]?.jsonPrimitive?.content ?: playerId
                    val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                    val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val banner = obj["banner_url"]?.jsonPrimitive?.contentOrNull
                    val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                    val lvl = obj["level"]?.jsonPrimitive?.intOrNull ?: 1
                    val xp = obj["xp"]?.jsonPrimitive?.intOrNull ?: 0
                    val loop = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                    val area = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val allTimeAreaM2 = obj["all_time_area_m2"]?.jsonPrimitive?.doubleOrNull ?: area
                    val maxLoopKm = obj["max_loop_distance_km"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val maxAreaM2 = obj["max_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val areaLostM2 = obj["area_lost_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                    // "distance_totale" lives on the courses table, not on profiles — sum it
                    // here the same way ArpentMainScreen does for the logged-in user, otherwise
                    // this always reads as 0 for every other player's profile.
                    val dist = try {
                        val coursesRes = supabase.postgrest["courses"].select {
                            filter { eq("utilisateur_id", playerId) }
                        }
                        val coursesArray = Json.parseToJsonElement(coursesRes.data) as? JsonArray
                        coursesArray?.sumOf {
                            (it as? JsonObject)?.get("distance_totale")?.jsonPrimitive?.doubleOrNull ?: 0.0
                        } ?: 0.0
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to sum player courses distance", e)
                        0.0
                    }

                    val gId = obj["guilde_id"]?.jsonPrimitive?.contentOrNull
                    var gNom: String? = null
                    var gCouleur: String? = null
                    if (gId != null) {
                        try {
                            val gRes = supabase.postgrest["guildes"].select {
                                filter { eq("id", gId) }
                            }
                            val gArray = Json.parseToJsonElement(gRes.data) as? JsonArray
                            val gObj = gArray?.firstOrNull() as? JsonObject
                            gNom = gObj?.get("nom")?.jsonPrimitive?.contentOrNull
                            gCouleur = gObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull
                        } catch (_: Exception) {}
                    }

                    playerProfileDetail = ProfileDetails(
                        id = id,
                        pseudonyme = pseudo,
                        avatarUrl = avatar,
                        bannerUrl = banner,
                        empireColor = color,
                        level = lvl,
                        xp = xp,
                        guildeNom = gNom,
                        guildeCouleur = gCouleur,
                        totalDistance = dist,
                        currentAreaM2 = area,
                        allTimeAreaM2 = allTimeAreaM2,
                        loopCount = loop,
                        maxLoopDistanceKm = maxLoopKm,
                        maxAreaKm2 = maxAreaM2 / 1_000_000.0,
                        areaLostKm2 = areaLostM2 / 1_000_000.0
                    )

                    // Load territories for the map background & territory navigation
                    try {
                        playerPolygons = fetchPlayerTerritoryPolygons(playerId)
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to load player territories", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to load player profile details", e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(playerId) {
        loadPlayerProfile()
    }

    // Full-screen-width profile view, opened above the current screen
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (playerProfileDetail != null) {
                val detail = playerProfileDetail!!
                PlayerProfileContent(
                    isMe = detail.id == currentUserId,
                    userId = detail.id,
                    userPseudo = detail.pseudonyme,
                    totalDistance = detail.totalDistance,
                    allTimeArea = detail.allTimeAreaM2 / 1_000_000.0,
                    currentArea = detail.currentAreaM2 / 1_000_000.0,
                    userEmpireColor = detail.empireColor,
                    userShareLocation = true,
                    userAvatarUrl = detail.avatarUrl,
                    userBannerUrl = detail.bannerUrl,
                    xp = detail.xp,
                    level = detail.level,
                    loopCount = detail.loopCount,
                    maxLoopDistanceKm = detail.maxLoopDistanceKm,
                    maxAreaKm2 = detail.maxAreaKm2,
                    areaLostKm2 = detail.areaLostKm2,
                    userGuildNom = detail.guildeNom,
                    userGuildCouleur = detail.guildeCouleur,
                    completedPolygons = playerPolygons,
                    onCloseClick = onDismissRequest,
                    onNavigateToTerritory = if (onNavigateToTerritory != null) { point ->
                        onDismissRequest()
                        onNavigateToTerritory(point)
                    } else null
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Impossible de charger le profil.", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

data class ProfileDetails(
    val id: String,
    val pseudonyme: String,
    val avatarUrl: String?,
    val bannerUrl: String? = null,
    val empireColor: String,
    val level: Int,
    val xp: Int,
    val guildeNom: String?,
    val guildeCouleur: String? = null,
    val totalDistance: Double,
    val currentAreaM2: Double,
    val allTimeAreaM2: Double = 0.0,
    val loopCount: Int,
    val maxLoopDistanceKm: Double = 0.0,
    val maxAreaKm2: Double = 0.0,
    val areaLostKm2: Double = 0.0
)
