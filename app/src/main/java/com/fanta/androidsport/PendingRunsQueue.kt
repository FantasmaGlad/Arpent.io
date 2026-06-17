package com.fanta.androidsport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import java.io.File

@Serializable
data class PendingRun(
    val userId: String,
    val dateDebut: String,
    val dateFin: String,
    val distanceKm: Double,
    val durationSec: Double,
    val isLoop: Boolean,
    val points: List<String>,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val vitesseMoyenne: Double = 0.0,
    val vitesseMax: Double = 0.0,
    val allureMoyenne: Double = 0.0,
    val caloriesEstimees: Double = 0.0,
    val denivelePositif: Double = 0.0,
    val deniveleNegatif: Double = 0.0,
    val totalSteps: Int = 0,
    val averageCadence: Int = 0,
    val nom: String? = null,
    val legende: String? = null,
    val pointsDetailsJson: String? = null
)

object PendingRunsQueue {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun migrateLegacyQueue(context: Context, dao: PendingRunDao) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "pending_runs.json")
        if (file.exists()) {
            try {
                val content = file.readText()
                val legacyRuns = json.decodeFromString<List<PendingRun>>(content)
                for (run in legacyRuns) {
                    dao.insertRun(PendingRunEntity.fromPendingRun(run))
                }
                file.delete()
                Log.d("PendingRunsQueue", "Migration réussie de ${legacyRuns.size} courses depuis le fichier JSON vers Room.")
            } catch (e: Exception) {
                Log.e("PendingRunsQueue", "Erreur lors de la migration du fichier JSON hérité", e)
            }
        }
    }

    suspend fun enqueue(context: Context, run: PendingRun) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.pendingRunDao()
            migrateLegacyQueue(context, dao)
            val entity = PendingRunEntity.fromPendingRun(run)
            dao.insertRun(entity)
            Log.d("PendingRunsQueue", "Course ajoutée à Room Database.")
        } catch (e: Exception) {
            Log.e("PendingRunsQueue", "Erreur lors de l'ajout de la course dans Room", e)
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    suspend fun syncPendingRuns(context: Context, supabase: SupabaseClient) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.pendingRunDao()
        migrateLegacyQueue(context, dao)

        if (!isNetworkAvailable(context)) {
            Log.d("PendingRunsQueue", "Pas de réseau pour la synchronisation.")
            return@withContext
        }

        val queue = try {
            dao.getAllRuns()
        } catch (e: Exception) {
            Log.e("PendingRunsQueue", "Erreur de lecture depuis la base locale Room", e)
            return@withContext
        }

        if (queue.isEmpty()) return@withContext

        Log.d("PendingRunsQueue", "Début de la synchronisation de ${queue.size} courses en attente...")

        for (entity in queue) {
            val run = entity.toPendingRun()
            try {
                // Parse pointsDetailsJson to JSON Element
                val pointsDetailsJsonElement = if (!run.pointsDetailsJson.isNullOrEmpty()) {
                    try {
                        json.parseToJsonElement(run.pointsDetailsJson)
                    } catch (e: Exception) {
                        JsonArray(emptyList())
                    }
                } else {
                    JsonArray(emptyList())
                }

                // Construction du payload JSON pour l'appel RPC de Supabase
                val params = buildJsonObject {
                    put("p_user_id", JsonPrimitive(run.userId))
                    put("p_date_debut", JsonPrimitive(run.dateDebut))
                    put("p_date_fin", JsonPrimitive(run.dateFin))
                    put("p_distance_totale", JsonPrimitive(run.distanceKm))
                    put("p_duree_secondes", JsonPrimitive(run.durationSec))
                    put("p_est_bouclee", JsonPrimitive(run.isLoop))
                    put("p_points", JsonArray(run.points.map { JsonPrimitive(it) }))
                    put("p_vitesse_moyenne", JsonPrimitive(run.vitesseMoyenne))
                    put("p_vitesse_max", JsonPrimitive(run.vitesseMax))
                    put("p_allure_moyenne", JsonPrimitive(run.allureMoyenne))
                    put("p_calories_estimees", JsonPrimitive(run.caloriesEstimees))
                    put("p_denivele_positif", JsonPrimitive(run.denivelePositif))
                    put("p_denivele_negatif", JsonPrimitive(run.deniveleNegatif))
                    put("p_total_steps", JsonPrimitive(run.totalSteps))
                    put("p_average_cadence", JsonPrimitive(run.averageCadence))
                    put("p_nom_course", JsonPrimitive(run.nom ?: ""))
                    put("p_legende", JsonPrimitive(run.legende ?: ""))
                    put("p_points_details", pointsDetailsJsonElement)
                }

                supabase.postgrest.rpc("enregistrer_course", params)

                // Mise à jour de la dernière position connue du profil utilisateur si présente
                if (run.lastLatitude != null && run.lastLongitude != null) {
                    try {
                        supabase.postgrest["profiles"].update(
                            mapOf(
                                "latitude" to run.lastLatitude,
                                "longitude" to run.lastLongitude
                            )
                        ) {
                            filter { eq("id", run.userId) }
                        }
                    } catch (ex: Exception) {
                        Log.e("PendingRunsQueue", "Erreur lors de la mise à jour de la position du profil", ex)
                    }
                }

                // Suppression de la ligne locale Room puisque la synchronisation a réussi
                dao.deleteRunById(entity.id)
                Log.d("PendingRunsQueue", "Course synchronisée avec succès et retirée de la base locale.")
            } catch (e: Exception) {
                Log.e("PendingRunsQueue", "Échec de synchronisation d'une course, conservée localement.", e)
            }
        }
        Log.d("PendingRunsQueue", "Fin de la synchronisation.")
    }
}
