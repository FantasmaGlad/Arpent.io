package com.fanta.androidsport.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanta.androidsport.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

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
