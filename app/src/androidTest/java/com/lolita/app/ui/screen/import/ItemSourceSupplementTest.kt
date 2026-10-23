package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.CategorySemantic
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class ItemSourceSupplementTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val target = ins.targetContext
    private lateinit var context: ContextWrapper
    private lateinit var root: File
    private lateinit var db: LolitaDatabase
    private lateinit var items: ItemRepository
    private lateinit var drafts: ItemDraftRepository
    private lateinit var original: Item
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setup() = runBlocking {
        root = File(target.cacheDir, "supplement-${UUID.randomUUID()}").apply { mkdirs() }
        context =
            object : ContextWrapper(target) {
                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            }
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        ImageFileHelper.configure(context, db)
        items = ItemRepository(db.itemDao(), database = db, context = context)
        drafts = ItemDraftRepository(db, items)
        db.brandDao().insertBrand(Brand(id = 1, name = "原品牌"))
        db.categoryDao()
            .insertCategory(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK))
        db.locationDao().insertLocation(Location(id = 1, name = "原位置"))
        original =
            Item(
                id = 1,
                brandId = 1,
                categoryId = 1,
                name = "人工名称",
                description = "手工备注",
                size = "M",
                colors = listOf("绀色"),
                status = ItemStatus.OWNED,
                imageUrls = listOf(image(0xff556677.toInt())),
                locationId = 1,
                importToken = "preserve-original-token",
                createdAt = 1234,
                updatedAt = 2345,
            )
        db.itemDao().insertItem(original)
        Unit
    }

    @After
    fun close() = runBlocking {
        ins.runOnMainSync { stores.forEach { it.clear() } }
        db.close()
        root.deleteRecursively()
        ImageFileHelper.configure(target, AppModule.database())
    }

    private fun image(color: Int): String {
        val file =
            File(File(context.filesDir, "images").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        val bitmap =
            Bitmap.createBitmap(21, 43, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.path
    }

    private fun candidate() =
        TaobaoProductDraft(
            "网页名称",
            "网页品牌",
            "L",
            "颜色分类：蓝色\n材质：棉",
            "网页说明",
            "https://item.taobao.com/item.htm?id=99&spm=tracking",
            emptyList(),
            imagePaths = listOf(image(0xff556677.toInt()), image(0xff8899aa.toInt())),
            sizeChartImagePaths = listOf(image(0xffaabbcc.toInt())),
        )

    private fun vm(): ItemSourceSupplementViewModel {
        lateinit var vm: ItemSourceSupplementViewModel
        ins.runOnMainSync {
            vm =
                ItemSourceSupplementViewModel(
                    target.applicationContext as Application,
                    1,
                    db,
                    items,
                    drafts,
                )
            stores += ViewModelStore().also { it.put("supplement", vm) }
        }
        await { !vm.state.value.busy }
        return vm
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (!predicate()) {
            check(System.currentTimeMillis() < deadline) { "补资料等待超时" }
            Thread.sleep(15)
        }
    }

    private fun stage(vm: ItemSourceSupplementViewModel) {
        ins.runOnMainSync { vm.stage(candidate()) }
        await { !vm.state.value.busy && vm.state.value.candidate != null }
        assertNull(vm.state.value.error)
    }

    @Test
    fun appendDeduplicatesBytesKeepsCoverAndNeverChangesIdentityOwnershipLocationOrLedger() =
        runBlocking {
            val vm = vm()
            stage(vm)
            var completed = false
            ins.runOnMainSync {
                vm.save(true) {
                    assertNull(it)
                    completed = true
                }
            }
            await { completed || vm.state.value.error != null }
            assertNull(vm.state.value.error)
            val updated = items.getItemById(1)!!
            assertEquals(2, updated.imageUrls.size)
            assertEquals(original.imageUrls.first(), updated.imageUrls.first())
            assertNotNull(updated.sizeChartImageUrl)
            assertEquals(
                original,
                updated.copy(
                    imageUrls = original.imageUrls,
                    sizeChartImageUrl = null,
                    sizeChartImageUrls = emptyList(),
                    updatedAt = original.updatedAt,
                ),
            )
            assertTrue(db.priceDao().getAllPricesList().isEmpty())
            assertTrue(db.paymentDao().getAllPaymentsList().isEmpty())
            assertNull(drafts.getForItem(1))
            val source = db.orderImportDao().sources().single()
            assertEquals("https://item.taobao.com/item.htm?id=99", source.publicUrl)
            assertEquals(setOf("photos", "details"), source.appliedFields.toSet())
            assertFalse(source.snapshotJson.contains(context.filesDir.path))
        }

    @Test
    fun existingDraftIsTheOnlyWriterAndEvidenceIsAddedOnlyWhenThatDraftCommits() = runBlocking {
        val prior =
            ItemDraft(
                targetItemId = 1,
                dataJson =
                    ItemDraftData(
                            name = "草稿人工名称",
                            description = "草稿人工备注",
                            brandId = 1,
                            categoryId = 1,
                            status = original.status,
                            size = original.size,
                            colors = original.colors,
                            locationId = 1,
                            importToken = original.importToken,
                            baselineUpdatedAt = original.updatedAt,
                        )
                        .encode(),
                imagePaths = original.imageUrls,
            )
        drafts.save(prior)
        val vm = vm()
        stage(vm)
        var editor: String? = null
        ins.runOnMainSync {
            vm.select("name", true)
            vm.save(true) { editor = it }
        }
        await { editor != null || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        assertEquals(prior.id, editor)
        assertEquals(original, items.getItemById(1))
        assertTrue(db.orderImportDao().sources().isEmpty())
        val staged = drafts.get(prior.id)!!
        assertEquals("草稿人工备注", staged.data().description)
        drafts.commit(
            staged.id,
            original.copy(
                name = staged.data().name,
                description = staged.data().description,
                imageUrls = staged.imagePaths,
                sizeChartImageUrl = staged.sizeChartImagePaths.firstOrNull(),
                sizeChartImageUrls = staged.sizeChartImagePaths.drop(1),
            ),
        )
        assertEquals("网页名称", items.getItemById(1)!!.name)
        assertEquals(1, db.orderImportDao().sources().size)
    }

    @Test
    fun staleItemAndUnconfirmedSkuCannotOverwriteCurrentData() = runBlocking {
        val vm = vm()
        stage(vm)
        ins.runOnMainSync { vm.save(false) { fail("未确认规格不能保存") } }
        await { vm.state.value.error != null }
        assertEquals(original, items.getItemById(1))
        items.updateItem(original.copy(name = "其他页面的新名称"))
        ins.runOnMainSync { vm.save(true) { fail("陈旧比较不能保存") } }
        await { !vm.state.value.busy }
        assertTrue(vm.state.value.error!!.contains("更新"))
        assertEquals("其他页面的新名称", items.getItemById(1)!!.name)
        assertNotNull(drafts.getForItem(1))
        assertTrue(db.orderImportDao().sources().isEmpty())
    }

    @Test
    fun pendingSupplementSurvivesZipRestoreWithExplicitImageReferences() = runBlocking {
        val vm = vm()
        stage(vm)
        val draft = drafts.getForItem(1)!!
        val hashes = draft.evidenceImagePaths.map { File(it).readBytes().toList() }
        val backup = BackupManager(context, db)
        val zip = backup.exportToZip().getOrThrow()
        backup.importBackup(zip).getOrThrow()
        val restored = drafts.getForItem(1)!!
        restored.validate()
        assertEquals(hashes, restored.evidenceImagePaths.map { File(it).readBytes().toList() })
        val next = vm()
        assertNotNull(next.state.value.candidate)
        assertEquals(2, next.state.value.candidate!!.imagePaths.size)
        assertEquals(original.name, items.getItemById(1)!!.name)
        assertTrue(db.orderImportDao().sources().isEmpty())
    }
}
