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
    val imagePaths: List<String> = emptyList()
)

object TaobaoProductParser {
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
            val brand = values["品牌"].orEmpty()
            val properties = values.entries.joinToString("；") { "${it.key}：${it.value}" }
            val pageText = root["text"]?.asString.orEmpty()
            val text = pageText.substringAfter("宝贝详情", pageText).substringBeforeLast("打开淘宝").trim().take(6000)
            val productUrl = "https://item.taobao.com/item.htm?id=$id"
            return TaobaoProductDraft(title, brand, values["尺码"].orEmpty(), properties,
                listOf("淘宝商品：$productUrl", "网页报价（仅供核对，不代表实付）：$priceLabel $priceText", properties, text).filter { it.isNotBlank() }.joinToString("\n"), productUrl, images)
        } catch (e: Exception) {
            throw IllegalArgumentException("尚未读取到完整商品资料，请完成登录、等待商品加载后重试", e)
        }
    }
}
