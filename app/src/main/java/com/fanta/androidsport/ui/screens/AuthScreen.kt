package com.fanta.androidsport.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.fanta.androidsport.supabase
import com.fanta.androidsport.ui.theme.ActiveOrange
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

// Deep green from the Connexion.svg artwork (#253D2C) used for texts over the white buttons
private val AuthGreen = Color(0xFF253D2C)
private val AuthButtonWhite = Color.White.copy(alpha = 0.7f)

// Fullscreen looping animated background rendered from assets/Connexion.svg
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConnexionAnimatedBackground(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
                    }
                }

                setBackgroundColor(android.graphics.Color.WHITE)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                loadUrl("https://appassets.androidplatform.net/assets/connexion.html")
            }
        },
        modifier = modifier
    )
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
        modifier = Modifier.fillMaxSize()
    ) {
        // Animated SVG background (loops indefinitely, branding included in the artwork)
        ConnexionAnimatedBackground(modifier = Modifier.fillMaxSize())

        // Auth controls anchored on the lower half, below the animated logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {

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
                            color = AuthGreen,
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
                            containerColor = AuthButtonWhite,
                            contentColor = AuthGreen
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
                            containerColor = AuthButtonWhite,
                            contentColor = AuthGreen
                        ),
                        shape = RoundedCornerShape(50),
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

                    Button(
                        onClick = {
                            errorMessage = null
                            authMode = "login"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuthButtonWhite,
                            contentColor = AuthGreen
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Déjà un compte ? Se connecter",
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AuthButtonWhite,
                            unfocusedContainerColor = AuthButtonWhite,
                            focusedBorderColor = AuthGreen,
                            unfocusedBorderColor = Color.Transparent,
                            focusedLabelColor = AuthGreen,
                            unfocusedLabelColor = AuthGreen.copy(alpha = 0.6f),
                            focusedTextColor = AuthGreen,
                            unfocusedTextColor = AuthGreen,
                            cursorColor = AuthGreen
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
                            containerColor = AuthButtonWhite,
                            contentColor = AuthGreen
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(color = AuthGreen, modifier = Modifier.size(24.dp))
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
                            color = AuthGreen.copy(alpha = 0.7f)
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AuthButtonWhite,
                            unfocusedContainerColor = AuthButtonWhite,
                            focusedBorderColor = AuthGreen,
                            unfocusedBorderColor = Color.Transparent,
                            focusedLabelColor = AuthGreen,
                            unfocusedLabelColor = AuthGreen.copy(alpha = 0.6f),
                            focusedTextColor = AuthGreen,
                            unfocusedTextColor = AuthGreen,
                            cursorColor = AuthGreen
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
                                    tint = AuthGreen.copy(alpha = 0.6f)
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AuthButtonWhite,
                            unfocusedContainerColor = AuthButtonWhite,
                            focusedBorderColor = AuthGreen,
                            unfocusedBorderColor = Color.Transparent,
                            focusedLabelColor = AuthGreen,
                            unfocusedLabelColor = AuthGreen.copy(alpha = 0.6f),
                            focusedTextColor = AuthGreen,
                            unfocusedTextColor = AuthGreen,
                            cursorColor = AuthGreen
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
                                    val msg = e.message ?: ""
                                    errorMessage = if (authMode == "login" && (
                                                msg.contains("invalid login credentials", ignoreCase = true) ||
                                                msg.contains("invalid_credentials", ignoreCase = true) ||
                                                msg.contains("invalid_grant", ignoreCase = true) ||
                                                msg.contains("invalid email or password", ignoreCase = true)
                                            )) {
                                        "Mot de passe erroné"
                                    } else {
                                        "Erreur d'authentification : ${e.localizedMessage}"
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuthButtonWhite,
                            contentColor = AuthGreen
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(color = AuthGreen, modifier = Modifier.size(24.dp))
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
                            color = AuthGreen.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
