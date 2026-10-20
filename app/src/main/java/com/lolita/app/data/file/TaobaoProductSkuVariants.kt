package com.lolita.app.data.file

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Public product specifications only. A choice is not an assertion about stock or a purchase. */
internal object TaobaoProductSkuVariants {
    private const val MAX_OPTIONS = 1_000
    private val sizeNames = setOf("尺码", "鞋码", "尺寸", "服装尺码", "尺码分类")
    data class Result(val variants: List<TaobaoProductVariant> = emptyList(), val issue: String? = null)
    private data class Value(val token: String, val name: String)
    private data class Dimension(val pid: String, val name: String, val values: List<Value>)
    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
    fun optionId(tokens: Collection<String>) = "sku-option:" + tokens.sorted().joinToString(";")

    fun parse(data: JsonObject, category: String, resolveCategory: (String) -> String): Result {
        val raw = runCatching { data.getAsJsonObject("skuData")?.text("skuV3Data") }.getOrNull()
            ?.takeIf(String::isNotBlank) ?: return Result()
        return try {
            val base = JsonParser.parseString(raw).asJsonObject.getAsJsonObject("global")
                ?.getAsJsonObject("skuGlobal")?.getAsJsonObject("context")?.getAsJsonObject("skuBase") ?: return Result()
            val props = base.getAsJsonArray("props") ?: return Result()
            if (props.none { it.isJsonObject && it.asJsonObject.text("name") in sizeNames }) return Result()
            val dimensions = props.map { element ->
                val prop = element.asJsonObject
                val pid = prop.text("pid"); require(pid.isNotBlank() && ':' !in pid && ';' !in pid)
                val values = prop.getAsJsonArray("values").map { row ->
                    val value = row.asJsonObject
                    val vid = value.text("vid"); val name = value.text("name")
                    require(vid.isNotBlank() && ':' !in vid && ';' !in vid && name.isNotBlank())
                    Value("$pid:$vid", name)
                }
                require(values.isNotEmpty() && values.map { it.token }.distinct().size == values.size &&
                    values.map { it.name }.distinct().size == values.size)
                Dimension(pid, prop.text("name"), values)
            }
            require(dimensions.size <= 6 && dimensions.map { it.pid }.distinct().size == dimensions.size)
            val size = dimensions.single { it.name in sizeNames }
            val ordered = dimensions.filter { it != size } + size
            val rows: List<List<Value>>
            if (base.has("skus")) {
                val skus = base.getAsJsonArray("skus")
                if (skus.size() > MAX_OPTIONS) return tooMany()
                rows = skus.map { element ->
                    val row = element.asJsonObject
                    val path = row.text("propPath").ifBlank { row.text("propsPath") }
                    val tokens = path.split(';').map(String::trim).filter(String::isNotBlank)
                    require(tokens.size == dimensions.size && tokens.distinct().size == tokens.size)
                    val values = ordered.map { dimension -> dimension.values.single { it.token in tokens } }
                    require(values.map { it.token }.toSet() == tokens.toSet())
                    values
                }.distinct()
            } else {
                // Some modern pages publish only independent options. Keep them as user-confirmed
                // candidates; never infer an in-stock SKU or copy a quote between combinations.
                var count = 1L
                ordered.forEach { count *= it.values.size; if (count > MAX_OPTIONS) return tooMany() }
                rows = ordered.fold(listOf(emptyList())) { combinations, dimension ->
                    combinations.flatMap { previous -> dimension.values.map { previous + it } }
                }
            }
            if (rows.isEmpty()) return Result(issue = "未读到完整尺码组合，可重新提取，或进入单件编辑核对尺码。")
            // The same merchant labels repeat in each candidate and SavedState. Bound that
            // expansion before allocating labels, as well as bounding the number of options.
            val expandedText = rows.sumOf { row -> row.sumOf { it.name.length.toLong() * 2 + it.token.length } + 160 }
            if (expandedText > 180_000) return tooMany()
            val order = ordered.map { dimension -> dimension.values.map { it.token } }
            val styleIndex = listOf("套装", "款式", "类型").firstNotNullOfOrNull { name ->
                ordered.indexOfFirst { it.name == name }.takeIf { it >= 0 }
            }
            val sorted = rows.sortedWith { left, right ->
                ordered.indices.firstNotNullOfOrNull { index ->
                    order[index].indexOf(left[index].token).compareTo(order[index].indexOf(right[index].token)).takeIf { it != 0 }
                } ?: 0
            }
            Result(sorted.map { values ->
                val group = values.dropLast(1).joinToString(" · ") { it.name }
                val label = values.joinToString(" · ") { it.name }
                TaobaoProductVariant(optionId(values.map { it.token }), label,
                    if (styleIndex == null) category else resolveCategory(values[styleIndex].name),
                    size = values.last().name, specificationGroup = group)
            })
        } catch (_: Exception) {
            Result(issue = "尺码组合尚未读完整，可重新提取，或进入单件编辑核对尺码。")
        }
    }

    private fun tooMany() = Result(issue = "规格组合较多，请进入单件编辑核对尺码；本次未截取部分组合。")
}
