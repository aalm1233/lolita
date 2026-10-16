package com.lolita.app.ui.screen.item

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ItemCoverTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var editor: ItemEditViewModel
    private val store = ViewModelStore()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        db.brandDao().insertBrand(Brand(id=1,name="封面测试品牌"))
        db.categoryDao().insertCategory(Category(id=1,name="OP"))
        db.itemDao().insertItem(Item(id=1,brandId=1,categoryId=1,name="封面裙子",description="保留备注",
            status=ItemStatus.OWNED,imageUrls=listOf("/first.png","/generated.png","/third.png")))
        compose.runOnIdle {
            editor=ItemEditViewModel(ItemRepository(db.itemDao(),database=db),BrandRepository(db.brandDao(),db.itemDao(),db),
                CategoryRepository(db.categoryDao(),db.itemDao(),db),CoordinateRepository(db.coordinateDao(),db.itemDao(),db),
                PriceRepository(db.priceDao(),db.paymentDao(),context,db),StyleRepository(db.styleDao(),db.itemDao(),db),
                SeasonRepository(db.seasonDao(),db.itemDao(),db),SourceRepository(db.sourceDao(),db.itemDao(),db),
                LocationRepository(db.locationDao(),db.itemDao()))
            store.put("editor",editor)
        }
    }
    @After fun close() {
        compose.runOnIdle { store.clear() };db.close()
    }
    @Test fun swipingToGeneratedPhotoCanMakeItThePersistedCoverWithoutLosingOtherImages() {
        compose.setContent { LolitaTheme { ItemDetailScreen(1,{}, {},viewModel=editor) } }
        compose.waitUntil(5000) { !editor.uiState.value.isLoading && editor.uiState.value.item!=null }
        compose.onNodeWithText("当前封面").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("item-detail-images").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val previews=File(context.cacheDir,"item-cover-previews").apply { mkdirs() }
        File(previews,"before-cover.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithText("设为封面").assertIsEnabled().performClick()
        compose.waitUntil(5000) { runBlocking { db.itemDao().getItemById(1)!!.imageUrls.first()=="/generated.png" } }
        compose.onNodeWithText("当前封面").assertIsDisplayed().assertIsNotEnabled()
        File(previews,"after-cover.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        runBlocking {
            val saved=db.itemDao().getItemById(1)!!
            assertEquals(listOf("/generated.png","/first.png","/third.png"),saved.imageUrls)
            assertEquals("保留备注",saved.description)
        }
    }
}
