package com.lolita.app.data

import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
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
import java.io.File
import java.util.UUID

/** A captured editor snapshot must never resurrect an already deleted target or its draft. */
class ItemDraftDeletionRaceTest {
    private class Fixture {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(target.cacheDir,"draft-deletion-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(target) {
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val items=ItemRepository(db.itemDao(),database=db,context=context)
        val drafts=ItemDraftRepository(db,items)
        val stores=mutableListOf<ViewModelStore>()
        val item=Item(id=1,brandId=1,categoryId=1,name="原物品",description="保留备注",status=ItemStatus.OWNED,updatedAt=100)
        val draft=ItemDraft(targetItemId=1,dataJson=ItemDraftData(name="未完成编辑",brandId=1,categoryId=1,baselineUpdatedAt=100).encode())
        suspend fun init() {
            ImageFileHelper.configure(context,db)
            db.brandDao().insertBrand(Brand(id=1,name="保留品牌"))
            db.categoryDao().insertCategory(Category(id=1,name="测试假发",categorySemantic=CategorySemantic.FULL_WIG))
            db.itemDao().insertItem(item)
        }
        suspend fun editor():ItemEditViewModel {
            val vm=withContext(Dispatchers.Main) {
                ItemEditViewModel(items,BrandRepository(db.brandDao(),db.itemDao(),db),CategoryRepository(db.categoryDao(),db.itemDao(),db),
                    CoordinateRepository(db.coordinateDao(),db.itemDao(),db),PriceRepository(db.priceDao(),db.paymentDao(),context,db),
                    StyleRepository(db.styleDao(),db.itemDao(),db),SeasonRepository(db.seasonDao(),db.itemDao(),db),
                    SourceRepository(db.sourceDao(),db.itemDao(),db),LocationRepository(db.locationDao(),db.itemDao()),drafts,StorageRepository(db))
                    .also {stores+=ViewModelStore().apply {put("editor",it)};it.loadItem(1)}
            }
            withTimeout(5000) {while(vm.uiState.value.isLoading || !vm.uiState.value.purchaseReviewsLoaded || vm.uiState.value.allItems.none {it.id==1L}) delay(10)}
            withContext(Dispatchers.Main) {
                vm.updateName("尚未保存的编辑")
                assertTrue("Fixture initial keepDraft must succeed while the target exists",runCatching {vm.keepDraft()}.isSuccess)
            }
            assertNotNull(db.itemDao().getItemById(1))
            assertNotNull(drafts.getForItem(1))
            return vm
        }
        suspend fun assertBackupWorks() {
            val backup=BackupManager(context,db).exportToJson()
            assertTrue("Deleting then receiving a late editor snapshot must leave a valid export graph: ${backup.exceptionOrNull()?.message}",backup.isSuccess)
            backup.getOrNull()?.let {context.contentResolver.delete(it,null,null)}
        }
        suspend fun close() {
            withContext(Dispatchers.Main) {stores.forEach {it.clear()}}
            db.close();root.deleteRecursively();ImageFileHelper.configure(target,AppModule.database())
        }
    }
    private suspend fun fixture(block:suspend (Fixture)->Unit) {val f=Fixture();try {f.init();block(f)} finally {f.close()}}

    @Test fun lateSaveCannotRecreateADeletedTargetsDraftAndBackupStillWorks()=runBlocking {fixture {f->
        f.drafts.save(f.draft)
        f.items.deleteItem(f.item)
        assertNull(f.drafts.get(f.draft.id))
        assertTrue("A captured late save must reject the missing target inside its transaction",runCatching {f.drafts.save(f.draft)}.isFailure)
        assertTrue(f.db.itemDraftDao().getAll().isEmpty())
        assertNull(f.db.itemDao().getItemById(1))
        // New-item drafts intentionally have no target and must remain available.
        val fresh=ItemDraft(dataJson=ItemDraftData(name="新物品").encode())
        f.drafts.save(fresh);assertNotNull(f.drafts.get(fresh.id))
        f.assertBackupWorks()
    }}
    @Test fun externallyDeletedItemClosesTheEditorAndKeepDraftCannotRestoreItsOldIdentity()=runBlocking {fixture {f->
        val vm=f.editor()
        f.items.deleteItem(f.item)
        withTimeout(5000) {while(vm.uiState.value.allItems.any {it.id==1L}) delay(10)}
        assertNull(f.db.itemDao().getItemById(1))
        val kept=withContext(Dispatchers.Main) {runCatching {vm.keepDraft()}}
        assertTrue("Observed deletion must close the autosave identity",f.db.itemDraftDao().getAll().isEmpty())
        assertTrue("A closed editor must not attempt to reconnect deleted recognition targets",kept.isSuccess)
        assertNull(vm.uiState.value.draftId)
        assertNull(vm.uiState.value.item)
        assertTrue(vm.uiState.value.loadError.orEmpty().contains("已删除"))
        assertFalse(vm.hasUnsavedChanges)
        assertTrue(withContext(Dispatchers.Main) {vm.saveItem()}.isFailure)
        f.assertBackupWorks()
    }}
    @Test fun deletingFromTheEditorSerializesAutosaveAndClosesKeepDraftBeforeReturning()=runBlocking {fixture {f->
        val vm=f.editor()
        val kept=withContext(Dispatchers.Main) {
            assertTrue(vm.deleteItem().isSuccess)
            runCatching {vm.keepDraft()}
        }
        assertNull(f.db.itemDao().getItemById(1))
        assertTrue("Editor deletion must not leave or revive a draft",f.db.itemDraftDao().getAll().isEmpty())
        assertTrue("A closed editor must not reconnect deleted targets",kept.isSuccess)
        assertNull(vm.uiState.value.draftId)
        assertNull(vm.uiState.value.item)
        assertFalse(vm.hasUnsavedChanges)
        assertTrue(withContext(Dispatchers.Main) {vm.saveItem()}.isFailure)
        f.assertBackupWorks()
    }}
}
