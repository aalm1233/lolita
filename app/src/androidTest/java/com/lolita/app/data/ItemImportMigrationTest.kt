package com.lolita.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteConstraintException
import com.lolita.app.data.local.LolitaDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ItemImportMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)

    private fun rows(db: SupportSQLiteDatabase, columns: List<String>? = null): List<Map<String, String?>> =
        db.query("SELECT * FROM items ORDER BY id").use { cursor ->
            val names = columns ?: cursor.columnNames.toList()
            buildList {
                while (cursor.moveToNext()) {
                    add(names.associateWith { name ->
                        val index = cursor.getColumnIndexOrThrow(name)
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    })
                }
            }
        }

    @Test fun upgradePreservesEveryOldItemFieldAndAddsNullableUniqueImportReceipts() {
        val name = "item-import-migration-${UUID.randomUUID()}"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val original = helper.createDatabase(name, 25)
            val before: List<Map<String, String?>>
            try {
                original.execSQL("INSERT INTO brands (id,name,created_at,is_preset) VALUES (101,'迁移品牌',1,0)")
                original.execSQL("INSERT INTO categories (id,name,created_at,is_preset,category_group) VALUES (307,'OP',2,0,'CLOTHING')")
                for (id in 1L..2L) {
                    original.execSQL("""INSERT INTO items
                        (id,brand_id,category_id,created_at,updated_at,name,description,status,image_urls,size_chart_image_url,size_chart_image_urls)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                        arrayOf(id, 101L, 307L, 3L, 4L, "旧服饰$id", "原详情$id", "WISHED",
                            "[\"原主图$id\"]", "原尺码首图$id", "[\"原尺码后图$id\"]"))
                }
                before = rows(original)
            } finally { original.close() }
            val migrated = helper.runMigrationsAndValidate(name, 26, true, LolitaDatabase.MIGRATION_25_26)
            try {
                assertEquals(before, rows(migrated, before.first().keys.toList()))
                migrated.query("SELECT COUNT(*) FROM items WHERE import_token IS NULL").use {
                    assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
                }
                migrated.execSQL("UPDATE items SET import_token='batch:0' WHERE id=1")
                assertThrows(SQLiteConstraintException::class.java) {
                    migrated.execSQL("UPDATE items SET import_token='batch:0' WHERE id=2")
                }
                migrated.execSQL("UPDATE items SET import_token='batch:1' WHERE id=2")
                assertEquals(before, rows(migrated, before.first().keys.toList()))
                migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                migrated.query("PRAGMA integrity_check").use {
                    assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
                }
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
