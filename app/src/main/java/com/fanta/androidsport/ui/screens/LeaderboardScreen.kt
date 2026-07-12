package com.fanta.androidsport.ui.screens

import android.location.Location
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fanta.androidsport.data.model.LeaderboardPlayer
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.TerritoryMapBackground
import com.fanta.androidsport.ui.theme.BrandGreen
import com.fanta.androidsport.ui.viewmodel.LeaderboardViewModel
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point

enum class MetricFilter { TERRITOIRE, DISTANCE, BOUCLES }

fun formatLeaderboardArea(areaM2: Double): String {
    val areaKm2 = areaM2 / 1_000_000.0
    return if (areaKm2 > 0.0 && areaKm2 < 0.001) {
        "%.4f km²".format(areaKm2)
    } else {
        "%.3f km²".format(areaKm2)
    }
}

fun formatLeaderboardDistance(distanceKm: Double): String {
    val distanceMeters = distanceKm * 1000.0
    return if (distanceMeters < 1000.0) {
        "%,.0f m".format(distanceMeters).replace(",", " ")
    } else {
        "%.1f km".format(distanceKm)
    }
}

fun formatLeaderboardLoops(loops: Int): String {
    return if (loops <= 1) {
        "$loops boucle"
    } else {
        "$loops boucles"
    }
}

private fun metricLabel(metric: MetricFilter): String = when (metric) {
    MetricFilter.TERRITOIRE -> "Territoire"
    MetricFilter.DISTANCE -> "Distance"
    MetricFilter.BOUCLES -> "Boucles"
}

private fun metricValue(player: LeaderboardPlayer, metric: MetricFilter): String = when (metric) {
    MetricFilter.TERRITOIRE -> formatLeaderboardArea(player.totalAreaM2)
    MetricFilter.DISTANCE -> formatLeaderboardDistance(player.distanceTotale)
    MetricFilter.BOUCLES -> formatLeaderboardLoops(player.loopCount)
}

// A single ranking row/card. Bigger for the podium (top 3), compact for the rest.
// Background is the player's banner at full opacity when set, otherwise their
// empire color at 70% opacity.
@Composable
fun LeaderboardPlayerCard(
    rank: Int,
    player: LeaderboardPlayer,
    metric: MetricFilter,
    isMe: Boolean,
    isTop: Boolean,
    onClick: () -> Unit
) {
    val fallbackColor = BrandGreen
    val parsedColor = remember(player.empireColor, fallbackColor) {
        try { Color(android.graphics.Color.parseColor(player.empireColor)) } catch (e: Exception) { fallbackColor }
    }
    val hasBanner = !player.bannerUrl.isNullOrEmpty()

    val cardHeight = if (isTop) 92.dp else 68.dp
    val avatarSize = if (isTop) 56.dp else 42.dp
    val rankFontSize = if (isTop) 24.sp else 17.sp
    val nameFontSize = if (isTop) 16.sp else 14.sp
    val guildFontSize = if (isTop) 12.sp else 11.sp
    val levelFontSize = if (isTop) 13.sp else 11.sp
    val valueFontSize = if (isTop) 16.sp else 13.sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isMe) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (hasBanner) {
            AsyncImage(
                model = player.bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.60f), Color.Black.copy(alpha = 0.20f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(parsedColor.copy(alpha = 0.70f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(if (isTop) 32.dp else 26.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    fontWeight = FontWeight.Black,
                    fontSize = rankFontSize,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AvatarImage(
                avatarUrl = player.avatarUrl,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.pseudonyme + if (isMe) " (Moi)" else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = nameFontSize,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!player.guildeNom.isNullOrEmpty()) {
                    Text(
                        text = player.guildeNom,
                        fontWeight = FontWeight.Medium,
                        fontSize = guildFontSize,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Niveau ${player.level}",
                    fontWeight = FontWeight.Bold,
                    fontSize = levelFontSize,
                    color = Color.White
                )
                Text(
                    text = metricValue(player, metric),
                    fontWeight = FontWeight.Black,
                    fontSize = valueFontSize,
                    color = Color.White
                )
            }
        }
    }
}

// Small capsule button on the divider between the podium and the rest of the
// ranking — opens a menu to switch which metric the leaderboard is sorted by.
@Composable
private fun MetricSwitcher(
    current: MetricFilter,
    onSelect: (MetricFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.35f))
        Box {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clickable { expanded = true }
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Changer le classement (${metricLabel(current)})",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MetricFilter.values().forEach { metric ->
                    DropdownMenuItem(
                        text = { Text(metricLabel(metric), fontWeight = if (metric == current) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onSelect(metric)
                            expanded = false
                        }
                    )
                }
            }
        }
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

    val players by viewModel.playersList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    var selectedPlayerForProfile by remember { mutableStateOf<LeaderboardPlayer?>(null) }

    LaunchedEffect(userId) {
        viewModel.init(userId)
    }

    // When the screen becomes active or visible, refresh the data.
    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.loadLeaderboardData(forceRefresh = true)
        }
    }

    // Best-effort last known location for the decorative map background.
    var userLocation by remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) userLocation = loc
            }
        }
    }
    val mapFallbackCenter = remember(userLocation) {
        if (userLocation != null) {
            Point.fromLngLat(userLocation!!.longitude, userLocation!!.latitude)
        } else {
            Point.fromLngLat(2.3522, 48.8566) // Paris default
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Same button-free map background as the profile screen
        if (isActive) {
            TerritoryMapBackground(
                polygons = emptyList(),
                empireColor = MaterialTheme.colorScheme.primary,
                fallbackCenter = mapFallbackCenter,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        } else if (players.isEmpty()) {
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
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Aucun joueur enregistré",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            val top3 = players.take(3)
            val rest = players.drop(3)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                top3.forEachIndexed { index, player ->
                    LeaderboardPlayerCard(
                        rank = index + 1,
                        player = player,
                        metric = selectedMetric,
                        isMe = player.id == userId,
                        isTop = true,
                        onClick = { selectedPlayerForProfile = player }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (rest.isNotEmpty()) {
                    MetricSwitcher(
                        current = selectedMetric,
                        onSelect = { viewModel.selectMetric(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(rest) { index, player ->
                            LeaderboardPlayerCard(
                                rank = index + 4,
                                player = player,
                                metric = selectedMetric,
                                isMe = player.id == userId,
                                isTop = false,
                                onClick = { selectedPlayerForProfile = player }
                            )
                        }
                    }
                }
            }
        }
    }

    // Detailed profile view Dialog — all other statistics live there.
    if (selectedPlayerForProfile != null) {
        PlayerProfileDialog(
            playerId = selectedPlayerForProfile!!.id,
            currentUserId = userId,
            onDismissRequest = { selectedPlayerForProfile = null },
            onNavigateToTerritory = { point ->
                selectedPlayerForProfile = null
                onPlayerClick(point)
            }
        )
    }
}
