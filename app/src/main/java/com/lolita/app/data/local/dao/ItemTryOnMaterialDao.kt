package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.ItemTryOnMaterial
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemTryOnMaterialDao {
    @Query("SELECT * FROM item_try_on_materials") suspend fun getAll(): List<ItemTryOnMaterial>
    @Query("SELECT * FROM item_try_on_materials") fun observeAll(): Flow<List<ItemTryOnMaterial>>
    @Query("SELECT * FROM item_try_on_materials WHERE item_id=:itemId") suspend fun getForItem(itemId: Long): ItemTryOnMaterial?
    @Query("SELECT * FROM item_try_on_materials WHERE item_id=:itemId") fun observe(itemId: Long): Flow<ItemTryOnMaterial?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(material: ItemTryOnMaterial)
    @Query("DELETE FROM item_try_on_materials WHERE item_id=:itemId") suspend fun deleteForItem(itemId: Long)
    @Query("DELETE FROM item_try_on_materials") suspend fun deleteAll()
}
