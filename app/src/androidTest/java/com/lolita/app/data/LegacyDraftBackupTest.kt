package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.system.Os
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Historical filenames are real Item references; copying them must not break a full backup. */
@RunWith(AndroidJUnit4::class)
class LegacyDraftBackupTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val gson = Gson()

    private inner class Fixture(private val legacyNames: Boolean) {
        val root = File(target.cacheDir, "legacy-draft-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val exports = mutableListOf<Uri>()
        val items = ItemRepository(db.itemDao(), database = db, context = context)
        val drafts = ItemDraftRepository(db, items)
        var pauses = 0
        var serviceStarts = 0
        val manager = BackupManager(context, db, withImageTasksPaused = { action -> pauses++; action() })
        val coordinator by lazy {
            ImageTaskCoordinator(db, scope, { serviceStarts++ },
                { ImageFileHelper.saveGeneratedImage(context, it) }, ImageFileHelper::deleteImage,
                { ImageFileHelper.isOwnedGeneratedImage(context, it) },
                validateSourceImage = { ImageFileHelper.isOwnedReadableImage(context, it) },
                snapshotBuiltInImage = { ImageFileHelper.copyAssetToInternalStorage(context, it) },
                validateProductImage = { ImageFileHelper.isOwnedReadableProductImage(context, it) })
        }
        lateinit var original: String
        lateinit var generated: String
        lateinit var chart: String
        lateinit var chartTwo: String
        lateinit var region: String
        lateinit var item: Item
        lateinit var price: Price
        lateinit var stage: PaymentStage
        lateinit var payment: Payment
        lateinit var copyDraftId: String
        lateinit var editDraftId: String
        private var colorNumber = 0

        fun image(name: String = "${UUID.randomUUID()}.png"): String {
            val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
            colorNumber++
            bitmap.eraseColor(Color.rgb(colorNumber * 37 % 255, colorNumber * 53 % 255, colorNumber * 71 % 255))
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
            }
            bitmap.recycle()
            return File(File(context.filesDir, "images").apply { mkdirs() }, name).apply { writeBytes(bytes) }.absolutePath
        }

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            original = image(if (legacyNames) "1000033962.jpg" else "${UUID.randomUUID()}.png")
            generated = image(if (legacyNames) "import_2024_generated_image.png" else "${UUID.randomUUID()}.png")
            chart = image(if (legacyNames) "old_size_chart.jpg" else "${UUID.randomUUID()}.png")
            chartTwo = image(if (legacyNames) "商品尺码表-第二张.png" else "${UUID.randomUUID()}.png")
            region = image()
            db.brandDao().insertBrand(Brand(id = 1, name = "旧品牌", createdAt = 10))
            db.categoryDao().insertCategory(Category(id = 1, name = "JSK", categorySemantic = CategorySemantic.JSK))
            db.locationDao().insertLocation(Location(id = 1, name = "原衣帽间", description = "原房间说明", createdAt = 12, updatedAt = 13))
            db.storageNodeDao().insert(StorageNode(id = 3, locationId = 1, name = "原衣柜", createdAt = 14, updatedAt = 15))
            item = Item(id = 1, brandId = 1, categoryId = 1, name = "旧资料连衣裙", description = "旧备注须保留", status = ItemStatus.OWNED,
                imageUrls = listOf(original, generated, original), size = "定制 88 / 70", colors = listOf("绀色", "奶白"),
                source = "淘宝旧订单", style = "古典", season = "春,秋", locationId = 1, storageNodeId = 3,
                composition = ItemComposition.SINGLE, sizeChartImageUrl = chart, sizeChartImageUrls = listOf(chartTwo, chart),
                createdAt = 100, updatedAt = 101)
            db.itemDao().insertItem(item)
            db.itemDao().insertItem(item.copy(id = 2, name = "共价腰带", imageUrls = emptyList(), parentItemId = 1,
                includedInParentPrice = true, sizeChartImageUrl = null, sizeChartImageUrls = emptyList(), importToken = "旧腰带导入标识"))
            price = Price(id = 5, itemId = 1, type = PriceType.DEPOSIT_BALANCE, totalPrice = 321.67, shipping = 12.34,
                deposit = 100.0, balance = 209.33, purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED, createdAt = 200)
            db.priceDao().insertPrice(price)
            db.orderDao().insertBinding(OrderBinding(1, 5, PurchaseRecordStatus.HISTORICAL_OWNED))
            stage = PaymentStage(id = 6, priceId = 5, name = "原尾款", amount = 209.33, dueDate = 1_700_000_000_001, customReminderDays = 4)
            db.orderDao().insertStage(stage)
            payment = Payment(id = 7, priceId = 5, stageId = 6, amount = 178.91, dueDate = 1_700_000_000_001,
                paidDate = 1_700_000_000_002, isPaid = true, createdAt = 203, customReminderDays = 2)
            db.paymentDao().insertPayment(payment)
            db.imageProvenanceDao().upsert(ImageProvenance(original, 1, ImageSourceType.ORIGINAL, "历史原图", createdAt = 204))
            db.imageProvenanceDao().upsert(ImageProvenance(generated, 1, ImageSourceType.AI_GENERATED, "legacy_display", createdAt = 205))
        }

        suspend fun copyAndEditDrafts() {
            copyDraftId = drafts.copyFromItem(1)
            editDraftId = drafts.copyFromItem(1)
            val copy = drafts.get(editDraftId)!!
            drafts.save(copy.copy(targetItemId = 1, dataJson = copy.data().copy(name = "尚未保存的新名称", baselineUpdatedAt = item.updatedAt,
                locationId = 1, storageNodeId = 3).encode(), evidenceImagePaths = listOf(region)))
        }

        suspend fun submit(originalOnly: Boolean): ImageTask {
            coordinator.awaitStartup()
            val source = if (originalOnly) original else image()
            val subject = requireNotNull(mannequinSubject(db.categoryDao().getCategoryById(1), confirmedSemantics = true))
            val id = coordinator.submitMannequin(MannequinRequest(1, source, subject = subject,
                originalImagePath = if (originalOnly) null else original), ImageGenerationProvider.SEEDREAM)
            val task = db.imageTaskDao().get(id)!!
            // Preserve a completed frozen request without running any executor or real service.
            return task.copy(status = ImageTaskStatus.COMPLETED, requestSent = true).also { db.imageTaskDao().update(it) }
        }

        suspend fun export(zip: Boolean, label: String): Uri {
            val result = if (zip) manager.exportToZip() else manager.exportToJson()
            assertTrue("$label: ${if (zip) "ZIP" else "JSON"} export rejected valid historical Item references: ${result.exceptionOrNull()}", result.isSuccess)
            return result.getOrThrow().also(exports::add)
        }

        fun read(uri: Uri): JsonObject = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fun jsonUri(json: JsonObject): Uri = Uri.fromFile(File(context.cacheDir, "${UUID.randomUUID()}.json").apply { writeText(json.toString()) })
        suspend fun state(): String = gson.toJson(listOf(db.brandDao().getAllBrandsList(), db.categoryDao().getAllCategoriesList(),
            db.locationDao().getAllLocationsList(), db.storageNodeDao().getAll(), db.itemDao().getAllItemsList(), db.priceDao().getAllPricesList(),
            db.paymentDao().getAllPaymentsList(), db.orderDao().getAllBindings(), db.orderDao().getAllStages(), db.itemDraftDao().getAll(),
            db.recognitionDao().getAll(), db.recognitionDao().getEvidence(), db.imageProvenanceDao().getAll(), db.imageTaskDao().getAll()))

        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            exports.forEach { context.contentResolver.delete(it, null, null) }
            db.close(); root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
        }
    }

    private suspend fun fixture(legacyNames: Boolean = true, block: suspend (Fixture) -> Unit) {
        val f = Fixture(legacyNames)
        try { f.init(); withTimeout(60_000) { block(f) } } finally { f.close() }
    }

    @Test fun copiedAndEditedLegacyItemDraftsSurviveJsonExportPreviewAndRestore() = runBlocking { draftRoundTrip(false) }
    @Test fun copiedAndEditedLegacyItemDraftsSurviveZipExportPreviewAndRestore() = runBlocking { draftRoundTrip(true) }

    private suspend fun draftRoundTrip(zip: Boolean) = fixture { f ->
        f.copyAndEditDrafts()
        val label = "legacy album/size-chart ${if (zip) "ZIP" else "JSON"}"
        assertFalse("$label fixture must exercise a non-UUID historical filename", ImageFileHelper.isOwnedReadableImage(f.context, f.original))
        assertTrue("$label historical bytes must be complete and privately owned", ImageFileHelper.isOwnedReadableProductImage(f.context, f.original))
        val beforeItem = f.db.itemDao().getItemById(1)!!
        val beforeDrafts = f.db.itemDraftDao().getAll().associateBy { it.id }
        assertEquals("$label real copy must share original image order", beforeItem.imageUrls, beforeDrafts.getValue(f.copyDraftId).imagePaths)
        assertEquals("$label generated provenance must reach the copied draft", listOf(f.generated), beforeDrafts.getValue(f.copyDraftId).appearanceOnlyImagePaths)
        val expectedAlbum = beforeItem.imageUrls.map(::imageSha256)
        val expectedCharts = (listOfNotNull(beforeItem.sizeChartImageUrl) + beforeItem.sizeChartImageUrls).map(::imageSha256)
        val expectedDraftHashes = beforeDrafts.mapValues { (_, draft) -> draft.protectedImagePaths().map(::imageSha256) }
        val originals = listOf(f.original, f.generated, f.chart, f.chartTwo, f.region).associateWith(::imageSha256)
        val uri = f.export(zip, label)
        if (zip) {
            val entries = zipEntries(f, uri)
            assertTrue("$label must include an integrity manifest", "image_manifest.json" in entries)
            val json = JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject
            assertTrue("$label every copied/edited draft photo must be archived", json.getAsJsonArray("itemDrafts").all { row ->
                listOf("imagePaths", "sizeChartImagePaths", "evidenceImagePaths", "appearanceOnlyImagePaths").all { key ->
                    row.asJsonObject.getAsJsonArray(key).all { it.asString.startsWith("zip://images/") }
                }
            })
        }
        val preview = f.manager.previewBackup(uri)
        assertTrue("$label preview must accept complete legacy draft references: ${preview.exceptionOrNull()}", preview.isSuccess)
        assertEquals("$label draft count", 2, preview.getOrThrow().itemDraftCount)
        assertEquals("$label item count", 2, preview.getOrThrow().itemCount)
        assertEquals("$label must have no missing images", 0, preview.getOrThrow().missingImageCount)
        val imported = f.manager.importBackup(uri)
        assertTrue("$label restore rejected complete legacy draft references: ${imported.exceptionOrNull()}", imported.isSuccess)
        assertEquals("$label all previewed records must restore", preview.getOrThrow().totalCount, imported.getOrThrow().totalImported)
        val restoredItem = f.db.itemDao().getItemById(1)!!
        assertEquals("$label original business fields", beforeItem.copy(imageUrls = restoredItem.imageUrls,
            sizeChartImageUrl = restoredItem.sizeChartImageUrl, sizeChartImageUrls = restoredItem.sizeChartImageUrls), restoredItem)
        assertEquals("$label original image bytes and order", expectedAlbum, restoredItem.imageUrls.map(::imageSha256))
        assertEquals("$label duplicate album reference", restoredItem.imageUrls.first(), restoredItem.imageUrls.last())
        assertEquals("$label size-chart bytes and order", expectedCharts,
            (listOfNotNull(restoredItem.sizeChartImageUrl) + restoredItem.sizeChartImageUrls).map(::imageSha256))
        beforeDrafts.forEach { (id, before) ->
            val restored = f.drafts.get(id)!!
            assertEquals("$label draft data and target $id", before.copy(imagePaths = restored.imagePaths, sizeChartImagePaths = restored.sizeChartImagePaths,
                evidenceImagePaths = restored.evidenceImagePaths, appearanceOnlyImagePaths = restored.appearanceOnlyImagePaths), restored)
            assertEquals("$label draft bytes and order $id", expectedDraftHashes.getValue(id), restored.protectedImagePaths().map(::imageSha256))
            assertEquals("$label shared album paths $id", restoredItem.imageUrls, restored.imagePaths)
            assertEquals("$label shared size-chart paths $id", listOfNotNull(restoredItem.sizeChartImageUrl) + restoredItem.sizeChartImageUrls, restored.sizeChartImagePaths)
            assertEquals("$label generated-source identity $id", listOf(restoredItem.imageUrls[1]), restored.appearanceOnlyImagePaths)
        }
        val provenance = f.db.imageProvenanceDao().getForItem(1).associateBy { it.sourceType }
        assertEquals("$label original-source provenance", restoredItem.imageUrls.first(), provenance.getValue(ImageSourceType.ORIGINAL).imagePath)
        assertEquals("$label generated-source provenance", restoredItem.imageUrls[1], provenance.getValue(ImageSourceType.AI_GENERATED).imagePath)
        assertEquals("$label generated-source mode", "legacy_display", provenance.getValue(ImageSourceType.AI_GENERATED).mode)
        assertEquals("$label original price", f.price, f.db.priceDao().getPriceById(5))
        assertEquals("$label original payment", f.payment, f.db.paymentDao().getPaymentById(7))
        assertEquals("$label original stage", f.stage, f.db.orderDao().getStage(6))
        assertEquals("$label original binding", OrderBinding(1, 5, PurchaseRecordStatus.HISTORICAL_OWNED), f.db.orderDao().getBinding(1))
        assertEquals("$label parent/child relation", 1L, f.db.itemDao().getItemById(2)!!.parentItemId)
        assertEquals("$label source images must never be renamed or overwritten", originals, originals.keys.associateWith(::imageSha256))
        assertEquals("$label restore barrier count", 1, f.pauses)
        assertEquals("$label no image service may start", 0, f.serviceStarts)
    }

    @Test fun admittedLegacyOriginalAndCroppedV4TasksSurviveJsonWithoutBeingMarkedMissing() = runBlocking { taskRoundTrip(false) }
    @Test fun admittedLegacyOriginalAndCroppedV4TasksSurviveZipWithFrozenInputsIntact() = runBlocking { taskRoundTrip(true) }
    @Test fun frozenLegacyOriginalsSurviveJsonAfterTheItemAlbumHasChanged() = runBlocking { taskRoundTrip(false, removeItemReference = true) }
    @Test fun frozenLegacyOriginalsSurviveZipAfterTheItemAlbumHasChanged() = runBlocking { taskRoundTrip(true, removeItemReference = true) }

    private suspend fun taskRoundTrip(zip: Boolean, removeItemReference: Boolean = false) = fixture { f ->
        val tasks = listOf(f.submit(originalOnly = true), f.submit(originalOnly = false))
        if (removeItemReference) {
            val current = f.db.itemDao().getItemById(1)!!
            f.items.updateItem(current.copy(imageUrls = listOf(f.image()), updatedAt = current.updatedAt + 1))
        }
        val label = "admitted v4 historical product ${if (zip) "ZIP" else "JSON"}; changedAlbum=$removeItemReference"
        val hashes = tasks.associate { it.id to it.sourceImagePaths.map(::imageSha256) }
        assertEquals("$label direct task must exercise a historical input filename", f.original, tasks.first().sourceImagePaths.last())
        assertEquals("$label crop task must retain the historical original", f.original, ImageTaskRequest.decode(tasks.last().requestJson).productOriginalImagePath)
        val starts = f.serviceStarts
        val uri = f.export(zip, label)
        val preview = f.manager.previewBackup(uri)
        assertTrue("$label preview failed: ${preview.exceptionOrNull()}", preview.isSuccess)
        assertEquals("$label missing count", 0, preview.getOrThrow().missingImageCount)
        assertEquals("$label task count", 2, preview.getOrThrow().imageTaskCount)
        val imported = f.manager.importBackup(uri)
        assertTrue("$label restore failed: ${imported.exceptionOrNull()}", imported.isSuccess)
        val album = f.db.itemDao().getItemById(1)!!.imageUrls
        tasks.forEach { before ->
            val restored = f.db.imageTaskDao().get(before.id)!!
            val request = ImageTaskRequest.decode(restored.requestJson)
            val beforeRequest = ImageTaskRequest.decode(before.requestJson)
            assertFalse("$label complete old-name inputs must never be falsely marked missing", restored.sourceResourcesMissing)
            assertTrue("$label immutable input retention", restored.keepSourceImages)
            assertEquals("$label completed status", ImageTaskStatus.COMPLETED, restored.status)
            assertEquals("$label immutable bytes and order", hashes.getValue(before.id), restored.sourceImagePaths.map(::imageSha256))
            assertEquals("$label prompt/provider snapshot", beforeRequest.copy(productOriginalImagePath = request.productOriginalImagePath), request)
            request.validateInputImages(restored.sourceImagePaths)
            val originalReference = request.productOriginalImagePath ?: restored.sourceImagePaths.last()
            if (removeItemReference) assertTrue("$label historical frozen original must survive independently of current Item references", originalReference !in album)
            else assertEquals("$label original reference shares restored album", album.first(), originalReference)
        }
        assertEquals("$label restore must not restart paid generation", starts, f.serviceStarts)
        assertEquals("$label original order survives", f.price, f.db.priceDao().getPriceById(5))
        assertEquals("$label original payment survives", f.payment, f.db.paymentDao().getPaymentById(7))
    }

    private suspend fun rejectDraftPath(label: String, path: (Fixture) -> String) = fixture(legacyNames = false) { f ->
        f.copyAndEditDrafts()
        val valid = f.read(f.export(false, "$label valid baseline"))
        val bad = path(f)
        val draft = f.drafts.get(f.editDraftId)!!
        f.drafts.save(draft.copy(imagePaths = listOf(bad), appearanceOnlyImagePaths = emptyList()))
        valid.getAsJsonArray("itemDrafts").single { it.asJsonObject["id"].asString == f.editDraftId }.asJsonObject.apply {
            add("imagePaths", gson.toJsonTree(listOf(bad))); add("appearanceOnlyImagePaths", gson.toJsonTree(emptyList<String>()))
        }
        val before = f.state()
        val json = f.manager.exportToJson(); json.getOrNull()?.let(f.exports::add)
        val zip = f.manager.exportToZip(); zip.getOrNull()?.let(f.exports::add)
        assertTrue("$label JSON export must reject unsafe draft album input; ZIP failure=${zip.exceptionOrNull()}", json.isFailure)
        assertTrue("$label ZIP export must reject unsafe draft album input", zip.isFailure)
        val uri = f.jsonUri(valid)
        assertTrue("$label JSON preview must reject unsafe draft album input", f.manager.previewBackup(uri).isFailure)
        assertTrue("$label JSON restore must reject unsafe draft album input", f.manager.importBackup(uri).isFailure)
        assertEquals("$label validation must precede restore barrier", 0, f.pauses)
        assertEquals("$label failed backup/restore must preserve database", before, f.state())
    }

    @Test fun missingLegacyDraftAlbumCannotBeReportedAsACompleteBackup() = runBlocking {
        rejectDraftPath("missing legacy draft album") { f -> File(f.context.filesDir, "images/missing-old-image.jpg").absolutePath }
    }
    @Test fun damagedLegacyDraftAlbumCannotBeReportedAsACompleteBackup() = runBlocking {
        rejectDraftPath("damaged legacy draft album") { f -> f.image("damaged-old-image.jpg").also { path ->
            val file = File(path); file.writeBytes(file.readBytes().copyOf(file.length().toInt() / 2))
        } }
    }
    @Test fun externalDraftAlbumCannotUseHistoricalFilenameCompatibility() = runBlocking {
        rejectDraftPath("external draft album") { f -> File(f.root, "outside.png").apply { writeBytes(File(f.original).readBytes()) }.absolutePath }
    }
    @Test fun symlinkedDraftAlbumCannotUseHistoricalFilenameCompatibility() = runBlocking {
        rejectDraftPath("symlinked draft album") { f -> File(f.context.filesDir, "images/old-link.png").also { Os.symlink(f.original, it.absolutePath) }.absolutePath }
    }

    @Test fun legacyFilenamePermissionDoesNotExtendToDraftEvidence() = runBlocking { fixture(legacyNames = false) { f ->
        f.copyAndEditDrafts()
        val before = f.drafts.get(f.editDraftId)!!
        val oldName = f.image("old-named-evidence.png")
        f.drafts.save(before.copy(evidenceImagePaths = listOf(oldName)))
        assertExportRejected(f, "non-UUID draft evidence")
    } }

    @Test fun legacyFilenamePermissionDoesNotExtendToRecognitionRegions() = runBlocking { fixture(legacyNames = false) { f ->
        f.copyAndEditDrafts()
        val oldName = f.image("old-named-recognition-region.png")
        val request = RecognitionRequest(draftId = f.editDraftId, targetItemId = 1,
            regions = listOf(RecognitionRegion("selected-region", imageSha256(oldName), 24, 32)), baseline = emptyMap(), family = WardrobeFamily.CLOTHING)
        f.db.recognitionDao().insert(RecognitionTask(UUID.randomUUID().toString(), f.editDraftId, 1, request.encode(), listOf(oldName)))
        assertExportRejected(f, "non-UUID recognition region with matching hash/dimensions")
    } }

    @Test fun legacyFilenamePermissionDoesNotExtendToFrozenTemplates() = runBlocking { fixture(legacyNames = false) { f ->
        val task = f.submit(originalOnly = true)
        val oldName = File(f.context.filesDir, "images/old-named-template.png").apply { writeBytes(File(task.sourceImagePaths.first()).readBytes()) }.absolutePath
        f.db.imageTaskDao().update(task.copy(sourceImagePaths = listOf(oldName, task.sourceImagePaths.last())))
        assertExportRejected(f, "non-UUID frozen mannequin template with matching hash")
    } }

    @Test fun legacyFilenamePermissionDoesNotExtendToUnreferencedDerivedInputs() = runBlocking { fixture(legacyNames = false) { f ->
        val task = f.submit(originalOnly = false)
        val oldName = File(f.context.filesDir, "images/old-named-crop.png").apply { writeBytes(File(task.sourceImagePaths.last()).readBytes()) }.absolutePath
        f.db.imageTaskDao().update(task.copy(sourceImagePaths = task.sourceImagePaths.dropLast(1) + oldName))
        assertExportRejected(f, "non-UUID derived input outside the Item album with matching hash")
    } }

    private suspend fun assertExportRejected(f: Fixture, label: String) {
        val before = f.state()
        val json = f.manager.exportToJson(); json.getOrNull()?.let(f.exports::add)
        val zip = f.manager.exportToZip(); zip.getOrNull()?.let(f.exports::add)
        assertTrue("$label JSON must retain strict UUID ownership; ZIP failure=${zip.exceptionOrNull()}", json.isFailure)
        assertTrue("$label ZIP must retain strict UUID ownership", zip.isFailure)
        assertEquals("$label rejected export must preserve database", before, f.state())
        assertEquals("$label rejected export must not enter restore barrier", 0, f.pauses)
    }

    private fun zipEntries(f: Fixture, uri: Uri): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(f.context.contentResolver.openInputStream(uri)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = input.readBytes()
            }
        }
    }
}
