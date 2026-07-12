package com.fanta.androidsport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanta.androidsport.data.model.LeaderboardPlayer
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.screens.MetricFilter
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// Single, unlabeled global ranking — Clans, Amis and Local filters have been removed.
// The only remaining choice is which metric to rank by (territoire / distance / boucles).
class LeaderboardViewModel : ViewModel() {
    private var userId: String = ""

    private val _selectedMetric = MutableStateFlow(MetricFilter.TERRITOIRE)
    val selectedMetric: StateFlow<MetricFilter> = _selectedMetric.asStateFlow()

    private val _players = MutableStateFlow<List<LeaderboardPlayer>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var isInitialized = false

    fun init(userId: String) {
        this.userId = userId
        if (!isInitialized) {
            isInitialized = true
            loadLeaderboardData(forceRefresh = false)
        }
    }

    fun selectMetric(metric: MetricFilter) {
        _selectedMetric.value = metric
    }

    val playersList: StateFlow<List<LeaderboardPlayer>> = combine(
        _players,
        _selectedMetric
    ) { players, metric ->
        when (metric) {
            MetricFilter.TERRITOIRE -> players.sortedByDescending { it.totalAreaM2 }
            MetricFilter.DISTANCE -> players.sortedByDescending { it.distanceTotale }
            MetricFilter.BOUCLES -> players.sortedByDescending { it.loopCount }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadLeaderboardData(forceRefresh: Boolean = true) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            if (_players.value.isEmpty()) {
                _isLoading.value = true
            }
            try {
                val playersResponse = withContext(Dispatchers.IO) {
                    supabase.postgrest["leaderboard"].select()
                }
                val parsedPlayers = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(playersResponse.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur_${id.take(8)}"
                        val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                        val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                        val level = obj["level"]?.jsonPrimitive?.intOrNull ?: 1
                        val bannerUrl = obj["banner_url"]?.jsonPrimitive?.contentOrNull
                        val pLat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                        val pLon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                        val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val loopCount = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val distTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val gNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                        val gColor = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                        LeaderboardPlayer(id, pseudo, tag, color, level, bannerUrl, pLat, pLon, areaM2, loopCount, distTotale, avatar, gNom, gColor)
                    } ?: emptyList()
                }
                _players.value = parsedPlayers
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to fetch leaderboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
