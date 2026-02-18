package com.lolita.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lolita.app.di.AppModule
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appPreferences = AppModule.appPreferences()
            val skinType by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
            LolitaTheme(skinType = skinType) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LolitaNavHost()
                }
            }
        }
    }
}
