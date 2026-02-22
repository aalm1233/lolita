package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.notification.CalendarEventHelper
import com.lolita.app.data.notification.PaymentReminderScheduler
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val backupDate: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0"
)

class BackupManager(
    private val context: Context,
    private val database: LolitaDatabase
) {
    private val gson = Gson()
    private var cachedBackupData: BackupData? = null
    private var cachedBackupUri: Uri? = null
    private var cachedImageCount: Int = 0

    suspend fun exportToJson(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val backupData = database.withTransaction {
                BackupData(
                    brands = database.brandDao().getAllBrandsList(),
                    categories = database.categoryDao().getAllCategoriesList(),
                    coordinates = database.coordinateDao().getAllCoordinatesList(),
                    items = database.itemDao().getAllItemsList(),
                    prices = database.priceDao().getAllPricesList(),
                    payments = database.paymentDao().getAllPaymentsList(),
                    outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                    outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                    styles = database.styleDao().getAllStylesList(),
                    seasons = database.seasonDao().getAllSeasonsList(),
                    locations = database.locationDao().getAllLocationsList()
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
            sb.appendLine("id,name,is_preset,created_at")
            database.brandDao().getAllBrandsList().forEach { b ->
                sb.appendLine("${b.id},${escapeCsv(b.name)},${b.isPreset},${b.createdAt}")
            }

            // Categories
            sb.appendLine("\n=== CATEGORIES ===")
            sb.appendLine("id,name,is_preset,created_at")
            database.categoryDao().getAllCategoriesList().forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${c.isPreset},${c.createdAt}")
            }

            // Items
            sb.appendLine("\n=== ITEMS ===")
            sb.appendLine("id,name,description,brand_id,category_id,coordinate_id,status,priority,image_url,color,season,style,size,size_chart_image_url,created_at,updated_at")
            database.itemDao().getAllItemsList().forEach { i ->
                sb.appendLine("${i.id},${escapeCsv(i.name)},${escapeCsv(i.description)},${i.brandId},${i.categoryId},${i.coordinateId},${i.status},${i.priority},${escapeCsv(i.imageUrl)},${escapeCsv(i.color)},${escapeCsv(i.season)},${escapeCsv(i.style)},${escapeCsv(i.size)},${escapeCsv(i.sizeChartImageUrl)},${i.createdAt},${i.updatedAt}")
            }

            // Coordinates
            sb.appendLine("\n=== COORDINATES ===")
            sb.appendLine("id,name,description,image_url,created_at,updated_at")
            database.coordinateDao().getAllCoordinatesList().forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${escapeCsv(c.description)},${escapeCsv(c.imageUrl)},${c.createdAt},${c.updatedAt}")
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

            // Prices
            sb.appendLine("\n=== PRICES ===")
            sb.appendLine("id,item_id,type,total_price,deposit,balance,purchase_date")
            database.priceDao().getAllPricesList().forEach { p ->
                sb.appendLine("${p.id},${p.itemId},${p.type},${p.totalPrice},${p.deposit},${p.balance},${p.purchaseDate}")
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
                    items = database.itemDao().getAllItemsList(),
                    prices = database.priceDao().getAllPricesList(),
                    payments = database.paymentDao().getAllPaymentsList(),
                    outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                    outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                    styles = database.styleDao().getAllStylesList(),
                    seasons = database.seasonDao().getAllSeasonsList(),
                    locations = database.locationDao().getAllLocationsList()
                )
            }

            val imagePaths = collectImagePaths(backupData)
            val jsonBytes = gson.toJson(backupData).toByteArray()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "lolita_backup_${timestamp}.zip"

            val baos = java.io.ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(jsonBytes)
                zos.closeEntry()

                imagePaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val entryName = "images/${file.name}"
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            val uri = createFileInDownloads(fileName, "application/zip", baos.toByteArray())
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJson(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            var imageCount = 0
            val backupData: BackupData

            if (cachedBackupUri == uri && cachedBackupData != null) {
                backupData = cachedBackupData!!
                imageCount = cachedImageCount
            } else if (isZipFile(uri)) {
                val (data, count) = parseZipBackup(uri)
                backupData = remapImagePaths(data)
                imageCount = count
            } else {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: return@withContext Result.failure(Exception("无法读取文件"))
                backupData = gson.fromJson(jsonString, BackupData::class.java)
            }
            cachedBackupData = null
            cachedBackupUri = null
            cachedImageCount = 0

            var imported = 0

            database.withTransaction {
                clearAllTables()

                backupData.brands.forEach { database.brandDao().insertBrand(it); imported++ }
                backupData.categories.forEach { database.categoryDao().insertCategory(it); imported++ }
                backupData.styles.forEach { database.styleDao().insertStyle(it); imported++ }
                backupData.seasons.forEach { database.seasonDao().insertSeason(it); imported++ }
                backupData.locations.forEach { database.locationDao().insertLocation(it); imported++ }
                backupData.coordinates.forEach { database.coordinateDao().insertCoordinate(it); imported++ }
                backupData.items.forEach { database.itemDao().insertItem(it); imported++ }
                backupData.prices.forEach { database.priceDao().insertPrice(it); imported++ }
                backupData.payments.forEach { database.paymentDao().insertPayment(it.copy(calendarEventId = null)); imported++ }
                backupData.outfitLogs.forEach { database.outfitLogDao().insertOutfitLog(it); imported++ }
                backupData.outfitItemCrossRefs.forEach { database.outfitLogDao().insertOutfitItemCrossRef(it); imported++ }
            }

            // Verify import
            val actualCount = database.itemDao().getItemCount()
            if (actualCount != backupData.items.size) {
                return@withContext Result.failure(Exception("数据验证失败：期望 ${backupData.items.size} 条服饰，实际 $actualCount 条"))
            }

            // Recreate calendar events for unpaid payments with due dates
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
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Reschedule reminders
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
                imageCount = imageCount,
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun previewBackup(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val backupData: BackupData
            var imageCount = 0

            if (isZipFile(uri)) {
                val (data, count) = parseZipBackup(uri)
                backupData = remapImagePaths(data)
                imageCount = count
            } else {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    ?: return@withContext Result.failure(Exception("无法读取文件"))
                backupData = gson.fromJson(jsonString, BackupData::class.java)
            }
            cachedBackupData = backupData
            cachedBackupUri = uri
            cachedImageCount = imageCount

            Result.success(BackupPreview(
                brandCount = backupData.brands.size,
                categoryCount = backupData.categories.size,
                coordinateCount = backupData.coordinates.size,
                itemCount = backupData.items.size,
                priceCount = backupData.prices.size,
                paymentCount = backupData.payments.size,
                outfitLogCount = backupData.outfitLogs.size,
                styleCount = backupData.styles.size,
                seasonCount = backupData.seasons.size,
                locationCount = backupData.locations.size,
                imageCount = imageCount,
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearAllTables() {
        // Clean up orphaned image files before clearing database
        val imagesDir = File(context.filesDir, "images")
        if (imagesDir.exists()) {
            imagesDir.listFiles()?.forEach { it.delete() }
        }

        database.outfitLogDao().deleteAllOutfitItemCrossRefs()
        database.outfitLogDao().deleteAllOutfitLogs()
        database.paymentDao().deleteAllPayments()
        database.priceDao().deleteAllPrices()
        database.itemDao().deleteAllItems()
        database.locationDao().deleteAllLocations()
        database.coordinateDao().deleteAllCoordinates()
        database.brandDao().deleteAllBrands()
        database.categoryDao().deleteAllCategories()
        database.styleDao().deleteAllStyles()
        database.seasonDao().deleteAllSeasons()
    }

    private fun collectImagePaths(backupData: BackupData): Set<String> {
        val paths = mutableSetOf<String>()
        backupData.items.forEach { item ->
            item.imageUrl?.let { paths.add(it) }
            item.sizeChartImageUrl?.let { paths.add(it) }
        }
        backupData.coordinates.forEach { coord ->
            coord.imageUrl?.let { paths.add(it) }
        }
        backupData.outfitLogs.forEach { log ->
            log.imageUrls.forEach { paths.add(it) }
        }
        backupData.locations.forEach { location ->
            location.imageUrl?.let { paths.add(it) }
        }
        return paths
    }

    private fun isZipFile(uri: Uri): Boolean {
        val bytes = ByteArray(2)
        context.contentResolver.openInputStream(uri)?.use { it.read(bytes) } ?: return false
        return bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    private fun parseZipBackup(uri: Uri): Pair<BackupData, Int> {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        var imageCount = 0
        var jsonString: String? = null

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "data.json" -> {
                            jsonString = zis.bufferedReader().readText()
                        }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
                            val fileName = entry.name.substringAfter("images/")
                            if (fileName.isNotBlank()) {
                                File(imagesDir, fileName).outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                                imageCount++
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: throw Exception("无法读取文件")

        val data = gson.fromJson(jsonString ?: throw Exception("ZIP中未找到data.json"), BackupData::class.java)
        return Pair(data, imageCount)
    }

    private fun remapImagePaths(backupData: BackupData): BackupData {
        val imagesDir = File(context.filesDir, "images").absolutePath
        fun remap(path: String?): String? {
            if (path == null) return null
            val fileName = File(path).name
            return "$imagesDir/$fileName"
        }
        return backupData.copy(
            items = backupData.items.map { it.copy(imageUrl = remap(it.imageUrl), sizeChartImageUrl = remap(it.sizeChartImageUrl)) },
            coordinates = backupData.coordinates.map { it.copy(imageUrl = remap(it.imageUrl)) },
            outfitLogs = backupData.outfitLogs.map { it.copy(imageUrls = it.imageUrls.map { url -> remap(url) ?: url }) },
            locations = backupData.locations.map { it.copy(imageUrl = remap(it.imageUrl)) }
        )
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
}

data class ImportSummary(
    val totalImported: Int,
    val totalSkipped: Int,
    val totalErrors: Int = 0,
    val imageCount: Int = 0,
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
    val imageCount: Int = 0,
    val backupDate: Long,
    val backupVersion: String
) {
    val totalCount: Int
        get() = brandCount + categoryCount + coordinateCount + itemCount + priceCount + paymentCount + outfitLogCount + styleCount + seasonCount + locationCount
}
