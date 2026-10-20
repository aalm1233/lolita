package com.lolita.app.data.file

import com.google.gson.JsonParser
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.domain.model.*
import com.lolita.app.domain.model.validateRecognitionEvidence
import java.util.UUID

/** Entire new graph is checked before a restore transaction touches the user's records. */
internal object WardrobeBackupValidation {
    fun validate(data: BackupData) {
        data.wigHeadTemplatePreferenceJson?.let { raw ->
            val preference=WigHeadTemplateSnapshot.decode(raw)
            require(preference.imagePath=="preference-only") { "头模偏好不得包含实际图片路径" }
        }
        StorageTree.validate(data.storageNodes, data.locations.map { it.id }.toSet())
        val nodes = data.storageNodes.associateBy { it.id }
        data.items.forEach { item ->
            require(item.specificationsJson != null && item.composition != null) { "物品规格或组成字段缺失" }
            WardrobeSpecifications.decode(item.specificationsJson)
            require(item.storageNodeId == null || nodes[item.storageNodeId]?.locationId == item.locationId) { "物品的房间和收纳位置不一致" }
        }
        val systemKeys = data.brands.mapNotNull { it.systemKey }
        require(systemKeys.distinct().size == systemKeys.size && systemKeys.all { it == Brand.UNSET_BRAND_KEY }) { "未知或重复系统品牌" }
        require(data.brands.none { it.systemKey != null && it.logoUrl != null }) { "未设置品牌不能关联真实商标" }
        require(data.categories.all { it.categorySemantic != null }) { "分类语义缺失" }
        require(data.itemDrafts.map { it.id }.distinct().size == data.itemDrafts.size) { "物品草稿身份重复" }
        val itemIds = data.items.map { it.id }.toSet()
        val targets = data.itemDrafts.mapNotNull { it.targetItemId }
        require(targets.distinct().size == targets.size) { "同一物品存在重复编辑草稿" }
        require(targets.all { it in itemIds }) { "编辑草稿对应的物品已不存在" }
        data.itemDrafts.forEach { draft ->
            draft.validate()
            val body = draft.data()
            require(body.storageNodeId == null || nodes[body.storageNodeId]?.locationId == body.locationId) { "草稿收纳位置无效" }
        }
        require(data.recognitionTasks.map { it.id }.distinct().size == data.recognitionTasks.size) { "识图任务身份重复" }
        data.recognitionTasks.forEach { it.validate() }
        require(data.itemEvidence.map { it.id }.distinct().size == data.itemEvidence.size) { "识图证据身份重复" }
        val draftIds = data.itemDrafts.map { it.id }.toSet()
        data.itemEvidence.forEach { evidence ->
            require(UUID.fromString(evidence.id).toString() == evidence.id)
            require(evidence.itemId?.let { it in itemIds } ?: (evidence.draftId in draftIds)) { "识图证据目标无效" }
            require(evidence.regionImagePaths.isNotEmpty() && evidence.regionImagePaths.none(String::isBlank)) { "识图证据缺少图片" }
            evidence.validateRecognitionEvidence()
        }
        val imageKeys = data.imageProvenance.map { it.itemId to it.imagePath }
        require(imageKeys.distinct().size == imageKeys.size) { "图片来源记录重复" }
        val items = data.items.associateBy { it.id }
        data.imageProvenance.forEach {
            require(it.itemId in itemIds && it.imagePath in (items[it.itemId]!!.imageUrls + listOfNotNull(items[it.itemId]!!.sizeChartImageUrl) + items[it.itemId]!!.sizeChartImageUrls) && it.mode.isNotBlank() && it.mode.length <= 80) { "图片来源不属于物品相册" }
        }
        require(data.itemTryOnMaterials.map { it.itemId }.distinct().size == data.itemTryOnMaterials.size &&
            data.itemTryOnMaterials.map { it.id }.distinct().size == data.itemTryOnMaterials.size &&
            data.itemTryOnMaterials.map { it.sourceTaskId }.distinct().size == data.itemTryOnMaterials.size) { "确认素材身份或物品归属重复" }
        data.itemTryOnMaterials.forEach { it.validate(); require(it.itemId in itemIds) { "确认素材对应物品不存在" } }
        data.tryOnResults.mapNotNull { it.requestSnapshotJson }.forEach { ImageTaskRequest.decode(it) }
    }
}
