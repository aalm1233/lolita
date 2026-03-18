package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
    val catalogEntries: List<CatalogEntry> = emptyList(),
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
    val appVersion: String = "1.0"
)

private data class PreparedBackup(
    val backupData: BackupData,
    val imageCount: Int,
    val isZip: Boolean,
    val stagingDir: File? = null
)

class BackupManager(
    private val context: Context,
    private val database: LolitaDatabase
) {
    private val gson = Gson()
    private var cachedPreparedBackup: PreparedBackup? = null
    private var cachedBackupUri: Uri? = null

    suspend fun exportToJson(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val backupData = database.withTransaction {
                BackupData(
                    brands = database.brandDao().getAllBrandsList(),
                    categories = database.categoryDao().getAllCategoriesList(),
                    coordinates = database.coordinateDao().getAllCoordinatesList(),
                    catalogEntries = database.catalogEntryDao().getAllCatalogEntriesList(),
                    items = database.itemDao().getAllItemsList(),
                    prices = database.priceDao().getAllPricesList(),
                    payments = database.paymentDao().getAllPaymentsList(),
                    outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                    outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                    styles = database.styleDao().getAllStylesList(),
                    seasons = database.seasonDao().getAllSeasonsList(),
                    locations = database.locationDao().getAllLocationsList(),
                    sources = database.sourceDao().getAllSourcesList()
                )
            }
            val jsonString = gson.toJson(backupData)
            val fileName = "lolita_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val uri = createFileInDownloads(fileName, "application/json", jsonString.toByteArray())
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToCsv(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val sb = StringBuilder()

            // Brands
            sb.appendLine("=== BRANDS ===")
            sb.appendLine("id,name,is_preset,logo_url,created_at")
            database.brandDao().getAllBrandsList().forEach { b ->
                sb.appendLine("${b.id},${escapeCsv(b.name)},${b.isPreset},${escapeCsv(b.logoUrl)},${b.createdAt}")
            }

            // Categories
            sb.appendLine("\n=== CATEGORIES ===")
            sb.appendLine("id,name,is_preset,created_at")
            database.categoryDao().getAllCategoriesList().forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${c.isPreset},${c.createdAt}")
            }

            // Items
            sb.appendLine("\n=== ITEMS ===")
            sb.appendLine("id,name,description,brand_id,category_id,coordinate_id,status,priority,image_urls,colors,season,style,size,size_chart_image_url,created_at,updated_at")
            database.itemDao().getAllItemsList().forEach { i ->
                sb.appendLine("${i.id},${escapeCsv(i.name)},${escapeCsv(i.description)},${i.brandId},${i.categoryId},${i.coordinateId},${i.status},${i.priority},${escapeCsv(i.imageUrls.joinToString(";"))},${escapeCsv(i.colors)},${escapeCsv(i.season)},${escapeCsv(i.style)},${escapeCsv(i.size)},${escapeCsv(i.sizeChartImageUrl)},${i.createdAt},${i.updatedAt}")
            }

            // Coordinates
            sb.appendLine("\n=== COORDINATES ===")
            sb.appendLine("id,name,description,image_urls,created_at,updated_at")
            database.coordinateDao().getAllCoordinatesList().forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${escapeCsv(c.description)},${escapeCsv(c.imageUrls.joinToString(";"))},${c.createdAt},${c.updatedAt}")
            }

            // Catalog entries
            sb.appendLine("\n=== CATALOG_ENTRIES ===")
            sb.appendLine("id,name,brand_id,category_id,series_name,reference_url,image_urls,colors,style,season,size,source,description,linked_item_id,created_at,updated_at")
            database.catalogEntryDao().getAllCatalogEntriesList().forEach { entry ->
                sb.appendLine("${entry.id},${escapeCsv(entry.name)},${entry.brandId ?: ""},${entry.categoryId ?: ""},${escapeCsv(entry.seriesName)},${escapeCsv(entry.referenceUrl)},${escapeCsv(entry.imageUrls.joinToString(";"))},${escapeCsv(entry.colors.joinToString(";"))},${escapeCsv(entry.style)},${escapeCsv(entry.season)},${escapeCsv(entry.size)},${escapeCsv(entry.source)},${escapeCsv(entry.description)},${entry.linkedItemId ?: ""},${entry.createdAt},${entry.updatedAt}")
            }

            // Styles
            sb.appendLine("\n=== STYLES ===")
            sb.appendLine("id,name,is_preset,created_at")
            database.styleDao().getAllStylesList().forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Seasons
            sb.appendLine("\n=== SEASONS ===")
            sb.appendLine("id,name,is_preset,created_at")
            database.seasonDao().getAllSeasonsList().forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Locations
            sb.appendLine("\n=== LOCATIONS ===")
            sb.appendLine("id,name,description,image_url,sort_order,created_at,updated_at")
            database.locationDao().getAllLocationsList().forEach { l ->
                sb.appendLine("${l.id},${escapeCsv(l.name)},${escapeCsv(l.description)},${escapeCsv(l.imageUrl)},${l.sortOrder},${l.createdAt},${l.updatedAt}")
            }

            // Sources
            sb.appendLine("\n=== SOURCES ===")
            sb.appendLine("id,name,is_preset,created_at")
            database.sourceDao().getAllSourcesList().forEach { s ->
                sb.appendLine("${s.id},${escapeCsv(s.name)},${s.isPreset},${s.createdAt}")
            }

            // Prices
            sb.appendLine("\n=== PRICES ===")
            sb.appendLine("id,item_id,type,total_price,deposit,balance")
            database.priceDao().getAllPricesList().forEach { p ->
                sb.appendLine("${p.id},${p.itemId},${p.type},${p.totalPrice},${p.deposit},${p.balance}")
            }

            // Payments
            sb.appendLine("\n=== PAYMENTS ===")
            sb.appendLine("id,price_id,amount,due_date,is_paid,paid_date,reminder_set,custom_reminder_days,calendar_event_id")
            database.paymentDao().getAllPaymentsList().forEach { p ->
                sb.appendLine("${p.id},${p.priceId},${p.amount},${p.dueDate},${p.isPaid},${p.paidDate},${p.reminderSet},${p.customReminderDays},${p.calendarEventId}")
            }

            // OutfitLogs
            sb.appendLine("\n=== OUTFIT_LOGS ===")
            sb.appendLine("id,date,note,image_urls,created_at")
            database.outfitLogDao().getAllOutfitLogsList().forEach { o ->
                sb.appendLine("${o.id},${o.date},${escapeCsv(o.note)},${escapeCsv(o.imageUrls.joinToString(";"))},${o.createdAt}")
            }

            // OutfitItemCrossRefs
            sb.appendLine("\n=== OUTFIT_ITEM_CROSS_REFS ===")
            sb.appendLine("outfit_log_id,item_id")
            database.outfitLogDao().getAllOutfitItemCrossRefsList().forEach { r ->
                sb.appendLine("${r.outfitLogId},${r.itemId}")
            }

            val uri = createFileInDownloads("lolita_export_$timestamp.csv", "text/csv", sb.toString().toByteArray())
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun exportToZip(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val backupData = database.withTransaction {
                BackupData(
                    brands = database.brandDao().getAllBrandsList(),
                    categories = database.categoryDao().getAllCategoriesList(),
                    coordinates = database.coordinateDao().getAllCoordinatesList(),
                    catalogEntries = database.catalogEntryDao().getAllCatalogEntriesList(),
                    items = database.itemDao().getAllItemsList(),
                    prices = database.priceDao().getAllPricesList(),
                    payments = database.paymentDao().getAllPaymentsList(),
                    outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                    outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                    styles = database.styleDao().getAllStylesList(),
                    seasons = database.seasonDao().getAllSeasonsList(),
                    locations = database.locationDao().getAllLocationsList(),
                    sources = database.sourceDao().getAllSourcesList()
                )
            }

            val imageArchiveNamesByPath = buildImageArchiveNames(collectImagePaths(backupData))
            val zipBackupData = rewriteImagePathsForZip(backupData, imageArchiveNamesByPath)
            val jsonBytes = gson.toJson(zipBackupData).toByteArray()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "lolita_backup_${timestamp}.zip"

            val (uri, outputStream) = createStreamInDownloads(fileName, "application/zip")
            outputStream.use { os ->
                ZipOutputStream(os).use { zos ->
                    zos.putNextEntry(ZipEntry("data.json"))
                    zos.write(jsonBytes)
                    zos.closeEntry()

                    imageArchiveNamesByPath.forEach { (path, archiveName) ->
                        val file = File(path)
                        if (file.exists()) {
                            val entryName = "images/$archiveName"
                            zos.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJson(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        var activatedImages: ActivatedImages? = null
        try {
            val preparedBackup = prepareBackup(uri, cacheResult = true)
            val backupData = if (preparedBackup.isZip) {
                remapZipImageRefsToDirectory(
                    preparedBackup.backupData,
                    File(context.filesDir, IMAGE_DIR)
                )
            } else {
                preparedBackup.backupData
            }

            var imported = 0

            if (preparedBackup.isZip) {
                activatedImages = activateStagedImages(preparedBackup)
            }

            database.withTransaction {
                clearAllTables()

                backupData.brands.forEach { database.brandDao().insertBrand(it); imported++ }
                backupData.categories.forEach { database.categoryDao().insertCategory(it); imported++ }
                backupData.styles.forEach { database.styleDao().insertStyle(it); imported++ }
                backupData.seasons.forEach { database.seasonDao().insertSeason(it); imported++ }
                backupData.locations.forEach { database.locationDao().insertLocation(it); imported++ }
                backupData.sources.forEach { database.sourceDao().insertSource(it); imported++ }
                backupData.coordinates.forEach { database.coordinateDao().insertCoordinate(it); imported++ }
                backupData.items.forEach { database.itemDao().insertItem(it); imported++ }
                backupData.catalogEntries.forEach { database.catalogEntryDao().insertCatalogEntry(it); imported++ }
                backupData.prices.forEach { database.priceDao().insertPrice(it); imported++ }
                backupData.payments.forEach { database.paymentDao().insertPayment(it.copy(calendarEventId = null)); imported++ }
                backupData.outfitLogs.forEach { database.outfitLogDao().insertOutfitLog(it); imported++ }
                backupData.outfitItemCrossRefs.forEach { database.outfitLogDao().insertOutfitItemCrossRef(it); imported++ }
            }

            val actualCount = database.itemDao().getItemCount()
            if (actualCount != backupData.items.size) {
                throw Exception("数据验证失败：期望 ${backupData.items.size} 条服饰，实际 $actualCount 条")
            }

            activatedImages?.let {
                commitActivatedImages(it)
                activatedImages = null
            }
            clearPreparedBackupCache()

            var calendarFailCount = 0
            try {
                val unpaidPayments = database.paymentDao().getAllPaymentsList()
                    .filter { !it.isPaid && it.dueDate > System.currentTimeMillis() }
                unpaidPayments.forEach { payment ->
                    try {
                        val eventId = CalendarEventHelper.insertEvent(
                            context = context,
                            title = "付款提醒",
                            description = "付款金额: ¥${String.format("%.2f", payment.amount)}",
                            startTimeMillis = payment.dueDate
                        )
                        if (eventId != null) {
                            database.paymentDao().updateCalendarEventId(payment.id, eventId)
                        } else {
                            calendarFailCount++
                        }
                    } catch (e: Exception) {
                        calendarFailCount++
                        Log.e("BackupManager", "Failed to create calendar event for payment ${payment.id}", e)
                    }
                }
            } catch (e: Exception) {
                calendarFailCount = -1
                Log.e("BackupManager", "Failed to recreate calendar events during import", e)
            }

            try {
                val scheduler = PaymentReminderScheduler(context)
                val allPayments = database.paymentDao().getAllPaymentsList()
                allPayments.forEach { payment ->
                    try { scheduler.cancelReminder(payment.id) } catch (_: Exception) {}
                }
                val pendingPayments = database.paymentDao().getPendingReminderPaymentsWithItemInfoList()
                val paymentEntities = database.paymentDao().getPendingReminderPaymentsList()
                val paymentMap = paymentEntities.associateBy { it.id }
                pendingPayments.forEach { info ->
                    val payment = paymentMap[info.paymentId] ?: return@forEach
                    try { scheduler.scheduleReminder(payment, info.itemName) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            Result.success(ImportSummary(
                totalImported = imported,
                totalSkipped = 0,
                totalErrors = 0,
                imageCount = preparedBackup.imageCount,
                calendarEventsFailed = if (calendarFailCount > 0) calendarFailCount else 0,
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            activatedImages?.let {
                rollbackActivatedImages(it)
                activatedImages = null
            }
            clearPreparedBackupCache()
            Result.failure(e)
        }
    }

    suspend fun previewBackup(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val preparedBackup = prepareBackup(uri, cacheResult = true)
            val backupData = preparedBackup.backupData

            Result.success(BackupPreview(
                brandCount = backupData.brands.size,
                categoryCount = backupData.categories.size,
                coordinateCount = backupData.coordinates.size,
                catalogCount = backupData.catalogEntries.size,
                itemCount = backupData.items.size,
                priceCount = backupData.prices.size,
                paymentCount = backupData.payments.size,
                outfitLogCount = backupData.outfitLogs.size,
                styleCount = backupData.styles.size,
                seasonCount = backupData.seasons.size,
                locationCount = backupData.locations.size,
                sourceCount = backupData.sources.size,
                imageCount = preparedBackup.imageCount,
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearAllTables() {
        database.sharedLibrarySyncDao().clearPricePlans()
        database.sharedLibrarySyncDao().clearSharedItems()
        database.sharedLibrarySyncDao().clearCoordinates()
        database.sharedLibrarySyncDao().clearCatalogEntries()
        database.sharedLibrarySyncDao().clearSources()
        database.sharedLibrarySyncDao().clearSeasons()
        database.sharedLibrarySyncDao().clearStyles()
        database.sharedLibrarySyncDao().clearCategories()
        database.sharedLibrarySyncDao().clearBrands()
        database.sharedLibrarySyncDao().deleteSyncState()
        database.outfitLogDao().deleteAllOutfitItemCrossRefs()
        database.outfitLogDao().deleteAllOutfitLogs()
        database.paymentDao().deleteAllPayments()
        database.priceDao().deleteAllPrices()
        database.catalogEntryDao().deleteAllCatalogEntries()
        database.itemDao().deleteAllItems()
        database.locationDao().deleteAllLocations()
        database.coordinateDao().deleteAllCoordinates()
        database.brandDao().deleteAllBrands()
        database.categoryDao().deleteAllCategories()
        database.styleDao().deleteAllStyles()
        database.seasonDao().deleteAllSeasons()
        database.sourceDao().deleteAllSources()
    }

    private fun collectImagePaths(backupData: BackupData): Set<String> {
        val paths = mutableSetOf<String>()
        backupData.brands.forEach { brand ->
            brand.logoUrl?.let { paths.add(it) }
        }
        backupData.items.forEach { item ->
            item.imageUrls.forEach { paths.add(it) }
            item.sizeChartImageUrl?.let { paths.add(it) }
        }
        backupData.coordinates.forEach { coord ->
            coord.imageUrls.forEach { paths.add(it) }
        }
        backupData.catalogEntries.forEach { entry ->
            entry.imageUrls.forEach { paths.add(it) }
        }
        backupData.outfitLogs.forEach { log ->
            log.imageUrls.forEach { paths.add(it) }
        }
        backupData.locations.forEach { location ->
            location.imageUrl?.let { paths.add(it) }
        }
        return paths
    }

    private fun buildImageArchiveNames(imagePaths: Set<String>): Map<String, String> {
        return imagePaths
            .filter { path -> File(path).isFile }
            .associateWith { path -> buildArchiveImageName(path) }
    }

    private fun buildArchiveImageName(path: String): String {
        val extension = File(path).extension
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return if (extension.isNotBlank()) "$digest.$extension" else digest
    }

    private fun rewriteImagePathsForZip(
        backupData: BackupData,
        imageArchiveNamesByPath: Map<String, String>
    ): BackupData {
        fun toZipRef(path: String?): String? {
            if (path == null) return null
            return imageArchiveNamesByPath[path]?.let { "$ZIP_IMAGE_PREFIX$it" } ?: path
        }

        return backupData.copy(
            brands = backupData.brands.map { it.copy(logoUrl = toZipRef(it.logoUrl)) },
            items = backupData.items.map {
                it.copy(
                    imageUrls = it.imageUrls.mapNotNull(::toZipRef),
                    sizeChartImageUrl = toZipRef(it.sizeChartImageUrl)
                )
            },
            catalogEntries = backupData.catalogEntries.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            coordinates = backupData.coordinates.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            outfitLogs = backupData.outfitLogs.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            locations = backupData.locations.map { it.copy(imageUrl = toZipRef(it.imageUrl)) }
        )
    }

    private fun isZipFile(uri: Uri): Boolean {
        val bytes = ByteArray(2)
        context.contentResolver.openInputStream(uri)?.use { it.read(bytes) } ?: return false
        return bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    private fun prepareBackup(uri: Uri, cacheResult: Boolean): PreparedBackup {
        if (cachedBackupUri == uri && cachedPreparedBackup != null) {
            return cachedPreparedBackup!!
        }

        clearPreparedBackupCache()

        val prepared = if (isZipFile(uri)) {
            parseZipBackup(uri)
        } else {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: throw Exception("无法读取文件")
            PreparedBackup(
                backupData = migrateBackupData(
                    gson.fromJson(migrateJsonString(jsonString), BackupData::class.java)
                ),
                imageCount = 0,
                isZip = false
            )
        }

        if (cacheResult) {
            cachedPreparedBackup = prepared
            cachedBackupUri = uri
        }
        return prepared
    }

    private fun parseZipBackup(uri: Uri): PreparedBackup {
        val stagingDir = createStagingDir()
        val stagedImagesDir = File(stagingDir, IMAGE_DIR).apply { mkdirs() }
        var imageCount = 0
        var jsonString: String? = null
        val availableImageNames = mutableSetOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "data.json" -> {
                                jsonString = zis.bufferedReader().readText()
                            }
                            entry.name.startsWith("images/") && !entry.isDirectory -> {
                                val fileName = entry.name.substringAfterLast("/")
                                if (fileName.isNotBlank()) {
                                    File(stagedImagesDir, fileName).outputStream().use { out ->
                                        zis.copyTo(out)
                                    }
                                    availableImageNames.add(fileName)
                                    imageCount++
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw Exception("无法读取文件")

            val data = gson.fromJson(
                migrateJsonString(jsonString ?: throw Exception("ZIP中未找到data.json")),
                BackupData::class.java
            )
            return PreparedBackup(
                backupData = migrateBackupData(
                    rewriteImagePathsFromZipEntries(data, availableImageNames)
                ),
                imageCount = imageCount,
                isZip = true,
                stagingDir = stagingDir
            )
        } catch (e: Exception) {
            deleteRecursivelyQuietly(stagingDir)
            throw e
        }
    }

    private fun rewriteImagePathsFromZipEntries(
        backupData: BackupData,
        availableImageNames: Set<String>
    ): BackupData {
        fun toZipRef(path: String?): String? {
            if (path == null) return null
            if (path.startsWith(ZIP_IMAGE_PREFIX)) return path
            val fileName = File(path).name
            return if (fileName.isNotBlank() && fileName in availableImageNames) {
                "$ZIP_IMAGE_PREFIX$fileName"
            } else {
                path
            }
        }

        return backupData.copy(
            brands = backupData.brands.map { it.copy(logoUrl = toZipRef(it.logoUrl)) },
            items = backupData.items.map {
                it.copy(
                    imageUrls = it.imageUrls.mapNotNull(::toZipRef),
                    sizeChartImageUrl = toZipRef(it.sizeChartImageUrl)
                )
            },
            catalogEntries = backupData.catalogEntries.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            coordinates = backupData.coordinates.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            outfitLogs = backupData.outfitLogs.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::toZipRef))
            },
            locations = backupData.locations.map { it.copy(imageUrl = toZipRef(it.imageUrl)) }
        )
    }

    private fun remapZipImageRefsToDirectory(backupData: BackupData, imageDir: File): BackupData {
        fun remap(path: String?): String? {
            if (path == null || !path.startsWith(ZIP_IMAGE_PREFIX)) return path
            val fileName = path.removePrefix(ZIP_IMAGE_PREFIX).substringAfterLast("/")
            return File(imageDir, fileName).absolutePath
        }

        return backupData.copy(
            brands = backupData.brands.map { it.copy(logoUrl = remap(it.logoUrl)) },
            items = backupData.items.map {
                it.copy(
                    imageUrls = it.imageUrls.mapNotNull(::remap),
                    sizeChartImageUrl = remap(it.sizeChartImageUrl)
                )
            },
            catalogEntries = backupData.catalogEntries.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::remap))
            },
            coordinates = backupData.coordinates.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::remap))
            },
            outfitLogs = backupData.outfitLogs.map {
                it.copy(imageUrls = it.imageUrls.mapNotNull(::remap))
            },
            locations = backupData.locations.map { it.copy(imageUrl = remap(it.imageUrl)) }
        )
    }

    private data class ActivatedImages(
        val liveImagesDir: File,
        val backupImagesDir: File?,
        val stagingDir: File
    )

    private fun activateStagedImages(preparedBackup: PreparedBackup): ActivatedImages {
        val stagingDir = preparedBackup.stagingDir
            ?: throw IllegalStateException("备份图片暂存目录不存在")
        val stagedImagesDir = File(stagingDir, IMAGE_DIR).apply { mkdirs() }
        val liveImagesDir = File(context.filesDir, IMAGE_DIR)
        val backupImagesDir = if (liveImagesDir.exists()) {
            File(context.filesDir, "${IMAGE_DIR}_backup_${UUID.randomUUID()}")
        } else {
            null
        }

        if (backupImagesDir != null) {
            moveDirectory(liveImagesDir, backupImagesDir)
        }

        try {
            moveDirectory(stagedImagesDir, liveImagesDir)
        } catch (e: Exception) {
            if (backupImagesDir != null && !liveImagesDir.exists()) {
                moveDirectory(backupImagesDir, liveImagesDir)
            }
            throw e
        }

        return ActivatedImages(
            liveImagesDir = liveImagesDir,
            backupImagesDir = backupImagesDir,
            stagingDir = stagingDir
        )
    }

    private fun commitActivatedImages(activatedImages: ActivatedImages) {
        deleteRecursivelyQuietly(activatedImages.backupImagesDir)
        deleteRecursivelyQuietly(activatedImages.stagingDir)
    }

    private fun rollbackActivatedImages(activatedImages: ActivatedImages) {
        deleteRecursivelyQuietly(activatedImages.liveImagesDir)
        activatedImages.backupImagesDir?.let { backupDir ->
            moveDirectory(backupDir, activatedImages.liveImagesDir)
        }
        deleteRecursivelyQuietly(activatedImages.stagingDir)
    }

    private fun moveDirectory(sourceDir: File, targetDir: File) {
        targetDir.parentFile?.mkdirs()
        if (!sourceDir.exists()) {
            if (!targetDir.exists()) targetDir.mkdirs()
            return
        }
        if (targetDir.exists()) {
            deleteRecursivelyQuietly(targetDir)
        }
        if (sourceDir.renameTo(targetDir)) return

        copyDirectory(sourceDir, targetDir)
        if (!sourceDir.deleteRecursively()) {
            throw Exception("无法清理临时目录")
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists()) target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyDirectory(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun createStagingDir(): File {
        val root = File(context.filesDir, BACKUP_STAGING_DIR).apply { mkdirs() }
        return File(root, "staging_${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun clearPreparedBackupCache() {
        cachedPreparedBackup?.stagingDir?.let { deleteRecursivelyQuietly(it) }
        cachedPreparedBackup = null
        cachedBackupUri = null
    }

    private fun deleteRecursivelyQuietly(file: File?) {
        if (file != null && file.exists()) {
            file.deleteRecursively()
        }
    }

    private fun createStreamInDownloads(fileName: String, mimeType: String): Pair<Uri, java.io.OutputStream> {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("无法创建文件")
        val outputStream = resolver.openOutputStream(uri) ?: throw Exception("无法写入文件")
        return Pair(uri, outputStream)
    }

    private fun createFileInDownloads(fileName: String, mimeType: String, content: ByteArray): Uri {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("无法创建文件")
        (resolver.openOutputStream(uri) ?: throw Exception("无法写入文件")).use { it.write(content) }
        return uri
    }

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /**
     * Pre-process backup JSON string for backward compatibility.
     * Renames old "color" field to "colors" so Gson can deserialize it.
     * Converts old single "imageUrl" field to "imageUrls" list format.
     */
    private fun migrateJsonString(json: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            ensureArrayField(root, "catalogEntries")
            ensureArrayField(root, "styles")
            ensureArrayField(root, "seasons")
            ensureArrayField(root, "locations")
            ensureArrayField(root, "sources")
            migrateArray(root, "items", migrateColor = true, migrateImageUrl = true)
            migrateArray(root, "coordinates", migrateColor = false, migrateImageUrl = true)
            migrateArray(root, "outfitLogs", migrateColor = false, migrateImageUrl = true)
            root.toString()
        } catch (_: Exception) {
            json
        }
    }

    private fun ensureArrayField(root: JsonObject, fieldName: String) {
        if (!root.has(fieldName) || root.get(fieldName).isJsonNull) {
            root.add(fieldName, JsonArray())
        }
    }

    private fun migrateArray(
        root: JsonObject,
        fieldName: String,
        migrateColor: Boolean,
        migrateImageUrl: Boolean
    ) {
        val array = root.getAsJsonArray(fieldName) ?: return
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach

            if (migrateColor && obj.has("color") && !obj.has("colors")) {
                obj.add("colors", obj.remove("color"))
            }

            if (migrateImageUrl && obj.has("imageUrl") && !obj.has("imageUrls")) {
                val imageUrl = obj.remove("imageUrl")
                val imageUrls = JsonArray()
                if (imageUrl != null && !imageUrl.isJsonNull) {
                    val value = imageUrl.asString
                    if (value.isNotBlank()) {
                        imageUrls.add(value)
                    }
                }
                obj.add("imageUrls", imageUrls)
            }
        }
    }

    /**
     * Post-process imported backup data for backward compatibility.
     * Converts old single-string color values to JSON array format.
     * Old backups: "color": "粉色" → new format: "colors": "[\"粉色\"]"
     */
    private fun migrateBackupData(backupData: BackupData): BackupData {
        val fixedItems = backupData.items.map { item ->
            if (item.colors != null && !item.colors.startsWith("[")) {
                // Old format: plain string like "粉色" → convert to JSON array
                item.copy(colors = gson.toJson(listOf(item.colors)))
            } else {
                item
            }
        }
        return if (fixedItems != backupData.items) {
            backupData.copy(items = fixedItems)
        } else {
            backupData
        }
    }

    companion object {
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
    val calendarEventsFailed: Int = 0,
    val backupDate: Long,
    val backupVersion: String
)

data class BackupPreview(
    val brandCount: Int,
    val categoryCount: Int,
    val coordinateCount: Int,
    val catalogCount: Int = 0,
    val itemCount: Int,
    val priceCount: Int,
    val paymentCount: Int,
    val outfitLogCount: Int,
    val styleCount: Int = 0,
    val seasonCount: Int = 0,
    val locationCount: Int = 0,
    val sourceCount: Int = 0,
    val imageCount: Int = 0,
    val backupDate: Long,
    val backupVersion: String
) {
    val totalCount: Int
        get() = brandCount + categoryCount + coordinateCount + catalogCount + itemCount + priceCount + paymentCount + outfitLogCount + styleCount + seasonCount + locationCount + sourceCount
}
