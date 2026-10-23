package com.lolita.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.AfterSaleMigration
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class AfterSaleMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, LolitaDatabase::class.java)
    private fun snapshot(db: SupportSQLiteDatabase, entities: List<JsonObject>) = entities.associate { e ->
        val columns = e["fields"].asJsonArray.map { it.asJsonObject["columnName"].asString }
        val table = e["tableName"].asString
        table to db.query("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table` ORDER BY rowid").use { cursor -> buildList {
            while (cursor.moveToNext()) add(columns.indices.map { if (cursor.isNull(it)) null else "${cursor.getType(it)}:${cursor.getString(it)}" })
        } }
    }
    @Test fun upgrade33To34PreservesEveryOldTableAndAddsOnlyEmptyAfterSaleTables() {
        val name = "after-sale-upgrade-${UUID.randomUUID()}"
        val entities = instrumentation.context.assets.open("com.lolita.app.data.local.LolitaDatabase/33.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject["database"].asJsonObject["entities"].asJsonArray.map { e -> e.asJsonObject }
        }
        val before: Map<String, List<List<String?>>>
        try {
            helper.createDatabase(name, 33).use { db ->
                val pending = entities.toMutableList(); val rows = mutableMapOf<String, Map<String, Any?>>()
                while (pending.isNotEmpty()) {
                    val e = pending.first { candidate -> candidate["foreignKeys"]?.asJsonArray?.toList().orEmpty().all { fk ->
                        fk.asJsonObject["table"].asString.let { it == candidate["tableName"].asString || it in rows }
                    } }
                    val table = e["tableName"].asString
                    val values = e["fields"].asJsonArray.associate { element ->
                        val field = element.asJsonObject; val column = field["columnName"].asString
                        val value: Any? = when {
                            field["notNull"]?.asBoolean != true -> null
                            field["affinity"].asString == "INTEGER" -> 1001L
                            field["affinity"].asString == "REAL" -> 123.45
                            else -> "旧资料:$table:$column"
                        }
                        column to value
                    }.toMutableMap()
                    e["foreignKeys"]?.asJsonArray?.forEach { element ->
                        val fk = element.asJsonObject; val parent = fk["table"].asString
                        fk["columns"].asJsonArray.forEachIndexed { index, c ->
                            val column = c.asString
                            if (parent != table) values[column] = rows.getValue(parent)[fk["referencedColumns"].asJsonArray[index].asString]
                        }
                    }
                    val columns = values.keys.toList()
                    db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})", columns.map { values[it] }.toTypedArray())
                    rows[table] = values; pending.remove(e)
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                before = snapshot(db, entities); assertTrue(before.values.all { it.isNotEmpty() })
            }
            helper.runMigrationsAndValidate(name, 34, true, AfterSaleMigration.MIGRATION_33_34).use { db ->
                assertEquals(before, snapshot(db, entities))
                for (table in listOf("item_reference_prices", "order_refunds", "order_cancellations")) db.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            }
        } finally { instrumentation.targetContext.deleteDatabase(name) }
    }
}
