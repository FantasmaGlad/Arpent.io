package com.fanta.androidsport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fanta.androidsport.ui.theme.SportAndroidTheme
import com.fanta.androidsport.ui.screens.ArpentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mapbox.common.MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
        LocationTrackerState.restoreState(applicationContext)
        
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
