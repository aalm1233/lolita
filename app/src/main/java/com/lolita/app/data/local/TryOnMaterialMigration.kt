package com.lolita.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TryOnMaterialMigration : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_try_on_materials (
            id TEXT NOT NULL PRIMARY KEY, item_id INTEGER NOT NULL, image_path TEXT NOT NULL,
            source_image_path TEXT NOT NULL, input_image_path TEXT NOT NULL, result_sha256 TEXT NOT NULL,
            source_task_id TEXT NOT NULL, request_json TEXT NOT NULL, created_at INTEGER NOT NULL,
            confirmed_at INTEGER NOT NULL, FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_try_on_materials_item_id ON item_try_on_materials(item_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_try_on_materials_source_task_id ON item_try_on_materials(source_task_id)")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN material_id TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN layers_json TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN generated_image_count INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE try_on_results ADD COLUMN base_snapshot_image_path TEXT DEFAULT NULL")
    }
}
