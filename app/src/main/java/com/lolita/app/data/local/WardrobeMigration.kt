package com.lolita.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive upgrade: no old Item, Location, order, payment or image row is rewritten. */
internal object WardrobeMigration : Migration(29,30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN category_semantic TEXT NOT NULL DEFAULT 'UNCONFIRMED'")
        db.execSQL("ALTER TABLE categories ADD COLUMN default_wear_role TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE brands ADD COLUMN system_key TEXT DEFAULT NULL")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_brands_system_key ON brands(system_key)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS storage_nodes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, location_id INTEGER NOT NULL,
            parent_node_id INTEGER, name TEXT NOT NULL, normalized_name TEXT NOT NULL,
            sibling_scope_id INTEGER NOT NULL, description TEXT NOT NULL, image_url TEXT,
            sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            FOREIGN KEY(location_id) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(parent_node_id) REFERENCES storage_nodes(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_storage_nodes_location_id ON storage_nodes(location_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_storage_nodes_parent_node_id ON storage_nodes(parent_node_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_storage_nodes_location_id_sibling_scope_id_normalized_name ON storage_nodes(location_id,sibling_scope_id,normalized_name)")
        db.execSQL("ALTER TABLE items ADD COLUMN specifications_json TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE items ADD COLUMN composition TEXT NOT NULL DEFAULT 'UNCONFIRMED'")
        db.execSQL("ALTER TABLE items ADD COLUMN storage_node_id INTEGER DEFAULT NULL REFERENCES storage_nodes(id) ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_items_storage_node_id ON items(storage_node_id)")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN keep_source_images INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE image_tasks ADD COLUMN source_resources_missing INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE try_on_results ADD COLUMN request_snapshot_json TEXT DEFAULT NULL")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_drafts (
            id TEXT NOT NULL PRIMARY KEY, target_item_id INTEGER, data_json TEXT NOT NULL,
            image_paths TEXT NOT NULL DEFAULT '[]', size_chart_image_paths TEXT NOT NULL DEFAULT '[]',
            evidence_image_paths TEXT NOT NULL DEFAULT '[]', appearance_only_image_paths TEXT NOT NULL DEFAULT '[]', updated_at INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_drafts_target_item_id ON item_drafts(target_item_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS recognition_tasks (
            id TEXT NOT NULL PRIMARY KEY,draft_id TEXT NOT NULL,target_item_id INTEGER,
            request_json TEXT NOT NULL,region_image_paths TEXT NOT NULL DEFAULT '[]',status TEXT NOT NULL,
            request_sent INTEGER NOT NULL DEFAULT 0,result_json TEXT,review_json TEXT,error TEXT,
            created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,actual_model TEXT DEFAULT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_tasks_draft_id ON recognition_tasks(draft_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_tasks_target_item_id ON recognition_tasks(target_item_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS item_evidence (
            id TEXT NOT NULL PRIMARY KEY,item_id INTEGER,draft_id TEXT NOT NULL,field_key TEXT NOT NULL,
            candidate_json TEXT NOT NULL,region_image_paths TEXT NOT NULL DEFAULT '[]',created_at INTEGER NOT NULL,
            FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_evidence_item_id ON item_evidence(item_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_evidence_draft_id ON item_evidence(draft_id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS image_provenance (
            image_path TEXT NOT NULL,item_id INTEGER NOT NULL,source_type TEXT NOT NULL,mode TEXT NOT NULL,
            task_id TEXT,created_at INTEGER NOT NULL,PRIMARY KEY(image_path,item_id),
            FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_image_provenance_item_id ON image_provenance(item_id)")
    }
}
