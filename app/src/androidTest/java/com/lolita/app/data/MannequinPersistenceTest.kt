package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MannequinPersistenceTest {
    private class Fixture : AutoCloseable {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(target.cacheDir,"mannequin-data-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(target) {
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
        }
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val repository=ItemRepository(db.itemDao(),database=db,context=context)
        suspend fun setup():Item {
            ImageFileHelper.configure(context,db)
            val brand=db.brandDao().insertBrand(Brand(name="原品牌"))
            val category=db.categoryDao().insertCategory(Category(name="OP"))
            val item=Item(name="原裙子",brandId=brand,categoryId=category,status=ItemStatus.WISHED,
                description="私人备注",imageUrls=listOf(image()),createdAt=123,updatedAt=456)
            return item.copy(id=db.itemDao().insertItem(item))
        }
        suspend fun image():String {
            val bytes=ByteArrayOutputStream().use { out ->
                val bitmap=Bitmap.createBitmap(32,48,Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray()
            }
            return ImageFileHelper.saveGeneratedImage(context,bytes)
        }
        override fun close() {
            try {
                ImageFileHelper.configure(target.applicationContext,AppModule.database())
            } finally {
                db.close()
                root.deleteRecursively()
            }
        }
    }

    @Test fun queuedAppendReadsLatestItemAndOnlyAddsImageAfterExistingOrder()= runBlocking {
        Fixture().use { f ->
            val original=f.setup()
            val concurrentPhoto=f.image()
            val preview=f.image()
            val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            val latest=original.copy(name="并发改名",description="并发备注",imageUrls=original.imageUrls+concurrentPhoto,status=ItemStatus.OWNED)
            val edit=async(Dispatchers.IO) {
                f.db.withTransaction {
                    f.db.itemDao().updateItem(latest)
                    entered.complete(Unit); release.await()
                }
            }
            entered.await()
            val append=async(Dispatchers.IO) { f.repository.appendGeneratedImage(original.id,preview) }
            release.complete(Unit); edit.await(); append.await()
            val saved=f.db.itemDao().getItemById(original.id)!!
            assertEquals(latest.copy(imageUrls=latest.imageUrls+preview,updatedAt=saved.updatedAt),saved)
            assertTrue(saved.updatedAt>456)
            f.repository.appendGeneratedImage(original.id,preview)
            assertEquals(saved,f.db.itemDao().getItemById(original.id))
        }
    }

    @Test fun concurrentAppendsKeepBothAndCleanupCannotDeleteCommittedImage()=runBlocking {
        Fixture().use { f ->
            val original=f.setup(); val first=f.image(); val second=f.image()
            coroutineScope {
                listOf(first,second,first).map { path -> async(Dispatchers.IO) { f.repository.appendGeneratedImage(original.id,path) } }.awaitAll()
            }
            val saved=f.db.itemDao().getItemById(original.id)!!
            assertEquals(original.imageUrls.first(),saved.imageUrls.first())
            assertEquals(3,saved.imageUrls.size)
            assertEquals(setOf(first,second),saved.imageUrls.drop(1).toSet())
            ImageFileHelper.deleteImage(first)
            assertTrue(File(first).isFile)
            assertEquals(0,f.db.tryOnDao().getAll().size)
        }
    }

    @Test fun deletedItemAndNonOwnedOrMissingFilesAreRejectedWithoutMutation()=runBlocking {
        Fixture().use { f ->
            val original=f.setup(); val preview=f.image()
            val external=File(f.root,"external.png").apply { writeBytes(File(preview).readBytes()) }
            val empty=File(File(preview).parentFile,"empty.png").apply { writeBytes(byteArrayOf()) }
            val missing=File(File(preview).parentFile,"missing.png")
            for (path in listOf(external.absolutePath,empty.absolutePath,missing.absolutePath)) {
                reject { f.repository.appendGeneratedImage(original.id,path) }
                assertEquals(original,f.db.itemDao().getItemById(original.id))
            }
            f.db.itemDao().deleteItem(original)
            reject { f.repository.appendGeneratedImage(original.id,preview) }
            assertNull(f.db.itemDao().getItemById(original.id)); assertTrue(File(preview).isFile)
        }
    }

    @Test fun confirmedPreviewSurvivesExistingZipBackupAndRestoreInImageOrder()=runBlocking {
        Fixture().use { f ->
            val original=f.setup(); val preview=f.image()
            f.repository.appendGeneratedImage(original.id,preview)
            val before=f.db.itemDao().getItemById(original.id)!!
            val bytes=before.imageUrls.map { File(it).readBytes() }
            val manager=BackupManager(f.context,f.db)
            val zip=manager.exportToZip().getOrThrow()
            try {
                manager.importBackup(zip).getOrThrow()
                val restored=f.db.itemDao().getItemById(original.id)!!
                assertEquals(before.copy(imageUrls=restored.imageUrls),restored)
                assertEquals(2,restored.imageUrls.size)
                bytes.forEachIndexed { index, expected -> assertArrayEquals(expected,File(restored.imageUrls[index]).readBytes()) }
                assertEquals(0,f.db.tryOnDao().getAll().size)
            } finally { f.context.contentResolver.delete(zip,null,null) }
        }
    }

    @Test fun staleSnapshotDeletionCleansLatestGeneratedAndSizeChartFilesButProtectsSharedPhoto()=runBlocking {
        Fixture().use { f ->
            val stale=f.setup()
            val preview=f.image(); val shared=f.image(); val chart=f.image(); val extraChart=f.image()
            val latest=stale.copy(imageUrls=stale.imageUrls+shared,sizeChartImageUrl=chart,sizeChartImageUrls=listOf(extraChart))
            f.db.itemDao().updateItem(latest)
            val sibling=stale.copy(id=0,name="共享原图的另一条记录",imageUrls=listOf(shared))
            val siblingId=f.db.itemDao().insertItem(sibling)
            f.repository.appendGeneratedImage(stale.id,preview)
            f.repository.deleteItem(stale)
            assertNull(f.db.itemDao().getItemById(stale.id))
            assertFalse("stale snapshot's original image must be cleaned",File(stale.imageUrls.single()).exists())
            assertFalse("generated image appended after snapshot must be cleaned",File(preview).exists())
            assertFalse("latest primary size chart must be cleaned",File(chart).exists())
            assertFalse("latest additional size chart must be cleaned",File(extraChart).exists())
            assertTrue("a second Item still references this image",File(shared).isFile)
            assertEquals(listOf(shared),f.db.itemDao().getItemById(siblingId)!!.imageUrls)
        }
    }

    @Test fun appendRequiresRealTransactionInsteadOfOptionalRepositoryFallback()=runBlocking {
        Fixture().use { f ->
            val original=f.setup(); val preview=f.image()
            reject { ItemRepository(f.db.itemDao(),context=f.context).appendGeneratedImage(original.id,preview) }
            assertEquals(original,f.db.itemDao().getItemById(original.id))
        }
    }

    private suspend fun reject(block:suspend()->Unit) {
        try { block(); fail("invalid append accepted") }
        catch(e:CancellationException) { throw e }
        catch(_:IllegalArgumentException) { }
        catch(_:IllegalStateException) { }
    }
}
