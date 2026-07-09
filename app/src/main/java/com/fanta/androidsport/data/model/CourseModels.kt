package com.fanta.androidsport.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CourseItem(
    val id: String,
    val dateDebut: String,
    val distanceTotale: Double,
    val dureeSecondes: Double,
    val estBouclee: Boolean,
    val vitesseMoyenne: Double,
    val vitesseMax: Double,
    val allureMoyenne: Double,
    val caloriesEstimees: Double,
    val denivelePositif: Double,
    val deniveleNegatif: Double
)

@Serializable
data class GPSPoint(
    val longitude: Double,
    val latitude: Double,
    val altitude: Double? = null,
    val timestamp_gps: String? = null
)

@Serializable
data class CourseReaction(
    val id: String,
    val utilisateur_id: String,
    val pseudonyme: String,
    val type_reaction: String
)

@Serializable
data class CourseCommentaire(
    val id: String,
    val utilisateur_id: String,
    val pseudonyme: String,
    val avatar_url: String? = null,
    val contenu: String,
    val date_creation: String
)

data class FeedCourseItem(
    val id: String,
    val utilisateurId: String,
    val pseudonyme: String,
    val avatarUrl: String?,
    val empireColor: String?,
    val level: Int,
    val guildeNom: String?,
    val guildeCouleur: String?,
    val dateDebut: String,
    val dateFin: String,
    val distanceTotale: Double,
    val dureeSecondes: Double,
    val estBouclee: Boolean,
    val vitesseMoyenne: Double,
    val vitesseMax: Double,
    val allureMoyenne: Double,
    val caloriesEstimees: Double,
    val denivelePositif: Double,
    val deniveleNegatif: Double,
    val nom: String?,
    val legende: String?,
    val superficieConquise: Double,
    val totalSteps: Int,
    val averageCadence: Int,
    val imageUrl: String? = null,
    val pointsGps: List<GPSPoint>,
    val reactions: List<CourseReaction>,
    val commentaires: List<CourseCommentaire>
)
