package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Lossless rows from retired catalog tables, including original keys and links. */
@Entity(tableName = "legacy_catalog_archive", primaryKeys = ["source_table", "record_key"])
data class LegacyCatalogArchive(
    @ColumnInfo(name = "source_table") val sourceTable: String,
    @ColumnInfo(name = "record_key") val recordKey: String,
    @ColumnInfo(name = "payload") val payload: String
)
