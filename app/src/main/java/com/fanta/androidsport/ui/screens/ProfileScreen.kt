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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.io.File

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var selectedTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(shareLocationEnabled) {
        if (shareLocationEnabled == userShareLocation) return@LaunchedEffect
        delay(300) // debounce update
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

        delay(500) // debounce updates to database
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

    var isEditingPseudo by remember { mutableStateOf(false) }
    var tempPseudo by remember { mutableStateOf(userPseudo) }

    LaunchedEffect(userPseudo) {
        tempPseudo = userPseudo
    }

    // Light Theme Colors
    val lightBg = Color(0xFFF9FAFB)
    val textPrimary = Color(0xFF111827)
    val textSecondary = Color(0xFF4B5563)
    val textMuted = Color(0xFF9CA3AF)
    val cardStrokeColor = Color(0xFFE5E7EB)

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
                } catch (e: Exception) {
                    android.util.Log.e("Arpent", "Failed to update avatar", e)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Title & Settings Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MON ESPACE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = textPrimary,
                    letterSpacing = 1.5.sp
                )
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Paramètres",
                        tint = textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Tab Navigation Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = textPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = parsedUserColor
                    )
                },
                divider = {
                    HorizontalDivider(color = cardStrokeColor)
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "Profil",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    selectedContentColor = parsedUserColor,
                    unselectedContentColor = textSecondary
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "Statistiques",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    },
                    selectedContentColor = parsedUserColor,
                    unselectedContentColor = textSecondary
                )
            }

            // Tab Content Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (selectedTabIndex == 0) {
                    // TAB 1: PROFIL
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(parsedUserColor)
                                .clickable {
                                    imageLauncher.launch("image/*")
                                }
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarImage(
                                    avatarUrl = userAvatarUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholderColor = parsedUserColor,
                                    placeholderIcon = Icons.Default.Person
                                )
                            }
                            
                            // Edit pencil overlay
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Importer photo",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pseudo
                        if (isEditingPseudo) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                OutlinedTextField(
                                    value = tempPseudo,
                                    onValueChange = { tempPseudo = it },
                                    label = { Text("Modifier le pseudo", color = textSecondary) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Black,
                                        focusedLabelColor = Color.Black,
                                        focusedTextColor = textPrimary,
                                        unfocusedTextColor = textPrimary,
                                        unfocusedBorderColor = cardStrokeColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (tempPseudo.trim().isEmpty()) {
                                            Toast.makeText(context, "Le pseudo ne peut pas être vide", Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    supabase.postgrest["profiles"].update(
                                                        mapOf("pseudonyme" to tempPseudo.trim())
                                                    ) {
                                                        filter { eq("id", userId) }
                                                    }
                                                }
                                                isEditingPseudo = false
                                                onStatsUpdated()
                                                Toast.makeText(context, "Pseudo mis à jour !", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Confirmer", tint = Color(0xFF10B981))
                                }
                                IconButton(
                                    onClick = {
                                        tempPseudo = userPseudo
                                        isEditingPseudo = false
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Annuler", tint = Color.Red)
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = userPseudo,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { isEditingPseudo = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Modifier le pseudo",
                                        tint = textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Explorateur Actif",
                            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.sp),
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Streak and Guild row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Streak widget
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, cardStrokeColor),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Série active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (userStreak > 0) "$userStreak jours" else "0 jour",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = textPrimary
                                        )
                                    }
                                }
                            }

                            // Guild widget
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, cardStrokeColor),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                val parsedGuildColor = remember(userGuildCouleur) {
                                    if (userGuildCouleur != null) {
                                        try { Color(android.graphics.Color.parseColor(userGuildCouleur)) } catch (_: Exception) { parsedUserColor }
                                    } else parsedUserColor
                                }
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = if (userGuildNom != null) parsedGuildColor else Color.Gray,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Guilde",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = userGuildNom ?: "Aucune",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (userGuildNom != null) textPrimary else textSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Level & XP Bar Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, cardStrokeColor),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = parsedUserColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Niveau $level",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = textPrimary
                                        )
                                    }
                                    Text(
                                        text = "$xp / 100 XP",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { xp.toFloat() / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = parsedUserColor,
                                    trackColor = Color(0xFFF3F4F6)
                                )
                            }
                        }

                        // Theme Selection Section
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, cardStrokeColor),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = parsedUserColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Thème de l'application",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = textPrimary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val themes = listOf(
                                    Triple("forest", "Forêt (Classique)", listOf(Color(0xFFFFFFFF), Color(0xFF36454F), Color(0xFF253D2C), Color(0xFFCFFFDC))),
                                    Triple("orchid", "Orchidée", listOf(Color(0xFFFFFFFF), Color(0xFF4F2B4E), Color(0xFFC96DC6), Color(0xFFED80E9))),
                                    Triple("blue_sky", "Bleu ciel", listOf(Color(0xFFFFFFFF), Color(0xFF162A33), Color(0xFF345766), Color(0xFF82C8E5)))
                                )
                                
                                val currentTheme = ThemeManager.themeState.value
                                
                                themes.forEach { (themeId, themeName, colors) ->
                                    val isSelected = currentTheme == themeId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) parsedUserColor.copy(alpha = 0.08f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) parsedUserColor else cardStrokeColor,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                ThemeManager.themeState.value = themeId
                                                val prefs = context.getSharedPreferences("arpent_prefs", android.content.Context.MODE_PRIVATE)
                                                prefs.edit().putString("app_theme", themeId).apply()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    ThemeManager.themeState.value = themeId
                                                    val prefs = context.getSharedPreferences("arpent_prefs", android.content.Context.MODE_PRIVATE)
                                                    prefs.edit().putString("app_theme", themeId).apply()
                                                },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = parsedUserColor,
                                                    unselectedColor = textSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = themeName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = textPrimary
                                            )
                                        }
                                        
                                        // Color dots representing the theme palette
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            colors.forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(1.dp, Color.LightGray, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: STATISTIQUES
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Section 1: Sports
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "PERFORMANCES SPORTIVES",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                title = "Distance totale",
                                value = "${"%.2f".format(totalDistance)} km",
                                icon = Icons.Default.DirectionsRun,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                            // We can use space/placeholder to occupy the right if single card
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Section 2: Conquest
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "STATISTIQUES DE CONQUÊTE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.sp
                            )
                        }

                        // 3 stat cards grid row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Empire All-Time",
                                value = "${"%.3f".format(allTimeArea)} km²",
                                icon = Icons.Default.Public,
                                tint = parsedUserColor,
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                            StatCard(
                                title = "Empire Actuel",
                                value = "${"%.3f".format(currentArea)} km²",
                                icon = Icons.Default.Map,
                                tint = Color(0xFF4B5563),
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                            StatCard(
                                title = "Boucles fermées",
                                value = "$loopCount",
                                icon = Icons.Default.Loop,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3 stat cards grid row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Max boucle",
                                value = "${"%.2f".format(maxLoopDistanceKm)} km",
                                icon = Icons.Default.TrendingUp,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                            StatCard(
                                title = "Superficie max",
                                value = "${"%.3f".format(maxAreaKm2)} km²",
                                icon = Icons.Default.PhotoSizeSelectActual,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                            StatCard(
                                title = "Superficie perdue",
                                value = "${"%.3f".format(areaLostKm2)} km²",
                                icon = Icons.Default.TrendingDown,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f),
                                cardStrokeColor = cardStrokeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Section 3: Map representation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "EMPIRE CONQUIS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.sp
                            )
                        }

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

                        LaunchedEffect(userCentroid) {
                            mapViewportState.setCameraOptions {
                                center(userCentroid)
                                zoom(13.5)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, cardStrokeColor, RoundedCornerShape(16.dp))
                        ) {
                            if (completedPolygons.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFF3F4F6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucune zone conquise pour le moment",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
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
                                        mapView.gestures.quickZoomEnabled = false
                                        mapView.gestures.pitchEnabled = false
                                        mapView.gestures.rotateEnabled = false
                                    }

                                    completedPolygons.forEach { polygonPoints ->
                                        val polygonState = remember(polygonPoints, parsedUserColor) {
                                            PolygonAnnotationState().apply {
                                                fillColor = parsedUserColor.copy(alpha = 0.35f)
                                                fillOutlineColor = parsedUserColor
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
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Settings Panel
    if (showSettingsSheet) {
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
                    color = textPrimary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = cardStrokeColor)

                // 1. Empire Color ListItem
                Column {
                    ListItem(
                        headlineContent = { Text("Couleur de l'Empire", color = textPrimary, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Personnalisez votre couleur sur la carte", color = textSecondary) },
                        leadingContent = {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = parsedUserColor)
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
                                    tint = textMuted
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

                HorizontalDivider(color = cardStrokeColor)

                // 2. Share Location ListItem
                ListItem(
                    headlineContent = { Text("Partager ma position (Temps réel)", color = textPrimary, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Permet aux autres joueurs de voir votre position sur la carte", color = textSecondary) },
                    leadingContent = {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = textSecondary)
                    },
                    trailingContent = {
                        Switch(
                            checked = shareLocationEnabled,
                            onCheckedChange = { shareLocationEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Black,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = cardStrokeColor)

                // 3. Notifications ListItem
                ListItem(
                    headlineContent = { Text("Notifications de capture", color = textPrimary, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Alertes en cas de capture de vos zones", color = textSecondary) },
                    leadingContent = {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = textSecondary)
                    },
                    trailingContent = {
                        var checked by remember { mutableStateOf(true) }
                        Switch(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Black,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = cardStrokeColor)

                // 4. Log Out ListItem
                ListItem(
                    headlineContent = { Text("Se déconnecter", color = Color.Red, fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Déconnexion", tint = Color.Red)
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            try {
                                supabase.auth.signOut()
                                try {
                                    val file = File(context.filesDir, "local_territories.json")
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                } catch (ex: Exception) {
                                    android.util.Log.e("Arpent", "Failed to delete local cache on signout", ex)
                                }
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
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    cardStrokeColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cardStrokeColor),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center
            )
        }
    }
}

