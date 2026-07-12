package com.fanta.androidsport.data.model

import androidx.compose.ui.graphics.Color

data class GuildRank(
    val rank: Int,
    val name: String,
    val territorySqKm: Double,
    val color: Color,
    val isUserGuild: Boolean = false
)

data class LeaderboardPlayer(
    val id: String,
    val pseudonyme: String,
    val tag: String?,
    val empireColor: String,
    val level: Int,
    val bannerUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val totalAreaM2: Double,
    val loopCount: Int,
    val distanceTotale: Double,
    val avatarUrl: String?,
    val guildeNom: String?,
    val guildeCouleur: String?
)
