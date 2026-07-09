package com.fanta.androidsport.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanta.androidsport.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay

@Composable
fun ArpentApp() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle(
        initialValue = SessionStatus.Initializing
    )

    // Enforce a minimum display duration of 3.5 seconds for the LoadingScreen to allow
    // the WebView to initialize and display the animated SVG loader.
    var isMinLoadingTimeoutFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3500)
        isMinLoadingTimeoutFinished = true
    }

    val showLoading = sessionStatus is SessionStatus.Initializing || !isMinLoadingTimeoutFinished

    if (showLoading) {
        LoadingScreen()
    } else {
        when (sessionStatus) {
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
            else -> {
                LoadingScreen()
            }
        }
    }
}
