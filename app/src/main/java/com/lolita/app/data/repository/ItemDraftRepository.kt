package com.lolita.app.data.repository

import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemDraft
import com.lolita.app.data.local.entity.registrationCopy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ItemDraftRepository(private val database: LolitaDatabase, private val itemRepository: ItemRepository) {
    fun observeDrafts() = database.itemDraftDao().observeDrafts()
    suspend fun get(id: String) = database.itemDraftDao().get(id)
    suspend fun getForItem(itemId: Long) = database.itemDraftDao().getForItem(itemId)
    suspend fun latestNew() = database.itemDraftDao().getAll().firstOrNull { it.targetItemId == null }
    suspend fun copyFromItem(itemId: Long): String = database.withTransaction {
        val item = database.itemDao().getItemById(itemId) ?: error("物品已删除，无法复制")
        val draft = ItemDraft(dataJson = item.registrationCopy().encode(), imagePaths = item.imageUrls,
            sizeChartImagePaths = listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls)
        draft.validate()
        database.itemDraftDao().save(draft)
        draft.id
    }
    suspend fun save(draft: ItemDraft) {
        draft.validate()
        database.withTransaction { database.itemDraftDao().save(draft) }
    }
    suspend fun commit(draftId: String, item: Item): Long = database.withTransaction {
        val draft = database.itemDraftDao().get(draftId) ?: error("物品草稿已不存在")
        require(draft.targetItemId == null || draft.targetItemId == item.id) { "物品草稿目标已变化" }
        val id = if (item.id > 0) { itemRepository.updateItem(item); item.id } else itemRepository.insertItem(item)
        database.itemDraftDao().delete(draftId)
        id
    }
    suspend fun discard(id: String) = withContext(NonCancellable) {
        val paths = database.withTransaction {
            database.itemDraftDao().get(id)?.protectedImagePaths().orEmpty().also { database.itemDraftDao().delete(id) }
        }
        ImageFileHelper.cleanupUnreferencedImages(paths)
    }
}
