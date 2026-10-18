package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Category
import java.text.Normalizer
import java.util.Locale

/** One-time legacy repair and backup import only; renaming never changes a confirmed identity. */
object HistoricalCategoryRecognition {
    private data class Match(val semantic: CategorySemantic, val role: TryOnRole? = null)

    private fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.lowercase(Locale.ROOT)

    private val names: Map<String, Match> = buildMap {
        CategorySemantic.entries.filter { it != CategorySemantic.UNCONFIRMED }.forEach {
            put(key(it.label), Match(it))
        }
        fun aliases(semantic: CategorySemantic, vararg labels: String, role: TryOnRole? = null) {
            labels.forEach { put(key(it), Match(semantic, role)) }
        }
        aliases(CategorySemantic.JSK, "吊带裙", "背带裙", "jumper skirt", "JSK裙")
        aliases(CategorySemantic.OP, "有袖连衣裙", "one piece", "OP裙")
        aliases(CategorySemantic.SK, "半裙", "半身裙", "skirt")
        aliases(CategorySemantic.BLOUSE, "衬衣", "blouse")
        aliases(CategorySemantic.TROUSERS, "裤子", "长裤", "短裤")
        aliases(CategorySemantic.OTHER_HOSIERY, "袜子", "袜装")
        aliases(CategorySemantic.OTHER_FOOTWEAR, "鞋", "鞋子", "鞋履")
        aliases(CategorySemantic.OTHER_BAG, "包", "包包", "包袋")
        aliases(CategorySemantic.OTHER_ACCESSORY, "头饰", "其他头饰", role = TryOnRole.HEAD)
        aliases(CategorySemantic.OTHER_ACCESSORY, "颈饰", role = TryOnRole.NECK_CHEST)
        aliases(CategorySemantic.EARRING, "耳环")
    }

    fun recognize(category: Category): Category {
        if (category.categorySemantic != CategorySemantic.UNCONFIRMED) return category
        val match = names[key(category.name)] ?: return category
        return category.copy(categorySemantic = match.semantic,
            defaultWearRole = category.defaultWearRole ?: match.role)
    }
}
