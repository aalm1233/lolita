package com.lolita.app.ui.screen.`import`

import com.google.gson.Gson
import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductVariant
import org.junit.Assert.*
import org.junit.Test

class TaobaoProductBatchTest {
    private fun original(variants: List<TaobaoProductVariant> = emptyList()) = TaobaoProductDraft(
        name = "庄园系列 OP 与配件", brand = "测试品牌", size = "M", properties = "套装：三件",
        description = "完整详情", productUrl = "https://item.taobao.com/item.htm?id=123",
        remoteImages = listOf("https://img.alicdn.com/head.jpg"), category = "OP",
        categoryCandidates = listOf("OP", "其他头饰"),
        remoteDetailImages = listOf("https://img.alicdn.com/detail.jpg"), variants = variants
    )

    @Test fun selectedPartsKeepOrderAndUseOnlyTheirOwnCategory() {
        val draft = original(listOf(TaobaoProductVariant("op", "OP", "OP"),
            TaobaoProductVariant("kc", "KC", "KC"), TaobaoProductVariant("bonnet", "头巾", "其他头饰")))
        val items = taobaoProductBatchItems(draft, setOf("bonnet", "op"))
        assertEquals(listOf("庄园系列 OP 与配件 · OP", "庄园系列 OP 与配件 · 头巾"), items.map { it.name })
        assertEquals(listOf("OP", "其他头饰"), items.map { it.category })
        assertEquals(listOf(listOf("OP"), listOf("其他头饰")), items.map { it.categoryCandidates })
        assertEquals("完整详情", items[1].description)
        assertEquals(listOf("https://img.alicdn.com/detail.jpg"), items[1].remoteDetailImages)
    }

    @Test fun UnknownPartDoesNotInheritDressCategory() {
        val items = taobaoProductBatchItems(original(listOf(TaobaoProductVariant("op", "OP", "OP"),
            TaobaoProductVariant("extra", "配件待核对"))), setOf("extra"))
        assertEquals("", items.single().category)
        assertTrue(items.single().categoryCandidates.isEmpty())
    }

    @Test fun singleCandidateKeepsOriginalTitleAndCategory() {
        val items = taobaoProductBatchItems(original(listOf(TaobaoProductVariant("one", "款式A"))), emptySet())
        assertEquals("庄园系列 OP 与配件", items.single().name)
        assertEquals("OP", items.single().category)
        assertEquals(listOf("OP", "其他头饰"), items.single().categoryCandidates)
    }

    @Test fun emptySelectionCannotStartMultipleParts() {
        val items = taobaoProductBatchItems(original(listOf(TaobaoProductVariant("a", "A"),
            TaobaoProductVariant("b", "B"))), emptySet())
        assertTrue(items.isEmpty())
    }

    @Test fun savedTokenAdvancesExactlyOnceAndFinalSaveCompletes() {
        val batch = TaobaoProductBatchProgress(items = listOf(original(), original()), activeToken = "first")
        val first = batch.editorReturned("first:saved")
        assertEquals(1, first.index)
        assertEquals(1, first.savedCount)
        assertNull(first.activeToken)
        assertFalse(first.completed)
        assertEquals(first, first.editorReturned("first:saved"))
        val second = first.copy(activeToken = "second").editorReturned("second:saved")
        assertEquals(2, second.index)
        assertEquals(2, second.savedCount)
        assertTrue(second.completed)
        assertNull(second.currentItem())
    }

    @Test fun discardPausesCurrentPartAndKeepsAlreadySavedProgress() {
        val batch = TaobaoProductBatchProgress(items = listOf(original(), original()), index = 1,
            savedCount = 1, activeToken = "second")
        val paused = batch.editorReturned("second:discarded")
        assertEquals(1, paused.index)
        assertEquals(1, paused.savedCount)
        assertTrue(paused.paused)
        assertNull(paused.activeToken)
        assertNotNull(paused.currentItem())
        val finished = paused.copy(activeToken = "retry", paused = false).editorReturned("retry:saved")
        assertTrue(finished.completed)
        assertEquals(2, finished.savedCount)
    }

