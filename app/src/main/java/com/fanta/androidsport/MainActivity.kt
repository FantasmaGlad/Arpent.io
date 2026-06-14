package com.fanta.androidsport

import android.os.Bundle
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.fanta.androidsport.ui.theme.ActiveOrange
import com.fanta.androidsport.ui.theme.ElectricBlue
import com.fanta.androidsport.ui.theme.NeonVolt
import com.fanta.androidsport.ui.theme.SportAndroidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotationState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotationState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.content.Context
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import kotlin.math.*

val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    install(Auth)
    install(Postgrest)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mapbox.common.MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
        
        setContent {
            SportAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArpentApp()
                }
            }
        }
    }
}

@Composable
fun ArpentApp() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle(
        initialValue = SessionStatus.Initializing
    )

    when (sessionStatus) {
        is SessionStatus.Initializing -> {
            LoadingScreen()
        }
        is SessionStatus.Authenticated -> {
            val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id
            if (userId != null) {
                ArpentMainScreen(userId = userId)
            } else {
                LoadingScreen()
            }
        }
        is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> {
            AuthScreen()
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = NeonVolt,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Chargement d'Arpent...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var authMode by remember { mutableStateOf("landing") } // "landing", "guest", "signup", "login"
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pseudonyme by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Glowing pulsing logo container
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(NeonVolt.copy(alpha = 0.1f))
                    .border(2.dp, NeonVolt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terrain,
                    contentDescription = null,
                    tint = NeonVolt,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ARPENT",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = 3.sp,
                    color = Color.White
                )
                Text(
                    text = ".IO",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = 3.sp,
                    color = NeonVolt
                )
            }

            Text(
                text = "Dominez votre ville. Un tracé à la fois.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // Error Display Card
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ActiveOrange.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, ActiveOrange)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ActiveOrange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            when (authMode) {
                "landing" -> {
                    Button(
                        onClick = {
                            errorMessage = null
                            authMode = "guest"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonVolt,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "CONTINUER COMME INVITÉ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            authMode = "signup"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "CRÉER UN COMPTE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            errorMessage = null
                            authMode = "login"
                        }
                    ) {
                        Text(
                            text = "Déjà un compte ? Se connecter",
                            color = ElectricBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                "guest" -> {
                    OutlinedTextField(
                        value = pseudonyme,
                        onValueChange = { pseudonyme = it },
                        label = { Text("Pseudonyme") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonVolt,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = NeonVolt,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (pseudonyme.trim().isEmpty()) {
                                errorMessage = "Veuillez entrer un pseudonyme."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    // Sign in anonymously
                                    supabase.auth.signInAnonymously()
                                    
                                    val userId = supabase.auth.currentUserOrNull()?.id
                                    if (userId != null) {
                                        // Update the user's profile with the custom pseudo
                                        try {
                                            supabase.postgrest["profiles"].update(
                                                mapOf("pseudonyme" to pseudonyme.trim())
                                            ) {
                                                filter {
                                                    eq("id", userId)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Unique constraint failure or other error
                                            supabase.auth.signOut()
                                            errorMessage = "Ce pseudonyme est déjà pris. Veuillez en choisir un autre."
                                            isLoading = false
                                            return@launch
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Erreur d'inscription anonyme : ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonVolt,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "COMMENCER L'AVENTURE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            authMode = "landing"
                            errorMessage = null
                        },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Retour",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                "signup", "login" -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Adresse e-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = ElectricBlue,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = ElectricBlue,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (email.trim().isEmpty() || password.trim().isEmpty()) {
                                errorMessage = "Veuillez remplir tous les champs."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    if (authMode == "signup") {
                                        // Email Sign Up
                                        supabase.auth.signUpWith(Email) {
                                            this.email = email.trim()
                                            this.password = password.trim()
                                        }
                                        // If signup is successful, show a success message or login automatically
                                        val session = supabase.auth.currentSessionOrNull()
                                        if (session == null) {
                                            errorMessage = "Inscription réussie ! Vous pouvez maintenant vous connecter."
                                        } else {
                                            Toast.makeText(context, "Inscription réussie !", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Email Sign In
                                        supabase.auth.signInWith(Email) {
                                            this.email = email.trim()
                                            this.password = password.trim()
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Erreur d'authentification : ${e.localizedMessage}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (authMode == "signup") "CRÉER MON COMPTE" else "SE CONNECTER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            authMode = "landing"
                            errorMessage = null
                        },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Retour",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArpentMainScreen(userId: String) {
    var navigationIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    var userPseudo by remember { mutableStateOf("Visiteur") }
    var totalDistanceKm by remember { mutableStateOf(0.0) }
    var allTimeAreaKm2 by remember { mutableStateOf(0.0) }
    var currentAreaKm2 by remember { mutableStateOf(0.0) }
    var userEmpireColor by remember { mutableStateOf("#00E676") }
    var userShareLocation by remember { mutableStateOf(true) }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }
    var userGuildId by remember { mutableStateOf<String?>(null) }
    var userGuildNom by remember { mutableStateOf<String?>(null) }
    var userGuildCouleur by remember { mutableStateOf<String?>(null) }
    var mapTargetPosition by remember { mutableStateOf<Point?>(null) }

    val completedPolygons = remember { mutableStateListOf<List<Point>>() }

    val scope = rememberCoroutineScope()

    fun refreshStats() {
        if (!isNetworkAvailable(context)) return
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch profile
                val profileRes = supabase.postgrest["profiles"].select {
                    filter { eq("id", userId) }
                }
                // 2. Fetch courses
                val coursesRes = supabase.postgrest["courses"].select {
                    filter { eq("utilisateur_id", userId) }
                }
                // 3. Fetch territories
                val terrRes = supabase.postgrest["territoires"].select {
                    filter { eq("utilisateur_id", userId) }
                }

                // Parse profile info first
                val profileArray = kotlinx.serialization.json.Json.parseToJsonElement(profileRes.data) as? kotlinx.serialization.json.JsonArray
                val profileObj = profileArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val pseudo = profileObj?.get("pseudonyme")?.jsonPrimitive?.contentOrNull ?: "Joueur_${userId.take(8)}"
                val color = profileObj?.get("empire_color")?.jsonPrimitive?.contentOrNull ?: "#00E676"
                val shareLoc = profileObj?.get("share_location")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
                val avatarUrl = profileObj?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                val guildeId = profileObj?.get("guilde_id")?.jsonPrimitive?.contentOrNull

                // Fetch guild details if present
                var gNom: String? = null
                var gColor: String? = null
                if (guildeId != null) {
                    try {
                        val guildRes = supabase.postgrest["guildes"].select {
                            filter { eq("id", guildeId) }
                        }
                        val guildArray = kotlinx.serialization.json.Json.parseToJsonElement(guildRes.data) as? kotlinx.serialization.json.JsonArray
                        val guildObj = guildArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                        gNom = guildObj?.get("nom")?.jsonPrimitive?.contentOrNull
                        gColor = guildObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull
                    } catch (e: Exception) {
                        android.util.Log.e("Arpent", "Failed to fetch guild info", e)
                    }
                }

                val parsed = withContext(Dispatchers.Default) {
                    val coursesArray = kotlinx.serialization.json.Json.parseToJsonElement(coursesRes.data) as? kotlinx.serialization.json.JsonArray
                    var totalDist = 0.0
                    coursesArray?.forEach {
                        val obj = it as? kotlinx.serialization.json.JsonObject
                        totalDist += obj?.get("distance_totale")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    }

                    val terrArray = kotlinx.serialization.json.Json.parseToJsonElement(terrRes.data) as? kotlinx.serialization.json.JsonArray
                    var totalAreaM2 = 0.0
                    terrArray?.forEach {
                        val obj = it as? kotlinx.serialization.json.JsonObject
                        totalAreaM2 += obj?.get("superficie_m2")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    }

                    Triple(pseudo, color, Triple(shareLoc, totalDist, totalAreaM2))
                }

                withContext(Dispatchers.Main) {
                    userPseudo = parsed.first
                    userEmpireColor = parsed.second
                    userShareLocation = parsed.third.first
                    totalDistanceKm = parsed.third.second
                    currentAreaKm2 = parsed.third.third / 1_000_000.0
                    allTimeAreaKm2 = parsed.third.third / 1_000_000.0
                    userAvatarUrl = avatarUrl
                    userGuildId = guildeId
                    userGuildNom = gNom
                    userGuildCouleur = gColor
                }
            } catch (e: Exception) {
                android.util.Log.e("Arpent", "Error fetching stats", e)
            }
        }
    }

    LaunchedEffect(userId) {
        // Load from local storage immediately so there is zero delay/blank screen
        val localPolys = loadTerritoriesLocally(context)
        completedPolygons.clear()
        completedPolygons.addAll(localPolys)

        refreshStats()
        syncTerritoriesFromDatabase(userId, context, completedPolygons)
    }

    // Required permissions depending on Android version
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // Allow starting the map if at least one location permission is granted
        if (fineGranted || coarseGranted) {
            permissionsGranted = true
        } else {
            Toast.makeText(context, "L'accès à la localisation est obligatoire pour utiliser Arpent.io.", Toast.LENGTH_LONG).show()
        }
    }

    if (permissionsGranted) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ARPENT",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = ".IO",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = NeonVolt
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Glowing status indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeonVolt)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = navigationIndex == 0,
                        onClick = { navigationIndex = 0 },
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Conquête") },
                        label = { Text("Conquête") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonVolt,
                            selectedTextColor = NeonVolt,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 1,
                        onClick = { navigationIndex = 1 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Classement") },
                        label = { Text("Classement") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricBlue,
                            selectedTextColor = ElectricBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 3,
                        onClick = { navigationIndex = 3 },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Guilde") },
                        label = { Text("Guilde") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE040FB),
                            selectedTextColor = Color(0xFFE040FB),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = navigationIndex == 2,
                        onClick = { navigationIndex = 2 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ActiveOrange,
                            selectedTextColor = ActiveOrange,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Render map in background for conquest, leaderboard and guild tabs
                if (navigationIndex == 0 || navigationIndex == 1 || navigationIndex == 3) {
                    ConquestMapScreen(
                        userId = userId,
                        userPseudo = userPseudo,
                        initialArea = currentAreaKm2,
                        completedPolygons = completedPolygons,
                        userEmpireColor = userEmpireColor,
                        userAvatarUrl = userAvatarUrl,
                        userGuildCouleur = userGuildCouleur,
                        userGuildNom = userGuildNom,
                        mapTargetPosition = mapTargetPosition,
                        onMapTargetPositionHandled = { mapTargetPosition = null },
                        onRunSaved = { refreshStats() }
                    )
                }

                // Overlay Leaderboard screen on top of the map when on Leaderboard tab
                if (navigationIndex == 1) {
                    LeaderboardScreen(
                        userId = userId,
                        userGuildId = userGuildId,
                        onPlayerClick = { point ->
                            mapTargetPosition = point
                            navigationIndex = 0
                        }
                    )
                }

                // Render Profile screen
                if (navigationIndex == 2) {
                    ProfileScreen(
                        userId = userId,
                        userPseudo = userPseudo,
                        totalDistance = totalDistanceKm,
                        allTimeArea = allTimeAreaKm2,
                        currentArea = currentAreaKm2,
                        userEmpireColor = userEmpireColor,
                        userShareLocation = userShareLocation,
                        userAvatarUrl = userAvatarUrl,
                        onStatsUpdated = { refreshStats() }
                    )
                }

                // Render Guilde screen
                if (navigationIndex == 3) {
                    GuildeScreen(
                        userId = userId,
                        onBackToLogin = {
                            scope.launch {
                                try {
                                    supabase.auth.signOut()
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    )
                }
            }
        }
    } else {
        PermissionRequestScreen(
            onRequestPermissions = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // A beautiful glowing radar/location graphic
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(NeonVolt.copy(alpha = 0.15f))
                    .border(2.dp, NeonVolt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = NeonVolt,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ARPENT.IO",
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Autorisation Requise",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pour conquérir les territoires, enregistrer vos courses et interagir avec la carte 3D, Arpent.io a besoin de vos autorisations système.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonVolt,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "AUTORISER L'ACCÈS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ==========================================
// CONQUEST / MAP SCREEN
// ==========================================

private fun calculateDistance(p1: Point, p2: Point): Double {
    val r = 6371000.0 // Earth radius in meters
    val lat1 = Math.toRadians(p1.latitude())
    val lat2 = Math.toRadians(p2.latitude())
    val dLat = Math.toRadians(p2.latitude() - p1.latitude())
    val dLng = Math.toRadians(p2.longitude() - p1.longitude())
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c // in meters
}

private fun estimateAreaKm2(points: List<Point>): Double {
    if (points.size < 3) return 0.0
    var area = 0.0
    val refLat = points[0].latitude()
    val cosLat = Math.cos(Math.toRadians(refLat))
    val xy = points.map { p ->
        val x = (p.longitude() - points[0].longitude()) * 111000.0 * cosLat
        val y = (p.latitude() - points[0].latitude()) * 111000.0
        Pair(x, y)
    }
    var j = xy.size - 1
    for (i in xy.indices) {
        area += (xy[j].first + xy[i].first) * (xy[j].second - xy[i].second)
        j = i
    }
    val areaM2 = Math.abs(area) / 2.0
    return areaM2 / 1_000_000.0 // convert m² to km²
}

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        if (originalBitmap == null) return null
        
        // Resize to max 160x160 for database storage efficiency
        val size = 160
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        android.util.Log.e("Arpent", "Failed to convert image to Base64", e)
        null
    }
}

fun base64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str == null || base64Str.isEmpty()) return null
    return try {
        val cleanStr = if (base64Str.startsWith("data:image")) {
            val commaIdx = base64Str.indexOf(",")
            if (commaIdx != -1) base64Str.substring(commaIdx + 1) else base64Str
        } else base64Str
        val bytes = Base64.decode(cleanStr, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.let { it.asImageBitmap() }
    } catch (e: Exception) {
        null
    }
}

fun getPolygonCentroid(points: List<Point>): Point {
    if (points.isEmpty()) return Point.fromLngLat(0.0, 0.0)
    var sumLng = 0.0
    var sumLat = 0.0
    var count = 0
    points.forEach { pt ->
        sumLng += pt.longitude()
        sumLat += pt.latitude()
        count++
    }
    return Point.fromLngLat(sumLng / count, sumLat / count)
}

fun getPolygonArea(points: List<Point>): Double {
    if (points.size < 3) return 0.0
    var area = 0.0
    val n = points.size
    for (i in 0 until n) {
        val j = (i + 1) % n
        area += points[i].longitude() * points[j].latitude()
        area -= points[j].longitude() * points[i].latitude()
    }
    return Math.abs(area) / 2.0
}

@Composable
fun AvatarImage(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    placeholderColor: Color = ElectricBlue,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person
) {
    val bitmap = remember(avatarUrl) { base64ToImageBitmap(avatarUrl) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.5f),
                tint = placeholderColor
            )
        }
    }
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    if (cm != null) {
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return false
}

private fun saveTerritoriesLocally(context: android.content.Context, polygons: List<List<Point>>) {
    try {
        val file = File(context.filesDir, "local_territories.json")
        val jsonArray = kotlinx.serialization.json.buildJsonArray {
            polygons.forEach { poly ->
                add(kotlinx.serialization.json.buildJsonArray {
                    poly.forEach { pt ->
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("lng", kotlinx.serialization.json.JsonPrimitive(pt.longitude()))
                            put("lat", kotlinx.serialization.json.JsonPrimitive(pt.latitude()))
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

private fun loadTerritoriesLocally(context: android.content.Context): List<List<Point>> {
    try {
        val file = File(context.filesDir, "local_territories.json")
        if (!file.exists()) return emptyList()
        val text = file.readText()
        val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val polygons = mutableListOf<List<Point>>()
        jsonArray.forEach { polyEl ->
            val pts = mutableListOf<Point>()
            (polyEl as? kotlinx.serialization.json.JsonArray)?.forEach { ptEl ->
                val obj = ptEl as? kotlinx.serialization.json.JsonObject
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

private suspend fun syncTerritoriesFromDatabase(
    userId: String,
    context: android.content.Context,
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
            val terrArray = kotlinx.serialization.json.Json.parseToJsonElement(result.data) as? kotlinx.serialization.json.JsonArray
            val polys = mutableListOf<List<Point>>()
            terrArray?.forEach { element ->
                val obj = element as? kotlinx.serialization.json.JsonObject
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
                        polys.add(pts)
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

private fun saveRunToDatabase(
    userId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    runStartTime: Long,
    runDistance: Double,
    isLoop: Boolean,
    closedPoints: List<Point>,
    onSuccess: (Double) -> Unit
) {

    scope.launch(Dispatchers.IO) {
        try {
            val dateDebut = java.time.Instant.ofEpochMilli(runStartTime).toString()
            val dateFin = java.time.Instant.now().toString()
            val durationSec = ((System.currentTimeMillis() - runStartTime) / 1000.0)
            val distanceKm = runDistance / 1000.0

            // Format coordinates array for PostGIS: "longitude latitude"
            val pointsArray = closedPoints.map { "${it.longitude()} ${it.latitude()}" }

            // Call RPC function to insert course and optionally territory
            val params = kotlinx.serialization.json.buildJsonObject {
                put("p_user_id", kotlinx.serialization.json.JsonPrimitive(userId.toString()))
                put("p_date_debut", kotlinx.serialization.json.JsonPrimitive(dateDebut))
                put("p_date_fin", kotlinx.serialization.json.JsonPrimitive(dateFin))
                put("p_distance_totale", kotlinx.serialization.json.JsonPrimitive(distanceKm))
                put("p_duree_secondes", kotlinx.serialization.json.JsonPrimitive(durationSec))
                put("p_est_bouclee", kotlinx.serialization.json.JsonPrimitive(isLoop))
                put("p_points", kotlinx.serialization.json.JsonArray(pointsArray.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            supabase.postgrest.rpc("enregistrer_course", params)

            if (closedPoints.isNotEmpty()) {
                val lastPt = closedPoints.last()
                try {
                    supabase.postgrest["profiles"].update(
                        mapOf(
                            "latitude" to lastPt.latitude(),
                            "longitude" to lastPt.longitude()
                        )
                    ) {
                        filter { eq("id", userId) }
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("Arpent", "Failed to update profile location", ex)
                }
            }

            val areaKm2 = estimateAreaKm2(closedPoints)
            withContext(Dispatchers.Main) {
                onSuccess(areaKm2)
            }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Erreur d'enregistrement dans la base de données", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erreur base de données : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun ConquestMapScreen(
    userId: String,
    userPseudo: String,
    initialArea: Double,
    completedPolygons: androidx.compose.runtime.snapshots.SnapshotStateList<List<Point>>,
    userEmpireColor: String,
    userAvatarUrl: String?,
    userGuildCouleur: String?,
    userGuildNom: String?,
    mapTargetPosition: Point?,
    onMapTargetPositionHandled: () -> Unit,
    onRunSaved: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Simulation state variables
    var isSimulatingRun by remember { mutableStateOf(false) }
    var simulationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Real run tracking states
    var isRealRunActive by remember { mutableStateOf(false) }
    var runStartTime by remember { mutableStateOf<Long?>(null) }
    var runDistance by remember { mutableStateOf(0.0) }

    // Live statistics
    var currentArea by remember { mutableStateOf(initialArea) }
    var sessionGainedArea by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0.0) }

    LaunchedEffect(initialArea) {
        currentArea = initialArea
    }

    // Parse user empire color
    val parsedUserColor = remember(userEmpireColor) {
        try { Color(android.graphics.Color.parseColor(userEmpireColor)) } catch (_: Exception) { Color(0xFF00E676) }
    }

    // Paris starting coordinates
    val parisCenter = Point.fromLngLat(2.3522, 48.8566)
    var currentPosition by remember { mutableStateOf(parisCenter) }

    // First location update flag
    var isFirstLocationUpdate by remember { mutableStateOf(true) }

    // Store references to drawn objects
    val activePathPoints = remember { mutableStateListOf<Point>() }

    // Mapbox Viewport State
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(parisCenter)
            zoom(16.2)
            pitch(60.0) // 3D Tilt perspective angle
            bearing(30.0)
        }
    }

    // --- Other players data ---
    data class OtherPlayerTerritory(
        val playerId: String,
        val pseudo: String,
        val empireColor: Color,
        val polygons: List<List<Point>>,
        val markerPosition: Point?,
        val avatarUrl: String?,
        val guildeNom: String?,
        val guildeCouleur: String?,
        val totalAreaM2: Double
    )
    var otherPlayersTerritories by remember { mutableStateOf<List<OtherPlayerTerritory>>(emptyList()) }
    var selectedPlayerStats by remember { mutableStateOf<OtherPlayerTerritory?>(null) }

    // Fetch other players' territories dynamically based on visible bounding box
    LaunchedEffect(userId) {
        // Sync current user's own territories first to handle fresh installs / reinstalls
        try {
            syncTerritoriesFromDatabase(userId, context, completedPolygons)
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to sync own territories in map screen", e)
        }
    }

    LaunchedEffect(userId, mapViewportState.cameraState) {
        val cam = mapViewportState.cameraState ?: return@LaunchedEffect
        val centerPoint = cam.center
        val currentZoom = cam.zoom
        
        delay(400) // Debounce viewport updates to avoid database spamming
        
        val lat = centerPoint.latitude()
        val lng = centerPoint.longitude()
        
        val multiplier = 2.5 
        val dLng = (360.0 / Math.pow(2.0, currentZoom)) * multiplier
        val dLat = (dLng * Math.cos(Math.toRadians(lat))) * multiplier
        
        val minLng = lng - dLng
        val maxLng = lng + dLng
        val minLat = lat - dLat
        val maxLat = lat + dLat
        
        try {
            val params = kotlinx.serialization.json.buildJsonObject {
                put("min_lng", kotlinx.serialization.json.JsonPrimitive(minLng))
                put("min_lat", kotlinx.serialization.json.JsonPrimitive(minLat))
                put("max_lng", kotlinx.serialization.json.JsonPrimitive(maxLng))
                put("max_lat", kotlinx.serialization.json.JsonPrimitive(maxLat))
            }
            val response = withContext(Dispatchers.IO) {
                supabase.postgrest.rpc("get_territoires_in_bbox", params)
            }
            
            val territories = withContext(Dispatchers.Default) {
                val array = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList<OtherPlayerTerritory>()
                val terrByUser = mutableMapOf<String, MutableList<List<Point>>>()
                
                data class PlayerDetails(
                    val pseudo: String,
                    val colorStr: String,
                    val avatarUrl: String?,
                    val guildeNom: String?,
                    val guildeCouleur: String?,
                    val totalAreaM2: Double
                )
                val userDetails = mutableMapOf<String, PlayerDetails>()
                val userLocations = mutableMapOf<String, Point>()
                
                array.forEach { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val uId = obj["utilisateur_id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur"
                    val colorStr = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                    val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val guildeNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                    val guildeCouleur = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                    val totalAreaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    
                    userDetails[uId] = PlayerDetails(pseudo, colorStr, avatarUrl, guildeNom, guildeCouleur, totalAreaM2)
                    
                    val latVal = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    val lonVal = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    if (latVal != null && lonVal != null) {
                        userLocations[uId] = Point.fromLngLat(lonVal, latVal)
                    }
                    
                    val pointsArr = obj["points"] as? kotlinx.serialization.json.JsonArray
                    if (pointsArr != null) {
                        val polygon = pointsArr.mapNotNull { pt ->
                            val coords = pt.jsonPrimitive.content.split(" ")
                            if (coords.size >= 2) {
                                val lon = coords[0].toDoubleOrNull() ?: return@mapNotNull null
                                val lat = coords[1].toDoubleOrNull() ?: return@mapNotNull null
                                Point.fromLngLat(lon, lat)
                            } else null
                        }
                        if (polygon.size >= 3) {
                            terrByUser.getOrPut(uId) { mutableListOf() }.add(polygon)
                        }
                    }
                }
                
                userDetails.keys.filter { it != userId }.map { pId ->
                    val detail = userDetails[pId]!!
                    val empColor = try { Color(android.graphics.Color.parseColor(detail.colorStr)) } catch (_: Exception) { Color(0xFF00E676) }
                    val marker = userLocations[pId]
                    val polys = terrByUser[pId] ?: emptyList()
                    OtherPlayerTerritory(
                        playerId = pId,
                        pseudo = detail.pseudo,
                        empireColor = empColor,
                        polygons = polys,
                        markerPosition = marker,
                        avatarUrl = detail.avatarUrl,
                        guildeNom = detail.guildeNom,
                        guildeCouleur = detail.guildeCouleur,
                        totalAreaM2 = detail.totalAreaM2
                    )
                }
            }
            
            withContext(Dispatchers.Main) {
                otherPlayersTerritories = territories
            }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to fetch territories in bounding box", e)
        }
    }

    // Handle map target position from leaderboard click
    LaunchedEffect(mapTargetPosition) {
        if (mapTargetPosition != null) {
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(mapTargetPosition)
                    .zoom(15.0)
                    .pitch(60.0)
                    .bearing(0.0)
                    .build(),
                mapAnimationOptions { duration(2000L) }
            )
            onMapTargetPositionHandled()
        }
    }

    // GPS Location listener using standard LocationManager
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    DisposableEffect(lifecycleOwner) {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                if (isRealRunActive) {
                    val prevPoint = activePathPoints.lastOrNull()
                    if (prevPoint == null || prevPoint != point) {
                        if (prevPoint != null) {
                            runDistance += calculateDistance(prevPoint, point)
                        }
                        activePathPoints.add(point)
                    }
                    currentPosition = point
                    currentSpeed = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                } else if (!isSimulatingRun) {
                    currentPosition = point
                    if (isFirstLocationUpdate) {
                        isFirstLocationUpdate = false
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(point)
                                .zoom(16.5)
                                .pitch(60.0)
                                .bearing(30.0)
                                .build(),
                            mapAnimationOptions { duration(1000L) }
                        )
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLocation = lastKnownGps ?: lastKnownNetwork
                bestLocation?.let {
                    val point = Point.fromLngLat(it.longitude, it.latitude)
                    currentPosition = point
                    if (isFirstLocationUpdate) {
                        isFirstLocationUpdate = false
                        mapViewportState.setCameraOptions {
                            center(point)
                            zoom(16.5)
                            pitch(60.0)
                            bearing(30.0)
                        }
                    }
                }

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L, // 3 seconds
                    2f,    // 2 meters
                    locationListener
                )
            }
        } catch (e: SecurityException) {
            // Permission not granted or disallowed
        }

        onDispose {
            simulationJob?.cancel()
            try {
                locationManager.removeUpdates(locationListener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // MapboxMap Composable
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            logo = {},
            attribution = {}
        ) {
            MapStyle(style = "mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8")

            // Draw active polyline
            if (activePathPoints.isNotEmpty()) {
                val polylineState = remember {
                    PolylineAnnotationState().apply {
                        lineColor = Color(0xFF00E5FF)
                        lineWidth = 6.0
                    }
                }
                PolylineAnnotation(
                    points = activePathPoints.toList(),
                    polylineAnnotationState = polylineState
                )
            }

            // Draw completed polygons (user's own territories)
            val parsedUserGuildColor = remember(userGuildCouleur) {
                if (userGuildCouleur != null) {
                    try { Color(android.graphics.Color.parseColor(userGuildCouleur)) } catch (_: Exception) { null }
                } else null
            }
            val userTerritoryColor = parsedUserGuildColor ?: parsedUserColor

            completedPolygons.forEach { polygonPoints ->
                val polygonState = remember(polygonPoints, userTerritoryColor, parsedUserColor) {
                    PolygonAnnotationState().apply {
                        fillColor = userTerritoryColor.copy(alpha = 0.25f)
                        fillOutlineColor = parsedUserColor
                    }
                }
                PolygonAnnotation(
                    points = listOf(polygonPoints),
                    polygonAnnotationState = polygonState
                )
            }

            // Draw user avatar exactly once at centroid of largest polygon
            val userLargestPolygon = remember(completedPolygons.size) {
                completedPolygons.maxByOrNull { getPolygonArea(it) }
            }
            val userCentroid = remember(userLargestPolygon) {
                userLargestPolygon?.let { getPolygonCentroid(it) }
            }
            if (userCentroid != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(userCentroid)
                        allowOverlap(true)
                    }
                ) {
                    AvatarImage(
                        avatarUrl = userAvatarUrl,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, parsedUserColor, CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                selectedPlayerStats = OtherPlayerTerritory(
                                    playerId = userId,
                                    pseudo = userPseudo,
                                    empireColor = parsedUserColor,
                                    polygons = completedPolygons.toList(),
                                    markerPosition = null,
                                    avatarUrl = userAvatarUrl,
                                    guildeNom = userGuildNom,
                                    guildeCouleur = userGuildCouleur,
                                    totalAreaM2 = currentArea * 1_000_000.0
                                )
                            }
                    )
                }
            }

            // Draw other players' territories
            otherPlayersTerritories.forEach { player ->
                val parsedGuildColor = remember(player.guildeCouleur) {
                    if (player.guildeCouleur != null) {
                        try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { null }
                    } else null
                }
                val territoryColor = parsedGuildColor ?: player.empireColor

                player.polygons.forEach { polygonPoints ->
                    val polygonState = remember(polygonPoints, territoryColor, player.empireColor) {
                        PolygonAnnotationState().apply {
                            fillColor = territoryColor.copy(alpha = 0.20f)
                            fillOutlineColor = player.empireColor
                        }
                    }
                    PolygonAnnotation(
                        points = listOf(polygonPoints),
                        polygonAnnotationState = polygonState
                    )
                }

                // Draw player avatar exactly once at centroid of largest polygon
                val playerLargestPolygon = remember(player.polygons) {
                    player.polygons.maxByOrNull { getPolygonArea(it) }
                }
                val playerCentroid = remember(playerLargestPolygon) {
                    playerLargestPolygon?.let { getPolygonCentroid(it) }
                }
                if (playerCentroid != null) {
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(playerCentroid)
                            allowOverlap(true)
                        }
                    ) {
                        AvatarImage(
                            avatarUrl = player.avatarUrl,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(2.dp, player.empireColor, CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    selectedPlayerStats = player
                                }
                        )
                    }
                }

                // Draw marker for each player with territory
                if (player.markerPosition != null) {
                    val circleState = remember(player.playerId) {
                        CircleAnnotationState().apply {
                            circleRadius = 10.0
                            circleColor = player.empireColor
                            circleStrokeWidth = 3.0
                            circleStrokeColor = Color.White
                        }
                    }
                    CircleAnnotation(
                        point = player.markerPosition,
                        circleAnnotationState = circleState
                    )
                }
            }
        }

        // --- OVERLAYS ---

        // 1. Top Conquest Status Indicator
        if (isSimulatingRun) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .widthIn(max = 340.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                border = BorderStroke(1.dp, NeonVolt.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pulseTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enregistrement Course",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${"%.1f".format(currentSpeed)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricBlue
                    )
                }
            }
        }

        // 2. Stats Overlay (Bottom-Left)
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .width(220.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VOTRE EMPIRE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${"%.2f".format(currentArea)} km²",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (isSimulatingRun || sessionGainedArea > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Capture",
                            tint = NeonVolt,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${"%.3f".format(sessionGainedArea)} km²",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonVolt
                        )
                    }
                }
            }
        }

        // 3. Control Actions Overlay (Floating Column on Right)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Camera Centering Fab
            FloatingActionButton(
                onClick = {
                    mapViewportState.flyTo(
                        CameraOptions.Builder()
                            .center(currentPosition)
                            .zoom(16.5)
                            .bearing(0.0)
                            .build(),
                        mapAnimationOptions { duration(800L) }
                    )
                    Toast.makeText(context, "Recentré sur votre position", Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Ma position", modifier = Modifier.size(24.dp))
            }

            // Camera Tilt toggle
            var is3D by remember { mutableStateOf(true) }
            FloatingActionButton(
                onClick = {
                    is3D = !is3D
                    val currentCamera = mapViewportState.cameraState
                    if (currentCamera != null) {
                        mapViewportState.flyTo(
                            CameraOptions.Builder()
                                .center(currentCamera.center)
                                .zoom(currentCamera.zoom)
                                .bearing(currentCamera.bearing)
                                .pitch(if (is3D) 60.0 else 0.0)
                                .build(),
                            mapAnimationOptions { duration(1000L) }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (is3D) ElectricBlue else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = if (is3D) Icons.Default.Layers else Icons.Default.LayersClear,
                    contentDescription = "Activer 3D",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Temporary neighborhood tour simulation button (Test)
            val testButtonColor by animateColorAsState(
                targetValue = if (isSimulatingRun) Color.Red else ActiveOrange,
                label = "test_btn_color"
            )
            FloatingActionButton(
                onClick = {
                    if (isRealRunActive) {
                        Toast.makeText(context, "Course réelle active. Arrêtez-la d'abord.", Toast.LENGTH_SHORT).show()
                    } else if (isSimulatingRun) {
                        // Stop simulation manually
                        simulationJob?.cancel()
                        isSimulatingRun = false
                        currentSpeed = 0.0

                        if (activePathPoints.size >= 3) {
                            val closedPoints = activePathPoints.toList() + activePathPoints[0]
                            saveRunToDatabase(
                                userId = userId,
                                scope = scope,
                                context = context,
                                runStartTime = runStartTime ?: System.currentTimeMillis(),
                                runDistance = runDistance,
                                isLoop = true,
                                closedPoints = closedPoints
                            ) { areaKm2 ->
                                completedPolygons.add(closedPoints)
                                saveTerritoriesLocally(context, completedPolygons)
                                currentArea += areaKm2
                                sessionGainedArea = areaKm2
                                Toast.makeText(context, "Simulation enregistrée ! Territoire conquis (+${"%.3f".format(areaKm2)} km²)", Toast.LENGTH_LONG).show()
                                onRunSaved()
                            }
                        } else {
                            Toast.makeText(context, "Simulation annulée (pas assez de points).", Toast.LENGTH_SHORT).show()
                            activePathPoints.clear()
                        }
                    } else {
                        // Start simulation
                        isSimulatingRun = true
                        sessionGainedArea = 0.0
                        runDistance = 0.0
                        runStartTime = System.currentTimeMillis()
                        
                        activePathPoints.clear()

                        simulationJob = scope.launch {
                            val startPoint = currentPosition
                            activePathPoints.add(startPoint)

                            // 10 simulated steps in a neighborhood tour
                            val stepDelta = listOf(
                                Pair(0.0003, 0.0001),
                                Pair(0.0006, 0.0004),
                                Pair(0.0007, 0.0009),
                                Pair(0.0004, 0.0012),
                                Pair(0.0000, 0.0013),
                                Pair(-0.0004, 0.0011),
                                Pair(-0.0006, 0.0007),
                                Pair(-0.0005, 0.0002),
                                Pair(-0.0002, 0.0000),
                                Pair(0.0, 0.0) // Return to start point to close loop
                            )

                            var stepIndex = 0
                            var lastPoint = startPoint
                            while (stepIndex < stepDelta.size && isSimulatingRun) {
                                currentSpeed = 10.0 + (Math.random() * 4.0)
                                val delta = stepDelta[stepIndex]
                                val newPos = Point.fromLngLat(
                                    startPoint.longitude() + delta.second,
                                    startPoint.latitude() + delta.first
                                )
                                runDistance += calculateDistance(lastPoint, newPos)
                                lastPoint = newPos
                                currentPosition = newPos
                                activePathPoints.add(newPos)

                                mapViewportState.easeTo(
                                    CameraOptions.Builder().center(newPos).build(),
                                    mapAnimationOptions { duration(500L) }
                                )
                                stepIndex++
                                delay(1200)
                            }

                            if (isSimulatingRun) {
                                val closedPoints = activePathPoints.toList()
                                saveRunToDatabase(
                                    userId = userId,
                                    scope = scope,
                                    context = context,
                                    runStartTime = runStartTime ?: System.currentTimeMillis(),
                                    runDistance = runDistance,
                                    isLoop = true,
                                    closedPoints = closedPoints
                                ) { areaKm2 ->
                                    completedPolygons.add(closedPoints)
                                    saveTerritoriesLocally(context, completedPolygons)
                                    currentArea += areaKm2
                                    sessionGainedArea = areaKm2
                                    Toast.makeText(context, "Simulation terminée ! Territoire conquis (+${"%.3f".format(areaKm2)} km²)", Toast.LENGTH_LONG).show()
                                    onRunSaved()
                                }
                                isSimulatingRun = false
                                currentSpeed = 0.0
                                activePathPoints.clear()
                            }
                        }
                    }
                },
                containerColor = testButtonColor,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Test",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            // Real run capturing button (GPS active tracking)
            val realButtonColor by animateColorAsState(
                targetValue = if (isRealRunActive) Color.Red else NeonVolt,
                label = "real_btn_color"
            )
            FloatingActionButton(
                onClick = {
                    if (isSimulatingRun) {
                        Toast.makeText(context, "Simulation active. Arrêtez-la d'abord.", Toast.LENGTH_SHORT).show()
                    } else if (isRealRunActive) {
                        // Stop real run
                        isRealRunActive = false
                        currentSpeed = 0.0

                        val isLoop = activePathPoints.size >= 3 && calculateDistance(activePathPoints.first(), activePathPoints.last()) <= 35.0
                        val closedPoints = if (isLoop && activePathPoints.first() != activePathPoints.last()) {
                            activePathPoints.toList() + activePathPoints[0]
                        } else {
                            activePathPoints.toList()
                        }

                        if (activePathPoints.isNotEmpty()) {
                            saveRunToDatabase(
                                userId = userId,
                                scope = scope,
                                context = context,
                                runStartTime = runStartTime ?: System.currentTimeMillis(),
                                runDistance = runDistance,
                                isLoop = isLoop,
                                closedPoints = closedPoints
                            ) { areaKm2 ->
                                if (isLoop) {
                                    completedPolygons.add(closedPoints)
                                    saveTerritoriesLocally(context, completedPolygons)
                                    currentArea += areaKm2
                                    sessionGainedArea = areaKm2
                                    Toast.makeText(context, "Course enregistrée ! Territoire conquis (+${"%.3f".format(areaKm2)} km²)", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Course enregistrée avec succès !", Toast.LENGTH_LONG).show()
                                }
                                onRunSaved()
                            }
                        } else {
                            Toast.makeText(context, "Course annulée (aucun point GPS enregistré).", Toast.LENGTH_SHORT).show()
                        }
                        activePathPoints.clear()
                        runDistance = 0.0
                        runStartTime = null
                    } else {
                        // Start real run
                        isRealRunActive = true
                        sessionGainedArea = 0.0
                        runDistance = 0.0
                        runStartTime = System.currentTimeMillis()

                        activePathPoints.clear()

                        // Initialize the first point if we have one
                        activePathPoints.add(currentPosition)
                        Toast.makeText(context, "Course réelle démarrée !", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = realButtonColor,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isRealRunActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Démarrer course",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Show player details stats dialog when clicked
        if (selectedPlayerStats != null) {
            val player = selectedPlayerStats!!
            AlertDialog(
                onDismissRequest = { selectedPlayerStats = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val parsedColor = player.empireColor
                        AvatarImage(
                            avatarUrl = player.avatarUrl,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(2.dp, parsedColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = player.pseudo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                            if (player.guildeNom != null) {
                                val gColor = try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { Color.Gray }
                                Text(text = "Clan: ${player.guildeNom}", color = gColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "Statistiques de l'Empire",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Couleur de l'Empire :", color = Color.Black)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(player.empireColor)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Superficie conquise :", color = Color.Black)
                            val areaStr = if (player.totalAreaM2 >= 10000.0) {
                                "%.4f km²".format(player.totalAreaM2 / 1_000_000.0)
                            } else {
                                "${player.totalAreaM2.toInt()} m²"
                            }
                            Text(
                                text = areaStr,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPlayerStats = null }) {
                        Text("FERMER", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color.White
            )
        }
    }
}

// ==========================================
// LEADERBOARD SCREEN (Guilds ranking)
// ==========================================

data class GuildRank(
    val rank: Int,
    val name: String,
    val territorySqKm: Double,
    val color: Color,
    val isUserGuild: Boolean = false
)

data class LeaderboardPlayer(
    val id: String,
    val pseudonyme: String,
    val empireColor: String,
    val latitude: Double?,
    val longitude: Double?,
    val totalAreaM2: Double,
    val avatarUrl: String?,
    val guildeNom: String?,
    val guildeCouleur: String?
)

data class LeaderboardClan(
    val id: String,
    val nom: String,
    val couleurHex: String,
    val avatarUrl: String?,
    val totalAreaM2: Double,
    val membreCount: Int
)

@Composable
fun LeaderboardScreen(
    userId: String,
    userGuildId: String? = null,
    onPlayerClick: (Point) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var players by remember { mutableStateOf<List<LeaderboardPlayer>>(emptyList()) }
    var clans by remember { mutableStateOf<List<LeaderboardClan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Joueurs, 1 = Clans

    LaunchedEffect(Unit) {
        try {
            // Fetch players
            val response = withContext(Dispatchers.IO) {
                supabase.postgrest["leaderboard"].select()
            }
            val fetchedPlayers = withContext(Dispatchers.Default) {
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(response.data) as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val pseudo = obj["pseudonyme"]?.jsonPrimitive?.contentOrNull ?: "Joueur_${id.take(8)}"
                    val color = obj["empire_color"]?.jsonPrimitive?.contentOrNull ?: "#00E676"
                    val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                    val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                    val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val avatar = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val gNom = obj["guilde_nom"]?.jsonPrimitive?.contentOrNull
                    val gColor = obj["guilde_couleur"]?.jsonPrimitive?.contentOrNull
                    LeaderboardPlayer(id, pseudo, color, lat, lon, areaM2, avatar, gNom, gColor)
                } ?: emptyList()
            }
            
            // Fetch clans
            val clanResponse = withContext(Dispatchers.IO) {
                supabase.postgrest["clan_leaderboard"].select()
            }
            val fetchedClans = withContext(Dispatchers.Default) {
                val jsonArray = kotlinx.serialization.json.Json.parseToJsonElement(clanResponse.data) as? kotlinx.serialization.json.JsonArray
                jsonArray?.mapNotNull { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: "Clan"
                    val color = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#E040FB"
                    val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
                    val areaM2 = obj["total_area_m2"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val membreCount = obj["membre_count"]?.jsonPrimitive?.intOrNull ?: 0
                    LeaderboardClan(id, nom, color, avatarUrl, areaM2, membreCount)
                } ?: emptyList()
            }
            
            players = fetchedPlayers
            clans = fetchedClans
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to fetch leaderboard", e)
        } finally {
            isLoading = false
        }
    }

    val leaderboardColorScheme = lightColorScheme(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.85f),
        onSurface = Color.Black,
        surfaceVariant = Color.White.copy(alpha = 0.95f),
        onSurfaceVariant = Color.Black,
        secondaryContainer = Color(0xFFE3F2FD).copy(alpha = 0.9f),
        onSecondaryContainer = Color.Black
    )

    MaterialTheme(colorScheme = leaderboardColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(16.dp)
        ) {
            // TabRow for Leaderboard
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ActiveOrange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("JOUEURS", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("CLANS", fontWeight = FontWeight.Bold) }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonVolt)
                }
            } else if (selectedTab == 0) {
                // JOUEURS LEADERBOARD
                if (players.isEmpty()) {
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucun joueur enregistré",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // Afficher le rang de l'utilisateur connecté s'il est présent
                    val userIndex = players.indexOfFirst { it.id == userId?.toString() }
                    if (userIndex != -1) {
                        val me = players[userIndex]
                        val suffix = if (userIndex == 0) "er" else "ème"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val parsedColor = remember(me.empireColor) {
                                    try { Color(android.graphics.Color.parseColor(me.empireColor)) } catch (e: Exception) { NeonVolt }
                                }
                                AvatarImage(
                                    avatarUrl = me.avatarUrl,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, parsedColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "VOTRE CLASSEMENT : ${userIndex + 1}$suffix",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = parsedColor
                                    )
                                    val areaStr = if (me.totalAreaM2 >= 10000) {
                                        "%.4f km²".format(me.totalAreaM2 / 1_000_000.0)
                                    } else {
                                        "${me.totalAreaM2.toInt()} m²"
                                    }
                                    Text(
                                        text = "${me.pseudonyme} • $areaStr",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Text(
                        text = "Classement des Conquérants",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(players) { index, player ->
                            val isMe = player.id == userId?.toString()
                            val cardBg = if (isMe) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val parsedColor = remember(player.empireColor) {
                                try { Color(android.graphics.Color.parseColor(player.empireColor)) } catch (e: Exception) { NeonVolt }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (player.latitude != null && player.longitude != null) {
                                            onPlayerClick(Point.fromLngLat(player.longitude, player.latitude))
                                        } else {
                                            Toast.makeText(context, "${player.pseudonyme} n'a pas de position sur la carte", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = if (isMe) BorderStroke(1.dp, parsedColor) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val rank = index + 1
                                        Text(
                                            text = "$rank",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.width(36.dp),
                                            color = when (rank) {
                                                1 -> ActiveOrange
                                                2 -> ElectricBlue
                                                3 -> Color(0xFFE91E63)
                                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            }
                                        )
                                        AvatarImage(
                                            avatarUrl = player.avatarUrl,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            placeholderColor = parsedColor,
                                            placeholderIcon = Icons.Default.Person
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (isMe) "${player.pseudonyme} (Vous)" else player.pseudonyme,
                                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (player.guildeNom != null) {
                                                val gColor = try { Color(android.graphics.Color.parseColor(player.guildeCouleur)) } catch (_: Exception) { Color.Gray }
                                                Text(
                                                    text = player.guildeNom,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = gColor
                                                )
                                            }
                                        }
                                    }
                                    val areaStr = if (player.totalAreaM2 >= 10000) {
                                        "%.4f km²".format(player.totalAreaM2 / 1_000_000.0)
                                    } else {
                                        "${player.totalAreaM2.toInt()} m²"
                                    }
                                    Text(
                                        text = areaStr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isMe) parsedColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // CLANS LEADERBOARD
                if (clans.isEmpty()) {
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Aucun clan enregistré",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // User's Clan rank card
                    if (userGuildId != null) {
                        val myClanIndex = clans.indexOfFirst { it.id == userGuildId }
                        if (myClanIndex != -1) {
                            val myClan = clans[myClanIndex]
                            val suffix = if (myClanIndex == 0) "er" else "ème"
                            val parsedClanColor = remember(myClan.couleurHex) {
                                try { Color(android.graphics.Color.parseColor(myClan.couleurHex)) } catch (_: Exception) { Color(0xFFE040FB) }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = parsedClanColor.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = myClan.avatarUrl,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                        placeholderColor = parsedClanColor,
                                        placeholderIcon = Icons.Default.Shield
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "RANG DE VOTRE CLAN : ${myClanIndex + 1}$suffix",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = parsedClanColor
                                        )
                                        val areaStr = if (myClan.totalAreaM2 >= 10000) {
                                            "%.4f km²".format(myClan.totalAreaM2 / 1_000_000.0)
                                        } else {
                                            "${myClan.totalAreaM2.toInt()} m²"
                                        }
                                        Text(
                                            text = "${myClan.nom} • $areaStr",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    Text(
                        text = "Classement des Clans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(clans) { index, clan ->
                            val isMyClan = clan.id == userGuildId
                            val cardBg = if (isMyClan) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val parsedClanColor = remember(clan.couleurHex) {
                                try { Color(android.graphics.Color.parseColor(clan.couleurHex)) } catch (_: Exception) { Color(0xFFE040FB) }
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = if (isMyClan) BorderStroke(1.dp, parsedClanColor) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val rank = index + 1
                                        Text(
                                            text = "$rank",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.width(36.dp),
                                            color = when (rank) {
                                                1 -> ActiveOrange
                                                2 -> ElectricBlue
                                                3 -> Color(0xFFE91E63)
                                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            }
                                        )
                                        AvatarImage(
                                            avatarUrl = clan.avatarUrl,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                            placeholderColor = parsedClanColor,
                                            placeholderIcon = Icons.Default.Shield
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (isMyClan) "${clan.nom} (Votre Clan)" else clan.nom,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${clan.membreCount} membre(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    val areaStr = if (clan.totalAreaM2 >= 10000) {
                                        "%.4f km²".format(clan.totalAreaM2 / 1_000_000.0)
                                    } else {
                                        "${clan.totalAreaM2.toInt()} m²"
                                    }
                                    Text(
                                        text = areaStr,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isMyClan) parsedClanColor else MaterialTheme.colorScheme.onSurface
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

@Composable
fun ColorWheel(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }
    var wheelRadius by remember { mutableStateOf(0f) }

    val colors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }
    val brush = remember {
        Brush.sweepGradient(colors)
    }

    fun getColorAtPoint(offset: Offset, center: Offset, radius: Float): Color? {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius || distance < 0.01f) return null

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) {
            angle += 360f
        }

        val saturation = (distance / radius).coerceIn(0f, 1f)
        val hsv = floatArrayOf(angle, saturation, 1.0f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            val color = getColorAtPoint(change.position, wheelCenter, wheelRadius)
                            if (color != null) onColorSelected(color)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        wheelCenter = center
        wheelRadius = size.minDimension / 2f

        drawCircle(
            brush = brush,
            radius = wheelRadius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = wheelRadius
            ),
            radius = wheelRadius,
            center = center
        )

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]

        val angleRad = Math.toRadians(hue.toDouble())
        val indicatorX = center.x + cos(angleRad).toFloat() * saturation * wheelRadius
        val indicatorY = center.y + sin(angleRad).toFloat() * saturation * wheelRadius

        drawCircle(
            color = Color.Black,
            radius = 10f,
            center = Offset(indicatorX, indicatorY),
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Color.White,
            radius = 8f,
            center = Offset(indicatorX, indicatorY)
        )
    }
}

// ==========================================
// PROFILE SCREEN
// ==========================================

@Composable
fun ProfileScreen(
    userId: String,
    userPseudo: String,
    totalDistance: Double,
    allTimeArea: Double,
    currentArea: Double,
    userEmpireColor: String,
    userShareLocation: Boolean,
    userAvatarUrl: String?,
    onStatsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val parsedUserColor = remember(userEmpireColor) {
        try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            NeonVolt
        }
    }

    var localColor by remember(userEmpireColor) {
        val parsed = try {
            Color(android.graphics.Color.parseColor(userEmpireColor))
        } catch (e: Exception) {
            NeonVolt
        }
        mutableStateOf(parsed)
    }

    var shareLocationEnabled by remember(userShareLocation) {
        mutableStateOf(userShareLocation)
    }

    LaunchedEffect(shareLocationEnabled) {
        if (shareLocationEnabled == userShareLocation) return@LaunchedEffect
        delay(300) // debounce update
        try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["profiles"].update(
                    mapOf("share_location" to shareLocationEnabled)
                ) {
                    filter { eq("id", userId) }
                }
            }
            onStatsUpdated()
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to update share_location", e)
        }
    }

    LaunchedEffect(localColor) {
        val localHex = String.format("#%06X", 0xFFFFFF and localColor.toArgb())
        if (localHex.equals(userEmpireColor, ignoreCase = true)) return@LaunchedEffect

        delay(500) // debounce updates to database
        try {
            withContext(Dispatchers.IO) {
                supabase.postgrest["profiles"].update(
                    mapOf("empire_color" to localHex)
                ) {
                    filter { eq("id", userId) }
                }
            }
            onStatsUpdated()
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to update empire color", e)
        }
    }

    var isEditingPseudo by remember { mutableStateOf(false) }
    var tempPseudo by remember { mutableStateOf(userPseudo) }

    LaunchedEffect(userPseudo) {
        tempPseudo = userPseudo
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Premium Avatar Card with photo import launcher
        val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            uri?.let {
                val base64 = uriToBase64(context, it)
                if (base64 != null) {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                supabase.postgrest["profiles"].update(
                                    mapOf("avatar_url" to base64)
                                ) {
                                    filter { eq("id", userId) }
                                }
                            }
                            onStatsUpdated()
                        } catch (e: Exception) {
                            android.util.Log.e("Arpent", "Failed to update avatar", e)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(ElectricBlue, parsedUserColor, ActiveOrange, ElectricBlue)
                    )
                )
                .clickable {
                    imageLauncher.launch("image/*")
                }
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                AvatarImage(
                    avatarUrl = userAvatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    placeholderColor = parsedUserColor,
                    placeholderIcon = Icons.Default.Person
                )
            }
            
            // Edit pencil overlay
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Importer photo",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isEditingPseudo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = tempPseudo,
                    onValueChange = { tempPseudo = it },
                    label = { Text("Modifier le pseudo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonVolt,
                        focusedLabelColor = NeonVolt,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (tempPseudo.trim().isEmpty()) {
                            Toast.makeText(context, "Le pseudo ne peut pas être vide", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        if (userId != null) {
                            scope.launch {
                                try {
                                    supabase.postgrest["profiles"].update(
                                        mapOf("pseudonyme" to tempPseudo.trim())
                                    ) {
                                        filter { eq("id", userId) }
                                    }
                                    isEditingPseudo = false
                                    onStatsUpdated()
                                    Toast.makeText(context, "Pseudo mis à jour !", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmer", tint = NeonVolt)
                }
                IconButton(
                    onClick = {
                        tempPseudo = userPseudo
                        isEditingPseudo = false
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Annuler", tint = Color.Red)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = userPseudo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { isEditingPseudo = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier le pseudo",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = "Explorateur Actif",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Level details based on captured area
        val level = (allTimeArea * 10).toInt() + 1
        val nextLevelXpNeeded = 100
        val currentXp = ((allTimeArea * 1000) % 100).toInt()
        val progress = currentXp.toFloat() / nextLevelXpNeeded.toFloat()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Niveau $level",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$currentXp / $nextLevelXpNeeded XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonVolt,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${"%.2f".format(totalDistance)} km", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = NeonVolt, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Empire All-Time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${"%.4f".format(allTimeArea)} km²", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
            Card(
                modifier = Modifier.weight(1.0f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = ActiveOrange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Empire Actuel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${"%.4f".format(currentArea)} km²", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Empire Custom Color Picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "COULEUR DE L'EMPIRE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    // Color Preview Dot
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(localColor)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                ColorWheel(
                    selectedColor = localColor,
                    onColorSelected = { localColor = it },
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Option Card List (Clean, Premium look)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Partager ma position (Temps réel)") },
                    supportingContent = { Text("Permet aux autres joueurs de voir votre position sur la carte") },
                    trailingContent = {
                        Switch(
                            checked = shareLocationEnabled,
                            onCheckedChange = { shareLocationEnabled = it }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ListItem(
                    headlineContent = { Text("Notifications de capture") },
                    supportingContent = { Text("Alertes en cas de vol de territoire") },
                    trailingContent = {
                        var checked by remember { mutableStateOf(true) }
                        Switch(checked = checked, onCheckedChange = { checked = it })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ListItem(
                    headlineContent = { Text("Se déconnecter", color = Color.Red, fontWeight = FontWeight.Bold) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Déconnexion",
                            tint = Color.Red
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            try {
                                supabase.auth.signOut()
                                try {
                                    val file = File(context.filesDir, "local_territories.json")
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                } catch (ex: Exception) {
                                    android.util.Log.e("Arpent", "Failed to delete local cache on signout", ex)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erreur de déconnexion", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun GuildeScreen(
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
                tint = Color(0xFFE040FB)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB), contentColor = Color.White),
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
    var newClanColor by remember { mutableStateOf("#E040FB") }
    var newClanAvatarBase64 by remember { mutableStateOf<String?>(null) }
    var clanSearchQuery by remember { mutableStateOf("") }
    var allClansList by remember { mutableStateOf<List<ClanItem>>(emptyList()) }
    var isClanLoading by remember { mutableStateOf(true) }
    
    val colorsList = listOf("#FF1744", "#D500F9", "#2979FF", "#00E676", "#FFEA00", "#FF9100")

    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val base64 = uriToBase64(context, it)
            if (base64 != null) {
                newClanAvatarBase64 = base64
            }
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
                    val col = guildObj?.get("couleur_hex")?.jsonPrimitive?.contentOrNull ?: "#E040FB"
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
                        val col = obj["couleur_hex"]?.jsonPrimitive?.contentOrNull ?: "#E040FB"
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            isFriendsLoading = true
            loadFriendsData()
        } else {
            isClanLoading = true
            loadClanData()
        }
    }

    val guildeColorScheme = lightColorScheme(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.85f),
        onSurface = Color.Black,
        surfaceVariant = Color.White.copy(alpha = 0.95f),
        onSurfaceVariant = Color.Black,
        secondaryContainer = Color(0xFFE040FB).copy(alpha = 0.15f),
        onSecondaryContainer = Color.Black
    )

    MaterialTheme(colorScheme = guildeColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.65f))
                .padding(16.dp)
        ) {
            // Tab Selector (Amis, Mon Clan, Clans)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFE040FB),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("AMIS", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("MON CLAN", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("CLANS", fontWeight = FontWeight.Bold) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedTab == 0) {
                // AMIS TAB
                if (isFriendsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE040FB))
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
                                border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Ajouter un ami",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = friendPseudoInput,
                                            onValueChange = { friendPseudoInput = it; searchError = null },
                                            placeholder = { Text("Pseudonyme") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFFE040FB),
                                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Ajouter", color = Color.White)
                                        }
                                    }
                                    if (searchError != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(searchError!!, color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        
                        // Proximity suggestions
                        if (suggestedFriends.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Joueurs à proximité",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(suggestedFriends) { suggestion ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
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
                                                fontWeight = FontWeight.Bold
                                            )
                                            val distStr = if (suggestion.distanceMeters >= 1000.0) {
                                                "À %.1f km".format(suggestion.distanceMeters / 1000.0)
                                            } else {
                                                "À ${suggestion.distanceMeters.toInt()} m"
                                            }
                                            Text(
                                                text = distStr,
                                                fontSize = 11.sp,
                                                color = Color.Gray
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Ajouter", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Pending requests list
                        if (pendingRequests.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Demandes en attente",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(pendingRequests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
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
                                            }
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Accepter", tint = Color.Green)
                                        }
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
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Refuser", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Friends list
                        item {
                            Text(
                                text = "Mes amis (${friendsList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
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
                                    shape = RoundedCornerShape(12.dp)
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
                                            Text(text = friend.pseudo, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(try { Color(android.graphics.Color.parseColor(friend.color)) } catch(_: Exception) { Color.Green })
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = "Empire", fontSize = 11.sp, color = Color.Gray)
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
                        CircularProgressIndicator(color = Color(0xFFE040FB))
                    }
                } else if (clanId != null) {
                    // User belongs to a clan
                    val parsedClanColor = remember(clanCouleur) {
                        try { Color(android.graphics.Color.parseColor(clanCouleur)) } catch (_: Exception) { Color(0xFFE040FB) }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(2.dp, parsedClanColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = clanAvatar,
                                        modifier = Modifier.size(64.dp),
                                        placeholderColor = parsedClanColor,
                                        placeholderIcon = Icons.Default.Shield
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(text = clanNom ?: "", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "${clanMembers.size} membre(s)", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        
                        item {
                            Text(text = "Membres du clan", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        
                        items(clanMembers) { member ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = member.avatarUrl,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = member.pseudo,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (member.id == userId) {
                                        Text("Vous", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("QUITTER LE CLAN", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    // User has no clan: show creation form (and encourage them to look at CLANS tab to join one)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "Créer un clan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color.Gray.copy(alpha = 0.15f))
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
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                            onClick = { imageLauncher.launch("image/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f), contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Choisir logo", fontSize = 12.sp)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = newClanName,
                                        onValueChange = { newClanName = it },
                                        placeholder = { Text("Nom du clan") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFE040FB),
                                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Couleur du clan", fontSize = 12.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        colorsList.forEach { colorStr ->
                                            val c = Color(android.graphics.Color.parseColor(colorStr))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .border(
                                                        width = if (newClanColor == colorStr) 3.dp else 0.dp,
                                                        color = if (newClanColor == colorStr) Color.Black else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { newClanColor = colorStr }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            if (newClanName.trim().isEmpty()) return@Button
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val newGuildRes = supabase.postgrest["guildes"].insert(
                                                        mapOf(
                                                            "nom" to newClanName.trim(),
                                                            "couleur_hex" to newClanColor,
                                                            "avatar_url" to newClanAvatarBase64
                                                        )
                                                    ) {
                                                        select()
                                                    }
                                                    val guildArray = kotlinx.serialization.json.Json.parseToJsonElement(newGuildRes.data) as? kotlinx.serialization.json.JsonArray
                                                    val guildObj = guildArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                                                    val createdGuildId = guildObj?.get("id")?.jsonPrimitive?.content
                                                    
                                                    if (createdGuildId != null) {
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
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB)),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("CRÉER LE CLAN", fontWeight = FontWeight.Bold, color = Color.White)
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
                                    color = Color.Gray,
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
                        CircularProgressIndicator(color = Color(0xFFE040FB))
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
                            placeholder = { Text("Rechercher un clan...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE040FB),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                        
                        if (filteredClans.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucun clan trouvé.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredClans) { clan ->
                                    val parsedCColor = remember(clan.color) {
                                        try { Color(android.graphics.Color.parseColor(clan.color)) } catch (_: Exception) { Color(0xFFE040FB) }
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AvatarImage(
                                                avatarUrl = clan.avatarUrl,
                                                modifier = Modifier.size(40.dp),
                                                placeholderColor = parsedCColor,
                                                placeholderIcon = Icons.Default.Shield
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = clan.nom,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (clanId == null) {
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = parsedCColor),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Rejoindre", color = Color.White, fontSize = 12.sp)
                                                }
                                            } else if (clanId == clan.id) {
                                                Text(
                                                    text = "Votre clan",
                                                    fontWeight = FontWeight.Bold,
                                                    color = parsedCColor,
                                                    fontSize = 12.sp
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

// Data models for GuildeScreen
data class FriendItem(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?,
    val color: String
)

data class PendingRequestItem(
    val id: String,
    val senderId: String,
    val pseudo: String,
    val avatarUrl: String?
)

data class ProximitySuggestion(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?,
    val empireColor: String,
    val distanceMeters: Double
)

data class ClanMember(
    val id: String,
    val pseudo: String,
    val avatarUrl: String?
)

data class ClanItem(
    val id: String,
    val nom: String,
    val color: String,
    val avatarUrl: String?
)


