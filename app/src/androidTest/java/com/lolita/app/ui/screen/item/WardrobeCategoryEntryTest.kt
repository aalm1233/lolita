package com.lolita.app.ui.screen.item

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lolita.app.data.local.entity.Category
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.WardrobeFamily
import com.lolita.app.ui.theme.LolitaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WardrobeCategoryEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun footwearEntryStartsWithFootwearTypesAndAllowsChangingFamily() {
        var selected = 0L
        compose.setContent { LolitaTheme {
            WardrobeCategorySelector(0, listOf(
                Category(id = 1, name = "玛丽珍鞋", categorySemantic = CategorySemantic.MARY_JANE),
                Category(id = 2, name = "长卷假发", categorySemantic = CategorySemantic.FULL_WIG)
            ), { selected = it }, preferredFamily = WardrobeFamily.FOOTWEAR)
        } }
        compose.onNodeWithText("鞋履 · 选择类型 *").performClick()
        compose.onNodeWithText("玛丽珍鞋 · 鞋履").assertExists()
        compose.onNodeWithText("长卷假发 · 假发").assertDoesNotExist()
        compose.onNodeWithText("假发", substring = false).performClick()
        compose.onNodeWithText("长卷假发 · 假发").performClick()
        compose.runOnIdle { assertEquals(2L, selected) }
    }
}
