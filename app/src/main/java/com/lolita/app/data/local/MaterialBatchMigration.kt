package com.lolita.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MaterialBatchMigration : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN batch_id TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN batch_name TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN batch_position INTEGER DEFAULT NULL")
    }
}
