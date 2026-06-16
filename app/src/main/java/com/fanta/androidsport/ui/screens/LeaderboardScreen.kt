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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.data.model.LeaderboardClan
import com.fanta.androidsport.data.model.LeaderboardPlayer
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.CapsuleTabSelector
import com.fanta.androidsport.ui.theme.NeonVolt
import com.mapbox.geojson.Point
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun LeaderboardScreen(
    isActive: Boolean = false,
    userId: String,
    userGuildId: String? = null,
    onPlayerClick: (Point) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var players by remember { mutableStateOf<List<LeaderboardPlayer>>(emptyList()) }
    var clans by remember { mutableStateOf<List<LeaderboardClan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Joueurs, 1 = Clans

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        
        // Show loader only if we have no cached data yet
        if (players.isEmpty() && clans.isEmpty()) {
            isLoading = true
        }
        
        try {
            // Fetch players
            val response = withContext(Dispatchers.IO) {
                supabase.postgrest["leaderboard"].select {
                    order("total_area_m2", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
            }
            val fetchedPlayers = withContext(Dispatchers.Default) {
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur_${id.take(8)}"
                    val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                    val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                    val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val gNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                    val gColor = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                    LeaderboardPlayer(id, pseudo, tag, color, lat, lon, areaM2, avatar, gNom, gColor)
                } ?: emptyList()
            }
            
            // Fetch clans
            val clanResponse = withContext(Dispatchers.IO) {
                supabase.postgrest["clan_leaderboard"].select {
                    order("total_area_m2", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
            }
            val fetchedClans = withContext(Dispatchers.Default) {
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(clanResponse.data) as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: "Clan"
                    val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                    val color = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#CCFF00"
                    val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val membreCount = obj["membre_count"]?.jsonPrimitive?.intOrNull ?: 0
                    LeaderboardClan(id, nom, tag, color, avatarUrl, areaM2, membreCount)
                } ?: emptyList()
            }
            
            players = fetchedPlayers
            clans = fetchedClans
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to fetch leaderboard", e)
        } finally {
            isLoading = false
        }
    }

    val leaderboardColorScheme = lightColorScheme(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.9f),
        onSurface = Color.Black,
        surfaceVariant = Color.White.copy(alpha = 0.95f),
        onSurfaceVariant = Color.Black,
        secondaryContainer = Color(0xFFE3F2FD).copy(alpha = 0.9f),
        onSecondaryContainer = Color.Black
    )

    MaterialTheme(colorScheme = leaderboardColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(16.dp)
        ) {
            // Capsule Tab Selector
            CapsuleTabSelector(
                tabs = listOf("JOUEURS", "CLANS"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonVolt)
                }
            } else if (selectedTab == 0) {
                // JOUEURS LEADERBOARD
                if (players.isEmpty()) {
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
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucun joueur enregistré",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                } else {
                    // Afficher le rang de l'utilisateur connecté s'il est présent
                    val userIndex = players.indexOfFirst { it.id == userId }
                    if (userIndex != -1) {
                        val me = players[userIndex]
                        val suffix = if (userIndex == 0) "er" else "ème"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            border = BorderStroke(1.5.dp, NeonVolt)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val parsedColor = remember(me.empireColor) {
                                    try { Color(android.graphics.Color.parseColor(me.empireColor)) } catch (e: Exception) { NeonVolt }
                                }
                                AvatarImage(
                                    avatarUrl = me.avatarUrl,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, parsedColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = me.pseudonyme,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        if (me.tag != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "#${me.tag}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Black.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    val areaStr = "%.3f"
                                        .format(me.totalAreaM2 / 1_000_000.0) + " km²"
                                    Text(
                                        text = areaStr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF0F1318))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${userIndex + 1}$suffix",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = NeonVolt
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Text(
                        text = "CLASSEMENT DES CONQUÉRANTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = Color.Black.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(players) { index, player ->
                            val isMe = player.id == userId
                            val cardBg = if (isMe) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            }
                            val parsedColor = remember(player.empireColor) {
                                try { Color(android.graphics.Color.parseColor(player.empireColor)) } catch (e: Exception) { NeonVolt }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (player.latitude != null && player.longitude != null) {
                                            onPlayerClick(Point.fromLngLat(player.longitude, player.latitude))
                                        } else {
                                            Toast.makeText(context, "${player.pseudonyme} n'a pas de position sur la carte", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = if (isMe) BorderStroke(1.5.dp, NeonVolt) else BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val rank = index + 1
                                        val isTop3 = rank <= 3
                                        val rankColor = when (rank) {
                                            1 -> NeonVolt
                                            2 -> Color.White
                                            3 -> Color.White
                                            else -> Color.Black.copy(alpha = 0.6f)
                                        }
                                        val circleBg = if (isTop3) Color(0xFF0F1318) else Color.Black.copy(alpha = 0.05f)
                                        val circleText = if (isTop3) rankColor else Color.Black.copy(alpha = 0.8f)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(circleBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$rank",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = circleText
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AvatarImage(
                                            avatarUrl = player.avatarUrl,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, parsedColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = player.pseudonyme,
                                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = Color.Black
                                                )
                                            }
                                            if (player.guildeNom != null) {
                                                val gColor = try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { Color.Gray }
                                                Text(
                                                    text = player.guildeNom,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = gColor
                                                )
                                            }
                                        }
                                    }
                                    val areaStr = "%.3f"
                                        .format(player.totalAreaM2 / 1_000_000.0) + " km²"
                                    Text(
                                        text = areaStr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // CLANS LEADERBOARD
                if (clans.isEmpty()) {
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
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucun clan enregistré",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                } else {
                    // User's Clan rank card
                    if (userGuildId != null) {
                        val myClanIndex = clans.indexOfFirst { it.id == userGuildId }
                        if (myClanIndex != -1) {
                            val myClan = clans[myClanIndex]
                            val suffix = if (myClanIndex == 0) "er" else "ème"
                            val parsedClanColor = remember(myClan.couleurHex) {
                                try { Color(android.graphics.Color.parseColor(myClan.couleurHex)) } catch (_: Exception) { NeonVolt }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.95f)
                                ),
                                border = BorderStroke(1.5.dp, NeonVolt)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = myClan.avatarUrl,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, parsedClanColor, CircleShape),
                                        placeholderColor = parsedClanColor,
                                        placeholderIcon = Icons.Default.Shield
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = myClan.nom,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                            if (myClan.tag != null) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "#${myClan.tag}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Black.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                        val areaStr = "%.3f"
                                            .format(myClan.totalAreaM2 / 1_000_000.0) + " km²"
                                        Text(
                                            text = areaStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Black.copy(alpha = 0.6f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF0F1318))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${myClanIndex + 1}$suffix",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = NeonVolt
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    Text(
                        text = "CLASSEMENT DES CLANS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = Color.Black.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(clans) { index, clan ->
                            val isMyClan = clan.id == userGuildId
                            val cardBg = if (isMyClan) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            }
                            val parsedClanColor = remember(clan.couleurHex) {
                                try { Color(android.graphics.Color.parseColor(clan.couleurHex)) } catch (_: Exception) { NeonVolt }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = if (isMyClan) BorderStroke(1.5.dp, NeonVolt) else BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val rank = index + 1
                                        val isTop3 = rank <= 3
                                        val rankColor = when (rank) {
                                            1 -> NeonVolt
                                            2 -> Color.White
                                            3 -> Color.White
                                            else -> Color.Black.copy(alpha = 0.6f)
                                        }
                                        val circleBg = if (isTop3) Color(0xFF0F1318) else Color.Black.copy(alpha = 0.05f)
                                        val circleText = if (isTop3) rankColor else Color.Black.copy(alpha = 0.8f)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(circleBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$rank",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = circleText
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AvatarImage(
                                            avatarUrl = clan.avatarUrl,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, parsedClanColor, CircleShape),
                                            placeholderColor = parsedClanColor,
                                            placeholderIcon = Icons.Default.Shield
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = clan.nom,
                                                    fontWeight = if (isMyClan) FontWeight.Bold else FontWeight.Medium,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = Color.Black
                                                )
                                                if (clan.tag != null) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "#${clan.tag}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Black.copy(alpha = 0.5f)
                                                    )
                                                }
                                                if (isMyClan) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Color(0xFF0F1318))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Votre Clan", color = NeonVolt, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${clan.membreCount} membre(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    val areaStr = "%.3f"
                                        .format(clan.totalAreaM2 / 1_000_000.0) + " km²"
                                    Text(
                                        text = areaStr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
