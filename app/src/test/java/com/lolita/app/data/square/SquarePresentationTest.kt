package com.lolita.app.data.square

import org.junit.Assert.*
import org.junit.Test

class SquarePresentationTest {
    private fun catalog(): SquareCatalog = javaClass.getResourceAsStream("/square-polish.json")!!.bufferedReader().use {
        SquarePackage.decode(it.readText())
    }
    private fun event(c: SquareCatalog, id: String) = c.events.single { it.id == id }

    @Test fun bunnyGroupsOnlyExplicitColourSuffixesAndKeepsAllOriginalKeys() {
        val c = catalog(); val e = event(c, "bow-bunny-acc-spot")
        val groups = SquarePresentation.groups(c, e)
        assertEquals(4, groups.single { it.label == "经典颈饰" }.members.size)
        assertEquals(2, groups.single { it.label == "SP官配颈饰" }.members.size)
        assertEquals(3, groups.single { it.label == "手袖一对" }.members.size)
        assertEquals(SquareBrowse.parts(c, e).map { it.key }.toSet(), groups.flatMap { it.members }.map { it.key }.toSet())
        assertTrue(groups.size < SquareBrowse.parts(c, e).size)
        val members = groups.single { it.label == "经典颈饰" }.members
        val plans = members.map { SquareWishPlans.build(c, SquareBrowse.request(c, e, listOf(SquareBrowseChoice(it.key)), false)).single() }
        assertEquals(4, plans.map { it.snapshot.identity() }.distinct().size)
    }

    @Test fun displayGroupingNeverCrossesSalesLinksOrExplicitMaterialDimension() {
        val c = catalog(); val e = event(c, "bow-bunny-acc-spot"); val p = c.products.single { it.id == "976892512716" }
        val changed = p.copy(skus = p.skus.map { if (it.parts.single().key == "acc-02") it.copy(rawProperties = it.rawProperties + ("材质" to "丝绸")) else it })
        val cc = c.copy(products = c.products.map { if (it.id == p.id) changed else it })
        assertTrue(SquarePresentation.groups(cc, e).none { g -> g.members.any { it.partKey == "acc-01" } && g.members.any { it.partKey == "acc-02" } })
        assertEquals(2, SquarePresentation.groups(c, event(c, "angelo-villainess-elegance-deposit")).size)
    }

    @Test fun accessoryFilterOpensActualAccessoryAndColourFromMatchingSku() {
        val c = catalog(); val e = event(c, "jixiangtian-2025-repeat-drop")
        val filter = SquareBrowseFilter(shop = e.shopId, form = "小物")
        assertTrue(e in filter.events(c))
        val opening = filter.opening(c, e)!!
        val part = SquareBrowse.parts(c, e).single { it.key == opening.key }
        assertTrue(part.accessory)
        val selected = SquareBrowse.select(c, part, opening)
        assertTrue(selected.rows.any { it.second.form == "小物" })
        assertFalse(part.name.contains("OP"))
        val color = filter.rows(c, e).first().second.color!!
        val colored = filter.copy(color = color).opening(c, e)!!
        val selectedPart = SquareBrowse.parts(c, e).single { it.key == colored.key }
        assertTrue(SquareBrowse.select(c, selectedPart, colored).rows.any { it.second.color == color })
    }

    @Test fun facetCandidatesRespectShopKindAndOtherAttributesWithoutRawPartNames() {
        val c = catalog(); val e = event(c, "bow-bunny-acc-spot")
        val filter = SquareBrowseFilter(shop = e.shopId)
        val colors = filter.candidates(c, "color")
        assertTrue("白粉" in colors); assertFalse("生成色" in colors)
        assertFalse(colors.any { "颈饰" in it || "胸针" in it || "袜" in it })
        assertTrue(filter.copy(kind = "尾款").candidates(c, "color").isEmpty())
        val other = filter.copy(form = "套装").candidates(c, "color")
        assertTrue(other.all { it in setOf("白蓝", "经典粉色", "棕色") })
        assertFalse(squareColorFacet("百合戒指")); assertTrue(squareColorFacet("赤雾银"))
    }

    @Test fun referencePhotosNeverEnterWishEvenWhenHeroCanDisplayThem() {
        val c = catalog(); val e = event(c, "jixiangtian-2025-repeat-drop")
        val choice = SquareBrowseFilter().opening(c, e)!!; val part = SquareBrowse.parts(c, e).single { it.key == choice.key }
        val selection = SquareBrowse.select(c, part, choice)
        assertTrue(selection.photos.isEmpty()); assertTrue(selection.seriesPhotos.isNotEmpty())
        val plan = SquareWishPlans.build(c, SquareBrowse.request(c, e, listOf(choice), false)).single()
        assertTrue(plan.snapshot.members.single().photos.isEmpty())
        assertEquals("", plan.snapshot.members.single().size)
    }

    @Test fun compactPricesKeepActualStageRangesAndUnknownInsteadOfZero() {
        val c = catalog(); val e = event(c, "angelo-villainess-elegance-deposit")
        val rows = SquareBrowseFilter().rows(c, e)
        assertTrue(SquarePresentation.price(rows).contains("定金"))
        assertFalse(SquarePresentation.price(rows).contains("全款"))
        assertTrue(SquarePresentation.price(rows.map { it.first to it.second.copy(price = null) }).startsWith("报价未确认"))
        assertTrue(SquarePresentation.price(rows.take(1).map { it.first to it.second.copy(price = null) } + rows.take(1)).contains("部分未知"))
        assertEquals("日期未知", SquarePresentation.date(null))
        assertEquals("日期未知", SquarePresentation.date("采集记录：2026-11-09，仅页面证据"))
        assertEquals("2026-11-09", SquarePresentation.date("2026-11-09"))
    }

    @Test fun sharedLongSeriesPrefixIsRemovedOnlyFromDisplayName() {
        val c = catalog(); val e = event(c, "angelo-villainess-elegance-deposit")
        val parts = SquareBrowse.parts(c, e)
        assertEquals(setOf("蕾丝姬袖连衣裙", "可提拉背带半裙"), parts.map { SquarePresentation.partName(e, it) }.toSet())
        assertTrue(parts.all { it.name.startsWith("Angelo·Villainess Elegance") })
    }
}
