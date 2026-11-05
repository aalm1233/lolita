package com.lolita.app.ui.screen.square

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.data.square.*
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real first package is adb-pushed to /sdcard/Download/square-first.zip on a dedicated AVD. */
class SquareImportTest {
    @get:Rule val compose=createComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation();private val target=inst.targetContext
    private val testRoot=File(target.cacheDir,"square-${UUID.randomUUID()}")
    private val context=object:ContextWrapper(target){
        override fun getFilesDir()=File(testRoot,"files").apply{mkdirs()}
        override fun getCacheDir()=File(testRoot,"cache").apply{mkdirs()}
        override fun getNoBackupFilesDir()=File(testRoot,"no-backup").apply{mkdirs()}
    }
    private lateinit var db:LolitaDatabase;private lateinit var repo:SquareRepository;private lateinit var drafts:ItemDraftRepository
    private val store=ViewModelStore();private val skin=mutableStateOf(SkinType.CLASSIC);private val dark=mutableStateOf(false)
    private var vm:SquareViewModel?=null
    private lateinit var host:android.view.View
    private fun <T> io(block:suspend()->T)=runBlocking(Dispatchers.IO){block()}
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        drafts=ItemDraftRepository(db,ItemRepository(db.itemDao(),database=db,context=context));repo=SquareRepository(context,db,drafts)
        ImageFileHelper.configure(context,db)
        io{repo.load();db.brandDao().insertBrand(Brand(id=1,name="古典玩偶"));db.categoryDao().insertCategory(Category(id=1,name="JSK",categorySemantic=CategorySemantic.JSK))}
    }
    @After fun cleanup(){inst.runOnMainSync{store.clear()};io{db.close()};testRoot.deleteRecursively();ImageFileHelper.configure(target,com.lolita.app.di.AppModule.database())}
    private fun input():File {
        val file=File(target.cacheDir,"square-first-fixture.zip")
        if(!file.exists() || file.length()!=270998822L){
            val fd=inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-first.zip")
            FileInputStream(fd.fileDescriptor).use{input->file.outputStream().use{input.copyTo(it)}};fd.close()
        };assertEquals("Push the real first package before this test",270998822L,file.length());return file
    }
    private fun imported():SquareCatalog=io{val preview=repo.preview(Uri.fromFile(input())){};repo.apply(preview);repo.catalog.value!!}
    private fun show(){inst.runOnMainSync{vm=SquareViewModel(repo,SavedStateHandle());store.put("square",vm!!)}
        compose.setContent{val view=LocalView.current
            SideEffect{host=view;(view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
            val cfg=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides cfg,LocalViewModelStoreOwner provides object:ViewModelStoreOwner{override val viewModelStore=store}){
                LolitaTheme(skin.value,dark.value){Surface(color=MaterialTheme.colorScheme.background){Box(Modifier.fillMaxSize()){SkinArtBackground();SquareScreen({}, {},vm!!)}}}
            }
        };compose.mainClock.autoAdvance=true
    }
    private fun screenshot(name:String){compose.waitForIdle();val dir=File(target.filesDir,"square-native").apply{mkdirs()};val bitmap=inst.uiAutomation.takeScreenshot();File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}

    @Test fun realPackageReimportFailureAndCompleteWishDraftSurviveBackup() {
        val c=imported();assertEquals(3,c.shops.size);assertEquals(10,c.events.size);assertEquals(14,c.products.size);assertEquals(542,c.products.sumOf{it.skus.size});assertEquals(421,c.assets.size)
        val repeated=imported();assertEquals(14,repeated.products.size)
        val bad=File(context.cacheDir,"bad.zip");ZipOutputStream(bad.outputStream()).use{z->z.putNextEntry(ZipEntry("../bad.png"));z.write(byteArrayOf(1));z.closeEntry()}
        try{io{repo.preview(Uri.fromFile(bad)) {}};fail("bad ZIP must fail")}catch(expected:Exception){}
        assertEquals(c,repo.catalog.value);assertTrue(c.assets.all{File(repo.image(it.path)).exists()})
        val product=c.products.first{it.id=="1088022205740"};val sku=product.skus.first{it.content=="JSK"&&it.color=="蓝色"&&it.size=="M"}
        val r=io{repo.prepare("dorothy-cotton",sku.key,null)};val draft=io{drafts.get(r.draftId!!)!!};val data=draft.data()
        assertEquals(ItemStatus.WISHED,data.status);assertEquals("M",data.size);assertEquals(listOf("蓝色"),data.colors)
        assertEquals(54,draft.sizeChartImagePaths.size);assertTrue(draft.imagePaths.isNotEmpty());assertTrue((draft.imagePaths+draft.sizeChartImagePaths).all{File(it).isFile && !it.contains("square_catalog")})
        assertEquals(r,io{repo.prepare("dorothy-cotton",sku.key,null)});assertEquals(1,io{db.itemDraftDao().getAll().size})
        val item=Item(brandId=1,categoryId=1,name=data.name,description=data.description,status=data.status,size=data.size,colors=data.colors,
            style=data.style,source=data.source,composition=data.composition,importToken=data.importToken,imageUrls=draft.imagePaths,
            sizeChartImageUrl=draft.sizeChartImagePaths.first(),sizeChartImageUrls=draft.sizeChartImagePaths.drop(1))
        val id=io{drafts.commit(draft.id,item)};assertEquals(id,io{repo.prepare("dorothy-cotton",sku.key,null).itemId})
        val deposit=c.products.first{it.id=="1090713386610"}.skus.first{it.content=="JSK"&&it.color=="蓝色"&&it.size=="M"}
        assertEquals(sku.variantId,deposit.variantId);assertEquals(id,io{repo.prepare("dorothy-cotton",deposit.key,null).itemId})
        assertEquals(0,io{db.priceDao().getAllPricesList().size})
        val before=io{db.itemDao().getItemById(id)!!}
        val manager=BackupManager(context,db)
        val exported=io{manager.exportToZip().getOrThrow()};io{manager.previewBackup(exported).getOrThrow();manager.importBackup(exported).getOrThrow()}
        val after=io{db.itemDao().getItemById(id)!!};assertEquals(before.copy(imageUrls=after.imageUrls,sizeChartImageUrl=after.sizeChartImageUrl,sizeChartImageUrls=after.sizeChartImageUrls),after)
        assertEquals(54,listOfNotNull(after.sizeChartImageUrl).size+after.sizeChartImageUrls.size)
        assertTrue((after.imageUrls+listOfNotNull(after.sizeChartImageUrl)+after.sizeChartImageUrls).all{File(it).exists()})
        val combination=product.skus.first{it.parts.size>1};val secondary=combination.parts[1]
        val separate=io{repo.prepare("dorothy-cotton",combination.key,secondary.key)};val part=io{drafts.get(separate.draftId!!)!!}.data()
        assertNull(part.size);assertTrue(part.colors.isEmpty());assertEquals(ItemStatus.WISHED,part.status)
        val tail=c.products.first{it.id=="1089219231672"}
        try{io{repo.prepare("pilgrim-balance",tail.skus.first().key,null)};fail("Tail must stay read only")}catch(expected:IllegalArgumentException){}
    }

    @Test fun nativeFeedSelectionImagesAndFourteenSkinVariants(){val c=imported();show();compose.waitUntil(15000){vm!!.busy.value==null}
        val small=target.resources.configuration.screenWidthDp<=320
        for(type in SkinType.entries)for(night in listOf(false,true)){
            compose.runOnIdle{skin.value=type;dark.value=night;vm!!.back()}
            compose.onNodeWithTag("square-feed").assertExists();screenshot("${type.name.lowercase()}-${if(night)"dark" else "light"}${if(small)"-small" else ""}-feed")
            compose.runOnIdle{vm!!.open("dorothy-cotton")};compose.onNodeWithText("选择规格，加入衣橱").assertIsDisplayed()
            screenshot("${type.name.lowercase()}-${if(night)"dark" else "light"}${if(small)"-small" else ""}-detail")
            compose.onNodeWithText("选择规格，加入衣橱").performClick();compose.onNodeWithText("核对并登记到心愿").assertIsNotEnabled()
            val sku=c.products.first{it.id=="1088022205740"}.skus.first{it.color=="蓝色"&&it.content=="JSK"&&it.size=="M"}
            compose.runOnIdle{vm!!.selectSku(sku.key)};compose.onNodeWithText("核对并登记到心愿").assertIsEnabled()
            screenshot("${type.name.lowercase()}-${if(night)"dark" else "light"}${if(small)"-small" else ""}-selection")
            compose.runOnIdle{vm!!.back();vm!!.back()}
        }
        compose.runOnIdle{vm!!.open("pilgrim-balance")};compose.onNodeWithText("选择规格，加入衣橱").assertDoesNotExist();screenshot("tail-read-only${if(small)"-small" else ""}")
        compose.runOnIdle{vm!!.back();vm!!.set("color","浅蓝色");vm!!.set("form","小物")};compose.onNodeWithText("没有符合条件的动态").assertExists()
    }

    @Test fun firstUseImportEntryUsesRealSkinAndLargeTextLayout(){show();compose.waitUntil(15000){vm!!.busy.value==null}
        for(type in SkinType.entries)for(night in listOf(false,true)){
            compose.runOnIdle{skin.value=type;dark.value=night};compose.onNodeWithTag("square-import").assertExists();screenshot("empty-${type.name.lowercase()}-${if(night)"dark" else "light"}${if(target.resources.configuration.screenWidthDp<=320)"-small" else ""}")
        }
    }
    @Test fun realSmallDoubleFontKeepsSelectionAndKeyboardActionReachable(){
        Assume.assumeTrue(target.resources.configuration.screenWidthDp<=320 && target.resources.configuration.fontScale>=1.99f)
        val c=imported();show();compose.waitUntil(15000){vm!!.busy.value==null}
        compose.runOnIdle{vm!!.open("dorothy-cotton");vm!!.choose();vm!!.selectSku(c.products.first{it.id=="1088022205740"}.skus.first{it.content=="JSK"&&it.size=="M"}.key)}
        compose.onNodeWithTag("square-selection").performScrollToNode(hasText("搜索款式、颜色或尺码"))
        compose.onNodeWithText("搜索款式、颜色或尺码").performClick().performTextInput("JSK")
        compose.waitUntil(10000){ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime())==true}
        compose.onNodeWithText("核对并登记到心愿").assertIsDisplayed().assertIsEnabled()
        screenshot("small-double-font-real-ime")
    }

    @Test fun neoSeparateShirtAndAccessoriesLinkRemainsSelectable(){
        val c=imported();show();compose.waitUntil(15000){vm!!.busy.value==null}
        compose.runOnIdle{vm!!.open("pilgrim-repeat");vm!!.choose()}
        val second=c.products.first{it.id=="1060482620961"}
        compose.onNodeWithTag("square-selection").performScrollToNode(hasText(second.title))
        compose.onNodeWithText(second.title).performClick()
        compose.runOnIdle{assertEquals(second.id,vm!!.productId.value)}
        val sku=second.skus.first()
        compose.runOnIdle{vm!!.selectSku(sku.key)}
        compose.onNodeWithText("核对并登记到心愿").assertIsEnabled()
        screenshot("neo-second-link-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
    }

}
