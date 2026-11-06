package com.lolita.app.data.square

import com.google.gson.JsonObject
import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class SquareBrowseTest {
    private fun sku(id: String, part: String, color: String?, size: String?, photos: List<String> = listOf("$part-$color"), product: String = "1", phase: String = "FULL") = SquareSku(id, "$product:$id", product,
        mapOf("颜色分类" to "$color $part", "尺码" to (size ?: "未知")), if (phase == "FULL") "899" else "100", phase, "未知", part, color, size,
        if (part == "KC") "小物" else "单品", listOf(SquarePart(part, part, part, part == "KC", color, size, "独立SKU", null)), emptyList(), false,
        mapOf("颜色分类" to "$color $part", "尺码" to (size ?: "未知")), "v-$part-$color-$size", photos)
    private val e = SquareEvent("event", 1, "shop", "design", "batch", "商品", "", "上新", "预售", "未知", "899", "全款", "", "", "", "2026-11-06", listOf("1"))
    private fun catalog(skus: List<SquareSku> = listOf(sku("s", "JSK", "蓝色", "S"), sku("m", "JSK", "蓝色", "M"), sku("black", "JSK", "黑色", "M"), sku("kc", "KC", null, null))) = SquareCatalog(packageId = "test", title = "目录", capturedAt = "2026-11-06", shops = listOf(SquareShop("shop", "店铺", 1)), events = listOf(e), products = listOf(SquareProduct("1", 1, "shop", "商品", "https://item.taobao.com/item.htm?id=1", "2026-11-06", "FULL", "全款", skus, JsonObject(), listOf(SquareImage("series", "main", 0, "")))), assets = emptyList())
    private fun part(c: SquareCatalog, key: String) = SquareBrowse.parts(c, c.events.first()).single { it.partKey == key }
    private fun plans(c: SquareCatalog, choices: List<SquareBrowseChoice>, grouped: Boolean = false) = SquareWishPlans.build(c, SquareBrowse.request(c, c.events.first(), choices, grouped))
    private inline fun rejected(block: () -> Unit) { try { block(); fail("Must reject invalid selection/source") } catch (_: IllegalArgumentException) {} }

    @Test fun unknownSizeIsNotFirstSkuAndColourOwnsPhotos() {
        val c = catalog(); val p = part(c, "JSK")
        val selected = SquareBrowse.select(c, p, SquareBrowseChoice(p.key, "蓝色"))
        assertNull(selected.selectedSkuKey); assertEquals("", selected.choice.size); assertEquals(listOf("JSK-蓝色"), selected.photos)
        assertEquals(setOf("S", "M"), SquareBrowse.sizes(c, p, "蓝色").toSet())
        assertTrue(SquareBrowse.select(c, p, SquareBrowseChoice(p.key)).photos.isEmpty())
        rejected { SquareBrowse.select(c, p, SquareBrowseChoice(p.key, "黑色", "S")) }
    }
    @Test fun componentInBundleDoesNotBorrowDressSpecOrBundlePrice() {
        val shirt = sku("bundle", "JSK", "蓝色", "L").let { it.copy(parts = it.parts + SquarePart("胸针", "胸针", "胸针", true, null, null, "包含", "未标明"), content = "JSK＋胸针", form = "组合") }
        val c = catalog(listOf(shirt)); val brooch = part(c, "胸针")
        val choice = SquareBrowseChoice(brooch.key); val s = SquareBrowse.select(c, brooch, choice)
        assertTrue(SquareBrowse.colors(c, brooch).isEmpty()); assertTrue(SquareBrowse.sizes(c, brooch, "").isEmpty())
        assertTrue(s.photos.isEmpty()); assertNull(s.quote); assertTrue(s.onlyInCombination)
        val m = plans(c, listOf(choice)).single().snapshot.members.single()
        assertEquals("", m.color); assertEquals("", m.size); assertTrue(m.photos.isEmpty()); assertFalse(m.origins.single().quoteAppliesToMember)
    }
    @Test fun crossLinkPersonalGroupKeepsIndependentMembersAndOrderIndependentIdentity() {
        val c0 = catalog(); val p2 = c0.products.single().copy(id = "2", skus = listOf(sku("kc2", "KC", null, null, listOf("kc-photo"), "2")))
        val c = c0.copy(events = listOf(e.copy(productIds = listOf("1", "2"))), products = listOf(c0.products.single().copy(skus = c0.products.single().skus.filter { it.content != "KC" }), p2))
        val choices = listOf(SquareBrowseChoice(part(c, "JSK").key, "蓝色", "M"), SquareBrowseChoice(part(c, "KC").key))
        val a = plans(c, choices, true).single(); val b = plans(c, choices.reversed(), true).single()
        assertEquals(a.snapshot.identity(), b.snapshot.identity()); assertEquals(2, a.snapshot.members.size)
        assertEquals("M", a.snapshot.members.first().size); assertEquals("", a.snapshot.members.last().size)
        assertEquals(listOf("kc-photo"), a.snapshot.members.last().photos.map { a.paths[it] })
        assertEquals(2, plans(c, choices).size)
    }
    @Test fun depositLinkNeedsExplicitVariantMatchAndDoesNotReplaceFullQuote() {
        val base = catalog(); val dep = base.products.single().copy(id = "2", pricePhase = "DEPOSIT", priceType = "定金", skus = base.products.single().skus.map { it.copy(itemId = "2", key = "2:${it.id}", price = "100") })
        val c = base.copy(products = base.products + dep, events = listOf(e.copy(productIds = listOf("1", "2"))))
        assertEquals(2, SquareBrowse.parts(c, c.events.first()).size)
        val selected = SquareBrowse.select(c, part(c, "JSK"), SquareBrowseChoice(part(c, "JSK").key, "蓝色"))
        assertEquals("899", selected.quote); assertEquals("FULL", selected.phase); assertNull(selected.selectedSkuKey)
        val unlinked = c.copy(products = listOf(base.products.single(), dep.copy(skus = dep.skus.map { it.copy(variantId = "other-${it.id}") })))
        assertEquals(4, SquareBrowse.parts(unlinked, unlinked.events.first()).size)
    }
    @Test fun longAndShortFromSamePartKeyRemainDistinct() {
        val base = sku("long", "内裙", "蓝色", "M")
        val a = base.copy(content = "长款SET", rawProperties = base.rawProperties + ("套装" to "长款SET"), parts = base.parts + SquarePart("外裙", "外裙", null, false, null, null, "包含", null))
        val b = a.copy(id = "short", key = "1:short", content = "短款SET", rawProperties = a.rawProperties + ("套装" to "短款SET"))
        val c = catalog(listOf(a, b)); val parts = SquareBrowse.parts(c, e)
        assertEquals(4, parts.size); assertEquals(2, parts.filter { it.partKey == "内裙" }.map { it.key }.distinct().size)
    }
    @Test fun staleCatalogueAndBalanceCannotCreateWish() {
        val c = catalog(); val choice = SquareBrowseChoice(part(c, "JSK").key)
        val request = SquareBrowse.request(c, e, listOf(choice), false)
        rejected { SquareWishPlans.build(c.copy(products = c.products.map { it.copy(revision = 2) }), request) }
        rejected { SquareBrowse.request(c, e.copy(kind = "尾款"), listOf(choice), false) }
    }
    @Test fun snapshotStrictFieldsImageIndicesAndQuotePhase() {
        val c = catalog(); val plan = plans(c, listOf(SquareBrowseChoice(part(c, "JSK").key, "蓝色"))).single()
        assertEquals(plan.snapshot, SquareWishSnapshot.decode(plan.snapshot.encode(), plan.paths.size))
        val json = SquarePackage.gson.toJsonTree(plan.snapshot).asJsonObject; json.addProperty("hiddenPath", "/private/image.png")
        rejected { SquareWishSnapshot.decode(json.toString(), plan.paths.size) }
        rejected { SquareWishSnapshot.decode(plan.snapshot.copy(members = plan.snapshot.members.map { it.copy(photos = listOf(999)) }).encode(), plan.paths.size) }
        rejected { SquareWishSnapshot.decode(plan.snapshot.copy(members = plan.snapshot.members.map { it.copy(origins = it.origins.map { o -> o.copy(phase = "BALANCE") }) }).encode(), plan.paths.size) }
    }
    @Test fun exactRelatedDeletedAndLegacyDraftAreDifferentOutcomes() {
        val c = catalog(); val blue = plans(c, listOf(SquareBrowseChoice(part(c, "JSK").key, "蓝色"))).single()
        val item = Item(id = 1, brandId = 1, categoryId = 1, name = "旧名", description = "自填", status = ItemStatus.WISHED, importToken = blue.snapshot.identity())
        val source = ItemSourceRecord(itemId = 1, kind = "SQUARE_WISH", publicUrl = c.products.first().url, snapshotJson = blue.snapshot.encode(), imagePaths = blue.paths)
        val state = SquareWishWardrobe(listOf(item), listOf(source))
        assertEquals(listOf(1L), SquareWishPlans.match(blue, state).exact)
        val sized = plans(c, listOf(SquareBrowseChoice(part(c, "JSK").key, "蓝色", "M"))).single()
        assertEquals(listOf(1L), SquareWishPlans.match(sized, state).related)
        val black = plans(c, listOf(SquareBrowseChoice(part(c, "JSK").key, "黑色", "M"))).single()
        assertTrue(SquareWishPlans.match(black, state).related.isEmpty())
        assertTrue(SquareWishPlans.match(blue, state.copy(items = emptyList())).exact.isEmpty())
        val draft = ItemDraft(id = "draft", dataJson = ItemDraftData(importToken = blue.legacyTokens.first()).encode())
        assertEquals(listOf("draft"), SquareWishPlans.match(blue, SquareWishWardrobe(drafts = listOf(draft))).drafts)
    }
    @Test fun accessoryOnlyInDepositBundlesDoesNotDuplicateIndependentFullPricePart() {
        val c = catalog(); val standalone = c.products.single().skus.first { it.content == "JSK" }
        val kc = c.products.single().skus.first { it.content == "KC" }.parts.single()
        val combo = standalone.copy(id = "combo", key = "1:combo", content = "JSK＋KC", parts = standalone.parts + kc, variantId = "combo-variant")
        val full = c.products.single().copy(skus = c.products.single().skus + combo)
        val deposit = full.copy(id = "2", pricePhase = "DEPOSIT", skus = listOf(combo.copy(itemId = "2", key = "2:combo", photoPaths = emptyList())))
        val both = c.copy(products = listOf(full, deposit), events = listOf(e.copy(productIds = listOf("1", "2"))))
        val parts = SquareBrowse.parts(both, both.events.first())
        assertEquals(1, parts.count { it.partKey == "KC" })
        val part = parts.single { it.partKey == "KC" }
        assertEquals("899", SquareBrowse.select(both, part, SquareBrowseChoice(part.key)).quote)
    }

    @Test fun userSpecificationSeparatorsCannotCollideInWishIdentity() {
        val c = catalog(); val plan = plans(c,listOf(SquareBrowseChoice(part(c,"JSK").key,"蓝色"))).single()
        val m = plan.snapshot.members.single()
        val a = plan.snapshot.copy(members=listOf(m.copy(color="A:B",size="C")))
        val b = plan.snapshot.copy(members=listOf(m.copy(color="A",size="B:C")))
        assertNotEquals(a.identity(),b.identity())
        rejected { SquareWishSnapshot.decode(plan.snapshot.copy(members=listOf(m,m.copy(color="另一色"))).encode(),plan.paths.size) }
    }

    @Test fun oldAccessorySplitFromBundleIsFoundByNewIndependentBrowser() {
        val c0=catalog();val base=c0.products.single().skus.first{it.content=="JSK"}
        val kc=SquarePart("KC","KC","KC",true,null,null,"包含","未标明")
        val combo=base.copy(id="bundle",key="1:bundle",variantId="bundle-variant",parts=base.parts+kc,content="JSK+KC")
        val c=c0.copy(products=listOf(c0.products.single().copy(skus=c0.products.single().skus+combo)))
        val plan=plans(c,listOf(SquareBrowseChoice(part(c,"KC").key))).single()
        val token="square:"+squareDigest(SquareUnit(listOf("KC"),"KC").identity(e,combo))
        assertTrue(token in plan.legacyTokens)
        val old=Item(id=7,brandId=1,categoryId=1,name="用户已改名的KC",description="旧来源",status=ItemStatus.WISHED,importToken=token)
        assertEquals(listOf(7L),SquareWishPlans.match(plan,SquareWishWardrobe(items=listOf(old))).exact)
    }

    @Test fun archivedProductsAndEndedBatchesNeverEnterShoppingButRemainInCatalog() {
        val c = catalog(); val archived = c.products.single().copy(id="2", title="仅展示商品", listing=SquareListing("DISPLAY_ONLY", "商家明确仅展示", "2026-11-06T22:00:00+08:00"), skus=listOf(sku("x","项链","红色",null,product="2")))
        val mixed = c.copy(products=c.products+archived, events=listOf(e.copy(productIds=listOf("2","1"))))
        assertEquals(listOf("1"), SquareVisibility.products(mixed,mixed.events.single()).map { it.id })
        assertFalse(SquareBrowse.parts(mixed,mixed.events.single()).any { it.partKey=="项链" })
        assertEquals(2,mixed.products.size)
        val off = c.copy(products=c.products.map { it.copy(listing=SquareListing("OFF_SALE","商家下架提示","2026-11-06T22:00:00+08:00")) })
        assertTrue(SquareVisibility.events(off).isEmpty()); assertTrue(SquareBrowse.parts(off,e).isEmpty())
        val oldRequest=SquareBrowse.request(c,e,listOf(SquareBrowseChoice(part(c,"JSK").key)),false)
        rejected { SquareWishPlans.build(off,oldRequest) }
        assertTrue(SquareVisibility.events(c.copy(products=c.products.map { it.copy(pricePhase="PLACEHOLDER") })).isEmpty())
        assertTrue(SquareVisibility.events(c.copy(events=listOf(e.copy(state="预约已结束")))).isEmpty())
        assertEquals(1,SquareVisibility.events(c.copy(events=listOf(e.copy(kind="尾款",state="尾款开放")))).size)
        assertEquals(1,SquareVisibility.events(c.copy(events=listOf(e.copy(state="部分规格无货")))).size)
        val restored=off.copy(products=off.products.map { it.copy(listing=it.listing!!.copy(status="ACTIVE")) })
        assertEquals(1,SquareVisibility.events(restored).size)
    }

}
