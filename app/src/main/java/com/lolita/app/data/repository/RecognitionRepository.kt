package com.lolita.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.google.gson.Gson
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class RecognitionRepository(private val database: LolitaDatabase, private val context: Context,
    private val deleteImage: suspend (String) -> Unit = { ImageFileHelper.deleteImage(it) }) {
    fun observe(draftId: String) = database.recognitionDao().observe(draftId)
    suspend fun get(id: String) = database.recognitionDao().get(id)

    suspend fun addRegion(draftId: String, path: String, baseline: Map<RecognitionField,String>,
        family: WardrobeFamily, appearanceOnly: Boolean): String = withContext(Dispatchers.IO) {
        require(ImageFileHelper.isOwnedReadableImage(context,path)) { "请重新选择清楚的图片区域" }
        val file = File(path)
        require(file.length() in 1..(3L*1024*1024)) { "选区文件超过3 MiB，请缩小区域" }
        val bytes = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        val region = RecognitionRegion(UUID.randomUUID().toString(),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },bounds.outWidth,bounds.outHeight,appearanceOnly)
        database.withTransaction {
            val draft = database.itemDraftDao().get(draftId) ?: error("编辑草稿已关闭，请重新进入")
            val prepared = database.recognitionDao().getAll().firstOrNull { it.draftId==draftId && it.status==RecognitionStatus.PREPARED }
            val request = prepared?.request()?.copy(regions=prepared.request().regions+region) ?:
                RecognitionRequest(draftId=draftId,targetItemId=draft.targetItemId,regions=listOf(region),baseline=baseline,family=family)
            request.validate()
            val paths = prepared?.regionImagePaths.orEmpty()+path
            require(paths.sumOf { File(it).length() } <= 12L*1024*1024) { "选区合计超过12 MiB，请减少区域" }
            val task = prepared?.copy(requestJson=request.encode(),regionImagePaths=paths,updatedAt=System.currentTimeMillis()) ?:
                RecognitionTask(UUID.randomUUID().toString(),draftId,draft.targetItemId,request.encode(),paths)
            task.validate()
            if (prepared==null) database.recognitionDao().insert(task) else database.recognitionDao().update(task)
            task.id
        }
    }

    suspend fun removeRegion(id: String,index: Int) = withContext(NonCancellable+Dispatchers.IO) {
        val removed = database.withTransaction {
            val task=database.recognitionDao().get(id) ?: return@withTransaction null
            require(task.status==RecognitionStatus.PREPARED) { "已提交的区域不能更换" }
            require(index in task.regionImagePaths.indices)
            val paths=task.regionImagePaths.toMutableList()
            val path=paths.removeAt(index)
            if(paths.isEmpty()) database.recognitionDao().delete(id) else {
                val regions=task.request().regions.toMutableList().apply { removeAt(index) }
                database.recognitionDao().update(task.copy(requestJson=task.request().copy(regions=regions).encode(),regionImagePaths=paths))
            }
            path
        }
        removed?.let { deleteImage(it) }
    }

    suspend fun confirmation(id: String, baseline: Map<RecognitionField,String>,family: WardrobeFamily,provider: RecognitionProvider? = null): RecognitionTask = database.withTransaction {
        val task=database.recognitionDao().get(id) ?: error("识图草稿已不存在")
        require(task.status==RecognitionStatus.PREPARED && !task.requestSent)
        val draft=database.itemDraftDao().get(task.draftId) ?: error("编辑草稿已关闭")
        require(draft.targetItemId == task.targetItemId)
        task.copy(requestJson=task.request().copy(baseline=baseline,family=family,provider=provider ?: task.request().provider,model=(provider ?: task.request().provider).model).encode(),updatedAt=System.currentTimeMillis()).also {
            database.recognitionDao().update(it)
        }
    }

    suspend fun isAppearanceOnly(path: String): Boolean = database.withTransaction {
        database.itemDraftDao().getAll().any { path in it.appearanceOnlyImagePaths } ||
        database.imageProvenanceDao().getAll().any { it.imagePath == path && it.sourceType == ImageSourceType.AI_GENERATED } ||
            database.recognitionDao().getEvidence().any { path in it.regionImagePaths &&
                runCatching { RecognitionEvidencePayload.decode(it.candidateJson).appearanceOnly }.getOrDefault(true) } ||
            database.recognitionDao().getAll().any { task -> task.regionImagePaths.withIndex().any { (index, source) ->
                source == path && task.request().regions.getOrNull(index)?.appearanceOnly == true
            } }
    }

    suspend fun draftEvidence(draftId: String) = database.recognitionDao().getEvidence().filter { it.draftId==draftId && it.itemId==null }

    fun observeEvidence(itemId: Long) = database.recognitionDao().observeEvidence(itemId)

    suspend fun reprepare(id: String): String = database.withTransaction {
        val previous = database.recognitionDao().get(id) ?: error("识图记录已移除")
        require(previous.status in setOf(RecognitionStatus.FAILED, RecognitionStatus.INTERRUPTED)) { "请先核对本次识图状态" }
        val draft = database.itemDraftDao().get(previous.draftId) ?: error("请重新进入物品编辑后再提交")
        require(draft.targetItemId == previous.targetItemId)
        require(previous.regionImagePaths.all { ImageFileHelper.isOwnedReadableImage(context,it) }) { "部分区域已不可读，请重新框选" }
        val next = RecognitionTask(UUID.randomUUID().toString(),draft.id,draft.targetItemId,
            previous.request().encode(),previous.regionImagePaths)
        next.validate()
        database.recognitionDao().insert(next)
        next.id
    }

    suspend fun saveReview(id: String, json: String) {
        require(json.length<=512*1024)
        database.withTransaction {
            val task=database.recognitionDao().get(id) ?: error("识图记录已移除")
            require(task.status in setOf(RecognitionStatus.AWAITING_REVIEW, RecognitionStatus.APPLIED))
            val candidateIds=task.result()!!.candidates.map { it.id }.toSet()
            val submitted=RecognitionReview.decode(json,candidateIds)
            val history=task.reviewJson?.let { RecognitionReview.decode(it,candidateIds).appliedValues }.orEmpty()
            database.recognitionDao().update(task.copy(reviewJson=submitted.copy(appliedValues=history).encode(),updatedAt=System.currentTimeMillis()))
        }
    }

    /** Caller takes the editor draft mutex before entering this Room transaction. */
    suspend fun apply(id: String, review: RecognitionReview,
        applyToDraft: suspend (RecognitionRequest,List<RecognitionCandidate>,RecognitionReview)->RecognitionMerge): RecognitionMerge = database.withTransaction {
        val task=database.recognitionDao().get(id) ?: error("识图结果已不存在")
        task.validate()
        require(task.status in setOf(RecognitionStatus.AWAITING_REVIEW, RecognitionStatus.APPLIED)) { "请重新核对识图结果" }
        val request=task.request()
        val draft=database.itemDraftDao().get(task.draftId) ?: error("编辑草稿已关闭")
        require(draft.targetItemId==task.targetItemId) { "编辑草稿已变化" }
        task.targetItemId?.let { require(database.itemDao().getItemById(it)!=null) { "物品已删除" } }
        val original=task.result()!!.candidates
        val candidateIds=original.map { it.id }.toSet()
        val submitted=RecognitionReview.decode(review.encode(),candidateIds)
        val history=task.reviewJson?.let { RecognitionReview.decode(it,candidateIds).appliedValues }.orEmpty()
        val regionIndex=request.regions.associateBy { it.id }
        val edited=original.map { candidate ->
            val sourceRestricted=candidate.regionIds.any { regionIndex.getValue(it).appearanceOnly }
            candidate.copy(value=submitted.edits[candidate.id] ?: candidate.value,
                source=if(sourceRestricted && (candidate.source!=RecognitionSource.VISUAL || candidate.field.textOnly)) RecognitionSource.UNKNOWN else candidate.source)
        }
        val selection=submitted.copy(appliedValues=history).forSelection(edited)
        if(edited.any { it.id in selection.selectedIds && it.field==RecognitionField.BRAND }) selection.resolvedBrandId?.let {
            require(database.brandDao().getBrandById(it)?.isUnsetBrand==false) { "请选择仍存在的真实品牌" }
        }
        if(edited.any { it.id in selection.selectedIds && it.field==RecognitionField.CATEGORY }) selection.resolvedCategoryId?.let {
            require(database.categoryDao().getCategoryById(it)!=null) { "所选类型已删除，请重新选择" }
        }
        val merge=applyToDraft(request.copy(baseline=request.baseline+history),edited,selection)
        require(merge.acceptedIds.all { it in selection.selectedIds })
        val index=request.regions.map { it.id }.withIndex().associate { it.value to it.index }
        edited.filter { it.id in merge.acceptedIds }.forEach { candidate ->
            val paths=candidate.regionIds.map { task.regionImagePaths[index.getValue(it)] }
            val payload=RecognitionEvidencePayload(taskId=task.id,
                appearanceOnly=candidate.regionIds.any { regionIndex.getValue(it).appearanceOnly },candidate=candidate)
            val evidenceId=UUID.nameUUIDFromBytes("${task.id}:${task.draftId}:${candidate.id}".toByteArray(Charsets.UTF_8)).toString()
            database.recognitionDao().deleteEvidence(evidenceId)
            val evidence=ItemEvidence(evidenceId,null,task.draftId,candidate.field,payload.encode(),paths)
            evidence.validateRecognitionEvidence()
            database.recognitionDao().insertEvidence(evidence)
        }
        // APPLIED remains reviewable. Accepted values authorize only their own next revision, never manual edits.
        database.recognitionDao().update(task.copy(status=if(merge.acceptedIds.isNotEmpty() || task.status==RecognitionStatus.APPLIED) RecognitionStatus.APPLIED else RecognitionStatus.AWAITING_REVIEW,
            reviewJson=selection.copy(selectedIds=selection.selectedIds-merge.acceptedIds,appliedValues=history+merge.values).encode(),updatedAt=System.currentTimeMillis()))
        merge
    }

    suspend fun discard(id: String) = withContext(NonCancellable+Dispatchers.IO) {
        val paths=database.withTransaction {
            val task=database.recognitionDao().get(id) ?: return@withTransaction emptyList()
            require(task.status!=RecognitionStatus.RUNNING) { "请先取消正在进行的识图" }
            database.recognitionDao().delete(id)
            task.regionImagePaths
        }
        paths.forEach { deleteImage(it) }
    }
}
