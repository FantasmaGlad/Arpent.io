package com.fanta.androidsport.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

@Composable
fun ProfileScreen(
    userId: String,
    userPseudo: String,
    userTag: String?,
    totalDistance: Double,
    allTimeArea: Double,
    currentArea: Double,
    maxArea: Double,
    areaLost: Double,
    xp: Int,
    level: Int,
    loopCount: Int,
    maxLoopDistanceKm: Double,
    ghostMode: Boolean,
    streak: Int,
    userEmpireColor: String,
    userShareLocation: Boolean,
    userAvatarUrl: String?,
    userGuildNom: String?,
    userGuildCouleur: String?,
    onStatsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val parsedUserColor = remember(userEmpireColor) {
        try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            ElectricBlue
        }
    }

    var localColor by remember(userEmpireColor) {
        val parsed = try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            ElectricBlue
        }
        mutableStateOf(parsed)
    }

    var shareLocationEnabled by remember(userShareLocation) {
        mutableStateOf(userShareLocation)
    }

    var ghostModeEnabled by remember(ghostMode) {
        mutableStateOf(ghostMode)
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Aperçu, 1: Conquêtes, 2: Activités

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

    LaunchedEffect(ghostModeEnabled) {
        if (ghostModeEnabled == ghostMode) return@LaunchedEffect
        delay(300)
        try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["profiles"].update(
                    mapOf("ghost_mode" to ghostModeEnabled)
                ) {
                    filter { eq("id", userId) }
                }
            }
            onStatsUpdated()
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to update ghost_mode", e)
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

    var isEditingPseudo by remember { mutableStateOf(false) }
    var tempPseudo by remember { mutableStateOf(userPseudo) }
    var tempTag by remember { mutableStateOf(userTag ?: "") }

    LaunchedEffect(userPseudo, userTag) {
        tempPseudo = userPseudo
        tempTag = userTag ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Avatar Section
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
                .size(100.dp)
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
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                AvatarImage(
                    avatarUrl = userAvatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    placeholderColor = parsedUserColor,
                    placeholderIcon = Icons.Default.Person
                )
            }
            
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Importer photo",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile Identity (Pseudo, Tag, Guild)
        if (isEditingPseudo) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Modifier le profil",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempPseudo,
                        onValueChange = { tempPseudo = it },
                        label = { Text("Pseudo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            focusedLabelColor = ElectricBlue
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempTag,
                        onValueChange = { if (it.length <= 4) tempTag = it },
                        label = { Text("Tag de joueur / clan (max 4 caractères)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            focusedLabelColor = ElectricBlue
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                tempPseudo = userPseudo
                                tempTag = userTag ?: ""
                                isEditingPseudo = false
                            }
                        ) {
                            Text("Annuler", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (tempPseudo.trim().isEmpty()) {
                                    Toast.makeText(context, "Le pseudo ne peut pas être vide", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val cleanTag = tempTag.trim().uppercase()
                                            supabase.postgrest["profiles"].update(
                                                mapOf(
                                                    "pseudonyme" to tempPseudo.trim(),
                                                    "tag" to if (cleanTag.isEmpty()) null else cleanTag
                                                )
                                            ) {
                                                filter { eq("id", userId) }
                                            }
                                        }
                                        isEditingPseudo = false
                                        onStatsUpdated()
                                        Toast.makeText(context, "Profil mis à jour !", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Sauvegarder", color = ElectricBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = userPseudo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!userTag.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(parsedUserColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = userTag,
                                color = parsedUserColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { isEditingPseudo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifier le pseudo",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!userGuildNom.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val gColor = remember(userGuildCouleur) {
                        try {
                            Color(android.graphics.Color.parseColor(userGuildCouleur))
                        } catch (e: Exception) {
                            Color.Gray
                        }
                    }
                    Text(
                        text = "Clan: $userGuildNom",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = gColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom TabRow mimicking Strava's clean tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("Aperçu", "Conquêtes", "Activités")
            tabs.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Contents
        when (selectedTab) {
            0 -> { // Aperçu (Overview)
                // 1. XP / Level Progress Bar (calculated precisely with formula level = FLOOR(SQRT(xp / 250.0)) + 1)
                val calculatedLevel = remember(xp) {
                    (Math.floor(Math.sqrt(xp / 250.0)) + 1).toInt()
                }
                val minXpForLevel = (250 * (calculatedLevel - 1) * (calculatedLevel - 1))
                val maxXpForNextLevel = (250 * calculatedLevel * calculatedLevel)
                val xpInCurrentLevel = xp - minXpForLevel
                val xpNeededForNextLevel = maxXpForNextLevel - minXpForLevel
                val progress = if (xpNeededForNextLevel > 0) xpInCurrentLevel.toFloat() / xpNeededForNextLevel.toFloat() else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Niveau $calculatedLevel",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                            Text(
                                text = "$xp / $maxXpForNextLevel XP",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ElectricBlue,
                            trackColor = Color.Black.copy(alpha = 0.05f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Encore ${maxXpForNextLevel - xp} XP pour le niveau ${calculatedLevel + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Streak Badge (Flame icon or emoji representation)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🔥", fontSize = 28.sp)
                        Column {
                            Text(
                                text = if (streak > 0) "$streak jours d'affilée" else "Pas de série active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Black
                            )
                            Text(
                                text = if (streak > 0) "Continuez à courir et capturer pour maintenir votre série !" else "Courez aujourd'hui pour démarrer une série de conquêtes !",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Empire Custom Color Picker
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Couleur de votre Empire",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(localColor)
                                    .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ColorWheel(
                            selectedColor = localColor,
                            onColorSelected = { localColor = it },
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Settings (Ghost Mode / Share Location)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Mode Fantôme", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Votre position n'apparaît plus sur la carte publique") },
                            trailingContent = {
                                Switch(
                                    checked = ghostModeEnabled,
                                    onCheckedChange = { ghostModeEnabled = it }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black.copy(alpha = 0.05f))
                        ListItem(
                            headlineContent = { Text("Partager ma position (Temps réel)", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Permet de voir votre position en direct si le mode fantôme est désactivé") },
                            trailingContent = {
                                Switch(
                                    checked = shareLocationEnabled,
                                    onCheckedChange = { shareLocationEnabled = it }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Black.copy(alpha = 0.05f))
                        ListItem(
                            headlineContent = { Text("Se déconnecter", color = Color(0xFFC62828), fontWeight = FontWeight.Bold) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Déconnexion",
                                    tint = Color(0xFFC62828)
                                )
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
            }

            1 -> { // Conquêtes (Conquests)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Statistiques de Territoire",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Grid Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Territoire Actuel", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (currentArea < 0.01) "${"%.1f".format(currentArea * 1_000_000)} m²" else "${"%.3f".format(currentArea)} km²",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Record Historique", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (maxArea < 0.01) "${"%.1f".format(maxArea * 1_000_000)} m²" else "${"%.3f".format(maxArea)} km²",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats Grid Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Territoire Perdu", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (areaLost < 0.01) "${"%.1f".format(areaLost * 1_000_000)} m²" else "${"%.3f".format(areaLost)} km²",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Territoire Total Conquis", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (allTimeArea < 0.01) "${"%.1f".format(allTimeArea * 1_000_000)} m²" else "${"%.3f".format(allTimeArea)} km²",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> { // Activités (Activities / Loops)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Statistiques de Course",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Distance Totale", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${"%.2f".format(totalDistance)} km",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Boucles Complétées", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$loopCount",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Plus Longue Boucle", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${"%.2f".format(maxLoopDistanceKm)} km",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "💡 Vos activités physiques complètes sont répertoriées dans l'onglet des Courses du menu principal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