    @Test fun staleOrMalformedResultsCannotAdvanceOrPause() {
        val batch = TaobaoProductBatchProgress(items = listOf(original(), original()), activeToken = "current")
        listOf("old:saved", "old:discarded", "current:unknown", "current:saved:extra", "saved", "")
            .forEach { assertEquals(batch, batch.editorReturned(it)) }
    }

    @Test fun editorDraftUsesIndependentCopiesForAllMasterImages() {
        val master = original().copy(imagePaths = listOf("/cache/head"), sizeChartImagePath = "/cache/legacy",
            sizeChartImagePaths = listOf("/cache/detail1", "/cache/detail2"))
        val editor = taobaoProductEditorDraft(original().copy(importToken = "record-one"), master, mapOf("/cache/head" to "/images/copy-head",
            "/cache/legacy" to "/images/copy-legacy", "/cache/detail1" to "/images/copy-detail1",
            "/cache/detail2" to "/images/copy-detail2"), "one", 1, 3)
        assertEquals(listOf("/images/copy-head"), editor.imagePaths)
        assertEquals("/images/copy-legacy", editor.sizeChartImagePath)
        assertEquals(listOf("/images/copy-detail1", "/images/copy-detail2"), editor.sizeChartImagePaths)
        assertEquals("record-one", editor.importToken)
        assertEquals("one", editor.editorToken)
        assertEquals(2, editor.batchIndex)
        assertEquals(3, editor.batchTotal)
    }

