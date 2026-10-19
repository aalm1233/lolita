package com.lolita.app.data.file

import com.google.gson.Gson
import com.lolita.app.ui.screen.`import`.*
import org.junit.Assert.*
import org.junit.Test

class TaobaoProductColorStylesTest {
    private val names = listOf("款式一", "款式二", "款式三", "款式四")

    private fun draft(selected: Boolean = false, explicitStyles: Boolean = false): TaobaoProductDraft {
        val properties = mutableListOf(
            mapOf("pid" to "1627207", "name" to "颜色分类", "values" to names.mapIndexed { index, name ->
                mapOf("vid" to "$index", "name" to name)
            }),
            mapOf("pid" to "20509", "name" to "尺码", "values" to listOf(
                mapOf("vid" to "10", "name" to "M"), mapOf("vid" to "11", "name" to "L")))
        )
        if (explicitStyles) properties += mapOf("pid" to "30000", "name" to "款式", "values" to listOf(
            mapOf("vid" to "20", "name" to "短袜"), mapOf("vid" to "21", "name" to "连裤袜")))
        val sku = Gson().toJson(mapOf("global" to mapOf("skuGlobal" to mapOf("context" to mapOf(
            "skuBase" to mapOf("props" to properties))))))
        val root = mutableMapOf<String, Any>(
            "url" to "https://item.taobao.com/item.htm?id=844758918476", "detailStatus" to "complete",
            "detailImages" to listOf(mapOf("url" to "https://img.alicdn.com/detail.jpg")),
            "entry" to mapOf("apiStack" to listOf(mapOf("data" to mapOf(
                "titleVO" to mapOf("title" to "摇篮时光印花连裤袜"),
                "headPicVO" to mapOf("images" to listOf(mapOf("url" to "https://img.alicdn.com/head.jpg"))),
                "skuData" to mapOf("skuV3Data" to sku),
                "priceVO" to mapOf("price" to mapOf("priceText" to "35", "priceTail" to "起"))
            ))))
        )
        if (selected) root["liveSku"] = mapOf("present" to true, "selectedNames" to listOf(names[2], "M"),
            "selectedText" to "已选“${names[2]}；M”", "priceText" to "36.50", "priceTitle" to "全款")
        return TaobaoProductParser.parse(Gson().toJson(root))
    }

    @Test fun fourColorStylesRemainOptInAndSizesDoNotMultiplyItems() {
        val draft = draft()
        assertTrue(draft.variants.isEmpty())
        assertEquals(names, draft.colorVariants.map { it.name })
        assertEquals(listOf("连裤袜", "连裤袜", "连裤袜", "连裤袜"), draft.colorVariants.map { it.category })
        assertEquals(4, draft.colorVariants.map { it.id }.toSet().size)
        assertEquals(draft.colorVariants, draft().colorVariants)
        val original = taobaoProductBatchItems(draft, draft.colorVariants.map { it.id }.toSet())
        assertEquals(1, original.size)
        assertEquals(draft.name, original.single().name)
        assertTrue(original.single().colorVariants.isEmpty())
    }

    @Test fun choosingOnlyTwoKeepsSourceOrderCategoryAllImagesAndIndependentImportIdentities() {
        val draft = draft()
        val selected = setOf(draft.colorVariants[2].id, draft.colorVariants[0].id)
        val items = taobaoProductBatchItems(draft, selected, splitColorVariants = true)
        assertEquals(listOf("摇篮时光印花连裤袜 · 款式一", "摇篮时光印花连裤袜 · 款式三"), items.map { it.name })
        assertEquals(2, items.map { it.importToken }.toSet().size)
        assertTrue(items.all { it.category == "连裤袜" && it.remoteImages == draft.remoteImages &&
            it.remoteDetailImages == draft.remoteDetailImages && it.variants.isEmpty() && it.colorVariants.isEmpty() })
        assertTrue(taobaoProductBatchItems(draft, emptySet(), true).isEmpty())
        val restored = taobaoProductRestoreBatch(taobaoProductCompactBatch(TaobaoProductBatchProgress(items)), draft)
        assertEquals(items, restored.items)
    }

    @Test fun onlyTheFullySelectedColorSkuGetsItsQuoteAndOtherStylesDoNotInheritIt() {
        val draft = draft(selected = true)
        assertEquals(listOf(null, null, "36.5", null), draft.colorVariants.map { it.referenceQuote?.amount })
        val items = taobaoProductBatchItems(draft, setOf(draft.colorVariants[0].id, draft.colorVariants[2].id), true)
        assertNull(TaobaoProductQuotes.fromDescription(items[0].description))
        assertEquals("36.5", TaobaoProductQuotes.fromDescription(items[1].description)?.amount)
        assertTrue(draft().colorVariants.all { it.referenceQuote == null })
    }

    @Test fun explicitPiecesKeepTheirExistingPrecedenceOverColorOptions() {
        val draft = draft(explicitStyles = true)
        assertEquals(listOf("短袜", "连裤袜"), draft.variants.map { it.name })
        assertTrue(draft.colorVariants.isEmpty())
        assertEquals(draft.variants, taobaoProductVariants(draft, true))
    }

    @Test fun oldSerializedDraftWithoutColorOptionsStillImportsOnce() {
        val gson = Gson()
        val json = gson.toJsonTree(draft()).asJsonObject.apply { remove("colorVariants") }
        val old = gson.fromJson(json, TaobaoProductDraft::class.java)
        assertTrue(taobaoProductVariants(old, true).isEmpty())
        assertEquals(1, taobaoProductBatchItems(old, emptySet()).size)
    }
}
