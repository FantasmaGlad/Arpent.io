package com.fanta.androidsport

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fanta.androidsport.ui.theme.SportAndroidTheme
import com.fanta.androidsport.ui.theme.ThemeManager
import com.fanta.androidsport.ui.screens.ArpentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mapbox.common.MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
        LocationTrackerState.restoreState(applicationContext)
        NotificationScheduler.scheduleNextAlarm(applicationContext)

        val prefs = getSharedPreferences("arpent_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("app_theme", "forest") ?: "forest"
        ThemeManager.themeState.value = savedTheme
        
        setContent {
            val currentTheme = ThemeManager.themeState.value
            SportAndroidTheme(theme = currentTheme) {
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

