package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.content.ContextWrapper
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.model.*
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.*
import org.junit.Assert.*

class OrderImportSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var context: ContextWrapper
    private lateinit var root: File
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setup() = runBlocking {
        root = File(target.cacheDir, "order-session-${UUID.randomUUID()}").apply { mkdirs() }
        context =
            object : ContextWrapper(target) {
                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            }
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        ImageFileHelper.configure(context, db)
        db.brandDao().insertBrand(Brand(id = 1, name = "测试店铺"))
        db.categoryDao()
            .insertCategory(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK))
        Unit
    }

    @After
    fun close() = runBlocking {
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        delay(300)
        db.close()
        root.deleteRecursively()
        ImageFileHelper.configure(target, AppModule.database())
    }

    private fun vm(): TaobaoImportViewModel {
        lateinit var result: TaobaoImportViewModel
        instrumentation.runOnMainSync {
            result =
                TaobaoImportViewModel(
                    target.applicationContext as Application,
                    BrandRepository(db.brandDao(), db.itemDao(), db),
                    CategoryRepository(db.categoryDao(), db.itemDao(), db),
                    ItemRepository(db.itemDao(), database = db, context = context),
                    PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                    OrderRepository(db, context),
                    db,
                )
            stores += ViewModelStore().also { it.put("import", result) }
        }
        await {
            !result.uiState.value.isRestoring &&
                result.uiState.value.brands.any { it.id == 1L } &&
                result.uiState.value.categories.any { it.id == 1L }
        }
        return result
    }

    private fun state(vm: TaobaoImportViewModel): MutableStateFlow<TaobaoImportUiState> {
        @Suppress("UNCHECKED_CAST")
        return TaobaoImportViewModel::class
            .java
            .getDeclaredField("_uiState")
            .apply { isAccessible = true }
            .get(vm) as MutableStateFlow<TaobaoImportUiState>
    }

    private fun prepare(
        vm: TaobaoImportViewModel,
        quantity: Int = 2,
        orderId: String = "order-source",
    ) {
        val item =
            TaobaoOrderItem(
                "来源样例裙",
                "https://item.taobao.com/item.htm?id=123456",
                "蓝色;M;JSK",
                quantity,
                300.0,
                "JSK",
                "M",
                "蓝色",
                orderId,
                "2026-10-01",
                "测试店铺",
            )
        val order =
            TaobaoOrder(
                orderId,
                "2026-10-01",
                "交易成功",
                "测试店铺",
                310.0 * quantity,
                10.0 * quantity,
                listOf(item),
            )
        instrumentation.runOnMainSync {
            state(vm).value =
                vm.uiState.value.copy(
                    sessionId = UUID.randomUUID().toString(),
                    currentStep = ImportStep.SELECT,
                    orders = listOf(order),
                    selectedItems = setOf("$orderId:0"),
                    fileLoaded = true,
                )
            vm.proceedToPrepare()
        }
        assertTrue(vm.uiState.value.currentStep in setOf(ImportStep.DETAIL, ImportStep.RESULT))
    }

    private fun ready(vm: TaobaoImportViewModel, index: Int = 0) {
        instrumentation.runOnMainSync {
            vm.updateImportItem(index) { it.copy(purchaseDate = "2026-10-01") }
            vm.updateImportItem(index) { it.copy(financialConfirmed = true) }
        }
        assertTrue(
            vm.uiState.value.toString(),
            isImportItemReady(index, vm.uiState.value.importItems),
        )
    }

    private fun submit(vm: TaobaoImportViewModel) {
        instrumentation.runOnMainSync { vm.executeImport() }
        await {
            vm.uiState.value.currentStep == ImportStep.RESULT ||
                vm.uiState.value.errorMessage != null
        }
        assertNull(vm.uiState.value.errorMessage)
    }

    private fun await(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 12000
        while (!check()) {
            check(System.currentTimeMillis() < end) { "导入状态等待超时" }
            Thread.sleep(20)
        }
    }

    @Test
    fun partialCommitRestoresAndRepeatedSourcesDoNotDuplicateItemsOrPayments() = runBlocking {
        val first = vm()
        prepare(first)
        ready(first)
        submit(first)
        assertEquals(1, db.itemDao().getAllItemsList().size)
        assertEquals(1, first.uiState.value.importResult!!.skippedCount)
        val session = first.uiState.value.sessionId!!
        val restored = vm()
        instrumentation.runOnMainSync { restored.restoreSession(session) }
        await { !restored.uiState.value.isRestoring && restored.uiState.value.sessionId == session }
        assertNotNull(restored.uiState.value.importItems[0].committedItemId)
        assertNull(restored.uiState.value.importItems[1].committedItemId)
        ready(restored, 1)
        submit(restored)
        assertEquals(2, db.itemDao().getAllItemsList().size)
        assertEquals(620.0, db.paymentDao().getAllPaymentsList().sumOf { it.amount }, 0.0)
        val repeat = vm()
        prepare(repeat)
        assertTrue(repeat.uiState.value.importItems.all { it.committedItemId != null })
        assertEquals(0, importNewItemCount(repeat.uiState.value.importItems))
        assertEquals(2, db.orderImportDao().receipts().size)
    }

    @Test
    fun rawIncompleteInputAndUnknownDateSurviveNewViewModelWithoutWritingBusinessRows() =
        runBlocking {
            val first = vm()
            prepare(first, 1)
            instrumentation.runOnMainSync {
                first.updateImportItem(0) {
                    it.copy(
                        priceInput = "12.",
                        price = 12.0,
                        paidInput = "",
                        paidAmount = Double.NaN,
                    )
                }
            }
            assertTrue(first.saveDraft())
            val id = first.uiState.value.sessionId!!
            val second = vm()
            instrumentation.runOnMainSync { second.restoreSession(id) }
            await { second.uiState.value.sessionId == id && !second.uiState.value.isRestoring }
            val row = second.uiState.value.importItems.single()
            assertEquals("12.", row.priceInput)
            assertEquals("", row.paidInput)
            assertEquals("", row.purchaseDate)
            assertFalse(isImportItemReady(0, listOf(row)))
            assertTrue(db.itemDao().getAllItemsList().isEmpty())
            assertTrue(db.paymentDao().getAllPaymentsList().isEmpty())
        }

    @Test
    fun concurrentSessionsHaveOneSourceReceiptAndOneActualPayment() = runBlocking {
        val one = vm()
        val two = vm()
        prepare(one, 1)
        prepare(two, 1)
        ready(one)
        ready(two)
        instrumentation.runOnMainSync {
            one.executeImport()
            two.executeImport()
        }
        await {
            one.uiState.value.currentStep != ImportStep.IMPORTING &&
                two.uiState.value.currentStep != ImportStep.IMPORTING
        }
        assertEquals(1, db.itemDao().getAllItemsList().size)
        assertEquals(1, db.paymentDao().getAllPaymentsList().size)
        assertEquals(1, db.orderImportDao().commits().size)
        assertEquals(1, db.orderImportDao().receipts().size)
        assertTrue(one.uiState.value.errorMessage != null || two.uiState.value.errorMessage != null)
    }

    @Test
    fun failedFinalWriteRollsBackWholeBatchAndKeepsDraft() = runBlocking {
        val first = vm()
        prepare(first, 1)
        ready(first)
        instrumentation.runOnMainSync {
            first.updateImportItem(0) { it.copy(categoryId = 999999) }
            first.updateImportItem(0) { it.copy(financialConfirmed = true) }
            first.executeImport()
        }
        await { first.uiState.value.errorMessage != null }
        assertTrue(db.itemDao().getAllItemsList().isEmpty())
        assertTrue(db.priceDao().getAllPricesList().isEmpty())
        assertTrue(db.orderImportDao().receipts().isEmpty())
        assertTrue(db.orderImportDao().commits().isEmpty())
        assertNotNull(db.orderImportDao().session(first.uiState.value.sessionId!!))
    }

    @Test
    fun backup14IncludesSessionsReceiptsSourcesAndRejectsDuplicateReceiptGraph() = runBlocking {
        val first = vm()
        prepare(first)
        ready(first)
        submit(first)
        val manager = BackupManager(context, db)
        val csvUri = manager.exportToCsv().getOrThrow()
        val csv = context.contentResolver.openInputStream(csvUri)!!.bufferedReader().use { it.readText() }
        val sourceLines = csv.substringAfter("物品ID,来源类型,公开商品链接,记录时间").substringBefore("=== SOURCES ===")
        assertTrue(sourceLines.contains("ORDER_IMPORT"))
        assertFalse(sourceLines.contains("taobao:v1:"))
        assertFalse(sourceLines.contains("import_token"))
        val uri = manager.exportToJson().getOrThrow()
        val json =
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject
            }
        assertEquals(14, json["formatVersion"].asInt)
        assertEquals(33, json["schemaVersion"].asInt)
        assertEquals(1, json["orderImportSessions"].asJsonArray.size())
        assertEquals(2, json["orderImportRows"].asJsonArray.size())
        assertEquals(1, json["orderImportReceipts"].asJsonArray.size())
        val preview = manager.previewBackup(uri).getOrThrow()
        assertEquals(1, preview.orderImportReceiptCount)
        val before = Gson().toJson(db.orderImportDao().receipts())
        manager.importBackup(uri).getOrThrow()
        assertEquals(before, Gson().toJson(db.orderImportDao().receipts()))
        val duplicate = json.deepCopy()
        duplicate["orderImportReceipts"]
            .asJsonArray
            .add(duplicate["orderImportReceipts"].asJsonArray[0].deepCopy())
        val bad =
            File(context.cacheDir, "bad-${UUID.randomUUID()}.json").apply {
                writeText(duplicate.toString())
            }
        assertTrue(manager.importBackup(Uri.fromFile(bad)).isFailure)
        assertEquals(before, Gson().toJson(db.orderImportDao().receipts()))
        val legacy = json.deepCopy()
        legacy.addProperty("formatVersion", 13)
        legacy.addProperty("schemaVersion", 32)
        listOf(
                "orderImportSessions",
                "orderImportFiles",
                "orderImportRows",
                "orderImportCommits",
                "orderImportReceipts",
                "itemSourceRecords",
            )
            .forEach { legacy.remove(it) }
        val old =
            File(context.cacheDir, "old-${UUID.randomUUID()}.json").apply {
                writeText(legacy.toString())
            }
        manager.importBackup(Uri.fromFile(old)).getOrThrow()
        assertTrue(db.orderImportDao().sessions().isEmpty())
        assertEquals(1, db.itemDao().getAllItemsList().size)
    }

    @Test
    fun fileConflictIsVisibleAndMoneyIsNotAcceptedAsZero() = runBlocking {
        fun file(paid: String): File =
            File(context.cacheDir, "$paid.xlsx").also { out ->
                XSSFWorkbook().use { book ->
                    val sheet = book.createSheet()
                    val head = sheet.createRow(0)
                    val row = sheet.createRow(1)
                    listOf(
                            "订单号",
                            "订单提交时间",
                            "订单状态",
                            "店铺名称",
                            "商品名称",
                            "商品链接",
                            "型号款式",
                            "商品数量",
                            "商品金额",
                            "实付金额",
                            "运费",
                        )
                        .forEachIndexed { i, v -> head.createCell(i).setCellValue(v) }
                    listOf(
                            "o-1",
                            "2026-10-01",
                            "交易成功",
                            "测试店铺",
                            "样例裙",
                            "https://item.taobao.com/item.htm?id=1",
                            "蓝色;M;JSK",
                            "1",
                            "300",
                            paid,
                            "0",
                        )
                        .forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
                    out.outputStream().use(book::write)
                }
            }
        val first = vm()
        instrumentation.runOnMainSync {
            first.onFileSelected(listOf(Uri.fromFile(file("300")), Uri.fromFile(file("299"))))
        }
        await { !first.uiState.value.isLoading && first.uiState.value.fileLoaded }
        assertEquals(1, first.uiState.value.conflicts.size)
        instrumentation.runOnMainSync { first.proceedToPrepare() }
        assertEquals(ImportStep.CONFLICT, first.uiState.value.currentStep)
        instrumentation.runOnMainSync {
            first.resolveConflict("o-1", 1)
            first.proceedToPrepare()
        }
        assertEquals(299.0, first.uiState.value.importItems.single().paidAmount, 0.0)
        assertFalse(isImportItemReady(0, first.uiState.value.importItems))
    }

    @Test
    fun committedOperationRecoversResultWithoutReplayingAndDeletedSourceRequiresExplicitGeneration() =
        runBlocking {
            val first = vm()
            prepare(first, 1)
            ready(first)
            submit(first)
            val dao = db.orderImportDao()
            val session = dao.session(first.uiState.value.sessionId!!)!!
            val commit = dao.commits().single()
            val json = JsonParser.parseString(session.dataJson).asJsonObject
            json["state"].asJsonObject.addProperty("currentStep", "IMPORTING")
            dao.saveSession(
                session.copy(
                    dataJson = json.toString(),
                    state = "IMPORTING",
                    pendingOperationId = commit.id,
                )
            )
            val restored = vm()
            instrumentation.runOnMainSync { restored.restoreSession(session.id) }
            await {
                restored.uiState.value.sessionId == session.id &&
                    !restored.uiState.value.isRestoring
            }
            assertEquals(ImportStep.RESULT, restored.uiState.value.currentStep)
            assertEquals(commit.id, restored.uiState.value.importResult!!.commitId)
            assertEquals(1, db.paymentDao().getAllPaymentsList().size)
            ItemRepository(db.itemDao(), database = db, context = context)
                .deleteItem(db.itemDao().getAllItemsList().single())
            assertNull(dao.receipts().single().itemId)
            val again = vm()
            prepare(again, 1)
            assertTrue(again.uiState.value.importItems.single().sourceProblem!!.contains("已删除"))
            assertFalse(isImportItemReady(0, again.uiState.value.importItems))
            instrumentation.runOnMainSync { again.confirmSourceIdentity(0, true) }
            ready(again)
            submit(again)
            assertEquals(listOf(1, 2), dao.receipts().map { it.generation }.sorted())
            assertEquals(1, db.itemDao().getAllItemsList().size)
            assertEquals(1, db.paymentDao().getAllPaymentsList().size)
        }

    @Test
    fun draftPhotoZipRoundTripRemapsEveryReferenceAndDiscardProtectsSharedImage() = runBlocking {
        val first = vm()
        prepare(first, 1)
        val image =
            File(File(context.filesDir, "images").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        val bitmap =
            android.graphics.Bitmap.createBitmap(19, 37, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(0xff715581.toInt()) }
        image.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val bytes = image.readBytes()
        instrumentation.runOnMainSync {
            first.updateImportItem(0) { it.copy(imageUrl = image.path) }
        }
        assertTrue(first.saveDraft())
        ImageFileHelper.deleteImage(image.path)
        assertTrue(image.exists())
        val manager = BackupManager(context, db)
        val zip = manager.exportToZip().getOrThrow()
        manager.importBackup(zip).getOrThrow()
        val row = db.orderImportDao().rows().single()
        val mapped = File(row.imagePaths.single())
        assertArrayEquals(bytes, mapped.readBytes())
        assertFalse(row.dataJson.contains(image.path))
        val shared =
            db.itemDao()
                .insertItem(
                    Item(
                        brandId = 1,
                        categoryId = 1,
                        name = "共享照片",
                        description = "",
                        status = ItemStatus.OWNED,
                        imageUrls = listOf(mapped.path),
                    )
                )
        val paths = OrderImportRepository(db).discard(row.sessionId)
        ImageFileHelper.cleanupUnreferencedImages(paths)
        assertTrue(mapped.exists())
        assertEquals(listOf(mapped.path), db.itemDao().getItemById(shared)!!.imageUrls)
    }

    @Test
    fun sourceReplacementPreservesEditedInputsAndRemovalReportsExactUnsubmittedScope() =
        runBlocking {
            val first = vm()
            prepare(first, 1)
            instrumentation.runOnMainSync {
                first.updateImportItem(0) { it.copy(name = "手工名称", priceInput = "12.") }
            }
            val old = first.uiState.value.orders.single()
            val changed = old.copy(totalPaid = 299.0, rawPaid = "299")
            instrumentation.runOnMainSync {
                state(first).value =
                    first.uiState.value.copy(
                        conflicts = listOf(ImportSourceConflict(old.orderId, listOf(old, changed)))
                    )
                first.resolveConflict(old.orderId, 1)
                first.proceedToPrepare()
            }
            val row = first.uiState.value.importItems.single()
            assertEquals("手工名称", row.name)
            assertEquals("12.", row.priceInput)
            assertFalse(row.financialConfirmed)
            assertNotNull(row.sourceProblem)
            val file =
                OrderImportFile(
                    sessionId = first.uiState.value.sessionId!!,
                    name = "source.xlsx",
                    sha256 = "a".repeat(64),
                    state = "READY",
                    dataJson = Gson().toJson(listOf(changed)),
                )
            instrumentation.runOnMainSync {
                state(first).value = first.uiState.value.copy(files = listOf(file))
            }
            assertEquals(listOf(row), first.affectedByFileRemoval(file.id))
            instrumentation.runOnMainSync { first.removeFile(file.id) }
            assertTrue(first.uiState.value.importItems.isEmpty())
            assertTrue(db.itemDao().getAllItemsList().isEmpty())
        }

    @Test
    fun staleSessionCannotOverwriteOrResurrectDiscardedDraft() = runBlocking {
        val first = vm()
        prepare(first, 1)
        assertTrue(first.saveDraft())
        val id = first.uiState.value.sessionId!!
        val other = vm()
        instrumentation.runOnMainSync { other.restoreSession(id) }
        await { other.uiState.value.sessionId == id && !other.uiState.value.isRestoring }
        instrumentation.runOnMainSync { other.updateImportItem(0) { it.copy(name = "新页面的核对输入") } }
        assertTrue(other.saveDraft())
        instrumentation.runOnMainSync { first.updateImportItem(0) { it.copy(name = "旧页面") } }
        assertFalse(first.saveDraft())
        assertTrue(db.orderImportDao().rows(id).single().dataJson.contains("新页面的核对输入"))
        OrderImportRepository(db).discard(id)
        assertFalse(other.saveDraft())
        assertNull(db.orderImportDao().session(id))
    }
}
