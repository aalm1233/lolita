package com.lolita.app.ui.screen.`import`

import com.lolita.app.data.file.TaobaoProductDraft
import com.lolita.app.data.file.TaobaoProductQuotes
import java.util.UUID

/** Import metadata and result progression, independent of Android and image I/O. */
internal data class TaobaoProductBatchProgress(
    val items: List<TaobaoProductDraft>,
    val index: Int = 0,
    val savedCount: Int = 0,
    val activeToken: String? = null,
    val paused: Boolean = false,
    val completed: Boolean = false
) {
    fun currentItem(): TaobaoProductDraft? = if (completed) null else items.getOrNull(index)

    fun editorReturned(result: String): TaobaoProductBatchProgress {
        val token = activeToken ?: return this
        if (completed || currentItem() == null) return this
        return when (result) {
            "$token:saved" -> copy(index = index + 1, savedCount = savedCount + 1,
                activeToken = null, paused = false, completed = index + 1 == items.size)
            "$token:discarded" -> copy(activeToken = null, paused = true)
            else -> this
        }
    }
}

internal fun taobaoProductVariants(draft: TaobaoProductDraft, splitColorVariants: Boolean, splitSkuVariants: Boolean = false) =
    if (splitSkuVariants) draft.skuVariants.orEmpty()
    else draft.variants.orEmpty().ifEmpty { if (splitColorVariants) draft.colorVariants.orEmpty() else emptyList() }

internal fun taobaoProductRequiresSelection(draft: TaobaoProductDraft, splitColorVariants: Boolean, splitSkuVariants: Boolean = false) =
    splitSkuVariants || taobaoProductVariants(draft, splitColorVariants).size > 1

internal fun taobaoProductBatchItems(draft: TaobaoProductDraft, selectedIds: Set<String>,
    splitColorVariants: Boolean = false, splitSkuVariants: Boolean = false): List<TaobaoProductDraft> {
    val original = draft.copy(imagePaths = emptyList(), sizeChartImagePath = null,
        sizeChartImagePaths = emptyList(), variants = emptyList(), importToken = null, editorToken = null,
        batchIndex = 0, batchTotal = 0, colorVariants = emptyList(), skuVariants = emptyList(),
        skuSelectionIssue = null, selectedSkuId = null)
    val variants = taobaoProductVariants(draft, splitColorVariants, splitSkuVariants)
    if (!taobaoProductRequiresSelection(draft, splitColorVariants, splitSkuVariants))
        return listOf(original.copy(importToken = UUID.randomUUID().toString()))
    val selected = variants.distinctBy { it.id }.filter { it.id in selectedIds }
    if (selected.size > 30 || splitSkuVariants && selected.any { it.size.isNullOrBlank() }) return emptyList()
    return selected.map { variant ->
        original.copy(name = "${draft.name} · ${variant.name}", category = variant.category,
            size = if (splitSkuVariants) variant.size.orEmpty() else original.size,
            selectedSkuId = variant.id.takeIf { splitSkuVariants },
            referenceQuote = variant.referenceQuote,
            description = TaobaoProductQuotes.withQuote(original.description, variant.referenceQuote),
            importToken = UUID.randomUUID().toString(),
            categoryCandidates = listOf(variant.category).filter { it.isNotBlank() })
    }
}

/** Refuse missing copies rather than accidentally giving the editor a shared master file. */
internal fun taobaoProductEditorDraft(item: TaobaoProductDraft, master: TaobaoProductDraft,
    copies: Map<String, String>, token: String, index: Int, total: Int): TaobaoProductDraft {
    val masters = (master.imagePaths.orEmpty() + listOfNotNull(master.sizeChartImagePath) +
        master.sizeChartImagePaths.orEmpty()).toSet()
    require(masters.all { copies[it] != null && copies[it] !in masters }) { "图片副本不完整" }
    require(masters.map { copies.getValue(it) }.distinct().size == masters.size) { "图片副本不能共用" }
    return item.copy(imagePaths = master.imagePaths.orEmpty().map(copies::getValue),
        sizeChartImagePath = master.sizeChartImagePath?.let(copies::getValue),
        sizeChartImagePaths = master.sizeChartImagePaths.orEmpty().map(copies::getValue),
        importToken = item.importToken, editorToken = token, batchIndex = index + 1, batchTotal = total)
}

/** SavedState contains shared product metadata once, rather than once per selected piece. */
internal fun taobaoProductCompactBatch(batch: TaobaoProductBatchProgress): TaobaoProductBatchProgress =
    batch.copy(items = batch.items.map { item ->
        TaobaoProductDraft(name = item.name, brand = "", size = item.size, properties = "", description = "",
            productUrl = "", remoteImages = emptyList(), category = item.category,
            categoryCandidates = item.categoryCandidates, importToken = item.importToken, referenceQuote = item.referenceQuote,
            selectedSkuId = item.selectedSkuId)
    })

internal fun taobaoProductRestoreBatch(batch: TaobaoProductBatchProgress,
    original: TaobaoProductDraft): TaobaoProductBatchProgress {
    val shared = original.copy(imagePaths = emptyList(), sizeChartImagePath = null,
        sizeChartImagePaths = emptyList(), variants = emptyList(), importToken = null, editorToken = null,
        batchIndex = 0, batchTotal = 0, colorVariants = emptyList(), skuVariants = emptyList(),
        skuSelectionIssue = null, selectedSkuId = null)
    return batch.copy(items = batch.items.map { item -> shared.copy(name = item.name,
        category = item.category, categoryCandidates = item.categoryCandidates,
        size = if (item.selectedSkuId != null) item.size.orEmpty() else shared.size,
        selectedSkuId = item.selectedSkuId,
        referenceQuote = item.referenceQuote,
        description = TaobaoProductQuotes.withQuote(shared.description, item.referenceQuote),
        importToken = item.importToken?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()) })
}
