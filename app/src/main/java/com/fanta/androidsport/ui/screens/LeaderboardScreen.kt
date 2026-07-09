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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.data.model.LeaderboardClan
import com.fanta.androidsport.data.model.LeaderboardPlayer
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.CapsuleTabSelector
import com.fanta.androidsport.ui.viewmodel.LeaderboardViewModel
import androidx.compose.runtime.collectAsState
import com.mapbox.geojson.Point
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import androidx.compose.material3.HorizontalDivider

enum class SocialFilter { GLOBAL, AMIS, LOCAL }
enum class MetricFilter { TERRITOIRE, DISTANCE, BOUCLES }

fun formatLeaderboardArea(areaM2: Double): String {
    return if (areaM2 < 10000.0) {
        "%,.0f m²".format(areaM2).replace(",", " ")
    } else {
        "%.1f ha".format(areaM2 / 10000.0)
    }
}

fun formatLeaderboardDistance(distanceMeters: Double): String {
    return if (distanceMeters < 1000.0) {
        "%,.0f m".format(distanceMeters).replace(",", " ")
    } else {
        "%.1f km".format(distanceMeters / 1000.0)
    }
}

fun formatLeaderboardLoops(loops: Int): String {
    return if (loops <= 1) {
        "$loops boucle"
    } else {
        "$loops boucles"
    }
}

@Composable
fun PlayerRow(
    rank: Int,
    player: LeaderboardPlayer,
    isMe: Boolean,
    metric: MetricFilter,
    friendsStatusMap: Map<String, String>,
    onClick: () -> Unit
) {
    val parsedColor = remember(player.empireColor) {
        try { Color(android.graphics.Color.parseColor(player.empireColor)) } catch (e: Exception) { Color(0xFF00875A) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isMe) Color(0xFF00875A).copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when (rank) {
                    1 -> Text("🥇", fontSize = 18.sp)
                    2 -> Text("🥈", fontSize = 18.sp)
                    3 -> Text("🥉", fontSize = 18.sp)
                    else -> Text(
                        text = "$rank",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
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
                        text = player.pseudonyme + if (isMe) " (Moi)" else "",
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1E1E1E)
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
        val valStr = when (metric) {
            MetricFilter.TERRITOIRE -> formatLeaderboardArea(player.totalAreaM2)
            MetricFilter.DISTANCE -> formatLeaderboardDistance(player.distanceTotale)
            MetricFilter.BOUCLES -> formatLeaderboardLoops(player.loopCount)
        }
        Text(
            text = valStr,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF1E1E1E)
        )
    }
}

@Composable
fun ClanRow(
    rank: Int,
    clan: LeaderboardClan,
    isMyClan: Boolean,
    metric: MetricFilter,
    onClick: () -> Unit
) {
    val parsedClanColor = remember(clan.couleurHex) {
        try { Color(android.graphics.Color.parseColor(clan.couleurHex)) } catch (_: Exception) { Color(0xFF00875A) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isMyClan) Color(0xFF00875A).copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when (rank) {
                    1 -> Text("🥇", fontSize = 18.sp)
                    2 -> Text("🥈", fontSize = 18.sp)
                    3 -> Text("🥉", fontSize = 18.sp)
                    else -> Text(
                        text = "$rank",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
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
                        text = clan.nom + if (isMyClan) " (Votre Clan)" else "",
                        fontWeight = if (isMyClan) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1E1E1E)
                    )
                }
                Text(
                    text = "${clan.membreCount} membre(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6E6E73)
                )
            }
        }
        val valStr = when (metric) {
            MetricFilter.TERRITOIRE -> formatLeaderboardArea(clan.totalAreaM2)
            MetricFilter.DISTANCE -> formatLeaderboardDistance(clan.distanceTotale)
            MetricFilter.BOUCLES -> formatLeaderboardLoops(clan.loopCount)
        }
        Text(
            text = valStr,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF1E1E1E)
        )
    }
}

