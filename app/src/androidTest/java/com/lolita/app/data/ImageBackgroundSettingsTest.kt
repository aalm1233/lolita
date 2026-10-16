package com.lolita.app.data

import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.ui.screen.common.imageBackgroundSettingsIntent
import com.lolita.app.ui.screen.common.readImageBackgroundStatus
import org.junit.Assert.*
import org.junit.Test

class ImageBackgroundSettingsTest {
    @Test fun settingsUseStandardUserDrivenDestinationsAndStatusIsReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val battery = imageBackgroundSettingsIntent(context, true)
        val app = imageBackgroundSettingsIntent(context, false)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, battery.action)
        assertNull(battery.component)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app.action)
        assertEquals("package:com.lolita.app", app.data.toString())
        assertNull(app.component)
        assertNotNull(battery.resolveActivity(context.packageManager))
        assertNotNull(app.resolveActivity(context.packageManager))
        val status = readImageBackgroundStatus(context)
        assertFalse(status.network.contains("无法读取"))
        assertFalse(status.battery.contains("无法读取"))
        assertFalse(status.background.contains("无法读取"))
    }
}
