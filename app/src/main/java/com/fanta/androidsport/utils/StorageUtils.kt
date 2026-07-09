package com.fanta.androidsport.utils

import android.content.Context
import android.widget.Toast
import com.fanta.androidsport.PendingRun
import com.fanta.androidsport.PendingRunsQueue
import com.fanta.androidsport.supabase
import com.mapbox.geojson.Point
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun saveTerritoriesLocally(context: Context, polygons: List<List<Point>>) {
    try {
        val file = File(context.filesDir, "local_territories.json")
        val jsonArray = kotlinx.serialization.json.buildJsonArray {
            polygons.forEach { poly ->
                add(kotlinx.serialization.json.buildJsonArray {
                    poly.forEach { pt ->
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("lng", JsonPrimitive(pt.longitude()))
                            put("lat", JsonPrimitive(pt.latitude()))
                        })
                    }
                })
            }
        }
        file.writeText(jsonArray.toString())
        android.util.Log.d("Arpent", "Territoires sauvegardés en local: ${polygons.size} polygones.")
    } catch (e: Exception) {
        android.util.Log.e("Arpent", "Failed to save local territories", e)
    }
}

fun loadTerritoriesLocally(context: Context): List<List<Point>> {
    try {
        val file = File(context.filesDir, "local_territories.json")
        if (!file.exists()) return emptyList()
        val text = file.readText()
        val jsonArray = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        val polygons = mutableListOf<List<Point>>()
        jsonArray.forEach { polyEl ->
            val pts = mutableListOf<Point>()
            (polyEl as? JsonArray)?.forEach { ptEl ->
                val obj = ptEl as? JsonObject
                val lng = obj?.get("lng")?.jsonPrimitive?.doubleOrNull
                val lat = obj?.get("lat")?.jsonPrimitive?.doubleOrNull
                if (lng != null && lat != null) {
                    pts.add(Point.fromLngLat(lng, lat))
                }
            }
            if (pts.isNotEmpty()) {
                polygons.add(pts)
            }
        }
        android.util.Log.d("Arpent", "Territoires chargés du local: ${polygons.size} polygones.")
        return polygons
    } catch (e: Exception) {
        android.util.Log.e("Arpent", "Failed to load local territories", e)
        return emptyList()
    }
}

suspend fun syncTerritoriesFromDatabase(
    userId: String,
    context: Context,
    completedPolygonsList: androidx.compose.runtime.snapshots.SnapshotStateList<List<Point>>
) {
    if (!isNetworkAvailable(context)) {
        android.util.Log.d("Arpent", "Pas de réseau détecté, synchronisation différée. Utilisation du cache local.")
        return
    }

    try {
        val result = withContext(Dispatchers.IO) {
            supabase.postgrest["territoires"].select {
                filter {
                    eq("utilisateur_id", userId)
                }
            }
        }
        val dbPolygons = withContext(Dispatchers.Default) {
            val terrArray = Json.parseToJsonElement(result.data) as? JsonArray
            val polys = mutableListOf<List<Point>>()
            terrArray?.forEach { element ->
                val obj = element as? JsonObject
                val ptsArray = obj?.get("points")?.jsonArray
                if (ptsArray != null) {
                    val pts = ptsArray.mapNotNull { ptEl ->
                        val str = ptEl.jsonPrimitive.content
                        val parts = str.split(" ")
                        if (parts.size == 2) {
                            val lng = parts[0].toDoubleOrNull()
                            val lat = parts[1].toDoubleOrNull()
                            if (lng != null && lat != null) {
                                Point.fromLngLat(lng, lat)
                            } else null
                        } else null
                    }
                    if (pts.isNotEmpty()) {
                        polys.addAll(splitIntoClosedPolygons(pts))
                    }
                }
            }
            polys
        }

        withContext(Dispatchers.Main) {
            completedPolygonsList.clear()
            completedPolygonsList.addAll(dbPolygons)
        }
        withContext(Dispatchers.IO) {
            saveTerritoriesLocally(context, dbPolygons)
        }
        android.util.Log.d("Arpent", "Synchronisation avec la BDD réussie: ${dbPolygons.size} polygones.")
    } catch (e: Exception) {
        android.util.Log.e("Arpent", "Error syncing territories from database", e)
    }
}


