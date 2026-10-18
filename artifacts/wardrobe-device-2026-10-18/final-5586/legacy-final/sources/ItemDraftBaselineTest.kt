package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.ui.screen.item.ItemEditViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** A durable edit may not silently turn a newer persisted row back into its old snapshot. */
class ItemDraftBaselineTest {
    private class Fixture {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(target.cacheDir,"draft-baseline-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(target) {override fun getFilesDir()=File(root,"files").apply {mkdirs()}}
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val items=ItemRepository(db.itemDao(),database=db,context=context)
        val drafts=ItemDraftRepository(db,items)
        val stores=mutableListOf<ViewModelStore>()
        lateinit var item:Item
        suspend fun init() {
            ImageFileHelper.configure(context,db)
            db.brandDao().insertBrand(Brand(id=1,name="保留品牌"))
            db.categoryDao().insertCategory(Category(id=1,name="测试假发",categorySemantic=CategorySemantic.FULL_WIG))
            db.locationDao().insertLocation(Location(id=1,name="原房间"))
            db.locationDao().insertLocation(Location(id=2,name="新房间"))
            item=Item(id=1,name="原记录",description="原备注",brandId=1,categoryId=1,status=ItemStatus.OWNED,locationId=1,updatedAt=100)
            db.itemDao().insertItem(item)
        }
        suspend fun draft()=ItemDraft(targetItemId=1,dataJson=ItemDraftData(name="未完成的编辑",description="旧草稿备注",brandId=1,
            categoryId=1,locationId=1,baselineUpdatedAt=100).encode()).also {drafts.save(it)}
        suspend fun image():String {
            val bitmap=Bitmap.createBitmap(12,16,Bitmap.Config.ARGB_8888)
            val bytes=ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
            bitmap.recycle();return ImageFileHelper.saveGeneratedImage(context,bytes)
        }
        suspend fun editor():ItemEditViewModel {
            val vm=withContext(Dispatchers.Main) {
                ItemEditViewModel(items,BrandRepository(db.brandDao(),db.itemDao(),db),CategoryRepository(db.categoryDao(),db.itemDao(),db),
                    CoordinateRepository(db.coordinateDao(),db.itemDao(),db),PriceRepository(db.priceDao(),db.paymentDao(),context,db),
                    StyleRepository(db.styleDao(),db.itemDao(),db),SeasonRepository(db.seasonDao(),db.itemDao(),db),
                    SourceRepository(db.sourceDao(),db.itemDao(),db),LocationRepository(db.locationDao(),db.itemDao()),drafts,StorageRepository(db))
                    .also {stores+=ViewModelStore().apply {put("editor",it)};it.loadItem(1)}
            }
            withTimeout(5000) {while(vm.uiState.value.isLoading) delay(10)}
            return vm
        }
        suspend fun close() {withContext(Dispatchers.Main) {stores.forEach {it.clear()}};db.close();root.deleteRecursively();ImageFileHelper.configure(target,AppModule.database())}
    }
    private suspend fun fixture(action:suspend (Fixture)->Unit) {val f=Fixture();try {f.init();action(f)} finally {f.close()}}

