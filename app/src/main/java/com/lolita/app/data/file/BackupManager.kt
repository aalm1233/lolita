package com.lolita.app.data.file

import android.content.Context
import android.os.Build
import com.lolita.app.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.ImageTaskKind
import com.lolita.app.domain.model.ImageTaskStatus
import com.lolita.app.domain.model.ImageTaskRequest
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val brands: List<Brand>,
    val categories: List<Category>,
    val coordinates: List<Coordinate>,
    val items: List<Item>,
    val prices: List<Price>,
    val payments: List<Payment>,
    val outfitLogs: List<OutfitLog>,
    val outfitItemCrossRefs: List<OutfitItemCrossRef>,
    val styles: List<Style> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val locations: List<Location> = emptyList(),
    val sources: List<Source> = emptyList(),
    val backupDate: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val formatVersion: Int = BackupFormat.VERSION,
    val schemaVersion: Int = BackupFormat.SCHEMA_VERSION,
    val legacyCatalogArchives: List<LegacyCatalogArchive> = emptyList(),
    val syncStates: List<SharedLibrarySyncState> = emptyList(),
    val remoteBrands: List<RemoteBrand> = emptyList(),
    val remoteCategories: List<RemoteCategory> = emptyList(),
    val remoteStyles: List<RemoteStyle> = emptyList(),
    val remoteSeasons: List<RemoteSeason> = emptyList(),
    val remoteSources: List<RemoteSource> = emptyList(),
    val remoteCoordinates: List<RemoteSharedCoordinate> = emptyList(),
    val remoteItems: List<RemoteSharedItem> = emptyList(),
    val remotePricePlans: List<RemoteSharedPricePlan> = emptyList(),
    val orderBindings: List<OrderBinding> = emptyList(),
    val paymentStages: List<PaymentStage> = emptyList(),
    val monthlyBudgets: List<MonthlyBudget> = emptyList(),
    val tryOnResults: List<TryOnResult> = emptyList(),
    val imageTasks: List<ImageTask> = emptyList(),
    val personalModels: List<PersonalModel> = emptyList(),
    val personalModelDrafts: List<PersonalModelDraft> = emptyList(),
    val storageNodes: List<StorageNode> = emptyList(),
    val itemDrafts: List<ItemDraft> = emptyList(),
    val recognitionTasks: List<RecognitionTask> = emptyList(),
    val itemEvidence: List<ItemEvidence> = emptyList(),
    val imageProvenance: List<ImageProvenance> = emptyList(),
    val wigHeadTemplatePreferenceJson: String? = null
)

private data class PreparedBackup(
    val backupData: BackupData,
    val imageCount: Int,
    val isZip: Boolean,
    val stagingDir: File? = null,
    val missingImageCount: Int = 0
)

