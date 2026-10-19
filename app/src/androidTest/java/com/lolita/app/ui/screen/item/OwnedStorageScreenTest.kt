package com.lolita.app.ui.screen.item

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.ui.theme.LolitaTheme
import com.lolita.app.ui.theme.SkinType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class OwnedStorageScreenTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db:LolitaDatabase
    private lateinit var vm:ItemEditViewModel
    private val store=ViewModelStore()
    private var shelf=0L
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        db.brandDao().insertBrand(Brand(id=1,name="受控测试品牌"))
        db.categoryDao().insertCategory(Category(id=1,name="JSK"))
        db.locationDao().insertLocation(Location(id=1,name="受控测试卧室"))
        shelf=StorageRepository(db).createNode(1,null,"受控测试衣柜")
        listOf(ItemStatus.OWNED,ItemStatus.PENDING_BALANCE).forEachIndexed {i,status->
            db.itemDao().insertItem(Item(id=i+1L,brandId=1,categoryId=1,name="受控主裙${i+1}",description="原备注",
                status=status,locationId=1,storageNodeId=shelf,
                specificationsJson=com.lolita.app.domain.model.WardrobeSpecifications().encode()))
        }
        compose.runOnIdle {
            vm=ItemEditViewModel(ItemRepository(db.itemDao(),database=db),BrandRepository(db.brandDao(),db.itemDao(),db),
                CategoryRepository(db.categoryDao(),db.itemDao(),db),CoordinateRepository(db.coordinateDao(),db.itemDao(),db),
                PriceRepository(db.priceDao(),db.paymentDao(),context,db),StyleRepository(db.styleDao(),db.itemDao(),db),
                SeasonRepository(db.seasonDao(),db.itemDao(),db),SourceRepository(db.sourceDao(),db.itemDao(),db),
                LocationRepository(db.locationDao(),db.itemDao()),storageRepository=StorageRepository(db))
            store.put("owned-storage",vm)
        }
    }
    @After fun close(){compose.runOnIdle {store.clear()};db.close()}
    private fun await(id:Long) {
        compose.waitForIdle() // Drive composition and its load effect before awaiting the Room state.
        runBlocking {withTimeout(10_000){vm.uiState.first{!it.isLoading&&it.item?.id==id}}}
        compose.waitForIdle()
    }
    @Test fun unpaidDetailAndEditorHidePathsAcrossSkinsWhileOwnedDetailsKeepNavigation() {
        val mode=mutableStateOf("detail");val id=mutableLongStateOf(1)
        val skin=mutableStateOf(SkinType.DEFAULT);val dark=mutableStateOf(false)
        val destinations=mutableListOf<Long>()
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {
                screenWidthDp=320
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides Density(LocalDensity.current.density,2f)) {
                LolitaTheme(skinType=skin.value,darkTheme=dark.value) {Surface {Box(Modifier.size(320.dp,600.dp)) {
                    if(mode.value=="detail") ItemDetailScreen(id.longValue,{}, {},onNavigateToLocation={destinations+=it},viewModel=vm)
                    else ItemEditScreen(id.longValue,onBack={},onSaveSuccess={},viewModel=vm)
                }}}
            }
        }
        await(1)
        compose.onNodeWithText("存放位置").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("受控测试卧室 → 受控测试衣柜").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(listOf(1L),destinations);id.longValue=2}
        await(2)
        val original=runBlocking{db.itemDao().getItemById(2)}
        for(page in listOf("detail","edit")) {
            compose.runOnIdle{mode.value=page};compose.waitForIdle();await(2)
            for(type in SkinType.entries) for(isDark in listOf(false,true)) {
                compose.runOnIdle{skin.value=type;dark.value=isDark}
                val anchor=compose.onNodeWithText(if(page=="detail") "新增小物" else "颜色：未设置")
                anchor.performScrollTo()
                val offset=(anchor.getUnclippedBoundsInRoot().top-compose.onRoot().getUnclippedBoundsInRoot().top-180.dp).value*
                    context.resources.displayMetrics.density
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollAction())
                    .performSemanticsAction(SemanticsActions.ScrollBy){it(0f,offset)}
                compose.mainClock.advanceTimeBy(1_000)
                anchor.assertIsDisplayed()
                compose.onNodeWithText("存放位置").assertDoesNotExist()
                compose.onNodeWithText("存放位置（可选）").assertDoesNotExist()
                compose.onNodeWithText("受控测试卧室 → 受控测试衣柜").assertDoesNotExist()
                val file=File(context.filesDir,"owned-storage/$page-${type.name}-${if(isDark) "dark" else "light"}.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        runBlocking{assertEquals(original,db.itemDao().getItemById(2))}
    }
    @Test fun newUnpaidSaveDropsEntryDefaultsAndExistingUnpaidEditRetainsLegacyReferences()=runBlocking {
        compose.runOnIdle{vm.loadItem(0,defaultStatus="PENDING_PAYMENT",defaultLocationId=1,defaultStorageNodeId=shelf)}
        withTimeout(10_000){vm.uiState.first{!it.isLoading&&it.status==ItemStatus.PENDING_PAYMENT}}
        compose.runOnIdle{vm.updateName("待付新物品");vm.updateBrand(1);vm.updateCategory(1);vm.updateStorage(2,null)}
        val id=withContext(Dispatchers.Main){vm.saveItem().getOrThrow()}
        val created=db.itemDao().getItemById(id)!!
        assertNull(created.locationId);assertNull(created.storageNodeId)
        compose.runOnIdle{vm.loadItem(2)};await(2)
        val original=db.itemDao().getItemById(2)!!
        compose.runOnIdle{vm.updateStorage(null,null);vm.updateLocation(2);vm.updateDescription("只更新备注")}
        withContext(Dispatchers.Main){vm.saveItem().getOrThrow()}
        val edited=db.itemDao().getItemById(2)!!
        assertEquals(original.copy(description="只更新备注",updatedAt=edited.updatedAt),edited)
    }
}
