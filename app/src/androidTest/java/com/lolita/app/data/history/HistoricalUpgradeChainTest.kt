package com.lolita.app.data.history

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.*
import com.lolita.app.data.repository.*
import com.lolita.app.ui.screen.square.SquareScreen
import com.lolita.app.ui.screen.square.SquareViewModel
import com.lolita.app.ui.theme.LolitaTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Unique on-disk databases, actual Room upgrades, DAO reads and actual JSON exports. */
class HistoricalUpgradeChainTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext
    @get:Rule val helper = MigrationTestHelper(inst, LolitaDatabase::class.java)
    @get:Rule val compose = createComposeRule()
    private val gson = Gson()
    private val color = "粉\"色\\绀\n色"
    private val note = "多次升级保留的原说明\n第二行"
    private val displayed = mutableStateOf<SquareViewModel?>(null)
    private var contentInstalled = false
    private val current = arrayOf(
        LolitaDatabase.MIGRATION_1_2, LolitaDatabase.MIGRATION_2_3,
        LolitaDatabase.MIGRATION_3_4, LolitaDatabase.MIGRATION_4_5,
        LolitaDatabase.MIGRATION_5_6,
        LolitaDatabase.MIGRATION_6_7, LolitaDatabase.MIGRATION_7_8,
        LolitaDatabase.MIGRATION_8_9, LolitaDatabase.MIGRATION_9_10,
        LolitaDatabase.MIGRATION_10_11, LolitaDatabase.MIGRATION_11_12,
        LolitaDatabase.MIGRATION_12_13, LolitaDatabase.MIGRATION_13_14,
        LolitaDatabase.MIGRATION_14_15, LolitaDatabase.MIGRATION_15_16,
        LolitaDatabase.MIGRATION_16_17, LolitaDatabase.MIGRATION_17_18,
        LolitaDatabase.MIGRATION_18_19, LolitaDatabase.MIGRATION_19_20,
        LolitaDatabase.MIGRATION_20_21, LolitaDatabase.MIGRATION_21_22,
        LolitaDatabase.MIGRATION_22_23, LolitaDatabase.MIGRATION_23_24,
        LolitaDatabase.MIGRATION_24_25, LolitaDatabase.MIGRATION_25_26,
        LolitaDatabase.MIGRATION_26_27, LolitaDatabase.MIGRATION_27_28,
        LolitaDatabase.MIGRATION_28_29, LolitaDatabase.MIGRATION_29_30,
        LolitaDatabase.MIGRATION_30_31, LolitaDatabase.MIGRATION_31_32,
        MIGRATION_32_33, AfterSaleMigration.MIGRATION_33_34
    )

    private fun entities(version: Int) = inst.context.assets
        .open("com.lolita.app.data.local.LolitaDatabase/$version.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject["database"].asJsonObject["entities"]
                .asJsonArray.map { row -> row.asJsonObject }.associateBy { row -> row["tableName"].asString }
        }

    private fun seed(db: SupportSQLiteDatabase, version: Int, path: String) {
        val schema = entities(version)
        fun insert(table: String, overrides: Map<String, Any?> = emptyMap()) {
            val fields = schema.getValue(table)["fields"].asJsonArray.map { it.asJsonObject }
            val columns = fields.map { it["columnName"].asString }
            val values = fields.map { field ->
                val column = field["columnName"].asString
                if (overrides.containsKey(column)) overrides[column] else when {
                    column in setOf("image_urls", "colors") -> gson.toJson(listOf(if (column == "colors") color else path))
                    column == "size_chart_image_urls" -> "[]"
                    column == "composition" || column == "category_semantic" -> "UNCONFIRMED"
                    column == "specifications_json" -> ""
                    column == "purchase_record_status" -> "NEEDS_REVIEW"
                    column == "shipping" -> 0.0
                    column == "image_url" -> path
                    column == "color" -> color
                    column == "description" || column == "note" -> note
                    column == "name" -> "原$table"
                    column == "category_group" -> "CLOTHING"
                    column == "priority" -> "MEDIUM"
                    column == "status" -> "OWNED"
                    column == "type" -> "FULL"
                    field["notNull"]?.asBoolean != true -> null
                    field["affinity"].asString == "REAL" -> 123.45
                    field["affinity"].asString == "INTEGER" -> if (column in setOf("is_paid", "reminder_set", "is_preset", "coordinate_order", "sort_order", "included_in_parent_price")) 0L else 1L
                    else -> "原$column"
                }
            }.toTypedArray()
            db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})", values)
        }
        insert("brands"); insert("categories"); insert("coordinates")
        if ("locations" in schema) insert("locations")
        (1L..5L).forEach { id ->
            insert("items", mapOf("id" to id, "name" to "原物品$id", "status" to if (id == 2L) "WISHED" else "OWNED"))
            insert("prices", mapOf("id" to id, "item_id" to id, "total_price" to if (id == 5L) -3.21 else 123.45))
        }
        insert("payments", mapOf("id" to 10L, "price_id" to 3L, "amount" to 50.0, "is_paid" to 1L, "paid_date" to null))
        insert("outfit_logs")
        insert("outfit_item_cross_ref", mapOf("item_id" to 1L, "outfit_log_id" to 1L))
    }

    private fun runChain(start: Int, stops: List<Int>, migrations: Array<Migration>, openService: Boolean = false, damagedLegacyColor: Boolean = false, legacyV12ColorColumn: Boolean = false) = runBlocking {
        val name = "historical-chain-${UUID.randomUUID()}"
        val root = File(target.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val image = File(File(context.filesDir, "images").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(0xff9988aa.toInt())
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(image.readBytes()).toList()
        var db: LolitaDatabase? = null
        val exports = mutableListOf<android.net.Uri>()
        val store = ViewModelStore()
        try {
            val brokenColors = "[\\\"粉色\\\"]" // A historical invalid JSON spelling, NOT today's encoder.
            helper.createDatabase(name, if (start == 1) 2 else start).use {
                seed(it, if (start == 1) 2 else start, image.path)
                if (start == 1) {
                    // No Room v1 export survives in Git. The exact entity diff f5a2cf1c ->
                    // 9a36ae8c adds only these four fields; derive v1 from the checked-in v2.
                    listOf("color", "season", "style").forEach { column -> it.execSQL("ALTER TABLE items DROP COLUMN `$column`") }
                    it.execSQL("ALTER TABLE prices DROP COLUMN purchase_date")
                    it.version = 1
                }
                if (damagedLegacyColor) it.execSQL("UPDATE items SET colors=? WHERE id=1", arrayOf(brokenColors))
                if (legacyV12ColorColumn) {
                    it.execSQL("ALTER TABLE items ADD COLUMN color TEXT")
                    it.execSQL("UPDATE items SET color=?, colors=?", arrayOf(color, brokenColors))
                }
            }
            var version = start
            for (stop in stops) {
                helper.runMigrationsAndValidate(name, stop, true, *migrations.filter { it.startVersion >= version && it.endVersion <= stop }.toTypedArray()).use { upgraded ->
                    assertEquals("items $version->$stop", 5, upgraded.query("SELECT COUNT(*) FROM items").use { it.moveToFirst(); it.getInt(0) })
                    assertEquals("prices $version->$stop", 5, upgraded.query("SELECT COUNT(*) FROM prices").use { it.moveToFirst(); it.getInt(0) })
                    assertEquals("payments $version->$stop", 1, upgraded.query("SELECT COUNT(*) FROM payments").use { it.moveToFirst(); it.getInt(0) })
                    assertFalse("FK $version->$stop", upgraded.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
                    // Rows written by intermediate feature versions must also survive later upgrades.
                    if (stop in setOf(28, 29)) {
                        val request = """{"version":${if (stop == 28) 1 else 2},"garments":[{"itemId":1,"name":"旧任务物品","role":"MAIN"}],"instructions":""${if (stop == 29) ",\"personalModel\":null,\"tryOnModel\":null" else ""}}"""
                        upgraded.execSQL("INSERT INTO image_tasks(id,kind,provider,title,request_json,source_image_paths,status,request_sent,created_at,updated_at) VALUES (?,'TRY_ON','OPENAI','旧试衣任务',?,'[]','COMPLETED',1,1,1)", arrayOf(UUID.randomUUID().toString(), request))
                    }
                    if (stop == 30) {
                        val body = """{"version":1,"name":"旧物品草稿","description":"保留未完成资料","brandId":1,"categoryId":1,"status":"WISHED","priority":"MEDIUM","colors":[],"seasons":[],"specificationsJson":"","composition":"UNCONFIRMED"}"""
                        upgraded.execSQL("INSERT INTO item_drafts(id,data_json,image_paths,updated_at) VALUES (?,?,?,1)", arrayOf(UUID.randomUUID().toString(), body, gson.toJson(listOf(image.path))))
                    }
                }
                version = stop
            }
            assertEquals(34, version)
            db = Room.databaseBuilder(context, LolitaDatabase::class.java, name).build()
            val actual = db.itemDao().getAllItemsList()
            assertEquals(5, actual.size)
            actual.forEach { item ->
                assertEquals(note, item.description)
                if (!damagedLegacyColor || item.id != 1L) assertEquals(if (start == 1) emptyList<String>() else listOf(color), item.colors)
                assertEquals(listOf(image.path), item.imageUrls)
                assertEquals("UNCONFIRMED", item.composition.name)
                assertEquals("", item.specificationsJson)
            }
            assertNull(db.paymentDao().getAllPaymentsList().single().paidDate)
            assertEquals(-3.21, db.priceDao().getAllPricesList().single { it.id == 5L }.totalPrice, 0.0)
            val items = ItemRepository(db.itemDao(), database = db, context = context)
            val repository = SquareRepository(context, db, ItemDraftRepository(db, items), defaultServiceUrl = "")
            assertEquals(5, withTimeout(10_000) { repository.wardrobe.first() }.items.size)
            val manager = BackupManager(context, db)
            val result = manager.exportToJson()
            var exportedSha: String? = null
            if (damagedLegacyColor) {
                assertTrue("Bad history must be a caught failure, not a fabricated complete export", result.isFailure)
                assertTrue("Export must return a caught Exception", result.exceptionOrNull() is Exception)
                assertEquals(brokenColors, db.openHelper.readableDatabase.query("SELECT colors FROM items WHERE id=1").use { it.moveToFirst(); it.getString(0) })
            } else {
                val uri = result.getOrThrow().also(exports::add)
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                exportedSha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                assertEquals(5, json["items"].asJsonArray.size())
                assertEquals(1, json["payments"].asJsonArray.size())
                assertEquals(stops.count { it in setOf(28, 29) }, json["imageTasks"].asJsonArray.size())
                assertEquals(if (30 in stops) 1 else 0, json["itemDrafts"].asJsonArray.size())
                manager.previewBackup(uri).getOrThrow()
            }
            assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(image.readBytes()).toList())
            if (openService) {
                lateinit var vm: SquareViewModel
                inst.runOnMainSync { vm = SquareViewModel(repository, SavedStateHandle(), MutableStateFlow(false)); store.put(name, vm) }
                inst.runOnMainSync { displayed.value = vm }
                if (!contentInstalled) {
                    compose.setContent { LolitaTheme { Surface(Modifier.fillMaxSize()) { displayed.value?.let { model -> key(model) { SquareScreen({}, {}, model) } } } } }
                    contentInstalled = true
                }
                compose.onNodeWithTag("square-service").performClick()
                compose.onNodeWithTag("square-service-address").assertIsDisplayed().performTextReplacement("http://192.168.0.200:18765")
                compose.onNodeWithText("取消").performClick()
                compose.onNodeWithTag("square-service").assertIsDisplayed()
            }
            File(target.filesDir, "historical-upgrade-results").apply { mkdirs() }.let { directory ->
                File(directory, "from-$start-via-${stops.joinToString("-")}-damaged-$damagedLegacyColor-v12extra-$legacyV12ColorColumn.json").writeText(gson.toJson(mapOf(
                    "from" to start, "via" to stops, "to" to 34,
                    "sourceDerivedV1" to (start == 1), "releasedBodies" to (migrations === ReleasedUpgradeMigrations.all),
                    "damagedLegacyColor" to damagedLegacyColor, "exportSucceeded" to result.isSuccess,
                    "legacyV12ColorColumn" to legacyV12ColorColumn,
                    "exportSha256" to exportedSha, "squareServiceOpenedAndCancelled" to openService,
                    "itemsPreserved" to 5, "pricesPreserved" to 5, "paymentsPreserved" to 1,
                    "unknownPaidDatePreserved" to true, "imageSha256Preserved" to true
                )))
            }
        } finally {
            inst.runOnMainSync { displayed.value = null; store.clear() }
            if (contentInstalled) compose.waitForIdle()
            db?.close()
            exports.forEach { context.contentResolver.delete(it, null, null) }
            target.deleteDatabase(name); root.deleteRecursively()
        }
    }

    @Test fun currentMigrationsFrom6ReopenAtEveryHistoricalMilestoneAndExportJson() =
        runChain(6, listOf(10, 13, 15, 18, 20, 23, 26, 29, 30, 31, 32, 33, 34), current)

    @Test fun released20Through34BodiesReopenEachVersionExportJsonAndOpenSquareService() =
        runChain(20, (21..34).toList(), ReleasedUpgradeMigrations.all, openService = true)

    @Test fun releasedMultiHopGroupsReachSameReadableJsonEndpoint() =
        runChain(20, listOf(23, 26, 30, 34), ReleasedUpgradeMigrations.all)

    @Test fun alreadyMigratedBadColorIsPreservedAndJsonFailsWithoutCrashingSquareService() =
        runChain(20, (21..34).toList(), ReleasedUpgradeMigrations.all, openService = true, damagedLegacyColor = true)

    @Test fun everyHistoricalSchemaDirectlyUpgradesTo34ExportsJsonAndOpensService() {
        val failures = mutableListOf<String>()
        for (start in 1..34) {
            try { runChain(start, listOf(34), current, openService = true) }
            catch (failure: AssertionError) { failures += "$start: ${failure.javaClass.simpleName}" }
            catch (failure: Exception) { failures += "$start: ${failure.javaClass.simpleName}" }
        }
        assertTrue("Failed direct starting schemas: $failures", failures.isEmpty())
    }

    @Test fun historicalV12WithExtraOldColorColumnRepairsAndDirectlyUpgradesTo34() =
        runChain(12, listOf(34), current, openService = true, legacyV12ColorColumn = true)
}
