package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.ui.screen.`import`.*
import org.junit.Assert.*
import org.junit.Test

class TaobaoProductSkuVariantsTest {
    private val gson = Gson()
    private fun raw(colors: List<String> = listOf("黑色", "白色"), sizes: List<String> = listOf("S", "M", "L"),
        sizeName: String = "尺码", paths: List<String>? = null, styles: Boolean = false): String {
        val props = mutableListOf<Map<String, Any>>()
        if (colors.isNotEmpty()) props += mapOf("pid" to "c", "name" to "颜色分类", "values" to colors.mapIndexed { i, n -> mapOf("vid" to "$i", "name" to n) })
        if (sizes.isNotEmpty()) props += mapOf("pid" to "s", "name" to sizeName, "values" to sizes.mapIndexed { i, n -> mapOf("vid" to "$i", "name" to n) })
        if (styles) props += mapOf("pid" to "t", "name" to "款式", "values" to listOf(mapOf("vid" to "0", "name" to "OP"), mapOf("vid" to "1", "name" to "KC")))
        val base = mutableMapOf<String, Any>("props" to props)
        if (paths != null) base["skus"] = paths.mapIndexed { i, p -> mapOf("skuId" to "$i", "propPath" to p) }
        val sku = gson.toJson(mapOf("global" to mapOf("skuGlobal" to mapOf("context" to mapOf("skuBase" to base)))))
        return gson.toJson(mapOf("url" to "https://item.taobao.com/item.htm?id=123456", "detailStatus" to "complete",
            "detailImages" to listOf(mapOf("url" to "https://img.alicdn.com/detail.png")),
            "entry" to mapOf("apiStack" to listOf(mapOf("data" to mapOf("titleVO" to mapOf("title" to "印花连裤袜"),
                "headPicVO" to mapOf("images" to listOf(mapOf("url" to "https://img.alicdn.com/head.png"))),
                "skuData" to mapOf("skuV3Data" to sku), "priceVO" to mapOf("price" to mapOf("priceText" to "19", "priceTail" to "起"))))))))
    }
    private fun parse(raw: String = raw()) = TaobaoProductParser.parse(raw)
    private fun live(raw: String = raw(), names: List<String> = listOf("黑色", "M"), price: String = "36.50", tail: String = "", title: String = "全款"): TaobaoProductDraft {
        val root = JsonParser.parseString(raw).asJsonObject
        root.add("liveSku", gson.toJsonTree(mapOf("present" to true, "selectedNames" to names,
            "selectedText" to "已选“${names.joinToString("；")}”", "priceText" to price, "priceTail" to tail, "priceTitle" to title)))
        return parse(root.toString())
    }
    @Test fun colorAndSizeChoicesAreOptInAndKeepExactMerchantSizeLabels() {
        val draft = parse(raw(sizes = listOf("M（适合155-165）", "大码")))
        assertEquals(listOf("黑色 · M（适合155-165）", "黑色 · 大码", "白色 · M（适合155-165）", "白色 · 大码"), draft.skuVariants.orEmpty().map { it.name })
        assertEquals(1, taobaoProductBatchItems(draft, draft.skuVariants.orEmpty().map { it.id }.toSet()).size)
        assertTrue(draft.skuVariants.orEmpty().all { it.referenceQuote == null })
        assertEquals(listOf("黑色", "白色"), draft.colorVariants.map { it.name })
    }
    @Test fun exactTwoChoicesCarrySizeQuoteAndIdentityThroughCompactQueueAndEditor() {
        val draft = live()
        val selected = draft.skuVariants.orEmpty().filter { it.name in setOf("白色 · L", "黑色 · M") }
        val items = taobaoProductBatchItems(draft, selected.map { it.id }.toSet(), splitSkuVariants = true)
        assertEquals(listOf("M", "L"), items.map { it.size })
        assertEquals(listOf("印花连裤袜 · 黑色 · M", "印花连裤袜 · 白色 · L"), items.map { it.name })
        assertEquals(2, items.map { it.importToken }.toSet().size)
        assertEquals(listOf("36.5", null), items.map { it.referenceQuote?.amount })
        assertTrue(items.all { it.skuVariants.orEmpty().isEmpty() && it.colorVariants.isEmpty() && it.remoteDetailImages == draft.remoteDetailImages })
        val compact = gson.fromJson(gson.toJson(taobaoProductCompactBatch(TaobaoProductBatchProgress(items))), TaobaoProductBatchProgress::class.java)
        val restored = taobaoProductRestoreBatch(compact, draft.copy(size = "不能覆盖已选尺码"))
        assertEquals(items.map { it.size }, restored.items.map { it.size })
        assertEquals(items.map { it.selectedSkuId }, restored.items.map { it.selectedSkuId })
        assertEquals(items.map { it.importToken }, restored.items.map { it.importToken })
        assertEquals(items.map { it.referenceQuote }, restored.items.map { it.referenceQuote })
        val editor = taobaoProductEditorDraft(restored.items[1], draft, emptyMap(), "editor-2", 1, 2)
        assertEquals("L", editor.size); assertEquals(2, editor.batchIndex)
    }
    @Test fun sameColorCanSelectTwoSizesWithoutSharingQuoteOrSilentlySelectingOtherSizes() {
        val draft = live(); val black = draft.skuVariants.orEmpty().filter { it.specificationGroup == "黑色" && it.size in listOf("S", "M") }
        val items = taobaoProductBatchItems(draft, black.map { it.id }.toSet(), splitSkuVariants = true)
        assertEquals(listOf("S", "M"), items.map { it.size }); assertNull(items[0].referenceQuote)
        assertEquals("36.5", items[1].referenceQuote?.amount)
        assertTrue(taobaoProductBatchItems(draft, setOf("missing"), splitSkuVariants = true).isEmpty())
    }
    @Test fun sparseSkuPathsDoNotInventMissingColorSizeCombinations() {
        val draft = parse(raw(paths = listOf("s:1;c:1", "c:0;s:0")))
        assertEquals(listOf("黑色 · S", "白色 · M"), draft.skuVariants.orEmpty().map { it.name })
        assertNull(draft.skuSelectionIssue)
        assertEquals(draft.skuVariants.orEmpty().map { it.id }, parse(raw(paths = listOf("c:0;s:0", "c:1;s:1"))).skuVariants.orEmpty().map { it.id })
    }
    @Test fun onlyOneSizeStillRequiresExplicitChoiceAndShoeSizeIsNotNormalizedAway() {
        val draft = parse(raw(colors = emptyList(), sizes = listOf("36 / 脚长23cm"), sizeName = "鞋码"))
        assertTrue(taobaoProductBatchItems(draft, emptySet(), splitSkuVariants = true).isEmpty())
        val item = taobaoProductBatchItems(draft, setOf(draft.skuVariants.orEmpty().single().id), splitSkuVariants = true).single()
        assertEquals("36 / 脚长23cm", item.size)
    }
    @Test fun explicitPieceDefaultsRemainButOptInSkuKeepsAllDimensionsAndExactPrice() {
        val draft = live(raw(styles = true), listOf("白色", "M", "OP"))
        assertEquals(listOf("OP", "KC"), taobaoProductVariants(draft, false).map { it.name })
        assertEquals(12, taobaoProductVariants(draft, false, true).size)
        val quoted = draft.skuVariants.orEmpty().single { it.referenceQuote != null }
        assertEquals("白色 · OP · M", quoted.name)
        assertEquals("OP", quoted.category)
        assertEquals("M", quoted.size)
        assertTrue(draft.skuVariants.orEmpty().filter { it.id != quoted.id }.all { it.referenceQuote == null })
    }
    @Test fun noSizeKeepsLegacyColorsAndMalformedOrOversizedMatrixDoesNotReturnPartialChoices() {
        assertTrue(parse(raw(sizes = emptyList())).skuVariants.orEmpty().isEmpty())
        for (source in listOf(raw(paths = listOf("c:0;s:0", "c:1;s:missing")), raw(colors = listOf("同名", "同名")),
            raw(colors = (0..30).map { "颜色$it" }, sizes = (0..32).map { "$it" }), raw(paths = emptyList()))) {
            val draft = parse(source)
            assertTrue(draft.skuVariants.orEmpty().isEmpty()); assertNotNull(draft.skuSelectionIssue)
            assertEquals(1, taobaoProductBatchItems(draft, emptySet()).size)
        }
    }
    @Test fun partialRangeDepositAndUnrelatedDimensionQuotesStayEmpty() {
        assertTrue(live(names = listOf("黑色")).skuVariants.orEmpty().all { it.referenceQuote == null })
        assertTrue(live(price = "29-39").skuVariants.orEmpty().all { it.referenceQuote == null })
        assertTrue(live(tail = "起").skuVariants.orEmpty().all { it.referenceQuote == null })
        assertTrue(live(title = "定金").skuVariants.orEmpty().all { it.referenceQuote == null })
        assertTrue(live(names = listOf("黑色", "unknown")).skuVariants.orEmpty().all { it.referenceQuote == null })
    }
    @Test fun ssrQuoteUsesTheCompleteSelectedPathAndDoesNotBleedToSameColor() {
        val root = JsonParser.parseString(raw()).asJsonObject
        val data = root.getAsJsonObject("entry").getAsJsonArray("apiStack")[0].asJsonObject.getAsJsonObject("data")
        val sku = JsonParser.parseString(data.getAsJsonObject("skuData")["skuV3Data"].asString).asJsonObject
        val ctx = sku.getAsJsonObject("global").getAsJsonObject("skuGlobal").getAsJsonObject("context")
        ctx.getAsJsonObject("skuBase").addProperty("selectedPath", "s:2;c:1")
        ctx.getAsJsonObject("skuBase").addProperty("selectedPathStatus", "2")
        ctx.add("simpleSkuHead", gson.toJsonTree(mapOf("price" to mapOf("priceText" to "42"))))
        data.getAsJsonObject("skuData").addProperty("skuV3Data", sku.toString())
        val draft = parse(root.toString())
        assertEquals("白色 · L", draft.skuVariants.orEmpty().single { it.referenceQuote != null }.name)
    }
    @Test fun oldSessionAndCompactQueueKeepTheirSharedSizeWhileNewMetadataIsOptional() {
        val root = gson.toJsonTree(parse().copy(size = "均码")).asJsonObject
        listOf("skuVariants", "skuSelectionIssue", "selectedSkuId").forEach(root::remove)
        val old = gson.fromJson(root, TaobaoProductDraft::class.java)
        assertTrue(taobaoProductVariants(old, false, true).isEmpty())
        val queue = TaobaoProductBatchProgress(listOf(old.copy(name = "旧款", importToken = "original-token")))
        val compactRoot = gson.toJsonTree(taobaoProductCompactBatch(queue)).asJsonObject
        compactRoot.getAsJsonArray("items")[0].asJsonObject.apply { remove("selectedSkuId"); addProperty("size", "") }
        val compact = gson.fromJson(compactRoot, TaobaoProductBatchProgress::class.java)
        val restored = taobaoProductRestoreBatch(compact, old).items.single()
        assertEquals("均码", restored.size); assertEquals("original-token", restored.importToken)
    }

    @Test fun unknownMensSetCategoryAndOrdinaryColorNamesDoNotInheritOrGuessAnotherPiece() {
        val real = parse(javaClass.getResource("/taobao-shouwang-price.json")!!.readText())
        val maleSets = real.skuVariants.orEmpty().filter { "男款SET" in it.name }
        assertTrue(maleSets.isNotEmpty())
        assertTrue(maleSets.all { it.category.isBlank() })
        assertEquals("白+黑 · 男款SET · ManS", maleSets.single { it.referenceQuote != null }.name)
        val colored = parse(raw(colors = listOf("裙撑印花", "衬衫纹样")))
        assertTrue(colored.skuVariants.orEmpty().all { it.category == "连裤袜" })
    }

    @Test fun veryLongMerchantLabelsCannotExpandTheSavedImportSessionWithoutBound() {
        val draft = parse(raw(colors = listOf("长".repeat(50_000))))
        assertTrue(draft.skuVariants.orEmpty().isEmpty())
        assertNotNull(draft.skuSelectionIssue)
        assertEquals(1, taobaoProductBatchItems(draft, emptySet()).size)
    }
}
