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
import kotlinx.serialization.encodeToString
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
    val deniveleNegatif: Double = 0.0
)

object PendingRunsQueue {
    private const val FILE_NAME = "pending_runs.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun getQueueFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    @Synchronized
    private fun readQueue(context: Context): List<PendingRun> {
        val file = getQueueFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            json.decodeFromString<List<PendingRun>>(content)
        } catch (e: Exception) {
            Log.e("PendingRunsQueue", "Erreur lors de la lecture de la file d'attente locale", e)
            emptyList()
        }
    }

    @Synchronized
    private fun writeQueue(context: Context, queue: List<PendingRun>) {
        val file = getQueueFile(context)
        try {
            val content = json.encodeToString(queue)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("PendingRunsQueue", "Erreur lors de l'écriture de la file d'attente locale", e)
        }
    }

    suspend fun enqueue(context: Context, run: PendingRun) = withContext(Dispatchers.IO) {
        val currentQueue = readQueue(context).toMutableList()
        currentQueue.add(run)
        writeQueue(context, currentQueue)
        Log.d("PendingRunsQueue", "Course ajoutée à la file locale. Taille: ${currentQueue.size}")
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
        if (!isNetworkAvailable(context)) {
            Log.d("PendingRunsQueue", "Pas de réseau pour la synchronisation.")
            return@withContext
        }

        val queue = readQueue(context)
        if (queue.isEmpty()) return@withContext

        Log.d("PendingRunsQueue", "Début de la synchronisation de ${queue.size} courses en attente...")
        val remainingQueue = mutableListOf<PendingRun>()

        for (run in queue) {
            try {
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
                Log.d("PendingRunsQueue", "Course synchronisée avec succès.")
            } catch (e: Exception) {
                Log.e("PendingRunsQueue", "Échec de synchronisation d'une course, remise dans la file d'attente", e)
                remainingQueue.add(run)
            }
        }

        writeQueue(context, remainingQueue)
        Log.d("PendingRunsQueue", "Fin de la synchronisation. Reste dans la file: ${remainingQueue.size}")
    }
}
