package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category

/** New display batches stop after one catalog image; legacy transparent tasks stay unchanged. */
fun batchDisplaySubject(category: Category): MannequinSubject {
    require(category.categorySemantic.supportsMaterialPreparation) { "请先确认物品类型" }
    val dress = mannequinSubject(category, confirmedSemantics = true)
        ?.takeIf { it.presentation == MannequinPresentation.DRESS_FORM }
    return dress ?: MannequinSubject(category.id, category.name, MannequinPresentation.ACCESSORY, category.categorySemantic)
}

val MannequinSubject.batchDisplayLabel: String
    get() = if (presentation == MannequinPresentation.DRESS_FORM) {
        if (semantic == CategorySemantic.BLOUSE) "衬衫人台图" else "正面人台图"
    } else "单品主视图"

data class PreparedDisplayInput(val product: PreparedMaterialInput, val subject: MannequinSubject, val composition: ItemComposition,
    val templatePath: String? = null, val templateSha256: String? = null) {
    val paths get() = listOfNotNull(templatePath) + product.imagePath
}

data class BatchDisplayInput(val itemId: Long, val prepared: PreparedDisplayInput, val originalPath: String,
    val instructions: String, val position: Int)
