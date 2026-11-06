package com.lolita.app.data.square

import java.util.UUID

/** A visible garment is independent of choosing a complete sales SKU. */
data class SquareBrowseRow(val productId: String, val skuKey: String, val partKey: String)
data class SquareBrowsePart(
    val key: String, val partKey: String, val name: String, val category: String?,
    val accessory: Boolean, val rows: List<SquareBrowseRow>
)
data class SquareBrowseChoice(val key: String, val color: String = "", val size: String = "")
data class SquareBrowseSelection(
    val part: SquareBrowsePart, val choice: SquareBrowseChoice,
    val rows: List<Pair<SquareProduct, SquareSku>>, val photos: List<String>,
    val documents: List<String>, val materials: List<SquareMaterial>, val seriesPhotos: List<String>,
    val onlyInCombination: Boolean, val quote: String?, val priceType: String?, val phase: String?,
    val selectedSkuKey: String?
)

internal fun squareDigest(value: String) = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

object SquareBrowse {
    fun parts(c: SquareCatalog, e: SquareEvent): List<SquareBrowsePart> {
        data class Bucket(val product: SquareProduct, val slot: String, val part: SquarePart, val skus: List<SquareSku>)
        val products = c.products.filter { it.id in e.productIds && (!e.canRegister || it.pricePhase != "BALANCE") }
        val variants = products.associate { it.id to it.skus.map { sku -> sku.variantId }.toSet() }
        val buckets = products.flatMap { p ->
            val linked = mutableSetOf(p.id)
            do { val before = linked.size
                products.filter { candidate -> linked.any { id -> variants.getValue(id).intersect(variants.getValue(candidate.id)).isNotEmpty() } }.forEach { linked.add(it.id) }
            } while (linked.size != before)
            // A linked deposit page may list KC only inside bundles while the full-price page sells it alone.
            val independent = products.filter { it.id in linked }.flatMap { it.skus }.filter { it.parts.size == 1 }.map { it.parts.single().key }.toSet()
            p.skus.flatMap { sku -> sku.parts.filter { sku.parts.size == 1 || it.key !in independent }.map { it to sku } }
                .groupBy { (part, sku) ->
                    // Explicit non-colour/non-size sales dimensions separate long/short or material options.
                    val dimensions = sku.rawProperties.filterKeys { it !in setOf("颜色分类", "颜色", "尺码", "尺寸", "size", "color") }.toSortedMap()
                    SquarePackage.gson.toJson(listOf(part.key, dimensions, if (sku.parts.size > 1) sku.content else ""))
                }.map { (slot, entries) -> Bucket(p, slot, entries.first().first, entries.map { it.second }.distinctBy { it.key }) }
        }
        val groups = mutableListOf<MutableList<Bucket>>()
        buckets.forEach { b ->
            // Cross-link consolidation needs the collector's explicit variant identity, never similar titles.
            val linked = groups.filter { group -> group.any { a -> a.slot == b.slot && a.skus.any { x -> b.skus.any { it.variantId == x.variantId } } } }
            if (linked.isEmpty()) groups.add(mutableListOf(b)) else {
                val target = linked.first(); target.add(b)
                linked.drop(1).forEach { target.addAll(it); groups.remove(it) }
            }
        }
        return groups.map { group ->
            val first = group.first(); val p = first.part
            val contextual = first.skus.first().takeIf { it.parts.size > 1 }?.content
            val key = squareDigest("${e.shopId}:${e.batchId}:${group.map { it.product.id }.sorted().first()}:${first.slot}")
            SquareBrowsePart(key, p.key, if (contextual != null) "${p.name} · $contextual" else p.name,
                p.category, p.isAccessory, group.flatMap { b -> b.skus.map { SquareBrowseRow(b.product.id, it.key, p.key) } }.distinct())
        }.sortedWith(compareBy<SquareBrowsePart> { if (it.category in setOf("JSK", "OP", "SK")) 0 else if (!it.accessory) 1 else 2 })
    }

