package com.lolita.app.data.square

import java.time.LocalDate
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneId

/** Current purchasing state and historical release identity are different facts. */
object SquareEventPresentation {
    fun kind(e: SquareEvent): String {
        if (!e.canRegister) return "尾款"
        val state = e.state.orEmpty().trim()
        val main = state.substringBefore('（').substringBefore('(')
        val negative = listOf("非现货", "不是现货", "无现货", "现货已售罄", "现货售罄", "暂无现货", "现货结束", "已转预约", "转为预约")
        val historical = listOf("历史现货", "原为现货", "曾经现货", "旧现货")
        val currentSpot = (listOf("现货", "余量", "跑单", "掉落").any(main::contains) || state.contains("当前页面现货")) &&
            negative.none(state::contains) && historical.none(main::contains) &&
            listOf("预约中", "定金预约", "预热", "待开售").none(main::contains)
        return if (currentSpot) "现货" else e.kind
    }

    fun rerelease(e: SquareEvent): Boolean = e.kind == "再贩" ||
        (e.title.contains("再贩") && !e.title.contains("非再贩"))

    fun badge(e: SquareEvent): String = kind(e).let { current ->
        if (current == "现货" && rerelease(e)) "现货 · 再贩" else current
    }

    fun time(e: SquareEvent, capturedAt: String): SquareEventTime {
        val known = businessTime(e.date, e.kind)
        val capture = capturedDate(e.captured) ?: capturedDate(capturedAt)
        val raw = e.date?.takeIf { it.isNotBlank() }
        val detail = listOfNotNull(raw?.let { "时间原文：$it" },
            known?.takeUnless { it.yearExplicit }?.let { "原文未注明年份，按原月日显示。" },
            capture?.let { "采集：$it（不代表上架或首次开售时间）" }).joinToString("\n")
        return known?.copy(detail = detail) ?: if (capture != null) SquareEventTime("采集", capture, detail, true, true)
            else SquareEventTime("日期", "未提供", detail.ifBlank { "未取得上架、开售或采集日期。" }, false)
    }

    private val day = Regex("(?<![\\d.])(?:(?:19|20)\\d{2}[-/.年]\\s*)?\\d{1,2}(?:月\\s*\\d{1,2}日?|[./-]\\d{1,2})(?:\\s*(?:晚|晚上|早|上午|下午|凌晨)?\\s*\\d{1,2}(?:[:：]\\d{2}|[点时](?:半|\\d{1,2}分)?))?(?!\\d)")
    private val explicitYear = Regex("^(?:19|20)\\d{2}[-/.年]")
    private val provenance = listOf("采集", "核实", "核验", "页面在售", "观察", "快照")

    internal fun businessTime(raw: String?, kind: String = ""): SquareEventTime? {
        if (raw.isNullOrBlank()) return null
        val history = listOf("历史预约", "旧预约", "早于本轮", "未推定新一轮", "非本轮").any(raw::contains)
        val clauses = raw.split(Regex("[；;。，,\n]"))
        val facts = mutableListOf<SquareEventTime>()
        for (clause in clauses) {
            if (provenance.any(clause::contains) || listOf("出货", "发货", "制作", "交期").any(clause::contains)) continue
            val dates = day.findAll(clause).filter { validDay(it.value) }.toList()
            if (dates.isEmpty()) continue
            val first = dates.first()
            val second = dates.getOrNull(1)?.takeIf {
                clause.substring(first.range.last + 1, it.range.first).trim().matches(Regex("(?:至|到|—|－|-|~|～|–)"))
            }
            val label = when {
                "历史定金" in clause -> "历史定金"
                "图示开售" in clause -> "图示开售"
                "历史开售" in clause -> "历史开售"
                "补尾" in clause -> "补尾"
                "截止" in clause && (kind == "尾款" || "尾款" in clause) -> "尾款截止"
                "尾款" in clause -> "尾款"
                ("预约" in clause || "预售" in clause) && history && "本轮" !in clause -> "历史预约"
                "预约" in clause || "预售" in clause -> "预约"
                "上架" in clause -> "上架"
                "开售" in clause || "首发" in clause -> "开售"
                clause.trim().matches(Regex("^[\\d年月日./:：\\s—至到~～–-]+$")) -> "日期"
                else -> continue
            }
            val value = listOfNotNull(first, second).joinToString("—") { it.value.trim() }
            facts.add(SquareEventTime(label, value, "", explicitYear.containsMatchIn(first.value)))
        }
        val candidates = facts.filterNot { it.label.startsWith("历史") }.ifEmpty { facts }.distinctBy { it.label to it.value }
        return when (candidates.size) {
            0 -> null
            1 -> candidates.single()
            else -> SquareEventTime(candidates.map { it.label }.distinct().singleOrNull() ?: "时间", "多个图示批次", "", candidates.all { it.yearExplicit })
        }
    }

    private fun validDay(value: String): Boolean = runCatching {
        val numbers = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
        if (explicitYear.containsMatchIn(value)) LocalDate.of(numbers[0], numbers[1], numbers[2])
        else MonthDay.of(numbers[0], numbers[1])
        true
    }.getOrDefault(false)

    private fun capturedDate(raw: String?): String? = raw?.let {
        runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate().toString() }.getOrNull()
            ?: runCatching { LocalDate.parse(it).toString() }.getOrNull()
    }
}

data class SquareEventTime(val label: String, val value: String, val detail: String, val yearExplicit: Boolean, val fromCapture: Boolean = false) {
    val summary get() = "$label $value"
}
