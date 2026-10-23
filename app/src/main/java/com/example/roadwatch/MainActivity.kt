package com.example.roadwatch

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import com.example.roadwatch.data.auth.AuthViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.roadwatch.ui.navigation.RoadWatchApp
import com.example.roadwatch.ui.theme.RoadWatchTheme

class MainActivity : ComponentActivity() {
    private val auth by lazy { ViewModelProvider(this)[AuthViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth.handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            RoadWatchTheme { RoadWatchApp(auth) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        auth.handleIntent(intent)
    }
}
