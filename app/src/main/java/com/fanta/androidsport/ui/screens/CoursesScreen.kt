package com.fanta.androidsport.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.components.AvatarImage
import com.fanta.androidsport.ui.theme.NeonVolt
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
    var isLoading by remember { mutableStateOf(true) }

    var selectedDetailCourse by remember { mutableStateOf<FeedCourseItem?>(null) }
    var showDeleteConfirmCourseId by remember { mutableStateOf<String?>(null) }

    fun loadFeed() {
        scope.launch(Dispatchers.IO) {
            try {
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
                            pointsGps = points,
                            reactions = reactions,
                            commentaires = commentaires
                        )
                    } ?: emptyList()
                }
                feedCourses = fetchedCourses
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Failed to load feed", e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        isLoading = true
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

    val darkScheme = darkColorScheme(
        background = Color.Black,
        surface = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color.White
    )

    MaterialTheme(colorScheme = darkScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "FEED DES CONQUÊTES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        color = NeonVolt
                    ),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonVolt)
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
                                    .background(Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsRun,
                                    contentDescription = null,
                                    tint = NeonVolt,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucune course dans le feed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ajoutez des amis ou lancez-vous pour commencer la conquête !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
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
                            var newCommentText by remember { mutableStateOf("") }

                            FeedCourseCard(
                                course = course,
                                currentUserId = userId,
                                showComments = showCommentsSection,
                                newCommentText = newCommentText,
                                onToggleComments = { showCommentsSection = !showCommentsSection },
                                onCommentTextChange = { newCommentText = it },
                                onSendComment = {
                                    submitComment(course.id, newCommentText)
                                    newCommentText = ""
                                },
                                onReact = { type, current ->
                                    toggleReaction(course.id, type, current)
                                },
                                onViewDetails = {
                                    selectedDetailCourse = course
                                },
                                onDelete = {
                                    showDeleteConfirmCourseId = course.id
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
                    title = { Text("Supprimer la course", color = Color.White) },
                    text = { Text("Êtes-vous sûr de vouloir supprimer cette course ? Cette action est irréversible et supprimera le territoire associé.", color = Color.LightGray) },
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
                            Text("ANNULER", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1E1E1E)
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
    showComments: Boolean,
    newCommentText: String,
    onToggleComments: () -> Unit,
    onCommentTextChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onReact: (String, CourseReaction?) -> Unit,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val userReactionTrident = course.reactions.firstOrNull { it.utilisateur_id == currentUserId && it.type_reaction == "trident" }
    val userReactionCrown = course.reactions.firstOrNull { it.utilisateur_id == currentUserId && it.type_reaction == "couronne" }

    val tridentCount = course.reactions.count { it.type_reaction == "trident" }
    val crownCount = course.reactions.count { it.type_reaction == "couronne" }

    val parsedEmpireColor = try {
        Color(android.graphics.Color.parseColor(course.empireColor ?: "#CCFF00"))
    } catch (_: Exception) {
        NeonVolt
    }

    val parsedGuildColor = try {
        Color(android.graphics.Color.parseColor(course.guildeCouleur ?: "#FFFFFF"))
    } catch (_: Exception) {
        Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: User Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    avatarUrl = course.avatarUrl,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, parsedEmpireColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course.pseudonyme,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(parsedEmpireColor.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Niveau ${course.level}",
                                color = parsedEmpireColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!course.guildeNom.isNullOrEmpty()) {
                        Text(
                            text = "Clan: ${course.guildeNom}",
                            color = parsedGuildColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (course.utilisateurId == currentUserId) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer la course",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Run Name and Legend
            Text(
                text = course.nom ?: "Course Arpent.io",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = NeonVolt
            )
            val formattedDate = remember(course.dateDebut) {
                try {
                    val instant = java.time.Instant.parse(course.dateDebut)
                    val formatter = java.time.format.DateTimeFormatter
                        .ofPattern("dd MMMM yyyy à HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                    formatter.format(instant)
                } catch (_: Exception) {
                    course.dateDebut
                }
            }
            Text(
                text = formattedDate,
                fontSize = 11.sp,
                color = Color.Gray
            )

            if (!course.legende.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "« ${course.legende} »",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricWidget(label = "Distance", value = "%.2f km".format(course.distanceTotale))
                val min = (course.dureeSecondes / 60).toInt()
                val sec = (course.dureeSecondes % 60).toInt()
                MetricWidget(label = "Durée", value = "%dm %02ds".format(min, sec))
                
                val allureMin = course.allureMoyenne.toInt()
                val allureSec = ((course.allureMoyenne - allureMin) * 60).toInt()
                val allureStr = if (course.allureMoyenne > 0) "%d:%02d /km".format(allureMin, allureSec) else "--:--"
                MetricWidget(label = "Allure", value = allureStr)

                if (course.denivelePositif > 0) {
                    MetricWidget(label = "Dénivelé", value = "+${course.denivelePositif.toInt()}m")
                }
                if (course.totalSteps > 0) {
                    MetricWidget(label = "Cadence Moy.", value = "${course.averageCadence} ppm")
                    MetricWidget(label = "Pas", value = "${course.totalSteps} pas")
                }
                MetricWidget(label = "Calories", value = "${course.caloriesEstimees.toInt()} kcal")
                if (course.estBouclee && course.superficieConquise > 0) {
                    MetricWidget(label = "Territoire", value = "+${"%.3f".format(course.superficieConquise / 1e6)} km²")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas GPS Track preview
            if (course.pointsGps.isNotEmpty()) {
                RoutePreviewCanvas(
                    points = course.pointsGps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onViewDetails() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Reactions / Action buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trident reaction
                    ReactionChip(
                        symbol = "🔱",
                        count = tridentCount,
                        isSelected = userReactionTrident != null,
                        onClick = { onReact("trident", userReactionTrident) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Crown reaction
                    ReactionChip(
                        symbol = "👑",
                        count = crownCount,
                        isSelected = userReactionCrown != null,
                        onClick = { onReact("couronne", userReactionCrown) }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleComments) {
                        Text(
                            text = if (course.commentaires.isEmpty()) "Commenter" else "Comments (${course.commentaires.size})",
                            color = NeonVolt,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onViewDetails) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Voir détails",
                            tint = Color.White
                        )
                    }
                }
            }

            // Expandable Comments section
            if (showComments) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                // List comments
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
                                color = NeonVolt,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val commentDate = try {
                                val instant = java.time.Instant.parse(comment.date_creation)
                                val formatter = java.time.format.DateTimeFormatter
                                    .ofPattern("dd MMM à HH:mm")
                                    .withZone(java.time.ZoneId.systemDefault())
                                formatter.format(instant)
                            } catch (_: Exception) {
                                comment.date_creation
                            }
                            Text(
                                text = commentDate,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = comment.contenu,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Add comment input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = onCommentTextChange,
                        placeholder = { Text("Votre commentaire...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonVolt,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonVolt,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSendComment,
                        enabled = newCommentText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Envoyer",
                            tint = if (newCommentText.isNotBlank()) NeonVolt else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricWidget(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ReactionChip(
    symbol: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NeonVolt.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (isSelected) NeonVolt else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = symbol, fontSize = 14.sp)
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    color = if (isSelected) NeonVolt else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RoutePreviewCanvas(
    points: List<GPSPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color.Black, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Pas de tracé GPS", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        return
    }

    Canvas(
        modifier = modifier
            .background(Color.Black, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        val lats = points.map { it.latitude }
        val lons = points.map { it.longitude }
        val minLat = lats.minOrNull() ?: 0.0
        val maxLat = lats.maxOrNull() ?: 0.0
        val minLon = lons.minOrNull() ?: 0.0
        val maxLon = lons.maxOrNull() ?: 0.0

        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon

        val sizeX = size.width
        val sizeY = size.height

        val pathPoints = points.map { pt ->
            val x = if (lonRange > 0) {
                ((pt.longitude - minLon) / lonRange * sizeX).toFloat()
            } else {
                sizeX / 2f
            }
            val y = if (latRange > 0) {
                (sizeY - ((pt.latitude - minLat) / latRange * sizeY)).toFloat()
            } else {
                sizeY / 2f
            }
            Offset(x, y)
        }

        if (pathPoints.size > 1) {
            val path = Path().apply {
                moveTo(pathPoints[0].x, pathPoints[0].y)
                for (i in 1 until pathPoints.size) {
                    lineTo(pathPoints[i].x, pathPoints[i].y)
                }
            }
            drawPath(
                path = path,
                color = NeonVolt,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Start & End markers
            drawCircle(
                color = Color.Green,
                radius = 8f,
                center = pathPoints.first()
            )
            drawCircle(
                color = Color.Red,
                radius = 8f,
                center = pathPoints.last()
            )
        }
    }
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
        try {
            java.time.Instant.parse(pt.timestamp_gps).toEpochMilli()
        } catch (e: Exception) {
            try {
                val formatter = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                java.time.ZonedDateTime.parse(pt.timestamp_gps, formatter).toInstant().toEpochMilli()
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

        if (currentTargetIndex < targets.size && accumulatedDistance >= targets[currentTargetIndex]) {
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
                color = Color.White,
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
                    color = NeonVolt,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (splits.isEmpty()) {
                    Text(
                        text = "Aucun temps de passage disponible (course trop courte).",
                        color = Color.Gray,
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
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (split.distanceMeters >= 1000) "${split.distanceMeters / 1000.0} km" else "${split.distanceMeters} m",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = split.timeFormatted,
                                    color = NeonVolt,
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
                Text("FERMER", color = NeonVolt, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp)
    )
}
