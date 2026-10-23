package com.lolita.app.data.repository

import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.RecognitionEvidencePayload
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ItemDraftConflictException : IllegalStateException(
    "原物品已在其他页面更新，旧草稿已保留。请保留退出，或明确放弃旧草稿并重新读取最新照片、位置与资料。")

class ItemDraftTargetDeletedException : IllegalStateException("物品已删除，编辑草稿已关闭")

class ItemDraftRepository(private val database: LolitaDatabase, private val itemRepository: ItemRepository, private val beforeDiscard: suspend (String) -> Unit = {}) {
    fun observeDrafts() = database.itemDraftDao().observeDrafts()
    suspend fun get(id: String) = database.itemDraftDao().get(id)
    suspend fun getForItem(itemId: Long) = database.itemDraftDao().getForItem(itemId)
    suspend fun latestNew() = database.itemDraftDao().getAll().firstOrNull { it.targetItemId == null }
    suspend fun copyFromItem(itemId: Long): String = database.withTransaction {
        val item = database.itemDao().getItemById(itemId) ?: error("物品已删除，无法复制")
        val draft = ItemDraft(dataJson = item.registrationCopy().encode(), imagePaths = item.imageUrls,
            sizeChartImagePaths = listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls,
            appearanceOnlyImagePaths = knownAppearancePaths(item.imageUrls + listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls))
        draft.validate()
        database.itemDraftDao().save(draft)
        draft.id
    }
    suspend fun save(draft: ItemDraft) {
        database.withTransaction {
            requireTargetExists(draft.targetItemId)
            val paths=draft.imagePaths+draft.sizeChartImagePaths
            val known=knownAppearancePaths(paths)+database.itemDraftDao().get(draft.id)?.appearanceOnlyImagePaths.orEmpty()+draft.appearanceOnlyImagePaths
            val data=draft.data()
            val node=data.storageNodeId?.let { database.storageNodeDao().getById(it) }
            val roomId=node?.locationId ?: data.locationId?.takeIf { database.locationDao().getLocationByIdSync(it)!=null }
            val saved=draft.copy(dataJson=data.copy(locationId=roomId,storageNodeId=node?.id).encode(),
                appearanceOnlyImagePaths=paths.filter { it in known }.distinct())
            saved.validate()
            database.itemDraftDao().save(saved)
        }
    }
    suspend fun commit(draftId: String, item: Item): Long = database.withTransaction {
        val draft = database.itemDraftDao().get(draftId) ?: error("物品草稿已不存在")
        require(draft.targetItemId == null || draft.targetItemId == item.id) { "物品草稿目标已变化" }
        if(item.id>0) {
            val current=database.itemDao().getItemById(item.id) ?: error("物品已删除，草稿已保留")
            val baseline=draft.data().baselineUpdatedAt
            if(baseline==null || baseline!=current.updatedAt) throw ItemDraftConflictException()
        }
        val appearancePaths=knownAppearancePaths(item.imageUrls+listOfNotNull(item.sizeChartImageUrl)+item.sizeChartImageUrls)+draft.appearanceOnlyImagePaths
        val id = if (item.id > 0) { itemRepository.updateItem(item); item.id } else itemRepository.insertItem(item)
        draft.data().sourceSupplementJson?.let(com.lolita.app.data.model.ProductSupplementPayload::decode)?.let { supplement ->
            if (supplement.appliedFields.isNotEmpty()) {
                database.orderImportDao().insertSources(listOf(ItemSourceRecord(itemId=id,kind="PRODUCT_SUPPLEMENT",
                    publicUrl=supplement.candidate.productUrl,
                    snapshotJson=com.google.gson.Gson().toJson(supplement.candidate),appliedFields=supplement.appliedFields)))
            }
        }
        val staged=database.recognitionDao().getEvidence().filter { it.draftId==draftId && it.itemId==null }
        val origins=staged.mapNotNull { evidence ->
            val payload=RecognitionEvidencePayload.decode(evidence.candidateJson)
            payload.taskId?.let { it to payload.candidate.id }
        }.toSet()
        database.recognitionDao().getEvidence().filter { it.itemId==id }.forEach { evidence ->
            val payload=RecognitionEvidencePayload.decode(evidence.candidateJson)
            if(payload.taskId != null && (payload.taskId to payload.candidate.id) in origins) database.recognitionDao().deleteEvidence(evidence.id)
        }
        database.recognitionDao().attachEvidenceToItem(draftId,id)
        database.recognitionDao().getAll().filter { it.draftId==draftId }.forEach { task ->
            database.recognitionDao().update(task.copy(targetItemId=id,
                requestJson=task.request().copy(targetItemId=id).encode(),updatedAt=System.currentTimeMillis()))
        }
        (item.imageUrls+listOfNotNull(item.sizeChartImageUrl)+item.sizeChartImageUrls).filter { it in appearancePaths }.distinct().forEach { path ->
            if(database.imageProvenanceDao().get(id,path)?.sourceType != ImageSourceType.AI_GENERATED)
                database.imageProvenanceDao().upsert(ImageProvenance(path,id,ImageSourceType.AI_GENERATED,"recognition-source"))
        }
        database.itemDraftDao().delete(draftId)
        id
    }
    suspend fun hasRecognitionForItem(itemId: Long) = database.recognitionDao().getAll().any { it.targetItemId==itemId }

