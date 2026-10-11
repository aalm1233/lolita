package com.lolita.app.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonNull
import com.google.gson.JsonObject

internal object LegacyCatalogMigration {
    fun createArchive(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS legacy_catalog_archive (
            source_table TEXT NOT NULL, record_key TEXT NOT NULL, payload TEXT NOT NULL,
            PRIMARY KEY(source_table, record_key))""")
    }

    fun archive(db: SupportSQLiteDatabase, table: String, key: String) {
        db.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                val row = JsonObject()
                cursor.columnNames.forEachIndexed { index, name ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> row.add(name, JsonNull.INSTANCE)
                        Cursor.FIELD_TYPE_INTEGER -> row.addProperty(name, cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> row.addProperty(name, cursor.getDouble(index))
                        else -> row.addProperty(name, cursor.getString(index))
                    }
                }
                db.execSQL("INSERT INTO legacy_catalog_archive VALUES (?, ?, ?)",
                    arrayOf(table, cursor.getString(cursor.getColumnIndexOrThrow(key)), row.toString()))
            }
        }
    }
}
