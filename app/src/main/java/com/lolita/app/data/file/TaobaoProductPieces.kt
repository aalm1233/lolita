package com.lolita.app.data.file

/** Only explicit, named garment combinations may reinterpret a merchant's color dimension. */
internal object TaobaoProductPieces {
    private val piece = Regex("(?i)(OP|JSK|SK|KC|Bonnet|颈饰|手袖|衬衫|背带裤|皇冠|腿环|头饰|头巾|假领|斗篷|披肩|袜子|手套)$")
    private val color = Regex("黑|白|红|蓝|灰|绿|棕|粉|紫|黄|杏|奶|咖|橙|生成色|均码|尺码")
    private fun type(name: String): String? {
        if (color.containsMatchIn(name)) return null
        val match = piece.find(name) ?: return null
        if (match.range.first > 0 && name[match.range.first - 1].isLetter() && name[match.range.first - 1].code < 128) return null
        return match.value.uppercase(java.util.Locale.ROOT)
    }
    fun colorComponents(names: List<String>): List<String> {
        if (names.none { '+' in it || '＋' in it }) return emptyList()
        val rows = names.map { name -> name.split('+', '＋').map(String::trim) }
        if (rows.any { row -> row.any { it.isBlank() || type(it) == null } }) return emptyList()
        val singleNames = rows.filter { it.size == 1 }.flatten().groupBy { type(it) }
        val result = rows.flatten().map { name ->
            // A bare combination word may refer to its unique standalone piece. Explicit style
            // prefixes remain distinct even when they share a garment category.
            if (name.uppercase(java.util.Locale.ROOT) == type(name)) singleNames[type(name)]?.distinct()?.singleOrNull() ?: name
            else name
        }.distinct()
        return result.takeIf { result.mapNotNull(::type).distinct().size > 1 }.orEmpty()
    }
}
