package com.lolita.app.ui.screen.mannequin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.local.entity.*
import com.lolita.app.ui.screen.item.MannequinDetailEntry
import com.lolita.app.ui.theme.LolitaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MannequinDetailEntryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitDressesAndIndependentOrAttachedAccessoriesWithPicturesExposeEntry() {
        val category = mutableStateOf(Category(id = 2, name = "OP"))
        val item = mutableStateOf(Item(id = 42, name = "普通名称", brandId = 1, categoryId = 2, description = "", status = ItemStatus.OWNED, imageUrls = listOf("/photo.png")))
        var destination = 0L
        compose.setContent { LolitaTheme { MannequinDetailEntry(item.value, category.value) { destination = it } } }
        for (status in listOf(ItemStatus.OWNED, ItemStatus.WISHED, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE)) {
            compose.runOnIdle { item.value = item.value.copy(status = status) }
            compose.onNodeWithText("生成人台图（实验）").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(42L, destination) }
        }
        compose.runOnIdle { category.value = category.value.copy(group = CategoryGroup.ACCESSORY) }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
        for (parent in listOf(null, 99L)) {
            compose.runOnIdle { item.value = item.value.copy(parentItemId = parent) }
            compose.onNodeWithText("生成小物展示图（实验）").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(42L, destination) }
        }
        compose.runOnIdle { category.value = category.value.copy(name = "其他", group = CategoryGroup.CLOTHING); item.value = item.value.copy(name = "OP连衣裙") }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
        compose.onNodeWithText("生成小物展示图（实验）").assertDoesNotExist()
        compose.runOnIdle { category.value = category.value.copy(name = "连衣裙"); item.value = item.value.copy(imageUrls = emptyList()) }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
    }
}