    fun rows(c: SquareCatalog, part: SquareBrowsePart): List<Pair<SquareProduct, SquareSku>> = part.rows.map { row ->
        val p = c.products.single { it.id == row.productId }; p to p.skus.single { it.key == row.skuKey }
    }
    fun colors(c: SquareCatalog, part: SquareBrowsePart): List<String> = rows(c, part).mapNotNull { (_, s) -> s.parts.single { it.key == part.partKey }.color }.distinct()
    fun sizes(c: SquareCatalog, part: SquareBrowsePart, color: String): List<String> = rows(c, part).filter { (_, s) -> color.isEmpty() || s.parts.single { it.key == part.partKey }.color == color }
        .mapNotNull { (_, s) -> s.parts.single { it.key == part.partKey }.size }.distinct()

    fun select(c: SquareCatalog, part: SquareBrowsePart, choice: SquareBrowseChoice): SquareBrowseSelection {
        require(part.key == choice.key) { "所选物品已改变，请重新选择" }
        val rows = rows(c, part).filter { (_, s) -> val p = s.parts.single { it.key == part.partKey }
            (choice.color.isEmpty() || p.color == choice.color) && (choice.size.isEmpty() || p.size == choice.size) }
        require(rows.isNotEmpty()) { "所选颜色和尺码不对应同一规格，请重新选择" }
        val independent = rows.filter { it.second.parts.size == 1 }
        val preferred = independent.filter { it.first.pricePhase == "FULL" }.ifEmpty { independent.filter { it.first.pricePhase != "PLACEHOLDER" }.ifEmpty { independent } }
        val phase = preferred.map { it.first.pricePhase }.distinct().singleOrNull()
        val quotes = preferred.map { it.second.price }.distinct()
        val photoSets = independent.map { it.second.photoPaths.orEmpty() }.filter { it.isNotEmpty() }.distinct()
        // Unselected colour cannot silently borrow another colour's cover.
        val photos = if (photoSets.size == 1) photoSets.single() else if (choice.color.isNotEmpty()) photoSets.flatten().distinct() else emptyList()
        val products = rows.map { it.first }.distinctBy { it.id }
        val materials = products.flatMap { it.materials.orEmpty() }.filter { it.partKey == part.partKey }.distinctBy { it.facts to it.detailPaths }
        return SquareBrowseSelection(part, choice, rows, photos, materials.flatMap { it.detailPaths }.distinct(), materials,
            products.flatMap { it.images.filter { image -> image.role == "main" }.sortedBy { image -> image.order }.map { image -> image.path } }.distinct(),
            independent.isEmpty() || independent.all { it.second.notSoldAlone },
            quotes.singleOrNull()?.takeIf { phase != null }, preferred.map { it.first.priceType }.distinct().singleOrNull(), phase,
            preferred.singleOrNull()?.takeIf { choice.size.isNotEmpty() && (choice.color.isNotEmpty() || colors(c, part).isEmpty()) }?.second?.key)
    }

    fun request(c: SquareCatalog, e: SquareEvent, choices: List<SquareBrowseChoice>, grouped: Boolean): SquareWishRequest {
        require(e.canRegister && choices.size in 1..20 && choices.map { it.key }.distinct().size == choices.size) { "请选择要加入心愿的物品" }
        val parts = parts(c, e).associateBy { it.key }
        choices.forEach { select(c, parts[it.key] ?: error("商品资料已更新，请重新选择"), it) }
        return SquareWishRequest(e.id, e.revision, e.productIds.associateWith { id -> c.products.single { it.id == id }.revision }, choices, grouped && choices.size > 1)
    }
}

data class SquareWishRequest(val eventId: String, val eventRevision: Long, val productRevisions: Map<String, Long>, val choices: List<SquareBrowseChoice>, val grouped: Boolean, val allowRelated: Boolean = false)
