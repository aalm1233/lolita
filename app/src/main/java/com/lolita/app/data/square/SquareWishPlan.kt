package com.lolita.app.data.square

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemDraft
import com.lolita.app.data.local.entity.ItemSourceRecord

/** One transaction may write several ordinary Items; a personal combination writes exactly one. */
data class SquareWishPlan(val snapshot: SquareWishSnapshot, val paths: List<String>, val legacyTokens: Set<String>)
data class SquareWishWardrobe(val items: List<Item> = emptyList(), val sources: List<ItemSourceRecord> = emptyList(), val drafts: List<ItemDraft> = emptyList())
data class SquareWishMatch(val exact: List<Long> = emptyList(), val related: List<Long> = emptyList(), val drafts: List<String> = emptyList())
data class SquareWishResult(val itemIds: List<Long> = emptyList(), val added: Int = 0, val related: Boolean = false, val draftIds: List<String> = emptyList())

object SquareWishPlans {
    fun build(c: SquareCatalog, request: SquareWishRequest): List<SquareWishPlan> {
        val e = c.events.single { it.id == request.eventId }
        require(e.canRegister && e.revision == request.eventRevision && e.productIds.associateWith { id -> c.products.single { it.id == id }.revision } == request.productRevisions) { "商品资料已更新，请重新选择" }
        require(request.choices.size in 1..20 && request.choices.map { it.key }.distinct().size == request.choices.size && (!request.grouped || request.choices.size > 1)) { "请选择要加入心愿的物品" }
        val parts = SquareBrowse.parts(c, e).associateBy { it.key }
        val selections = request.choices.map { SquareBrowse.select(c, parts[it.key] ?: error("商品资料已改变，请重新选择"), it) }
        val shop = c.shops.single { it.id == e.shopId }
        return (if (request.grouped) listOf(selections) else selections.map { listOf(it) }).map { selected ->
            val paths = selected.flatMap { it.photos + it.documents + it.seriesPhotos.take(1) }.distinct()
            fun refs(pathsToMap: List<String>) = pathsToMap.map { paths.indexOf(it).also { index -> require(index >= 0) } }
            val members = selected.map { s ->
                val origins = s.rows.groupBy { it.first.id }.values.map { rows ->
                    val product = rows.first().first
                    val independent = rows.filter { it.second.parts.size == 1 }
                    val quotes = independent.map { it.second.price }.distinct()
                    val candidates = rows.map { (_, sku) ->
                        val part = sku.parts.single { it.key == s.part.partKey }
                        val token = "square:" + squareDigest(SquareUnit(listOf(part.key), part.name).identity(e, sku))
                        SquareWishSku(sku.key, sku.variantId, part.color, part.size, sku.price, sku.rawProperties, token)
                    }
                    SquareWishOrigin(product.id, com.lolita.app.domain.model.OrderImportSource.publicUrl(product.url) ?: error("来源链接无法识别，请重新获取公开商品资料"), product.capturedAt, product.pricePhase, product.priceType, candidates,
                        s.selectedSkuKey?.takeIf { key -> candidates.any { it.key == key } }, quotes.singleOrNull(), independent.isNotEmpty())
                }
                SquareWishMember(s.part.key, s.part.partKey, s.part.name, s.choice.color, s.choice.size, s.part.category,
                    refs(s.photos), refs(s.documents), refs(s.seriesPhotos.take(1)), s.materials.map { it.facts }.filter { it.isNotBlank() }.distinct(), origins, s.materials.firstNotNullOfOrNull { it.sizeTable })
            }
            val snapshot = SquareWishSnapshot(shopId = shop.id, shopName = shop.name, eventId = e.id, designId = e.designId, batchId = e.batchId, title = e.title, grouped = request.grouped, members = members)
            val legacy = members.flatMap { it.origins }.flatMap { it.candidates }.map { it.legacyToken }.toMutableSet()
            if (!request.grouped && selected.single().rows.any { it.second.parts.size == 1 }) {
                val selection = selected.single()
                val linked = selection.rows.map { it.first.id }.toMutableSet()
                val products = c.products.filter { it.id in e.productIds }
                do { val before = linked.size
                    val variants = products.filter { it.id in linked }.flatMap { it.skus }.map { it.variantId }.toSet()
                    products.filter { p -> p.skus.any { it.variantId in variants } }.forEach { linked.add(it.id) }
                } while (before != linked.size)
                val dimensions = selection.rows.map { (_,sku) -> sku.rawProperties.filterKeys { it !in setOf("颜色分类","颜色","尺码","尺寸","size","color") } }
                val common = dimensions.first().filter { (key,value) -> dimensions.all { it[key] == value } }
                products.filter { it.id in linked }.forEach { product -> product.skus.forEach { sku ->
                    val component = sku.parts.firstOrNull { it.key == selection.part.partKey }
                    if (component != null && common.all { (key,value) -> sku.rawProperties[key] == value } &&
                        (selection.choice.color.isEmpty() || component.color.isNullOrEmpty() || component.color == selection.choice.color) &&
                        (selection.choice.size.isEmpty() || component.size.isNullOrEmpty() || component.size == selection.choice.size)) {
                        legacy.add("square:" + squareDigest(SquareUnit(listOf(component.key),component.name).identity(e,sku)))
                    }
                } }
            }
            if (request.grouped) {
                legacy.clear()
                c.products.filter { it.id in e.productIds }.forEach { p -> p.skus.forEach { sku ->
                    if (members.all { m -> sku.parts.any { part -> part.key == m.partKey && (m.color.isEmpty() || m.color == part.color) && (m.size.isEmpty() || m.size == part.size) } }) {
                        legacy.add("square:" + squareDigest(SquareUnit(members.map { it.partKey }, "组合").identity(e, sku)))
                    }
                } }
            }
            SquareWishPlan(snapshot, paths, legacy)
        }
    }

    fun match(plan: SquareWishPlan, wardrobe: SquareWishWardrobe): SquareWishMatch {
        val snapshot = plan.snapshot
        val exact = mutableSetOf<Long>(); val related = mutableSetOf<Long>()
        val items = wardrobe.items.associateBy { it.id }
        wardrobe.items.filter { it.importToken == snapshot.identity() }.forEach { exact.add(it.id) }
        wardrobe.sources.filter { it.kind == "SQUARE_WISH" && it.itemId in items }.forEach { source ->
            val previous = runCatching { SquareWishSnapshot.decode(source.snapshotJson, source.imagePaths.size) }.getOrNull() ?: return@forEach
            if (previous.shopId == snapshot.shopId && previous.batchId == snapshot.batchId) {
                if (previous.identity() == snapshot.identity()) exact.add(source.itemId)
                else if (snapshot.members.any { m -> previous.members.any { it.overlaps(m) } }) related.add(source.itemId)
            }
        }
        wardrobe.items.filter { it.importToken in plan.legacyTokens }.forEach { item ->
            val member = snapshot.members.singleOrNull()
            if (member != null && item.size.orEmpty() == member.size && item.colors == listOf(member.color).filter { it.isNotEmpty() }) exact.add(item.id)
            else related.add(item.id)
        }
        val drafts = wardrobe.drafts.filter { draft -> draft.targetItemId == null && runCatching { draft.data().importToken in plan.legacyTokens }.getOrDefault(false) }.map { it.id }
        return SquareWishMatch(exact.toList(), (related - exact).toList(), drafts)
    }
}
