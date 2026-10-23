package com.lolita.app.data.model

import com.google.gson.*
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.domain.model.OrderImportSource

/** Only indices point into ItemDraft.evidenceImagePaths, so ZIP can remap images safely. */
data class ProductSupplementPayload(
    val version: Int = 1,
    val candidate: TaobaoProductDraft,
    val photoIndices: List<Int>,
    val detailIndices: List<Int>,
    val existingEditor: Boolean,
    val appliedFields: List<String> = emptyList(),
) {
    fun encode(): String = Gson().toJson(this)

    fun validate(imageCount: Int? = null) {
        require(
            version == 1 &&
                candidate.imagePaths.isEmpty() &&
                candidate.sizeChartImagePaths.isEmpty() &&
                candidate.sizeChartImagePath == null
        )
        require(
            OrderImportSource.publicUrl(candidate.productUrl) == candidate.productUrl &&
                photoIndices.size <= 5 &&
                detailIndices.size <= 50
        )
        require(
            (photoIndices + detailIndices).all {
                it >= 0 && (imageCount == null || it < imageCount)
            }
        ) {
            "补资料图片引用无效"
        }
        require(
            appliedFields.all {
                it in
                    setOf(
                        "name",
                        "brandId",
                        "categoryId",
                        "colors",
                        "size",
                        "specifications",
                        "photos",
                        "details",
                    )
            }
        )
    }

    companion object {
        fun decode(json: String): ProductSupplementPayload {
            val root = JsonParser.parseString(json).asJsonObject
            require(
                root.keySet().all {
                    it in
                        setOf(
                            "version",
                            "candidate",
                            "photoIndices",
                            "detailIndices",
                            "existingEditor",
                            "appliedFields",
                        )
                } && root["version"].asBigDecimal.intValueExact() == 1
            )
            val fields =
                TaobaoProductDraft::class
                    .java
                    .declaredFields
                    .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    .map { it.name }
                    .toSet()
            require(root["candidate"].asJsonObject.keySet().all { it in fields }) { "商品补充资料字段无效" }
            if (!root.has("appliedFields")) root.add("appliedFields", JsonArray())
            return Gson().fromJson(root, ProductSupplementPayload::class.java).also {
                it.validate()
            }
        }
    }
}
