package com.fanta.androidsport.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanta.androidsport.data.model.LeaderboardClan
import com.fanta.androidsport.data.model.LeaderboardPlayer
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.screens.MetricFilter
import com.fanta.androidsport.ui.screens.SocialFilter
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

class LeaderboardViewModel : ViewModel() {
    private var userId: String = ""
    private var userGuildId: String? = null

    private val _selectedTab = MutableStateFlow(0) // 0 = Joueurs, 1 = Clans
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedSocialFilter = MutableStateFlow(SocialFilter.GLOBAL)
    val selectedSocialFilter: StateFlow<SocialFilter> = _selectedSocialFilter.asStateFlow()

    private val _selectedMetric = MutableStateFlow(MetricFilter.TERRITOIRE)
    val selectedMetric: StateFlow<MetricFilter> = _selectedMetric.asStateFlow()

    // Cache of loaded data
    private val _globalPlayers = MutableStateFlow<List<LeaderboardPlayer>>(emptyList())
    private val _globalClans = MutableStateFlow<List<LeaderboardClan>>(emptyList())
    private val _friendsStatusMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _sentGuildInvitationsSet = MutableStateFlow<Set<String>>(emptySet())
    
    private val _userLat = MutableStateFlow<Double?>(null)
    val userLat: StateFlow<Double?> = _userLat.asStateFlow()
    
    private val _userLon = MutableStateFlow<Double?>(null)
    val userLon: StateFlow<Double?> = _userLon.asStateFlow()