    @Test fun missingOrReusedCopyCannotTransferAMasterFileToEditor() {
        val master = original().copy(imagePaths = listOf("/cache/head"),
            sizeChartImagePaths = listOf("/cache/detail"))
        assertThrows(IllegalArgumentException::class.java) {
            taobaoProductEditorDraft(original().copy(importToken = "record-one"), master, mapOf("/cache/head" to "/images/head"), "one", 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            taobaoProductEditorDraft(original().copy(importToken = "record-one"), master, mapOf("/cache/head" to "/cache/head",
                "/cache/detail" to "/images/detail"), "one", 0, 1)
        }
    }

    @Test fun compactProgressDoesNotMultiplySharedProductMetadataForThirtyPieces() {
        val variants = (1..30).map { TaobaoProductVariant("piece$it", "物品$it", "KC") }
        val shared = original(variants).copy(description = "详情".repeat(3000),
            remoteDetailImages = (1..50).map { "https://img.alicdn.com/detail$it.jpg" })
        val full = TaobaoProductBatchProgress(taobaoProductBatchItems(shared, variants.map { it.id }.toSet()))
        val compact = taobaoProductCompactBatch(full)
        assertEquals(30, compact.items.size)
        assertTrue(compact.items.all { it.description.isEmpty() && it.remoteDetailImages.isEmpty() })
        assertTrue(Gson().toJson(compact).toByteArray(Charsets.UTF_8).size < 20_000)
    }

    @Test fun hydratingCompactProgressRestoresSharedFieldsWithoutResettingTheCurrentPiece() {
        val shared = original().copy(brandAliases = listOf("品牌别名"))
        val metadata = listOf(TaobaoProductDraft("庄园系列 OP 与配件 · OP", "", "", "", "", "", emptyList(),
            category = "OP", categoryCandidates = listOf("OP"), importToken = "record-op"),
            TaobaoProductDraft("庄园系列 OP 与配件 · 头巾", "", "", "", "", "", emptyList(),
                category = "其他头饰", categoryCandidates = listOf("其他头饰"), importToken = "record-head"))
        val compact = TaobaoProductBatchProgress(metadata, index = 1, savedCount = 1, activeToken = "second")
        val hydrated = taobaoProductRestoreBatch(compact, shared)
        assertEquals(1, hydrated.index)
        assertEquals(1, hydrated.savedCount)
        assertEquals("second", hydrated.activeToken)
        assertEquals(listOf("record-op", "record-head"), hydrated.items.map { it.importToken })
        val current = hydrated.currentItem()!!
        assertEquals("庄园系列 OP 与配件 · 头巾", current.name)
        assertEquals("其他头饰", current.category)
        assertEquals(listOf("其他头饰"), current.categoryCandidates)
        assertEquals("完整详情", current.description)
        assertEquals("测试品牌", current.brand)
        assertEquals("M", current.size)
        assertEquals(listOf("品牌别名"), current.brandAliases)
        assertEquals(listOf("https://img.alicdn.com/detail.jpg"), current.remoteDetailImages)
        assertTrue(current.variants.isEmpty())
        assertTrue(current.imagePaths.isEmpty())
    }

    @Test fun eachQueuedPieceKeepsItsRecordIdentityAcrossDifferentEditorAttempts() {
        val variants = listOf(TaobaoProductVariant("op", "OP", "OP"), TaobaoProductVariant("kc", "KC", "KC"))
        val items = taobaoProductBatchItems(original(variants), setOf("op", "kc"))
        assertTrue(items.all { !it.importToken.isNullOrBlank() })
        assertNotEquals(items[0].importToken, items[1].importToken)
        val firstAttempt = taobaoProductEditorDraft(items[0], original(), emptyMap(), "attempt-one", 0, 2)
        val retry = taobaoProductEditorDraft(items[0], original(), emptyMap(), "attempt-retry", 0, 2)
        assertEquals(items[0].importToken, firstAttempt.importToken)
        assertEquals(firstAttempt.importToken, retry.importToken)
        assertEquals("attempt-one", firstAttempt.editorToken)
        assertEquals("attempt-retry", retry.editorToken)
        assertNotEquals(firstAttempt.editorToken, retry.editorToken)
    }

    @Test fun singleWholeProductAlsoKeepsItsRecordIdentityWhenRetrying() {
        val item = taobaoProductBatchItems(original(), emptySet()).single()
        assertFalse(item.importToken.isNullOrBlank())
        val first = taobaoProductEditorDraft(item, original(), emptyMap(), "single-first", 0, 1)
        val retry = taobaoProductEditorDraft(item, original(), emptyMap(), "single-retry", 0, 1)
        assertEquals(item.importToken, first.importToken)
        assertEquals(first.importToken, retry.importToken)
        assertNotEquals(first.editorToken, retry.editorToken)
    }

    @Test fun compactProgressPersistsRecordIdentitiesWithoutReplacingThemWithEditorTokens() {
        val items = listOf(original().copy(importToken = "record-one"), original().copy(importToken = "record-two"))
        val compact = taobaoProductCompactBatch(TaobaoProductBatchProgress(items, index = 1,
            savedCount = 1, activeToken = "attempt-second"))
        val roundTrip = Gson().fromJson(Gson().toJson(compact), TaobaoProductBatchProgress::class.java)
        val restored = taobaoProductRestoreBatch(roundTrip, original())
        assertEquals(listOf("record-one", "record-two"), restored.items.map { it.importToken })
        assertEquals("attempt-second", restored.activeToken)
        assertEquals(1, restored.index)
        assertEquals(1, restored.savedCount)
    }

    @Test fun restoredProgressKeepsNextItemAndActiveToken() {
        val before = TaobaoProductBatchProgress(items = listOf(original(), original()), index = 1,
            savedCount = 1, activeToken = "second")
        val restored = Gson().fromJson(Gson().toJson(before), TaobaoProductBatchProgress::class.java)
        assertEquals(1, restored.index)
        assertEquals(1, restored.savedCount)
        assertEquals("second", restored.activeToken)
        val finished = restored.editorReturned("second:saved")
        assertTrue(finished.completed)
        assertEquals(2, finished.savedCount)
    }
}
