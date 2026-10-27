package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.ImageProvenance
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageProvenanceDao {
    @Query("SELECT * FROM image_provenance") fun observeAll(): Flow<List<ImageProvenance>>
    @Query("SELECT * FROM image_provenance") suspend fun getAll(): List<ImageProvenance>
    @Query("SELECT * FROM image_provenance WHERE item_id=:id") suspend fun getForItem(id: Long): List<ImageProvenance>
    @Query("SELECT * FROM image_provenance WHERE item_id=:id") fun observe(id: Long): Flow<List<ImageProvenance>>
    @Query("SELECT * FROM image_provenance WHERE item_id=:id AND image_path=:path") suspend fun get(id: Long, path: String): ImageProvenance?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(value: ImageProvenance)
    @Query("DELETE FROM image_provenance WHERE item_id=:itemId AND image_path=:path") suspend fun delete(itemId: Long, path: String)
    @Query("DELETE FROM image_provenance") suspend fun deleteAll()
}
