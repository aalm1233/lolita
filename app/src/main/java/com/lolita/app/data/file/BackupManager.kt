package com.lolita.app.data.file

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.notification.PaymentReminderScheduler
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    suspend fun exportToJson(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val backupData = BackupData(
                brands = database.brandDao().getAllBrandsList(),
                categories = database.categoryDao().getAllCategoriesList(),
                coordinates = database.coordinateDao().getAllCoordinatesList(),
                items = database.itemDao().getAllItemsList(),
                prices = database.priceDao().getAllPricesList(),
                payments = database.paymentDao().getAllPaymentsList(),
                outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                styles = database.styleDao().getAllStylesList(),
                seasons = database.seasonDao().getAllSeasonsList()
            )
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
            sb.appendLine("id,name,description,brand_id,category_id,coordinate_id,status,priority,image_url,created_at,updated_at")
            database.itemDao().getAllItemsList().forEach { i ->
                sb.appendLine("${i.id},${escapeCsv(i.name)},${escapeCsv(i.description)},${i.brandId},${i.categoryId},${i.coordinateId},${i.status},${i.priority},${escapeCsv(i.imageUrl)},${i.createdAt},${i.updatedAt}")
            }

            // Coordinates
            sb.appendLine("\n=== COORDINATES ===")
            sb.appendLine("id,name,description,created_at,updated_at")
            database.coordinateDao().getAllCoordinatesList().forEach { c ->
                sb.appendLine("${c.id},${escapeCsv(c.name)},${escapeCsv(c.description)},${c.createdAt},${c.updatedAt}")
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
    suspend fun importFromJson(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val backupData = if (cachedBackupUri == uri && cachedBackupData != null) {
                cachedBackupData!!
            } else {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@withContext Result.failure(Exception("无法读取文件"))
                gson.fromJson(jsonString, BackupData::class.java)
            }
            cachedBackupData = null
            cachedBackupUri = null

            var imported = 0
            var skipped = 0

            database.withTransaction {
                // Import in order respecting foreign keys
                backupData.brands.forEach { try { database.brandDao().insertBrand(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.categories.forEach { try { database.categoryDao().insertCategory(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.styles.forEach { try { database.styleDao().insertStyle(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.seasons.forEach { try { database.seasonDao().insertSeason(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.coordinates.forEach { try { database.coordinateDao().insertCoordinate(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.items.forEach { try { database.itemDao().insertItem(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.prices.forEach { try { database.priceDao().insertPrice(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.payments.forEach { try { database.paymentDao().insertPayment(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.outfitLogs.forEach { try { database.outfitLogDao().insertOutfitLog(it); imported++ } catch (_: Exception) { skipped++ } }
                backupData.outfitItemCrossRefs.forEach { try { database.outfitLogDao().insertOutfitItemCrossRef(it); imported++ } catch (_: Exception) { skipped++ } }
            }

            // Reschedule reminders for imported payments (M-10)
            try {
                val scheduler = PaymentReminderScheduler(context)
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
                totalSkipped = skipped,
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun previewBackup(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(Exception("无法读取文件"))

            val backupData = gson.fromJson(jsonString, BackupData::class.java)
            cachedBackupData = backupData
            cachedBackupUri = uri

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
                backupDate = backupData.backupDate,
                backupVersion = backupData.appVersion
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
    val backupDate: Long,
    val backupVersion: String
) {
    val totalCount: Int
        get() = brandCount + categoryCount + coordinateCount + itemCount + priceCount + paymentCount + outfitLogCount + styleCount + seasonCount
}
