package com.lolita.app.ui.screen.square

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.navigation.compose.composable
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
    private val detailVm=mutableStateOf<com.lolita.app.ui.screen.item.ItemEditViewModel?>(null)
    private lateinit var host:android.view.View
    private fun <T> io(block:suspend()->T)=runBlocking(Dispatchers.IO){block()}
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        drafts=ItemDraftRepository(db,ItemRepository(db.itemDao(),database=db,context=context));repo=SquareRepository(context,db,drafts,defaultServiceUrl="")
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
                LolitaTheme(skin.value,dark.value){Surface(color=MaterialTheme.colorScheme.background){Box(Modifier.fillMaxSize()){SkinArtBackground();if(detailVm.value==null)SquareScreen({}, {},vm!!) else com.lolita.app.ui.screen.item.ItemDetailScreen(1,{}, {},viewModel=detailVm.value!!)}}}
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
        assertEquals(0,draft.sizeChartImagePaths.size);assertTrue(draft.imagePaths.isEmpty());assertTrue((draft.imagePaths+draft.sizeChartImagePaths).all{File(it).isFile && !it.contains("square_catalog")})
        assertEquals(r,io{repo.prepare("dorothy-cotton",sku.key,null)});assertEquals(1,io{db.itemDraftDao().getAll().size})
        val item=Item(brandId=1,categoryId=1,name=data.name,description=data.description,status=data.status,size=data.size,colors=data.colors,
            style=data.style,source=data.source,composition=data.composition,importToken=data.importToken,imageUrls=draft.imagePaths,
            sizeChartImageUrl=draft.sizeChartImagePaths.firstOrNull(),sizeChartImageUrls=draft.sizeChartImagePaths.drop(1))
        val id=io{drafts.commit(draft.id,item)};assertEquals(id,io{repo.prepare("dorothy-cotton",sku.key,null).itemId})
        val deposit=c.products.first{it.id=="1090713386610"}.skus.first{it.content=="JSK"&&it.color=="蓝色"&&it.size=="M"}
        assertEquals(sku.variantId,deposit.variantId);assertEquals(id,io{repo.prepare("dorothy-cotton",deposit.key,null).itemId})
        assertEquals(0,io{db.priceDao().getAllPricesList().size})
        val before=io{db.itemDao().getItemById(id)!!}
        val manager=BackupManager(context,db)
        val exported=io{manager.exportToZip().getOrThrow()};io{manager.previewBackup(exported).getOrThrow();manager.importBackup(exported).getOrThrow()}
        val after=io{db.itemDao().getItemById(id)!!};assertEquals(before.copy(imageUrls=after.imageUrls,sizeChartImageUrl=after.sizeChartImageUrl,sizeChartImageUrls=after.sizeChartImageUrls),after)
        assertEquals(0,listOfNotNull(after.sizeChartImageUrl).size+after.sizeChartImageUrls.size)
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
            compose.onNodeWithText("选择规格，加入衣橱").performClick();compose.onNodeWithText("选择部件").assertIsNotEnabled()
            val sku=c.products.first{it.id=="1088022205740"}.skus.first{it.color=="蓝色"&&it.content=="JSK"&&it.size=="M"}
            compose.runOnIdle{vm!!.selectSku(sku.key)};compose.onNodeWithText("选择部件").assertIsEnabled()
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
        compose.onNodeWithText("选择部件").assertIsDisplayed().assertIsEnabled()
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
        compose.onNodeWithText("选择部件").assertIsEnabled()
        screenshot("neo-second-link-${if(target.resources.configuration.screenWidthDp<=320)"small" else "normal"}")
    }

    private fun refined():SquareCatalog=io {
        val file=File(context.cacheDir,"refined.zip")
        val fd=inst.uiAutomation.executeShellCommand("cat /sdcard/Download/square-refined.zip")
        FileInputStream(fd.fileDescriptor).use { input ->file.outputStream().use{input.copyTo(it)} };fd.close()
        val preview=repo.preview(Uri.fromFile(file)){};repo.apply(preview);repo.catalog.value!!
    }

    @Test fun refinedComponentImagesCustomGroupsAndPausedSelectionStayIndependent(){
        imported();val c=refined();val p=c.products.first{it.id=="1088022205740"}
        val sku=p.skus.first{it.content=="JSK+KC" && it.color=="蓝色" && it.size=="M"}
        val jsk=sku.parts.first{it.key=="JSK"};val kc=sku.parts.first{it.key=="KC"}
        val a=p.suggestUnit(sku,listOf(jsk),"单品");val b=p.suggestUnit(sku,listOf(kc),"单品")
        assertEquals(1,a.photos.size);assertEquals(1,b.photos.size);assertNotEquals(a.photos,b.photos)
        assertEquals(1,a.documents.size);assertEquals(1,b.documents.size);assertNotEquals(a.documents,b.documents)
        val first=io{repo.prepare("dorothy-cotton",sku.key,a)};val second=io{repo.prepare("dorothy-cotton",sku.key,b)}
        val da=io{drafts.get(first.draftId!!)!!};val dbb=io{drafts.get(second.draftId!!)!!}
        assertEquals(1,da.imagePaths.size);assertEquals(1,dbb.imagePaths.size)
        assertEquals(SquarePackage.gson.toJson(a.photos.map{File(repo.image(it)).readBytes().contentHashCode()}),SquarePackage.gson.toJson(da.imagePaths.map{File(it).readBytes().contentHashCode()}))
        assertNull(dbb.data().size);assertTrue(dbb.data().colors.isEmpty())
        assertTrue((da.imagePaths+da.sizeChartImagePaths+dbb.imagePaths+dbb.sizeChartImagePaths).distinct().size==4)
        assertFalse(dbb.data().description.contains("版型与细节：小微高腰"))
        val three=p.skus.first{it.parts.size==3 && it.color=="蓝色" && it.size=="M"}
        val custom=p.suggestUnit(three,three.parts.filter{it.key in listOf("JSK","KC")},"服饰组合")
        val grouped=io{repo.prepare("dorothy-cotton",three.key,custom)}
        val data=io{drafts.get(grouped.draftId!!)!!}.data()
        assertEquals(com.lolita.app.domain.model.ItemComposition.SET,data.composition);assertFalse(data.description.substringBefore("来源与购买快照").contains("包含部件：JSK、KC、内搭"))
        assertEquals(0,io{db.priceDao().getAllPricesList().size})
        val saved=SavedStateHandle();lateinit var firstVm:SquareViewModel
        inst.runOnMainSync{firstVm=SquareViewModel(repo,saved);store.put("plan",firstVm)}
        compose.waitUntil(10000){firstVm.busy.value==null}
        compose.runOnIdle{firstVm.open("dorothy-cotton");firstVm.choose();firstVm.selectSku(sku.key);firstVm.next();firstVm.next();firstVm.next()}
        assertEquals(2,firstVm.units().size);assertEquals(3,firstVm.step.value)
        val snapshot=saved.keys().associateWith{saved.get<Any?>(it)}
        lateinit var restored:SquareViewModel
        inst.runOnMainSync{restored=SquareViewModel(repo,SavedStateHandle(snapshot));store.put("restored",restored)}
        assertEquals(firstVm.units(),restored.units());assertEquals(sku.key,restored.skuKey.value);assertEquals(3,restored.step.value)
        val exported=io{BackupManager(context,db).exportToZip().getOrThrow()}
        io{BackupManager(context,db).importBackup(exported).getOrThrow()}
        assertEquals(3,io{db.itemDraftDao().getAll().size});assertTrue(io{db.itemDraftDao().getAll()}.flatMap{it.protectedImagePaths()}.all{File(it).isFile})
    }

    @Test fun refinedNativeSplitImageAssignmentAndPreview(){val c=refined();show();compose.waitUntil(15000){vm!!.busy.value==null}
        val p=c.products.first{it.id=="1088022205740"};val sku=p.skus.first{it.content=="JSK+KC" && it.color=="蓝色" && it.size=="M"}
        for(type in SkinType.entries)for(night in listOf(false,true)){
            val suffix="${type.name.lowercase()}-${if(night)"dark" else "light"}${if(target.resources.configuration.screenWidthDp<=320)"-small" else ""}"
            compose.runOnIdle{skin.value=type;dark.value=night;vm!!.open("dorothy-cotton");vm!!.choose();vm!!.selectSku(sku.key)}
            compose.onNodeWithText("选择部件").performClick()
            compose.onNodeWithTag("square-registration").performScrollToNode(hasText("分别作为单品"));compose.onNodeWithText("分别作为单品").assertExists();screenshot("refined-split-$suffix")
            compose.onNodeWithText("为物品选图").performClick()
            compose.onNodeWithTag("square-registration").performScrollToNode(hasText("JSK 的图片"));compose.onNodeWithText("JSK 的图片").assertExists();screenshot("refined-jsk-photos-$suffix")
            compose.runOnIdle{vm!!.setPhotoUnit(1)};compose.onNodeWithTag("square-registration").performScrollToNode(hasText("KC 的图片"));compose.onNodeWithText("KC 的图片").assertExists();screenshot("refined-kc-photos-$suffix")
            compose.onNodeWithTag("square-registration").performScrollToNode(hasText("资料图"));compose.onNodeWithText("资料图",useUnmergedTree=true).performClick();screenshot("refined-kc-documents-$suffix")
            compose.onNodeWithText("查看登记预览").performClick();compose.onNodeWithText("核对第 1 / 2 件").assertIsDisplayed();screenshot("refined-preview-$suffix")
            assertEquals(1,vm!!.units()[0].photos.size);assertEquals(1,vm!!.units()[1].photos.size)
        }
    }

    @Test fun refinedPrivateItemDetailsUseRealPhotosAndFourteenSkins(){val c=refined()
        val p=c.products.first{it.id=="1088022205740"};val sku=p.skus.first{it.content=="JSK"&&it.color=="蓝色"&&it.size=="M"}
        val r=io{repo.prepare("dorothy-cotton",sku.key,null)};val draft=io{drafts.get(r.draftId!!)!!};val data=draft.data()
        io{drafts.commit(draft.id,Item(brandId=1,categoryId=1,name=data.name,description=data.description,status=data.status,
            size=data.size,colors=data.colors,imageUrls=draft.imagePaths,sizeChartImageUrl=draft.sizeChartImagePaths.firstOrNull(),
            sizeChartImageUrls=draft.sizeChartImagePaths.drop(1),importToken=data.importToken))}
        lateinit var detail:com.lolita.app.ui.screen.item.ItemEditViewModel
        inst.runOnMainSync {
            val items=ItemRepository(db.itemDao(),database=db,context=context)
            detail=com.lolita.app.ui.screen.item.ItemEditViewModel(items,BrandRepository(db.brandDao(),db.itemDao(),db),
                CategoryRepository(db.categoryDao(),db.itemDao(),db),CoordinateRepository(db.coordinateDao(),db.itemDao(),db),
                PriceRepository(db.priceDao(),db.paymentDao(),context,db),StyleRepository(db.styleDao(),db.itemDao(),db),
                SeasonRepository(db.seasonDao(),db.itemDao(),db),SourceRepository(db.sourceDao(),db.itemDao(),db),
                LocationRepository(db.locationDao(),db.itemDao()),drafts,StorageRepository(db))
            store.put("private-detail",detail);detailVm.value=detail
        }
        show();compose.waitUntil(15000){!detail.uiState.value.isLoading && detail.uiState.value.item!=null}
        for(type in SkinType.entries)for(night in listOf(false,true)){
            compose.runOnIdle{skin.value=type;dark.value=night}
            compose.onNodeWithText("物品详情").assertIsDisplayed()
            compose.onNodeWithTag("item-detail-images").assertExists()
            screenshot("refined-item-${type.name.lowercase()}-${if(night)"dark" else "light"}${if(target.resources.configuration.screenWidthDp<=320)"-small" else ""}")
        }
        compose.onNodeWithText("查看完整来源与购买快照").performScrollTo().performClick()
        compose.onNode(hasText("资料标识：",substring=true)).assertExists();screenshot("refined-item-source-expanded")
        assertEquals(0,io{db.priceDao().getAllPricesList().size})
    }

    @Test fun actualNavEntryReceiptAdvancesSeparateViewModelStateAndPauseKeepsNextUnit(){
        val c=refined();val sku=c.products.first{it.id=="1088022205740"}.skus.first{it.content=="JSK+KC"&&it.color=="蓝色"&&it.size=="L"}
        lateinit var model:SquareViewModel
        inst.runOnMainSync{model=SquareViewModel(repo,SavedStateHandle());store.put("nav-receipt",model)}
        compose.waitUntil(10000){model.busy.value==null}
        compose.runOnIdle{model.open("dorothy-cotton");model.choose();model.selectSku(sku.key);model.next();model.next();model.next();model.prepare()}
        compose.waitUntil(10000){model.registration.value!=null}
        val draft=model.registration.value!!.draftId!!;compose.runOnIdle{model.consumedRegistration()}
        lateinit var nav:androidx.navigation.NavHostController
        compose.setContent{
            val n=androidx.navigation.compose.rememberNavController();SideEffect{nav=n}
            androidx.navigation.compose.NavHost(n,startDestination="square"){
                composable("square"){entry->SquareEditorResult(entry,model);Text("广场回执测试")}
                composable("editor"){Text("编辑回执测试")}
            }
        }
        compose.runOnIdle{nav.navigate("editor")}
        compose.runOnIdle{nav.previousBackStackEntry!!.savedStateHandle["square_editor_result"]="$draft:saved";nav.popBackStack()}
        compose.waitUntil(5000){model.unitIndex.value==1};assertEquals("KC",model.units()[1].name)
        compose.runOnIdle{model.prepare()};compose.waitUntil(10000){model.registration.value!=null}
        val next=model.registration.value!!.draftId!!;compose.runOnIdle{model.consumedRegistration();nav.navigate("editor")}
        compose.runOnIdle{nav.previousBackStackEntry!!.savedStateHandle["square_editor_result"]="$draft:saved";nav.popBackStack()}
        compose.waitForIdle();assertEquals(1,model.unitIndex.value)
        compose.runOnIdle{nav.navigate("editor")}
        compose.runOnIdle{nav.previousBackStackEntry!!.savedStateHandle["square_editor_result"]="$next:paused";nav.popBackStack()}
        compose.waitUntil(5000){model.editorResult.value==null};assertEquals(1,model.unitIndex.value)
        assertNotNull(io{drafts.get(next)})
        compose.runOnIdle{nav.navigate("editor")}
        compose.runOnIdle{nav.previousBackStackEntry!!.savedStateHandle["square_editor_result"]="$draft:saved";nav.popBackStack()}
        compose.waitForIdle();assertEquals(1,model.unitIndex.value)
    }

}
