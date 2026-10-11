package com.lolita.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** Room already wraps migrations in a transaction. Preserve children of rebuilt parents. */
internal object MigrationDataGuard {
    private val children = listOf("prices", "payments", "outfit_item_cross_ref", "catalog_entries")

    fun snapshotChildren(db: SupportSQLiteDatabase): List<String> = children.filter { table ->
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            it.moveToFirst()
        }
    }.also { tables ->
        tables.forEach { snapshot(db, it) }
    }

    fun snapshot(db: SupportSQLiteDatabase, table: String) {
        db.execSQL("CREATE TEMP TABLE `integrity_$table` AS SELECT * FROM `$table`")
    }

    fun restoreChildren(db: SupportSQLiteDatabase, tables: List<String>) {
        tables.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
        tables.forEach { restore(db, it) }
    }

    fun restore(db: SupportSQLiteDatabase, table: String) {
        // Some upgrade connections have FK enforcement disabled. DROP may leave children alive.
        db.execSQL("DELETE FROM `$table`")
        db.execSQL("INSERT INTO `$table` SELECT * FROM `integrity_$table`")
        db.execSQL("DROP TABLE `integrity_$table`")
    }
}
