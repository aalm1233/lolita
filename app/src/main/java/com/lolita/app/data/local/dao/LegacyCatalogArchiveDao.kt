package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lolita.app.data.local.entity.LegacyCatalogArchive

@Dao
interface LegacyCatalogArchiveDao {
    @Query("SELECT * FROM legacy_catalog_archive ORDER BY source_table, record_key")
    suspend fun getAll(): List<LegacyCatalogArchive>

    @Insert
    suspend fun insertAll(rows: List<LegacyCatalogArchive>)

    @Query("DELETE FROM legacy_catalog_archive")
    suspend fun deleteAll()
}
