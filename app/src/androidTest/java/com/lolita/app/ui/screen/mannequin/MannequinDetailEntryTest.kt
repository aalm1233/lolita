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
    @Test fun ordinaryDressesAndAccessoriesNoLongerExposeLegacyGenerationEntry() {
        val category = mutableStateOf(Category(id=2,name="改名类型",categorySemantic=CategorySemantic.OP))
        val item = Item(id=42,name="物品",description="",brandId=1,categoryId=2,status=ItemStatus.OWNED,imageUrls=listOf("/photo.png"))
        compose.setContent { LolitaTheme { MannequinDetailEntry(item,category.value) {} } }
        for (semantic in listOf(CategorySemantic.OP,CategorySemantic.BLOUSE,CategorySemantic.KC,CategorySemantic.HAIR_PIECE,CategorySemantic.MARY_JANE)) {
            compose.runOnIdle { category.value=category.value.copy(categorySemantic=semantic) }
            compose.onNodeWithText("生成人台图（实验）").assertDoesNotExist()
            compose.onNodeWithText("生成衬衫人台图").assertDoesNotExist()
            compose.onNodeWithText("生成单品展示图").assertDoesNotExist()
            compose.onNodeWithText("生成假发头模图").assertDoesNotExist()
        }
    }
    @Test fun fullWigKeepsIndependentHeadEntryWithAnyProductReferenceRegardlessOfNameOrGroup() {
        val category=mutableStateOf(Category(id=2,name="用户改名",categorySemantic=CategorySemantic.FULL_WIG))
        val item=mutableStateOf(Item(id=42,name="普通名字",description="",brandId=1,categoryId=2,status=ItemStatus.WISHED,imageUrls=listOf("/photo.png")))
        var destination=0L
        compose.setContent { LolitaTheme { MannequinDetailEntry(item.value,category.value) { destination=it } } }
        for (group in CategoryGroup.entries) {
            compose.runOnIdle { category.value=category.value.copy(group=group) }
            compose.onNodeWithText("生成假发头模图").assertIsDisplayed().performClick()
            assertEquals(42L,destination)
        }
        compose.runOnIdle { item.value=item.value.copy(imageUrls=emptyList(),sizeChartImageUrls=listOf("/detail.png")) }
        compose.onNodeWithText("生成假发头模图").assertIsDisplayed()
        compose.runOnIdle { item.value=item.value.copy(sizeChartImageUrls=emptyList()) }
        compose.onNodeWithText("生成假发头模图").assertDoesNotExist()
    }
}
