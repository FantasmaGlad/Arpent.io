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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import com.fanta.androidsport.data.model.CourseCommentaire
import com.fanta.androidsport.data.model.CourseReaction
import com.fanta.androidsport.data.model.FeedCourseItem
import com.fanta.androidsport.data.model.GPSPoint
import com.fanta.androidsport.BuildConfig
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.components.TerritoryMapBackground
import com.fanta.androidsport.utils.fetchPlayerTerritoryPolygons
import com.fanta.androidsport.utils.getPolygonArea
import com.fanta.androidsport.utils.getPolygonCentroid
import com.mapbox.geojson.Point
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

// Temporary switch: likes and comments are hidden from the courses feed for now.
// Flip back to true to restore the like button, comment field and comment list.
private const val SHOW_LIKES_AND_COMMENTS = false

@Composable
fun CoursesScreen(
    userId: String,
    isActive: Boolean,
    onNavigateToTerritory: ((Point) -> Unit)? = null
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

    // System back closes the course details panel instead of leaving the screen
    BackHandler(enabled = selectedDetailCourse != null) {
        selectedDetailCourse = null
    }

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
                    // Remove existing reaction
                    supabase.postgrest["course_reactions"].delete {
                        filter {
                            eq("course_id", courseId)
                            eq("utilisateur_id", userId)
                            eq("type_reaction", type)
                        }
                    }
                    android.util.Log.d("Arpent", "Reaction supprimée: courseId=$courseId type=$type")
                } else {
                    // Insert new reaction — use upsert to handle any duplicate gracefully
                    val reactObj = kotlinx.serialization.json.buildJsonObject {
                        put("course_id", courseId)
                        put("utilisateur_id", userId)
                        put("type_reaction", type)
                    }
                    try {
                        supabase.postgrest["course_reactions"].insert(reactObj) {
                            // onConflict = upsert-like: do nothing on duplicate (unique constraint)
                        }
                        android.util.Log.d("Arpent", "Reaction insérée: courseId=$courseId type=$type userId=$userId")
                    } catch (insertEx: Exception) {
                        // Duplicate reaction already exists — treat as success (already liked)
                        val msg = insertEx.message ?: ""
                        if (msg.contains("23505") || msg.contains("unique", ignoreCase = true) || msg.contains("duplicate", ignoreCase = true)) {
                            android.util.Log.d("Arpent", "Reaction déjà existante (contrainte unique) — ignorée")
                        } else {
                            android.util.Log.e("Arpent", "Erreur insert reaction: ${insertEx.message}", insertEx)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Erreur Like: ${insertEx.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                loadFeed()
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to toggle reaction: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erreur réaction: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
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

    // Fly to the player's biggest current territory (empire), not their live position
    fun locatePlayerEmpire(playerId: String) {
        if (onNavigateToTerritory == null) return
        scope.launch(Dispatchers.IO) {
            try {
                val polygons = fetchPlayerTerritoryPolygons(playerId)
                val largest = polygons.maxByOrNull { getPolygonArea(it) }
                val centroid = largest?.let { getPolygonCentroid(it) }
                withContext(Dispatchers.Main) {
                    if (centroid != null) {
                        onNavigateToTerritory.invoke(centroid)
                    } else {
                        Toast.makeText(context, "Ce joueur n'a pas encore d'empire.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to locate player empire", e)
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
            // Same button-free map background as the profile screen
            if (isActive) {
                TerritoryMapBackground(
                    polygons = emptyList(),
                    empireColor = MaterialTheme.colorScheme.primary,
                    fallbackCenter = Point.fromLngLat(mapCenter.first, mapCenter.second),
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Readability scrim over the map
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 8.dp)
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
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
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
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ajoutez des amis ou lancez-vous pour commencer la conquête !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                },
                                onAvatarClick = {
                                    locatePlayerEmpire(course.utilisateurId)
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
                    title = { Text("Supprimer la course") },
                    text = { Text("Êtes-vous sûr de vouloir supprimer cette course ? Cette action est irréversible et supprimera le territoire associé.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmCourseId?.let { deleteCourse(it) }
                                showDeleteConfirmCourseId = null
                            }
                        ) {
                            Text("SUPPRIMER", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmCourseId = null }) {
                            Text("ANNULER")
                        }
                    }
                )
            }

            // Full-width course details panel, opened over the feed (stays below the app header)
            if (selectedDetailCourse != null) {
                CourseDetailsPanel(
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
    onSendFriendRequest: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
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
            .padding(bottom = 16.dp)
    ) {
        // 1. The Main Premium Card — full screen width, color at 70% opacity over the map
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D).copy(alpha = 0.70f)) // fond sombre translucide
            ) {
                // Voile empire uniforme
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(cardGradient)
                )
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
                    // Avatar — tap to fly to this player's empire on the map
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, CircleShape)
                            .clickable(enabled = onAvatarClick != null) {
                                onAvatarClick?.invoke()
                            },
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

                        if (SHOW_LIKES_AND_COMMENTS) {
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

                    }
                }

                // Full-width Mapbox map under the course trace, Strava style (tap = details)
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                                .background(Color(0xFF111111).copy(alpha = 0.7f)),
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

                // Expandable Comments section (déroulée via le bouton comment)
                if (SHOW_LIKES_AND_COMMENTS && showComments) {
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
        if (SHOW_LIKES_AND_COMMENTS) {
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

data class TrackPoint(
    val point: GPSPoint,
    val distance: Double,
    val time: Long?,
    val elevation: Double,
    val cumulativeDPlus: Double
)

data class DetailedSplit(
    val label: String,
    val splitTimeFormatted: String,
    val dPlusFormatted: String,
    val cumulativeTimeFormatted: String
)

fun parseGpsTimestamp(timestampGps: String?): Long? {
    if (timestampGps.isNullOrBlank()) return null
    val clean = timestampGps.replace(" ", "T")
    return try {
        java.time.Instant.parse(clean).toEpochMilli()
    } catch (e: Exception) {
        try {
            val formatter = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
            java.time.ZonedDateTime.parse(clean, formatter).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            try {
                val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
                java.time.LocalDateTime.parse(clean, formatter).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (e3: Exception) {
                null
            }
        }
    }
}

// Delegates to GeoUtils.calculateDistance (the project's single reference Haversine
// implementation) instead of keeping a second copy that can silently drift from it —
// see structure.md/README known pitfalls.
fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    return com.fanta.androidsport.utils.calculateDistance(
        Point.fromLngLat(lon1, lat1),
        Point.fromLngLat(lon2, lat2)
    )
}

fun getInterpolatedState(trackPoints: List<TrackPoint>, d: Double): InterpolatedState {
    if (trackPoints.isEmpty()) return InterpolatedState(null, 0.0, 0.0)
    if (d <= 0.0) return InterpolatedState(trackPoints.first().time, 0.0, trackPoints.first().elevation)
    val last = trackPoints.last()
    if (d >= last.distance) return InterpolatedState(last.time, last.cumulativeDPlus, last.elevation)

    var low = 0
    var high = trackPoints.size - 1
    while (low < high - 1) {
        val mid = (low + high) / 2
        if (trackPoints[mid].distance < d) {
            low = mid
        } else {
            high = mid
        }
    }
    val p1 = trackPoints[low]
    val p2 = trackPoints[high]
    val denom = p2.distance - p1.distance
    val fraction = if (denom > 0) (d - p1.distance) / denom else 0.0

    val interpolatedTime = if (p1.time != null && p2.time != null) {
        p1.time + ((p2.time - p1.time) * fraction).toLong()
    } else null

    val interpolatedDPlus = p1.cumulativeDPlus + (p2.cumulativeDPlus - p1.cumulativeDPlus) * fraction
    val interpolatedElevation = p1.elevation + (p2.elevation - p1.elevation) * fraction

    return InterpolatedState(interpolatedTime, interpolatedDPlus, interpolatedElevation)
}

data class InterpolatedState(
    val time: Long?,
    val cumulativeDPlus: Double,
    val elevation: Double
)

fun formatDuration(ms: Long?): String {
    if (ms == null) return "--:--"
    val totalSeconds = ms / 1000
    val hr = totalSeconds / 3600
    val min = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return if (hr > 0) {
        "%d:%02d:%02d".format(hr, min, sec)
    } else {
        "%d:%02d".format(min, sec)
    }
}

fun calculateDetailedSplits(trackPoints: List<TrackPoint>): List<DetailedSplit> {
    if (trackPoints.isEmpty()) return emptyList()
    val totalDistance = trackPoints.last().distance
    val splits = mutableListOf<DetailedSplit>()
    var k = 1
    while (true) {
        val dStart = (k - 1) * 1000.0
        if (dStart >= totalDistance) break
        
        val isLast = (k * 1000.0) >= totalDistance
        val dEnd = if (isLast) totalDistance else k * 1000.0
        
        val stateStart = getInterpolatedState(trackPoints, dStart)
        val stateEnd = getInterpolatedState(trackPoints, dEnd)
        
        val splitDist = dEnd - dStart
        if (isLast && splitDist < 5.0 && k > 1) {
            break
        }
        
        val label = if (isLast && splitDist < 995.0) {
            "%.1f".format(dEnd / 1000.0)
        } else {
            k.toString()
        }
        
        val endT = stateEnd.time
        val startT = stateStart.time
        val firstT = trackPoints.firstOrNull()?.time
        
        val splitTimeMs = if (endT != null && startT != null) {
            endT - startT
        } else null
        
        val cumulativeTimeMs = if (endT != null && firstT != null) {
            endT - firstT
        } else null
        
        val dPlusGain = stateEnd.cumulativeDPlus - stateStart.cumulativeDPlus
        
        splits.add(
            DetailedSplit(
                label = label,
                splitTimeFormatted = formatDuration(splitTimeMs),
                dPlusFormatted = "${dPlusGain.toInt()} d+",
                cumulativeTimeFormatted = formatDuration(cumulativeTimeMs)
            )
        )
        
        k++
    }
    return splits
}

fun calculateBestEffort(trackPoints: List<TrackPoint>, targetDistance: Double): Long? {
    if (trackPoints.isEmpty()) return null
    val totalDistance = trackPoints.last().distance
    if (totalDistance < targetDistance) return null
    
    var minTime: Long? = null
    var activeIndex = 0
    
    for (i in trackPoints.indices) {
        val dStart = trackPoints[i].distance
        val dEnd = dStart + targetDistance
        if (dEnd > totalDistance) break
        
        val tStart = trackPoints[i].time ?: continue
        
        while (activeIndex < trackPoints.size - 1 && trackPoints[activeIndex].distance < dEnd) {
            activeIndex++
        }
        
        val p2 = trackPoints[activeIndex]
        val p1 = if (activeIndex > 0) trackPoints[activeIndex - 1] else p2
        
        val denom = p2.distance - p1.distance
        val fraction = if (denom > 0) (dEnd - p1.distance) / denom else 0.0
        
        if (p1.time != null && p2.time != null) {
            val tEnd = p1.time + ((p2.time - p1.time) * fraction).toLong()
            val elapsed = tEnd - tStart
            if (minTime == null || elapsed < minTime) {
                minTime = elapsed
            }
        }
    }
    return minTime
}

@Composable
fun ElevationProfileChart(
    trackPoints: List<TrackPoint>,
    empireColor: Color,
    modifier: Modifier = Modifier
) {
    val sampledPoints = remember(trackPoints) {
        if (trackPoints.size > 150) {
            val step = trackPoints.size / 150
            trackPoints.filterIndexed { index, _ -> index % step == 0 || index == trackPoints.size - 1 }
        } else {
            trackPoints
        }
    }

    if (sampledPoints.size < 2) {
        Box(
            modifier = modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Profil de dénivelé indisponible", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        return
    }

    val elevations = sampledPoints.map { it.elevation }
    val minElev = elevations.minOrNull() ?: 0.0
    val maxElev = elevations.maxOrNull() ?: 0.0
    val elevationSpan = (maxElev - minElev).coerceAtLeast(10.0)
    
    val yMinBound = minElev - (elevationSpan * 0.15)
    val yMaxBound = maxElev + (elevationSpan * 0.15)
    val ySpanAdjusted = yMaxBound - yMinBound
    val totalDistance = trackPoints.last().distance

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 36.dp, vertical = 20.dp)
    ) {
        val width = size.width
        val height = size.height

        val gridLines = 3
        for (i in 0 until gridLines) {
            val yFactor = i.toFloat() / (gridLines - 1)
            val y = yFactor * height
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            
            val valAtGrid = yMaxBound - (yFactor * ySpanAdjusted)
            val paintGrid = Paint().apply {
                color = android.graphics.Color.argb((255 * 0.6f).toInt(), 255, 255, 255)
                textSize = 9.sp.toPx()
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${valAtGrid.toInt()}m",
                -4.dp.toPx(),
                y + 3.dp.toPx(),
                paintGrid
            )
        }

        val paintBottomLeft = Paint().apply {
            color = android.graphics.Color.argb((255 * 0.6f).toInt(), 255, 255, 255)
            textSize = 9.sp.toPx()
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            "0 km",
            0f,
            height + 12.dp.toPx(),
            paintBottomLeft
        )

        val totalKmFormatted = "%.1f km".format(totalDistance / 1000.0)
        val paintBottomRight = Paint().apply {
            color = android.graphics.Color.argb((255 * 0.6f).toInt(), 255, 255, 255)
            textSize = 9.sp.toPx()
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            totalKmFormatted,
            width,
            height + 12.dp.toPx(),
            paintBottomRight
        )

        val path = Path()
        val fillPath = Path()

        sampledPoints.forEachIndexed { index, pt ->
            val xFactor = pt.distance / totalDistance
            val yFactor = (pt.elevation - yMinBound) / ySpanAdjusted
            val px = (xFactor * width).toFloat()
            val py = (height - (yFactor * height)).toFloat()

            if (index == 0) {
                path.moveTo(px, py)
                fillPath.moveTo(px, height)
                fillPath.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fillPath.lineTo(px, py)
            }

            if (index == sampledPoints.size - 1) {
                fillPath.lineTo(px, height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    empireColor.copy(alpha = 0.4f),
                    empireColor.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = height
            )
        )

        drawPath(
            path = path,
            color = empireColor,
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.cornerPathEffect(8.dp.toPx())
            )
        )
    }
}

@Composable
fun CourseDetailsPanel(
    course: FeedCourseItem,
    onDismiss: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val empireColor = try {
        Color(android.graphics.Color.parseColor(course.empireColor ?: "#00875A"))
    } catch (_: Exception) {
        themeColor
    }

    val trackPoints = remember(course.pointsGps) {
        val points = course.pointsGps
        val list = mutableListOf<TrackPoint>()
        var totalDist = 0.0
        var totalDPlus = 0.0
        for (i in points.indices) {
            val pt = points[i]
            val dist = if (i == 0) 0.0 else calculateDistanceMeters(points[i - 1].latitude, points[i - 1].longitude, pt.latitude, pt.longitude)
            totalDist += dist
            
            val elevation = pt.altitude ?: 0.0
            val dPlusGain = if (i == 0) 0.0 else {
                val prevElev = points[i - 1].altitude ?: 0.0
                maxOf(0.0, elevation - prevElev)
            }
            totalDPlus += dPlusGain
            val time = parseGpsTimestamp(pt.timestamp_gps)
            list.add(
                TrackPoint(
                    point = pt,
                    distance = totalDist,
                    time = time,
                    elevation = elevation,
                    cumulativeDPlus = totalDPlus
                )
            )
        }
        list
    }

    val splits = remember(trackPoints) { calculateDetailedSplits(trackPoints) }
    
    val bestEffortDistances = listOf(
        50.0 to "50 m",
        100.0 to "100 m",
        200.0 to "200 m",
        400.0 to "400 m",
        800.0 to "800 m",
        1000.0 to "1000 m",
        2000.0 to "2000 m",
        5000.0 to "5000 m",
        10000.0 to "10 000 m"
    )

    val bestEfforts = remember(trackPoints) {
        bestEffortDistances.map { (dist, label) ->
            label to calculateBestEffort(trackPoints, dist)
        }
    }

    // Full-screen-width panel drawn over the feed; it never overlaps the app header
    // because it lives inside the tab content area.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Consume taps so they don't reach the feed below
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(onBackground.copy(alpha = 0.08f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour au feed",
                        tint = onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.nom ?: "Course sans nom",
                        color = onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "par @${course.pseudonyme}",
                        color = onBackground.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable content — full width of the screen
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Vue du tracé (30% transparency background)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(empireColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, empireColor.copy(alpha = 0.15f)), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Vue du tracé",
                        color = onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    RoutePreviewCanvas(
                        points = course.pointsGps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        lineColor = empireColor
                    )
                }

                // Card 2: Vue du D+ par Km (Altitude/Elevation Profile) (30% transparency background)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(empireColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, empireColor.copy(alpha = 0.15f)), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Vue du D+ par Km",
                        color = onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ElevationProfileChart(
                        trackPoints = trackPoints,
                        empireColor = empireColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }

                // Section 3: Temps de passages par km
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Temps de passages par km",
                        color = onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp)
                    )

                    if (splits.isEmpty()) {
                        Text(
                            text = "Données de splits insuffisantes",
                            color = onBackground.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        // Column Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Km", color = onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Text("Allure/Split", color = onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("D+", color = onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            Text("Cumul", color = onBackground.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                        }

                        splits.forEach { split ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = split.label,
                                    color = onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = split.splitTimeFormatted,
                                    color = onBackground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = split.dPlusFormatted,
                                    color = onBackground.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    text = split.cumulativeTimeFormatted,
                                    color = onBackground.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(70.dp)
                                )
                            }
                            HorizontalDivider(color = onBackground.copy(alpha = 0.1f))
                        }
                    }
                }

                // Section 4: Meilleurs Temps
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Meilleurs Temps",
                        color = onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 12.dp)
                    )

                    bestEfforts.forEach { (label, timeMs) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = onBackground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatDuration(timeMs),
                                color = if (timeMs != null) empireColor else onBackground.copy(alpha = 0.3f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = onBackground.copy(alpha = 0.1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
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

