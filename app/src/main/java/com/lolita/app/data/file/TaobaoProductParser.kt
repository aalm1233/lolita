package com.lolita.app.data.file

import com.google.gson.JsonParser
import java.net.URI

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
    val sizeChartImagePaths: List<String> = emptyList()
)

object TaobaoProductParser {
    fun normalizedBrand(name: String): String = name.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    fun matchBrand(existing: List<String>, aliases: List<String>): String? {
        val names = aliases.filter { it.isNotBlank() }.map(::normalizedBrand).toSet()
        return existing.filter { normalizedBrand(it) in names }.singleOrNull()
    }
    private fun categories(title: String, properties: Map<String, String>): List<String> {
        val text = listOf(properties["类型"], properties["类别"], properties["款式"], properties["套装"], title).filterNotNull().joinToString(" ")
        val result = mutableListOf<String>()
        for (key in listOf("JSK", "OP", "SK", "KC", "Bonnet")) {
            if (Regex("(?i)(?<![a-z])$key(?![a-z])").containsMatchIn(text)) result += key
        }
        for (key in listOf("斗篷", "披肩", "发带", "袜子", "手套")) if (key in text) result += key
        if ("头饰" in text || "头巾" in text) result += "其他头饰"
        if ("假领" in text || "男款SET" in text) result += "其他配饰"
        return result.distinct()
    }
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
            val images = data.getAsJsonObject("headPicVO")?.getAsJsonArray("images")?.mapNotNull {
                it.asJsonObject["url"]?.asString?.let { value -> if (value.startsWith("//")) "https:$value" else value }
                    ?.takeIf(::allowedImage)
            }?.distinct()?.take(5).orEmpty()
            val brand = values["品牌"].orEmpty().takeUnless { normalizedBrand(it) in setOf("other其他", "other", "其他", "无品牌") }.orEmpty()
            val shopName = data.getAsJsonObject("featureVO")?.getAsJsonObject("seller")?.get("shopName")?.asString.orEmpty()
            val categoryCandidates = categories(title, values)
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
                listOf("淘宝商品：$productUrl", "网页报价（仅供核对，不代表实付）：$priceLabel $priceText", properties, text).filter { it.isNotBlank() }.joinToString("\n"), productUrl, images, shopName = shopName, brandAliases = listOf(brand).filter { it.isNotBlank() },
                category = categoryCandidates.firstOrNull().orEmpty(), categoryCandidates = categoryCandidates, remoteDetailImages = detailImages)
        } catch (e: Exception) {
            throw IllegalArgumentException("尚未读取到完整商品资料，请完成登录、等待商品加载后重试", e)
        }
    }
}