    private val _localPlayers = MutableStateFlow<List<LeaderboardPlayer>>(emptyList())
    private val _localClans = MutableStateFlow<List<LeaderboardClan>>(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var isInitialized = false

    fun init(userId: String, userGuildId: String?) {
        this.userId = userId
        this.userGuildId = userGuildId
        if (!isInitialized) {
            isInitialized = true
            loadLeaderboardData(forceRefresh = false)
        }
    }

    fun updateGuildId(guildId: String?) {
        if (this.userGuildId != guildId) {
            this.userGuildId = guildId
            if (isInitialized) {
                fetchGuildInvitations()
            }
        }
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectSocialFilter(filter: SocialFilter) {
        _selectedSocialFilter.value = filter
        if (filter == SocialFilter.LOCAL) {
            if (_localPlayers.value.isEmpty() && _localClans.value.isEmpty()) {
                _isLoading.value = true
            }
            fetchLocalLeaderboard()
        }
    }

    fun selectMetric(metric: MetricFilter) {
        _selectedMetric.value = metric
    }

    // Exposed flows computed dynamically and reactively on background Thread
    val playersList: StateFlow<List<LeaderboardPlayer>> = combine(
        _globalPlayers,
        _friendsStatusMap,
        _localPlayers,
        _selectedSocialFilter,
        _selectedMetric
    ) { global, friends, local, social, metric ->
        val filtered = when (social) {
            SocialFilter.GLOBAL -> global
            SocialFilter.AMIS -> global.filter { it.id == userId || friends[it.id] == "accepte" }
            SocialFilter.LOCAL -> local
        }
        when (metric) {
            MetricFilter.TERRITOIRE -> filtered.sortedByDescending { it.totalAreaM2 }
            MetricFilter.DISTANCE -> filtered.sortedByDescending { it.distanceTotale }
            MetricFilter.BOUCLES -> filtered.sortedByDescending { it.loopCount }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @Suppress("UNCHECKED_CAST")
    val clansList: StateFlow<List<LeaderboardClan>> = combine(
        _globalClans,
        _friendsStatusMap,
        _localClans,
        _selectedSocialFilter,
        _selectedMetric,
        playersList
    ) { array ->
        val global = array[0] as List<LeaderboardClan>
        val friends = array[1] as Map<String, String>
        val local = array[2] as List<LeaderboardClan>
        val social = array[3] as SocialFilter
        val metric = array[4] as MetricFilter
        val players = array[5] as List<LeaderboardPlayer>

        val filtered = when (social) {
            SocialFilter.GLOBAL -> global
            SocialFilter.AMIS -> {
                val friendClans = players.filter { it.id == userId || friends[it.id] == "accepte" }
                    .mapNotNull { it.guildeNom }
                    .toSet()
                global.filter { it.nom in friendClans }
            }
            SocialFilter.LOCAL -> local
        }
        when (metric) {
            MetricFilter.TERRITOIRE -> filtered.sortedByDescending { it.totalAreaM2 }
            MetricFilter.DISTANCE -> filtered.sortedByDescending { it.distanceTotale }
            MetricFilter.BOUCLES -> filtered.sortedByDescending { it.loopCount }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val friendsStatusMap: StateFlow<Map<String, String>> = _friendsStatusMap.asStateFlow()
    val sentGuildInvitationsSet: StateFlow<Set<String>> = _sentGuildInvitationsSet.asStateFlow()

    fun loadLeaderboardData(forceRefresh: Boolean = true) {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            // Only show full loading spinner if cache is empty to allow silent background refreshes
            if (_globalPlayers.value.isEmpty() && _globalClans.value.isEmpty()) {
                _isLoading.value = true
            }
            try {
                // 1. Fetch profiles for user coordinate share state
                try {
                    val userProfileRes = withContext(Dispatchers.IO) {
                        supabase.postgrest["profiles"].select {
                            filter { eq("id", userId) }
                        }
                    }
                    val jsonArray = Json.parseToJsonElement(userProfileRes.data) as? JsonArray
                    val userObj = jsonArray?.firstOrNull() as? JsonObject
                    val shareLoc = userObj?.get("share_location")?.jsonPrimitive?.booleanOrNull ?: false
                    if (shareLoc) {
                        _userLat.value = userObj?.get("latitude")?.jsonPrimitive?.doubleOrNull
                        _userLon.value = userObj?.get("longitude")?.jsonPrimitive?.doubleOrNull
                    } else {
                        _userLat.value = null
                        _userLon.value = null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LeaderboardVM", "Failed to fetch user coordinates", e)
                }

                // 2. Fetch all players (Global leaderboard)
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
                        val pLat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                        val pLon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                        val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val loopCount = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val distTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val gNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                        val gColor = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                        LeaderboardPlayer(id, pseudo, tag, color, pLat, pLon, areaM2, loopCount, distTotale, avatar, gNom, gColor)
                    } ?: emptyList()
                }
                _globalPlayers.value = parsedPlayers

                // 3. Fetch all clans (Global clan leaderboard)
                val clansResponse = withContext(Dispatchers.IO) {
                    supabase.postgrest["clan_leaderboard"].select()
                }
                val parsedClans = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(clansResponse.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: "Clan"
                        val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                        val color = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                        val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val membreCount = obj["membre_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val loopCount = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val distTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        LeaderboardClan(id, nom, tag, color, avatarUrl, areaM2, membreCount, loopCount, distTotale)
                    } ?: emptyList()
                }
                _globalClans.value = parsedClans

                // 4. Fetch friendships
                val friendsResponse = withContext(Dispatchers.IO) {
                    supabase.postgrest["amis"].select {
                        filter {
                            or {
                                eq("demandeur_id", userId)
                                eq("destinataire_id", userId)
                            }
                        }
                    }
                }
                val fetchedFriends = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(friendsResponse.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val dem = obj["demandeur_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val dest = obj["destinataire_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val stat = obj["statut"]?.jsonPrimitive?.content ?: "en_attente"
                        val otherUser = if (dem == userId) dest else dem
                        val mappedStatus = if (stat == "en_attente") {
                            if (dem == userId) "en_attente_envoye" else "en_attente_recu"
                        } else {
                            stat
                        }
                        otherUser to mappedStatus
                    }?.toMap() ?: emptyMap()
                }
                _friendsStatusMap.value = fetchedFriends

                // 5. Fetch guild invitations
                fetchGuildInvitations()

                // 6. Fetch local leaderboard if we have position
                if (_selectedSocialFilter.value == SocialFilter.LOCAL) {
                    fetchLocalLeaderboard()
                }
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to fetch leaderboard data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchGuildInvitations() {
        val guildId = userGuildId ?: return
        viewModelScope.launch {
            try {
                val invitesResponse = withContext(Dispatchers.IO) {
                    supabase.postgrest["guilde_invitations"].select {
                        filter {
                            eq("guilde_id", guildId)
                            eq("statut", "en_attente")
                        }
                    }
                }
                val fetchedInvites = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(invitesResponse.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        obj["destinataire_id"]?.jsonPrimitive?.content
                    }?.toSet() ?: emptySet()
                }
                _sentGuildInvitationsSet.value = fetchedInvites
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to fetch guild invitations", e)
            }
        }
    }

    fun fetchLocalLeaderboard() {
        val lat = _userLat.value
        val lon = _userLon.value
        if (lat == null || lon == null) return
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("user_lat", lat)
                    put("user_lon", lon)
                    put("max_dist_meters", 50000.0) // 50km
                }
                
                val response = withContext(Dispatchers.IO) {
                    supabase.postgrest.rpc("get_local_leaderboard", params)
                }
                val parsedPlayers = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(response.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur_${id.take(8)}"
                        val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                        val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                        val pLat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                        val pLon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                        val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val loopCount = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val distTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val gNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                        val gColor = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                        LeaderboardPlayer(id, pseudo, tag, color, pLat, pLon, areaM2, loopCount, distTotale, avatar, gNom, gColor)
                    } ?: emptyList()
                }
                _localPlayers.value = parsedPlayers

                val responseClans = withContext(Dispatchers.IO) {
                    supabase.postgrest.rpc("get_local_clan_leaderboard", params)
                }
                val parsedClans = withContext(Dispatchers.Default) {
                    val jsonArray = Json.parseToJsonElement(responseClans.data) as? JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: "Clan"
                        val tag = obj["tag"]?.jsonPrimitive?.contentOrNull
                        val color = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#00875A"
                        val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val membreCount = obj["membre_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val loopCount = obj["loop_count"]?.jsonPrimitive?.intOrNull ?: 0
                        val distTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        LeaderboardClan(id, nom, tag, color, avatarUrl, areaM2, membreCount, loopCount, distTotale)
                    } ?: emptyList()
                }
                _localClans.value = parsedClans
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to fetch local leaderboard", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendFriendRequest(friendId: String, onResult: (Boolean) -> Unit) {
        if (userId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val insertObj = buildJsonObject {
                    put("demandeur_id", userId)
                    put("destinataire_id", friendId)
                    put("statut", "en_attente")
                }
                supabase.postgrest["amis"].insert(insertObj)
                loadLeaderboardData(forceRefresh = true)
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to send friend request", e)
                onResult(false)
            }
        }
    }

    fun inviteToGuild(friendId: String, onResult: (Boolean) -> Unit) {
        val guildId = userGuildId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val insertObj = buildJsonObject {
                    put("guilde_id", guildId)
                    put("destinataire_id", friendId)
                    put("statut", "en_attente")
                }
                supabase.postgrest["guilde_invitations"].insert(insertObj)
                fetchGuildInvitations()
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Failed to invite to guild", e)
                onResult(false)
            }
        }
    }
}
