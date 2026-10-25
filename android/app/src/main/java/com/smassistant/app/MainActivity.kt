package com.smassistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smassistant.app.navigation.SmNavHost
import com.smassistant.app.ui.theme.SmAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmAssistantTheme {
                SmNavHost()
            }
        }
    }
}
