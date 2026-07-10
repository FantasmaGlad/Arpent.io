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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.ColorWheel
import com.fanta.androidsport.ui.theme.ThemeManager
import com.fanta.androidsport.utils.getPolygonArea
import com.fanta.androidsport.utils.getPolygonCentroid
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotationState
import com.mapbox.maps.extension.compose.style.MapStyle
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
import java.io.File

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
    onStatsUpdated: () -> Unit
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
        onStatsUpdated = onStatsUpdated
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
    onCloseClick: (() -> Unit)? = null
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
    var showStatsSheet by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showAchievementsDialog by remember { mutableStateOf(false) }

    // Active color system
    val activeColor = if (isMe) MaterialTheme.colorScheme.primary else parsedUserColor

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

    var tempPseudo by remember { mutableStateOf(userPseudo) }
    LaunchedEffect(userPseudo) {
        tempPseudo = userPseudo
    }

    // Supabase avatar photo upload picker
    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                        if (bytes != null) {
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
                    }
                    onStatsUpdated()
                    Toast.makeText(context, "Avatar mis à jour !", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("Arpent", "Failed to update avatar", e)
                }
            }
        }
    }

    // MAIN CONTAINER (Black Background)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)) // Pure modern deep black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Custom header with navigation/title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onCloseClick != null) {
                    IconButton(onClick = onCloseClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = if (isMe) "MON ESPACE" else "PROFIL JOUEUR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
                if (isMe) {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. BANNER CARD (White card banner placeholder)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable(enabled = isMe) {
                        // Clicking Banner when isMe allows quick upload trigger
                        imageLauncher.launch("image/*")
                    },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(4.dp, activeColor),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Banière",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = Color.Black,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. PROFILE INFO ROW (Avatar, Pseudo, Guild, Level, and Edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Frame (White background circle with colored border)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, activeColor, CircleShape)
                        .clickable(enabled = isMe) {
                            imageLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarImage(
                        avatarUrl = userAvatarUrl,
                        modifier = Modifier.fillMaxSize(),
                        placeholderColor = activeColor,
                        placeholderIcon = Icons.Default.Person
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Pseudo & Clan
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = userPseudo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = userGuildNom ?: "Sans Guilde",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Level badge
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Niveau $level",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = activeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. DESCRIPTION PILL BUTTON
            Button(
                onClick = {
                    if (isMe) {
                        showEditDescriptionDialog = true
                    } else {
                        Toast.makeText(context, localDescription.ifEmpty { "Ce conquérant n'a pas rédigé de description." }, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = localDescription.ifEmpty { "Description" },
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. SUCCÈS CARD (with Protruding Bottom + Button Extension)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "Succès",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Row of achievements (Conquérant, Randonneur, Explorateur)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            AchievementBadge(
                                name = "Conquérant",
                                icon = Icons.Default.EmojiEvents,
                                color = activeColor,
                                isUnlocked = totalDistance > 0.0 || currentArea > 0.0
                            )
                            AchievementBadge(
                                name = "Randonneur",
                                icon = Icons.Default.DirectionsRun,
                                color = activeColor,
                                isUnlocked = totalDistance >= 5.0
                            )
                            AchievementBadge(
                                name = "Explorateur",
                                icon = Icons.Default.Public,
                                color = activeColor,
                                isUnlocked = loopCount > 0
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Protruding add_circle tab button
                Box(
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .background(Color.White, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clickable { showAchievementsDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Voir succès",
                        tint = activeColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. SOCIAL CARD (ONLY FOR ME)
            if (isMe) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = "Social",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isFriendsLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = activeColor)
                                }
                            } else if (friendsList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun ami pour le moment.",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    friendsList.take(3).forEach { friend ->
                                        val friendThemeColor = remember(friend.empireColor) {
                                            try { Color(android.graphics.Color.parseColor(friend.empireColor)) } catch(_: Exception) { activeColor }
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(containerColor = friendThemeColor.copy(alpha = 0.08f)),
                                            border = BorderStroke(1.dp, friendThemeColor.copy(alpha = 0.15f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AvatarImage(
                                                    avatarUrl = friend.avatarUrl,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .border(1.5.dp, friendThemeColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = friend.pseudo,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color.Black
                                                    )
                                                    if (friend.guildNom != null) {
                                                        Text(
                                                            text = friend.guildNom,
                                                            fontSize = 11.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Lvl ${friend.level}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color.Black.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Options",
                                                    tint = Color.Gray,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clickable {
                                                            Toast.makeText(context, "Ami: ${friend.pseudo}", Toast.LENGTH_SHORT).show()
                                                        }
                                                )
                                            }
                                        }
                                    }
                                    if (friendsList.size > 3) {
                                        Text(
                                            text = "Et ${friendsList.size - 3} autres amis...",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Protruding add_circle tab button
                    Box(
                        modifier = Modifier
                            .offset(y = (-2).dp)
                            .background(Color.White, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clickable { showAddFriendDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Ajouter un ami",
                            tint = activeColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 6. WIDE STATISTIQUES PILL BUTTON (ONLY FOR ME)
            if (isMe) {
                Button(
                    onClick = { showStatsSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                ) {
                    Text(
                        text = "Statistiques",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheet: Settings Panel (Only if isMe)
    if (showSettingsSheet && isMe) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White
        ) {
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
                    color = Color.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Empire Color selector
                Column {
                    ListItem(
                        headlineContent = { Text("Couleur de l'Empire", color = Color.Black, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Personnalisez votre couleur sur la carte", color = Color.Gray) },
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
                                        .border(1.dp, Color.LightGray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (showColorPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color.Gray
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

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Edit Pseudonym
                var pseudoText by remember { mutableStateOf(userPseudo) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Nom d'utilisateur", fontWeight = FontWeight.SemiBold, color = Color.Black, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pseudoText,
                            onValueChange = { pseudoText = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            if (pseudoText.trim().isEmpty()) {
                                Toast.makeText(context, "Pseudo vide", Toast.LENGTH_SHORT).show()
                                return@IconButton
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
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Valider", tint = activeColor)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Theme selection
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Thème visuel", fontWeight = FontWeight.SemiBold, color = Color.Black, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
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
                                    } catch(_: Exception) {}
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Share Location
                ListItem(
                    headlineContent = { Text("Partager ma position (Temps réel)", color = Color.Black, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Permet aux autres joueurs de voir votre position sur la carte", color = Color.Gray) },
                    leadingContent = {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.Gray)
                    },
                    trailingContent = {
                        Switch(
                            checked = shareLocationEnabled,
                            onCheckedChange = { shareLocationEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = activeColor
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Logout button
                ListItem(
                    headlineContent = { Text("Se déconnecter", color = Color.Red, fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Déconnexion", tint = Color.Red)
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

    // Modal Bottom Sheet: Statistiques Panel
    if (showStatsSheet && isMe) {
        ModalBottomSheet(
            onDismissRequest = { showStatsSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MES STATISTIQUES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { showStatsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Performances Sportives
                Text(
                    text = "PERFORMANCES SPORTIVES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "Distance totale",
                        value = "${"%.2f".format(totalDistance)} km",
                        icon = Icons.Default.DirectionsRun,
                        tint = activeColor,
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatCard(
                        title = "Max boucle",
                        value = "${"%.2f".format(maxLoopDistanceKm)} km",
                        icon = Icons.Default.TrendingUp,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Statistiques de Conquête
                Text(
                    text = "STATISTIQUES DE CONQUÊTE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Empire All-Time",
                        value = "${"%.3f".format(allTimeArea)} km²",
                        icon = Icons.Default.Public,
                        tint = activeColor,
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                    StatCard(
                        title = "Empire Actuel",
                        value = "${"%.3f".format(currentArea)} km²",
                        icon = Icons.Default.Map,
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                    StatCard(
                        title = "Boucles fermées",
                        value = "$loopCount",
                        icon = Icons.Default.Loop,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Superficie max",
                        value = "${"%.3f".format(maxAreaKm2)} km²",
                        icon = Icons.Default.PhotoSizeSelectActual,
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                    StatCard(
                        title = "Superficie perdue",
                        value = "${"%.3f".format(areaLostKm2)} km²",
                        icon = Icons.Default.TrendingDown,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                        cardStrokeColor = Color(0xFFE5E7EB)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Map Representation
                Text(
                    text = "EMPIRE CONQUIS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val userLargestPolygon = remember(completedPolygons) {
                    completedPolygons.maxByOrNull { getPolygonArea(it) }
                }
                val userCentroid = remember(userLargestPolygon) {
                    userLargestPolygon?.let { getPolygonCentroid(it) } ?: Point.fromLngLat(2.3522, 48.8566)
                }
                val mapViewportState = rememberMapViewportState {
                    setCameraOptions {
                        center(userCentroid)
                        zoom(13.5)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                ) {
                    if (completedPolygons.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF3F4F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Aucune zone conquise", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        MapboxMap(
                            modifier = Modifier.fillMaxSize(),
                            mapViewportState = mapViewportState,
                            logo = {},
                            attribution = {}
                        ) {
                            MapStyle(style = "mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8")
                            MapEffect(Unit) { mapView ->
                                mapView.gestures.scrollEnabled = false
                                mapView.gestures.pinchToZoomEnabled = false
                                mapView.gestures.doubleTapToZoomInEnabled = false
                                mapView.gestures.doubleTouchToZoomOutEnabled = false
                            }
                            completedPolygons.forEach { polygonPoints ->
                                val polygonState = remember(polygonPoints, activeColor) {
                                    PolygonAnnotationState().apply {
                                        fillColor = activeColor.copy(alpha = 0.35f)
                                        fillOutlineColor = activeColor
                                    }
                                }
                                PolygonAnnotation(
                                    points = listOf(polygonPoints),
                                    polygonAnnotationState = polygonState
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
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
                    Text("Annuler", color = Color.Gray)
                }
            }
        )
    }

    // Dialogue: Achievements details list
    if (showAchievementsDialog) {
        AlertDialog(
            onDismissRequest = { showAchievementsDialog = false },
            title = { Text("Médailles & Accomplissements", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Débloquez des insignes en explorant et courant :", color = Color.Gray, fontSize = 13.sp)

                    ListItem(
                        headlineContent = { Text("Conquérant", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Capturez votre premier territoire ou commencez une course.") },
                        leadingContent = {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (totalDistance > 0.0 || currentArea > 0.0) activeColor else Color.Gray
                            )
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Randonneur", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Parcourez au moins 5 km au total (${"%.2f".format(totalDistance)}/5 km)") },
                        leadingContent = {
                            Icon(
                                Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = if (totalDistance >= 5.0) activeColor else Color.Gray
                            )
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Explorateur", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Fermez au moins 1 boucle complète ($loopCount boucle(s) fermée(s))") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = if (loopCount > 0) activeColor else Color.Gray
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAchievementsDialog = false }) {
                    Text("Fermer", color = activeColor)
                }
            }
        )
    }

    // Dialogue: Add friend search (Social sheet)
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
                                val userColor = try { Color(android.graphics.Color.parseColor(userColorHex)) } catch(_: Exception) { activeColor }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
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
                                    Text(pseudo, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1f))
                                    
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
                                                } catch(e: Exception) {
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
                        Text("Aucun joueur trouvé", color = Color.Gray, fontSize = 13.sp)
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

@Composable
fun AchievementBadge(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isUnlocked: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isUnlocked) color else Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (isUnlocked) Color.White else Color.LightGray,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.Black
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileDialog(
    playerId: String,
    currentUserId: String,
    onDismissRequest: () -> Unit
) {
    var playerProfileDetail by remember(playerId) { mutableStateOf<ProfileDetails?>(null) }
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
                    val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                    val lvl = obj["level"]?.jsonPrimitive?.intOrNull ?: 1
                    val xp = obj["xp"]?.jsonPrimitive?.intOrNull ?: 0
                    val dist = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val loop = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                    val area = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    
                    val gId = obj["guilde_id"]?.jsonPrimitive?.contentOrNull
                    var gNom: String? = null
                    if (gId != null) {
                        try {
                            val gRes = supabase.postgrest["guildes"].select {
                                filter { eq("id", gId) }
                            }
                            val gArray = Json.parseToJsonElement(gRes.data) as? JsonArray
                            val gObj = gArray?.firstOrNull() as? JsonObject
                            gNom = gObj?.get("nom")?.jsonPrimitive?.contentOrNull
                        } catch(_: Exception) {}
                    }
                    
                    playerProfileDetail = ProfileDetails(
                        id = id,
                        pseudonyme = pseudo,
                        avatarUrl = avatar,
                        empireColor = color,
                        level = lvl,
                        xp = xp,
                        guildeNom = gNom,
                        totalDistance = dist,
                        currentAreaM2 = area,
                        loopCount = loop
                    )
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

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0C0C0C)
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
                    allTimeArea = detail.currentAreaM2 / 1_000_000.0,
                    currentArea = detail.currentAreaM2 / 1_000_000.0,
                    userEmpireColor = detail.empireColor,
                    userShareLocation = true,
                    userAvatarUrl = detail.avatarUrl,
                    xp = detail.xp,
                    level = detail.level,
                    loopCount = detail.loopCount,
                    userGuildNom = detail.guildeNom,
                    onCloseClick = onDismissRequest
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Impossible de charger le profil.", color = Color.White)
                }
            }
        }
    }
}

data class ProfileDetails(
    val id: String,
    val pseudonyme: String,
    val avatarUrl: String?,
    val empireColor: String,
    val level: Int,
    val xp: Int,
    val guildeNom: String?,
    val totalDistance: Double,
    val currentAreaM2: Double,
    val loopCount: Int
)

private fun getCumulativeXpForLevel(level: Int): Int {
    if (level <= 1) return 0
    var sum = 0.0
    var currentStep = 100.0
    for (i in 1 until level) {
        sum += currentStep
        currentStep *= 1.15
    }
    return sum.toInt()
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    cardStrokeColor: Color = Color(0xFFE5E7EB)
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, cardStrokeColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