@Composable
fun LeaderboardScreen(
    isActive: Boolean = false,
    userId: String,
    userGuildId: String? = null,
    viewModel: LeaderboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onPlayerClick: (Point) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val players by viewModel.playersList.collectAsState()
    val clans by viewModel.clansList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val selectedSocialFilter by viewModel.selectedSocialFilter.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val userLatState by viewModel.userLat.collectAsState()

    val friendsStatusMap by viewModel.friendsStatusMap.collectAsState()
    val sentGuildInvitationsSet by viewModel.sentGuildInvitationsSet.collectAsState()
    var selectedPlayerForProfile by remember { mutableStateOf<LeaderboardPlayer?>(null) }

    LaunchedEffect(userId, userGuildId) {
        viewModel.init(userId, userGuildId)
    }

    // When the screen becomes active or visible, refresh the data.
    // It will show the cached/warmed data immediately and load updates silently in the background.
    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.loadLeaderboardData(forceRefresh = true)
        }
    }

    fun sendFriendRequest(otherUserId: String) {
        viewModel.sendFriendRequest(otherUserId) { success ->
            if (success) {
                Toast.makeText(context, "Demande d'ami envoyée.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun inviteToGuild(otherUserId: String) {
        if (userGuildId == null) return
        viewModel.inviteToGuild(otherUserId) { success ->
            if (success) {
                Toast.makeText(context, "Invitation au clan envoyée.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val leaderboardColorScheme = lightColorScheme(
        primary = Color(0xFF00875A),
        background = Color(0xFFF4F5F7),
        surface = Color.White,
        onSurface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFFE9EBEF),
        onSurfaceVariant = Color(0xFF6E6E73)
    )

    val meIndex = players.indexOfFirst { it.id == userId }
    val showStickyPlayer = selectedTab == 0 && meIndex != -1 && meIndex >= 8

    val myClanIndex = if (userGuildId != null) clans.indexOfFirst { it.id == userGuildId } else -1
    val showStickyClan = selectedTab == 1 && myClanIndex != -1 && myClanIndex >= 8

    val showSticky = showStickyPlayer || showStickyClan

    MaterialTheme(colorScheme = leaderboardColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F5F7))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .padding(bottom = if (showSticky) 76.dp else 0.dp)
            ) {
                CapsuleTabSelector(
                    tabs = listOf("JOUEURS", "CLANS"),
                    selectedTabIndex = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Level 2 filters (Social)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SocialFilter.values().forEach { filter ->
                        val isSelected = selectedSocialFilter == filter
                        val text = when (filter) {
                            SocialFilter.GLOBAL -> "Global"
                            SocialFilter.AMIS -> "Amis"
                            SocialFilter.LOCAL -> "Local"
                        }
                        val bgColor = if (isSelected) Color(0xFF00875A) else Color(0xFFE9EBEF)
                        val contentColor = if (isSelected) Color.White else Color(0xFF6E6E73)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgColor)
                                .clickable { viewModel.selectSocialFilter(filter) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = text,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Level 3 filters (Metric)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricFilter.values().forEach { metric ->
                        val isSelected = selectedMetric == metric
                        val text = when (metric) {
                            MetricFilter.TERRITOIRE -> "Territoire"
                            MetricFilter.DISTANCE -> "Distance"
                            MetricFilter.BOUCLES -> "Boucles"
                        }
                        val bgColor = if (isSelected) Color(0xFF00875A).copy(alpha = 0.1f) else Color.Transparent
                        val borderStroke = if (isSelected) BorderStroke(1.5.dp, Color(0xFF00875A)) else BorderStroke(1.dp, Color(0xFFE5E5EA))
                        val contentColor = if (isSelected) Color(0xFF00875A) else Color(0xFF6E6E73)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(borderStroke, RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectMetric(metric) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = text,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00875A))
                    }
                } else if (selectedSocialFilter == SocialFilter.LOCAL && userLatState == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Localisation non partagée",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E1E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Activez le partage de position dans votre profil pour voir les conquérants et clans à proximité.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6E6E73),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (selectedTab == 0) {
                    if (players.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Aucun joueur enregistré",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1E1E)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                        ) {
                            itemsIndexed(players) { index, player ->
                                if (index > 0) {
                                    HorizontalDivider(color = Color(0xFFF4F5F7))
                                }
                                PlayerRow(
                                    rank = index + 1,
                                    player = player,
                                    isMe = player.id == userId,
                                    metric = selectedMetric,
                                    friendsStatusMap = friendsStatusMap,
                                    onClick = { selectedPlayerForProfile = player }
                                )
                            }
                        }
                    }
                } else {
                    if (clans.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
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
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Aucun clan enregistré",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1E1E)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                        ) {
                            itemsIndexed(clans) { index, clan ->
                                if (index > 0) {
                                    HorizontalDivider(color = Color(0xFFF4F5F7))
                                }
                                ClanRow(
                                    rank = index + 1,
                                    clan = clan,
                                    isMyClan = clan.id == userGuildId,
                                    metric = selectedMetric,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Bottom Row
            if (showStickyPlayer) {
                val me = players[meIndex]
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00875A).copy(alpha = 0.3f))
                ) {
                    PlayerRow(
                        rank = meIndex + 1,
                        player = me,
                        isMe = true,
                        metric = selectedMetric,
                        friendsStatusMap = friendsStatusMap,
                        onClick = { selectedPlayerForProfile = me }
                    )
                }
            } else if (showStickyClan) {
                val myClan = clans[myClanIndex]
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00875A).copy(alpha = 0.3f))
                ) {
                    ClanRow(
                        rank = myClanIndex + 1,
                        clan = myClan,
                        isMyClan = true,
                        metric = selectedMetric,
                        onClick = {}
                    )
                }
            }
        }

        // Detailed profile view Dialog
        if (selectedPlayerForProfile != null) {
            val player = selectedPlayerForProfile!!
            val parsedColor = remember(player.empireColor) {
                try { Color(android.graphics.Color.parseColor(player.empireColor)) } catch (e: Exception) { Color(0xFF00875A) }
            }
            val friendStatus = friendsStatusMap[player.id]
            val me = players.firstOrNull { it.id == userId }
            val myGuildNom = me?.guildeNom
            val isAlreadyInOurGuild = player.guildeNom != null && myGuildNom != null && player.guildeNom == myGuildNom

            AlertDialog(
                onDismissRequest = { selectedPlayerForProfile = null },
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AvatarImage(
                                avatarUrl = player.avatarUrl,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, parsedColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = player.pseudonyme,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF1E1E1E)
                                )
                                if (player.tag != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "#${player.tag}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF6E6E73)
                                    )
                                }
                            }
                            if (player.guildeNom != null) {
                                val gColor = try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { Color(0xFF00875A) }
                                Text(
                                    text = "Clan: ${player.guildeNom}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = gColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Statistics
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF4F5F7), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Territoire total", color = Color(0xFF6E6E73), fontSize = 13.sp)
                                    Text(
                                        text = formatLeaderboardArea(player.totalAreaM2),
                                        color = Color(0xFF1E1E1E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Distance totale", color = Color(0xFF6E6E73), fontSize = 13.sp)
                                    Text(
                                        text = formatLeaderboardDistance(player.distanceTotale),
                                        color = Color(0xFF1E1E1E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Boucles bouclées", color = Color(0xFF6E6E73), fontSize = 13.sp)
                                    Text(
                                        text = formatLeaderboardLoops(player.loopCount),
                                        color = Color(0xFF1E1E1E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                if (player.latitude != null && player.longitude != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Coordonnées", color = Color(0xFF6E6E73), fontSize = 13.sp)
                                        Text(
                                            text = "%.5f, %.5f".format(player.latitude, player.longitude),
                                            color = Color(0xFF1E1E1E),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Link to see territory on the map
                        if (player.latitude != null && player.longitude != null) {
                            TextButton(
                                onClick = {
                                    onPlayerClick(Point.fromLngLat(player.longitude, player.latitude))
                                    selectedPlayerForProfile = null
                                }
                            ) {
                                Text(
                                    text = "Aller sur son territoire",
                                    color = Color(0xFF00875A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Aucune position partagée",
                                color = Color(0xFF6E6E73),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Friend invitation action
                        if (player.id != userId) {
                            val (friendBtnText, friendBtnEnabled, friendBtnColor) = when (friendStatus) {
                                "accepte" -> Triple("✓ Amis", false, Color(0xFF00875A))
                                "en_attente_envoye" -> Triple("Demande envoyée", false, Color.Gray)
                                "en_attente_recu" -> Triple("Répondre (demande reçue)", false, Color(0xFF00875A))
                                else -> Triple("Demander en ami", true, Color(0xFF00875A))
                            }

                            Button(
                                onClick = { sendFriendRequest(player.id) },
                                enabled = friendBtnEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = friendBtnColor,
                                    disabledContainerColor = if (friendStatus == "accepte") Color(0xFF00875A).copy(alpha = 0.1f) else Color.LightGray
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = friendBtnText,
                                    color = if (friendStatus == "accepte") Color(0xFF00875A) else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Guild invitation action
                            if (userGuildId != null) {
                                val isInvited = sentGuildInvitationsSet.contains(player.id)
                                when {
                                    isAlreadyInOurGuild -> {
                                        OutlinedButton(
                                            onClick = {},
                                            enabled = false,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Déjà dans votre clan", color = Color.Gray)
                                        }
                                    }
                                    isInvited -> {
                                        OutlinedButton(
                                            onClick = {},
                                            enabled = false,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Clan : Invitation envoyée", color = Color.Gray)
                                        }
                                    }
                                    else -> {
                                        OutlinedButton(
                                            onClick = { inviteToGuild(player.id) },
                                            border = BorderStroke(1.5.dp, Color(0xFF00875A)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Inviter dans le clan", color = Color(0xFF00875A), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPlayerForProfile = null }) {
                        Text("FERMER", color = Color(0xFF6E6E73), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
