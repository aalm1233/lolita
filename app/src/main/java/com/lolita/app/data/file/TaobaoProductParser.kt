package com.lolita.app.data.file

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

data class TaobaoProductVariant(val id: String, val name: String, val category: String = "", val referenceQuote: TaobaoProductQuote? = null)

data class TaobaoProductDraft(
    val name: String,
    val brand: String,
    val size: String,
    val properties: String,
    val description: String,
    val productUrl: String,
    val remoteImages: List<String>,
    val imagePaths: List<String> = emptyList(),
    val shopName: String = "",
    val brandAliases: List<String> = emptyList(),
    val category: String = "",
    val categoryCandidates: List<String> = emptyList(),
    val remoteDetailImages: List<String> = emptyList(),
    val sizeChartImagePath: String? = null,
    val sizeChartImagePaths: List<String> = emptyList(),
    val variants: List<TaobaoProductVariant> = emptyList(),
    val importToken: String? = null,
    val editorToken: String? = null,
    val batchIndex: Int = 0,
    val batchTotal: Int = 0,
    val referenceQuote: TaobaoProductQuote? = null
)

class TaobaoProductDetailsNotReadyException : IllegalArgumentException("详情尚未加载完整，请打开商品详情、等待加载后重新提取，避免漏掉详情图片")

object TaobaoProductParser {
    fun normalizedBrand(name: String): String = name.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    fun matchBrand(existing: List<String>, aliases: List<String>): String? {
        val names = aliases.filter { it.isNotBlank() }.map(::normalizedBrand).toSet()
        return existing.filter { normalizedBrand(it) in names }.singleOrNull()
    }
    fun explicitCategoryCandidates(title: String, properties: Map<String, String>): List<String> {
        val text = listOf(properties["类型"], properties["类别"], properties["款式"], properties["套装"], title).filterNotNull().joinToString(" ")
        val result = mutableListOf<String>()
        val explicit = listOf("全头假发", "刘海片", "假马尾", "假辫", "发片", "短袜", "中筒袜", "及膝袜", "过膝袜", "连裤袜", "袜套",
            "玛丽珍鞋", "茶会鞋", "浅口鞋", "短靴", "长靴", "裙撑", "手提包", "斜挎包", "背包", "手拿包", "阳伞", "扇子", "手杖", "玩偶", "花束")
        explicit.filter { it in text }.sortedByDescending { it.length }.forEach { key ->
            if (result.none { it.contains(key) }) result += key
        }

        for (key in listOf("JSK", "OP", "SK", "KC", "Bonnet")) {
            if (Regex("(?i)(?<![a-z])$key(?![a-z])").containsMatchIn(text)) result += key
        }
        for (key in listOf("斗篷", "披肩", "发带", "袜子", "手套")) if (key in text) result += key
        if ("头饰" in text || "头巾" in text) result += "其他头饰"
        if ("皇冠" in text) result += "其他头饰"
        if ("衬衫" in text) result += "衬衫"
        if ("背带裤" in text) result += "裤子"
        if (listOf("假领", "男款SET", "颈饰", "手袖", "腿环").any { it in text }) result += "其他配饰"
        return result.distinct()
    }

    private fun JsonObject.text(key: String): String = get(key)
        ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()

    private fun variantCategory(name: String): String {
        // A mixed listing's OP title must not classify its men's set or accessories.
        if ("男款" in name && Regex("(?i)(?<![a-z])SET(?![a-z])").containsMatchIn(name)) return ""
        return explicitCategoryCandidates(name, emptyMap()).firstOrNull().orEmpty()
    }

