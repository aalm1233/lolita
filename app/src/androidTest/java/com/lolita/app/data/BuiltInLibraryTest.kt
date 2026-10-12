package com.lolita.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.BuiltInLibrary
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.BrandRepository
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Every database, settings namespace and private image directory is isolated from user data. */
@RunWith(AndroidJUnit4::class)
class BuiltInLibraryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: FixtureContext
    private lateinit var db: LolitaDatabase
    private val published = mutableListOf<Uri>()

    private inner class FixtureContext : ContextWrapper(instrumentation.targetContext) {
        val root = File(baseContext.cacheDir, "builtin-${UUID.randomUUID()}").apply { mkdirs() }
        private val namespace = "builtin-test-${UUID.randomUUID()}"
        val preferences = java.util.Collections.synchronizedList(mutableListOf<SharedPreferences>())
        @Volatile var onFilesAccess: (() -> Unit)? = null
        override fun getFilesDir(): File {
            onFilesAccess?.invoke()
            return File(root, "files").apply { mkdirs() }
        }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getAssets(): AssetManager = instrumentation.context.assets
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$namespace-$name", mode).also { preferences.add(it) }
        fun cleanup() { preferences.forEach { it.edit().clear().commit() }; root.deleteRecursively() }
    }

    @Before fun setup() {
        context = FixtureContext()
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
    }
    @After fun cleanup() {
        published.forEach { context.contentResolver.delete(it, null, null) }
        db.close()
        context.cleanup()
    }
    private fun imageFiles() = File(context.root, "files/images").listFiles()?.toList().orEmpty()
    private fun fixtureBytes() = context.assets.open("builtin/fixture.png").use { it.readBytes() }
    private fun assertLocalImage(path: String?) {
        val file = File(requireNotNull(path))
        assertEquals(File(context.filesDir, "images").canonicalFile, file.parentFile!!.canonicalFile)
        UUID.fromString(file.nameWithoutExtension)
        assertArrayEquals(fixtureBytes(), file.readBytes())
    }
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected operation failure") } catch (_: Exception) { }
    }

    @Test fun initializesSixRoomsAndOnlyMissingPreciselyMatchedBrandImages() = runBlocking {
        val bedroom = Location(id = 81, name = "卧室", description = "用户描述", sortOrder = 30,
            createdAt = 123, updatedAt = 456, imageUrl = " ")
        val cabinet = Location(id = 82, name = "主卧衣柜", description = "不归并", sortOrder = 40,
            createdAt = 789, updatedAt = 790, imageUrl = "/用户照片")
        db.locationDao().insertLocation(bedroom); db.locationDao().insertLocation(cabinet)
        val blank = Brand(id = 41, name = "Verified Shop", logoUrl = "", createdAt = 12, isPreset = true)
        val whitespace = Brand(id = 42, name = "  VERIFIED SHOP ", logoUrl = " \t", createdAt = 13)
        val alias = Brand(id = 43, name = "核验店铺", createdAt = 14)
        val photo = Brand(id = 44, name = "verified shop", logoUrl = "/原有照片", createdAt = 15)
        val similar = Brand(id = 45, name = "Verified Shop 商品", createdAt = 16)
        listOf(blank, whitespace, alias, photo, similar).forEach { db.brandDao().insertBrand(it) }
        BuiltInLibrary(context, db).initialize()
        assertEquals(7, db.locationDao().getAllLocationsList().size)
        val actualBedroom = db.locationDao().getLocationByName("卧室")!!
        assertEquals(bedroom.copy(imageUrl = actualBedroom.imageUrl), actualBedroom)
        assertLocalImage(actualBedroom.imageUrl)
        assertEquals(cabinet, db.locationDao().getLocationByName("主卧衣柜"))
        assertEquals(listOf(41, 42, 43, 44, 45).map(Int::toLong).toSet(), db.brandDao().getAllBrandsList().map { it.id }.toSet())
        listOf(blank, whitespace, alias).forEach { original ->
            val actual = db.brandDao().getBrandById(original.id)!!
            assertEquals(original.copy(logoUrl = actual.logoUrl), actual)
            assertLocalImage(actual.logoUrl)
        }
        assertEquals(photo, db.brandDao().getBrandById(photo.id))
        assertEquals(similar, db.brandDao().getBrandById(similar.id))
        assertEquals(listOf(41, 42, 43, 44, 45), db.locationDao().getAllLocationsList()
            .filter { it.name !in listOf("卧室", "主卧衣柜") }.map { it.sortOrder })
    }

    @Test fun existingDefaultRoomAndBrandPhotographsRemainByteExact() = runBlocking {
        val photo = File(context.filesDir, "user-photo.png").apply { writeBytes(byteArrayOf(7, 11, 13)) }
        val room = Location(id = 77, name = "客厅", description = "用户原描述", imageUrl = photo.absolutePath,
            sortOrder = 19, createdAt = 101, updatedAt = 102)
        val brand = Brand(id = 78, name = "核验店铺", logoUrl = photo.absolutePath, createdAt = 103, isPreset = true)
        db.locationDao().insertLocation(room)
        db.brandDao().insertBrand(brand)
        BuiltInLibrary(context, db).initialize()
        assertEquals(room, db.locationDao().getLocationByName("客厅"))
        assertEquals(brand, db.brandDao().getBrandById(78))
        assertArrayEquals(byteArrayOf(7, 11, 13), photo.readBytes())
        assertEquals(5, imageFiles().size)
    }

    @Test fun repeatedConcurrentInitializationAndUserDeletionRenameOrRemovalNeverReapplyDefaults() = runBlocking {
        db.brandDao().insertBrand(Brand(name = "核验店铺"))
        coroutineScope { repeat(8) { launch(Dispatchers.IO) { BuiltInLibrary(context, db).initialize() } } }
        val originalFiles = imageFiles().map { it.name }.toSet()
        db.locationDao().deleteLocation(db.locationDao().getLocationByName("卧室")!!)
        db.locationDao().updateLocation(db.locationDao().getLocationByName("客厅")!!.copy(name = "自己的客厅"))
        db.locationDao().updateLocation(db.locationDao().getLocationByName("书房")!!.copy(imageUrl = null))
        val brand = db.brandDao().getBrandByName("核验店铺")!!
        db.brandDao().updateBrand(brand.copy(logoUrl = ""))
        repeat(3) { BuiltInLibrary(context, db).initialize() }
        assertEquals(5, db.locationDao().getAllLocationsList().size)
        assertNull(db.locationDao().getLocationByName("卧室"))
        assertNull(db.locationDao().getLocationByName("客厅"))
        assertNotNull(db.locationDao().getLocationByName("自己的客厅"))
        assertNull(db.locationDao().getLocationByName("书房")!!.imageUrl)
        assertEquals("", db.brandDao().getBrandById(brand.id)!!.logoUrl)
        assertEquals(originalFiles, imageFiles().map { it.name }.toSet())
    }

    @Test fun failedCopyAndTransactionCleanStagedFilesAndAllowRetry() = runBlocking {
        File(context.filesDir, "images").writeText("blocks directory creation")
        fails { BuiltInLibrary(context, db).initialize() }
        assertTrue(db.locationDao().getAllLocationsList().isEmpty())
        File(context.filesDir, "images").delete()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_builtin BEFORE INSERT ON locations WHEN NEW.name='客厅' BEGIN SELECT RAISE(ABORT, 'fixture rollback'); END")
        fails { BuiltInLibrary(context, db).initialize() }
        assertTrue(db.locationDao().getAllLocationsList().isEmpty())
        assertTrue(imageFiles().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_builtin")
        BuiltInLibrary(context, db).initialize()
        assertEquals(6, db.locationDao().getAllLocationsList().size)
        assertEquals(6, imageFiles().size)
        fails { ImageFileHelper.copyAssetToInternalStorage(context, "builtin/missing.png") }
        assertEquals(6, imageFiles().size)
    }

    @Test fun cancellationRollsBackAndCleansFilesWithoutSettingCompletion() = runBlocking {
        val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { BuiltInLibrary(context, db).initialize() }
        context.onFilesAccess = { if (imageFiles().isNotEmpty()) job.cancel() }
        job.start(); job.join()
        context.onFilesAccess = null
        assertTrue(job.isCancelled)
        assertTrue(db.locationDao().getAllLocationsList().isEmpty())
        assertTrue(imageFiles().isEmpty())
        BuiltInLibrary(context, db).initialize()
        assertEquals(6, db.locationDao().getAllLocationsList().size)
    }

    @Test fun newBrandInsertionGetsExactLogoOnceAndFailedInsertLeavesNoFiles() = runBlocking {
        val repository = BrandRepository(db.brandDao(), db.itemDao(), db, BuiltInLibrary(context, db))
        val id = repository.insertBrand(Brand(name = "verified shop", logoUrl = ""))
        val actual = db.brandDao().getBrandById(id)!!
        assertLocalImage(actual.logoUrl)
        val files = imageFiles().map { it.name }.toSet()
        fails { repository.insertBrand(Brand(name = "verified shop")) }
        assertEquals(files, imageFiles().map { it.name }.toSet())
        repository.updateBrand(actual.copy(logoUrl = null))
        assertNull(db.brandDao().getBrandById(id)!!.logoUrl)
        val similarId = repository.insertBrand(Brand(name = "verified shop extra"))
        assertNull(db.brandDao().getBrandById(similarId)!!.logoUrl)
        val photoId = repository.insertBrand(Brand(name = "核验店铺", logoUrl = "/原有照片"))
        assertEquals("/原有照片", db.brandDao().getBrandById(photoId)!!.logoUrl)
        assertEquals(files, imageFiles().map { it.name }.toSet())
    }

    @Test fun restoringSparseSourceNeverAddsDefaultsOrFillsItsMissingImagesAfterRestore() = runBlocking {
        val source = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val room = Location(id = 321, name = "仅源位置", description = "源字段\n完整", imageUrl = null,
            sortOrder = 16, createdAt = 17, updatedAt = 18)
        val brand = Brand(id = 322, name = "核验店铺", logoUrl = "", createdAt = 19, isPreset = true)
        try {
            source.locationDao().insertLocation(room)
            source.brandDao().insertBrand(brand)
            val uri = BackupManager(context, source).exportToJson().getOrThrow().also { published.add(it) }
            val library = BuiltInLibrary(context, db)
            val startup = async(Dispatchers.IO) { library.initialize() }
            val restore = BackupManager(context, db) { library.initialize() }
            restore.importBackup(uri).getOrThrow()
            startup.await()
            repeat(3) { BuiltInLibrary(context, db).initialize() }
            assertEquals(listOf(room), db.locationDao().getAllLocationsList())
            assertEquals(listOf(brand), db.brandDao().getAllBrandsList())
        } finally { source.close() }
    }

    @Test fun failedInitializationIsABackupFailureAndCancellationStillPropagates() = runBlocking {
        val failure = java.io.IOException("fixture initialization failure")
        val manager = BackupManager(context, db) { throw failure }
        assertSame(failure, manager.exportToJson().exceptionOrNull())
        assertSame(failure, manager.exportToCsv().exceptionOrNull())
        assertSame(failure, manager.exportToZip().exceptionOrNull())
        assertSame(failure, manager.previewBackup(Uri.EMPTY).exceptionOrNull())
        assertSame(failure, manager.importBackup(Uri.EMPTY).exceptionOrNull())
        assertTrue(db.locationDao().getAllLocationsList().isEmpty())
        assertTrue(imageFiles().isEmpty())
        val cancellation = CancellationException("fixture cancellation")
        try {
            BackupManager(context, db) { throw cancellation }.exportToZip()
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    @Test fun backupBarrierCompletesDefaultsBeforeZipAndRestoreRemainsFieldExact() = runBlocking {
        db.brandDao().insertBrand(Brand(id = 91, name = "核验店铺", createdAt = 11))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val library = BuiltInLibrary(context, db)
        val manager = BackupManager(context, db) { started.complete(Unit); release.await(); library.initialize() }
        val exporting = async(Dispatchers.IO) { manager.exportToZip().getOrThrow() }
        started.await()
        assertFalse(exporting.isCompleted)
        assertTrue(db.locationDao().getAllLocationsList().isEmpty())
        release.complete(Unit)
        val uri = exporting.await().also { published.add(it) }
        val images = mutableListOf<ByteArray>()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.startsWith("images/")) images.add(zip.readBytes())
                }
            }
        }
        assertEquals(7, images.size)
        images.forEach { assertArrayEquals(fixtureBytes(), it) }
        val rooms = db.locationDao().getAllLocationsList()
        val brands = db.brandDao().getAllBrandsList()
        val targetContext = FixtureContext()
        val target = Room.inMemoryDatabaseBuilder(targetContext, LolitaDatabase::class.java).build()
        try {
            val targetLibrary = BuiltInLibrary(targetContext, target)
            val restoring = BackupManager(targetContext, target) { targetLibrary.initialize() }
            restoring.previewBackup(uri).getOrThrow()
            restoring.importBackup(uri).getOrThrow()
            val restoredRooms = target.locationDao().getAllLocationsList()
            val restoredBrands = target.brandDao().getAllBrandsList()
            assertEquals(rooms.map { it.copy(imageUrl = null) }, restoredRooms.map { it.copy(imageUrl = null) })
            assertEquals(brands.map { it.copy(logoUrl = null) }, restoredBrands.map { it.copy(logoUrl = null) })
            restoredRooms.forEach { assertArrayEquals(fixtureBytes(), File(it.imageUrl!!).readBytes()) }
            restoredBrands.forEach { assertArrayEquals(fixtureBytes(), File(it.logoUrl!!).readBytes()) }
            target.locationDao().deleteAllLocations()
            target.brandDao().updateBrand(restoredBrands.single().copy(logoUrl = null))
            BuiltInLibrary(targetContext, target).initialize()
            assertTrue(target.locationDao().getAllLocationsList().isEmpty())
            assertNull(target.brandDao().getAllBrandsList().single().logoUrl)
        } finally { target.close(); targetContext.cleanup() }
    }
}
