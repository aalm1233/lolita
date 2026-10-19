package com.lolita.app.ui.screen.mannequin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.CategorySemantic
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
        val category = mutableStateOf(Category(id = 2, name = "OP", categorySemantic = CategorySemantic.OP))
        val item = mutableStateOf(Item(id = 42, name = "普通名称", brandId = 1, categoryId = 2, description = "", status = ItemStatus.OWNED, imageUrls = listOf("/photo.png")))
        var destination = 0L
        compose.setContent { LolitaTheme { MannequinDetailEntry(item.value, category.value) { destination = it } } }
        for (status in listOf(ItemStatus.OWNED, ItemStatus.WISHED, ItemStatus.PENDING_PAYMENT, ItemStatus.PENDING_BALANCE)) {
            compose.runOnIdle { item.value = item.value.copy(status = status) }
            compose.onNodeWithText("生成人台图（实验）").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(42L, destination) }
        }
        compose.runOnIdle { category.value = category.value.copy(group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.KC) }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
        for (parent in listOf(null, 99L)) {
            compose.runOnIdle { item.value = item.value.copy(parentItemId = parent) }
            compose.onNodeWithText("生成单品展示图").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(42L, destination) }
        }
        compose.runOnIdle { category.value = category.value.copy(name = "其他", group = CategoryGroup.CLOTHING, categorySemantic = CategorySemantic.UNCONFIRMED); item.value = item.value.copy(name = "OP连衣裙") }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
        compose.onNodeWithText("生成单品展示图").assertDoesNotExist()
        compose.runOnIdle { category.value = category.value.copy(name = "连衣裙", categorySemantic = CategorySemantic.OP); item.value = item.value.copy(imageUrls = emptyList()) }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
        compose.runOnIdle { item.value = item.value.copy(sizeChartImageUrl = "/legacy-detail.png") }
        compose.onNodeWithText("生成人台图（实验）").assertIsDisplayed()
        compose.runOnIdle { item.value = item.value.copy(sizeChartImageUrl = null, sizeChartImageUrls = listOf("/additional-detail.png")) }
        compose.onNodeWithText("生成人台图（实验）").assertIsDisplayed()
        compose.runOnIdle { item.value = item.value.copy(sizeChartImageUrls = emptyList()) }
        compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
    }

    @Test fun stableBlouseAndOtherModesUseTheirOwnLabelsRegardlessOfLegacyGroup() {
        val category = mutableStateOf(Category(id = 2, name = "用户的衬衫", categorySemantic = CategorySemantic.BLOUSE))
        val item = mutableStateOf(Item(id = 42, name = "待展示物品", description = "", brandId = 1, categoryId = 2,
            status = ItemStatus.OWNED, imageUrls = listOf("/photo.png")))
        var destination = 0L
        compose.setContent { LolitaTheme { MannequinDetailEntry(item.value, category.value) { destination = it } } }
        for (group in CategoryGroup.entries) {
            compose.runOnIdle { category.value = category.value.copy(group = group) }
            compose.onNodeWithText("生成衬衫人台图").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(42L, destination) }
        }
        for (semantic in listOf(CategorySemantic.TROUSERS, CategorySemantic.OUTERWEAR, CategorySemantic.CAPE,
            CategorySemantic.SHAWL, CategorySemantic.OTHER_CLOTHING, CategorySemantic.UNCONFIRMED)) {
            compose.runOnIdle { category.value = category.value.copy(categorySemantic = semantic) }
            compose.onNodeWithText("生成衬衫人台图").assertDoesNotExist()
        }
        compose.runOnIdle { category.value = category.value.copy(group = CategoryGroup.CLOTHING, categorySemantic = CategorySemantic.MARY_JANE) }
        compose.onNodeWithText("生成单品展示图").assertIsDisplayed()
        compose.runOnIdle { category.value = category.value.copy(group = CategoryGroup.ACCESSORY, categorySemantic = CategorySemantic.FULL_WIG) }
        compose.onNodeWithText("生成假发头模图").assertIsDisplayed()
        compose.runOnIdle { item.value = item.value.copy(imageUrls = emptyList()) }
        compose.onNodeWithText("生成假发头模图").assertDoesNotExist()
    }
}