    private fun variants(data: JsonObject, parameters: Map<String, String>): List<TaobaoProductVariant> {
        val skuCandidates = runCatching {
            val raw = data.getAsJsonObject("skuData")?.get("skuV3Data")
                ?: return@runCatching emptyList<TaobaoProductVariant>()
            val sku = JsonParser.parseString(raw.asString).asJsonObject
            val props = sku.getAsJsonObject("global")?.getAsJsonObject("skuGlobal")
                ?.getAsJsonObject("context")?.getAsJsonObject("skuBase")?.getAsJsonArray("props")
                ?: return@runCatching emptyList<TaobaoProductVariant>()
            // These dimensions name separate pieces. Color and size dimensions stay in one piece.
            for (propertyName in listOf("套装", "款式", "类型")) {
                val candidates = props.filter { it.isJsonObject && it.asJsonObject.text("name") == propertyName }
                    .flatMap { property ->
                        val prop = property.asJsonObject
                        val pid = prop.text("pid").ifBlank { propertyName }
                        prop.getAsJsonArray("values")?.mapNotNull { value ->
                            if (!value.isJsonObject) return@mapNotNull null
                            val row = value.asJsonObject
                            val name = row.text("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val vid = row.text("vid")
                            TaobaoProductVariant("sku:$pid:$vid:$name", name, variantCategory(name))
                        }.orEmpty()
                    }
                if (candidates.isNotEmpty()) return@runCatching candidates
            }
            val color = props.firstOrNull { it.isJsonObject && it.asJsonObject.text("name") == "颜色分类" }?.asJsonObject
            val names = color?.getAsJsonArray("values")?.map { it.asJsonObject.text("name") }.orEmpty()
            TaobaoProductPieces.colorComponents(names).map { name ->
                TaobaoProductVariant("sku:${color?.text("pid")}:component:$name", name, variantCategory(name))
            }
        }.getOrDefault(emptyList())
        val candidates = skuCandidates.ifEmpty {
            parameters["套装"].orEmpty().split(Regex("[,，、;；\\n\\r]+"))
                .map(String::trim).filter(String::isNotBlank).map { name ->
                    TaobaoProductVariant("params:套装:$name", name, variantCategory(name))
                }
        }.distinctBy { it.name }.take(30)
        return candidates.takeIf { it.size > 1 }.orEmpty()
    }

    private fun selectedQuote(root: JsonObject, data: JsonObject): Pair<TaobaoProductQuote, List<String>>? = runCatching {
        val sku = data.getAsJsonObject("skuData")?.text("skuV3Data")?.takeIf(String::isNotBlank)
            ?.let { JsonParser.parseString(it).asJsonObject }
        val context = sku?.getAsJsonObject("global")?.getAsJsonObject("skuGlobal")?.getAsJsonObject("context")
        val base = context?.getAsJsonObject("skuBase")
        val props = base?.getAsJsonArray("props")?.map { it.asJsonObject }.orEmpty()
        val live = root.getAsJsonObject("liveSku")?.takeIf { it.get("present")?.asBoolean == true }
        val names: List<String>
        val price: JsonObject
        if (live != null) {
            names = live.getAsJsonArray("selectedNames")?.map { it.asString }.orEmpty()
            // A price shown before all dimensions are selected is a range or a stale combination.
            if (props.isEmpty() || names.size != props.size || props.any { prop ->
                prop.getAsJsonArray("values").count { it.asJsonObject.text("name") in names } != 1
            }) return@runCatching null
            if (live.has("selectedText")) {
                val selectedText = live.text("selectedText")
                if (!selectedText.startsWith("已选“") || !selectedText.endsWith("”") ||
                    selectedText.removePrefix("已选“").removeSuffix("”").split('；', ';') != names)
                    return@runCatching null
            }
            price = live
        } else if (base != null && props.isNotEmpty()) {
            if (base.text("selectedPathStatus") != "2") return@runCatching null
            val selected = base.text("selectedPath").split(';').toSet()
            names = props.map { prop ->
                prop.getAsJsonArray("values").singleOrNull { "${prop.text("pid")}:${it.asJsonObject.text("vid")}" in selected }
                    ?.asJsonObject?.text("name") ?: return@runCatching null
            }
            val head = context.getAsJsonObject("simpleSkuHead") ?: return@runCatching null
            price = head.getAsJsonObject("subPrice")?.takeIf { it.text("priceText").isNotBlank() }
                ?: head.getAsJsonObject("price") ?: return@runCatching null
        } else {
            // A plain price may describe a single item, but never assign it to split variants.
            names = emptyList()
            price = data.getAsJsonObject("priceVO")?.getAsJsonObject("price") ?: return@runCatching null
        }
        val label = price.text("priceTitle")
        if (price.text("priceTail").isNotBlank() || listOf(label, price.text("priceSummary"), names.joinToString(" ")).any {
            listOf("定金", "订金", "尾款", "补款", "补尾", "预约").any { word -> word in it }
        }) return@runCatching null
        if (Regex("\\d\\s*[-~～至]\\s*\\d").containsMatchIn(price.text("priceSummary"))) return@runCatching null
        val amount = TaobaoProductQuotes.amount(price.text("priceText")) ?: return@runCatching null
        val style = listOf("套装", "款式", "类型").firstNotNullOfOrNull { propertyName ->
            props.firstOrNull { it.text("name") == propertyName }
        } ?: props.firstOrNull { it.text("name") == "颜色分类" &&
            TaobaoProductPieces.colorComponents(it.getAsJsonArray("values").map { value -> value.asJsonObject.text("name") }).isNotEmpty() }
        val styleNames = if (style == null) emptyList() else if (live == null)
            listOf(names[props.indexOf(style)]) else listOf(style.getAsJsonArray("values")
                .map { it.asJsonObject.text("name") }.single { it in names })
        TaobaoProductQuote(amount, names.joinToString("；"), label) to styleNames
    }.getOrNull()

    private fun uri(url: String) = runCatching { URI(url) }.getOrNull()
    fun allowedPage(url: String): Boolean {
        val u = uri(url) ?: return false
        val host = u.host?.lowercase() ?: return false
        return u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) &&
            (host == "e.tb.cn" || host == "taobao.com" || host.endsWith(".taobao.com") ||
                host == "tmall.com" || host.endsWith(".tmall.com"))
    }
    fun shareUrl(text: String): String? = Regex("https?://[^\\s「」<>\"]+").findAll(text)
        .map { it.value.trimEnd('，', '。') }.firstOrNull { url ->
            if (!allowedPage(url)) false else {
                val u = uri(url)!!
                u.host == "e.tb.cn" || (u.host in setOf("item.taobao.com", "detail.tmall.com", "h5.m.taobao.com", "pages-g.m.taobao.com") &&
                    Regex("(?:^|&)id=\\d+(?:&|$)").containsMatchIn(u.rawQuery.orEmpty()))
            }
        }
    fun allowedImage(url: String): Boolean {
        val u = uri(url) ?: return false
        return u.scheme == "https" && u.userInfo == null && (u.port == -1 || u.port == 443) &&
            (u.host == "alicdn.com" || u.host?.endsWith(".alicdn.com") == true)
    }
    fun parse(raw: String): TaobaoProductDraft {
        try {
            require(raw.length <= 4_000_000) { "商品资料过大" }
            val root = JsonParser.parseString(raw).asJsonObject
            if (root.text("detailStatus") == "pending") throw TaobaoProductDetailsNotReadyException()
            val url = root["url"].asString
            require(allowedPage(url))
            val id = Regex("(?:^|&)id=(\\d+)(?:&|$)").find(uri(url)?.rawQuery.orEmpty())?.groupValues?.get(1)
                ?: error("商品链接缺少编号")
            val data = root["entry"].asJsonObject["apiStack"].asJsonArray[0].asJsonObject["data"].asJsonObject
            val title = data["titleVO"].asJsonObject["title"].asString.trim()
            require(title.isNotEmpty())
            val props = data.getAsJsonObject("paramsVO")?.getAsJsonArray("groupProps")
            val values = linkedMapOf<String, String>()
            props?.forEach { group -> group.asJsonObject.entrySet().forEach { (_, rows) ->
                rows.asJsonArray.forEach { row -> row.asJsonObject.entrySet().forEach { (k, v) -> values[k] = v.asString } }
            } }
            val price = data.getAsJsonObject("priceVO")?.getAsJsonObject("price")
            val priceText = price?.get("priceText")?.asString.orEmpty()
            val priceLabel = price?.get("priceTitle")?.asString.orEmpty()
            val priceTail = price?.get("priceTail")?.asString.orEmpty()
            val selected = selectedQuote(root, data)
            val parts = variants(data, values).map { variant -> variant.copy(
                referenceQuote = selected?.takeIf { variant.name in it.second }?.first) }
            val referenceQuote = selected?.takeIf { parts.isEmpty() || it.second.isNotEmpty() }?.first
            val images = data.getAsJsonObject("headPicVO")?.getAsJsonArray("images")?.mapNotNull {
                it.asJsonObject["url"]?.asString?.let { value -> if (value.startsWith("//")) "https:$value" else value }
                    ?.takeIf(::allowedImage)
            }?.distinct()?.take(5).orEmpty()
            val brand = values["品牌"].orEmpty().takeUnless { normalizedBrand(it) in setOf("other其他", "other", "其他", "无品牌") }.orEmpty()
            val shopName = data.getAsJsonObject("featureVO")?.getAsJsonObject("seller")?.get("shopName")?.asString.orEmpty()
            val categoryCandidates = explicitCategoryCandidates(title, values)
            val detailImages = root.getAsJsonArray("detailImages")?.mapNotNull { image ->
                val value = image.asJsonObject["url"]?.asString.orEmpty()
                val url = if (value.startsWith("//")) "https:$value" else value
                url.takeIf { allowedImage(it) && !it.contains("-2-tps-2-2") && !it.contains("-2-tps-790-300") }
            }?.distinct().orEmpty()
            require(detailImages.size <= 50) { "详情图片超过 50 张，请分批手动添加" }
            val properties = values.entries.joinToString("；") { "${it.key}：${it.value}" }
            val pageText = root["text"]?.asString.orEmpty()
            val text = pageText.substringAfter("宝贝详情", pageText).substringBeforeLast("打开淘宝").trim().take(6000)
            val productUrl = "https://item.taobao.com/item.htm?id=$id"
            return TaobaoProductDraft(title, brand, values["尺码"].orEmpty(), properties,
                TaobaoProductQuotes.withQuote(listOf("淘宝商品：$productUrl", "${if (priceTail.contains("起")) "页面起价" else "页面报价"}（仅供核对，不代表各款式实付）：$priceLabel $priceText$priceTail", properties, text).filter { it.isNotBlank() }.joinToString("\n"), referenceQuote), productUrl, images, shopName = shopName, brandAliases = listOf(brand).filter { it.isNotBlank() },
                category = categoryCandidates.firstOrNull().orEmpty(), categoryCandidates = categoryCandidates, remoteDetailImages = detailImages, variants = parts, referenceQuote = referenceQuote)
        } catch (e: Exception) {
            if (e is TaobaoProductDetailsNotReadyException) throw e
            throw IllegalArgumentException("尚未读取到完整商品资料，请完成登录、等待商品加载后重试", e)
        }
    }
}
