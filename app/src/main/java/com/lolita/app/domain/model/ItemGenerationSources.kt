package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus

/** Trying an item does not assert a purchase, payment, or storage location. */
val ItemStatus.supportsVirtualTryOn: Boolean
    get() = this == ItemStatus.OWNED || this == ItemStatus.WISHED

/** Existing product references only; caller still validates private files and their bytes. */
val Item.productReferenceImages: List<String>
    get() = (imageUrls + listOfNotNull(sizeChartImageUrl) + sizeChartImageUrls)
        .filter(String::isNotBlank).distinct()

val Item.productDetailImages: List<String>
    get() = (listOfNotNull(sizeChartImageUrl) + sizeChartImageUrls)
        .filter { it.isNotBlank() && it !in imageUrls }.distinct()