    @Test fun reopeningAnOldDraftBlocksOverwriteAndRetainsBothTheDraftAndLatestPhotosAndLocation()=runBlocking {fixture {f->
        val draft=f.draft();val photo=f.image()
        f.db.itemDao().updateItem(f.item.copy(imageUrls=listOf(photo),locationId=2,updatedAt=200))
        val vm=f.editor()
        assertNotNull("A stale durable draft must request explicit discard/reload",vm.uiState.value.loadError)
        assertTrue(withContext(Dispatchers.Main) {vm.saveItem()}.isFailure)
        assertEquals(100L,f.drafts.get(draft.id)!!.data().baselineUpdatedAt)
        assertEquals("未完成的编辑",f.drafts.get(draft.id)!!.data().name)
        assertEquals(listOf(photo),f.db.itemDao().getItemById(1)!!.imageUrls)
        assertEquals(2L,f.db.itemDao().getItemById(1)!!.locationId)
        withContext(Dispatchers.Main) {vm.discardConflictingDraftAndReload()}
        withTimeout(5000) {while(vm.uiState.value.isLoading) delay(10)}
        assertNull(vm.uiState.value.loadError)
        assertNull(f.drafts.get(draft.id))
        assertEquals(listOf(photo),vm.uiState.value.imageUrls)
        assertEquals(2L,vm.uiState.value.locationId)
        withContext(Dispatchers.Main) {vm.updateName("重新编辑")}
        assertTrue(withContext(Dispatchers.Main) {vm.saveItem()}.isSuccess)
        assertEquals(listOf(photo),f.db.itemDao().getItemById(1)!!.imageUrls)
    }}
    @Test fun savingAnAlreadyOpenEditorRejectsAConcurrentChangeWithoutReplacingItsBaseline()=runBlocking {fixture {f->
        val vm=f.editor()
        withContext(Dispatchers.Main) {vm.updateName("我的编辑");vm.keepDraft()}
        val draftId=vm.uiState.value.draftId!!;val photo=f.image()
        f.db.itemDao().updateItem(f.item.copy(imageUrls=listOf(photo),locationId=2,updatedAt=200))
        val saved=withContext(Dispatchers.Main) {vm.saveItem()}
        assertTrue("Concurrent detail append or batch move must not be lost",saved.isFailure)
        assertEquals(100L,f.drafts.get(draftId)!!.data().baselineUpdatedAt)
        assertEquals(listOf(photo),f.db.itemDao().getItemById(1)!!.imageUrls)
        assertEquals(2L,f.db.itemDao().getItemById(1)!!.locationId)
    }}
    @Test fun repositoryCommitRejectsAStaleDraftEvenWithoutTheEditor()=runBlocking {fixture {f->
        val draft=f.draft();f.db.itemDao().updateItem(f.item.copy(name="外部修改",updatedAt=200))
        assertTrue("Repository must enforce the original draft baseline atomically",runCatching {f.drafts.commit(draft.id,f.item.copy(name="覆盖尝试"))}.isFailure)
        assertEquals("外部修改",f.db.itemDao().getItemById(1)!!.name)
        assertNotNull(f.drafts.get(draft.id))
    }}
    @Test fun aFreshEditorCanStillSaveWithoutInventingAConflict()=runBlocking {fixture {f->
        val vm=f.editor()
        withContext(Dispatchers.Main) {vm.updateName("正常编辑")}
        assertTrue(withContext(Dispatchers.Main) {vm.saveItem()}.isSuccess)
        assertEquals("正常编辑",f.db.itemDao().getItemById(1)!!.name)
    }}
    @Test fun aLiveObservedItemAndOwnCoverActionCannotLaunderTheOldEditableBaseline()=runBlocking {fixture {f->
        val vm=f.editor()
        withTimeout(5000) {while(!vm.uiState.value.purchaseReviewsLoaded) delay(10)}
        val first=f.image();val second=f.image()
        f.db.itemDao().updateItem(f.item.copy(imageUrls=listOf(first,second),locationId=2,updatedAt=200))
        withTimeout(5000) {while(vm.uiState.value.item?.updatedAt!=200L) delay(10)}
        // The observer refreshes identity/ledger data, while the form still owns its original fields.
        assertEquals(1L,vm.uiState.value.locationId)
        withContext(Dispatchers.Main) {vm.setImageCover(second)}
        withTimeout(5000) {while(vm.uiState.value.isSettingCover) delay(10)}
        assertEquals(listOf(second,first),vm.uiState.value.imageUrls)
        withContext(Dispatchers.Main) {vm.updateName("封面后的旧编辑")}
        assertTrue("An own image action cannot authorize old location or other form values",withContext(Dispatchers.Main) {vm.saveItem()}.isFailure)
        assertEquals(2L,f.db.itemDao().getItemById(1)!!.locationId)
        assertEquals(listOf(second,first),f.db.itemDao().getItemById(1)!!.imageUrls)
    }}
    @Test fun legacyPreviewAppendRecordsAppearanceOnlyProvenanceInTheSameTransaction()=runBlocking {fixture {f->
        val photo=f.image();f.items.appendGeneratedImage(1,photo)
        val provenance=f.db.imageProvenanceDao().get(1,photo)
        assertNotNull("Known generated legacy previews need durable provenance",provenance)
        assertEquals(ImageSourceType.AI_GENERATED,provenance!!.sourceType)
        assertEquals("legacy_display",provenance.mode)
        assertTrue(RecognitionRepository(f.db,f.context).isAppearanceOnly(photo))
        f.items.appendGeneratedImage(1,photo)
        assertEquals(1,f.db.itemDao().getItemById(1)!!.imageUrls.size)
    }}
}
