package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.data.repository.TryOnRepository
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TryOnPersistenceTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),LolitaDatabase::class.java)

    @Test fun migrationOnlyAddsHistoryAndPreservesEveryExistingColumn() {
        val name = "tryon-migration-${UUID.randomUUID()}"
        try {
            migration.createDatabase(name,26).use { old ->
                old.execSQL("INSERT INTO brands(id,name,created_at,is_preset) VALUES(7,'原品牌',123,0)")
            }
            migration.runMigrationsAndValidate(name,27,true,LolitaDatabase.MIGRATION_26_27).use { db ->
                db.query("SELECT name,created_at,is_preset FROM brands WHERE id=7").use {
                    assertTrue(it.moveToFirst()); assertEquals("原品牌",it.getString(0)); assertEquals(123,it.getInt(1)); assertEquals(0,it.getInt(2))
                }
                db.query("SELECT COUNT(*) FROM try_on_results").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { target.deleteDatabase(name) }
    }

    @Test fun zipRoundTripIncludesResultAndDeletionProtectsSharedImage() = runBlocking {
        val root = File(target.cacheDir,"tryon-db-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val exported = mutableListOf<android.net.Uri>()
        try {
            ImageFileHelper.configure(context,db)
            val repo = TryOnRepository(context,db)
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(32,48,Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG,100,output); bitmap.recycle(); output.toByteArray()
            }
            val selected = listOf(TryOnSelection(33,"原裙子","/old/photo.jpg",TryOnRole.MAIN))
            val id = repo.save("搭配",bytes,selected,"gpt-image-2")
            val original = repo.results.first().single()
            assertEquals(listOf("33"),original.itemIds)
            val manager = BackupManager(context,db)
            val zip = manager.exportToZip().getOrThrow().also { exported.add(it) }
            assertEquals(1,manager.previewBackup(zip).getOrThrow().tryOnResultCount)
            manager.importBackup(zip).getOrThrow()
            val restored = db.tryOnDao().getAll().single()
            assertEquals(original.copy(imageUrl = restored.imageUrl),restored)
            assertArrayEquals(bytes,File(restored.imageUrl).readBytes())
            db.tryOnDao().insert(restored.copy(id=0,name="共享图片"))
            repo.delete(restored.id)
            assertTrue(File(restored.imageUrl).isFile)
            repo.delete(db.tryOnDao().getAll().single().id)
            assertFalse(File(restored.imageUrl).exists())
        } finally {
            exported.forEach { context.contentResolver.delete(it,null,null) }
            db.close(); root.deleteRecursively()
        }
    }
}
