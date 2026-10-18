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
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.WardrobeCategoryLibrary
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.TryOnRole
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
    @Test fun concurrentAndRepeatedInitializationRecognizesHistoricalNamesWithoutReplacingTheirIdentity() = runBlocking { fixture { db,prefs ->
        val old=Category(id=700,name="全头假发",isPreset=true)
        db.categoryDao().insertCategory(old)
        coroutineScope {(1..6).map {async(Dispatchers.IO) {WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()}}.awaitAll()}
        val first=db.categoryDao().getAllCategoriesList()
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        assertEquals(first,db.categoryDao().getAllCategoriesList())
        assertEquals(old.copy(categorySemantic=CategorySemantic.FULL_WIG),db.categoryDao().getCategoryById(700))
        assertEquals(first.size,first.map {it.name}.distinct().size)
    } }
    @Test fun allTwelveOriginalCategoriesAreRecognizedWithTheirOriginalIdsNamesGroupsAndTimes() = runBlocking { fixture { db,prefs ->
        val mappings=listOf("JSK" to CategorySemantic.JSK,"OP" to CategorySemantic.OP,"SK" to CategorySemantic.SK,
            "KC" to CategorySemantic.KC,"斗篷" to CategorySemantic.CAPE,"披肩" to CategorySemantic.SHAWL,
            "发带" to CategorySemantic.HEADBAND,"Bonnet" to CategorySemantic.BONNET,"其他头饰" to CategorySemantic.OTHER_ACCESSORY,
            "袜子" to CategorySemantic.OTHER_HOSIERY,"手套" to CategorySemantic.GLOVES,"其他配饰" to CategorySemantic.OTHER_ACCESSORY)
        val originals=mappings.mapIndexed {index,(name,_) -> Category(id=500L+index,name=name,isPreset=true,
            group=if(index<3) CategoryGroup.CLOTHING else CategoryGroup.ACCESSORY,createdAt=1234L+index)}
        originals.forEach {db.categoryDao().insertCategory(it)}
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        originals.forEachIndexed {index,old ->
            assertEquals(old.copy(categorySemantic=mappings[index].second,
                defaultWearRole=if(old.name=="其他头饰") TryOnRole.HEAD else null),db.categoryDao().getCategoryById(old.id))
        }
    } }
    @Test fun existing255InitializationStillRunsRecognitionOnceAndDoesNotReseedMissingDefaults() = runBlocking { fixture { db,prefs ->
        context.getSharedPreferences(prefs,Context.MODE_PRIVATE).edit().putBoolean("initialized_v1",true).commit()
        val old=Category(id=300,name=" ＪＳＫ ",createdAt=42)
        val confirmed=Category(id=301,name="OP",categorySemantic=CategorySemantic.KC,defaultWearRole=TryOnRole.WAIST)
        val override=Category(id=302,name="其他头饰",defaultWearRole=TryOnRole.HAND)
        val ambiguous=Category(id=303,name="JSK＋KC")
        listOf(old,confirmed,override,ambiguous).forEach {db.categoryDao().insertCategory(it)}
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        assertEquals(old.copy(categorySemantic=CategorySemantic.JSK),db.categoryDao().getCategoryById(300))
        assertEquals(confirmed,db.categoryDao().getCategoryById(301))
        assertEquals(override.copy(categorySemantic=CategorySemantic.OTHER_ACCESSORY),db.categoryDao().getCategoryById(302))
        assertEquals(ambiguous,db.categoryDao().getCategoryById(303))
        assertEquals(4,db.categoryDao().getAllCategoriesList().size)
        db.categoryDao().updateCategory(ambiguous.copy(name="SK"))
        db.categoryDao().updateCategory(old.copy(name="自定义改名",categorySemantic=CategorySemantic.JSK))
        WardrobeCategoryLibrary(context,db,prefs).awaitInitialization()
        assertEquals(CategorySemantic.UNCONFIRMED,db.categoryDao().getCategoryById(303)!!.categorySemantic)
        assertEquals(CategorySemantic.JSK,db.categoryDao().getCategoryById(300)!!.categorySemantic)
        assertEquals(4,db.categoryDao().getAllCategoriesList().size)
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
