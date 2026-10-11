package com.lolita.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/** Encode through JSON rather than SQL concatenation, preserving quotes and control characters. */
internal object MigrationJsonLists {
    fun copySingleColumn(db: SupportSQLiteDatabase, source: String, target: String, old: String, new: String) {
        db.query("SELECT id, `$old` FROM `$source`").use { cursor ->
            while (cursor.moveToNext()) {
                val values = JsonArray()
                if (!cursor.isNull(1) && cursor.getString(1).isNotEmpty()) values.add(cursor.getString(1))
                db.execSQL("UPDATE `$target` SET `$new`=? WHERE id=?", arrayOf<Any>(values.toString(), cursor.getLong(0)))
            }
        }
    }

    fun repairColors(db: SupportSQLiteDatabase) {
        val hasColor = db.query("PRAGMA table_info(items)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(1) == "color") found = true
            found
        }
        db.query("SELECT id, colors, ${if (hasColor) "color" else "NULL"} FROM items").use { cursor ->
            while (cursor.moveToNext()) {
                val raw = if (cursor.isNull(1)) "[]" else cursor.getString(1).ifEmpty { "[]" }
                fun parse(value: String): JsonArray? = runCatching {
                    JsonParser.parseString(value).asJsonArray.also { array ->
                        require(array.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
                    }
                }.getOrNull()
                val repaired = parse(raw) ?: if (!cursor.isNull(2)) {
                    JsonArray().apply { if (cursor.getString(2).isNotEmpty()) add(cursor.getString(2)) }
                } else parse(raw.replace("\\\"", "\"")) ?: error("服饰 ${cursor.getLong(0)} 颜色损坏，拒绝丢失数据的升级")
                db.execSQL("UPDATE items SET colors=? WHERE id=?", arrayOf<Any>(repaired.toString(), cursor.getLong(0)))
            }
        }
    }
}
