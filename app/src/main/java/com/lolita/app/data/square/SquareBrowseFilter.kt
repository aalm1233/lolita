package com.lolita.app.data.square

data class SquareBrowseFilter(val query: String = "", val kind: String = "", val shop: String = "",
    val color: String = "", val style: String = "", val form: String = "") {
    fun contains(c: SquareCatalog, e: SquareEvent): Boolean = SquareVisibility.event(c, e) &&
        (kind.isEmpty() || e.kind == kind) && (shop.isEmpty() || e.shopId == shop) &&
        (query.isBlank() || (listOf(e.title, e.subtitle.orEmpty(), c.shops.find { it.id == e.shopId }?.name.orEmpty()) +
            SquareVisibility.products(c, e).map { it.title }).joinToString(" ").contains(query, true))

    fun rows(c: SquareCatalog, e: SquareEvent): List<Pair<SquareProduct, SquareSku>> =
        SquareVisibility.products(c, e).flatMap { p -> p.skus.filter { it.matches(color, style, form) }.map { p to it } }

    fun events(c: SquareCatalog) = SquareVisibility.events(c).filter { contains(c, it) && rows(c, it).isNotEmpty() }

    /** Select an original member touched by the same actual SKU used in the feed filter. */
    fun opening(c: SquareCatalog, e: SquareEvent): SquareBrowseChoice? {
        val matches = rows(c, e).map { it.first.id to it.second.key }.toSet()
        val part = SquareBrowse.parts(c, e).firstOrNull { p -> p.rows.any { (it.productId to it.skuKey) in matches } } ?: return null
        val row = SquareBrowse.rows(c, part).first { (it.first.id to it.second.key) in matches }
        val actualColor = row.second.parts.single { it.key == part.partKey }.color.orEmpty()
        return SquareBrowseChoice(part.key, actualColor)
    }

    fun candidates(c: SquareCatalog, facet: String): List<String> {
        val context = when (facet) { "color" -> copy(color = ""); "style" -> copy(style = ""); else -> copy(form = "") }
        val skus = SquareVisibility.events(c).filter { context.contains(c, it) }.flatMap { context.rows(c, it).map { row -> row.second } }
        return when (facet) {
            "color" -> skus.mapNotNull { it.color }.filter(::squareColorFacet)
            "style" -> skus.flatMap { it.styleFacts.map { fact -> fact.value } }
            else -> skus.map { it.form }
        }.distinct().sorted()
    }
}

/** Keep explicit colour names. Product/size descriptions in Taobao's colour field stay in raw data. */
internal fun squareColorFacet(value: String): Boolean = value.length in 1..32 &&
    listOf("黑", "白", "粉", "红", "蓝", "绿", "紫", "黄", "棕", "灰", "金", "银", "绯", "生成", "翡翠", "赤雾").any(value::contains) &&
    listOf("颈饰", "头饰", "发", "衣", "裙", "袖", "袜", "帽", "吊坠", "尺码", "款", "套", "胸针", "戒指", "项链", "元", "定金", "尾款").none(value::contains)
