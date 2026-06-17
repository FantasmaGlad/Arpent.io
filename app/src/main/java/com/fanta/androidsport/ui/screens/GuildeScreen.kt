package com.fanta.androidsport.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.ui.theme.NeonVolt
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.CapsuleTabSelector
import com.fanta.androidsport.ui.components.ColorWheel
import com.fanta.androidsport.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.*

@Composable
fun GuildeScreen(
    isActive: Boolean = false,
    userId: String,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Check if the user is anonymous
    val isAnonymous = remember {
        supabase.auth.currentUserOrNull()?.email.isNullOrEmpty()
    }
    
    if (isAnonymous) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = NeonVolt
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Rejoignez la communauté !",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La demande d'ami et rejoindre un clan ne sont possibles que si un compte est créé.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBackToLogin,
                colors = ButtonDefaults.buttonColors(containerColor = NeonVolt, contentColor = Color.Black),
                shape = RoundedCornerShape(50)
            ) {
                Text("CRÉER UN COMPTE", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // Tabs: 0 = AMIS, 1 = MON CLAN, 2 = CLANS
    var selectedTab by remember { mutableStateOf(0) }
    
    // Friends state
    var friendPseudoInput by remember { mutableStateOf("") }
    var friendsList by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    var pendingRequests by remember { mutableStateOf<List<PendingRequestItem>>(emptyList()) }
    var suggestedFriends by remember { mutableStateOf<List<ProximitySuggestion>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isFriendsLoading by remember { mutableStateOf(true) }
    
    // Clan state
    var clanId by remember { mutableStateOf<String?>(null) }
    var clanNom by remember { mutableStateOf<String?>(null) }
    var clanCouleur by remember { mutableStateOf<String?>(null) }
    var clanAvatar by remember { mutableStateOf<String?>(null) }
    var clanMembers by remember { mutableStateOf<List<ClanMember>>(emptyList()) }
    
    // Clan creation/joining forms
    var newClanName by remember { mutableStateOf("") }
    var newClanColor by remember { mutableStateOf("#CCFF00") }
    var newClanAvatarBase64 by remember { mutableStateOf<String?>(null) }
    var clanSearchQuery by remember { mutableStateOf("") }
    var allClansList by remember { mutableStateOf<List<ClanItem>>(emptyList()) }
    var isClanLoading by remember { mutableStateOf(true) }

    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            newClanAvatarBase64 = it.toString()
        }
    }

    fun loadFriendsData() {
        scope.launch(Dispatchers.IO) {
            try {
                // Fetch friends
                val res = supabase.postgrest["amis"].select {
                    filter {
                        or {
                            eq("demandeur_id", userId)
                            eq("destinataire_id", userId)
                        }
                    }
                }
                
                val array = kotlinx.serialization.json.Json.parseToJsonElement(res.data) as? kotlinx.serialization.json.JsonArray
                val friends = mutableListOf<FriendItem>()
                val pending = mutableListOf<PendingRequestItem>()
                val otherUserIds = mutableListOf<String>()
                val relationMap = mutableMapOf<String, Pair<String, String>>()
                
                array?.forEach { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val relId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                    val demId = obj["demandeur_id"]?.jsonPrimitive?.content ?: return@forEach
                    val destId = obj["destinataire_id"]?.jsonPrimitive?.content ?: return@forEach
                    val statut = obj["statut"]?.jsonPrimitive?.content ?: "en_attente"
                    
                    if (demId == userId) {
                        otherUserIds.add(destId)
                        relationMap[destId] = Pair(statut, relId)
                    } else {
                        otherUserIds.add(demId)
                        relationMap[demId] = Pair(statut, relId)
                    }
                }
                
                if (otherUserIds.isNotEmpty()) {
                    val profilesRes = supabase.postgrest["profiles"].select {
                        filter {
                            isIn("id", otherUserIds)
                        }
                    }
                    val profArray = kotlinx.serialization.json.Json.parseToJsonElement(profilesRes.data) as? kotlinx.serialization.json.JsonArray
                    profArray?.forEach { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val pId = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                        
                        val (statut, relId) = relationMap[pId] ?: Pair("en_attente", "")
                        if (statut == "accepte") {
                            friends.add(FriendItem(pId, pseudo, avatar, color))
                        } else {
                            val isDem = array?.any { 
                                val o = it as? kotlinx.serialization.json.JsonObject
                                o?.get("demandeur_id")?.jsonPrimitive?.content == pId
                            } ?: false
                            if (isDem) {
                                pending.add(PendingRequestItem(relId, pId, pseudo, avatar))
                            }
                        }
                    }
                }
                
                // Fetch suggestions by proximity (Max 50 km)
                val suggestionsParams = kotlinx.serialization.json.buildJsonObject {
                    put("p_utilisateur_id", kotlinx.serialization.json.JsonPrimitive(userId))
                    put("p_max_distance_meters", kotlinx.serialization.json.JsonPrimitive(50000.0))
                }
                val suggestionsRes = supabase.postgrest.rpc("suggerer_amis_proximite", suggestionsParams)
                val suggestionsArray = kotlinx.serialization.json.Json.parseToJsonElement(suggestionsRes.data) as? kotlinx.serialization.json.JsonArray
                val suggestionsList = suggestionsArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val sId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val sPseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                    val sAvatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val sColor = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                    val sDist = obj["distance_meters"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    ProximitySuggestion(sId, sPseudo, sAvatar, sColor, sDist)
                } ?: emptyList()

                // Filter out users who are already friends or have pending relations
                val filterOutIds = otherUserIds.toSet() + setOf(userId)
                val finalSuggestionsList = suggestionsList.filter { it.id !in filterOutIds }
                
                withContext(Dispatchers.Main) {
                    friendsList = friends
                    pendingRequests = pending
                    suggestedFriends = finalSuggestionsList
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error loading friends data", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isFriendsLoading = false
                }
            }
        }
    }

    fun loadClanData() {
        scope.launch(Dispatchers.IO) {
            try {
                // Get current profile clan
                val profileRes = supabase.postgrest["profiles"].select {
                    filter { eq("id", userId) }
                }
                val profileArray = kotlinx.serialization.json.Json.parseToJsonElement(profileRes.data) as? kotlinx.serialization.json.JsonArray
                val profileObj = profileArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val uClanId = profileObj?.get("guilde_id")?.jsonPrimitive?.contentOrNull
                
                if (uClanId != null) {
                    // Fetch guild details
                    val guildRes = supabase.postgrest["guildes"].select {
                        filter { eq("id", uClanId) }
                    }
                    val guildArray = kotlinx.serialization.json.Json.parseToJsonElement(guildRes.data) as? kotlinx.serialization.json.JsonArray
                    val guildObj = guildArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                    val nom = guildObj?.get("nom")?.jsonPrimitive?.contentOrNull ?: "Mon Clan"
                    val col = guildObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull ?: "#CCFF00"
                    val av = guildObj?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                    
                    // Fetch members
                    val membersRes = supabase.postgrest["profiles"].select {
                        filter { eq("guilde_id", uClanId) }
                    }
                    val membersArray = kotlinx.serialization.json.Json.parseToJsonElement(membersRes.data) as? kotlinx.serialization.json.JsonArray
                    val members = membersArray?.mapNotNull { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        ClanMember(id, pseudo, avatar)
                    } ?: emptyList()
                    
                    withContext(Dispatchers.Main) {
                        clanId = uClanId
                        clanNom = nom
                        clanCouleur = col
                        clanAvatar = av
                        clanMembers = members
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        clanId = null
                    }
                    // Fetch all existing guilds
                    val allRes = supabase.postgrest["guildes"].select()
                    val allArray = kotlinx.serialization.json.Json.parseToJsonElement(allRes.data) as? kotlinx.serialization.json.JsonArray
                    val clans = allArray?.mapNotNull { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: "Clan"
                        val col = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#CCFF00"
                        val av = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        ClanItem(id, nom, col, av)
                    } ?: emptyList()
                    
                    withContext(Dispatchers.Main) {
                        allClansList = clans
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error loading clan data", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isClanLoading = false
                }
            }
        }
    }

    LaunchedEffect(isActive, selectedTab) {
        if (!isActive) return@LaunchedEffect
        if (selectedTab == 0) {
            if (friendsList.isEmpty()) {
                isFriendsLoading = true
            }
            loadFriendsData()
        } else {
            if (clanId == null && allClansList.isEmpty() && clanMembers.isEmpty()) {
                isClanLoading = true
            }
            loadClanData()
        }
    }

    val guildeColorScheme = lightColorScheme(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.85f),
        onSurface = Color.Black,
        surfaceVariant = Color.White.copy(alpha = 0.95f),
        onSurfaceVariant = Color.Black,
        secondaryContainer = NeonVolt.copy(alpha = 0.15f),
        onSecondaryContainer = Color.Black
    )

    MaterialTheme(colorScheme = guildeColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(16.dp)
        ) {
            CapsuleTabSelector(
                tabs = listOf("AMIS", "MON CLAN", "CLANS"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedTab == 0) {
                // AMIS TAB
                if (isFriendsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonVolt)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Friend request form
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "AJOUTER UN AMI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = Color.Black.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = friendPseudoInput,
                                            onValueChange = { friendPseudoInput = it; searchError = null },
                                            placeholder = { Text("Pseudonyme", color = Color.Black.copy(alpha = 0.4f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(50),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White.copy(alpha = 0.95f),
                                                unfocusedContainerColor = Color.White.copy(alpha = 0.7f),
                                                focusedBorderColor = NeonVolt,
                                                unfocusedBorderColor = Color.Black.copy(alpha = 0.06f),
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                            onClick = {
                                                if (friendPseudoInput.trim().isEmpty()) return@Button
                                                searchError = null
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val targetRes = supabase.postgrest["profiles"].select {
                                                            filter { eq("pseudonyme", friendPseudoInput.trim()) }
                                                        }
                                                        val targetArray = kotlinx.serialization.json.Json.parseToJsonElement(targetRes.data) as? kotlinx.serialization.json.JsonArray
                                                        val targetObj = targetArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                                                        val targetId = targetObj?.get("id")?.jsonPrimitive?.content
                                                        
                                                        if (targetId == null) {
                                                            withContext(Dispatchers.Main) {
                                                                searchError = "Joueur introuvable."
                                                            }
                                                            return@launch
                                                        }
                                                        if (targetId == userId) {
                                                            withContext(Dispatchers.Main) {
                                                                searchError = "Vous ne pouvez pas vous ajouter vous-même."
                                                            }
                                                            return@launch
                                                        }
                                                        
                                                        supabase.postgrest["amis"].insert(
                                                            mapOf(
                                                                "demandeur_id" to userId,
                                                                "destinataire_id" to targetId,
                                                                "statut" to "en_attente"
                                                            )
                                                        )
                                                        withContext(Dispatchers.Main) {
                                                            friendPseudoInput = ""
                                                            Toast.makeText(context, "Demande d'ami envoyée !", Toast.LENGTH_SHORT).show()
                                                            loadFriendsData()
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            searchError = "Une demande est déjà en cours ou existe déjà."
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1318), contentColor = NeonVolt),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                        ) {
                                            Text("Ajouter", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (searchError != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(searchError!!, color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        
                        // Proximity suggestions
                        if (suggestedFriends.isNotEmpty()) {
                            item {
                                Text(
                                    text = "JOUEURS À PROXIMITÉ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(suggestedFriends) { suggestion ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val pColor = try { Color(android.graphics.Color.parseColor(suggestion.empireColor)) } catch (_: Exception) { NeonVolt }
                                        AvatarImage(
                                            avatarUrl = suggestion.avatarUrl,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, pColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.pseudo,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                            val distStr = if (suggestion.distanceMeters >= 1000.0) {
                                                "À %.1f km".format(suggestion.distanceMeters / 1000.0)
                                            } else {
                                                "À ${suggestion.distanceMeters.toInt()} m"
                                            }
                                            Text(
                                                text = distStr,
                                                fontSize = 11.sp,
                                                color = Color.Black.copy(alpha = 0.5f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        supabase.postgrest["amis"].insert(
                                                            mapOf(
                                                                "demandeur_id" to userId,
                                                                "destinataire_id" to suggestion.id,
                                                                "statut" to "en_attente"
                                                            )
                                                        )
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Demande envoyée !", Toast.LENGTH_SHORT).show()
                                                            loadFriendsData()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Arpent", "Failed to send proximity friend request", e)
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1318), contentColor = NeonVolt),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text("Ajouter", color = NeonVolt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Pending requests list
                        if (pendingRequests.isNotEmpty()) {
                            item {
                                Text(
                                    text = "DEMANDES EN ATTENTE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(pendingRequests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            avatarUrl = req.avatarUrl,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = req.pseudo,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        supabase.postgrest["amis"].update(
                                                            mapOf("statut" to "accepte")
                                                        ) {
                                                            filter { eq("id", req.id) }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Demande acceptée !", Toast.LENGTH_SHORT).show()
                                                            loadFriendsData()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Arpent", "Failed to accept friend", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F1318).copy(alpha = 0.05f))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Accepter", tint = Color(0xFF00C853))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        supabase.postgrest["amis"].delete {
                                                            filter { eq("id", req.id) }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Demande refusée.", Toast.LENGTH_SHORT).show()
                                                            loadFriendsData()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Arpent", "Failed to decline friend", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F1318).copy(alpha = 0.05f))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Refuser", tint = Color(0xFFD50000))
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Friends list
                        item {
                            Text(
                                text = "MES AMIS (${friendsList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        if (friendsList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Vous n'avez pas encore d'amis.", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(friendsList) { friend ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            avatarUrl = friend.avatarUrl,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(text = friend.pseudo, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(try { Color(android.graphics.Color.parseColor(friend.color)) } catch(_: Exception) { Color.Green })
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = "Empire", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // MON CLAN TAB
                if (isClanLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonVolt)
                    }
                } else if (clanId != null) {
                    // User belongs to a clan
                    val parsedClanColor = remember(clanCouleur) {
                        try { Color(android.graphics.Color.parseColor(clanCouleur)) } catch (_: Exception) { NeonVolt }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1318)),
                                border = BorderStroke(1.dp, parsedClanColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = clanAvatar,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, parsedClanColor, CircleShape),
                                        placeholderColor = parsedClanColor,
                                        placeholderIcon = Icons.Default.Shield
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(text = clanNom ?: "", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "${clanMembers.size} membre(s)", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        
                        item {
                            Text(
                                text = "MEMBRES DU CLAN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        
                        items(clanMembers) { member ->
                            val isMe = member.id == userId
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isMe) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.9f)),
                                border = if (isMe) BorderStroke(1.5.dp, NeonVolt) else BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = member.avatarUrl,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, if (isMe) NeonVolt else Color.Black.copy(alpha = 0.1f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = member.pseudo,
                                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                                        color = Color.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isMe) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF0F1318))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "VOUS",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonVolt
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            supabase.postgrest["profiles"].update(
                                                mapOf("guilde_id" to null)
                                            ) {
                                                filter { eq("id", userId) }
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Vous avez quitté le clan.", Toast.LENGTH_SHORT).show()
                                                clanId = null
                                                loadClanData()
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("Arpent", "Failed to leave clan", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFEBEE),
                                    contentColor = Color(0xFFC62828)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("QUITTER LE CLAN", fontWeight = FontWeight.Bold, color = Color(0xFFC62828), letterSpacing = 1.sp)
                            }
                        }
                    }
                } else {
                    // User has no clan: show creation form
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "CRÉER UN CLAN",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        letterSpacing = 1.sp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F1318).copy(alpha = 0.08f))
                                                .clickable { imageLauncher.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AvatarImage(
                                                avatarUrl = newClanAvatarBase64,
                                                modifier = Modifier.fillMaxSize(),
                                                placeholderColor = Color(android.graphics.Color.parseColor(newClanColor)),
                                                placeholderIcon = Icons.Default.Shield
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Button(
                                            onClick = { imageLauncher.launch("image/*") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF0F1318).copy(alpha = 0.08f),
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text("Choisir logo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedTextField(
                                        value = newClanName,
                                        onValueChange = { newClanName = it },
                                        placeholder = { Text("Nom du clan", color = Color.Black.copy(alpha = 0.4f)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(50),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White.copy(alpha = 0.95f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.7f),
                                            focusedBorderColor = NeonVolt,
                                            unfocusedBorderColor = Color.Black.copy(alpha = 0.06f)
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Couleur du clan",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black.copy(alpha = 0.6f),
                                            modifier = Modifier.weight(1f)
                                        )
                                        val parsedColor = remember(newClanColor) {
                                            try { Color(android.graphics.Color.parseColor(newClanColor)) } catch (_: Exception) { Color(0xFFCCFF00) }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(parsedColor)
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val parsedColorForWheel = remember(newClanColor) {
                                        try { Color(android.graphics.Color.parseColor(newClanColor)) } catch (_: Exception) { Color(0xFFCCFF00) }
                                    }
                                    ColorWheel(
                                        selectedColor = parsedColorForWheel,
                                        onColorSelected = { color ->
                                            val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                                            newClanColor = hex
                                        },
                                        modifier = Modifier
                                            .size(160.dp)
                                            .align(Alignment.CenterHorizontally)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            if (newClanName.trim().isEmpty()) return@Button
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val newGuildRes = supabase.postgrest["guildes"].insert(
                                                        mapOf(
                                                            "nom" to newClanName.trim(),
                                                            "couleur_hex" to newClanColor,
                                                            "avatar_url" to null,
                                                            "chef_id" to userId
                                                        )
                                                    ) {
                                                        select()
                                                    }
                                                    val guildArray = kotlinx.serialization.json.Json.parseToJsonElement(newGuildRes.data) as? kotlinx.serialization.json.JsonArray
                                                    val guildObj = guildArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                                                    val createdGuildId = guildObj?.get("id")?.jsonPrimitive?.content
                                                    
                                                    if (createdGuildId != null) {
                                                        var finalAvatarUrl: String? = null
                                                        val localUriStr = newClanAvatarBase64
                                                        if (localUriStr != null && (localUriStr.startsWith("content://") || localUriStr.startsWith("file://"))) {
                                                            try {
                                                                val bytes = context.contentResolver.openInputStream(android.net.Uri.parse(localUriStr))?.use { it.readBytes() }
                                                                 if (bytes != null) {
                                                                    val bucket = supabase.storage.from("Images")
                                                                    val filename = "guild_${createdGuildId}.jpg"
                                                                    bucket.upload(filename, bytes) {
                                                                        upsert = true
                                                                    }
                                                                    val publicUrl = bucket.publicUrl(filename)
                                                                    finalAvatarUrl = "$publicUrl?t=${System.currentTimeMillis()}"
                                                                    
                                                                    supabase.postgrest["guildes"].update(
                                                                        mapOf("avatar_url" to finalAvatarUrl)
                                                                    ) {
                                                                        filter { eq("id", createdGuildId) }
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                android.util.Log.e("Arpent", "Failed to upload guild avatar", e)
                                                            }
                                                        }

                                                        supabase.postgrest["profiles"].update(
                                                            mapOf("guilde_id" to createdGuildId)
                                                        ) {
                                                            filter { eq("id", userId) }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Clan créé avec succès !", Toast.LENGTH_SHORT).show()
                                                            loadClanData()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Ce nom de clan est déjà pris.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonVolt, contentColor = Color.Black),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Text("CRÉER LE CLAN", fontWeight = FontWeight.Bold, color = Color.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Pour rejoindre un clan existant, allez sur l'onglet 'CLANS'.",
                                    color = Color.Black.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // CLANS TAB (Search & Explore clans)
                if (isClanLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonVolt)
                    }
                } else {
                    val filteredClans = remember(allClansList, clanSearchQuery) {
                        if (clanSearchQuery.trim().isEmpty()) {
                            allClansList
                        } else {
                            allClansList.filter { it.nom.contains(clanSearchQuery.trim(), ignoreCase = true) }
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        OutlinedTextField(
                            value = clanSearchQuery,
                            onValueChange = { clanSearchQuery = it },
                            placeholder = { Text("Rechercher un clan...", color = Color.Black.copy(alpha = 0.4f)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black.copy(alpha = 0.4f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedContainerColor = Color.White.copy(alpha = 0.95f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.7f),
                                focusedBorderColor = NeonVolt,
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.06f)
                            )
                        )
                        
                        if (filteredClans.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucun clan trouvé.", color = Color.Black.copy(alpha = 0.4f), fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredClans) { clan ->
                                    val parsedCColor = remember(clan.color) {
                                        try { Color(android.graphics.Color.parseColor(clan.color)) } catch (_: Exception) { NeonVolt }
                                    }
                                    val isMyClan = clanId == clan.id
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                                        border = if (isMyClan) BorderStroke(1.5.dp, NeonVolt) else BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AvatarImage(
                                                avatarUrl = clan.avatarUrl,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, if (isMyClan) NeonVolt else Color.Black.copy(alpha = 0.1f), CircleShape),
                                                placeholderColor = parsedCColor,
                                                placeholderIcon = Icons.Default.Shield
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = clan.nom,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (clanId == null) {
                                                val isCColorLight = (parsedCColor.red * 0.299f + parsedCColor.green * 0.587f + parsedCColor.blue * 0.114f) > 0.6f
                                                Button(
                                                    onClick = {
                                                        scope.launch(Dispatchers.IO) {
                                                            try {
                                                                supabase.postgrest["profiles"].update(
                                                                    mapOf("guilde_id" to clan.id)
                                                                ) {
                                                                    filter { eq("id", userId) }
                                                                }
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "Vous avez rejoint le clan ${clan.nom} !", Toast.LENGTH_SHORT).show()
                                                                    loadClanData()
                                                                    // Switch to MON CLAN tab to see it
                                                                    selectedTab = 1
                                                                }
                                                            } catch (e: Exception) {
                                                                android.util.Log.e("Arpent", "Failed to join clan", e)
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = parsedCColor,
                                                        contentColor = if (isCColorLight) Color.Black else Color.White
                                                    ),
                                                    shape = RoundedCornerShape(50),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Rejoindre", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (isMyClan) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(50))
                                                        .background(Color(0xFF0F1318))
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "VOTRE CLAN",
                                                        fontWeight = FontWeight.Bold,
                                                        color = NeonVolt,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
