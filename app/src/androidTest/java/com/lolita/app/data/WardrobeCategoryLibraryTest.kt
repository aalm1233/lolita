package com.lolita.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.lolita.app.data.file.BackupData
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.WardrobeCategoryLibrary
import com.lolita.app.domain.model.CategorySemantic
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WardrobeCategoryLibraryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(block:suspend(LolitaDatabase,String)->Unit) {
        val preferencesName="wardrobe-category-test-${UUID.randomUUID()}"
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        try {block(db,preferencesName)} finally {db.close();context.deleteSharedPreferences(preferencesName)}
    }
    @Test fun concurrentAndRepeatedInitializationPreserveHistoricalIdentityAndNeverReclassifyIt() = runBlocking { fixture { db,prefs ->
        val old=Category(id=700,name="全头假发",isPreset=true)
        db.categoryDao().insertCategory(old)
        coroutineScope {(1..6).map {async(Dispatchers.IO) {WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()}}.awaitAll()}
        val first=db.categoryDao().getAllCategoriesList()
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        assertEquals(first,db.categoryDao().getAllCategoriesList())
        assertEquals(old,db.categoryDao().getCategoryById(700))
        assertEquals(CategorySemantic.UNCONFIRMED,db.categoryDao().getCategoryById(700)!!.categorySemantic)
        assertEquals(first.size,first.map {it.name}.distinct().size)
    } }
    @Test fun deletingOrRenamingInitializedDefaultsDoesNotBringThemBackEvenWithANewLibraryInstance() = runBlocking { fixture { db,prefs ->
        val library=WardrobeCategoryLibrary(context,db,prefs)
        library.awaitInitialization()
        val repo=CategoryRepository(db.categoryDao(),db.itemDao(),db,library::awaitInitialization)
        val wig=db.categoryDao().getCategoryByName("全头假发")!!
        repo.deleteCategory(wig)
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        assertNull(db.categoryDao().getCategoryByName("全头假发"))
        val shoe=db.categoryDao().getCategoryByName("玛丽珍鞋")!!
        repo.updateCategory(shoe.copy(name="我的鞋型"))
        repo.ensurePresetCategories()
        assertNull(db.categoryDao().getCategoryByName("玛丽珍鞋"))
        assertEquals(shoe.copy(name="我的鞋型"),db.categoryDao().getCategoryById(shoe.id))
    } }
    @Test fun actualBackupRestoreWaitsForTheDurableMarkerAndNeverAppendsDefaultsAfterReplacement() = runBlocking { fixture { db,prefs ->
        val old=Category(id=987,name="恢复的旧分类")
        val data=BackupData(brands=emptyList(),categories=listOf(old),coordinates=emptyList(),items=emptyList(),prices=emptyList(),payments=emptyList(),outfitLogs=emptyList(),outfitItemCrossRefs=emptyList())
        val file=File(context.cacheDir,"category-restore-${UUID.randomUUID()}.json")
        try {
            file.writeText(GsonBuilder().serializeNulls().create().toJson(data))
            var barriers=0
            BackupManager(context,db,awaitInitialization={barriers++;WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()})
                .importBackup(Uri.fromFile(file)).getOrThrow()
            assertTrue(barriers>0)
            assertEquals(listOf(old),db.categoryDao().getAllCategoriesList())
            WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
            assertEquals("Restore must keep exactly the restored category set",listOf(old),db.categoryDao().getAllCategoriesList())
            assertTrue(context.getSharedPreferences(prefs,Context.MODE_PRIVATE).getBoolean("initialized_v1",false))
        } finally {file.delete()}
    } }
}
