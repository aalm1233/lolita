package com.lolita.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.di.AppModule
import com.lolita.app.ui.navigation.LolitaNavHost
import com.lolita.app.ui.screen.settings.AppUpdateHost
import com.lolita.app.ui.screen.settings.AppUpdateViewModel
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType

class MainActivity : ComponentActivity() {
    private var openQuickOutfit by mutableStateOf(false)
    private var openImageTasks by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntent(intent)
    }

    private fun consumeReminderIntent(intent: Intent) {
        if (intent.getStringExtra("navigate_to") == "image_tasks") {
            openImageTasks = true
            intent.removeExtra("navigate_to")
        }
        if (intent.getStringExtra("navigate_to") == "quick_outfit_log") {
            openQuickOutfit = true
            intent.removeExtra("navigate_to")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_quick_outfit", openQuickOutfit)
        outState.putBoolean("pending_image_tasks", openImageTasks)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeReminderIntent(intent)
        else {
            openQuickOutfit = savedInstanceState.getBoolean("pending_quick_outfit")
            openImageTasks = savedInstanceState.getBoolean("pending_image_tasks")
            intent.removeExtra("navigate_to")
        }
        setContent {
            val appUpdateViewModel: AppUpdateViewModel = viewModel()
            val appPreferences = AppModule.appPreferences()
            val skinType by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
            LolitaTheme(skinType = skinType) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LolitaNavHost(
                        openQuickOutfit = openQuickOutfit,
                        openImageTasks = openImageTasks,
                        onImageTasksOpened = { openImageTasks = false },
                        onQuickOutfitOpened = { openQuickOutfit = false },
                        appUpdateViewModel = appUpdateViewModel
                    )
                    AppUpdateHost(appUpdateViewModel)
                }
            }
        }
    }
}
