package com.lolita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lolita.app.data.local.entity.ItemDraft
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDraftDao {
    @Query("SELECT * FROM item_drafts ORDER BY updated_at DESC") fun observeDrafts(): Flow<List<ItemDraft>>
    @Query("SELECT * FROM item_drafts ORDER BY updated_at DESC") suspend fun getAll(): List<ItemDraft>
    @Query("SELECT * FROM item_drafts WHERE id=:id") suspend fun get(id: String): ItemDraft?
    @Query("SELECT * FROM item_drafts WHERE target_item_id=:itemId LIMIT 1") suspend fun getForItem(itemId: Long): ItemDraft?
    @Upsert suspend fun save(draft: ItemDraft)
    @Query("DELETE FROM item_drafts WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM item_drafts") suspend fun deleteAll()
}
