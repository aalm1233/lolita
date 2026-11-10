package com.lolita.app.data.square

/** Read-only display projections. Never use these labels or groups as a saved source identity. */
object SquarePresentation {
    fun phase(phase: String?) = when (phase) {
        "FULL" -> "全款"; "DEPOSIT" -> "定金"; "BALANCE" -> "尾款"; else -> "价格阶段未知"
    }

    fun price(rows: List<Pair<SquareProduct, SquareSku>>): String {
        if (rows.isEmpty()) return "报价未确认"
        val preferred = rows.filter { it.first.pricePhase == "FULL" }.ifEmpty { rows }
        val phases = preferred.map { it.first.pricePhase }.distinct()
        if (phases.size != 1) return "多阶段报价"
        val values = preferred.mapNotNull { it.second.price?.toBigDecimalOrNull() }.distinct().sorted()
        if (values.isEmpty()) return "报价未确认 · ${phase(phases.single())}"
        fun money(v: java.math.BigDecimal) = "¥" + v.stripTrailingZeros().toPlainString()
        val amount = if (values.size == 1) money(values.first()) else "${money(values.first())}–${money(values.last())}"
        return "$amount · ${phase(phases.single())}" + (if (preferred.any { it.second.parts.size > 1 || it.second.notSoldAlone }) " · 含组合价" else "") + if (preferred.any { it.second.price?.toBigDecimalOrNull() == null }) " · 部分未知" else ""
    }

    fun date(raw: String?): String {
        val text = raw.orEmpty().trim()
        // Provenance often contains a capture date; it is not the sale/release date.
        return Regex("\\d{4}[-年/.]\\d{1,2}[-月/.]\\d{1,2}日?").matchEntire(text)?.value ?: "日期未知"
    }

    fun partName(e: SquareEvent, p: SquareBrowsePart): String {
        val prefix = e.title.substringBefore(" · ").trim().trimEnd('·', ' ', '-', '—')
        return p.name.removePrefix(prefix).trimStart(' ', '·', '-', '—', '：', ':').ifBlank { p.name }
    }
}

/** A selector group only. Every member keeps its original key and independent wish selection. */
data class SquareDisplayGroup(val label: String, val members: List<SquareBrowsePart>) {
    val key get() = members.first().key
}

fun SquarePresentation.groups(c: SquareCatalog, e: SquareEvent): List<SquareDisplayGroup> {
    val parts = SquareBrowse.parts(c, e)
    val buckets = parts.groupBy { part ->
        val rows = SquareBrowse.rows(c, part)
        val color = SquareBrowse.colors(c, part).singleOrNull()
        val suffix = color?.let { listOf("（$it）", "($it)").firstOrNull(part.name::endsWith) }
        if (suffix == null || rows.map { it.first.id }.distinct().size != 1 || rows.any { it.second.parts.size != 1 || it.second.notSoldAlone }) listOf(part.key)
        else listOf(rows.first().first.id, part.name.removeSuffix(suffix), part.category, part.accessory,
            rows.map { it.second.form }.distinct().sorted(),
            rows.map { it.second.rawProperties.filterKeys { key -> key !in setOf("颜色分类", "颜色", "尺码", "尺寸", "size", "color") }.toSortedMap() }.distinct(),
            rows.map { it.second.parts.single().relation }.distinct())
    }
    return buckets.values.flatMap { members ->
        val colors = members.map { SquareBrowse.colors(c, it).singleOrNull() }
        if (members.size > 1 && colors.none { it == null } && colors.distinct().size == members.size) {
            val color = colors.first()!!
            val label = partName(e, members.first()).removeSuffix("（$color）").removeSuffix("($color)")
            listOf(SquareDisplayGroup(label, members))
        } else members.map { SquareDisplayGroup(partName(e, it), listOf(it)) }
    }
}
