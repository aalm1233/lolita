package com.lolita.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.file.BackupData
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.di.AppModule
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(AndroidJUnit4::class)
class DataIntegrityTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)
    private val migrations = arrayOf(
        LolitaDatabase.MIGRATION_1_2, LolitaDatabase.MIGRATION_2_3, LolitaDatabase.MIGRATION_3_4,
        LolitaDatabase.MIGRATION_4_5, LolitaDatabase.MIGRATION_5_6,
        LolitaDatabase.MIGRATION_6_7, LolitaDatabase.MIGRATION_7_8, LolitaDatabase.MIGRATION_8_9,
        LolitaDatabase.MIGRATION_9_10, LolitaDatabase.MIGRATION_10_11, LolitaDatabase.MIGRATION_11_12,
        LolitaDatabase.MIGRATION_12_13, LolitaDatabase.MIGRATION_13_14, LolitaDatabase.MIGRATION_14_15,
        LolitaDatabase.MIGRATION_15_16, LolitaDatabase.MIGRATION_16_17, LolitaDatabase.MIGRATION_17_18,
        LolitaDatabase.MIGRATION_18_19, LolitaDatabase.MIGRATION_19_20, LolitaDatabase.MIGRATION_20_21, LolitaDatabase.MIGRATION_21_22, LolitaDatabase.MIGRATION_22_23
    )

    private fun seed(db: SupportSQLiteDatabase, table: String, recordId: Long = 1L) {
        val columns = mutableListOf<String>(); val values = mutableListOf<Any?>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1); val type = cursor.getString(2)
                columns.add(name)
                val value: Any? = when {
                    name == "color" || name == "image_url" -> "引号\"与换行\n和反斜线\\"
                    name == "id" -> recordId
                    name in setOf("brand_id", "category_id", "coordinate_id", "linked_item_id", "item_id", "price_id", "outfit_log_id") -> 1L
                    cursor.getInt(3) == 0 -> null
                    type == "INTEGER" || type == "REAL" -> 100L
                    else -> mapOf("status" to "OWNED", "priority" to "MEDIUM", "type" to "FULL", "category_group" to "CLOTHING", "image_urls" to "[]", "colors" to "[]")[name] ?: "样本$table"
                }
                values.add(value)
            }
        }
        db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})", values.toTypedArray())
    }

    @Test fun migrationChainsPreserveChildrenLinksAndArchivedCatalog() {
        for (version in 2..20) {
            val name = "integrity_migration_$version"
            context.deleteDatabase(name)
            helper.createDatabase(name, version).apply {
                execSQL("PRAGMA foreign_keys=ON")
                listOf("brands", "categories", "coordinates", "items", "prices", "payments", "outfit_logs", "outfit_item_cross_ref").forEach { seed(this, it) }
                if (version in 16..18) seed(this, "catalog_entries")
                if (version in 17..18) seed(this, "remote_catalog_entries")
                close()
            }
            val db = helper.runMigrationsAndValidate(name, 23, true, *migrations)
            try {
                listOf("items", "prices", "payments", "outfit_logs", "outfit_item_cross_ref").forEach { table ->
                    db.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals("v$version $table", 1, it.getInt(0)) }
                }
                if (version <= 14) {
                    db.query("SELECT image_urls FROM items WHERE id=1").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(listOf("引号\"与换行\n和反斜线\\"), Gson().fromJson(it.getString(0), Array<String>::class.java).toList())
                    }
                }
                if (version <= 10) {
                    db.query("SELECT colors FROM items WHERE id=1").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(listOf("引号\"与换行\n和反斜线\\"), Gson().fromJson(it.getString(0), Array<String>::class.java).toList())
                    }
                }
                db.query("SELECT coordinate_id FROM items WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)) }
                db.query("SELECT payload FROM legacy_catalog_archive WHERE source_table='catalog_entries'").use {
                    if (version in 16..18) {
                        assertTrue(it.moveToFirst())
                        assertEquals(1L, JsonParser.parseString(it.getString(0)).asJsonObject["linked_item_id"].asLong)
                    } else assertFalse(it.moveToFirst())
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun migrationPreservesAmbiguousPricesWithoutChoosingOrSumming() {
        val name = "order_conflict_migration"
        context.deleteDatabase(name)
        helper.createDatabase(name, 20).apply {
            execSQL("PRAGMA foreign_keys=ON")
            listOf("brands", "categories", "coordinates", "items", "prices", "payments").forEach { seed(this, it) }
            execSQL("UPDATE payments SET amount=30,is_paid=1,paid_date=123,due_date=100")
            execSQL("INSERT INTO prices(id,item_id,type,total_price,created_at) VALUES(2,1,'FULL',250,12)")
            execSQL("INSERT INTO payments(id,price_id,amount,is_paid,due_date,created_at,reminder_set) VALUES(2,2,250,0,200,13,0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 21, true, LolitaDatabase.MIGRATION_20_21)
        try {
            db.query("SELECT COUNT(*) FROM order_bindings").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM prices").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM payments").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            db.query("SELECT amount,paid_date,is_paid,stage_id FROM payments WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals(30.0,it.getDouble(0),0.0); assertEquals(123L,it.getLong(1)); assertEquals(1,it.getInt(2)); assertEquals(1L,it.getLong(3))
            }
            db.query("SELECT COUNT(*) FROM payment_stages").use { assertTrue(it.moveToFirst()); assertEquals(2,it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationKeepsUncertainHistoricalPricesAndUnknownActualDates() {
        val name = "unknown_purchase_date_migration"
        context.deleteDatabase(name)
        helper.createDatabase(name, 13).apply {
            execSQL("PRAGMA foreign_keys=ON")
            listOf("brands", "categories", "coordinates", "items", "prices").forEach { seed(this, it) }
            seed(this, "items", 2)
            seed(this, "prices", 2)
            seed(this, "items", 3)
            seed(this, "prices", 3)
            execSQL("UPDATE items SET status='WISHED' WHERE id=2")
            execSQL("UPDATE prices SET item_id=2 WHERE id=2")
            execSQL("UPDATE prices SET item_id=3,total_price=1.005 WHERE id=3")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 23, true, *migrations)
        try {
            db.query("SELECT COUNT(*) FROM payments").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
            db.query("SELECT item_id FROM order_bindings").use {
                assertTrue(it.moveToFirst()); assertEquals(1L,it.getLong(0)); assertFalse(it.moveToNext())
            }
            db.query("SELECT status FROM items WHERE id=2").use { assertTrue(it.moveToFirst()); assertEquals("WISHED",it.getString(0)) }
            db.query("SELECT COUNT(*) FROM prices").use { assertTrue(it.moveToFirst()); assertEquals(3,it.getInt(0)) }
            db.query("SELECT purchase_record_status FROM order_bindings WHERE item_id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("HISTORICAL_OWNED",it.getString(0))
            }
            db.query("SELECT total_price FROM prices WHERE id=3").use { assertTrue(it.moveToFirst()); assertEquals(1.005,it.getDouble(0),0.0) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun fixture(image: String): BackupData = BackupData(
        brands = listOf(Brand(id = 101, name = "品牌", createdAt = 100)),
        categories = listOf(Category(id = 307, name = "分类", createdAt = 101)),
        coordinates = emptyList(),
        items = listOf(Item(id = 9001, name = "服饰", description = "中文,\"备注\"\n换行", brandId = 101, categoryId = 307,
            status = ItemStatus.PENDING_PAYMENT, imageUrls = listOf(image, image), sizeChartImageUrl = image, source = "来源", coordinateOrder = 7, createdAt = 102, updatedAt = 103)),
        orderBindings = listOf(OrderBinding(itemId = 9001, priceId = 8)),
        paymentStages = listOf(PaymentStage(id = 9, priceId = 8, name = "Legacy", amount = 1234.56, dueDate = 105)),
        prices = listOf(Price(id = 8, itemId = 9001, type = PriceType.FULL, totalPrice = 1234.56, createdAt = 104)),
        payments = listOf(Payment(id = 9, priceId = 8, stageId = 9, amount = 1234.56, dueDate = 105, createdAt = 106)),
        outfitLogs = listOf(OutfitLog(id = 10, date = 107, note = "日志", imageUrls = listOf(image), createdAt = 108, updatedAt = 109)),
        outfitItemCrossRefs = listOf(OutfitItemCrossRef(outfitLogId = 10, itemId = 9001)),
        remoteBrands = listOf(RemoteBrand("remote", "远程", image, 110)),
        legacyCatalogArchives = listOf(LegacyCatalogArchive("catalog_entries", "22", """{"id":22,"name":"图鉴","linked_item_id":9001,"image_urls":${Gson().toJson(Gson().toJson(listOf(image)))}}""")),
        backupDate = 111
    )

    private fun jsonFile(data: BackupData): File = File.createTempFile("integrity_input", ".json", context.cacheDir).apply { writeText(Gson().toJson(data)) }
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    @Test fun zipRoundTripPreservesFieldsSharedImagesArchiveAndRemoteData() = runBlocking {
        val source = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val image = File.createTempFile("integrity_image", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(1, 7, 23, 42)) }
        val input = jsonFile(fixture(image.absolutePath))
        var output: Uri? = null
        val restoredImages = mutableSetOf<String>()
        try {
            val a = BackupManager(context, source); val b = BackupManager(context, target)
            a.importBackup(Uri.fromFile(input)).getOrThrow()
            output = a.exportToZip().getOrThrow()
            b.previewBackup(output).getOrThrow()
            b.importBackup(output).getOrThrow()
            val actual = target.itemDao().getItemById(9001)!!
            restoredImages.addAll(actual.imageUrls)
            assertEquals(fixture(image.absolutePath).items.single().copy(imageUrls = actual.imageUrls, sizeChartImageUrl = actual.sizeChartImageUrl), actual)
            assertEquals(actual.imageUrls[0], actual.imageUrls[1])
            assertEquals(actual.imageUrls[0], actual.sizeChartImageUrl)
            assertEquals(digest(image), digest(File(actual.imageUrls[0])))
            assertEquals(fixture(image.absolutePath).prices, target.priceDao().getAllPricesList())
            assertEquals(fixture(image.absolutePath).payments, target.paymentDao().getAllPaymentsList())
            assertEquals(fixture(image.absolutePath).outfitItemCrossRefs, target.outfitLogDao().getAllOutfitItemCrossRefsList())
            assertEquals(actual.imageUrls[0], target.sharedLibrarySyncDao().getAllBrandsList().single().logoUrl)
            val archive = JsonParser.parseString(target.legacyCatalogArchiveDao().getAll().single().payload).asJsonObject
            assertEquals(9001L, archive["linked_item_id"].asLong)
            assertEquals(listOf(actual.imageUrls[0]), Gson().fromJson(archive["image_urls"].asString, Array<String>::class.java).toList())
            assertTrue(image.exists())
        } finally {
            source.close(); target.close(); image.delete(); input.delete()
            output?.let { context.contentResolver.delete(it, null, null) }
            restoredImages.forEach { File(it).delete() }
        }
    }

    @Test fun failedTransactionKeepsExistingDatabaseAndImagesUsable() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val image = File.createTempFile("integrity_existing", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(7, 9)) }
        val original = fixture(image.absolutePath)
        val first = jsonFile(original)
        val invalid = jsonFile(original.copy(brands = original.brands + original.brands.single().copy(id = 102)))
        try {
            val manager = BackupManager(context, db)
            manager.importBackup(Uri.fromFile(first)).getOrThrow()
            assertTrue(manager.importBackup(Uri.fromFile(invalid)).isFailure)
            assertEquals(original.items.single(), db.itemDao().getItemById(9001))
            assertEquals(original.payments, db.paymentDao().getAllPaymentsList())
            assertTrue(image.exists())
            assertEquals(1, db.itemDao().getItemCount())
        } finally { db.close(); image.delete(); first.delete(); invalid.delete() }
    }

    @Test fun changedPreviewAndMissingImagesAreRejectedWithoutReplacingData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val image = File.createTempFile("integrity_missing", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(1)) }
        val data = fixture(image.absolutePath); val input = jsonFile(data)
        try {
            val manager = BackupManager(context, db)
            manager.previewBackup(Uri.fromFile(input)).getOrThrow()
            input.writeText(Gson().toJson(data.copy(backupDate = 999)))
            assertTrue(manager.importBackup(Uri.fromFile(input)).isFailure)
            assertEquals(0, db.itemDao().getItemCount())
            manager.importBackup(Uri.fromFile(input)).getOrThrow()
            image.delete()
            assertTrue(manager.exportToZip().isFailure)
            assertEquals(1, db.itemDao().getItemCount())
        } finally { db.close(); image.delete(); input.delete() }
    }

    private fun modifiedZip(uri: Uri, transform: (String, ByteArray) -> ByteArray): File {
        val file = File.createTempFile("integrity_modified", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { out ->
            ZipInputStream(context.contentResolver.openInputStream(uri)).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val name = entry.name
                    out.putNextEntry(ZipEntry(name))
                    out.write(transform(name, input.readBytes()))
                    out.closeEntry(); input.closeEntry(); entry = input.nextEntry
                }
            }
        }
        return file
    }

    @Test fun rejectsTamperedZipAndRollsBackPromotedImagesOnSqlFailure() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val image = File.createTempFile("integrity_zip", ".png", context.cacheDir).apply { writeBytes(byteArrayOf(3, 4)) }
        val input = jsonFile(fixture(image.absolutePath))
        val files = mutableListOf<File>(); var zip: Uri? = null
        try {
            val manager = BackupManager(context, db)
            manager.importBackup(Uri.fromFile(input)).getOrThrow()
            zip = manager.exportToZip().getOrThrow()
            val badHash = modifiedZip(zip) { name, bytes -> if (name.startsWith("images/")) byteArrayOf(99) else bytes }.also { files.add(it) }
            assertTrue(manager.importBackup(Uri.fromFile(badHash)).isFailure)
            val invalid = modifiedZip(zip) { name, bytes ->
                if (name == "data.json") {
                    val data = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                    val duplicate = data["brands"].asJsonArray[0].deepCopy().asJsonObject
                    duplicate.addProperty("id", 102)
                    data["brands"].asJsonArray.add(duplicate)
                    data.toString().toByteArray()
                } else bytes
            }.also { files.add(it) }
            val directory = File(context.filesDir, "images")
            val before = directory.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            assertTrue(manager.importBackup(Uri.fromFile(invalid)).isFailure)
            assertEquals(before, directory.listFiles()?.map { it.name }?.toSet() ?: emptySet<String>())
            assertEquals(fixture(image.absolutePath).items.single(), db.itemDao().getItemById(9001))
            assertTrue(image.exists())
            assertNull(db.restoreJournalDao().get())
            db.openHelper.writableDatabase.execSQL("UPDATE items SET colors='[broken'")
            assertTrue(manager.exportToJson().isFailure)
        } finally {
            db.close(); image.delete(); input.delete(); files.forEach { it.delete() }
            zip?.let { context.contentResolver.delete(it, null, null) }
        }
    }

    @Test fun sharedImageRemainsUntilLastReferenceIsRemoved() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val image = File.createTempFile("integrity_shared", ".png", dir).apply { writeBytes(byteArrayOf(5)) }
        try {
            ImageFileHelper.configure(context, db)
            db.brandDao().insertBrand(Brand(id = 1, name = "甲", logoUrl = image.absolutePath))
            db.brandDao().insertBrand(Brand(id = 2, name = "乙", logoUrl = image.absolutePath))
            ImageFileHelper.deleteImage(image.absolutePath)
            assertTrue(image.exists())
            db.brandDao().deleteAllBrands()
            ImageFileHelper.deleteImage(image.absolutePath)
            assertFalse(image.exists())
        } finally {
            ImageFileHelper.configure(context, AppModule.database())
            db.close(); image.delete()
        }
    }
}