fun saveRunToDatabase(
    userId: String,
    scope: CoroutineScope,
    context: Context,
    runStartTime: Long,
    runDistance: Double,
    isLoop: Boolean,
    closedPoints: List<Point>,
    rawPoints: List<com.fanta.androidsport.TrackerPoint>,
    completedPolygons: androidx.compose.runtime.snapshots.SnapshotStateList<List<Point>>,
    nom: String?,
    legende: String?,
    imageUrl: String? = null,
    onSuccess: (Double) -> Unit,
    onSyncComplete: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val dateDebut = java.time.Instant.ofEpochMilli(runStartTime).toString()
            val dateFin = java.time.Instant.now().toString()
            val durationSec = ((System.currentTimeMillis() - runStartTime) / 1000.0)
            val distanceKm = runDistance / 1000.0

            // Compute Strava-like metrics
            val vitesseMoyenne = if (durationSec > 0) (distanceKm / (durationSec / 3600.0)) else 0.0
            val allureMoyenne = if (distanceKm > 0) ((durationSec / 60.0) / distanceKm) else 0.0
            // Rough calorie estimate: ~60 kcal per km of running (avg 70 kg person)
            val caloriesEstimees = distanceKm * 60.0

            // Format coordinates array for PostGIS: "longitude latitude"
            val pointsArray = closedPoints.map { "${it.longitude()} ${it.latitude()}" }
            val lastPt = closedPoints.lastOrNull()

            // Fetch tracker metrics (passed as parameter to prevent race condition)
            val totalSteps = if (rawPoints.isNotEmpty()) rawPoints.last().steps ?: 0 else 0
            val validCadences = rawPoints.mapNotNull { it.cadence }.filter { it > 0 }
            val averageCadence = if (validCadences.isNotEmpty()) validCadences.average().toInt() else 0

            val rawSpeeds = rawPoints.mapNotNull { it.speed }
            val vitesseMax = if (rawSpeeds.isNotEmpty()) rawSpeeds.maxOrNull() ?: 0.0 else 0.0

            var denivelePos = 0.0
            var deniveleNeg = 0.0
            val validAltitudes = rawPoints.mapNotNull { it.altitude }
            if (validAltitudes.size > 1) {
                var prevAlt = validAltitudes.first()
                for (i in 1 until validAltitudes.size) {
                    val currAlt = validAltitudes[i]
                    val diff = currAlt - prevAlt
                    if (diff > 0.5) { // filter out minor noise
                        denivelePos += diff
                        prevAlt = currAlt
                    } else if (diff < -0.5) {
                        deniveleNeg += -diff
                        prevAlt = currAlt
                    }
                }
            }

            // Serialize pointsDetailsJson
            val pointsDetailsJson = try {
                Json.encodeToString(rawPoints)
            } catch (e: Exception) {
                null
            }

            val pendingRun = PendingRun(
                userId = userId,
                dateDebut = dateDebut,
                dateFin = dateFin,
                distanceKm = distanceKm,
                durationSec = durationSec,
                isLoop = isLoop,
                points = pointsArray,
                lastLatitude = lastPt?.latitude(),
                lastLongitude = lastPt?.longitude(),
                vitesseMoyenne = vitesseMoyenne,
                vitesseMax = vitesseMax,
                allureMoyenne = allureMoyenne,
                caloriesEstimees = caloriesEstimees,
                denivelePositif = denivelePos,
                deniveleNegatif = deniveleNeg,
                totalSteps = totalSteps,
                averageCadence = averageCadence,
                nom = nom,
                legende = legende,
                pointsDetailsJson = pointsDetailsJson,
                imageUrl = imageUrl
            )

            // Save to local offline queue first
            PendingRunsQueue.enqueue(context, pendingRun)

            // Compute estimated area for immediate UI update
            val areaKm2 = estimateAreaKm2(closedPoints)
            withContext(Dispatchers.Main) {
                onSuccess(areaKm2)
            }

            // Sync with remote database in background
            if (isNetworkAvailable(context)) {
                PendingRunsQueue.syncPendingRuns(context, supabase)
                // Fetch the clean, database-fused polygons
                syncTerritoriesFromDatabase(userId, context, completedPolygons)
                // Trigger stats refresh again to show exact DB cached areas
                withContext(Dispatchers.Main) {
                    onSyncComplete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Erreur d'enregistrement local de la course", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur d'enregistrement : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
