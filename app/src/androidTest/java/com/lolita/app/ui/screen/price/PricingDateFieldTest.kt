package com.lolita.app.ui.screen.price

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.ui.theme.LolitaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class PricingDateFieldTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previousZone: TimeZone
    @Before fun setUp() { previousZone = TimeZone.getDefault() }
    @After fun tearDown() { TimeZone.setDefault(previousZone) }

    private fun confirmExistingDate(zone: String, date: String) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val localDayStart = LocalDate.parse(date).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli()
        var saved: Long? = null
        compose.setContent { LolitaTheme { PricingDateField("应付日期回归", localDayStart, { saved = it }) } }
        compose.onNode(hasClickAction() and hasText("应付日期回归")).performClick()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(localDayStart, saved) }
    }
    @Test fun shanghaiMonthStartRoundTripRetainsLocalDate() = confirmExistingDate("Asia/Shanghai", "2026-10-01")
    @Test fun westernMonthStartRoundTripRetainsLocalMidnight() = confirmExistingDate("America/Los_Angeles", "2026-10-01")
    @Test fun daylightSavingDayRoundTripRetainsLocalMidnight() = confirmExistingDate("America/New_York", "2026-03-08")
}
