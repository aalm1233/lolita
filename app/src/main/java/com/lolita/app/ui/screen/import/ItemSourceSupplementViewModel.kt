package com.lolita.app.ui.screen.`import`

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.model.ProductSupplementPayload
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.OrderImportSource
import com.lolita.app.ui.screen.common.uiErrorText
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class ProductSupplementState(
    val item: Item? = null,
    val candidate: TaobaoProductDraft? = null,
    val sourceUrl: String = "",
    val draft: ItemDraft? = null,
    val selected: Set<String> = emptySet(),
    val selectedBrand: Long? = null,
    val selectedCategory: Long? = null,
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val sizeInput: String? = null,
    val colorInput: String = "",
    val busy: Boolean = true,
    val error: String? = null,
    val existingEditor: Boolean = false,
)

internal class ItemSourceSupplementViewModel(
    app: Application,
    private val itemId: Long,
    private val db: LolitaDatabase = AppModule.database(),
    private val items: ItemRepository = AppModule.itemRepository(),
    private val drafts: ItemDraftRepository = AppModule.itemDraftRepository(),
) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(ProductSupplementState())
    val state = mutable.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val item = items.getItemById(itemId) ?: error("物品已删除，请返回衣橱")
                val draft = drafts.getForItem(itemId)
                val payload =
                    draft?.data()?.sourceSupplementJson?.let(ProductSupplementPayload::decode)
                val candidate =
                    payload?.let { p ->
                        p.validate(draft.evidenceImagePaths.size)
                        p.candidate.copy(
                            imagePaths = p.photoIndices.map { draft.evidenceImagePaths[it] },
                            sizeChartImagePaths =
                                p.detailIndices.map { draft.evidenceImagePaths[it] },
                        )
                    }
                val source =
                    db.orderImportDao()
                        .sources()
                        .lastOrNull { it.itemId == itemId && it.publicUrl != null }
                        ?.publicUrl
                        .orEmpty()
                mutable.value =
                    ProductSupplementState(
                        item,
                        candidate,
                        source,
                        draft,
                        selected = payload?.appliedFields?.toSet().orEmpty(),
                        sizeInput = item.size,
                        colorInput = item.colors.joinToString("、"),
                        brands = db.brandDao().getAllBrandsList(),
                        categories = db.categoryDao().getAllCategoriesList(),
                        busy = false,
                        existingEditor = payload?.existingEditor ?: (draft != null),
                    )
            } catch (failure: Exception) {
                mutable.value =
                    mutable.value.copy(busy = false, error = uiErrorText(failure, "物品资料无法读取，请返回重试"))
            }
        }
    }

    fun select(field: String, selected: Boolean) {
        mutable.update {
            it.copy(selected = if (selected) it.selected + field else it.selected - field)
        }
    }

    fun brand(id: Long) {
        mutable.update { it.copy(selectedBrand = id, selected = it.selected + "brandId") }
    }

    fun category(id: Long) {
        mutable.update { it.copy(selectedCategory = id, selected = it.selected + "categoryId") }
    }

    fun size(value: String?) {
        mutable.update { it.copy(sizeInput = value, selected = it.selected + "size") }
    }

    fun color(value: String) {
        mutable.update { it.copy(colorInput = value, selected = it.selected + "colors") }
    }

    fun stage(candidate: TaobaoProductDraft) {
        if (state.value.busy) return
        viewModelScope.launch {
            val lease =
                ImageFileHelper.pinImageReferences(
                    getApplication(),
                    candidate.imagePaths + candidate.sizeChartImagePaths,
                )
            mutable.update { it.copy(busy = true, error = null) }
            try {
                db.withTransaction {
                    val item = items.getItemById(itemId) ?: error("物品已删除，请返回衣橱")
                    val old = drafts.getForItem(itemId)
                    require(old == null || old.data().baselineUpdatedAt == item.updatedAt) {
                        "原编辑草稿已过期，请先核对原编辑内容"
                    }
                    val existed =
                        old?.data()
                            ?.sourceSupplementJson
                            ?.let(ProductSupplementPayload::decode)
                            ?.existingEditor ?: (old != null)
                    val base =
                        old
                            ?: ItemDraft(
                                targetItemId = itemId,
                                dataJson =
                                    ItemDraftData(
                                            name = item.name,
                                            description = item.description,
                                            brandId = item.brandId,
                                            categoryId = item.categoryId,
                                            coordinateId = item.coordinateId,
                                            parentItemId = item.parentItemId,
                                            includedInParentPrice = item.includedInParentPrice,
                                            status = item.status,
                                            priority = item.priority,
                                            colors = item.colors,
                                            seasons = item.season?.split(',').orEmpty(),
                                            style = item.style,
                                            size = item.size,
                                            source = item.source,
                                            locationId = item.locationId,
                                            storageNodeId = item.storageNodeId,
                                            specificationsJson = item.specificationsJson,
                                            composition = item.composition,
                                            importToken = item.importToken,
                                            baselineUpdatedAt = item.updatedAt,
                                        )
                                        .encode(),
                                imagePaths = item.imageUrls,
                                sizeChartImagePaths =
                                    listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls,
                            )
                    val images =
                        (base.evidenceImagePaths +
                                candidate.imagePaths +
                                candidate.sizeChartImagePaths)
                            .distinct()
                    val safe =
                        candidate.copy(
                            imagePaths = emptyList(),
                            sizeChartImagePath = null,
                            sizeChartImagePaths = emptyList(),
                            importToken = null,
                            editorToken = null,
                            productUrl =
                                OrderImportSource.publicUrl(candidate.productUrl)
                                    ?: error("商品公开链接无效"),
                        )
                    val payload =
                        ProductSupplementPayload(
                            candidate = safe,
                            photoIndices = candidate.imagePaths.map(images::indexOf),
                            detailIndices = candidate.sizeChartImagePaths.map(images::indexOf),
                            existingEditor = existed,
                        )
                    val saved =
                        base.copy(
                            evidenceImagePaths = images,
                            dataJson =
                                base.data().copy(sourceSupplementJson = payload.encode()).encode(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    drafts.save(saved)
                    mutable.update {
                        it.copy(
                            item = item,
                            candidate = candidate,
                            draft = saved,
                            busy = false,
                            existingEditor = existed,
                            sizeInput = base.data().size,
                            colorInput = base.data().colors.joinToString("、"),
                            selected =
                                buildSet {
                                    if (candidate.imagePaths.isNotEmpty()) add("photos")
                                    if (candidate.sizeChartImagePaths.isNotEmpty()) add("details")
                                },
                        )
                    }
                }
            } catch (failure: Exception) {
                mutable.update {
                    it.copy(busy = false, error = uiErrorText(failure, "补充草稿未保存，请重试"))
                }
            } finally {
                lease.release()
            }
        }
    }

    private fun dedup(paths: List<String>): List<String> {
        val hashes = mutableSetOf<String>()
        return paths.filter { path ->
            val file = File(path)
            if (!file.isFile) false
            else
                hashes.add(
                    file.inputStream().use { stream ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                        }
                        digest.digest().joinToString("") { "%02x".format(it) }
                    }
                )
        }
    }

    fun save(confirmedSku: Boolean, onDone: (String?) -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            val frozen = state.value
            val candidate = frozen.candidate ?: return@launch
            val snapshot = frozen.draft ?: return@launch
            if (!confirmedSku) {
                mutable.update { it.copy(error = "请确认网页规格属于本件物品") }
                return@launch
            }
            if (frozen.selected.isEmpty()) {
                mutable.update { it.copy(error = "请至少选择一项补充资料") }
                return@launch
            }
            mutable.update { it.copy(busy = true, error = null) }
            try {
                var editor: String? = null
                db.withTransaction {
                    val current = items.getItemById(itemId) ?: error("物品已删除，请返回衣橱")
                    val draft = drafts.get(snapshot.id) ?: error("原编辑草稿已关闭，请重新比较")
                    require(
                        current.updatedAt == frozen.item?.updatedAt &&
                            draft.updatedAt == snapshot.updatedAt &&
                            draft.dataJson == snapshot.dataJson
                    ) {
                        "物品或编辑草稿已有更新，请重新比较，不能覆盖其他修改"
                    }
                    val selected = frozen.selected
                    val data = draft.data()
                    val payload =
                        ProductSupplementPayload.decode(requireNotNull(data.sourceSupplementJson))
                            .copy(appliedFields = selected.toList())
                    require(data.baselineUpdatedAt == current.updatedAt) { "原编辑草稿已过期，请先核对原编辑内容" }
                    val merged =
                        data.copy(
                            description =
                                if ("specifications" in selected)
                                    data.description + "\n商品参数（用户选用）：\n" + candidate.properties
                                else data.description,
                            name = if ("name" in selected) candidate.name else data.name,
                            brandId =
                                if ("brandId" in selected) frozen.selectedBrand ?: data.brandId
                                else data.brandId,
                            categoryId =
                                if ("categoryId" in selected)
                                    frozen.selectedCategory ?: data.categoryId
                                else data.categoryId,
                            size = if ("size" in selected) frozen.sizeInput else data.size,
                            colors =
                                if ("colors" in selected)
                                    frozen.colorInput
                                        .split(Regex("[、，,]"))
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                        .distinct()
                                else data.colors,
                            sourceSupplementJson = payload.encode(),
                        )
                    val photos =
                        if ("photos" in selected) dedup(draft.imagePaths + candidate.imagePaths)
                        else draft.imagePaths
                    val details =
                        if ("details" in selected)
                            dedup(draft.sizeChartImagePaths + candidate.sizeChartImagePaths)
                        else draft.sizeChartImagePaths
                    val saved =
                        draft.copy(
                            dataJson = merged.encode(),
                            imagePaths = photos,
                            sizeChartImagePaths = details,
                            evidenceImagePaths = draft.evidenceImagePaths,
                            updatedAt = System.currentTimeMillis(),
                        )
                    drafts.save(saved)
                    if (frozen.existingEditor) editor = saved.id
                    else {
                        drafts.commit(
                            saved.id,
                            current.copy(
                                description = merged.description,
                                name = merged.name,
                                brandId = merged.brandId,
                                categoryId = merged.categoryId,
                                size = merged.size,
                                colors = merged.colors,
                                imageUrls = photos,
                                sizeChartImageUrl = details.firstOrNull(),
                                sizeChartImageUrls = details.drop(1),
                            ),
                        )
                    }
                }
                ImageFileHelper.cleanupUnreferencedImages(
                    candidate.imagePaths + candidate.sizeChartImagePaths
                )
                mutable.update { it.copy(busy = false) }
                onDone(editor)
            } catch (failure: Exception) {
                mutable.update {
                    it.copy(busy = false, error = uiErrorText(failure, "补充资料未保存，请重新比较后重试"))
                }
            }
        }
    }
}
