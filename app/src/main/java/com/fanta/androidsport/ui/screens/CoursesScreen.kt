package com.fanta.androidsport.ui.screens

import android.widget.Toast
import com.fanta.androidsport.R
import com.google.android.gms.location.LocationServices
import com.fanta.androidsport.ui.theme.BrandGreen
import com.fanta.androidsport.ui.theme.SportAndroidTheme
import com.fanta.androidsport.ui.theme.ThemeManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.data.model.CourseCommentaire
import com.fanta.androidsport.data.model.CourseReaction
import com.fanta.androidsport.data.model.FeedCourseItem
import com.fanta.androidsport.data.model.GPSPoint
import com.fanta.androidsport.BuildConfig
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import coil.compose.AsyncImage
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
fun CoursesScreen(
    userId: String,
    isActive: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedCourses by remember { mutableStateOf<List<FeedCourseItem>>(emptyList()) }
    var friendsStatusMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var userLocation by remember { mutableStateOf<android.location.Location?>(null) }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    userLocation = loc
                }
            }
        }
    }

    var selectedDetailCourse by remember { mutableStateOf<FeedCourseItem?>(null) }
    var showDeleteConfirmCourseId by remember { mutableStateOf<String?>(null) }

    fun loadFeed() {
        scope.launch(Dispatchers.IO) {
            try {
                // Fetch friends to know the relationship status
                val amisRes = supabase.postgrest["amis"].select {
                    filter {
                        or {
                            eq("demandeur_id", userId)
                            eq("destinataire_id", userId)
                        }
                    }
                }
                val amisArray = kotlinx.serialization.json.Json.parseToJsonElement(amisRes.data) as? kotlinx.serialization.json.JsonArray
                val tempMap = mutableMapOf<String, String>()
                amisArray?.forEach { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val demId = obj["demandeur_id"]?.jsonPrimitive?.content ?: return@forEach
                    val destId = obj["destinataire_id"]?.jsonPrimitive?.content ?: return@forEach
                    val statut = obj["statut"]?.jsonPrimitive?.content ?: "en_attente"
                    if (statut == "accepte") {
                        val otherId = if (demId == userId) destId else demId
                        tempMap[otherId] = "accepte"
                    } else if (statut == "en_attente") {
                        if (demId == userId) {
                            tempMap[destId] = "en_attente_envoye"
                        } else {
                            tempMap[demId] = "en_attente_recu"
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    friendsStatusMap = tempMap
                }

                val params = kotlinx.serialization.json.buildJsonObject {
                    put("p_utilisateur_id", kotlinx.serialization.json.JsonPrimitive(userId))
                }
                val response = supabase.postgrest.rpc("get_feed_courses", params)
                val fetchedCourses = withContext(Dispatchers.Default) {
                    val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray
                    jsonArray?.mapNotNull { element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val uId = obj["utilisateur_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                        val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                        val empColor = obj["empire_color"]?.jsonPrimitive?.contentOrNull
                        val level = obj["level"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                        val guildeNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                        val guildeCouleur = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                        val dateDebut = obj["date_debut"]?.jsonPrimitive?.contentOrNull ?: ""
                        val dateFin = obj["date_fin"]?.jsonPrimitive?.contentOrNull ?: ""
                        val distanceTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val dureeSecondes = obj["duree_secondes"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val estBouclee = obj["est_bouclee"]?.jsonPrimitive?.booleanOrNull ?: false
                        val vitesseMoyenne = obj["vitesse_moyenne"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val vitesseMax = obj["vitesse_max"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val allureMoyenne = obj["allure_moyenne"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val caloriesEstimees = obj["calories_estimees"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val denivelePositif = obj["denivele_positif"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val deniveleNegatif = obj["denivele_negatif"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val nom = obj["nom"]?.jsonPrimitive?.contentOrNull
                        val legende = obj["legende"]?.jsonPrimitive?.contentOrNull
                        val superficieConquise = obj["superficie_conquise"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val totalSteps = obj["total_steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val averageCadence = obj["average_cadence"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val imageUrl = obj["image_url"]?.jsonPrimitive?.contentOrNull

                        val ptsArray = obj["points_gps"] as? kotlinx.serialization.json.JsonArray
                        val points = ptsArray?.mapNotNull { ptElem ->
                            val ptObj = ptElem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val lon = ptObj["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                            val lat = ptObj["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                            val alt = ptObj["altitude"]?.jsonPrimitive?.doubleOrNull
                            val timeStr = ptObj["timestamp_gps"]?.jsonPrimitive?.contentOrNull
                            GPSPoint(lon, lat, alt, timeStr)
                        } ?: emptyList()

                        val reactsArray = obj["reactions"] as? kotlinx.serialization.json.JsonArray
                        val reactions = reactsArray?.mapNotNull { rElem ->
                            val rObj = rElem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val rId = rObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val rUserId = rObj["utilisateur_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val rPseudo = rObj["pseudonyme"]?.jsonPrimitive?.content ?: "Joueur"
                            val rType = rObj["type_reaction"]?.jsonPrimitive?.content ?: "trident"
                            CourseReaction(rId, rUserId, rPseudo, rType)
                        } ?: emptyList()

                        val commentsArray = obj["commentaires"] as? kotlinx.serialization.json.JsonArray
                        val commentaires = commentsArray?.mapNotNull { cElem ->
                            val cObj = cElem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val cId = cObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val cUserId = cObj["utilisateur_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val cPseudo = cObj["pseudonyme"]?.jsonPrimitive?.content ?: "Joueur"
                            val cAvatar = cObj["avatar_url"]?.jsonPrimitive?.contentOrNull
                            val cContenu = cObj["contenu"]?.jsonPrimitive?.content ?: ""
                            val cDate = cObj["date_creation"]?.jsonPrimitive?.content ?: ""
                            CourseCommentaire(cId, cUserId, cPseudo, cAvatar, cContenu, cDate)
                        } ?: emptyList()

                        FeedCourseItem(
                            id = id,
                            utilisateurId = uId,
                            pseudonyme = pseudo,
                            avatarUrl = avatar,
                            empireColor = empColor,
                            level = level,
                            guildeNom = guildeNom,
                            guildeCouleur = guildeCouleur,
                            dateDebut = dateDebut,
                            dateFin = dateFin,
                            distanceTotale = distanceTotale,
                            dureeSecondes = dureeSecondes,
                            estBouclee = estBouclee,
                            vitesseMoyenne = vitesseMoyenne,
                            vitesseMax = vitesseMax,
                            allureMoyenne = allureMoyenne,
                            caloriesEstimees = caloriesEstimees,
                            denivelePositif = denivelePositif,
                            deniveleNegatif = deniveleNegatif,
                            nom = nom,
                            legende = legende,
                            superficieConquise = superficieConquise,
                            totalSteps = totalSteps,
                            averageCadence = averageCadence,
                            imageUrl = imageUrl,
                            pointsGps = points,
                            reactions = reactions,
                            commentaires = commentaires
                        )
                    } ?: emptyList()
                }
                feedCourses = fetchedCourses
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to load feed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (feedCourses.isEmpty()) {
                        isLoading = false
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    // Pre-fetch data once on composition entry (background warm-up) so the user
    // doesn't see a spinner on first tab visit.
    var hasPreFetched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasPreFetched) {
            hasPreFetched = true
            loadFeed()
        }
    }

    // Refresh when the tab becomes active.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        if (feedCourses.isEmpty()) {
            isLoading = true
        }
        loadFeed()
    }

    fun toggleReaction(courseId: String, type: String, currentReaction: CourseReaction?) {
        scope.launch(Dispatchers.IO) {
            try {
                if (currentReaction != null) {
                    supabase.postgrest["course_reactions"].delete {
                        filter {
                            eq("course_id", courseId)
                            eq("utilisateur_id", userId)
                            eq("type_reaction", type)
                        }
                    }
                } else {
                    val reactObj = kotlinx.serialization.json.buildJsonObject {
                        put("course_id", courseId)
                        put("utilisateur_id", userId)
                        put("type_reaction", type)
                    }
                    supabase.postgrest["course_reactions"].insert(reactObj)
                }
                loadFeed()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to toggle reaction", e)
            }
        }
    }

    fun submitComment(courseId: String, content: String) {
        if (content.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val commentObj = kotlinx.serialization.json.buildJsonObject {
                    put("course_id", courseId)
                    put("utilisateur_id", userId)
                    put("contenu", content)
                }
                supabase.postgrest["course_commentaires"].insert(commentObj)
                loadFeed()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to post comment", e)
            }
        }
    }

    fun deleteCourse(courseId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                supabase.postgrest["courses"].delete {
                    filter {
                        eq("id", courseId)
                        eq("utilisateur_id", userId)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Course supprimée.", Toast.LENGTH_SHORT).show()
                }
                loadFeed()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to delete course", e)
            }
        }
    }

    fun sendFriendRequest(otherUserId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val insertObj = kotlinx.serialization.json.buildJsonObject {
                    put("demandeur_id", userId)
                    put("destinataire_id", otherUserId)
                    put("statut", "en_attente")
                }
                supabase.postgrest["amis"].insert(insertObj)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Demande d'ami envoyée.", Toast.LENGTH_SHORT).show()
                }
                loadFeed()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to send friend request", e)
            }
        }
    }

    SportAndroidTheme(theme = ThemeManager.themeState.value) {
        val mapCenter = remember(userLocation, feedCourses) {
            if (userLocation != null) {
                Pair(userLocation!!.longitude, userLocation!!.latitude)
            } else if (feedCourses.isNotEmpty()) {
                val firstCoursePoints = feedCourses.first().pointsGps
                if (firstCoursePoints.isNotEmpty()) {
                    Pair(firstCoursePoints.first().longitude, firstCoursePoints.first().latitude)
                } else {
                    Pair(2.3522, 48.8566) // Paris default
                }
            } else {
                Pair(2.3522, 48.8566) // Paris default
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Blurred Mapbox Static Map Background (conquest style)
            AsyncImage(
                model = "https://api.mapbox.com/styles/v1/fantasmaglad/cmqe0myj4002c01qr2jd549n8/static/${mapCenter.first},${mapCenter.second},13,0,0/800x1200@2x?access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}&attribution=false&logo=false",
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.30f
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {


                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandGreen)
                    }
                } else if (feedCourses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRun,
                                    contentDescription = null,
                                    tint = BrandGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucune course dans le feed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E1E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ajoutez des amis ou lancez-vous pour commencer la conquête !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6E6E73)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(feedCourses) { course ->
                            var showCommentsSection by remember { mutableStateOf(false) }
                            var showAddCommentField by remember { mutableStateOf(false) }
                            var newCommentText by remember { mutableStateOf("") }

                            FeedCourseCard(
                                course = course,
                                currentUserId = userId,
                                friendStatus = friendsStatusMap[course.utilisateurId],
                                showComments = showCommentsSection,
                                showAddComment = showAddCommentField,
                                newCommentText = newCommentText,
                                onToggleComments = { showCommentsSection = !showCommentsSection },
                                onToggleAddComment = {
                                    showAddCommentField = !showAddCommentField
                                    if (showAddCommentField) showCommentsSection = false
                                },
                                onCommentTextChange = { newCommentText = it },
                                onSendComment = {
                                    submitComment(course.id, newCommentText)
                                    newCommentText = ""
                                    showAddCommentField = false
                                },
                                onReact = { type, current ->
                                    toggleReaction(course.id, type, current)
                                },
                                onViewDetails = {
                                    selectedDetailCourse = course
                                },
                                onDelete = {
                                    showDeleteConfirmCourseId = course.id
                                },
                                onSendFriendRequest = {
                                    sendFriendRequest(course.utilisateurId)
                                }
                            )
                        }
                    }
                }
            }

            // Deletion confirmation
            if (showDeleteConfirmCourseId != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmCourseId = null },
                    title = { Text("Supprimer la course", color = Color(0xFF1E1E1E)) },
                    text = { Text("Êtes-vous sûr de vouloir supprimer cette course ? Cette action est irréversible et supprimera le territoire associé.", color = Color(0xFF6E6E73)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmCourseId?.let { deleteCourse(it) }
                                showDeleteConfirmCourseId = null
                            }
                        ) {
                            Text("SUPPRIMER", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmCourseId = null }) {
                            Text("ANNULER", color = Color(0xFF6E6E73))
                        }
                    },
                    containerColor = Color.White
                )
            }

            // Detail Dialog with Splits
            if (selectedDetailCourse != null) {
                CourseDetailsDialog(
                    course = selectedDetailCourse!!,
                    onDismiss = { selectedDetailCourse = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedCourseCard(
    course: FeedCourseItem,
    currentUserId: String,
    friendStatus: String?,
    showComments: Boolean,
    showAddComment: Boolean,
    newCommentText: String,
    onToggleComments: () -> Unit,
    onToggleAddComment: () -> Unit,
    onCommentTextChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onReact: (String, CourseReaction?) -> Unit,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit,
    onSendFriendRequest: () -> Unit
) {
    // Optimistic like state
    var optimisticLiked by remember(course.id) {
        mutableStateOf(course.reactions.any { it.utilisateur_id == currentUserId && it.type_reaction == "baamix" })
    }
    val userReactionBaamix = course.reactions.firstOrNull { it.utilisateur_id == currentUserId && it.type_reaction == "baamix" }
    val likeScale by animateFloatAsState(
        targetValue = if (optimisticLiked) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    val likeCount = remember(course.reactions, optimisticLiked) {
        val baseCount = course.reactions.count { it.type_reaction == "baamix" }
        val wasLiked = course.reactions.any { it.utilisateur_id == currentUserId && it.type_reaction == "baamix" }
        if (optimisticLiked && !wasLiked) {
            baseCount + 1
        } else if (!optimisticLiked && wasLiked) {
            maxOf(0, baseCount - 1)
        } else {
            baseCount
        }
    }

    // Couleur du thème profil de l'utilisateur courant (boutons, accents UI)
    val themeColor = MaterialTheme.colorScheme.primary
    // Couleur empire de l'auteur de la carte (gradient de fond)
    val empireColor = try {
        Color(android.graphics.Color.parseColor(course.empireColor ?: "#00875A"))
    } catch (_: Exception) { themeColor }
    val cardGradient = empireColor.copy(alpha = 0.13f) // voile uniforme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // 1. The Main Premium Card with dynamic gradient
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D)) // fond sombre uniforme
            ) {
                // Voile empire uniforme
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(cardGradient)
                )
                // Tracé GPS flou en arrière-plan à 30%
                if (course.pointsGps.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .alpha(0.30f)
                            .blur(12.dp)
                    ) {
                        RoutePreviewCanvas(
                            points = course.pointsGps,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                // Header: User Info & Friend button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarImage(
                            avatarUrl = course.avatarUrl,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = course.pseudonyme,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            if (course.utilisateurId != currentUserId) {
                                Spacer(modifier = Modifier.width(8.dp))
                                val isFriend = friendStatus == "accepte"
                                val text = when (friendStatus) {
                                    "accepte" -> "Ami"
                                    "en_attente_envoye" -> "Invité"
                                    "en_attente_recu" -> "Répondre"
                                    else -> "+ Ami"
                                }
                                Text(
                                    text = "• $text",
                                    color = if (isFriend) empireColor else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable(enabled = friendStatus == null) {
                                        onSendFriendRequest()
                                    }
                                )
                            }
                        }
                        if (!course.guildeNom.isNullOrEmpty()) {
                            Text(
                                text = course.guildeNom,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Niveau ${course.level}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (course.utilisateurId == currentUserId) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer la course",
                                    tint = Color.Red.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Two-column Content Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Image Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(empireColor.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, empireColor.copy(alpha = 0.35f)), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!course.imageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = course.imageUrl,
                                    contentDescription = "Image de la course",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = empireColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Arpent.io",
                                        color = empireColor.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val desc = course.legende ?: course.nom ?: "Course sans description"
                            Text(
                                text = desc,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Like Heart Button (optimistic)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (optimisticLiked) themeColor.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.10f)
                                    )
                                    .border(
                                        BorderStroke(1.dp,
                                            if (optimisticLiked) themeColor.copy(alpha = 0.6f)
                                            else Color.White.copy(alpha = 0.15f)
                                        ),
                                        CircleShape
                                    )
                                    .clickable {
                                        optimisticLiked = !optimisticLiked
                                        onReact("baamix", userReactionBaamix)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baamix_like),
                                    contentDescription = "Like",
                                    tint = if (optimisticLiked) themeColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp).scale(likeScale)
                                )
                            }
                            if (likeCount > 0) {
                                Text(
                                    text = likeCount.toString(),
                                    color = if (optimisticLiked) themeColor else Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Add Comment Button (ouvre le champ rapide inline)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (showAddComment) empireColor.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.10f)
                                    )
                                    .border(
                                        BorderStroke(1.dp,
                                            if (showAddComment) empireColor.copy(alpha = 0.5f)
                                            else Color.White.copy(alpha = 0.15f)
                                        ),
                                        CircleShape
                                    )
                                    .clickable { onToggleAddComment() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddComment,
                                    contentDescription = "Ajouter un commentaire",
                                    tint = if (showAddComment) empireColor else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Quick Add Comment Field (visible si showAddComment) - sans fond
                        if (showAddComment) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = newCommentText,
                                    onValueChange = onCommentTextChange,
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(empireColor),
                                    decorationBox = { innerTextField ->
                                        if (newCommentText.isEmpty()) {
                                            Text(
                                                text = "Écrire un commentaire...",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                IconButton(
                                    onClick = onSendComment,
                                    enabled = newCommentText.isNotBlank(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Envoyer",
                                        tint = if (newCommentText.isNotBlank()) empireColor else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Statistics Block
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Distance
                            Text(
                                text = "%.2f km".format(course.distanceTotale),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            // Duration
                            val min = (course.dureeSecondes / 60).toInt()
                            val sec = (course.dureeSecondes % 60).toInt()
                            Text(
                                text = "%d min %02d sec".format(min, sec),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            // Pace
                            val allureMin = course.allureMoyenne.toInt()
                            val allureSec = ((course.allureMoyenne - allureMin) * 60).toInt()
                            val allureStr = if (course.allureMoyenne > 0) "%d:%02d mins/km".format(allureMin, allureSec) else "--:-- mins/km"
                            Text(
                                text = allureStr,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            // Elevation
                            Text(
                                text = "+ ${course.denivelePositif.toInt()} m d +",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            // Area
                            val areaKm2 = course.superficieConquise / 1e6
                            Text(
                                text = "+ %.3f km2".format(areaKm2),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GPS Track Map Preview Card (Mapbox + tracé)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, empireColor.copy(alpha = 0.35f)), RoundedCornerShape(16.dp))
                                .clickable { onViewDetails() }
                        ) {
                            if (course.pointsGps.isNotEmpty()) {
                                RouteMapPreview(
                                    points = course.pointsGps,
                                    empireColor = empireColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF111111)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun tracé",
                                        color = empireColor.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Comments section (déroulée via le bouton comment)
                if (showComments) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (course.commentaires.isEmpty()) {
                        Text(
                            text = "Aucun commentaire — soyez le premier !",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        course.commentaires.forEach { comment ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = comment.pseudonyme,
                                        fontWeight = FontWeight.Bold,
                                        color = empireColor,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val commentDate = try {
                                        val instant = java.time.Instant.parse(comment.date_creation)
                                        val formatter = java.time.format.DateTimeFormatter
                                            .ofPattern("dd MMM à HH:mm")
                                            .withZone(java.time.ZoneId.systemDefault())
                                        formatter.format(instant)
                                    } catch (_: Exception) { comment.date_creation }
                                    Text(text = commentDate, color = Color.Gray, fontSize = 10.sp)
                                }
                                Text(
                                    text = comment.contenu,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            } // close background box
        } // close card

        // Protruding Comment Toggle Button (dérouler/replier les commentaires)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 16.dp)
                .size(width = 60.dp, height = 32.dp)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(empireColor) // couleur uniforme
                .clickable { onToggleComments() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showComments) Icons.Default.ExpandLess else Icons.Default.Comment,
                contentDescription = "Voir les commentaires",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MetricWidget(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color(0xFF6E6E73),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color(0xFF1E1E1E),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RoutePreviewCanvas(
    points: List<GPSPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null
) {
    val traceColor = lineColor ?: BrandGreen
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFFF4F5F7), shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Pas de tracé GPS", color = Color(0xFF6E6E73), fontSize = 12.sp)
        }
        return
    }

    val hexColor = remember(traceColor) {
        try {
            val argb = traceColor.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            String.format("#%02x%02x%02x", r, g, b)
        } catch (e: Exception) {
            "#00E5FF"
        }
    }

    // Build Mapbox Static Image URL with polyline overlay
    val mapboxToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
    val staticMapUrl = remember(points, hexColor) {
        // Build simplified polyline for the path overlay (sample max 80 points for URL length)
        val step = if (points.size > 80) points.size / 80 else 1
        val sampled = points.filterIndexed { i, _ -> i % step == 0 || i == points.size - 1 }
        val pathCoords = sampled.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        val geoJsonPath = "{\"type\":\"Feature\",\"properties\":{\"stroke\":\"$hexColor\",\"stroke-width\":5,\"stroke-opacity\":0.9},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$pathCoords]}}"
        val encodedGeoJson = java.net.URLEncoder.encode(geoJsonPath, "UTF-8").replace("+", "%20")

        "https://api.mapbox.com/styles/v1/fantasmaglad/cmqe0myj4002c01qr2jd549n8/static/" +
            "geojson($encodedGeoJson)/" +
            "auto/600x260@2x" +
            "?access_token=$mapboxToken&attribution=false&logo=false"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Mapbox static map background
        AsyncImage(
            model = staticMapUrl,
            contentDescription = "Carte du tracé",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Vue parallèle style Strava : carte Mapbox statique en fond + tracé GPS en surimpression coloré.
 */
@Composable
fun RouteMapPreview(
    points: List<GPSPoint>,
    empireColor: Color,
    modifier: Modifier = Modifier
) {
    RoutePreviewCanvas(
        points = points,
        modifier = modifier,
        lineColor = empireColor
    )
}

data class SplitItem(
    val distanceMeters: Int,
    val timeFormatted: String
)

fun calculateSplits(points: List<GPSPoint>): List<SplitItem> {
    if (points.size < 2) return emptyList()

    val splits = mutableListOf<SplitItem>()
    val targets = listOf(100, 400, 800, 1000, 2000, 5000, 10000)
    var currentTargetIndex = 0

    var accumulatedDistance = 0.0
    var startTime: Long? = null

    val parsedTimes = points.map { pt ->
        if (pt.timestamp_gps == null) return@map null
        val cleanTimestamp = pt.timestamp_gps.replace(" ", "T")
        try {
            java.time.Instant.parse(cleanTimestamp).toEpochMilli()
        } catch (e: Exception) {
            try {
                val formatter = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                java.time.ZonedDateTime.parse(cleanTimestamp, formatter).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    startTime = parsedTimes.firstOrNull { it != null }

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]

        val dist = calculateDistanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        accumulatedDistance += dist

        while (currentTargetIndex < targets.size && accumulatedDistance >= targets[currentTargetIndex]) {
            val target = targets[currentTargetIndex]
            val currTime = parsedTimes[i]
            val elapsedMs = if (startTime != null && currTime != null) currTime - startTime else null

            val timeStr = if (elapsedMs != null) {
                val sec = (elapsedMs / 1000) % 60
                val min = (elapsedMs / 1000) / 60
                "%d:%02d".format(min, sec)
            } else {
                val estimatedSec = (target / 3.0).toInt()
                "%d:%02d".format(estimatedSec / 60, estimatedSec % 60)
            }

            splits.add(SplitItem(target, timeStr))
            currentTargetIndex++
        }
    }

    return splits
}

fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
            Math.cos(phi1) * Math.cos(phi2) *
            Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return r * c
}

@Composable
fun CourseDetailsDialog(
    course: FeedCourseItem,
    onDismiss: () -> Unit
) {
    val splits = remember(course.pointsGps) { calculateSplits(course.pointsGps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = course.nom ?: "Course Arpent.io",
                color = Color(0xFF1E1E1E),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Large canvas path
                RoutePreviewCanvas(
                    points = course.pointsGps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Temps de passage (Splits)",
                    color = BrandGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (splits.isEmpty()) {
                    Text(
                        text = "Aucun temps de passage disponible (course trop courte).",
                        color = Color(0xFF6E6E73),
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(splits) { split ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF4F5F7), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (split.distanceMeters >= 1000) "${split.distanceMeters / 1000.0} km" else "${split.distanceMeters} m",
                                    color = Color(0xFF1E1E1E),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = split.timeFormatted,
                                    color = BrandGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("FERMER", color = BrandGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 16.dp,
    verticalGap: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        
        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0
        
        placeables.forEach { placeable ->
            val horizontalGapPx = horizontalGap.roundToPx()
            if (currentRowWidth + placeable.width + (if (currentRow.isEmpty()) 0 else horizontalGapPx) <= layoutWidth) {
                currentRow.add(placeable)
                currentRowWidth += placeable.width + (if (currentRow.size == 1) 0 else horizontalGapPx)
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        
        val verticalGapPx = verticalGap.roundToPx()
        val totalHeight = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 } +
                (if (rows.size > 1) (rows.size - 1) * verticalGapPx else 0)
        
        layout(layoutWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + horizontalGap.roundToPx()
                }
                y += rowHeight + verticalGapPx
            }
        }
    }
}

