package com.lolita.app.data

import android.graphics.Bitmap
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.WardrobeMigration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Populates every v29 table and snapshots every old column, including opaque historical JSON. */
class WardrobeMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, LolitaDatabase::class.java)
    private val gson = Gson()

    private fun snapshot(db: SupportSQLiteDatabase, entities: List<JsonObject>): Map<String, List<List<String?>>> =
        entities.associate { entity ->
            val table = entity["tableName"].asString
            val columns = entity["fields"].asJsonArray.map { it.asJsonObject["columnName"].asString }
            table to db.query("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table` ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(columns.indices.map { index ->
                    if (cursor.isNull(index)) null else "${cursor.getType(index)}:${cursor.getString(index)}"
                }) }
            }
        }

    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    @Test fun upgrade29To30PreservesEveryOldTableColumnRelationshipAndImageByte() {
        val context = instrumentation.targetContext
        val name = "wardrobe-upgrade-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        val images = (1..2).map { number ->
            File(directory, "${UUID.randomUUID()}.png").also { file ->
                val bitmap = Bitmap.createBitmap(8 + number, 12 + number, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(if (number == 1) 0xffaa5577.toInt() else 0xff5577aa.toInt())
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        val hashes = images.associate { it.path to hash(it) }
        val entities = instrumentation.context.assets.open("com.lolita.app.data.local.LolitaDatabase/29.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject["database"].asJsonObject["entities"].asJsonArray.map { row -> row.asJsonObject }
        }
        fun value(table: String, field: JsonObject): Any? {
            val column = field["columnName"].asString
            val affinity = field["affinity"].asString
            return when {
                column == "parent_item_id" -> null
                column == "try_on_result_id" -> null
                column == "included_in_parent_price" -> 0L
                column in setOf("image_urls", "source_image_paths", "size_chart_image_urls") -> gson.toJson(images.map { it.path })
                column in setOf("image_url", "logo_url", "size_chart_image_url", "result_image_path") -> images.first().path
                column == "colors" -> "[\"奶茶色\",\"蓝色\"]"
                column == "item_ids" -> "[\"1001\",\"1002\"]"
                column == "item_names" -> "[\"旧主件\",\"旧小物\"]"
                column == "category_group" -> "CLOTHING"
                column == "priority" -> "HIGH"
                column == "status" -> if (table == "items") "OWNED" else "COMPLETED"
                column in setOf("type", "price_type") -> "FULL"
                column == "purchase_record_status" -> "TRACKED"
                column == "provider" -> "OPENAI"
                column == "kind" -> "PERSONAL_MODEL"
                column == "month" -> "2026-09"
                column == "request_json" -> "{\"version\":1,\"historicalEvidence\":\"旧请求原样保留\"}"
                column == "data_json" -> "{\"historicalDraft\":\"旧草稿原样保留\"}"
                column == "previousPaymentsJson" -> "[]"
                column == "affectedPriceIdsJson" -> "[1001]"
                column == "source_table" -> "catalog_entries"
                column == "record_key" -> "1001"
                column == "payload" -> "{\"id\":1001,\"image_urls\":${gson.toJson(images.map { it.path })},\"description\":\"历史图鉴证据\"}"
                column == "size" -> "EU 37 / 定制 23～25 cm"
                column == "season" -> "春,秋"
                column == "style" -> "原风格"
                column == "source" -> "原来源"
                affinity == "INTEGER" -> when (column) {
                    "created_at", "date", "due_date", "paid_date" -> 1_700_000_000_123L
                    "updated_at", "last_synced_at" -> 1_700_000_000_456L
                    "is_paid", "request_sent", "reminder_set", "is_preset" -> 1L
                    "sort_order", "coordinate_order" -> 7L
                    "custom_reminder_days" -> 5L
                    else -> 1001L
                }
                affinity == "REAL" -> if (column == "shipping") 12.34 else 123.45
                column == "id" -> UUID.nameUUIDFromBytes(table.toByteArray()).toString()
                column == "public_id" -> "legacy-$table"
                column.endsWith("_public_id") -> "legacy-related-$column"
                else -> "原$table-$column"
            }
        }
        fun insert(db: SupportSQLiteDatabase, entity: JsonObject, overrides: Map<String, Any?> = emptyMap()) {
            val table = entity["tableName"].asString
            val fields = entity["fields"].asJsonArray.map { it.asJsonObject }
            val columns = fields.map { it["columnName"].asString }
            val values = fields.map { field -> val column = field["columnName"].asString
                if (overrides.containsKey(column)) overrides[column] else value(table, field) }
            db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})", values.toTypedArray())
        }
        try {
            val old = helper.createDatabase(name, 29)
            val before: Map<String, List<List<String?>>>
            try {
                val pending = entities.toMutableList()
                val inserted = mutableSetOf<String>()
                while (pending.isNotEmpty()) {
                    val next = pending.firstOrNull { entity -> entity["foreignKeys"]?.asJsonArray?.toList().orEmpty().all {
                        val parent = it.asJsonObject["table"].asString
                        parent == entity["tableName"].asString || parent in inserted
                    } } ?: error("旧 schema 外键依赖无法排序")
                    insert(old, next, if (next["tableName"].asString == "locations") mapOf("name" to "卧室") else emptyMap())
                    inserted.add(next["tableName"].asString); pending.remove(next)
                }
                val room = entities.single { it["tableName"].asString == "locations" }
                listOf("客厅", "衣帽间", "书房", "玄关", "储物间").forEachIndexed { index, title ->
                    insert(old, room, mapOf("id" to 1002L + index, "name" to title, "sort_order" to index.toLong()))
                }
                val item = entities.single { it["tableName"].asString == "items" }
                insert(old, item, mapOf("id" to 1002L, "name" to "旧小物", "parent_item_id" to 1001L,
                    "included_in_parent_price" to 1L, "import_token" to "旧队列:小物"))
                old.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                before = snapshot(old, entities)
                assertTrue(before.values.all { it.isNotEmpty() })
            } finally { old.close() }
            val upgraded = helper.runMigrationsAndValidate(name, 30, true, WardrobeMigration)
            try {
                assertEquals(before, snapshot(upgraded, entities))
                upgraded.query("SELECT COUNT(*) FROM storage_nodes").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                upgraded.query("SELECT COUNT(*) FROM items WHERE storage_node_id IS NULL AND specifications_json='' AND composition='UNCONFIRMED'").use {
                    assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
                }
                upgraded.query("SELECT COUNT(*) FROM categories WHERE category_semantic='UNCONFIRMED' AND default_wear_role IS NULL").use {
                    assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
                }
                upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                upgraded.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
                hashes.forEach { (path, expected) -> assertEquals(expected, hash(File(path))) }
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name); directory.deleteRecursively() }
    }
}