    /** Reconnect retained/late results to the new editor without sending or applying anything. */
    suspend fun reconnectRecognition(itemId: Long,draftId: String) = database.withTransaction {
        requireTargetExists(itemId)
        val draft=database.itemDraftDao().get(draftId) ?: error("物品草稿已关闭")
        require(draft.targetItemId==itemId)
        database.recognitionDao().getAll().filter { it.targetItemId==itemId && it.draftId!=draftId }.forEach { task ->
            database.recognitionDao().update(task.copy(draftId=draftId,
                requestJson=task.request().copy(draftId=draftId).encode(),updatedAt=System.currentTimeMillis()))
        }
    }
    private suspend fun requireTargetExists(targetItemId: Long?) {
        targetItemId?.let { if(database.itemDao().getItemById(it)==null) throw ItemDraftTargetDeletedException() }
    }
    private suspend fun knownAppearancePaths(paths: List<String>): List<String> {
        val known=database.imageProvenanceDao().getAll().filter { it.sourceType==ImageSourceType.AI_GENERATED }.map { it.imagePath }.toMutableSet()
        database.itemDraftDao().getAll().forEach { known.addAll(it.appearanceOnlyImagePaths) }
        database.recognitionDao().getAll().forEach { task ->
            val request=runCatching { task.request() }.getOrNull()
            if(request==null) known.addAll(task.regionImagePaths) else request.regions.forEachIndexed { index,region ->
                if(region.appearanceOnly) task.regionImagePaths.getOrNull(index)?.let { known.add(it) }
            }
        }
        database.recognitionDao().getEvidence().forEach { evidence ->
            if(runCatching { RecognitionEvidencePayload.decode(evidence.candidateJson).appearanceOnly }.getOrDefault(true)) known.addAll(evidence.regionImagePaths)
        }
        return paths.filter { it in known }.distinct()
    }
    suspend fun discard(id: String) = withContext(NonCancellable) {
        beforeDiscard(id)
        val paths = database.withTransaction {
            val tasks = database.recognitionDao().getAll().filter { it.draftId==id }
            val evidence = database.recognitionDao().getEvidence().filter { it.draftId==id && it.itemId==null }
            val references = database.itemDraftDao().get(id)?.protectedImagePaths().orEmpty() + tasks.flatMap { it.regionImagePaths } + evidence.flatMap { it.regionImagePaths }
            tasks.forEach { database.recognitionDao().delete(it.id) }
            database.recognitionDao().deleteDraftEvidence(id)
            database.itemDraftDao().delete(id)
            references
        }
        ImageFileHelper.cleanupUnreferencedImages(paths)
    }
}
