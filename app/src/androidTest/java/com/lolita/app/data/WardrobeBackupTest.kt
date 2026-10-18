package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
import com.lolita.app.data.repository.RecognitionRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Actual Room, public backup entry points and real image bytes. No transport is started. */
@RunWith(AndroidJUnit4::class)
class WardrobeBackupTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val gson = GsonBuilder().serializeNulls().create()
    private val newTables = listOf("storageNodes", "itemDrafts", "recognitionTasks", "itemEvidence", "imageProvenance")

    private inner class Fixture {
        val root = File(target.cacheDir, "wardrobe-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val exports = mutableListOf<Uri>()
        var paused = 0
        val manager = BackupManager(context, db, withImageTasksPaused = { action -> paused++; action() })
        val draftId = UUID.randomUUID().toString()
        val recognitionId = UUID.randomUUID().toString()
        val evidenceId = UUID.randomUUID().toString()
        lateinit var original: String
        lateinit var second: String
        lateinit var region: String
        lateinit var taskRegion: String
        lateinit var cover: String
        lateinit var draftImage: String
        lateinit var chart: String
        lateinit var item: Item
        lateinit var price: Price
        lateinit var payment: Payment
        lateinit var stage: PaymentStage
        var generationId: String? = null
        var imageNumber = 0
        var serviceStarts = 0
        val coordinator by lazy { ImageTaskCoordinator(db, scope, { serviceStarts++ },
            { ImageFileHelper.saveGeneratedImage(context, it) }, ImageFileHelper::deleteImage,
            { ImageFileHelper.isOwnedReadableImage(context, it) }) }

        suspend fun image(): String {
            imageNumber++
            val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(imageNumber * 21 % 255, imageNumber * 43 % 255, imageNumber * 61 % 255))
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray()
            }
            bitmap.recycle()
            return ImageFileHelper.saveGeneratedImage(context, bytes)
        }

        fun candidate(field: RecognitionField = RecognitionField.WIG_LENGTH, source: RecognitionSource = RecognitionSource.TEXT) =
            RecognitionCandidate("length", field, "60 cm", source, listOf("selected-region"), "商品实测发长60 cm",
                measurement = if (field == RecognitionField.WIG_LENGTH) RecognitionMeasurement(
                    RecognitionMeasurementTarget.HAIR_LENGTH, RecognitionMeasurementBasis.PRODUCT_MEASURED,
                    RecognitionMeasurementUnit.CM) else null)

        suspend fun init() {
            ImageFileHelper.configure(context, db)
            original = image(); second = image(); region = image(); taskRegion = image()
            cover = image(); draftImage = image(); chart = image()
            db.brandDao().insertBrand(Brand(id = 1, name = "原品牌"))
            db.categoryDao().insertCategory(Category(id = 1, name = "全头假发", categorySemantic = CategorySemantic.FULL_WIG))
            db.locationDao().insertLocation(Location(id = 1, name = "衣帽间", description = "保留旧房间", createdAt = 10, updatedAt = 11))
            db.locationDao().insertLocation(Location(id = 2, name = "书房", createdAt = 12, updatedAt = 13))
            // Child sorts before parent. Restore must use topology, not exported list order.
            db.storageNodeDao().insert(StorageNode(id = 20, locationId = 1, name = "假发柜", imageUrl = cover, sortOrder = 9, createdAt = 20, updatedAt = 21))
            db.storageNodeDao().insert(StorageNode(id = 10, locationId = 1, parentNodeId = 20, name = "第一层", sortOrder = -1, createdAt = 22, updatedAt = 23))
            item = Item(id = 1, brandId = 1, categoryId = 1, name = "旧假发", description = "旧备注与单位原样保留", status = ItemStatus.OWNED,
                imageUrls = listOf(original, second, original), size = "头围 52～58 cm", colors = listOf("奶茶色", "挑染蓝"),
                locationId = 1, storageNodeId = 10, composition = ItemComposition.SINGLE,
                sizeChartImageUrl = chart, sizeChartImageUrls = listOf(chart, second), createdAt = 100, updatedAt = 101)
            db.itemDao().insertItem(item)
            db.itemDao().insertItem(item.copy(id = 2, name = "随附发网", parentItemId = 1, includedInParentPrice = true, imageUrls = emptyList(), importToken = "历史导入小物"))
            price = Price(id = 5, itemId = 1, type = PriceType.DEPOSIT_BALANCE, totalPrice = 142.37, shipping = 12.34,
                deposit = 42.0, balance = 88.03, createdAt = 200, purchaseRecordStatus = PurchaseRecordStatus.HISTORICAL_OWNED)
            db.priceDao().insertPrice(price)
            db.orderDao().insertBinding(OrderBinding(1, 5, PurchaseRecordStatus.HISTORICAL_OWNED))
            stage = PaymentStage(id = 6, priceId = 5, name = "历史尾款阶段", amount = 88.03, dueDate = 1_700_000_000_001, customReminderDays = 5)
            db.orderDao().insertStage(stage)
            payment = Payment(id = 7, priceId = 5, stageId = 6, amount = 51.23, dueDate = 1_700_000_000_001,
                paidDate = 1_700_000_000_002, isPaid = true, createdAt = 203, customReminderDays = 3)
            db.paymentDao().insertPayment(payment)
            db.itemDraftDao().save(ItemDraft(id = draftId, targetItemId = 1,
                dataJson = ItemDraftData(name = "尚未保存的改名", size = "EU 37 / 原单位", brandId = 1, categoryId = 1, locationId = 1, storageNodeId = 10).encode(),
                imagePaths = listOf(draftImage, original), sizeChartImagePaths = listOf(chart), evidenceImagePaths = listOf(region),
                appearanceOnlyImagePaths = listOf(draftImage), updatedAt = 301))
            val request = RecognitionRequest(draftId = draftId, targetItemId = 1,
                regions = listOf(RecognitionRegion("task-region", imageSha256(taskRegion), 24, 32)), baseline = mapOf(RecognitionField.NAME to "旧假发"), family = WardrobeFamily.WIG)
            db.recognitionDao().insert(RecognitionTask(recognitionId, draftId, 1, request.encode(), listOf(taskRegion),
                status = RecognitionStatus.RUNNING, requestSent = true, createdAt = 302, updatedAt = 303))
            db.recognitionDao().insertEvidence(ItemEvidence(evidenceId, 1, draftId, RecognitionField.WIG_LENGTH,
                gson.toJson(candidate()), listOf(region), createdAt = 304))
            db.imageProvenanceDao().upsert(ImageProvenance(original, 1, ImageSourceType.ORIGINAL, "原始照片", createdAt = 305))
        }

        suspend fun completedV4Task() {
            coordinator.awaitStartup()
            val catalog = WigHeadTemplateCatalog(context)
            val head = catalog.snapshot(catalog.templates.first().id)
            val id = coordinator.submitMannequin(MannequinRequest(1, region, subject = mannequinSubject(db.categoryDao().getCategoryById(1)),
                wigHeadTemplate = head, referenceViewConfirmed = true, originalImagePath = original), ImageGenerationProvider.SEEDREAM)
            val result = image()
            val task = db.imageTaskDao().get(id)!!
            db.imageTaskDao().update(task.copy(status = ImageTaskStatus.COMPLETED, requestSent = true, resultImagePath = result))
            db.itemDao().updateItem(db.itemDao().getItemById(1)!!.copy(imageUrls = item.imageUrls + result))
            db.imageProvenanceDao().upsert(ImageProvenance(result, 1, ImageSourceType.AI_GENERATED, "WIG_HEAD", id, 400))
            generationId = id
        }

        suspend fun export(zip: Boolean): Uri = (if (zip) manager.exportToZip() else manager.exportToJson()).getOrThrow().also(exports::add)
        fun read(uri: Uri): JsonObject = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fun jsonUri(json: JsonObject): Uri = Uri.fromFile(File(context.cacheDir, "${UUID.randomUUID()}.json").apply { writeText(json.toString()) })
        suspend fun state(): String = gson.toJson(listOf(db.brandDao().getAllBrandsList(), db.categoryDao().getAllCategoriesList(), db.locationDao().getAllLocationsList(),
            db.storageNodeDao().getAll(), db.itemDao().getAllItemsList(), db.priceDao().getAllPricesList(), db.paymentDao().getAllPaymentsList(),
            db.orderDao().getAllBindings(), db.orderDao().getAllStages(), db.itemDraftDao().getAll(), db.recognitionDao().getAll(),
            db.recognitionDao().getEvidence(), db.imageProvenanceDao().getAll()))
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            exports.forEach { context.contentResolver.delete(it, null, null) }
            db.close(); root.deleteRecursively()
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.init(); withTimeout(90_000) { block(f) } } finally { f.close() }
    }

    @Test fun jsonRoundTripPreservesNewGraphsOrderFieldsAndActualCompletedV4Inputs() = runBlocking { roundTrip(false) }
    @Test fun zipRoundTripMapsSharedRegionsAndActualTemplatesOnceAndPreservesTheirSha() = runBlocking { roundTrip(true) }

    private suspend fun roundTrip(zip: Boolean) = fixture { f ->
        f.completedV4Task()
        val beforeItem = f.db.itemDao().getItemById(1)!!
        val beforeDraft = f.db.itemDraftDao().get(f.draftId)!!
        val beforeTask = f.db.imageTaskDao().get(f.generationId!!)!!
        val beforeRequest = ImageTaskRequest.decode(beforeTask.requestJson)
        assertEquals(4, beforeRequest.version); assertTrue(beforeTask.keepSourceImages)
        beforeRequest.validateInputImages(beforeTask.sourceImagePaths)
        val expectedAlbumHashes = beforeItem.imageUrls.map(::imageSha256)
        val uri = f.export(zip)
        if (zip) {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(f.context.contentResolver.openInputStream(uri)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory) entries[entry.name] = input.readBytes()
                }
            }
            val json = JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject
            newTables.forEach { assertTrue("ZIP must contain $it", json.getAsJsonArray(it).size() > 0) }
            val archiveTask = json.getAsJsonArray("imageTasks").single().asJsonObject
            assertTrue(archiveTask["keepSourceImages"].asBoolean)
            assertTrue(archiveTask.getAsJsonArray("sourceImagePaths").all { it.asString.startsWith("zip://images/") })
            val archiveRequest = JsonParser.parseString(archiveTask["requestJson"].asString).asJsonObject
            assertEquals(archiveTask.getAsJsonArray("sourceImagePaths")[0], archiveRequest.getAsJsonObject("wigHeadTemplate")["imagePath"])
            assertTrue(entries.keys.any { it.startsWith("images/") })
            assertTrue(entries.containsKey("image_manifest.json"))
        }
        val preview = f.manager.previewBackup(uri).getOrThrow()
        assertEquals(2, preview.itemCount)
        assertEquals(2, preview.locationCount)
        assertEquals(1, preview.imageTaskCount)
        val summary = f.manager.importBackup(uri).getOrThrow()
        assertEquals(preview.totalCount, summary.totalImported)
        val restored = f.db.itemDao().getItemById(1)!!
        assertEquals(beforeItem.copy(imageUrls = restored.imageUrls, sizeChartImageUrl = restored.sizeChartImageUrl,
            sizeChartImageUrls = restored.sizeChartImageUrls), restored)
        assertEquals(expectedAlbumHashes, restored.imageUrls.map(::imageSha256))
        assertEquals(restored.imageUrls.first(), restored.imageUrls[2])
        assertEquals(f.price, f.db.priceDao().getPriceById(5))
        assertEquals(f.payment, f.db.paymentDao().getPaymentById(7))
        assertEquals(f.stage, f.db.orderDao().getStage(6))
        assertEquals(OrderBinding(1, 5, PurchaseRecordStatus.HISTORICAL_OWNED), f.db.orderDao().getBinding(1))
        assertEquals(1L, f.db.itemDao().getItemById(2)!!.parentItemId)
        assertEquals("衣帽间 → 假发柜 → 第一层", StorageTree.pathFor(1, 10, f.db.locationDao().getAllLocationsList(), f.db.storageNodeDao().getAll()))
        val draft = f.db.itemDraftDao().get(f.draftId)!!
        assertEquals(beforeDraft.copy(imagePaths = draft.imagePaths, sizeChartImagePaths = draft.sizeChartImagePaths,
            evidenceImagePaths = draft.evidenceImagePaths, appearanceOnlyImagePaths = draft.appearanceOnlyImagePaths), draft)
        assertEquals(imageSha256(f.draftImage), imageSha256(draft.imagePaths.first()))
        assertEquals(listOf(draft.imagePaths.first()), draft.appearanceOnlyImagePaths)
        val evidence = f.db.recognitionDao().getEvidence().single()
        assertEquals(imageSha256(f.region), imageSha256(evidence.regionImagePaths.single()))
        assertEquals(evidence.regionImagePaths, draft.evidenceImagePaths)
        assertFalse(evidence.regionImagePaths.single() in restored.imageUrls)
        val recognition = f.db.recognitionDao().get(f.recognitionId)!!
        assertEquals(RecognitionStatus.INTERRUPTED, recognition.status)
        assertTrue(recognition.requestSent)
        assertEquals(imageSha256(f.taskRegion), imageSha256(recognition.regionImagePaths.single()))
        val task = f.db.imageTaskDao().get(f.generationId!!)!!
        val request = ImageTaskRequest.decode(task.requestJson)
        assertEquals(ImageTaskStatus.COMPLETED, task.status)
        assertTrue(task.keepSourceImages)
        assertFalse(task.sourceResourcesMissing)
        assertEquals(beforeRequest.inputSha256s, task.sourceImagePaths.map(::imageSha256))
        assertEquals(task.sourceImagePaths.first(), request.wigHeadTemplate!!.imagePath)
        assertEquals(restored.imageUrls.first(), request.productOriginalImagePath)
        request.wigHeadTemplate!!.validateImage(); request.validateInputImages(task.sourceImagePaths)
        assertEquals(beforeRequest.copy(wigHeadTemplate = request.wigHeadTemplate, productOriginalImagePath = request.productOriginalImagePath), request)
        assertEquals(evidence.regionImagePaths.single(), task.sourceImagePaths.last())
        assertEquals(zip, beforeTask.sourceImagePaths != task.sourceImagePaths)
        val provenance = f.db.imageProvenanceDao().getForItem(1).single { it.sourceType == ImageSourceType.AI_GENERATED }
        assertEquals(restored.imageUrls.last(), provenance.imagePath)
        f.coordinator.delete(task.id)
        assertNotNull(f.db.imageProvenanceDao().get(1, provenance.imagePath))
        ImageFileHelper.deleteImage(evidence.regionImagePaths.single())
        assertTrue(File(evidence.regionImagePaths.single()).isFile)
        assertTrue(File(provenance.imagePath).isFile)
    }

    @Test fun oldFormatNineWithMissingWardrobeFieldsDefaultsToUnknownAndKeepsOldOrderAndImageOrder() = runBlocking { fixture { f ->
        val legacy = ImageTask(UUID.randomUUID().toString(), ImageTaskKind.MANNEQUIN, ImageGenerationProvider.SEEDREAM, "历史展示图任务",
            ImageTaskRequest(listOf(ImageTaskGarment(1, "旧假发", TryOnRole.MAIN))).encode(), emptyList(),
            status = ImageTaskStatus.COMPLETED, requestSent = true, resultImagePath = f.original, createdAt = 410, updatedAt = 411)
        f.db.imageTaskDao().insert(legacy)
        val json = f.read(f.export(false)).apply {
            addProperty("formatVersion", 9); addProperty("schemaVersion", 29)
            newTables.forEach(::remove)
            getAsJsonArray("items").forEach { raw -> raw.asJsonObject.apply {
                remove("storageNodeId"); remove("specificationsJson"); remove("composition")
            } }
            getAsJsonArray("categories").forEach { it.asJsonObject.apply { remove("categorySemantic"); remove("defaultWearRole") } }
            getAsJsonArray("brands").forEach { it.asJsonObject.remove("systemKey") }
            getAsJsonArray("imageTasks").forEach { it.asJsonObject.apply {
                remove("keepSourceImages"); remove("sourceResourcesMissing")
            } }
        }
        val uri = f.jsonUri(json)
        f.manager.importBackup(uri).getOrThrow()
        assertTrue(f.db.storageNodeDao().getAll().isEmpty()); assertTrue(f.db.itemDraftDao().getAll().isEmpty())
        assertTrue(f.db.recognitionDao().getAll().isEmpty()); assertTrue(f.db.recognitionDao().getEvidence().isEmpty())
        assertTrue(f.db.imageProvenanceDao().getAll().isEmpty())
        val item = f.db.itemDao().getItemById(1)!!
        assertEquals(ItemComposition.UNCONFIRMED, item.composition); assertEquals("", item.specificationsJson); assertNull(item.storageNodeId)
        assertEquals(f.item.imageUrls, item.imageUrls); assertEquals(f.item.size, item.size); assertEquals(f.item.colors, item.colors)
        assertEquals(CategorySemantic.FULL_WIG, f.db.categoryDao().getCategoryById(1)!!.categorySemantic)
        assertEquals(legacy, f.db.imageTaskDao().get(legacy.id))
        assertEquals(f.price, f.db.priceDao().getPriceById(5)); assertEquals(f.payment, f.db.paymentDao().getPaymentById(7))
    } }

    private suspend fun rejectMutation(label: String, mutation: (Fixture, JsonObject) -> Unit) = fixture { f ->
        val json = f.read(f.export(false)); mutation(f, json)
        val before = f.state(); val hashes = File(f.context.filesDir, "images").listFiles()!!.associate { it.path to imageSha256(it.path) }
        val uri = f.jsonUri(json)
        val preview = f.manager.previewBackup(uri)
        val imported = f.manager.importBackup(uri)
        assertTrue("$label preview must reject; importRejected=${imported.isFailure}, pauseCount=${f.paused}", preview.isFailure)
        assertTrue("$label import must reject", imported.isFailure)
        assertEquals("$label must validate before pausing or writing", 0, f.paused)
        assertEquals(before, f.state()); hashes.forEach { (path, sha) -> assertEquals(sha, imageSha256(path)) }
    }

    @Test fun unknownSemanticIsRejectedBeforePausingAndNoRowsOrPhotosChange() = runBlocking {
        rejectMutation("unknown semantic") { _, j -> j.getAsJsonArray("categories")[0].asJsonObject.addProperty("categorySemantic", "FUTURE_TYPE") }
    }
    @Test fun cyclicNodesAreRejectedBeforePausingAndNoRowsOrPhotosChange() = runBlocking {
        rejectMutation("cycle") { _, j -> j.getAsJsonArray("storageNodes").first { it.asJsonObject["id"].asLong == 20L }.asJsonObject.apply {
            addProperty("parentNodeId", 10); addProperty("siblingScopeId", 10)
        } }
    }
    @Test fun crossRoomItemNodeIsRejectedBeforePausingAndNoRowsOrPhotosChange() = runBlocking {
        rejectMutation("cross room") { _, j -> j.getAsJsonArray("items")[0].asJsonObject.addProperty("locationId", 2) }
    }
    @Test fun invalidZipGraphIsRejectedBeforeActivatingAnyNewImageOrWritingRows() = runBlocking { fixture { f ->
        val original = f.export(true)
        val file = File(f.context.cacheDir, "wrong-room.zip")
        ZipInputStream(f.context.contentResolver.openInputStream(original)).use { input ->
            ZipOutputStream(file.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    var bytes = input.readBytes()
                    if (entry.name == "data.json") {
                        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                        json.getAsJsonArray("items")[0].asJsonObject.addProperty("locationId", 2)
                        bytes = json.toString().toByteArray()
                    }
                    output.putNextEntry(ZipEntry(entry.name)); output.write(bytes); output.closeEntry()
                }
            }
        }
        val before = f.state()
        val images = File(f.context.filesDir, "images").listFiles()!!.associate { it.path to imageSha256(it.path) }
        assertTrue(f.manager.previewBackup(Uri.fromFile(file)).isFailure)
        assertTrue(f.manager.importBackup(Uri.fromFile(file)).isFailure)
        assertEquals(0, f.paused); assertEquals(before, f.state())
        assertEquals(images, File(f.context.filesDir, "images").listFiles()!!.associate { it.path to imageSha256(it.path) })
    } }
    @Test fun orphanEvidenceIsRejectedBeforePausingAndNoRowsOrPhotosChange() = runBlocking {
        rejectMutation("orphan evidence") { _, j -> j.getAsJsonArray("itemEvidence")[0].asJsonObject.addProperty("itemId", 999) }
    }
    @Test fun visualBrandGuessCannotBeRestoredAsAcceptedEvidence() = runBlocking {
        rejectMutation("ineligible brand evidence") { f, j -> j.getAsJsonArray("itemEvidence")[0].asJsonObject.apply {
            addProperty("field", RecognitionField.BRAND.name)
            addProperty("candidateJson", gson.toJson(f.candidate(RecognitionField.BRAND, RecognitionSource.VISUAL).copy(value = "猜测品牌", quote = "")))
        } }
    }
    @Test fun missingDraftRegionCannotReplaceTheCurrentDatabase() = runBlocking {
        rejectMutation("missing draft region") { _, j -> j.getAsJsonArray("itemDrafts")[0].asJsonObject.add("evidenceImagePaths", JsonArray().apply { add("/missing/region.jpg") }) }
    }
    @Test fun recognitionRegionHashMismatchCannotReplaceTheCurrentDatabase() = runBlocking {
        rejectMutation("wrong region hash") { _, j ->
            val row = j.getAsJsonArray("recognitionTasks")[0].asJsonObject
            val request = JsonParser.parseString(row["requestJson"].asString).asJsonObject
            request.getAsJsonArray("regions")[0].asJsonObject.addProperty("sha256", "0".repeat(64))
            row.addProperty("requestJson", request.toString())
        }
    }
    @Test fun changedCompletedV4InputCannotBeExportedAsAnUnchangedFrozenRequest() = runBlocking { fixture { f ->
        f.completedV4Task()
        val task = f.db.imageTaskDao().get(f.generationId!!)!!
        val frozen = task.requestJson
        val replacement = f.image()
        File(replacement).copyTo(File(task.sourceImagePaths.last()), overwrite = true)
        val json = f.manager.exportToJson().also { it.getOrNull()?.let(f.exports::add) }
        val zip = f.manager.exportToZip().also { it.getOrNull()?.let(f.exports::add) }
        assertTrue("JSON export must reject altered immutable inputs; zipRejected=${zip.isFailure}", json.isFailure)
        assertTrue("ZIP manifest must not legitimize bytes that disagree with the frozen request", zip.isFailure)
        assertEquals(frozen, f.db.imageTaskDao().get(task.id)!!.requestJson)
        assertEquals(ImageTaskStatus.COMPLETED, f.db.imageTaskDao().get(task.id)!!.status)
    } }

    @Test fun jsonWithMissingCompletedV4HeadKeepsTheFrozenTaskAndMarksItsResourcesMissing() = runBlocking { fixture { f ->
        f.completedV4Task()
        val task = f.db.imageTaskDao().get(f.generationId!!)!!
        val request = ImageTaskRequest.decode(task.requestJson)
        val uri = f.export(false)
        val startsBeforeRestore = f.serviceStarts
        assertTrue(File(request.wigHeadTemplate!!.imagePath).delete())
        val preview = f.manager.previewBackup(uri).getOrThrow()
        assertTrue(preview.missingImageCount >= 1)
        val summary = f.manager.importBackup(uri).getOrThrow()
        assertTrue(summary.missingImageCount >= 1)
        val restored = f.db.imageTaskDao().get(task.id)!!
        assertTrue(restored.sourceResourcesMissing)
        assertTrue(restored.keepSourceImages)
        assertEquals(ImageTaskStatus.COMPLETED, restored.status)
        assertEquals(task.requestJson, restored.requestJson)
        assertEquals(task.sourceImagePaths, restored.sourceImagePaths)
        assertEquals(task.resultImagePath, restored.resultImagePath)
        assertEquals(request.wigHeadTemplate, ImageTaskRequest.decode(restored.requestJson).wigHeadTemplate)
        assertFalse(File(request.wigHeadTemplate!!.imagePath).exists())
        assertEquals(startsBeforeRestore, f.serviceStarts)
    } }

    @Test fun missingHistoricalInputCanBeReexportedWithoutBlockingAllLaterZipBackups() = runBlocking { fixture { f ->
        f.completedV4Task()
        val task = f.db.imageTaskDao().get(f.generationId!!)!!
        val request = ImageTaskRequest.decode(task.requestJson)
        val json = f.export(false)
        assertTrue(File(request.wigHeadTemplate!!.imagePath).delete())
        assertTrue("Unmarked missing frozen input must still fail closed", f.manager.exportToZip().isFailure)
        f.manager.importBackup(json).getOrThrow()
        assertTrue(f.db.imageTaskDao().get(task.id)!!.sourceResourcesMissing)
        val zipResult = f.manager.exportToZip()
        assertTrue("Explicitly blocked historical input must not prevent backing up all other data", zipResult.isSuccess)
        val zip = zipResult.getOrThrow().also(f.exports::add)
        assertTrue(f.manager.previewBackup(zip).getOrThrow().missingImageCount >= 1)
        f.manager.importBackup(zip).getOrThrow()
        val restored = f.db.imageTaskDao().get(task.id)!!
        assertTrue(restored.sourceResourcesMissing)
        assertEquals(request.inputSha256s, ImageTaskRequest.decode(restored.requestJson).inputSha256s)
        assertEquals(request.wigHeadTemplate, ImageTaskRequest.decode(restored.requestJson).wigHeadTemplate)
        assertEquals(2, f.db.itemDao().getAllItemsList().size)
        assertEquals(f.price, f.db.priceDao().getPriceById(5))
        val album = f.db.itemDao().getItemById(1)!!.imageUrls.first()
        assertTrue(File(album).delete())
        assertTrue("A blocked historical task cannot exempt a missing business image", f.manager.exportToZip().isFailure)
    } }

    @Test fun informationalCompletedResultDoesNotMakeABlockedMissingInputMandatoryAgain() = runBlocking { fixture { f ->
        f.completedV4Task()
        val task = f.db.imageTaskDao().get(f.generationId!!)!!
        val missing = ImageTaskRequest.decode(task.requestJson).wigHeadTemplate!!.imagePath
        val historical = ImageTask(UUID.randomUUID().toString(), ImageTaskKind.MANNEQUIN,
            ImageGenerationProvider.SEEDREAM, "旧任务的信息路径",
            ImageTaskRequest(listOf(ImageTaskGarment(1,"原物品",TryOnRole.MAIN))).encode(), emptyList(),
            status = ImageTaskStatus.COMPLETED, requestSent = true, resultImagePath = missing)
        f.db.imageTaskDao().insert(historical)
        val json = f.export(false)
        assertTrue(File(missing).delete())
        f.manager.importBackup(json).getOrThrow()
        assertTrue(f.db.imageTaskDao().get(task.id)!!.sourceResourcesMissing)
        assertEquals(missing,f.db.imageTaskDao().get(historical.id)!!.resultImagePath)
        val zipResult = f.manager.exportToZip()
        assertTrue("Informational completed output must not re-require an explicitly blocked missing input",zipResult.isSuccess)
        val zip = zipResult.getOrThrow().also(f.exports::add)
        assertTrue(f.manager.previewBackup(zip).getOrThrow().missingImageCount > 0)
        f.manager.importBackup(zip).getOrThrow()
        assertTrue(f.db.imageTaskDao().get(task.id)!!.sourceResourcesMissing)
        assertEquals(ImageTaskStatus.COMPLETED,f.db.imageTaskDao().get(historical.id)!!.status)
        assertEquals(f.price,f.db.priceDao().getPriceById(5))
    } }

    @Test fun jsonRestoresTheHeadPreferenceAsMetadataWithoutAddingItsImageToTheBackup() = runBlocking {
        preferenceRoundTrip(false)
    }

    @Test fun zipRestoresTheHeadPreferenceAndKeepsExistingBusinessImageIdentities() = runBlocking {
        preferenceRoundTrip(true)
    }

    private suspend fun preferenceRoundTrip(zip: Boolean) = fixture { f ->
        val catalog = WigHeadTemplateCatalog(f.context)
        val selected = catalog.snapshot(catalog.templates.first().id)
        val expected = selected.copy(imagePath = "preference-only")
        WigHeadTemplateCatalog.writePreference(f.context, selected)
        val duplicate = ImageFileHelper.saveGeneratedImage(f.context, File(f.original).readBytes())
        assertNotEquals(f.original, duplicate)
        assertEquals(imageSha256(f.original), imageSha256(duplicate))
        val room = f.db.locationDao().getLocationByIdSync(2)!!
        f.db.locationDao().updateLocation(room.copy(imageUrl = duplicate))
        val uri = f.export(zip)
        val entries = if (zip) zipEntries(f, uri) else emptyMap()
        val json = if (zip) JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject else f.read(uri)
        assertEquals(expected, WigHeadTemplateSnapshot.decode(json["wigHeadTemplatePreferenceJson"].asString))
        assertFalse(json.toString().contains(selected.imagePath))
        if (zip) {
            val albumPath = json.getAsJsonArray("items").first { it.asJsonObject["id"].asLong == 1L }
                .asJsonObject.getAsJsonArray("imageUrls")[0].asString
            val roomPath = json.getAsJsonArray("locations").first { it.asJsonObject["id"].asLong == 2L }
                .asJsonObject["imageUrl"].asString
            assertNotEquals(albumPath, roomPath)
            assertEquals(2, entries.filterKeys { it.startsWith("images/") }.values.count { bytesSha256(it) == imageSha256(f.original) })
            assertFalse(entries.filterKeys { it.startsWith("images/") }.values.any { bytesSha256(it) == selected.sha256 })
        }
        WigHeadTemplateCatalog.writePreference(f.context, null)
        assertNull(WigHeadTemplateCatalog.readPreference(f.context))
        f.manager.importBackup(uri).getOrThrow()
        assertEquals(expected, WigHeadTemplateCatalog.readPreference(f.context))
        val album = f.db.itemDao().getItemById(1)!!.imageUrls.first()
        val restoredRoomImage = f.db.locationDao().getLocationByIdSync(2)!!.imageUrl!!
        assertEquals(imageSha256(f.original), imageSha256(restoredRoomImage))
        if (zip) {
            assertNotEquals(album, restoredRoomImage)
            assertTrue(album !in setOf(f.original, duplicate))
            assertEquals(UUID.fromString(File(album).nameWithoutExtension).toString(), File(album).nameWithoutExtension)
        } else {
            assertEquals(f.original, album)
            assertEquals(duplicate, restoredRoomImage)
        }
    }

    @Test fun immutableV4HeadsShareBytesWhileAnExistingBusinessImageKeepsItsIdentity() = runBlocking { fixture { f ->
        f.completedV4Task()
        val first = f.db.imageTaskDao().get(f.generationId!!)!!
        val firstRequest = ImageTaskRequest.decode(first.requestJson)
        val firstHead = requireNotNull(firstRequest.wigHeadTemplate)
        val secondInputs = first.sourceImagePaths.map { ImageFileHelper.saveGeneratedImage(f.context, File(it).readBytes()) }
        assertTrue(first.sourceImagePaths.zip(secondInputs).all { (a,b) -> a != b && imageSha256(a) == imageSha256(b) })
        val secondRequest = firstRequest.copy(wigHeadTemplate = firstHead.copy(imagePath = secondInputs.first()))
        val second = first.copy(id = UUID.randomUUID().toString(), requestJson = secondRequest.encode(), sourceImagePaths = secondInputs)
        f.db.imageTaskDao().insert(second)
        val zip = f.export(true)
        val entries = zipEntries(f, zip)
        val json = JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject
        val tasks = json.getAsJsonArray("imageTasks").map { it.asJsonObject }.associateBy { it["id"].asString }
        val firstPaths = tasks.getValue(first.id).getAsJsonArray("sourceImagePaths")
        val secondPaths = tasks.getValue(second.id).getAsJsonArray("sourceImagePaths")
        assertEquals(firstPaths[0], secondPaths[0])
        assertNotEquals("The first task also uses an existing business image; its identity stays distinct", firstPaths[1], secondPaths[1])
        assertEquals(1, entries.filterKeys { it.startsWith("images/") }.values.count { bytesSha256(it) == firstHead.sha256 })
        f.manager.importBackup(zip).getOrThrow()
        val restoredFirst = f.db.imageTaskDao().get(first.id)!!
        val restoredSecond = f.db.imageTaskDao().get(second.id)!!
        assertEquals(restoredFirst.sourceImagePaths.first(), restoredSecond.sourceImagePaths.first())
        assertNotEquals(restoredFirst.sourceImagePaths.last(), restoredSecond.sourceImagePaths.last())
        assertEquals(firstRequest.inputSha256s, restoredFirst.sourceImagePaths.map(::imageSha256))
        assertEquals(firstRequest.inputSha256s, restoredSecond.sourceImagePaths.map(::imageSha256))
        ImageTaskRequest.decode(restoredFirst.requestJson).validateInputImages(restoredFirst.sourceImagePaths)
        ImageTaskRequest.decode(restoredSecond.requestJson).validateInputImages(restoredSecond.sourceImagePaths)
        assertFalse(restoredFirst.resultImagePath in restoredFirst.sourceImagePaths)
    } }

    @Test fun zipDedupKeepsBothRegionIdentitiesAndRemovingOneStillProtectsTheSharedImage() = runBlocking { fixture { f ->
        val duplicate = ImageFileHelper.saveGeneratedImage(f.context, File(f.taskRegion).readBytes())
        assertNotEquals(f.taskRegion, duplicate)
        val original = f.db.recognitionDao().get(f.recognitionId)!!
        val request = original.request()
        val second = request.regions.single().copy(id = "same-content-second-region", appearanceOnly = true)
        val twoRegions = request.copy(regions = request.regions + second)
        val sourcePaths = listOf(f.taskRegion, duplicate)
        f.db.recognitionDao().update(original.copy(requestJson = twoRegions.encode(), regionImagePaths = sourcePaths))
        val uri = f.export(true)
        val entries = zipEntries(f, uri)
        assertEquals(1, entries.filterKeys { it.startsWith("images/") }.values.count { bytesSha256(it) == imageSha256(f.taskRegion) })
        val json = JsonParser.parseString(entries.getValue("data.json").toString(Charsets.UTF_8)).asJsonObject
        val archivedPaths = json.getAsJsonArray("recognitionTasks").single().asJsonObject.getAsJsonArray("regionImagePaths")
        assertEquals(2, archivedPaths.size())
        assertEquals(archivedPaths[0], archivedPaths[1])
        f.manager.importBackup(uri).getOrThrow()
        val restored = f.db.recognitionDao().get(f.recognitionId)!!
        assertEquals(RecognitionStatus.INTERRUPTED, restored.status)
        assertEquals(twoRegions, restored.request())
        assertEquals(2, restored.regionImagePaths.size)
        assertEquals(1, restored.regionImagePaths.distinct().size)
        val shared = restored.regionImagePaths.distinct().single()
        assertTrue(shared !in sourcePaths)
        assertEquals(request.regions.single().sha256, imageSha256(shared))
        val repository = RecognitionRepository(f.db, f.context)
        val preparedId = repository.reprepare(restored.id)
        repository.discard(restored.id)
        repository.removeRegion(preparedId, 0)
        val remaining = repository.get(preparedId)!!
        assertEquals(listOf(second), remaining.request().regions)
        assertEquals(listOf(shared), remaining.regionImagePaths)
        assertTrue("The remaining region still owns the shared image", File(shared).isFile)
        repository.removeRegion(preparedId, 0)
        assertNull(repository.get(preparedId))
        assertFalse("The last reference releases the image", File(shared).exists())
    } }

    private fun bytesSha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun zipEntries(f: Fixture, uri: Uri): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(f.context.contentResolver.openInputStream(uri)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = input.readBytes()
            }
        }
    }
}
