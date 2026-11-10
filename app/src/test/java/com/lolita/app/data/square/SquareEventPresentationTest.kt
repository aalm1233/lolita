package com.lolita.app.data.square

import org.junit.Assert.*
import org.junit.Test

class SquareEventPresentationTest {
    private fun event(date: String?, kind: String = "上新", state: String? = "已开售", title: String = "商品") =
        SquareEvent("e", 1, "s", "d", "b", title, null, kind, state, date, null, null, null, null, null, "2026-11-09", listOf("p"))
    private fun time(raw: String?, kind: String = "上新") = SquareEventPresentation.time(event(raw, kind), "2026-11-10")

    @Test fun imageMonthDayAndClockAreVisibleWithoutInventingYear() {
        val t = time("10月30日 20:00 开售")
        assertEquals("开售 10月30日 20:00", t.summary); assertFalse(t.yearExplicit); assertFalse(t.fromCapture)
        assertTrue(t.detail.contains("年份")); assertFalse(t.value.contains("2026"))
    }
    @Test fun datedPeriodsAndMixedRangeYearAreNotDiscardedAsUnknown() {
        assertEquals("预约 2026-10-17—2026-11-17", time("三团预约 2026-10-17 至 2026-11-17", "再贩").summary)
        assertEquals("预约 2026/10/30—11/20", time("2026/10/30-11/20（定金/全款预售下单）").summary)
        assertEquals("补尾 10.25—11.15", time("10.25-11.15（标题原文补尾期，未注明年份）", "尾款").summary)
        assertEquals("尾款截止 11月12日 17:00", time("11月12日 17:00 截止", "尾款").summary)
    }
    @Test fun historicalReservationIsNotRelabelledCurrentRelease() {
        val t = time("详情图写“预约时间：2026.02.16 晚19:00开始”；早于本轮采集日，未推定新一轮日期；首发日期未知", "再贩")
        assertEquals("历史预约", t.label); assertEquals("2026.02.16 晚19:00", t.value)
        assertTrue(t.detail.contains("未推定新一轮"))
    }
    @Test fun captureAndShippingAreNeverShownAsLaunch() {
        for (raw in listOf(null, "上架日期未提供", "本次可核实：2026-11-09 页面在售；首发/首次上架日期未取得", "预计2026年11月15日发货；首发日期未取得")) {
            val t = time(raw); assertEquals("采集 2026-11-09", t.summary); assertTrue(t.fromCapture)
        }
        assertEquals("开售 11月11日 20:00", time("开售11月11日 20:00，2026-11-09采集").summary)
    }
    @Test fun invalidDatesStayUnknownAndCaptureOffsetUsesShanghaiDay() {
        assertEquals("采集", time("2026-02-30开售").label)
        val t = SquareEventPresentation.time(event(null).copy(captured="2026-11-09T20:30:00Z"), "2026-11-10")
        assertEquals("采集 2026-11-10", t.summary)
        assertEquals("日期 未提供", SquareEventPresentation.time(event(null).copy(captured=null), "bad").summary)
    }
    @Test fun historicalChineseHourRangeAndMultipleBatchesKeepTheirScope() {
        assertEquals("历史预约 2024年5月30日 上午10时—2024年6月27日 下午17时", time("历史预约：2024年5月30日 上午10时—2024年6月27日 下午17时；本轮现货日期未提供").summary)
        assertEquals("历史预约 多个图示批次", time("历史预约A：2025年7月16日—2025年8月14日；历史预约B：2025年5月22日—2025年6月24日").summary)
        assertEquals("预约 11月11日 20:00", time("历史预约：2025年5月22日；本轮预约：11月11日 20:00").summary)
    }
    @Test fun currentDropGoesToSpotWithHistoricalRereleaseBadge() {
        val e = event("06月12日20:00开启预售", "再贩", "再贩·跑单掉落·全款预售（部分规格无货）", "2025维多利亚花园 · 再贩掉落")
        assertEquals("现货", SquareEventPresentation.kind(e)); assertEquals("现货 · 再贩", SquareEventPresentation.badge(e))
        assertEquals("现货", SquareEventPresentation.kind(e.copy(kind="现货")))
        assertTrue(SquareEventPresentation.rerelease(e.copy(kind="现货")))
    }
    @Test fun activePreorderAndNegativeOrHistoricalSpotAreNotMoved() {
        for (state in listOf("定金预约中", "预约中（历史现货已售罄）", "原为现货，已转预约", "预热 · 11月11日开售", "暂无现货，等待再贩"))
            assertEquals(state, "再贩", SquareEventPresentation.kind(event(null,"再贩",state)))
        assertEquals("尾款", SquareEventPresentation.kind(event(null,"尾款","现货 · 补尾中")))
        assertEquals("上新", SquareEventPresentation.kind(event(null,"上新","已开售")))
    }
    @Test fun realPublicConflictsAllMoveToSpotAndFilterAgrees() {
        val c = javaClass.getResourceAsStream("/square-polish.json")!!.bufferedReader().use { SquarePackage.decode(it.readText()) }
        val e = c.events.single { it.id == "jixiangtian-2025-repeat-drop" }
        assertTrue(e in SquareBrowseFilter(kind="现货").events(c))
        assertFalse(e in SquareBrowseFilter(kind="再贩").events(c))
        assertEquals("采集", SquareEventPresentation.time(e, c.capturedAt).label)
        val dep = c.events.single { it.id == "angelo-villainess-elegance-deposit" }
        assertTrue(dep in SquareBrowseFilter(kind="上新").events(c))
    }
}
