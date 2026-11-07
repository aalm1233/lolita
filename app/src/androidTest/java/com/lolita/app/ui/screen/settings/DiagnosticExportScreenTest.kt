package com.lolita.app.ui.screen.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.data.repository.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.*
import org.junit.*
import java.io.File
import java.util.UUID

class DiagnosticExportScreenTest {
    @get:Rule val compose = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext
    private val root = File(target.cacheDir, "diagnostic-ui-${UUID.randomUUID()}").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: LolitaDatabase
    private val skin = mutableStateOf(SkinType.DEFAULT)
    private val dark = mutableStateOf(false)
    @Before fun show() {
        db = Room.inMemoryDatabaseBuilder(target, LolitaDatabase::class.java).build()
        val preferences = AppPreferences(target, PreferenceDataStoreFactory.create(scope = scope) { File(root, "test.preferences_pb") })
        lateinit var settings: SettingsViewModel
        lateinit var updates: AppUpdateViewModel
        inst.runOnMainSync {
            settings = SettingsViewModel(preferences, ItemRepository(db.itemDao(), database = db), CoordinateRepository(db.coordinateDao(), db.itemDao(), db), PriceRepository(db.priceDao(), db.paymentDao(), target, db))
            updates = AppUpdateViewModel(checkUpdate = { error("No network in isolated UI tests") })
            store.put("settings", settings); store.put("updates", updates)
        }
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config) {
                LolitaTheme(skin.value, dark.value) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box { SkinArtBackground(); SettingsScreen({}, {}, appPreferences = preferences, settingsViewModel = settings, appUpdateViewModel = updates) }
                } }
            }
        }
    }
    @After fun close() { inst.runOnMainSync { store.clear() }; scope.cancel(); db.close(); root.deleteRecursively() }
    private fun shot(name: String) {
        compose.waitForIdle(); inst.waitForIdleSync()
        val directory = File(target.filesDir, "diagnostic-native").apply { mkdirs() }
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun actualPersonalPageOffersDiagnosticExportAcrossFourteenSkins() {
        for (type in SkinType.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { skin.value = type; dark.value = night }
            compose.onNodeWithTag("diagnostic-export").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("设备、异常位置与内存状态；不含衣橱内容").assertIsDisplayed()
            shot("diagnostics-${type.name.lowercase()}-${if (night) "dark" else "light"}")
        }
    }
    @Test fun smallLargeFontPersonalEntryOpensRealSystemPickerAndCancellationReturns() {
        compose.onNodeWithTag("diagnostic-export").performScrollTo().assertIsDisplayed()
        shot("diagnostics-small-large-font")
        compose.onNodeWithTag("diagnostic-export").performClick()
        // A real ACTION_CREATE_DOCUMENT activity, not a mocked ActivityResultRegistry.
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline && inst.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.google.android.documentsui") Thread.sleep(100)
        Assert.assertEquals("com.google.android.documentsui", inst.uiAutomation.rootInActiveWindow?.packageName?.toString())
        inst.uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.onNodeWithTag("diagnostic-export").assertIsDisplayed()
        shot("diagnostics-small-picker-cancelled")
    }
}
