package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.domain.model.ItemComposition
import com.lolita.app.domain.model.WardrobeSpecifications
import java.util.UUID

/** Images are external to the payload so ZIP restore can remap every reference. */
@Entity(tableName = "item_drafts", indices = [Index(value = ["target_item_id"], unique = true)])
data class ItemDraft(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "target_item_id") val targetItemId: Long? = null,
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "image_paths", defaultValue = "'[]'") val imagePaths: List<String> = emptyList(),
    @ColumnInfo(name = "size_chart_image_paths", defaultValue = "'[]'") val sizeChartImagePaths: List<String> = emptyList(),
    @ColumnInfo(name = "evidence_image_paths", defaultValue = "'[]'") val evidenceImagePaths: List<String> = emptyList(),
    @ColumnInfo(name = "appearance_only_image_paths", defaultValue = "'[]'") val appearanceOnlyImagePaths: List<String> = emptyList(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    fun data(): ItemDraftData = ItemDraftData.decode(dataJson)
    fun protectedImagePaths(): List<String> = imagePaths + sizeChartImagePaths + evidenceImagePaths + appearanceOnlyImagePaths
    fun validate() {
        require(UUID.fromString(id).toString() == id && (targetItemId == null || targetItemId > 0)) { "物品草稿标识无效" }
        require(updatedAt >= 0 && protectedImagePaths().all(String::isNotBlank)) { "物品草稿图片或时间无效" }
        require(appearanceOnlyImagePaths.all { it in imagePaths || it in sizeChartImagePaths }) { "草稿图片来源引用无效" }
        data()
    }
}

data class ItemDraftData(val version: Int = 1, val name: String = "", val description: String = "", val brandId: Long = 0,
    val categoryId: Long = 0, val coordinateId: Long? = null, val parentItemId: Long? = null, val includedInParentPrice: Boolean = false,
    val status: ItemStatus = ItemStatus.OWNED, val priority: ItemPriority = ItemPriority.MEDIUM,
    val colors: List<String> = emptyList(), val seasons: List<String> = emptyList(), val style: String? = null,
    val size: String? = null, val source: String? = null, val locationId: Long? = null, val storageNodeId: Long? = null,
    val specificationsJson: String = "", val composition: ItemComposition = ItemComposition.UNCONFIRMED,
    val importToken: String? = null, val baselineUpdatedAt: Long? = null, val recognitionSessionIds: List<String> = emptyList()) {
    fun encode(): String = Gson().toJson(this)
    companion object {
        fun decode(json: String): ItemDraftData = try {
            val root = JsonParser.parseString(json).asJsonObject
            val allowed = ItemDraftData::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
            require(root.keySet().all { it in allowed } && root["version"]?.asJsonPrimitive?.let {
                it.isNumber && it.asBigDecimal.intValueExact() == 1
            } == true) { "物品草稿版本或字段无效" }
            val requiredStrings = setOf("name", "description", "specificationsJson")
            requiredStrings.forEach { key -> root[key]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) } }
            setOf("status", "priority", "composition").forEach { key -> root[key]?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                when (key) {
                    "status" -> ItemStatus.valueOf(it.asString)
                    "priority" -> ItemPriority.valueOf(it.asString)
                    else -> ItemComposition.valueOf(it.asString)
                }
            } }
            setOf("colors", "seasons", "recognitionSessionIds").forEach { key -> root[key]?.let { value ->
                require(value.isJsonArray && value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
            } }
            val data = Gson().fromJson(root, ItemDraftData::class.java)
            WardrobeSpecifications.decode(data.specificationsJson)
            data
        } catch (failure: Exception) { throw IllegalArgumentException("物品草稿无效", failure) }
    }
}

/** Copy registration data only; ownership, accounting and generation identities start fresh. */
fun Item.registrationCopy(): ItemDraftData = ItemDraftData(name = name, description = description,
    brandId = brandId, categoryId = categoryId, colors = colors, seasons = season?.split(",")?.filter(String::isNotBlank).orEmpty(),
    style = style, size = size, source = source, specificationsJson = specificationsJson, composition = composition)
