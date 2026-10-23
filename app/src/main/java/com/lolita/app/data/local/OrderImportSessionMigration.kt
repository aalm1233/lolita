package com.lolita.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration: the original 36 tables are left intact. */
internal val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `order_import_sessions` (`id` TEXT NOT NULL PRIMARY KEY, `version` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `state` TEXT NOT NULL, `data_json` TEXT NOT NULL, `pending_operation_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `order_import_files` (`id` TEXT NOT NULL PRIMARY KEY, `session_id` TEXT NOT NULL, `name` TEXT NOT NULL, `sha256` TEXT NOT NULL, `state` TEXT NOT NULL, `data_json` TEXT NOT NULL, `problem` TEXT, FOREIGN KEY(`session_id`) REFERENCES `order_import_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_files_session_id` ON `order_import_files` (`session_id`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `order_import_rows` (`id` TEXT NOT NULL PRIMARY KEY, `session_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `source_key` TEXT NOT NULL, `data_json` TEXT NOT NULL, `image_paths` TEXT NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `order_import_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_rows_session_id` ON `order_import_rows` (`session_id`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_order_import_rows_session_id_position` ON `order_import_rows` (`session_id`,`position`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `order_import_commits` (`id` TEXT NOT NULL PRIMARY KEY, `session_id` TEXT, `revision` INTEGER NOT NULL, `snapshot_hash` TEXT NOT NULL, `item_ids` TEXT NOT NULL, `merged_count` INTEGER NOT NULL, `linked_item_ids` TEXT NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `order_import_sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_commits_session_id` ON `order_import_commits` (`session_id`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `order_import_receipts` (`id` TEXT NOT NULL PRIMARY KEY, `source_key` TEXT NOT NULL, `generation` INTEGER NOT NULL, `order_id` TEXT NOT NULL, `group_key` TEXT NOT NULL, `unit_ordinal` INTEGER NOT NULL, `source_snapshot` TEXT NOT NULL, `commit_id` TEXT NOT NULL, `item_id` INTEGER, `paid_cents` INTEGER NOT NULL, `shipping_cents` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`commit_id`) REFERENCES `order_import_commits`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_receipts_item_id` ON `order_import_receipts` (`item_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_receipts_commit_id` ON `order_import_receipts` (`commit_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_order_import_receipts_order_id` ON `order_import_receipts` (`order_id`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_order_import_receipts_source_key_generation` ON `order_import_receipts` (`source_key`,`generation`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `item_source_records` (`id` TEXT NOT NULL PRIMARY KEY, `item_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `public_url` TEXT, `snapshot_json` TEXT NOT NULL, `applied_fields` TEXT NOT NULL, `image_paths` TEXT NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_item_source_records_item_id` ON `item_source_records` (`item_id`)"
            )
        }
    }
