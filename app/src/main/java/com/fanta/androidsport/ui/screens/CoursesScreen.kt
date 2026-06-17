package com.fanta.androidsport.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanta.androidsport.data.model.CourseItem
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.NeonVolt
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun CoursesScreen(
    userId: String,
    isActive: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf<List<CourseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        isLoading = true
        try {
            val response = withContext(Dispatchers.IO) {
                supabase.postgrest["courses"].select {
                    filter { eq("utilisateur_id", userId) }
                    order("date_debut", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
            }
            val fetchedCourses = withContext(Dispatchers.Default) {
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val dateDebut = obj["date_debut"]?.jsonPrimitive?.contentOrNull ?: ""
                    val distanceTotale = obj["distance_totale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val dureeSecondes = obj["duree_secondes"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val estBouclee = obj["est_bouclee"]?.jsonPrimitive?.booleanOrNull ?: false
                    val vitesseMoyenne = obj["vitesse_moyenne"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val vitesseMax = obj["vitesse_max"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val allureMoyenne = obj["allure_moyenne"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val caloriesEstimees = obj["calories_estimees"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val denivelePositif = obj["denivele_positif"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val deniveleNegatif = obj["denivele_negatif"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    CourseItem(
                        id = id,
                        dateDebut = dateDebut,
                        distanceTotale = distanceTotale,
                        dureeSecondes = dureeSecondes,
                        estBouclee = estBouclee,
                        vitesseMoyenne = vitesseMoyenne,
                        vitesseMax = vitesseMax,
                        allureMoyenne = allureMoyenne,
                        caloriesEstimees = caloriesEstimees,
                        denivelePositif = denivelePositif,
                        deniveleNegatif = deniveleNegatif
                    )
                } ?: emptyList()
            }
            courses = fetchedCourses
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to fetch courses", e)
        } finally {
            isLoading = false
        }
    }

    val coursesColorScheme = lightColorScheme(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.9f),
        onSurface = Color.Black,
        surfaceVariant = Color.White.copy(alpha = 0.95f),
        onSurfaceVariant = Color.Black,
        secondaryContainer = Color(0xFFE3F2FD).copy(alpha = 0.9f),
        onSecondaryContainer = Color.Black
    )

    MaterialTheme(colorScheme = coursesColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(16.dp)
        ) {
            Text(
                text = "MES ACTIVITÉS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = Color.Black.copy(alpha = 0.5f)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonVolt)
                }
            } else if (courses.isEmpty()) {
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
                                tint = Color.Black.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Aucune course enregistrée",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            } else {
                // Compute total stats
                val totalDist = courses.sumOf { it.distanceTotale }
                val totalDuration = courses.sumOf { it.dureeSecondes }
                val totalCalories = courses.sumOf { it.caloriesEstimees }

                // Display summary stats card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distance", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("%.2f km".format(totalDist), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Temps", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            val totalMin = (totalDuration / 60).toInt()
                            val h = totalMin / 60
                            val m = totalMin % 60
                            val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                            Text(timeStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Calories", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("${totalCalories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(courses) { course ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val instant = try {
                                        java.time.Instant.parse(course.dateDebut)
                                    } catch (_: Exception) {
                                        null
                                    }
                                    val formattedDate = if (instant != null) {
                                        val formatter = java.time.format.DateTimeFormatter
                                            .ofPattern("dd MMM yyyy à HH:mm")
                                            .withZone(java.time.ZoneId.systemDefault())
                                        formatter.format(instant)
                                    } else {
                                        course.dateDebut
                                    }

                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )

                                    if (course.estBouclee) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(ElectricBlue.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Boucle",
                                                color = ElectricBlue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Distance", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("%.2f km".format(course.distanceTotale), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                    Column {
                                        Text("Durée", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        val min = (course.dureeSecondes / 60).toInt()
                                        val sec = (course.dureeSecondes % 60).toInt()
                                        Text("${min}m ${sec}s", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                    Column {
                                        Text("Allure", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        val allureMin = course.allureMoyenne.toInt()
                                        val allureSec = ((course.allureMoyenne - allureMin) * 60).toInt()
                                        val allureStr = if (course.allureMoyenne > 0) "%d:%02d /km".format(allureMin, allureSec) else "--:--"
                                        Text(allureStr, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }

                                if (course.vitesseMoyenne > 0 || course.caloriesEstimees > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Vitesse Moy.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("%.1f km/h".format(course.vitesseMoyenne), style = MaterialTheme.typography.bodyMedium, color = Color.Black)
                                        }
                                        Column {
                                            Text("Calories", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${course.caloriesEstimees.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, color = Color.Black)
                                        }
                                        Column {
                                            Text("Dénivelé", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("+${course.denivelePositif.toInt()}m", style = MaterialTheme.typography.bodyMedium, color = Color.Black)
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
