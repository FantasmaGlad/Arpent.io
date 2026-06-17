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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.ColorWheel
import com.fanta.androidsport.ui.theme.ActiveOrange
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.NeonVolt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
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
    onStatsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val parsedUserColor = remember(userEmpireColor) {
        try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            NeonVolt
        }
    }

    var localColor by remember(userEmpireColor) {
        val parsed = try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            NeonVolt
        }
        mutableStateOf(parsed)
    }

    var shareLocationEnabled by remember(userShareLocation) {
        mutableStateOf(userShareLocation)
    }

    var showColorPicker by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Premium Avatar Card with photo import launcher
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
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(ElectricBlue, parsedUserColor, ActiveOrange, ElectricBlue)
                        )
                    )
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

            Spacer(modifier = Modifier.height(24.dp))

            // Level & XP bar
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

            Spacer(modifier = Modifier.height(20.dp))

            // Title: Statistiques
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "STATISTIQUES GÉNÉRALES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Primary Stats Grid (3 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Distance",
                    value = "${"%.2f".format(totalDistance)} km",
                    icon = Icons.Default.DirectionsRun,
                    tint = ElectricBlue,
                    modifier = Modifier.weight(1f),
                    cardStrokeColor = cardStrokeColor
                )
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
                    tint = ActiveOrange,
                    modifier = Modifier.weight(1f),
                    cardStrokeColor = cardStrokeColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Enrichment metrics Grid (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Boucles fermées",
                    value = "$loopCount",
                    icon = Icons.Default.Loop,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                    cardStrokeColor = cardStrokeColor
                )
                StatCard(
                    title = "Max boucle",
                    value = "${"%.2f".format(maxLoopDistanceKm)} km",
                    icon = Icons.Default.TrendingUp,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                    cardStrokeColor = cardStrokeColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

            Spacer(modifier = Modifier.height(28.dp))

            // Title: Paramètres
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "PARAMÈTRES DE L'APPLICATION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Settings Option Card List (Clean Light-themed Settings Group)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardStrokeColor),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    // Empire Color Picker Link
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardStrokeColor)
                    
                    // Share Location Switch
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardStrokeColor)

                    // Capture Notification Switch
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardStrokeColor)

                    // Log Out Action Link
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
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
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
