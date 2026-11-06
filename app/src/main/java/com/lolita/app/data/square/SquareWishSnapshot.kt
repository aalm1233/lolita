package com.lolita.app.data.square

import com.google.gson.JsonParser
import com.lolita.app.domain.model.OrderImportSource

/** Image references are indices into ItemSourceRecord.imagePaths, so ZIP restore can remap paths. */
data class SquareWishSnapshot(val version: Int = 1, val shopId: String, val shopName: String,
    val eventId: String, val designId: String, val batchId: String, val title: String,
    val grouped: Boolean, val members: List<SquareWishMember>) {
    fun encode(): String = SquarePackage.gson.toJson(this)
    fun identity(): String = "square:wish:v1:" + squareDigest(SquarePackage.gson.toJson(listOf(shopId, batchId, grouped, members.map { it.identity() }.sorted())))
    companion object {
        fun decode(json: String, imageCount: Int): SquareWishSnapshot {
            require(json.length <= 20 * 1024 * 1024) { "广场来源资料过大" }
            val root = JsonParser.parseString(json).asJsonObject
            require(root.keySet() == setOf("version", "shopId", "shopName", "eventId", "designId", "batchId", "title", "grouped", "members")) { "广场来源字段无效" }
            require(root["version"].asBigDecimal.intValueExact() == 1 && root["grouped"].asJsonPrimitive.isBoolean)
            val result = SquarePackage.gson.fromJson(root, SquareWishSnapshot::class.java)
            require(result.version == 1 && result.members.size in 1..20 && (if(result.grouped) result.members.size > 1 else result.members.size == 1)) { "广场来源版本或组合无效" }
            require(listOf(result.shopId, result.eventId, result.designId, result.batchId).all { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) })
            require(result.shopName.length in 1..200 && result.title.length in 1..500)
            val memberFields = setOf("browseKey", "partKey", "name", "color", "size", "category", "photos", "documents", "evidence", "facts", "origins", "sizeTable")
            root["members"].asJsonArray.forEach { element ->
                require(element.asJsonObject.keySet().all { it in memberFields }) { "广场单品字段无效" }
                element.asJsonObject["sizeTable"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let { table ->
                    require(table.keySet() == setOf("columns", "rows", "note")) { "尺码表字段无效" }
                    require(table["columns"].asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
                    table["rows"].asJsonArray.forEach { row ->
                        require(row.asJsonObject.keySet() == setOf("size", "values")) { "尺码表行字段无效" }
                        require(row.asJsonObject["values"].asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
                    }
                }
                element.asJsonObject["origins"].asJsonArray.forEach { origin ->
                    require(origin.asJsonObject.keySet().all { it in setOf("productId", "url", "capturedAt", "phase", "priceType", "candidates", "selectedSkuKey", "quote", "quoteAppliesToMember") })
                    origin.asJsonObject["candidates"].asJsonArray.forEach { sku ->
                        require(sku.asJsonObject.keySet().all { it in setOf("key", "variantId", "color", "size", "price", "properties", "legacyToken") })
                    }
                }
            }
            result.members.forEach { m ->
                require(m.browseKey.length in 1..100 && m.partKey.length in 1..100 && m.name.length in 1..500 && m.color.length <= 500 && m.size.length <= 500 && (m.category == null || m.category.length <= 200))
                m.sizeTable?.validate()
                require(m.facts.size <= 100 && m.facts.all { it.length <= 12000 })
                val refs = m.photos + m.documents + m.evidence
                require(refs.size <= 250 && refs.all { it in 0 until imageCount } && m.photos.distinct().size == m.photos.size && m.documents.distinct().size == m.documents.size)
                require(m.origins.size in 1..20 && m.origins.all { it.candidates.size in 1..1000 })
                m.origins.forEach { o ->
                    require(o.productId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && o.url == OrderImportSource.publicUrl(o.url) && o.capturedAt.length in 1..80 && o.phase in setOf("FULL", "DEPOSIT", "BALANCE", "PLACEHOLDER") && o.priceType.length in 1..500)
                    require(o.phase != "BALANCE") { "心愿来源不能是尾款" }
                    require(o.selectedSkuKey == null || o.candidates.any { it.key == o.selectedSkuKey })
                    require(o.quote == null || o.quote.toBigDecimalOrNull()?.let { it.signum() >= 0 && it <= 100_000_000.toBigDecimal() } == true)
                    o.candidates.forEach { s ->
                        require(s.key.startsWith(o.productId + ":") && s.key.length <= 201 && s.variantId.length in 1..100 && (s.color == null || s.color.length <= 500) && (s.size == null || s.size.length <= 500))
                        require(s.price == null || s.price.toBigDecimalOrNull()?.let { it.signum() >= 0 && it <= 100_000_000.toBigDecimal() } == true)
                        require(s.properties.size in 1..12 && s.properties.all { it.key.length in 1..100 && it.value.length in 1..500 })
                        require(s.legacyToken.startsWith("square:") && s.legacyToken.length <= 100)
                    }
                }
            }
            require(result.members.map { it.identity() }.distinct().size == result.members.size)
            return result
        }
    }
}
data class SquareWishMember(val browseKey: String, val partKey: String, val name: String,
    val color: String, val size: String, val category: String?, val photos: List<Int>, val documents: List<Int>,
    val evidence: List<Int>, val facts: List<String>, val origins: List<SquareWishOrigin>, val sizeTable: SquareSizeTable? = null) {
    fun identity() = SquarePackage.gson.toJson(listOf(browseKey, color, size))
    fun overlaps(other: SquareWishMember): Boolean = partKey == other.partKey &&
        (browseKey == other.browseKey || origins.flatMap { it.candidates }.any { a -> other.origins.flatMap { it.candidates }.any { it.variantId == a.variantId } }) &&
        (color == other.color || color.isEmpty() || other.color.isEmpty()) && (size == other.size || size.isEmpty() || other.size.isEmpty())
}
data class SquareWishOrigin(val productId: String, val url: String, val capturedAt: String,
    val phase: String, val priceType: String, val candidates: List<SquareWishSku>,
    val selectedSkuKey: String?, val quote: String?, val quoteAppliesToMember: Boolean)
data class SquareWishSku(val key: String, val variantId: String, val color: String?, val size: String?,
    val price: String?, val properties: Map<String, String>, val legacyToken: String)
