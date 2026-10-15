package com.example.roadwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.roadwatch.ui.navigation.RoadWatchApp
import com.example.roadwatch.ui.theme.RoadWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadWatchTheme { RoadWatchApp() }
        }
    }
}