class BackupManager(
    private val context: Context,
    private val database: LolitaDatabase,
    private val withImageTasksPaused: suspend (suspend () -> Result<ImportSummary>) -> Result<ImportSummary> = { it() },
    private val awaitInitialization: suspend () -> Unit = {}
) {
    private val gson = Gson()
    private val operationMutex = sharedOperationMutex
    private var cachedBackupDigest: String? = null
    private suspend fun <T> serialized(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            // Await before taking the backup lock; initialization never calls back into backups.
            awaitInitialization()
            operationMutex.withLock { block() }
        }

    private suspend fun <T> serializedResult(block: suspend () -> Result<T>): Result<T> =
        try {
            serialized(block)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }

    private suspend fun snapshot(): BackupData = database.withTransaction {
        validateStoredValues()
        val sync = database.sharedLibrarySyncDao()
        BackupData(
            wigHeadTemplatePreferenceJson = com.lolita.app.data.mannequin.WigHeadTemplateCatalog.readPreference(context)?.let { gson.toJson(it) },
            storageNodes = database.storageNodeDao().getAll(),
            itemDrafts = database.itemDraftDao().getAll(),
            recognitionTasks = database.recognitionDao().getAll(),
            itemEvidence = database.recognitionDao().getEvidence(),
            imageProvenance = database.imageProvenanceDao().getAll(),
            personalModels = database.personalModelDao().getAll(),
            personalModelDrafts = database.personalModelDao().getDrafts(),
            imageTasks = database.imageTaskDao().getAll(),
            tryOnResults = database.tryOnDao().getAll(),
            monthlyBudgets = database.budgetDao().getAll(),
            brands = database.brandDao().getAllBrandsList(),
            categories = database.categoryDao().getAllCategoriesList(),
            coordinates = database.coordinateDao().getAllCoordinatesList(),
            items = database.itemDao().getAllItemsList(),
            prices = database.priceDao().getAllPricesList(),
            payments = database.paymentDao().getAllPaymentsList(),
            outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
            outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
            styles = database.styleDao().getAllStylesList(), seasons = database.seasonDao().getAllSeasonsList(),
            locations = database.locationDao().getAllLocationsList(), sources = database.sourceDao().getAllSourcesList(),
            legacyCatalogArchives = database.legacyCatalogArchiveDao().getAll(),
            syncStates = sync.getAllSyncStatesList(), remoteBrands = sync.getAllBrandsList(),
            remoteCategories = sync.getAllCategoriesList(), remoteStyles = sync.getAllStylesList(),
            remoteSeasons = sync.getAllSeasonsList(), remoteSources = sync.getAllSourcesList(),
            remoteCoordinates = sync.getAllCoordinatesList(), remoteItems = sync.getAllItemsList(),
            remotePricePlans = sync.getAllPricePlansList(),
            orderBindings = database.orderDao().getAllBindings(),
            paymentStages = database.orderDao().getAllStages()
        ).also { BackupFormat.validate(it); validateWardrobeImages(it,context) }
    }

    private suspend fun <T> withPinnedSnapshot(block: suspend (BackupData) -> T): T {
        var lease: ImageFileHelper.ImageReferenceLease? = null
        try {
            val data = database.withTransaction {
                snapshot().also { lease = ImageFileHelper.pinImageReferences(context, collectImagePaths(it)) }
            }
            return block(data)
        } finally { lease?.release() }
    }

    private suspend fun <T> withPinnedImages(data: BackupData, block: suspend () -> T): T {
        var lease: ImageFileHelper.ImageReferenceLease? = null
        try {
            database.withTransaction { lease = ImageFileHelper.pinImageReferences(context, collectImagePaths(data)) }
            return block()
        } finally { lease?.release() }
    }

    private fun validateStoredValues() {
        // DAO converters have display fallbacks. Backups must never persist those fallbacks as data.
        val db = database.openHelper.readableDatabase
        val enums = listOf(
            Triple("image_tasks", "kind", ImageTaskKind.entries.map { it.name }),
            Triple("image_tasks", "status", ImageTaskStatus.entries.map { it.name }),
            Triple("image_tasks", "provider", ImageGenerationProvider.entries.map { it.name }),
            Triple("personal_models", "provider", ImageGenerationProvider.entries.map { it.name }),
            Triple("items", "status", ItemStatus.entries.map { it.name }),
            Triple("items", "priority", ItemPriority.entries.map { it.name }),
            Triple("order_bindings", "purchase_record_status", PurchaseRecordStatus.entries.map { it.name }),
            Triple("prices", "type", PriceType.entries.map { it.name }),
            Triple("prices", "purchase_record_status", PurchaseRecordStatus.entries.map { it.name }),
            Triple("categories", "category_group", CategoryGroup.entries.map { it.name }),
            Triple("remote_categories", "category_group", CategoryGroup.entries.map { it.name }),
            Triple("remote_shared_price_plans", "price_type", PriceType.entries.map { it.name })
        )
        enums.forEach { (table, column, allowed) ->
            db.query("SELECT `$column` FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) require(!cursor.isNull(0) && cursor.getString(0) in allowed) { "$table.$column 包含未知值，无法无损备份" }
            }
        }
        val arrays = listOf("item_drafts" to "image_paths", "item_drafts" to "size_chart_image_paths", "item_drafts" to "evidence_image_paths", "item_drafts" to "appearance_only_image_paths", "recognition_tasks" to "region_image_paths", "item_evidence" to "region_image_paths", "personal_models" to "source_image_paths", "personal_model_drafts" to "source_image_paths", "image_tasks" to "source_image_paths", "items" to "image_urls", "items" to "colors", "coordinates" to "image_urls",
            "outfit_logs" to "image_urls", "remote_shared_coordinates" to "image_urls",
            "remote_shared_items" to "image_urls", "remote_shared_items" to "colors")
        arrays.forEach { (table, column) ->
            db.query("SELECT `$column` FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    require(!cursor.isNull(0)) { "$table.$column 为空" }
                    val array = JsonParser.parseString(cursor.getString(0))
                    require(array.isJsonArray && array.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString }) { "$table.$column 格式损坏，无法无损备份" }
                }
            }
        }
    }
    private val cacheLock = Any()
    @Volatile
    private var cachedPreparedBackup: PreparedBackup? = null
    @Volatile
    private var cachedBackupUri: Uri? = null

    suspend fun exportToJson(): Result<Uri> = serializedResult {
        try {
            val backupData = snapshot()
            val jsonString = gson.toJson(backupData)
            val fileName = "lolita_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val uri = createFileInDownloads(fileName, "application/json", jsonString.toByteArray())
            Result.success(uri)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun exportToCsv(): Result<Uri> = serializedResult {
        try {
            val backupData = snapshot()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val sb = StringBuilder()

            sb.appendLine("=== MONTHLY BUDGETS ===")
            sb.appendLine("month,amount_cents")
            backupData.monthlyBudgets.forEach { sb.appendLine("${escapeCsv(it.month)},${it.amountCents}") }

            // Brands
            sb.appendLine("=== BRANDS ===")
            sb.appendLine("id,name,is_preset,logo_url,created_at,system_key")
            backupData.brands.forEach { b ->
                sb.appendLine("${b.id},${escapeCsv(b.name)},${b.isPreset},${escapeCsv(b.logoUrl)},${b.createdAt},${escapeCsv(b.systemKey)}")
            }

            // Categories
            sb.appendLine("\n=== CATEGORIES ===")
            sb.appendLine("id,name,is_preset,created_at,category_group,category_semantic,default_wear_role")
            backupData.categories.forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${c.isPreset},${c.createdAt},${c.group},${c.categorySemantic},${c.defaultWearRole ?: ""}")
            }

            // Items
            sb.appendLine("\n=== ITEMS ===")
            sb.appendLine("id,name,description,brand_id,category_id,coordinate_id,status,priority,image_urls,colors,season,style,size,size_chart_image_url,created_at,updated_at,location_id,source,coordinate_order,parent_item_id,included_in_parent_price,size_chart_image_urls,storage_node_id,storage_path,specifications_json,composition")
            backupData.items.forEach { i ->
                sb.appendLine("${i.id},${escapeCsv(i.name)},${escapeCsv(i.description)},${i.brandId},${i.categoryId},${i.coordinateId},${i.status},${i.priority},${escapeCsv(i.imageUrls.joinToString(";"))},${escapeCsv(i.colors.joinToString(";"))},${escapeCsv(i.season)},${escapeCsv(i.style)},${escapeCsv(i.size)},${escapeCsv(i.sizeChartImageUrl)},${i.createdAt},${i.updatedAt},${i.locationId ?: ""},${escapeCsv(i.source)},${i.coordinateOrder},${i.parentItemId ?: ""},${i.includedInParentPrice},${escapeCsv(i.sizeChartImageUrls.joinToString(";"))},${i.storageNodeId ?: ""},${escapeCsv(com.lolita.app.domain.model.StorageTree.pathFor(i.locationId,i.storageNodeId,backupData.locations,backupData.storageNodes))},${escapeCsv(i.specificationsJson)},${i.composition}")
            }

            // Coordinates
            sb.appendLine("\n=== COORDINATES ===")
            sb.appendLine("id,name,description,image_urls,created_at,updated_at")
            backupData.coordinates.forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${escapeCsv(c.description)},${escapeCsv(c.imageUrls.joinToString(";"))},${c.createdAt},${c.updatedAt}")
            }

            // Styles
            sb.appendLine("\n=== STYLES ===")
            sb.appendLine("id,name,is_preset,created_at")
            backupData.styles.forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Seasons
            sb.appendLine("\n=== SEASONS ===")
            sb.appendLine("id,name,is_preset,created_at")
            backupData.seasons.forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Locations
            sb.appendLine("\n=== LOCATIONS ===")
            sb.appendLine("id,name,description,image_url,sort_order,created_at,updated_at")
            backupData.locations.forEach { l ->
                sb.appendLine("${l.id},${escapeCsv(l.name)},${escapeCsv(l.description)},${escapeCsv(l.imageUrl)},${l.sortOrder},${l.createdAt},${l.updatedAt}")
            }

            sb.appendLine("\n=== STORAGE NODES ===")
            sb.appendLine("id,location_id,parent_node_id,name,description,image_url,sort_order,path,created_at,updated_at")
            backupData.storageNodes.forEach { n -> sb.appendLine("${n.id},${n.locationId},${n.parentNodeId ?: ""},${escapeCsv(n.name)},${escapeCsv(n.description)},${escapeCsv(n.imageUrl)},${n.sortOrder},${escapeCsv(com.lolita.app.domain.model.StorageTree.pathFor(n.locationId,n.id,backupData.locations,backupData.storageNodes))},${n.createdAt},${n.updatedAt}") }
            sb.appendLine("\n=== IMAGE SOURCES ===")
            sb.appendLine("item_id,image_path,source_type,mode")
            backupData.imageProvenance.forEach { image -> sb.appendLine("${image.itemId},${escapeCsv(image.imagePath)},${image.sourceType},${escapeCsv(image.mode)}") }

            // Sources
            sb.appendLine("\n=== SOURCES ===")
            sb.appendLine("id,name,is_preset,created_at")
            backupData.sources.forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Prices
            sb.appendLine("\n=== PRICES ===")
            sb.appendLine("id,item_id,type,total_price,deposit,balance,created_at,shipping,purchase_record_status")
            backupData.prices.forEach { p ->
                sb.appendLine("${p.id},${p.itemId},${p.type},${p.totalPrice},${p.deposit},${p.balance},${p.createdAt},${p.shipping},${p.purchaseRecordStatus}")
            }

            // Payments
            sb.appendLine("\n=== PAYMENTS ===")
            sb.appendLine("id,price_id,amount,due_date,is_paid,paid_date,reminder_set,custom_reminder_days,calendar_event_id,created_at,stage_id")
            backupData.payments.forEach { p ->
                sb.appendLine("${p.id},${p.priceId},${p.amount},${p.dueDate},${p.isPaid},${p.paidDate},${p.reminderSet},${p.customReminderDays},${p.calendarEventId},${p.createdAt},${p.stageId ?: ""}")
            }

            sb.appendLine("\n=== ORDER_BINDINGS ===")
            sb.appendLine("item_id,price_id,purchase_record_status")
            backupData.orderBindings.forEach { sb.appendLine("${it.itemId},${it.priceId},${it.purchaseRecordStatus}") }
            sb.appendLine("\n=== PAYMENT_STAGES ===")
            sb.appendLine("id,price_id,name,amount,due_date,reminder_set,custom_reminder_days,calendar_event_id")
            backupData.paymentStages.forEach {
                sb.appendLine("${it.id},${it.priceId},${escapeCsv(it.name)},${it.amount},${it.dueDate ?: ""},${it.reminderSet},${it.customReminderDays ?: ""},${it.calendarEventId ?: ""}")
            }

            sb.appendLine("\n=== TRY_ON_RESULTS ===")
            sb.appendLine("id,name,image_url,item_ids,item_names,model,created_at,personal_model_id,personal_model_name,request_snapshot_json")
            backupData.tryOnResults.forEach {
                sb.appendLine("${it.id},${escapeCsv(it.name)},${escapeCsv(it.imageUrl)},${escapeCsv(gson.toJson(it.itemIds))},${escapeCsv(gson.toJson(it.itemNames))},${escapeCsv(it.model)},${it.createdAt},${it.personalModelId ?: ""},${escapeCsv(it.personalModelName)},${escapeCsv(it.requestSnapshotJson)}")
            }
            sb.appendLine("\n=== IMAGE_TASKS ===")
            sb.appendLine("id,kind,provider,title,request_json,source_image_paths,status,request_sent,created_at,updated_at,try_on_result_id,result_image_path,error,personal_model_id,keep_source_images,source_resources_missing")
            backupData.imageTasks.forEach {
                sb.appendLine("${escapeCsv(it.id)},${it.kind},${it.provider},${escapeCsv(it.title)},${escapeCsv(it.requestJson)},${escapeCsv(gson.toJson(it.sourceImagePaths))},${it.status},${it.requestSent},${it.createdAt},${it.updatedAt},${it.tryOnResultId ?: ""},${escapeCsv(it.resultImagePath)},${escapeCsv(it.error)},${it.personalModelId ?: ""},${it.keepSourceImages},${it.sourceResourcesMissing}")
            }

            sb.appendLine("\n=== PERSONAL_MODELS ===")
            sb.appendLine("id,name,request_json,source_image_paths,image_url,provider,model,created_at,updated_at")
            backupData.personalModels.forEach {
                sb.appendLine("${it.id},${escapeCsv(it.name)},${escapeCsv(it.requestJson)},${escapeCsv(gson.toJson(it.sourceImagePaths))},${escapeCsv(it.imageUrl)},${it.provider},${escapeCsv(it.model)},${it.createdAt},${it.updatedAt}")
            }
            sb.appendLine("\n=== PERSONAL_MODEL_DRAFTS ===")
            sb.appendLine("id,data_json,source_image_paths,updated_at")
            backupData.personalModelDrafts.forEach {
                sb.appendLine("${escapeCsv(it.id)},${escapeCsv(it.dataJson)},${escapeCsv(gson.toJson(it.sourceImagePaths))},${it.updatedAt}")
            }

            // OutfitLogs
            sb.appendLine("\n=== OUTFIT_LOGS ===")
            sb.appendLine("id,date,note,image_urls,created_at,updated_at")
            backupData.outfitLogs.forEach { o ->
                sb.appendLine("${o.id},${o.date},${escapeCsv(o.note)},${escapeCsv(o.imageUrls.joinToString(";"))},${o.createdAt},${o.updatedAt}")
            }

            // OutfitItemCrossRefs
            sb.appendLine("\n=== OUTFIT_ITEM_CROSS_REFS ===")
            sb.appendLine("outfit_log_id,item_id")
            backupData.outfitItemCrossRefs.forEach { r ->
                sb.appendLine("${r.outfitLogId},${r.itemId}")
            }

            sb.appendLine("\n=== LEGACY_CATALOG_ARCHIVES ===")
            sb.appendLine("source_table,record_key,payload")
            backupData.legacyCatalogArchives.forEach { sb.appendLine("${escapeCsv(it.sourceTable)},${escapeCsv(it.recordKey)},${escapeCsv(it.payload)}") }
            sb.appendLine("\n=== SHARED_LIBRARY_CACHE ===")
            sb.appendLine("entity,payload")
            listOf("syncStates" to backupData.syncStates, "remoteBrands" to backupData.remoteBrands,
                "remoteCategories" to backupData.remoteCategories, "remoteStyles" to backupData.remoteStyles,
                "remoteSeasons" to backupData.remoteSeasons, "remoteSources" to backupData.remoteSources,
                "remoteCoordinates" to backupData.remoteCoordinates, "remoteItems" to backupData.remoteItems,
                "remotePricePlans" to backupData.remotePricePlans).forEach { (name, rows) ->
                rows.forEach { sb.appendLine("${escapeCsv(name)},${escapeCsv(gson.toJson(it))}") }
            }

            val uri = createFileInDownloads("lolita_export_$timestamp.csv", "text/csv", sb.toString().toByteArray())
            Result.success(uri)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
    suspend fun exportToZip(): Result<Uri> = serializedResult {
        withPinnedSnapshot { backupData ->
        try {

            val absentHistoricalInputs = historicalInputPaths(backupData).filter { !File(it).isFile }.toSet()
            val (imageArchiveNamesByPath, missingImageCount) = buildImageArchiveNames(
                collectImagePaths(backupData) - absentHistoricalInputs, deduplicatedInputPaths(backupData))
            require(missingImageCount == 0) { "有 $missingImageCount 张本地图片缺失，无法创建完整备份" }
            val zipBackupData = rewriteImagePathsForZip(backupData, imageArchiveNamesByPath)
            val jsonBytes = gson.toJson(zipBackupData).toByteArray()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "lolita_backup_${timestamp}.zip"

            val outputFile = File.createTempFile("backup_export_", ".zip", context.cacheDir)
            try {
            outputFile.outputStream().use { os ->
                ZipOutputStream(os).use { zos ->
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(jsonBytes)
                    zos.closeEntry()

                    val manifest = JsonObject()
                    val writtenNames=mutableSetOf<String>()
                    imageArchiveNamesByPath.forEach { (path, archiveName) ->
                        if(!writtenNames.add(archiveName)) return@forEach
                        val file = File(path)
                        require(file.isFile) { "备份期间图片丢失：$path" }
                        run {
                            val entryName = "images/$archiveName"
                            zos.putNextEntry(ZipEntry(entryName))
                            val digest = MessageDigest.getInstance("SHA-256")
                            java.security.DigestInputStream(file.inputStream(), digest).use { it.copyTo(zos) }
                            manifest.addProperty(archiveName, digest.digest().joinToString("") { "%02x".format(it) })
                            zos.closeEntry()
                        }
                    }
                    zos.putNextEntry(ZipEntry("image_manifest.json"))
                    zos.write(manifest.toString().toByteArray())
                    zos.closeEntry()
                }
            }
            Result.success(publishFile(fileName, "application/zip", outputFile))
            } finally { outputFile.delete() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
        }
    }

    suspend fun importBackup(uri: Uri): Result<ImportSummary> = serializedResult {
        try {
            check(recoverPendingRemindersLocked() == 0) { "上次恢复的提醒尚未完成，请授予权限后重试" }
            val prepared = prepareBackup(uri, cacheResult = true)
            BackupFormat.validate(prepared.backupData)
            withPinnedImages(prepared.backupData) {
                validatePreparedTaskImages(prepared)
                // Keep JSON image references alive before task cancellation releases them.
                withImageTasksPaused { importPreparedBackup(prepared) }
            }
        } catch (e: Exception) {
            clearPreparedBackupCache()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun importPreparedBackup(prepared: PreparedBackup): Result<ImportSummary> {
        var images: ActivatedImages? = null
        var committed = false
        var preferenceRestoreFailures = 0
        return try {
            var previousPayments = emptyList<Payment>()
            var importedData = prepared.backupData
            // Finish this bounded commit even if the screen disappears; keep old files intact.
            withContext(NonCancellable) {
                if (prepared.isZip) {
                    images = activateStagedImages(prepared)
                    importedData = images!!.data
                }
                importedData = importedData.copy(imageTasks = importedData.imageTasks.map { task ->
                    if (!task.status.retainsSourceImages) task else task.copy(
                        status = ImageTaskStatus.INTERRUPTED, sourceImagePaths = if (task.kind == ImageTaskKind.PERSONAL_MODEL || task.keepSourceImages) task.sourceImagePaths else emptyList(),
                        error = if (task.sourceResourcesMissing) "原输入资源缺失或校验失败，原模板与请求快照已保留；请重新选择资料新建任务" else if (task.requestSent) "从备份恢复的任务已中断；原请求可能已计费，请核对后重新发起" else "从备份恢复的任务未自动执行，请核对后重新发起")
                })
                importedData = importedData.copy(recognitionTasks = importedData.recognitionTasks.map { task ->
                    if (task.status in setOf(com.lolita.app.domain.model.RecognitionStatus.PREPARED,com.lolita.app.domain.model.RecognitionStatus.RUNNING))
                        task.copy(status=com.lolita.app.domain.model.RecognitionStatus.INTERRUPTED,error="从备份恢复的识图任务已中断，请主动核对后重新提交") else task
                })
                validatePendingTaskImages(importedData)
                database.withTransaction {
                    previousPayments = database.paymentDao().getAllPaymentsList() +
                        database.orderDao().getAllStages().map { stageReminder(it) } +
                        (database.restoreJournalDao().get()?.previousPaymentsJson?.let {
                            gson.fromJson(it, Array<Payment>::class.java).toList()
                        } ?: emptyList())
                    clearAllTables()
                    importedData.personalModels.forEach { database.personalModelDao().insert(it) }
                    importedData.personalModelDrafts.forEach { database.personalModelDao().upsertDraft(it) }
                    importedData.imageTasks.forEach { database.imageTaskDao().insert(it) }
                    importedData.tryOnResults.forEach { database.tryOnDao().insert(it) }
                    importedData.monthlyBudgets.forEach { database.budgetDao().save(it) }
                    importedData.brands.forEach { database.brandDao().insertBrand(it) }
                    importedData.categories.forEach { database.categoryDao().insertCategory(it) }
                    importedData.styles.forEach { database.styleDao().insertStyle(it) }
                    importedData.seasons.forEach { database.seasonDao().insertSeason(it) }
                    importedData.locations.forEach { database.locationDao().insertLocation(it) }
                    importedData.storageNodes.sortedBy { it.parentNodeId != null }.forEach { database.storageNodeDao().insert(it) }
                    importedData.itemDrafts.forEach { database.itemDraftDao().save(it) }
                    importedData.recognitionTasks.forEach { database.recognitionDao().insert(it) }
                    importedData.sources.forEach { database.sourceDao().insertSource(it) }
                    importedData.coordinates.forEach { database.coordinateDao().insertCoordinate(it) }
                    importedData.items.sortedBy { it.parentItemId != null }.forEach { database.itemDao().insertItem(it) }
                    importedData.itemEvidence.forEach { database.recognitionDao().insertEvidence(it) }
                    importedData.imageProvenance.forEach { database.imageProvenanceDao().upsert(it) }
                    importedData.prices.forEach { database.priceDao().insertPrice(it) }
                    importedData.orderBindings.forEach { database.orderDao().insertBinding(it) }
                    importedData.paymentStages.forEach { database.orderDao().insertStage(it.copy(calendarEventId = null)) }
                    importedData.payments.forEach { database.paymentDao().insertPayment(it.copy(calendarEventId = null)) }
                    importedData.outfitLogs.forEach { database.outfitLogDao().insertOutfitLog(it) }
                    importedData.outfitItemCrossRefs.forEach { database.outfitLogDao().insertOutfitItemCrossRef(it) }
                    database.legacyCatalogArchiveDao().insertAll(importedData.legacyCatalogArchives)
                    val sync = database.sharedLibrarySyncDao()
                    importedData.syncStates.forEach { sync.upsertSyncState(it) }
                    sync.upsertBrands(importedData.remoteBrands); sync.upsertCategories(importedData.remoteCategories)
                    sync.upsertStyles(importedData.remoteStyles); sync.upsertSeasons(importedData.remoteSeasons)
                    sync.upsertSources(importedData.remoteSources); sync.upsertCoordinates(importedData.remoteCoordinates)
                    sync.upsertSharedItems(importedData.remoteItems); sync.upsertPricePlans(importedData.remotePricePlans)
                    verifyRestoredData(importedData)
                    // Ownership is derived from the active order; ambiguous legacy orders stay untouched.
                    database.orderDao().refreshStatuses()
                    database.restoreJournalDao().save(RestoreJournal(previousPaymentsJson = gson.toJson(com.lolita.app.domain.usecase.ReminderCleanup.deduplicate(previousPayments))))
                }
                committed = true
                if(runCatching { com.lolita.app.data.mannequin.WigHeadTemplateCatalog.writePreference(context,
                    importedData.wigHeadTemplatePreferenceJson?.let(com.lolita.app.domain.model.WigHeadTemplateSnapshot::decode)) }.isFailure) preferenceRestoreFailures=1
                // Imported active tasks become interrupted. Remove only their newly
                // activated, now-unreferenced sources; all pre-import files stay intact.
                images?.let { activated ->
                    database.withTransaction {
                        val referenced = collectImagePaths(snapshot())
                        activated.files.filter { it.absolutePath !in referenced }.forEach { it.delete() }
                    }
                }
            }
            val calendarFailures = withContext(NonCancellable) { recoverPendingRemindersLocked() }
            clearPreparedBackupCache()
            Result.success(ImportSummary(
                totalImported = recordCount(importedData), totalSkipped = 0,
                totalErrors = calendarFailures + preferenceRestoreFailures, imageCount = prepared.imageCount,
                missingImageCount = prepared.missingImageCount, calendarEventsFailed = calendarFailures,
                backupDate = importedData.backupDate, backupVersion = importedData.appVersion
            ))
        } catch (e: Exception) {
            if (!committed) images?.files?.forEach { it.delete() }
            clearPreparedBackupCache()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun validatePendingTaskImages(data: BackupData) {
        validateWardrobeImages(data,context)
        personalImagePaths(data).forEach { path ->
            require(ImageFileHelper.isOwnedReadableImage(context, path)) { "个人模特图片丢失、损坏或不属于本机图片目录，无法安全恢复" }
        }
        val businessPaths = collectImagePaths(data.copy(imageTasks = emptyList()))
        data.imageTasks.filter { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }.forEach { task ->
            val path = requireNotNull(task.resultImagePath)
            require(path !in businessPaths && ImageFileHelper.isOwnedGeneratedImage(context, path)) {
                "待确认人台图丢失、损坏或已被其他资料使用，无法安全恢复"
            }
        }
    }

    /** New private references are complete, verified files, rather than optional legacy album URLs. */
    private suspend fun validateWardrobeImages(data: BackupData, validationContext: Context, map: (String)->String = { it }) {
        suspend fun readable(path: String, productImage: Boolean = false) {
            val localPath = map(path)
            val valid = if (productImage) ImageFileHelper.isOwnedReadableProductImage(validationContext, localPath)
                else ImageFileHelper.isOwnedReadableImage(validationContext, localPath)
            require(valid) { "收纳、草稿或识图证据图片缺失、损坏或不属于本机图片目录" }
        }
        data.storageNodes.mapNotNull { it.imageUrl }.forEach { readable(it) }
        // Editors and registration copies reuse historical album/size-chart filenames.
        // Appearance-only references are validated as a subset of those same product images.
        data.itemDrafts.flatMap { it.imagePaths + it.sizeChartImagePaths }.distinct().forEach { readable(it, productImage = true) }
        data.itemDrafts.flatMap { it.evidenceImagePaths }.distinct().forEach { readable(it) }
        data.itemEvidence.flatMap { it.regionImagePaths }.distinct().forEach { readable(it) }
        data.recognitionTasks.forEach { task ->
            if(task.status!=com.lolita.app.domain.model.RecognitionStatus.DISCARDED) {
                val request=task.request()
                task.regionImagePaths.forEachIndexed { index,path ->
                    readable(path)
                    val file=File(map(path))
                    require(file.length() in 1..(3L*1024*1024) && sha256(file)==request.regions[index].sha256) { "识图区域内容与任务快照不一致" }
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
                    require(bounds.outWidth==request.regions[index].width && bounds.outHeight==request.regions[index].height) { "识图区域尺寸与任务快照不一致" }
                }
            }
        }
        data.imageTasks.filter { it.keepSourceImages && !it.sourceResourcesMissing }.forEach { task ->
            validateFrozenTaskImages(task, validationContext, map)
        }
    }

    /** Match admission's product compatibility without depending on today's mutable Item album. */
    private suspend fun validateFrozenTaskImages(task: ImageTask, validationContext: Context, map: (String) -> String = { it }) {
        val request = ImageTaskRequest.decode(task.requestJson)
        suspend fun readable(path: String, productImage: Boolean) {
            val localPath = map(path)
            val valid = if (productImage) ImageFileHelper.isOwnedReadableProductImage(validationContext, localPath)
                else ImageFileHelper.isOwnedReadableImage(validationContext, localPath)
            require(valid) { "展示任务输入图片缺失、损坏或不属于本机图片目录" }
        }
        task.sourceImagePaths.forEachIndexed { index, path ->
            // A different original identifies a derived crop: crops and templates stay UUID-only.
            val productImage = task.kind == ImageTaskKind.MANNEQUIN && index == task.sourceImagePaths.lastIndex &&
                (request.productOriginalImagePath == null || request.productOriginalImagePath == path)
            readable(path, productImage)
        }
        request.productOriginalImagePath?.let { readable(it, productImage = task.kind == ImageTaskKind.MANNEQUIN) }
        request.mapImagePaths(map).validateInputImages(task.sourceImagePaths.map(map))
    }

    private suspend fun validatePreparedTaskImages(prepared: PreparedBackup) {
        val data = prepared.backupData
        val businessPaths = collectImagePaths(data.copy(imageTasks = emptyList()))
        val validationContext = if (prepared.isZip) object : android.content.ContextWrapper(context) {
            override fun getFilesDir(): File = requireNotNull(prepared.stagingDir)
        } else context
        validateWardrobeImages(data,validationContext) { path -> if(prepared.isZip) {
            require(path.startsWith(ZIP_IMAGE_PREFIX)) { "新资料图片未纳入ZIP" };File(File(validationContext.filesDir,IMAGE_DIR),path.removePrefix(ZIP_IMAGE_PREFIX)).absolutePath
        } else path }
        personalImagePaths(data).forEach { path ->
            val localPath = if (prepared.isZip) {
                require(path.startsWith(ZIP_IMAGE_PREFIX)) { "ZIP 中的个人模特图片引用无效" }
                File(File(validationContext.filesDir, IMAGE_DIR), path.removePrefix(ZIP_IMAGE_PREFIX)).absolutePath
            } else path
            require(ImageFileHelper.isOwnedReadableImage(validationContext, localPath)) { "个人模特图片丢失、损坏或不属于本机图片目录，无法安全恢复" }
        }
        data.imageTasks.filter { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }.forEach { task ->
            val path = requireNotNull(task.resultImagePath)
            val localPath = if (prepared.isZip) {
                require(path.startsWith(ZIP_IMAGE_PREFIX)) { "ZIP 中的待确认图片引用无效" }
                File(File(validationContext.filesDir, IMAGE_DIR), path.removePrefix(ZIP_IMAGE_PREFIX)).absolutePath
            } else path
            require(path !in businessPaths && ImageFileHelper.isOwnedGeneratedImage(validationContext, localPath)) {
                "待确认人台图丢失、损坏或已被其他资料使用，无法安全恢复"
            }
        }
    }

    private fun personalImagePaths(data: BackupData): Set<String> = buildSet {
        data.personalModels.forEach { addAll(it.protectedImagePaths()) }
        data.personalModelDrafts.forEach { addAll(it.sourceImagePaths) }
        data.imageTasks.filter { it.kind == ImageTaskKind.PERSONAL_MODEL }.forEach { addAll(it.protectedImagePaths()) }
        data.imageTasks.filter { it.kind == ImageTaskKind.TRY_ON && it.retainsSourceImages && !it.sourceResourcesMissing }.forEach {
            ImageTaskRequest.decode(it.requestJson).modelImagePath(it.sourceImagePaths)?.let(::add)
        }
    }

    private fun recordCount(data: BackupData): Int = listOf(
        data.storageNodes, data.itemDrafts, data.recognitionTasks, data.itemEvidence, data.imageProvenance,
        data.brands, data.categories, data.coordinates, data.items, data.prices, data.payments,
        data.outfitLogs, data.outfitItemCrossRefs, data.styles, data.seasons, data.locations, data.sources,
        data.legacyCatalogArchives, data.syncStates, data.remoteBrands, data.remoteCategories,
        data.remoteStyles, data.remoteSeasons, data.remoteSources, data.remoteCoordinates,
        data.remoteItems, data.remotePricePlans, data.orderBindings, data.paymentStages, data.monthlyBudgets, data.tryOnResults, data.imageTasks, data.personalModels, data.personalModelDrafts
    ).sumOf { it.size }

    private suspend fun verifyRestoredData(expected: BackupData) {
        val actual = snapshot()
        // Sort by stable keys; compare every business field, including image order and timestamps.
        fun <T> equal(a: List<T>, b: List<T>, key: (T) -> String) = a.sortedBy(key) == b.sortedBy(key)
        check(equal(actual.storageNodes, expected.storageNodes) { it.id.toString() } &&
            equal(actual.itemDrafts, expected.itemDrafts) { it.id } &&
            equal(actual.recognitionTasks, expected.recognitionTasks) { it.id } &&
            equal(actual.itemEvidence, expected.itemEvidence) { it.id } &&
            equal(actual.imageProvenance, expected.imageProvenance) { "${it.itemId}:${it.imagePath}" } &&
            equal(actual.imageTasks, expected.imageTasks) { it.id } &&
            equal(actual.personalModels, expected.personalModels) { it.id.toString() } &&
            equal(actual.personalModelDrafts, expected.personalModelDrafts) { it.id } &&
            equal(actual.tryOnResults, expected.tryOnResults) { it.id.toString() } &&
            equal(actual.monthlyBudgets, expected.monthlyBudgets) { it.month } &&
            equal(actual.brands, expected.brands) { it.id.toString() } &&
            equal(actual.categories, expected.categories) { it.id.toString() } &&
            equal(actual.coordinates, expected.coordinates) { it.id.toString() } &&
            equal(actual.items, expected.items) { it.id.toString() } &&
            equal(actual.prices, expected.prices) { it.id.toString() } &&
            equal(actual.orderBindings, expected.orderBindings) { it.itemId.toString() } &&
            equal(actual.paymentStages, expected.paymentStages.map { it.copy(calendarEventId = null) }) { it.id.toString() } &&
            equal(actual.payments, expected.payments.map { it.copy(calendarEventId = null) }) { it.id.toString() } &&
            equal(actual.outfitLogs, expected.outfitLogs) { it.id.toString() } &&
            equal(actual.outfitItemCrossRefs, expected.outfitItemCrossRefs) { "${it.outfitLogId}:${it.itemId}" } &&
            equal(actual.styles, expected.styles) { it.id.toString() } &&
            equal(actual.seasons, expected.seasons) { it.id.toString() } &&
            equal(actual.locations, expected.locations) { it.id.toString() } &&
            equal(actual.sources, expected.sources) { it.id.toString() } &&
            equal(actual.legacyCatalogArchives, expected.legacyCatalogArchives) { "${it.sourceTable}:${it.recordKey}" } &&
            equal(actual.syncStates, expected.syncStates) { it.cacheKey } &&
            equal(actual.remoteBrands, expected.remoteBrands) { it.publicId } &&
            equal(actual.remoteCategories, expected.remoteCategories) { it.publicId } &&
            equal(actual.remoteStyles, expected.remoteStyles) { it.publicId } &&
            equal(actual.remoteSeasons, expected.remoteSeasons) { it.publicId } &&
            equal(actual.remoteSources, expected.remoteSources) { it.publicId } &&
            equal(actual.remoteCoordinates, expected.remoteCoordinates) { it.publicId } &&
            equal(actual.remoteItems, expected.remoteItems) { it.publicId } &&
            equal(actual.remotePricePlans, expected.remotePricePlans) { it.publicId }) { "恢复数据与备份内容不一致，已回滚" }
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "恢复数据包含无效关联，已回滚" }
        }
    }

    suspend fun recoverPendingReminders(): Int = serialized { recoverPendingRemindersLocked() }

    private suspend fun recoverPendingRemindersLocked(): Int {
        val journal = database.restoreJournalDao().get() ?: return 0
        val previous: List<Payment> = gson.fromJson(journal.previousPaymentsJson, object : TypeToken<List<Payment>>() {}.type)
        val scope = journal.affectedPriceIdsJson?.let { gson.fromJson(it, Array<Long>::class.java).toSet() }
        val failures = reconcileReminders(com.lolita.app.domain.usecase.ReminderCleanup.deduplicate(previous), scope)
        if (failures == 0) database.withTransaction {
            if (database.restoreJournalDao().get() == journal) database.restoreJournalDao().clear()
        }
        return failures
    }

    private suspend fun reconcileReminders(previous: List<Payment>, affectedPriceIds: Set<Long>?): Int {
        val scheduler = PaymentReminderScheduler(context)
        var failures = 0
        val stagePointers = database.orderDao().getAllStages().associateBy { it.id }
        previous.forEach { payment ->
            if (runCatching { scheduler.cancelReminder(payment.id) }.isFailure) failures++
            payment.calendarEventId?.let { eventId ->
                if (!CalendarEventHelper.deleteEventChecked(context, eventId)) failures++
                else if (payment.id < 0) {
                    stagePointers[-payment.id]?.takeIf { it.calendarEventId == eventId }?.let {
                        database.orderDao().updateStageCalendarEventId(it.id, null)
                    }
                }
            }
        }
        val activePrices = database.orderDao().getAllBindings()
            .filter { it.purchaseRecordStatus == PurchaseRecordStatus.TRACKED &&
                (affectedPriceIds == null || it.priceId in affectedPriceIds) }.map { it.priceId }.toSet()
        val payments = database.paymentDao().getAllPaymentsList().filter { it.isPaid && it.priceId in activePrices }
        val prices = database.priceDao().getAllPricesList().associateBy { it.id }
        val items = database.itemDao().getAllItemsList().associateBy { it.id }
        val stages = database.orderDao().getAllStages().filter { it.priceId in activePrices }
        val paymentsByPrice = payments.groupBy { it.priceId }
        val remainingByStage = stages.groupBy { it.priceId }.flatMap { (priceId, orderStages) ->
            com.lolita.app.domain.usecase.StageAccounting.remainingByStage(
                orderStages, paymentsByPrice[priceId].orEmpty(), prices[priceId]?.totalPrice ?: 0.0
            ).entries.map { it.key to it.value }
        }.toMap()
        stages.forEach { stage ->
            val remaining = remainingByStage[stage.id] ?: 0.0
            if (stage.dueDate == null || !stage.reminderSet || remaining <= 0.0) return@forEach
            val payment = stageReminder(stage, remaining)
            val itemName = prices[stage.priceId]?.itemId?.let { items[it]?.name } ?: "服饰"
            val reminderName = "$itemName · ${stage.name}"
            try {
                scheduler.scheduleReminder(payment, reminderName)
                if (payment.dueDate > System.currentTimeMillis() && stage.calendarEventId == null) {
                    when (val event = CalendarEventHelper.insertEventChecked(context, "付款提醒 · $reminderName",
                        "付款金额: ¥${payment.amount}", payment.dueDate,
                        reminderMinutes = com.lolita.app.domain.usecase.PaymentReminderPolicy.calendarReminderMinutes(stage.customReminderDays))) {
                        is CalendarEventHelper.InsertResult.Created ->
                            database.orderDao().updateStageCalendarEventId(stage.id, event.eventId)
                        CalendarEventHelper.InsertResult.Unavailable ->
                            Log.i("BackupManager", "Calendar sync unavailable; installment alarm remains active for ${stage.id}")
                        CalendarEventHelper.InsertResult.Failed -> {
                            failures++
                            Log.w("BackupManager", "Calendar sync failed; retaining reminder journal for ${stage.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                failures++
                Log.e("BackupManager", "Failed to restore installment reminder ${stage.id}", e)
            }
        }
        return failures
    }

    /** Negative ids keep installment reminders separate from preserved legacy payment alarms. */
    private fun stageReminder(stage: PaymentStage, amount: Double = stage.amount) = Payment(
        id = -stage.id, priceId = stage.priceId, stageId = stage.id, amount = amount,
        dueDate = stage.dueDate ?: 0L, reminderSet = stage.reminderSet,
        customReminderDays = stage.customReminderDays, calendarEventId = stage.calendarEventId
    )

    suspend fun previewBackup(uri: Uri): Result<BackupPreview> = serializedResult {
        try {
            val preparedBackup = prepareBackup(uri, cacheResult = true)
            val backupData = preparedBackup.backupData
            val boundPriceIds = backupData.orderBindings.map { it.priceId }.toSet()
            val archivedOrders = backupData.prices.filter { it.id !in boundPriceIds }

            Result.success(BackupPreview(
                brandCount = backupData.brands.size,
                categoryCount = backupData.categories.size,
                coordinateCount = backupData.coordinates.size,
                itemCount = backupData.items.size,
                priceCount = backupData.prices.size,
                paymentCount = backupData.payments.size,
                orderBindingCount = backupData.orderBindings.size,
                historicalOwnedCount = backupData.orderBindings.count { it.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED },
                needsReviewCount = backupData.orderBindings.count { it.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW },
                archivedOrderCount = archivedOrders.size,
                archivedHistoricalOwnedCount = archivedOrders.count { it.purchaseRecordStatus == PurchaseRecordStatus.HISTORICAL_OWNED },
                archivedNeedsReviewCount = archivedOrders.count { it.purchaseRecordStatus == PurchaseRecordStatus.NEEDS_REVIEW },
                paymentStageCount = backupData.paymentStages.size,
                outfitLogCount = backupData.outfitLogs.size,
                styleCount = backupData.styles.size,
                seasonCount = backupData.seasons.size,
                locationCount = backupData.locations.size,
                sourceCount = backupData.sources.size,
                imageCount = preparedBackup.imageCount,
                missingImageCount = preparedBackup.missingImageCount,
                archiveCount = backupData.legacyCatalogArchives.size,
                monthlyBudgetCount = backupData.monthlyBudgets.size,
                tryOnResultCount = backupData.tryOnResults.size,
                imageTaskCount = backupData.imageTasks.size,
                personalModelCount = backupData.personalModels.size,
                personalModelDraftCount = backupData.personalModelDrafts.size,
                storageNodeCount = backupData.storageNodes.size,itemDraftCount = backupData.itemDrafts.size,
                recognitionTaskCount = backupData.recognitionTasks.size,itemEvidenceCount = backupData.itemEvidence.size,imageProvenanceCount = backupData.imageProvenance.size,
                additionalRecordCount = recordCount(backupData) - (backupData.brands.size + backupData.categories.size + backupData.coordinates.size + backupData.items.size + backupData.prices.size + backupData.payments.size + backupData.outfitLogs.size + backupData.styles.size + backupData.seasons.size + backupData.locations.size + backupData.sources.size + backupData.legacyCatalogArchives.size + backupData.orderBindings.size + backupData.paymentStages.size + backupData.monthlyBudgets.size + backupData.tryOnResults.size + backupData.imageTasks.size + backupData.personalModels.size + backupData.personalModelDrafts.size + backupData.storageNodes.size + backupData.itemDrafts.size + backupData.recognitionTasks.size + backupData.itemEvidence.size + backupData.imageProvenance.size),
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun clearAllTables() {
        database.recognitionDao().deleteAllEvidence()
        database.recognitionDao().deleteAll()
        database.itemDraftDao().deleteAll()
        database.imageProvenanceDao().deleteAll()
        database.imageTaskDao().deleteAll()
        database.personalModelDao().deleteAllDrafts()
        database.personalModelDao().deleteAll()
        database.sharedLibrarySyncDao().clearPricePlans()
        database.sharedLibrarySyncDao().clearSharedItems()
        database.sharedLibrarySyncDao().clearCoordinates()
        database.sharedLibrarySyncDao().clearSources()
        database.sharedLibrarySyncDao().clearSeasons()
        database.sharedLibrarySyncDao().clearStyles()
        database.sharedLibrarySyncDao().clearCategories()
        database.sharedLibrarySyncDao().clearBrands()
        database.sharedLibrarySyncDao().deleteSyncState()
        database.budgetDao().deleteAll()
        database.tryOnDao().deleteAll()
        database.legacyCatalogArchiveDao().deleteAll()
        database.outfitLogDao().deleteAllOutfitItemCrossRefs()
        database.outfitLogDao().deleteAllOutfitLogs()
        database.paymentDao().deleteAllPayments()
        database.orderDao().clearStages()
        database.orderDao().clearBindings()
        database.priceDao().deleteAllPrices()
        database.itemDao().deleteAllItems()
        database.storageNodeDao().deleteAll()
        database.locationDao().deleteAllLocations()
        database.coordinateDao().deleteAllCoordinates()
        database.brandDao().deleteAllBrands()
        database.categoryDao().deleteAllCategories()
        database.styleDao().deleteAllStyles()
        database.seasonDao().deleteAllSeasons()
        database.sourceDao().deleteAllSources()
    }

    private fun collectImagePaths(data: BackupData): Set<String> = buildSet {
        mapImagePaths(data) { path -> if (path.isNotBlank()) add(path); path }
    }

    private fun isRemoteImage(path: String) = path.startsWith("https://") || path.startsWith("http://")

    /** Only explicitly blocked task inputs can stay absent; albums and pending outputs remain required. */
    private fun historicalInputPaths(data: BackupData): Set<String> {
        val inputs = data.imageTasks.filter { it.sourceResourcesMissing }.flatMap { task ->
            task.sourceImagePaths + listOfNotNull(ImageTaskRequest.decode(task.requestJson).productOriginalImagePath)
        }.toSet()
        val business = collectImagePaths(data.copy(imageTasks = emptyList()))
        val requiredTaskPaths = data.imageTasks.filterNot { it.sourceResourcesMissing }.flatMap { it.protectedImagePaths() } +
            data.imageTasks.filter { it.status == ImageTaskStatus.AWAITING_CONFIRMATION }.mapNotNull { it.resultImagePath }
        return inputs - business - requiredTaskPaths.toSet()
    }

    /** Input resources may share bytes; existing business and pending-output identities may not. */
    private fun deduplicatedInputPaths(data: BackupData): Set<String> {
        val inputs = data.imageTasks.filter { it.keepSourceImages }.flatMap { it.sourceImagePaths } +
            data.recognitionTasks.flatMap { it.regionImagePaths } + data.itemEvidence.flatMap { it.regionImagePaths }
        val business = collectImagePaths(data.copy(imageTasks = emptyList(), recognitionTasks = emptyList(), itemEvidence = emptyList()))
        val exclusive = data.imageTasks.mapNotNull { it.resultImagePath } +
            data.imageTasks.filterNot { it.keepSourceImages }.flatMap { it.sourceImagePaths }
        return inputs.toSet() - business - exclusive.toSet()
    }

    private fun buildImageArchiveNames(imagePaths: Set<String>, deduplicated: Set<String>): Pair<Map<String, String>, Int> {
        val local = imagePaths.filterNot(::isRemoteImage)
        val existing = local.filter { path -> File(path).isFile }
        val missingCount = local.size - existing.size
        val namesByDigest=mutableMapOf<String,String>()
        return existing.associateWith { path ->
            if (path in deduplicated) namesByDigest.getOrPut(sha256(File(path))) { buildArchiveImageName(path) }
            else buildArchiveImageName(path)
        } to missingCount
    }

    private fun buildArchiveImageName(path: String): String {
        val extension = File(path).extension
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
        // Format 9 personal references retain the same strict UUID ownership checks in staging.
        val archiveId = UUID.nameUUIDFromBytes(path.toByteArray(Charsets.UTF_8)).toString()
        return if (extension.isNotBlank()) "$archiveId.$extension" else archiveId
    }

    private fun rewriteImagePathsForZip(data: BackupData, names: Map<String, String>): BackupData =
        mapImagePaths(data) { path -> names[path]?.let { "$ZIP_IMAGE_PREFIX$it" } ?: path }

    private fun mapImagePaths(data: BackupData, map: (String) -> String): BackupData {
        val businessPaths = mutableMapOf<String, String>()
        fun business(path: String): String = businessPaths.getOrPut(path) { map(path) }
        fun nullable(path: String?) = path?.let(::business)
        val mapped = data.copy(
            storageNodes = data.storageNodes.map { it.copy(imageUrl=nullable(it.imageUrl)) },
            itemDrafts = data.itemDrafts.map { it.copy(imagePaths=it.imagePaths.map(::business),sizeChartImagePaths=it.sizeChartImagePaths.map(::business),evidenceImagePaths=it.evidenceImagePaths.map(::business),appearanceOnlyImagePaths=it.appearanceOnlyImagePaths.map(::business)) },
            recognitionTasks = data.recognitionTasks.map { it.copy(regionImagePaths=it.regionImagePaths.map(::business)) },
            itemEvidence = data.itemEvidence.map { it.copy(regionImagePaths=it.regionImagePaths.map(::business)) },
            imageProvenance = data.imageProvenance.map { it.copy(imagePath=business(it.imagePath)) },
            personalModels = data.personalModels.map { it.copy(imageUrl = business(it.imageUrl), sourceImagePaths = it.sourceImagePaths.map(::business)) },
            personalModelDrafts = data.personalModelDrafts.map { it.copy(sourceImagePaths = it.sourceImagePaths.map(::business)) },
            tryOnResults = data.tryOnResults.map { it.copy(imageUrl = business(it.imageUrl)) },
            brands = data.brands.map { it.copy(logoUrl = nullable(it.logoUrl)) },
            items = data.items.map { it.copy(imageUrls = it.imageUrls.map(::business), sizeChartImageUrl = nullable(it.sizeChartImageUrl), sizeChartImageUrls = it.sizeChartImageUrls.map(::business)) },
            coordinates = data.coordinates.map { it.copy(imageUrls = it.imageUrls.map(::business)) },
            outfitLogs = data.outfitLogs.map { it.copy(imageUrls = it.imageUrls.map(::business)) },
            locations = data.locations.map { it.copy(imageUrl = nullable(it.imageUrl)) },
            remoteBrands = data.remoteBrands.map { it.copy(logoUrl = nullable(it.logoUrl)) },
            remoteCoordinates = data.remoteCoordinates.map { it.copy(imageUrls = it.imageUrls.map(::business)) },
            remoteItems = data.remoteItems.map { it.copy(imageUrls = it.imageUrls.map(::business), sizeChartImageUrl = nullable(it.sizeChartImageUrl)) },
            legacyCatalogArchives = data.legacyCatalogArchives.map { archive ->
                val row = JsonParser.parseString(archive.payload).asJsonObject
                row.get("image_urls")?.takeUnless { it.isJsonNull }?.let { value ->
                    val images = if (value.isJsonArray) value.asJsonArray else JsonParser.parseString(value.asString).asJsonArray
                    val mapped = JsonArray().apply { images.forEach { add(business(it.asString)) } }
                    // SQL column is encoded as TEXT in the lossless archive.
                    if (value.isJsonArray) row.add("image_urls", mapped) else row.addProperty("image_urls", mapped.toString())
                }
                archive.copy(payload = row.toString())
            }
        )
        return mapped.copy(imageTasks = data.imageTasks.map { task ->
            task.copy(
                requestJson = ImageTaskRequest.decode(task.requestJson).mapImagePaths(::business).encode(),
                sourceImagePaths = if (task.retainsSourceImages) task.sourceImagePaths.map(::business) else emptyList(),
                // Completed paths are informational. Missing/deleted business images do not
                // become mandatory ZIP entries or retain old-device absolute paths.
                resultImagePath = if (task.status == ImageTaskStatus.AWAITING_CONFIRMATION) task.resultImagePath?.let(map)
                    else task.resultImagePath?.let(businessPaths::get)
            )
        })
    }

    private fun isZipFile(uri: Uri): Boolean {
        val bytes = ByteArray(2)
        context.contentResolver.openInputStream(uri)?.use { it.read(bytes) } ?: return false
        return bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(8192)
            var count = input.read(bytes)
            while (count >= 0) {
                if (count > 0) digest.update(bytes, 0, count)
                count = input.read(bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun prepareBackup(uri: Uri, cacheResult: Boolean): PreparedBackup {
        val source = File.createTempFile("backup_source_", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { input.copyTo(it) }
            } ?: error("无法读取文件")
            val digest = sha256(source)
            if (cachedBackupUri == uri && cachedPreparedBackup != null) {
                require(cachedBackupDigest == digest) { "备份文件在预览后发生变化，请重新选择" }
                return cachedPreparedBackup!!.also { validatePreparedTaskImages(it) }
            }
            clearPreparedBackupCache()
            val sourceUri = Uri.fromFile(source)
            val prepared = if (isZipFile(sourceUri)) parseZipBackup(sourceUri)
            else {
                val data = markUnavailableTaskInputs(BackupFormat.parse(source.readText()))
                val missing = collectImagePaths(data).count { !isRemoteImage(it) && !File(it).isFile }
                PreparedBackup(data, 0, false, missingImageCount = missing)
            }
            try { validatePreparedTaskImages(prepared) }
            catch (failure: Throwable) { prepared.stagingDir?.deleteRecursively(); throw failure }
            if (cacheResult) {
                cachedPreparedBackup = prepared
                cachedBackupUri = uri
                cachedBackupDigest = digest
            }
            return prepared
        } finally {
            source.delete()
        }
    }

    /** Bare JSON can preserve historical requests whose old-device input files are unavailable. */
    private suspend fun markUnavailableTaskInputs(data: BackupData): BackupData = data.copy(
        imageTasks=data.imageTasks.map { task ->
            if(!task.keepSourceImages || task.sourceResourcesMissing) task else {
                val available=try {
                    validateFrozenTaskImages(task, context)
                    true
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { false }
                if(available) task else task.copy(sourceResourcesMissing=true,
                    error="原输入资源缺失或校验失败，原模板与请求快照已保留；请重新选择资料新建任务")
            }
        })

    private fun parseZipBackup(uri: Uri): PreparedBackup {
        val stagingDir = createStagingDir()
        val imagesDir = File(stagingDir, IMAGE_DIR).apply { check(mkdirs()) }
        var json: String? = null
        var manifest: JsonObject? = null
        val names = mutableSetOf<String>()
        val entries = mutableSetOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        require(entries.add(entry.name)) { "ZIP 包含重复条目：${entry.name}" }
                        when {
                            entry.name == "data.json" -> json = zip.bufferedReader().readText()
                            entry.name == "image_manifest.json" -> manifest = JsonParser.parseString(zip.bufferedReader().readText()).asJsonObject
                            entry.name.startsWith("images/") && !entry.isDirectory -> {
                                val name = entry.name.removePrefix("images/")
                                require(name.isNotBlank() && name != "." && name != ".." && name == File(name).name && !name.contains('\\')) { "ZIP 图片路径无效" }
                                require(names.add(name)) { "ZIP 包含重复图片" }
                                File(imagesDir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取文件")
            val data = BackupFormat.parse(json ?: error("ZIP 中未找到 data.json"))
            if (data.formatVersion >= 2) require(manifest != null) { "ZIP 缺少图片校验清单" }
            manifest?.let { hashes ->
                require(hashes.keySet() == names) { "图片清单与 ZIP 内容不一致" }
                names.forEach { require(hashes[it].asString == sha256(File(imagesDir, it))) { "图片校验失败：$it" } }
            }
            val rewritten = rewriteImagePathsFromZipEntries(data, names)
            val missingHistoricalInputs = historicalInputPaths(rewritten).count { !it.startsWith(ZIP_IMAGE_PREFIX) }
            return PreparedBackup(rewritten, names.size, true, stagingDir, missingImageCount = missingHistoricalInputs)
        } catch (e: Exception) {
            deleteRecursivelyQuietly(stagingDir)
            throw e
        }
    }

    private fun rewriteImagePathsFromZipEntries(data: BackupData, names: Set<String>): BackupData {
        val knownMissing=historicalInputPaths(data)
        return mapImagePaths(data) { path ->
            if (isRemoteImage(path) || path.isBlank()) path
            else {
                val name = if (path.startsWith(ZIP_IMAGE_PREFIX)) path.removePrefix(ZIP_IMAGE_PREFIX) else File(path).name
                if(name !in names && path in knownMissing && !path.startsWith(ZIP_IMAGE_PREFIX)) path
                else {
                    require(name in names && name == File(name).name) { "ZIP 缺少引用的图片：$path" }
                    "$ZIP_IMAGE_PREFIX$name"
                }
            }
        }
    }

    private data class ActivatedImages(val files: List<File>, val data: BackupData)

    /** Never replace/delete the live directory. A failed or killed import cannot damage old images. */
    private fun activateStagedImages(prepared: PreparedBackup): ActivatedImages {
        val staging = requireNotNull(prepared.stagingDir)
        val imageDir = File(context.filesDir, IMAGE_DIR).apply { check(exists() || mkdirs()) }
        val files = mutableListOf<File>()
        val paths = mutableMapOf<String, String>()
        try {
            val data = mapImagePaths(prepared.backupData) { path ->
                if (!path.startsWith(ZIP_IMAGE_PREFIX)) path
                else paths.getOrPut(path) {
                    val name = path.removePrefix(ZIP_IMAGE_PREFIX)
                    val source = File(File(staging, IMAGE_DIR), name)
                    val target = File(imageDir, "${UUID.randomUUID()}.${source.extension.ifBlank { "img" }}")
                    files.add(target)
                    target.outputStream().use { output ->
                        source.inputStream().use { it.copyTo(output) }
                        output.fd.sync()
                    }
                    target.absolutePath
                }
            }
            return ActivatedImages(files, data)
        } catch (e: Exception) {
            files.forEach { it.delete() }
            throw e
        }
    }

    private fun createStagingDir(): File {
        val root = File(context.filesDir, BACKUP_STAGING_DIR).apply { mkdirs() }
        return File(root, "staging_${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun clearPreparedBackupCache() {
        val cached: PreparedBackup?
        synchronized(cacheLock) {
            cached = cachedPreparedBackup
            cachedPreparedBackup = null
            cachedBackupUri = null
            cachedBackupDigest = null
        }
        cached?.stagingDir?.let { deleteRecursivelyQuietly(it) }
    }

    private fun deleteRecursivelyQuietly(file: File?) {
        if (file != null && file.exists()) {
            file.deleteRecursively()
        }
    }

    private fun publishFile(fileName: String, mimeType: String, file: File): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val directory = requireNotNull(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS))
            val target = File(directory, "${UUID.randomUUID()}_$fileName")
            try { file.copyTo(target) } catch (e: Exception) { target.delete(); throw e }
            return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("无法创建文件")
        try {
            (resolver.openOutputStream(uri) ?: error("无法写入文件")).use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) { "无法完成备份文件" }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun createFileInDownloads(name: String, mimeType: String, content: ByteArray): Uri {
        val file = File.createTempFile("backup_export_", ".tmp", context.cacheDir)
        return try {
            file.writeBytes(content)
            publishFile(name, mimeType, file)
        } finally { file.delete() }
    }

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        return "\"${value.replace("\"", "\"\"")}\""
    }

    companion object {
        private val sharedOperationMutex = Mutex()
        private const val IMAGE_DIR = "images"
        private const val BACKUP_STAGING_DIR = "backup_staging"
        private const val ZIP_IMAGE_PREFIX = "zip://images/"
    }
}

data class ImportSummary(
    val totalImported: Int,
    val totalSkipped: Int,
    val totalErrors: Int = 0,
    val imageCount: Int = 0,
    val missingImageCount: Int = 0,
    val calendarEventsFailed: Int = 0,
    val backupDate: Long,
    val backupVersion: String
)

data class BackupPreview(
    val brandCount: Int,
    val categoryCount: Int,
    val coordinateCount: Int,
    val itemCount: Int,
    val priceCount: Int,
    val paymentCount: Int,
    val outfitLogCount: Int,
    val styleCount: Int = 0,
    val seasonCount: Int = 0,
    val locationCount: Int = 0,
    val sourceCount: Int = 0,
    val archiveCount: Int = 0,
    val monthlyBudgetCount: Int = 0,
    val tryOnResultCount: Int = 0,
    val imageTaskCount: Int = 0,
    val personalModelCount: Int = 0,
    val personalModelDraftCount: Int = 0,
    val storageNodeCount:Int=0,val itemDraftCount:Int=0,val recognitionTaskCount:Int=0,val itemEvidenceCount:Int=0,val imageProvenanceCount:Int=0,
    val additionalRecordCount: Int = 0,
    val orderBindingCount: Int = 0,
    val historicalOwnedCount: Int = 0,
    val needsReviewCount: Int = 0,
    val archivedOrderCount: Int = 0,
    val archivedHistoricalOwnedCount: Int = 0,
    val archivedNeedsReviewCount: Int = 0,
    val paymentStageCount: Int = 0,
    val missingImageCount: Int = 0,
    val imageCount: Int = 0,
    val backupDate: Long,
    val backupVersion: String
) {
    val totalCount: Int
        get() = brandCount + categoryCount + coordinateCount + itemCount + priceCount + paymentCount + outfitLogCount + styleCount + seasonCount + locationCount + sourceCount + archiveCount + additionalRecordCount + orderBindingCount + paymentStageCount + monthlyBudgetCount + tryOnResultCount + imageTaskCount + personalModelCount + personalModelDraftCount + storageNodeCount + itemDraftCount + recognitionTaskCount + itemEvidenceCount + imageProvenanceCount
}
