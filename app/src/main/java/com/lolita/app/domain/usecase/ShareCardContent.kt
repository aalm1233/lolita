package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Item

data class ShareCardItem(val name: String, val brand: String?, val image: String?)
/** A whitelist: private ledger, location, source and notes never reach the renderer. */
data class ShareCardContent(val title: String, val images: List<String>, val items: List<ShareCardItem>) {
    companion object {
        fun create(title: String, images: List<String>, items: List<Item>, brands: Map<Long, String>, showBrands: Boolean) =
            ShareCardContent(title, images.take(3), items.map {
                ShareCardItem(it.name, if (showBrands) brands[it.brandId] else null, it.imageUrls.firstOrNull())
            })
    }
}
