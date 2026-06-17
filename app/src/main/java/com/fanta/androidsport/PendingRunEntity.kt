package com.fanta.androidsport

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "pending_runs")
data class PendingRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val dateDebut: String,
    val dateFin: String,
    val distanceKm: Double,
    val durationSec: Double,
    val isLoop: Boolean,
    val points: List<String>,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val vitesseMoyenne: Double,
    val vitesseMax: Double,
    val allureMoyenne: Double,
    val caloriesEstimees: Double,
    val denivelePositif: Double,
    val deniveleNegatif: Double,
    val totalSteps: Int = 0,
    val averageCadence: Int = 0,
    val nom: String? = null,
    val legende: String? = null,
    val pointsDetailsJson: String? = null
) {
    fun toPendingRun(): PendingRun {
        return PendingRun(
            userId = userId,
            dateDebut = dateDebut,
            dateFin = dateFin,
            distanceKm = distanceKm,
            durationSec = durationSec,
            isLoop = isLoop,
            points = points,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
            vitesseMoyenne = vitesseMoyenne,
            vitesseMax = vitesseMax,
            allureMoyenne = allureMoyenne,
            caloriesEstimees = caloriesEstimees,
            denivelePositif = denivelePositif,
            deniveleNegatif = deniveleNegatif,
            totalSteps = totalSteps,
            averageCadence = averageCadence,
            nom = nom,
            legende = legende,
            pointsDetailsJson = pointsDetailsJson
        )
    }

    companion object {
        fun fromPendingRun(run: PendingRun): PendingRunEntity {
            return PendingRunEntity(
                userId = run.userId,
                dateDebut = run.dateDebut,
                dateFin = run.dateFin,
                distanceKm = run.distanceKm,
                durationSec = run.durationSec,
                isLoop = run.isLoop,
                points = run.points,
                lastLatitude = run.lastLatitude,
                lastLongitude = run.lastLongitude,
                vitesseMoyenne = run.vitesseMoyenne,
                vitesseMax = run.vitesseMax,
                allureMoyenne = run.allureMoyenne,
                caloriesEstimees = run.caloriesEstimees,
                denivelePositif = run.denivelePositif,
                deniveleNegatif = run.deniveleNegatif,
                totalSteps = run.totalSteps,
                averageCadence = run.averageCadence,
                nom = run.nom,
                legende = run.legende,
                pointsDetailsJson = run.pointsDetailsJson
            )
        }
    }
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return json.decodeFromString(value)
    }
}
