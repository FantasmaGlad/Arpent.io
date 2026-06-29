package com.fanta.androidsport.data.model

data class FriendItem(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?,
    val color: String
)

data class PendingRequestItem(
    val id: String,
    val senderId: String,
    val pseudo: String,
    val avatarUrl: String?
)

data class ProximitySuggestion(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?,
    val empireColor: String,
    val distanceMeters: Double
)

data class ClanMember(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?
)

data class ClanItem(
    val id: String,
    val nom: String,
    val color: String,
    val avatarUrl: String?
)

data class GuildeInvitationItem(
    val id: String,
    val guildeId: String,
    val guildeNom: String,
    val guildeCouleur: String,
    val guildeAvatar: String?,
    val dateInvitation: String
)

