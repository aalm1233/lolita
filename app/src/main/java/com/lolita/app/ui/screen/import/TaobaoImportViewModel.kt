package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoOrderParser
import com.lolita.app.data.file.TaobaoProductParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.PaymentStage
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.local.entity.StorageNode
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OrderImportRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.WardrobeFamily
import com.lolita.app.ui.screen.common.uiErrorText
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ImportStep {
    ENTRY,
    FILES,
    SELECT,
    CONFLICT,
    PREPARE,
    DETAIL,
    REVIEW,
    IMPORTING,
    RESULT,
    ADDED,
    STORAGE,
}

enum class PaymentRole {
    DEPOSIT,
    BALANCE,
}

/** 待导入商品的可编辑状态 */
data class ImportItemState(
    val originalItem: TaobaoOrderItem,
    val name: String,
    val brandId: Long = 0L,
    val categoryId: Long = 0L,
    val color: String = "",
    val size: String = "",
    val price: Double = 0.0,
    val purchaseDate: String = "",
    val imageUrl: String? = null,
    val styleSpec: String = "",
    val paymentRole: PaymentRole? = null,
    val pairedWith: Int? = null,
    val manualBalance: Double? = null,
    val balanceDueDate: Long? = null,
    val shipping: Double = 0.0,
    val paidAmount: Double = 0.0,
    val financialConfirmed: Boolean = false,
    val sourceLineIndex: Int = 0,
    val sourceUnitIndex: Int = 0,
    // Keep raw drafts alongside parsed values so navigation never discards incomplete input.
    val priceInput: String? = null,
    val shippingInput: String? = null,
    val paidInput: String? = null,
    val balanceInput: String? = null,
    val specifications: WardrobeSpecifications = WardrobeSpecifications(),
    val composition: ItemComposition = ItemComposition.UNCONFIRMED,
    val locationId: Long? = null,
    val storageNodeId: Long? = null,
    val locationMappingNote: String? = null,
    val sourceKey: String = "",
    val sourceGroupKey: String = "",
    val sourceOrdinal: Int = 0,
    val committedItemId: Long? = null,
    val sourceProblem: String? = null,
    val identityConfirmed: Boolean = false,
    val allowDeletedReimport: Boolean = false,
)

data class ImportResult(
    val importedCount: Int = 0,
    val mergedCount: Int = 0, // 定金尾款合并数
    val skippedCount: Int = 0, // 未完善跳过数
    val itemIds: List<Long> = emptyList(),
    val commitId: String? = null,
    val linkedCount: Int = 0,
)

/** 预处理阶段发现的缺失数据项 */
data class MissingDataItem(
    val name: String,
    val type: MissingDataType,
    val checked: Boolean = true,
    val extra: String = "", // 附加信息，如 CategoryGroup
    val semantic: CategorySemantic = CategorySemantic.UNCONFIRMED,
)

enum class MissingDataType {
    BRAND,
    CATEGORY,
}

data class ImportSourceConflict(val orderId: String, val versions: List<TaobaoOrder>)

data class ImportSessionSummary(
    val id: String,
    val updatedAt: Long,
    val remaining: Int,
    val added: Int,
)

data class TaobaoImportUiState(
    val orders: List<TaobaoOrder> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fileLoaded: Boolean = false,
    val fileCount: Int = 0,
    val dedupCount: Int = 0,
    // Step flow
    val currentStep: ImportStep = ImportStep.ENTRY,
    val importItems: List<ImportItemState> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val locations: List<Location> = emptyList(),
    val storageNodes: List<StorageNode> = emptyList(),
    val currentItemIndex: Int = 0,
    // Prepare step: missing data
    val missingItems: List<MissingDataItem> = emptyList(),
    // Import result
    val importResult: ImportResult? = null,
    val sessionId: String? = null,
    val sessions: List<ImportSessionSummary> = emptyList(),
    val files: List<OrderImportFile> = emptyList(),
    val conflicts: List<ImportSourceConflict> = emptyList(),
    val query: String = "",
    val selectionFilter: String = "全部",
    val shopFilter: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val isRestoring: Boolean = true,
    val draftSaving: Boolean = false,
    val draftError: String? = null,
    val brandMappings: Map<String, Long> = emptyMap(),
    val newBrandNames: Set<String> = emptySet(),
    val resultItems: List<Item> = emptyList(),
    val linkedResultItems: List<Item> = emptyList(),
    val resultItemIds: List<Long> = emptyList(),
    val deletedResultItemIds: List<Long> = emptyList(),
)

class TaobaoImportViewModel
@JvmOverloads
constructor(
    application: Application,
    private val brandRepository: BrandRepository = AppModule.brandRepository(),
    private val categoryRepository: CategoryRepository = AppModule.categoryRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository(),
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val orderRepository: com.lolita.app.data.repository.OrderRepository =
        AppModule.orderRepository(),
    private val database: LolitaDatabase = AppModule.database(),
    private val sessionRepository: OrderImportRepository = OrderImportRepository(database),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TaobaoImportUiState())
    val uiState: StateFlow<TaobaoImportUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val saveMutex = Mutex()
    private var revision = 0L
    private var restoring = true
    private var receipts: List<OrderImportReceipt> = emptyList()
    private var operationId: String? = null
    private val imageLeases = mutableListOf<ImageFileHelper.ImageReferenceLease>()

    // Only files copied during this import are ours to discard. Persisted/shared paths
    // are protected by ImageFileHelper's database reference check.
    private val draftImages = mutableSetOf<String>()
    private var imageGeneration = 0L
    private val imageRequests = mutableMapOf<Int, Long>()

    private fun releaseDraftImages() {
        imageGeneration++
        imageRequests.clear()
        val paths = draftImages.toList()
        draftImages.clear()
        ImageFileHelper.cleanupUnreferencedImages(paths)
    }

    private fun releaseUnusedDraftImages() {
        val retained = _uiState.value.importItems.mapNotNull { it.imageUrl }.toSet()
        val removed = draftImages.filter { it !in retained }
        draftImages.removeAll(removed.toSet())
        ImageFileHelper.cleanupUnreferencedImages(removed)
    }

    override fun onCleared() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (_uiState.value.currentStep != ImportStep.IMPORTING) saveDraft()
            imageLeases.toList().forEach { it.release() }
            imageLeases.clear()
        }
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            receipts = sessionRepository.receipts()
            var initialListed = false
            sessionRepository.observeSessions().collect { sessions ->
                val summaries =
                    sessions.mapNotNull { session ->
                        runCatching {
                                val rows = sessionRepository.rows(session.id)
                                val commits = sessionRepository.commits(session.id)
                                ImportSessionSummary(
                                    session.id,
                                    session.updatedAt,
                                    rows.count { row ->
                                        JsonParser.parseString(row.dataJson)
                                            .asJsonObject["committedItemId"]
                                            ?.isJsonNull != false
                                    },
                                    commits.sumOf { it.itemIds.size },
                                )
                            }
                            .getOrNull()
                    }
                _uiState.value =
                    _uiState.value.copy(
                        sessions = summaries,
                        isRestoring = if (initialListed) _uiState.value.isRestoring else false,
                    )
                if (!initialListed) {
                    restoring = false
                    initialListed = true
                }
            }
        }
        viewModelScope.launch {
            uiState
                .map { persistenceKey(it) }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    if (
                        !restoring &&
                            _uiState.value.sessionId != null &&
                            !_uiState.value.isRestoring &&
                            _uiState.value.currentStep != ImportStep.IMPORTING
                    ) {
                        delay(100)
                        saveDraft()
                    }
                }
        }
        // 合并品牌和分类列表的加载，避免竞态
        viewModelScope.launch {
            brandRepository.ensureUnsetBrand()
            categoryRepository.ensurePresetCategories()
            combine(
                    brandRepository.getAllBrands(),
                    categoryRepository.getAllCategories(),
                    database.locationDao().getAllLocations(),
                    database.storageNodeDao().observeAll(),
                ) { brands, categories, locations, nodes ->
                    TaobaoImportUiState(
                        brands = brands,
                        categories = categories,
                        locations = locations,
                        storageNodes = nodes,
                    )
                }
                .collect { data ->
                    _uiState.value =
                        _uiState.value.copy(
                            brands = data.brands,
                            categories = data.categories,
                            locations = data.locations,
                            storageNodes = data.storageNodes,
                        )
                }
        }
    }

    fun onFileSelected(uris: List<Uri>) {
        if (
            uris.isEmpty() ||
                _uiState.value.currentStep == ImportStep.IMPORTING ||
                _uiState.value.isRestoring
        )
            return
        if (uris.size + _uiState.value.files.size > 10) {
            showError("每次会话最多读取10个文件")
            return
        }
        val sessionId = _uiState.value.sessionId ?: UUID.randomUUID().toString()
        _uiState.value =
            _uiState.value.copy(
                sessionId = sessionId,
                isLoading = true,
                currentStep = ImportStep.FILES,
                errorMessage = null,
            )
        viewModelScope.launch(Dispatchers.IO) {
            val files = _uiState.value.files.toMutableList()
            for (uri in uris) {
                var name = "订单文件.xlsx"
                try {
                    getApplication<Application>()
                        .contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c ->
                            if (c.moveToFirst())
                                name = c.getString(0).substringAfterLast('/').take(120)
                        }
                    val bytes =
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                            input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 20 * 1024 * 1024) {
                                    "文件超过20MiB，请拆分后重试"
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("无法读取文件，请重新选择")
                    val hash =
                        java.security.MessageDigest.getInstance("SHA-256")
                            .digest(bytes)
                            .joinToString("") { "%02x".format(it) }
                    if (files.any { it.sha256 == hash && it.state == "READY" }) continue
                    val parsed =
                        TaobaoOrderParser.parse(ByteArrayInputStream(bytes)).map {
                            it.copy(sourceFileName = name)
                        }
                    require(parsed.sumOf { o -> o.items.sumOf { it.quantity } } <= 2000) {
                        "文件展开资料超过2000条，请拆分"
                    }
                    files.removeAll { it.name == name && it.state == "FAILED" }
                    files +=
                        OrderImportFile(
                            sessionId = sessionId,
                            name = name,
                            sha256 = hash,
                            state = "READY",
                            dataJson = gson.toJson(parsed),
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    files +=
                        OrderImportFile(
                            sessionId = sessionId,
                            name = name,
                            sha256 = "",
                            state = "FAILED",
                            problem = uiErrorText(failure, "文件读取失败，请重新导出后选择"),
                        )
                }
            }
            mergeFiles(files)
            saveDraft()
        }
    }

    private fun mergeFiles(files: List<OrderImportFile>) {
        val all =
            files
                .filter { it.state == "READY" }
                .flatMap { gson.fromJson(it.dataJson, Array<TaobaoOrder>::class.java).toList() }
        val conflicts =
            all.groupBy { it.orderId }
                .mapNotNull { (id, rows) ->
                    val versions = rows.distinctBy(OrderImportSource::snapshot)
                    if (versions.size > 1) ImportSourceConflict(id, versions) else null
                }
        val orders = all.distinctBy { it.orderId }
        if (orders.sumOf { it.items.sumOf { row -> row.quantity } } > 2000) {
            showError("会话展开资料超过2000条，请移除文件后继续")
            return
        }
        val old = _uiState.value
        val validKeys =
            orders.flatMap { order -> order.items.indices.map { "${order.orderId}:$it" } }.toSet()
        val selected =
            if (old.fileLoaded) old.selectedItems.intersect(validKeys)
            else
                orders
                    .flatMap { order ->
                        order.items.mapIndexedNotNull { index, item ->
                            if (order.orderStatus == "交易成功" && !item.styleSpec.contains("意向金"))
                                "${order.orderId}:$index"
                            else null
                        }
                    }
                    .toSet()
        _uiState.value =
            old.copy(
                files = files,
                orders = orders,
                conflicts = conflicts,
                selectedItems = selected,
                fileLoaded = orders.isNotEmpty(),
                fileCount = files.count { it.state == "READY" },
                dedupCount = all.size - orders.size,
                isLoading = false,
                currentStep = ImportStep.FILES,
            )
    }

    fun useReadFiles() {
        if (_uiState.value.fileLoaded) setStep(ImportStep.SELECT)
    }

    fun resolveConflict(orderId: String, version: Int) {
        val chosen =
            _uiState.value.conflicts
                .firstOrNull { it.orderId == orderId }
                ?.versions
                ?.getOrNull(version) ?: return
        _uiState.value =
            _uiState.value.copy(
                orders = _uiState.value.orders.map { if (it.orderId == orderId) chosen else it },
                conflicts = _uiState.value.conflicts.filterNot { it.orderId == orderId },
                importItems =
                    _uiState.value.importItems.map { row ->
                        if (row.originalItem.orderId == orderId && row.committedItemId == null)
                            row.copy(
                                financialConfirmed = false,
                                identityConfirmed = false,
                                sourceProblem = "来源版本变化：人工输入已保留，请对照新来源重新核对",
                            )
                        else row
                    },
            )
    }

    fun affectedByFileRemoval(id: String): List<ImportItemState> {
        val remainingOrders =
            _uiState.value.files
                .filter { it.id != id && it.state == "READY" }
                .flatMap { gson.fromJson(it.dataJson, Array<TaobaoOrder>::class.java).toList() }
                .map { it.orderId }
                .toSet()
        return _uiState.value.importItems.filter {
            it.originalItem.orderId !in remainingOrders && it.committedItemId == null
        }
    }

    fun removeFile(id: String) {
        if (_uiState.value.isLoading || _uiState.value.currentStep == ImportStep.IMPORTING) return
        val oldRows = _uiState.value.importItems
        val removed = affectedByFileRemoval(id).map { it.sourceKey }.toSet()
        val kept = oldRows.filterNot { it.sourceKey in removed }
        val remapped =
            kept.map { row ->
                row.copy(
                    pairedWith =
                        row.pairedWith
                            ?.let { oldRows.getOrNull(it)?.sourceKey }
                            ?.let { key ->
                                kept.indexOfFirst { it.sourceKey == key }.takeIf { it >= 0 }
                            },
                    financialConfirmed =
                        row.financialConfirmed &&
                            (row.pairedWith == null ||
                                oldRows.getOrNull(row.pairedWith)?.sourceKey !in removed),
                )
            }
        _uiState.value = _uiState.value.copy(importItems = remapped)
        mergeFiles(_uiState.value.files.filterNot { it.id == id })
    }

    fun setStep(step: ImportStep) {
        if (_uiState.value.currentStep != ImportStep.IMPORTING)
            _uiState.value = _uiState.value.copy(currentStep = step)
    }

    fun newSession(onReady: () -> Unit = {}) {
        viewModelScope.launch {
            if (!saveDraft()) return@launch
            operationId = null
            revision = 0
            _uiState.value =
                TaobaoImportUiState(
                    brands = _uiState.value.brands,
                    categories = _uiState.value.categories,
                    locations = _uiState.value.locations,
                    storageNodes = _uiState.value.storageNodes,
                    sessions = _uiState.value.sessions,
                    isRestoring = false,
                    currentStep = ImportStep.FILES,
                )
            onReady()
        }
    }

    fun reportImportError(message: String) = showError(message)

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message, isLoading = false)
    }

    fun setQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun setSelectionFilter(value: String) {
        _uiState.value = _uiState.value.copy(selectionFilter = value)
    }

    fun setSelectionBounds(shop: String, from: String, to: String) {
        _uiState.value = _uiState.value.copy(shopFilter = shop, dateFrom = from, dateTo = to)
    }

    fun selectVisible(selected: Boolean) {
        val keys =
            visibleOrderLines(_uiState.value).map { "${it.first.orderId}:${it.second}" }.toSet()
        _uiState.value =
            _uiState.value.copy(
                selectedItems =
                    if (selected) _uiState.value.selectedItems + keys
                    else _uiState.value.selectedItems - keys
            )
    }

    fun toggleItem(orderId: String, itemIndex: Int) {
        val key = "$orderId:$itemIndex"
        val current = _uiState.value.selectedItems.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _uiState.value = _uiState.value.copy(selectedItems = current)
    }

    fun selectAll() {
        val all = mutableSetOf<String>()
        _uiState.value.orders.forEach { order ->
            order.items.forEachIndexed { index, _ -> all.add("${order.orderId}:$index") }
        }
        _uiState.value = _uiState.value.copy(selectedItems = all)
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun getSelectedItems(): List<TaobaoOrderItem> {
        val result = mutableListOf<TaobaoOrderItem>()
        _uiState.value.orders.forEach { order ->
            order.items.forEachIndexed { index, item ->
                if ("${order.orderId}:$index" in _uiState.value.selectedItems) {
                    result.add(item)
                }
            }
        }
        return result
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** 从选择步骤进入预处理步骤，扫描缺失的品牌和类型 */
    fun proceedToPrepare() {
        if (_uiState.value.conflicts.isNotEmpty()) {
            setStep(ImportStep.CONFLICT)
            return
        }
        val selected = getSelectedItems()
        if (selected.isEmpty()) return

        val brands = _uiState.value.brands
        val categories = _uiState.value.categories
        val brandNames = brands.map { it.name.lowercase() }.toSet()
        val categoryNames = categories.map { it.name.lowercase() }.toSet()

        val missingBrands = mutableSetOf<String>()
        val missingCategories = mutableMapOf<String, String>() // name → group hint

        for (item in selected) {
            // 检查品牌（基于店铺名匹配）
            if (matchBrand(item.shopName, brands) == null && item.shopName.isNotBlank()) {
                missingBrands.add(item.shopName)
            }
            // 检查类型（基于解析出的 parsedType）
            if (item.parsedType != null && matchCategory(item.parsedType, categories) == null) {
                val group = guessCategoryGroup(item.parsedType)
                missingCategories[item.parsedType] = group
            }
        }

        val missingItems = mutableListOf<MissingDataItem>()
        missingBrands.sorted().forEach {
            missingItems.add(
                MissingDataItem(name = it, type = MissingDataType.BRAND, checked = false)
            )
        }
        missingCategories.entries
            .sortedBy { it.key }
            .forEach { (name, group) ->
                val semantic =
                    CategorySemantic.entries.firstOrNull { it.label.equals(name, true) }
                        ?: TaobaoProductParser.explicitCategoryCandidates(name, emptyMap())
                            .firstOrNull()
                            ?.let { candidate ->
                                CategorySemantic.entries.firstOrNull {
                                    it.label.equals(candidate, true)
                                }
                            }
                        ?: CategorySemantic.UNCONFIRMED
                missingItems.add(
                    MissingDataItem(
                        name = name,
                        type = MissingDataType.CATEGORY,
                        extra = group,
                        semantic = semantic,
                        checked = semantic != CategorySemantic.UNCONFIRMED,
                    )
                )
            }

        if (missingItems.isEmpty()) {
            // 没有缺失数据，直接进入完善步骤
            buildDetailItems()
        } else {
            _uiState.value =
                _uiState.value.copy(currentStep = ImportStep.PREPARE, missingItems = missingItems)
        }
    }

    fun toggleMissingItem(index: Int) {
        val items = _uiState.value.missingItems.toMutableList()
        if (index in items.indices) {
            items[index] = items[index].copy(checked = !items[index].checked)
            _uiState.value = _uiState.value.copy(missingItems = items)
        }
    }

    fun confirmMissingSemantic(index: Int, semantic: CategorySemantic) {
        _uiState.value =
            _uiState.value.copy(
                missingItems =
                    _uiState.value.missingItems.mapIndexed { i, row ->
                        if (i == index) row.copy(semantic = semantic, checked = true) else row
                    }
            )
    }

    fun toggleAllMissingItems(checked: Boolean) {
        _uiState.value =
            _uiState.value.copy(
                missingItems = _uiState.value.missingItems.map { it.copy(checked = checked) }
            )
    }

    /** 确认预处理：创建勾选的缺失数据，然后进入完善步骤 */
    fun confirmPrepare() {
        val checkedItems = _uiState.value.missingItems.filter { it.checked }
        if (
            checkedItems.any {
                it.type == MissingDataType.CATEGORY && it.semantic == CategorySemantic.UNCONFIRMED
            }
        ) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先确认所选新类型的品类与默认穿戴位置")
            return
        }
        if (checkedItems.isEmpty()) {
            buildDetailItems()
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (item in checkedItems) {
                    when (item.type) {
                        MissingDataType.BRAND -> {
                            _uiState.value =
                                _uiState.value.copy(
                                    newBrandNames = _uiState.value.newBrandNames + item.name,
                                    brandMappings = _uiState.value.brandMappings - item.name,
                                )
                        }
                        MissingDataType.CATEGORY -> {
                            val group =
                                if (item.semantic.family == WardrobeFamily.CLOTHING)
                                    CategoryGroup.CLOTHING
                                else CategoryGroup.ACCESSORY
                            categoryRepository.insertCategory(
                                Category(
                                    name = item.name,
                                    group = group,
                                    categorySemantic = item.semantic,
                                    defaultWearRole = item.semantic.defaultWearRole,
                                )
                            )
                        }
                    }
                }
                // 等待 Flow 更新品牌和分类列表
                val brands = brandRepository.getAllBrands().first()
                val categories = categoryRepository.getAllCategories().first()
                withContext(Dispatchers.Main.immediate) {
                    _uiState.value =
                        _uiState.value.copy(
                            brands = brands,
                            categories = categories,
                            isLoading = false,
                        )
                    buildDetailItems()
                }
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        errorMessage = uiErrorText(e, "品牌或类型未能添加，请重试"),
                    )
            }
        }
    }

    fun goBackToSelectFromPrepare() {
        _uiState.value =
            _uiState.value.copy(currentStep = ImportStep.SELECT, missingItems = emptyList())
    }

    /** 构建 ImportItemState 列表并进入完善步骤 */
    private fun buildDetailItems() {
        if (_uiState.value.selectedItems.isEmpty()) return
        val brands = _uiState.value.brands
        val categories = _uiState.value.categories
        val importItems = mutableListOf<ImportItemState>()
        try {
            _uiState.value.orders.forEach { order ->
                require(order.items.all { it.quantity in 1..100 }) {
                    "订单 ${order.orderId} 商品数量无效或超过100，请先核对文件"
                }
                val pieces =
                    order.items.flatMapIndexed { line, item ->
                        List(item.quantity) { unit -> Triple(line, unit, item) }
                    }
                // Allocate against every original piece before selection. Skipped pieces keep their
                // share.
                val sourceShipping = OrderImportSource.amount(order.rawShipping, order.shipping)
                val sourcePaid = OrderImportSource.amount(order.rawPaid, order.totalPaid)
                val sourcePricesValid =
                    order.items.all { OrderImportSource.amount(it.rawPrice, it.price) != null }
                val ordinals = OrderImportSource.ordinals(order)
                val stableOrder =
                    pieces.indices.sortedWith(
                        compareBy<Int> { OrderImportSource.group(pieces[it].third) }
                            .thenBy { ordinals.getValue(pieces[it].first to pieces[it].second) }
                    )
                val shippingShares =
                    allocateImportMoney(sourceShipping ?: 0.0, List(pieces.size) { 1.0 })
                val paidShares =
                    allocateImportMoney(
                        sourcePaid ?: 0.0,
                        stableOrder.map { pieces[it].third.price.coerceAtLeast(0.0) },
                    )
                val stablePositions = stableOrder.withIndex().associate { it.value to it.index }
                val shipping = pieces.indices.map { shippingShares[stablePositions.getValue(it)] }
                val paid = pieces.indices.map { paidShares[stablePositions.getValue(it)] }
                val fullyPaid = order.orderStatus == "交易成功"
                pieces.forEachIndexed { pieceIndex, (line, unit, item) ->
                    if ("${order.orderId}:$line" in _uiState.value.selectedItems) {
                        // Fulfilment/refund status is not evidence of zero payment. Keep
                        // the exported amount and let the mandatory financial review confirm it.
                        val amountPaid = paid[pieceIndex]
                        val merchandise =
                            if (fullyPaid && paid[pieceIndex] >= shipping[pieceIndex])
                                importSubtract(paid[pieceIndex], shipping[pieceIndex])
                            else item.price
                        val locations = _uiState.value.locations
                        val nodes = _uiState.value.storageNodes
                        val candidateNode =
                            item.requestedStoragePath?.let { path ->
                                nodes
                                    .filter { node ->
                                        StorageTree.pathFor(
                                            node.locationId,
                                            node.id,
                                            locations,
                                            nodes,
                                        ) == path
                                    }
                                    .singleOrNull()
                            }
                        val requestedRoom =
                            item.requestedRoomName?.let { name ->
                                locations.filter { it.name == name }.singleOrNull()?.id
                            }
                        val conflictingRooms =
                            item.requestedRoomName != null &&
                                candidateNode != null &&
                                candidateNode.locationId != requestedRoom
                        val exactNode = candidateNode.takeUnless { conflictingRooms }
                        val exactRoom =
                            if (conflictingRooms) null else exactNode?.locationId ?: requestedRoom
                        val unresolved =
                            conflictingRooms ||
                                (item.requestedStoragePath != null && exactNode == null) ||
                                (item.requestedRoomName != null && exactRoom == null)
                        importItems +=
                            ImportItemState(
                                originalItem = item,
                                name = cleanItemName(item.name),
                                brandId =
                                    _uiState.value.brandMappings[item.shopName]
                                        ?: matchBrand(item.shopName, brands)?.id
                                        ?: brands.firstOrNull { it.isUnsetBrand }?.id
                                        ?: 0L,
                                categoryId = matchCategory(item.parsedType, categories)?.id ?: 0L,
                                color = item.parsedColor ?: "",
                                size = item.parsedSize ?: "",
                                price = merchandise,
                                purchaseDate = item.actualPaidDate.orEmpty(),
                                styleSpec = item.styleSpec,
                                priceInput =
                                    if (
                                        !sourcePricesValid ||
                                            sourcePaid == null ||
                                            sourceShipping == null
                                    )
                                        ""
                                    else null,
                                shippingInput = if (sourceShipping == null) "" else null,
                                paidInput =
                                    if (sourcePaid == null || !sourcePricesValid) "" else null,
                                sourceKey =
                                    OrderImportSource.key(item, ordinals.getValue(line to unit)),
                                sourceGroupKey = OrderImportSource.group(item),
                                sourceOrdinal = ordinals.getValue(line to unit),
                                sourceProblem =
                                    if (
                                        OrderImportSource.publicUrl(item.productUrl) == null ||
                                            order.items.count {
                                                OrderImportSource.group(it) ==
                                                    OrderImportSource.group(item)
                                            } > 1
                                    )
                                        "来源单件身份待确认：请核对原商品与件号"
                                    else null,
                                paymentRole = detectPaymentRole(item.name),
                                shipping = shipping[pieceIndex],
                                paidAmount = amountPaid,
                                sourceLineIndex = line,
                                sourceUnitIndex = unit,
                                specifications =
                                    WardrobeSpecifications.decode(item.specificationsJson),
                                locationId = exactRoom,
                                storageNodeId = exactNode?.id,
                                locationMappingNote =
                                    if (unresolved)
                                        "来源位置未精确匹配，请手动选择；原文：${item.requestedStoragePath ?: item.requestedRoomName}"
                                    else null,
                            )
                    }
                }
            }
            val resolved =
                importItems.map { row ->
                    val receipt =
                        receipts
                            .filter { it.sourceKey == row.sourceKey }
                            .maxByOrNull { it.generation }
                    val changed =
                        receipts.any { r ->
                            r.orderId == row.originalItem.orderId &&
                                runCatching {
                                        OrderImportSource.snapshot(
                                            gson.fromJson(r.sourceSnapshot, TaobaoOrder::class.java)
                                        )
                                    }
                                    .getOrNull() !=
                                    _uiState.value.orders
                                        .firstOrNull { it.orderId == r.orderId }
                                        ?.let(OrderImportSource::snapshot)
                        }
                    if (receipt == null) {
                        if (changed) row.copy(sourceProblem = "此订单来源与已保存记录有变化，请查看来源后确认本条新资料")
                        else row
                    } else
                        row.copy(
                            committedItemId = receipt.itemId,
                            sourceProblem =
                                if (receipt.itemId == null) "此资料曾导入，原物品已删除；重新登记需要明确确认"
                                else "此资料已导入，可查看原物品",
                        )
                }
            val previous = _uiState.value.importItems
            val retained =
                resolved.map { fresh ->
                    val old = previous.firstOrNull { it.sourceKey == fresh.sourceKey }
                    if (old == null || fresh.committedItemId != null) fresh
                    else
                        old.copy(
                            originalItem = fresh.originalItem,
                            sourceLineIndex = fresh.sourceLineIndex,
                            sourceUnitIndex = fresh.sourceUnitIndex,
                            sourceProblem = old.sourceProblem ?: fresh.sourceProblem,
                        )
                } + previous.filter { old -> resolved.none { it.sourceKey == old.sourceKey } }
            val merged =
                retained.map { row ->
                    val old = previous.firstOrNull { it.sourceKey == row.sourceKey }
                    row.copy(
                        pairedWith =
                            old?.pairedWith
                                ?.let { previous.getOrNull(it)?.sourceKey }
                                ?.let { key ->
                                    retained.indexOfFirst { it.sourceKey == key }.takeIf { it >= 0 }
                                }
                    )
                }
            val allLinked = merged.isNotEmpty() && merged.all { it.committedItemId != null }
            _uiState.value =
                _uiState.value.copy(
                    currentStep = if (allLinked) ImportStep.RESULT else ImportStep.DETAIL,
                    importResult =
                        if (allLinked)
                            ImportResult(
                                linkedCount =
                                    merged.mapNotNull { it.committedItemId }.distinct().size
                            )
                        else _uiState.value.importResult,
                    importItems =
                        if (previous.isEmpty()) autoMatchDepositBalance(merged) else merged,
                    currentItemIndex = 0,
                )
            if (allLinked) viewModelScope.launch { refreshResultItems() }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = uiErrorText(e, "金额解析失败"))
        }
    }

    private fun persistedState(state: TaobaoImportUiState) =
        state.copy(
            brands = emptyList(),
            categories = emptyList(),
            locations = emptyList(),
            storageNodes = emptyList(),
            sessions = emptyList(),
            files = emptyList(),
            importItems = emptyList(),
            resultItems = emptyList(),
            linkedResultItems = emptyList(),
            isRestoring = false,
            draftSaving = false,
            draftError = null,
            errorMessage = null,
            isLoading = false,
        )

    private fun safeRow(row: ImportItemState) =
        row.copy(
            imageUrl = null,
            price = row.price.takeIf { it.isFinite() } ?: 0.0,
            shipping = row.shipping.takeIf { it.isFinite() } ?: 0.0,
            paidAmount = row.paidAmount.takeIf { it.isFinite() } ?: 0.0,
            manualBalance = row.manualBalance?.takeIf { it.isFinite() },
        )

    private fun persistenceKey(state: TaobaoImportUiState): String =
        gson.toJson(
            listOf(
                persistedState(state),
                state.files,
                state.importItems.map { listOf(safeRow(it), it.imageUrl) },
            )
        )

    suspend fun saveDraft(): Boolean =
        saveMutex.withLock {
            val snapshot = _uiState.value
            val id = snapshot.sessionId ?: return@withLock true
            if (snapshot.isRestoring || restoring) return@withLock false
            _uiState.value = _uiState.value.copy(draftSaving = true)
            try {
                val old = sessionRepository.session(id)
                require(old == null || old.revision == revision) { "此会话已在其他页面更新，请重新打开草稿" }
                val nextRevision = revision + 1
                val data =
                    com.google.gson.JsonObject().apply {
                        addProperty("version", 1)
                        add("state", gson.toJsonTree(persistedState(snapshot)))
                    }
                val oldImages = sessionRepository.rows(id).flatMap { it.imagePaths }
                val rows =
                    snapshot.importItems.mapIndexed { i, row ->
                        OrderImportRow(
                            "$id:$i",
                            id,
                            i,
                            row.sourceKey,
                            gson.toJson(safeRow(row)),
                            listOfNotNull(row.imageUrl),
                        )
                    }
                sessionRepository.save(
                    OrderImportSession(
                        id = id,
                        revision = nextRevision,
                        dataJson = data.toString(),
                        pendingOperationId = operationId,
                        state =
                            if (snapshot.currentStep == ImportStep.IMPORTING) "IMPORTING"
                            else if (
                                snapshot.importItems.isNotEmpty() &&
                                    snapshot.importItems.all { it.committedItemId != null }
                            )
                                "COMPLETE"
                            else "ACTIVE",
                        createdAt = old?.createdAt ?: System.currentTimeMillis(),
                    ),
                    rows,
                    snapshot.files,
                )
                revision = nextRevision
                ImageFileHelper.cleanupUnreferencedImages(
                    oldImages - rows.flatMap { it.imagePaths }.toSet()
                )
                _uiState.value = _uiState.value.copy(draftSaving = false, draftError = null)
                val leases = imageLeases.toList()
                imageLeases.removeAll(leases.toSet())
                leases.forEach { it.release() }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        draftSaving = false,
                        draftError = uiErrorText(failure, "草稿尚未保存，请重试并暂时保留此页"),
                    )
                false
            }
        }

    fun saveAndLeave(onSaved: () -> Unit) {
        viewModelScope.launch { if (saveDraft()) onSaved() }
    }

    fun restoreSession(id: String) {
        if (_uiState.value.currentStep == ImportStep.IMPORTING) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            restoring = true
            try {
                val session = sessionRepository.session(id) ?: error("导入草稿不存在，请返回重新选择")
                val root = JsonParser.parseString(session.dataJson).asJsonObject
                require(
                    session.version == 1 &&
                        root["version"].asInt == 1 &&
                        root.keySet() == setOf("version", "state")
                ) {
                    "导入草稿版本无法读取"
                }
                val state = gson.fromJson(root["state"], TaobaoImportUiState::class.java)
                val rows =
                    sessionRepository.rows(id).map { stored ->
                        val row = gson.fromJson(stored.dataJson, ImportItemState::class.java)
                        row.copy(
                            imageUrl = stored.imagePaths.firstOrNull(),
                            price =
                                row.priceInput?.let { it.toDoubleOrNull() ?: Double.NaN }
                                    ?: row.price,
                            shipping =
                                row.shippingInput?.let { it.toDoubleOrNull() ?: Double.NaN }
                                    ?: row.shipping,
                            paidAmount =
                                row.paidInput?.let { it.toDoubleOrNull() ?: Double.NaN }
                                    ?: row.paidAmount,
                        )
                    }
                receipts = sessionRepository.receipts()
                val commits = sessionRepository.commits(id)
                val byKey = receipts.associateBy { it.sourceKey }
                val recovered =
                    rows.map { row ->
                        val receipt = byKey[row.sourceKey]
                        when {
                            receipt?.itemId != null -> row.copy(committedItemId = receipt.itemId)
                            receipt != null && row.committedItemId != null ->
                                row.copy(
                                    committedItemId = null,
                                    sourceProblem = "此资料曾导入，原物品已删除；重新登记需要明确确认",
                                    financialConfirmed = false,
                                )
                            else -> row
                        }
                    }
                val last = session.pendingOperationId?.let { database.orderImportDao().commit(it) }
                val current = _uiState.value
                _uiState.value =
                    state.copy(
                        sessionId = id,
                        isRestoring = false,
                        importItems = recovered,
                        files = sessionRepository.files(id),
                        brands = current.brands,
                        categories = current.categories,
                        locations = current.locations,
                        storageNodes = current.storageNodes,
                        sessions = current.sessions,
                        currentStep =
                            if (last != null) ImportStep.RESULT
                            else if (state.currentStep == ImportStep.IMPORTING) ImportStep.REVIEW
                            else state.currentStep,
                        importResult =
                            last?.let {
                                ImportResult(
                                    it.itemIds.size,
                                    it.mergedCount,
                                    recovered.count { r -> r.committedItemId == null },
                                    it.itemIds.map(String::toLong),
                                    it.id,
                                    it.linkedItemIds.size,
                                )
                            } ?: state.importResult,
                    )
                revision = session.revision
                operationId = null
                refreshResultItems()
            } catch (failure: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isRestoring = false,
                        currentStep = ImportStep.ENTRY,
                        errorMessage = uiErrorText(failure, "草稿无法读取，可返回选择其他会话"),
                    )
            } finally {
                restoring = false
            }
        }
    }

    fun discardSession(id: String) {
        viewModelScope.launch {
            val images = sessionRepository.discard(id)
            if (_uiState.value.sessionId == id) {
                operationId = null
                revision = 0
                _uiState.value =
                    _uiState.value.copy(
                        sessionId = null,
                        orders = emptyList(),
                        files = emptyList(),
                        importItems = emptyList(),
                        fileLoaded = false,
                        currentStep = ImportStep.ENTRY,
                    )
            }
            ImageFileHelper.cleanupUnreferencedImages(images)
        }
    }

    fun resumeRemaining() {
        _uiState.value =
            _uiState.value.copy(
                currentStep = ImportStep.DETAIL,
                currentItemIndex =
                    _uiState.value.importItems
                        .indexOfFirst { it.committedItemId == null }
                        .coerceAtLeast(0),
            )
    }

    fun reviewImport() {
        setStep(ImportStep.REVIEW)
    }

    fun setBrandMapping(shop: String, id: Long) {
        _uiState.value =
            _uiState.value.copy(brandMappings = _uiState.value.brandMappings + (shop to id))
    }

    fun applyBatchAttributes(indices: Set<Int>, brandId: Long?, categoryId: Long?) {
        _uiState.value =
            _uiState.value.copy(
                importItems =
                    _uiState.value.importItems.mapIndexed { i, row ->
                        if (i in indices && row.committedItemId == null)
                            row.copy(
                                brandId = brandId ?: row.brandId,
                                categoryId = categoryId ?: row.categoryId,
                                financialConfirmed =
                                    if (categoryId != null && row.paymentRole != null) false
                                    else row.financialConfirmed,
                            )
                        else row
                    }
            )
    }

    fun confirmSourceIdentity(index: Int, deletedReimport: Boolean = false) {
        updateImportItem(index) {
            it.copy(
                identityConfirmed = true,
                allowDeletedReimport = deletedReimport,
                sourceProblem = null,
                financialConfirmed = false,
            )
        }
    }

    fun nextUnresolved(): Int? =
        _uiState.value.importItems.indices.firstOrNull {
            !isImportItemReady(it, _uiState.value.importItems) &&
                _uiState.value.importItems[it].committedItemId == null
        }

    private suspend fun refreshResultItems() {
        val id = _uiState.value.sessionId ?: return
        val commits = sessionRepository.commits(id)
        val ids = commits.flatMap { it.itemIds }.distinct().mapNotNull(String::toLongOrNull)
        val items = ids.mapNotNull { itemRepository.getItemById(it) }
        val linked =
            (commits.flatMap { it.linkedItemIds }.mapNotNull(String::toLongOrNull) +
                    _uiState.value.importItems.mapNotNull { it.committedItemId })
                .distinct() - ids.toSet()
        _uiState.value =
            _uiState.value.copy(
                resultItems = items,
                resultItemIds = ids,
                deletedResultItemIds = ids - items.map { it.id }.toSet(),
                linkedResultItems = linked.mapNotNull { itemRepository.getItemById(it) },
            )
    }

    fun savedSourceOrders(orderId: String): List<TaobaoOrder> =
        receipts
            .filter { it.orderId == orderId }
            .mapNotNull {
                runCatching { gson.fromJson(it.sourceSnapshot, TaobaoOrder::class.java) }
                    .getOrNull()
            }
            .distinctBy(OrderImportSource::snapshot)

    suspend fun findExistingItems(query: String): List<Item> =
        itemRepository.searchItemsByName(query).first().take(100)

    fun linkExisting(index: Int, itemId: Long) {
        viewModelScope.launch {
            val row = _uiState.value.importItems.getOrNull(index) ?: return@launch
            try {
                require(row.committedItemId == null && row.pairedWith == null) { "请先解除配对，再关联已有物品" }
                check(saveDraft()) { "草稿尚未保存" }
                val id = UUID.randomUUID().toString()
                database.withTransaction {
                    require(database.orderImportDao().receipt(row.sourceKey) == null) {
                        "此来源已登记，请查看原物品"
                    }
                    require(itemRepository.getItemById(itemId) != null) { "原物品已删除，请重新选择" }
                    val order =
                        _uiState.value.orders.first { it.orderId == row.originalItem.orderId }
                    database
                        .orderImportDao()
                        .insertCommit(
                            OrderImportCommit(
                                id,
                                _uiState.value.sessionId,
                                revision,
                                OrderImportSource.hash(row.sourceKey),
                                emptyList(),
                                0,
                                listOf(itemId.toString()),
                            )
                        )
                    database
                        .orderImportDao()
                        .insertReceipts(
                            listOf(
                                OrderImportReceipt(
                                    sourceKey = row.sourceKey,
                                    orderId = order.orderId,
                                    groupKey = row.sourceGroupKey,
                                    unitOrdinal = row.sourceOrdinal,
                                    sourceSnapshot = gson.toJson(order),
                                    commitId = id,
                                    itemId = itemId,
                                    paidCents =
                                        runCatching { importCents(row.paidAmount) }.getOrDefault(0),
                                    shippingCents =
                                        runCatching { importCents(row.shipping) }.getOrDefault(0),
                                )
                            )
                        )
                    database
                        .orderImportDao()
                        .insertSources(
                            listOf(
                                ItemSourceRecord(
                                    itemId = itemId,
                                    kind = "ORDER_IMPORT",
                                    publicUrl =
                                        OrderImportSource.publicUrl(row.originalItem.productUrl),
                                    snapshotJson = gson.toJson(row.originalItem),
                                )
                            )
                        )
                }
                receipts = sessionRepository.receipts()
                _uiState.value =
                    _uiState.value.copy(
                        importItems =
                            _uiState.value.importItems.mapIndexed { i, source ->
                                if (i == index) source.copy(committedItemId = itemId) else source
                            },
                        importResult = ImportResult(linkedCount = 1, commitId = id),
                        currentStep = ImportStep.RESULT,
                    )
                saveDraft()
                refreshResultItems()
            } catch (failure: Exception) {
                showError(uiErrorText(failure, "原物品关联未完成，请重试"))
            }
        }
    }

    fun showAdded() {
        viewModelScope.launch {
            refreshResultItems()
            setStep(ImportStep.ADDED)
        }
    }

    fun moveImported(
        ids: Set<Long>,
        roomId: Long,
        nodeId: Long?,
        expected: Map<Long, Long>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                database.withTransaction {
                    val allowed = _uiState.value.resultItems.map { it.id }.toSet()
                    require(ids.isNotEmpty() && ids.all { it in allowed }) { "请重新选择本次物品" }
                    val current =
                        ids.map { itemRepository.getItemById(it) ?: error("所选物品已删除，请重新核对") }
                    require(
                        current.all { it.status.supportsStorage && expected[it.id] == it.updatedAt }
                    ) {
                        "所选物品的状态或位置已变化，请重新核对后再移动"
                    }
                    current.forEach {
                        itemRepository.updateItem(
                            it.copy(locationId = roomId, storageNodeId = nodeId)
                        )
                    }
                }
                refreshResultItems()
                onDone()
            } catch (failure: Exception) {
                showError(uiErrorText(failure, "收纳未保存，请重新核对物品与位置"))
            }
        }
    }

    private fun guessCategoryGroup(parsedType: String): String {
        val accessoryKeywords =
            listOf(
                "头饰",
                "蝴蝶结",
                "帽子",
                "KC",
                "发带",
                "发夹",
                "包",
                "鞋",
                "袜",
                "手套",
                "项链",
                "耳环",
                "戒指",
                "胸针",
                "腰链",
            )
        return if (accessoryKeywords.any { parsedType.contains(it, ignoreCase = true) }) "ACCESSORY"
        else "CLOTHING"
    }

    fun goBackToSelect() {
        if (_uiState.value.currentStep == ImportStep.IMPORTING) return
        _uiState.value = _uiState.value.copy(currentStep = ImportStep.ENTRY)
    }

    fun updateImportItem(index: Int, update: (ImportItemState) -> ImportItemState) {
        if (_uiState.value.currentStep == ImportStep.IMPORTING) return
        val items = _uiState.value.importItems.toMutableList()
        if (index in items.indices && items[index].committedItemId == null) {
            val old = items[index]
            val changed = update(old)
            val moneyChanged =
                old.price != changed.price ||
                    old.shipping != changed.shipping ||
                    old.paidAmount != changed.paidAmount ||
                    old.purchaseDate != changed.purchaseDate ||
                    old.manualBalance != changed.manualBalance ||
                    old.paymentRole != changed.paymentRole ||
                    old.pairedWith != changed.pairedWith ||
                    old.priceInput != changed.priceInput ||
                    old.shippingInput != changed.shippingInput ||
                    old.paidInput != changed.paidInput ||
                    old.size != changed.size ||
                    old.color != changed.color ||
                    old.categoryId != changed.categoryId
            items[index] = if (moneyChanged) changed.copy(financialConfirmed = false) else changed
            if (moneyChanged)
                changed.pairedWith?.let { partner ->
                    if (partner in items.indices)
                        items[partner] = items[partner].copy(financialConfirmed = false)
                }
            _uiState.value = _uiState.value.copy(importItems = items)
            if (old.imageUrl != changed.imageUrl) {
                imageRequests[index] = (imageRequests[index] ?: 0L) + 1L
                releaseUnusedDraftImages()
            }
        }
    }

    fun setCurrentItemIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentItemIndex = index)
    }

    fun onLocalImageSelected(itemIndex: Int, uri: Uri?) {
        if (
            uri == null ||
                _uiState.value.currentStep != ImportStep.DETAIL ||
                itemIndex !in _uiState.value.importItems.indices
        )
            return
        val generation = imageGeneration
        val request = (imageRequests[itemIndex] ?: 0L) + 1L
        imageRequests[itemIndex] = request
        viewModelScope.launch {
            try {
                val localPath = ImageFileHelper.copyToInternalStorage(getApplication(), uri)
                if (
                    generation != imageGeneration ||
                        imageRequests[itemIndex] != request ||
                        _uiState.value.currentStep != ImportStep.DETAIL
                ) {
                    ImageFileHelper.cleanupUnreferencedImages(listOf(localPath))
                    return@launch
                }
                imageLeases +=
                    ImageFileHelper.pinImageReferences(getApplication(), listOf(localPath))
                draftImages += localPath
                updateImportItem(itemIndex) { it.copy(imageUrl = localPath) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "读取图片失败，请重新选择")
            }
        }
    }

    /** 执行批量导入 — 使用 ImportItemState 中的预匹配结果 */
    fun executeImport() {
        if (_uiState.value.currentStep == ImportStep.IMPORTING) return
        val allItems = _uiState.value.importItems
        val validIndices = allItems.indices.filter { isImportItemReady(it, allItems) }.toSet()
        if (validIndices.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先完善品牌、类型并核对商品金额、邮费和实付")
            return
        }
        try {
            val byOrder = validIndices.map { allItems[it] }.groupBy { it.originalItem.orderId }
            byOrder.forEach { (orderId, rows) ->
                val original = _uiState.value.orders.first { it.orderId == orderId }
                require(
                    OrderImportSource.amount(original.rawShipping, original.shipping)?.let {
                        importCents(importSum(rows.map { it.shipping })) <= importCents(it)
                    } != false
                ) {
                    "订单 $orderId 分摊邮费超过源订单邮费，请核对，不能重复计费"
                }
                require(
                    OrderImportSource.amount(original.rawPaid, original.totalPaid)?.let {
                        importCents(importSum(rows.map { it.paidAmount })) <= importCents(it)
                    } != false
                ) {
                    "订单 $orderId 导入实付超过源订单实付，请核对分配"
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = uiErrorText(e))
            return
        }
        // A picker still copying must not attach its result to the committed snapshot.
        imageGeneration++
        imageRequests.clear()
        operationId = operationId ?: UUID.randomUUID().toString()
        _uiState.value = _uiState.value.copy(currentStep = ImportStep.IMPORTING)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                check(saveDraft()) { "草稿尚未保存，请重试" }
                val commitId = operationId!!
                val previous = database.orderImportDao().commit(commitId)
                if (previous != null) {
                    _uiState.value =
                        _uiState.value.copy(
                            currentStep = ImportStep.RESULT,
                            importResult =
                                ImportResult(
                                    previous.itemIds.size,
                                    previous.mergedCount,
                                    0,
                                    previous.itemIds.map(String::toLong),
                                    previous.id,
                                ),
                        )
                    refreshResultItems()
                    return@launch
                }
                val addedIds = mutableListOf<Long>()
                val committedRows = mutableMapOf<Int, Long>()

                val processed = mutableSetOf<Int>()
                var importedCount = 0
                var mergedCount = 0
                database.withTransaction {
                    val dao = database.orderImportDao()
                    require(
                        dao.session(requireNotNull(_uiState.value.sessionId))?.let {
                            it.revision == revision && it.pendingOperationId == commitId
                        } == true
                    ) {
                        "导入会话已有更新，请重新打开后确认"
                    }
                    val oldReceipts = dao.receipts().filter { it.itemId != null }
                    validIndices
                        .map { allItems[it] }
                        .groupBy { it.originalItem.orderId }
                        .forEach { (id, rows) ->
                            val order = _uiState.value.orders.first { it.orderId == id }
                            val prior = oldReceipts.filter { it.orderId == id }
                            require(
                                OrderImportSource.amount(order.rawPaid, order.totalPaid)?.let {
                                    prior.sumOf { it.paidCents } +
                                        rows.sumOf { importCents(it.paidAmount) } <= importCents(it)
                                } != false
                            ) {
                                "订单累计实付超过来源，请核对已导入份额"
                            }
                            require(
                                OrderImportSource.amount(order.rawShipping, order.shipping)?.let {
                                    prior.sumOf { it.shippingCents } +
                                        rows.sumOf { importCents(it.shipping) } <= importCents(it)
                                } != false
                            ) {
                                "订单累计运费超过来源，请核对已导入份额"
                            }
                        }
                    val commit =
                        OrderImportCommit(
                            commitId,
                            _uiState.value.sessionId,
                            revision,
                            OrderImportSource.hash(gson.toJson(allItems.map(::safeRow))),
                            emptyList(),
                            0,
                        )
                    dao.insertCommit(commit)
                    val createdBrands = mutableMapOf<String, Long>()
                    val storageTargets = mutableMapOf<Long, Pair<Long, Long?>>()
                    for (index in allItems.indices) {
                        if (index !in validIndices || index in processed) continue
                        val row = allItems[index]
                        val pairIndex =
                            row.pairedWith?.takeIf {
                                it in validIndices && allItems[it].pairedWith == index
                            }
                        val pair = pairIndex?.let { allItems[it] }
                        val main =
                            if (row.paymentRole == PaymentRole.BALANCE && pair != null) pair
                            else row
                        val sourceRows =
                            if (pair != null) listOf(main, if (main == row) pair else row)
                            else listOf(row)
                        val stages: List<Triple<String, Double, Long?>>
                        val shipping = importSum(sourceRows.map { it.shipping })
                        val total: Double
                        val type: PriceType
                        if (pair != null) {
                            type = PriceType.DEPOSIT_BALANCE
                            val deposit = sourceRows.first { it.paymentRole == PaymentRole.DEPOSIT }
                            val balance = sourceRows.first { it.paymentRole == PaymentRole.BALANCE }
                            stages =
                                listOf(
                                    Triple(
                                        "定金",
                                        importSum(listOf(deposit.price, deposit.shipping)),
                                        null,
                                    ),
                                    Triple(
                                        "尾款",
                                        importSum(listOf(balance.price, balance.shipping)),
                                        null,
                                    ),
                                )
                            total = importSum(stages.map { it.second })
                        } else if (main.paymentRole == PaymentRole.DEPOSIT) {
                            type = PriceType.DEPOSIT_BALANCE
                            stages =
                                listOf(
                                    Triple(
                                        "定金",
                                        importSum(listOf(main.price, main.shipping)),
                                        null,
                                    ),
                                    Triple("尾款", main.manualBalance!!, main.balanceDueDate),
                                )
                            total = importSum(stages.map { it.second })
                        } else {
                            type = PriceType.FULL
                            total = importSum(listOf(main.price, main.shipping))
                            stages = listOf(Triple("全款", total, null))
                        }
                        val metadata =
                            sourceRows.joinToString("\n") { source ->
                                val originalOrder =
                                    _uiState.value.orders.first {
                                        it.orderId == source.originalItem.orderId
                                    }
                                "淘宝订单：${source.originalItem.orderId}；源状态：${originalOrder.orderStatus}；源订单实付：${originalOrder.totalPaid}；原行：${source.sourceLineIndex + 1}；件号：${source.sourceUnitIndex + 1}/${source.originalItem.quantity}；原商品金额：${source.originalItem.price}；分摊邮费：${source.shipping}；核对实付：${source.paidAmount}"
                            }
                        val semantic =
                            _uiState.value.categories
                                .firstOrNull { it.id == main.categoryId }
                                ?.categorySemantic
                        val specifications =
                            when (semantic?.family) {
                                WardrobeFamily.FOOTWEAR ->
                                    main.specifications.copy(
                                        footwear =
                                            main.specifications.footwear.let {
                                                if (it.sizeText.isBlank())
                                                    it.copy(sizeText = main.size)
                                                else it
                                            }
                                    )
                                WardrobeFamily.HOSIERY ->
                                    main.specifications.copy(
                                        hosiery =
                                            main.specifications.hosiery.let {
                                                if (it.sizeText.isBlank())
                                                    it.copy(sizeText = main.size)
                                                else it
                                            }
                                    )
                                else -> main.specifications
                            }
                        sourceRows.forEach { source ->
                            source.imageUrl?.let { path ->
                                require(
                                    ImageFileHelper.isOwnedReadableProductImage(
                                        getApplication(),
                                        path,
                                    )
                                ) {
                                    "本件图片无法读取，请重新选择图片或移除后继续"
                                }
                            }
                            require(source.sourceKey.isNotBlank()) { "来源身份缺失，请重新核对文件" }
                            val old = dao.receipt(source.sourceKey)
                            require(
                                old == null || old.itemId == null && source.allowDeletedReimport
                            ) {
                                "此资料已经导入或曾被删除，请返回来源核对"
                            }
                        }
                        val resolvedBrand =
                            if (
                                main.originalItem.shopName in _uiState.value.newBrandNames &&
                                    main.originalItem.shopName !in _uiState.value.brandMappings &&
                                    main.brandId ==
                                        _uiState.value.brands.firstOrNull { it.isUnsetBrand }?.id
                            )
                                createdBrands.getOrPut(main.originalItem.shopName) {
                                    brandRepository.insertBrand(
                                        Brand(name = main.originalItem.shopName, logoUrl = "")
                                    )
                                }
                            else main.brandId
                        val itemId =
                            itemRepository.insertItem(
                                Item(
                                    name = main.name,
                                    brandId = resolvedBrand,
                                    categoryId = main.categoryId,
                                    colors =
                                        main.color.takeIf { it.isNotBlank() }?.let(::listOf)
                                            ?: emptyList(),
                                    size = main.size.ifBlank { null },
                                    imageUrls = sourceRows.mapNotNull { it.imageUrl }.distinct(),
                                    status = ItemStatus.PENDING_PAYMENT,
                                    description = metadata,
                                    importToken = "order:$commitId:${main.sourceKey}",
                                    specificationsJson = specifications.encode(),
                                    composition = main.composition,
                                )
                            )
                        addedIds += itemId
                        committedRows[index] = itemId
                        pairIndex?.let { committedRows[it] = itemId }
                        dao.insertReceipts(
                            sourceRows.map { source ->
                                val original =
                                    _uiState.value.orders.first {
                                        it.orderId == source.originalItem.orderId
                                    }
                                OrderImportReceipt(
                                    sourceKey = source.sourceKey,
                                    generation =
                                        (dao.receipt(source.sourceKey)?.generation ?: 0) + 1,
                                    orderId = source.originalItem.orderId,
                                    groupKey = source.sourceGroupKey,
                                    unitOrdinal = source.sourceOrdinal,
                                    sourceSnapshot = gson.toJson(original),
                                    commitId = commitId,
                                    itemId = itemId,
                                    paidCents = importCents(source.paidAmount),
                                    shippingCents = importCents(source.shipping),
                                )
                            }
                        )
                        dao.insertSources(
                            sourceRows.map { source ->
                                ItemSourceRecord(
                                    itemId = itemId,
                                    kind = "ORDER_IMPORT",
                                    publicUrl =
                                        OrderImportSource.publicUrl(source.originalItem.productUrl),
                                    snapshotJson = gson.toJson(source.originalItem),
                                    appliedFields = listOf("purchaseEvidence"),
                                )
                            }
                        )
                        main.locationId?.let { storageTargets[itemId] = it to main.storageNodeId }
                        val priceId =
                            priceRepository.insertPrice(
                                Price(
                                    itemId = itemId,
                                    type = type,
                                    totalPrice = total,
                                    shipping = shipping,
                                    deposit =
                                        if (type == PriceType.DEPOSIT_BALANCE) stages[0].second
                                        else null,
                                    balance =
                                        if (type == PriceType.DEPOSIT_BALANCE) stages[1].second
                                        else null,
                                )
                            )
                        stages.forEachIndexed { stageIndex, (name, amount, dueDate) ->
                            val stageId =
                                database
                                    .orderDao()
                                    .insertStage(
                                        PaymentStage(
                                            priceId = priceId,
                                            name = name,
                                            amount = amount,
                                            dueDate = dueDate,
                                        )
                                    )
                            val source = sourceRows.getOrNull(stageIndex)
                            if (source != null && source.paidAmount > 0) {
                                val paidDate =
                                    parseImportDate(source.purchaseDate) ?: error("请核对实际付款日期")
                                database
                                    .paymentDao()
                                    .insertPayment(
                                        Payment(
                                            priceId = priceId,
                                            stageId = stageId,
                                            amount = source.paidAmount,
                                            dueDate = paidDate,
                                            isPaid = true,
                                            paidDate = paidDate,
                                        )
                                    )
                            }
                        }
                        processed += index
                        pairIndex?.let {
                            processed += it
                            mergedCount++
                        }
                        importedCount++
                    }
                    dao.updateCommit(
                        commit.copy(
                            itemIds = addedIds.map(Long::toString),
                            mergedCount = mergedCount,
                        )
                    )
                    database.orderDao().refreshStatuses()
                    database
                        .itemDao()
                        .getItemsForStorageMove(storageTargets.keys.toList())
                        .filter { it.status.supportsStorage }
                        .forEach { item ->
                            val (room, node) = storageTargets.getValue(item.id)
                            itemRepository.updateItem(
                                item.copy(locationId = room, storageNodeId = node)
                            )
                        }
                }
                receipts = sessionRepository.receipts()
                _uiState.value =
                    _uiState.value.copy(
                        importItems =
                            _uiState.value.importItems.mapIndexed { i, row ->
                                committedRows[i]?.let { row.copy(committedItemId = it) } ?: row
                            }
                    )
                operationId = null

                // All external reminders are reconciled only after the complete import commits.
                runCatching { orderRepository.reconcileReminders() }
                    .onFailure {
                        android.util.Log.w(
                            "TaobaoImport",
                            "Import committed; reminders will retry",
                            it,
                        )
                    }
                _uiState.value =
                    _uiState.value.copy(
                        currentStep = ImportStep.RESULT,
                        importResult =
                            ImportResult(
                                importedCount,
                                mergedCount,
                                allItems.count { it.committedItemId == null } - processed.size,
                                addedIds,
                                commitId,
                            ),
                    )
                saveDraft()
                refreshResultItems()
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        currentStep = ImportStep.DETAIL,
                        errorMessage = uiErrorText(e, "导入未完成，请核对资料后重试"),
                    )
            }
        }
    }

    // --- 定金尾款匹配逻辑 ---

    private fun detectPaymentRole(name: String): PaymentRole? {
        val hasDeposit = name.contains("定金") || name.contains("定-金")
        val hasBalance = name.contains("尾款")
        // 同时含"尾款"和"定金"时，根据上下文判断：
        // "尾款*需有定金" / "尾款（需要有定金）" → 这是尾款页面
        // "定金页面需补尾款" / "定金*需补尾款" → 这是定金页面
        if (hasDeposit && hasBalance) {
            val depIdx = Regex("定-?金").find(name)?.range?.first ?: Int.MAX_VALUE
            val balIdx = name.indexOf("尾款")
            // 谁先出现谁是主角色，但"需有/需补"模式优先
            if (name.contains(Regex("尾款.*需有.*定金|尾款.*需要有.*定金"))) return PaymentRole.BALANCE
            if (name.contains(Regex("定-?金.*需补.*尾款|定-?金.*页面.*尾款"))) return PaymentRole.DEPOSIT
            return if (depIdx < balIdx) PaymentRole.DEPOSIT else PaymentRole.BALANCE
        }
        if (hasDeposit) return PaymentRole.DEPOSIT
        if (hasBalance) return PaymentRole.BALANCE
        return null
    }

    /** 自动匹配定金尾款配对 多维度评分: 核心名称匹配 > URL匹配 > 店铺匹配 > 关键词重叠 核心名称 = 去除所有噪音后的产品系列名 */
    private fun autoMatchDepositBalance(items: List<ImportItemState>): List<ImportItemState> {
        val result = items.toMutableList()
        val used = mutableSetOf<Int>()

        val deposits = items.indices.filter { items[it].paymentRole == PaymentRole.DEPOSIT }
        val balances = items.indices.filter { items[it].paymentRole == PaymentRole.BALANCE }

        // 预计算每个项的核心名称和关键词
        val coreNames = items.map { extractCoreName(it.originalItem.name) }
        val keywords = coreNames.map { extractKeywords(it) }

        for (dIdx in deposits) {
            if (dIdx in used) continue
            val dep = result[dIdx]
            val urlDep = dep.originalItem.productUrl
            val shopDep = dep.originalItem.shopName

            var bestMatch: Int? = null
            var bestScore = 0

            for (bIdx in balances) {
                if (bIdx in used) continue
                val bal = result[bIdx]
                if (dep.size.isNotBlank() && bal.size.isNotBlank() && dep.size != bal.size) continue
                if (dep.color.isNotBlank() && bal.color.isNotBlank() && dep.color != bal.color)
                    continue
                if (
                    dep.originalItem.parsedType != null &&
                        bal.originalItem.parsedType != null &&
                        dep.originalItem.parsedType != bal.originalItem.parsedType
                )
                    continue
                val urlBal = bal.originalItem.productUrl
                val shopBal = bal.originalItem.shopName

                var score = 0

                // 核心名称完全相同 → 强匹配
                if (coreNames[dIdx].isNotBlank() && coreNames[dIdx] == coreNames[bIdx]) {
                    score += 10
                }
                // 核心名称包含关系 → 中匹配
                else if (coreNames[dIdx].isNotBlank() && coreNames[bIdx].isNotBlank()) {
                    val shorter =
                        if (coreNames[dIdx].length <= coreNames[bIdx].length) coreNames[dIdx]
                        else coreNames[bIdx]
                    val longer =
                        if (coreNames[dIdx].length > coreNames[bIdx].length) coreNames[dIdx]
                        else coreNames[bIdx]
                    if (shorter.length >= 3 && longer.contains(shorter)) score += 7
                }

                // 关键词重叠匹配 — 处理名称结构不同但产品相同的情况
                if (score < 7 && keywords[dIdx].isNotEmpty() && keywords[bIdx].isNotEmpty()) {
                    val overlap = keywords[dIdx].intersect(keywords[bIdx])
                    val smaller = minOf(keywords[dIdx].size, keywords[bIdx].size)
                    if (
                        smaller > 0 && overlap.size.toFloat() / smaller >= 0.6f && overlap.size >= 2
                    ) {
                        score += 6
                    }
                }

                // productUrl 相同且非空 → 强匹配
                if (urlDep.isNotBlank() && urlDep == urlBal) score += 8

                // 店铺匹配（支持同品牌不同店铺名）
                if (shopDep.isNotBlank() && shopBal.isNotBlank()) {
                    if (shopDep == shopBal) {
                        score += 3
                    } else if (shopsAreSameBrand(shopDep, shopBal)) {
                        score += 2
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = bIdx
                }
            }

            // 至少需要名称匹配或 URL 匹配（score >= 8）
            if (bestMatch != null && bestScore >= 8) {
                result[dIdx] = result[dIdx].copy(pairedWith = bestMatch)
                result[bestMatch] = result[bestMatch].copy(pairedWith = dIdx)
                used.add(dIdx)
                used.add(bestMatch)
            }
        }
        return result
    }

    /** 从原始商品名中提取核心产品名（系列名），去除所有噪音 */
    private fun extractCoreName(name: String): String {
        return name
            .replace(Regex("【[^】]*】"), "") // 【预约期包邮】【定金】【尾款】
            .replace(Regex("＜[^＞]*＞"), "") // ＜不死锦鲤·横公鱼＞
            .replace(Regex("[《》]"), "") // 《旋转虎虎》→ 旋转虎虎（保留内容）
            .replace(Regex("（[^）]*）"), "") // （定金）（定-金）（长短款）
            .replace(Regex("\\([^)]*\\)"), "") // (208+800/183+730)
            .replace(Regex("定-?金"), "")
            .replace(Regex("尾款"), "")
            .replace(Regex("意向"), "")
            .replace(Regex("正式"), "")
            .replace(Regex("预约"), "")
            .replace(Regex("CP先行"), "")
            .replace(Regex("加购小物"), "")
            .replace(Regex("\\d+团"), "") // 2团
            .replace(Regex("\\d+批"), "")
            .replace(Regex("[一二三四五六七八九十]+团"), "") // 一团
            .replace(Regex("[一二三四五六七八九十]+批"), "") // 六批
            .replace(Regex("需有[^*｜|]*"), "") // 需有定金 / 需有一团定金
            .replace(Regex("需补[^*｜|]*"), "") // 需补尾款
            .replace(Regex("需要有[^*｜|]*"), "")
            .replace(Regex("路德\\s*"), "") // "路德 金鳞流光" → "金鳞流光"
            .replace(Regex("\\d+\\.\\d+日[^*｜|]*"), "") // 12.5日截
            .replace(Regex("\\d+月\\d+日[^*｜|]*"), "") // 1月2日-2月2日
            .replace(Regex("春季新款|秋冬纯棉|慢团|成团再贩|2024再贩|页面"), "")
            .replace(Regex("[｜|*·]"), " ") // 分隔符转空格
            .replace(Regex("\\s+"), " ") // 合并空格
            .trim()
            .trimStart('-', ' ')
            .trimEnd('-', ' ')
    }

    /** 从核心名称中提取有意义的关键词（用于模糊匹配） 过滤掉通用词，保留产品系列名、款式名等 */
    private fun extractKeywords(coreName: String): Set<String> {
        val stopWords =
            setOf(
                "原创",
                "lolita",
                "Lolita",
                "LOLITA",
                "洋装",
                "连衣裙",
                "套装",
                "复古",
                "优雅",
                "华丽",
                "刺绣",
                "新款",
                "春季",
                "秋冬",
                "纯棉",
                "页面",
                "时间",
                "开始",
                "截止",
            )
        return coreName
            .split(Regex("[\\s+\\-]"))
            .map { it.trim() }
            .filter { it.length >= 2 && it.lowercase() !in stopWords.map { s -> s.lowercase() } }
            .toSet()
    }

    /** 判断两个店铺名是否属于同一品牌（处理同品牌不同店铺名的情况） */
    private fun shopsAreSameBrand(shop1: String, shop2: String): Boolean {
        // 提取品牌关键词进行比较
        val keywords1 = extractBrandKeywords(shop1)
        val keywords2 = extractBrandKeywords(shop2)
        if (keywords1.isEmpty() || keywords2.isEmpty()) return false
        // 有交集即认为同品牌
        return keywords1.intersect(keywords2).isNotEmpty()
    }

    private fun extractBrandKeywords(shopName: String): Set<String> {
        val keywords = mutableSetOf<String>()
        // 提取英文单词（至少2个字符）
        Regex("[A-Za-z]{2,}").findAll(shopName).forEach { keywords.add(it.value.lowercase()) }
        // 提取中文品牌名片段（2-4个连续汉字）
        Regex("[\u4e00-\u9fff]{2,4}").findAll(shopName).forEach {
            val word = it.value
            // 过滤通用词
            if (word !in listOf("原创", "独立", "设计师", "品牌", "洋服", "洋装", "工作室", "新款")) {
                keywords.add(word)
            }
        }
        return keywords
    }

    /** 手动配对两个项 */
    fun manualPair(indexA: Int, indexB: Int) {
        val items = _uiState.value.importItems.toMutableList()
        if (
            indexA !in items.indices ||
                indexB !in items.indices ||
                _uiState.value.currentStep == ImportStep.IMPORTING
        )
            return
        if (items[indexA].committedItemId != null || items[indexB].committedItemId != null) return
        if (
            indexA == indexB ||
                setOf(items[indexA].paymentRole, items[indexB].paymentRole) !=
                    setOf(PaymentRole.DEPOSIT, PaymentRole.BALANCE)
        )
            return
        if (items[indexA].pairedWith == indexB && items[indexB].pairedWith == indexA) return
        // 先解除旧配对
        items[indexA].pairedWith?.let { old ->
            if (old in items.indices)
                items[old] = items[old].copy(pairedWith = null, financialConfirmed = false)
        }
        items[indexB].pairedWith?.let { old ->
            if (old in items.indices)
                items[old] = items[old].copy(pairedWith = null, financialConfirmed = false)
        }
        items[indexA] = items[indexA].copy(pairedWith = indexB, financialConfirmed = false)
        items[indexB] = items[indexB].copy(pairedWith = indexA, financialConfirmed = false)
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /** 手动设置订单的定金/尾款角色 */
    fun setPaymentRole(index: Int, role: PaymentRole?) {
        val items = _uiState.value.importItems.toMutableList()
        if (
            index !in items.indices ||
                items[index].committedItemId != null ||
                _uiState.value.currentStep == ImportStep.IMPORTING
        )
            return
        val oldRole = items[index].paymentRole
        if (oldRole == role) return
        // 角色变更时先解除旧配对
        if (oldRole != role && items[index].pairedWith != null) {
            val pairedIdx = items[index].pairedWith!!
            if (pairedIdx in items.indices) {
                items[pairedIdx] =
                    items[pairedIdx].copy(pairedWith = null, financialConfirmed = false)
            }
            items[index] = items[index].copy(pairedWith = null, financialConfirmed = false)
        }
        items[index] = items[index].copy(paymentRole = role, financialConfirmed = false)
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /** 取消配对 */
    fun unpair(index: Int) {
        val items = _uiState.value.importItems.toMutableList()
        if (
            index !in items.indices ||
                items[index].committedItemId != null ||
                _uiState.value.currentStep == ImportStep.IMPORTING
        )
            return
        val pairedIdx = items[index].pairedWith ?: return
        items[index] = items[index].copy(pairedWith = null, financialConfirmed = false)
        if (pairedIdx in items.indices) {
            items[pairedIdx] = items[pairedIdx].copy(pairedWith = null, financialConfirmed = false)
        }
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /** 在导入流程中新增类型 */
    fun addCategory(name: String, group: CategoryGroup, semantic: CategorySemantic) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                require(semantic != CategorySemantic.UNCONFIRMED) { "请选择物品类型" }
                categoryRepository.insertCategory(
                    Category(
                        name = name,
                        group = group,
                        categorySemantic = semantic,
                        defaultWearRole = semantic.defaultWearRole,
                    )
                )
                val categories = categoryRepository.getAllCategories().first()
                _uiState.value = _uiState.value.copy(categories = categories)
            } catch (_: Exception) {}
        }
    }

    // --- 智能匹配逻辑 ---

    private fun matchBrand(shopName: String, brands: List<Brand>): Brand? {
        val regular = brands.filter { it.systemKey == null }
        val matched =
            TaobaoProductParser.matchBrand(regular.map { it.name }, listOf(shopName)) ?: return null
        return regular.singleOrNull { it.name == matched }
    }

    private fun matchCategory(parsedType: String?, categories: List<Category>): Category? {
        if (parsedType == null) return null
        // 精确匹配
        categories
            .firstOrNull { it.name.equals(parsedType, ignoreCase = true) }
            ?.let {
                return it
            }
        // 关键词映射（精确匹配优先）
        val typeMap =
            mapOf(
                "OP" to listOf("OP", "开襟OP", "堆褶OP", "堆褶开襟OP"),
                "SK" to listOf("SK", "拼色SK", "半裙", "格裙", "长裙", "罩裙"),
                "JSK" to listOf("JSK"),
                "衬衫" to listOf("衬衫"),
                "斗篷" to listOf("斗篷", "罩衫斗篷", "罩衫"),
                "外套" to listOf("外套", "长外套", "大衣"),
                "帽子" to listOf("帽子", "贝雷帽"),
                "其他头饰" to listOf("头饰", "蝴蝶结头饰", "KC", "发箍", "发带", "发夹", "边夹", "头纱"),
                "腰封" to listOf("腰封", "拖尾腰封", "印花钢骨腰封"),
                "手袖" to listOf("手袖", "接袖", "袖子"),
                "包" to listOf("包", "包包"),
                "短裤" to listOf("短裤", "南瓜裤"),
                "胸针" to listOf("胸针"),
                "项链" to listOf("项链", "吊坠"),
                "耳环" to listOf("耳环"),
                "戒指" to listOf("戒指"),
                "上衣" to listOf("上衣", "胸衣", "蝙蝠胸衣"),
            )
        // 先精确匹配关键词
        for ((categoryName, keywords) in typeMap) {
            if (keywords.any { parsedType.equals(it, ignoreCase = true) }) {
                return categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
            }
        }
        // 再包含匹配兜底：按关键词长度处理，避免 JSK 被先匹配成 SK。
        val mappedKeywords =
            typeMap
                .flatMap { (categoryName, keywords) ->
                    keywords.map { keyword -> keyword to categoryName }
                }
                .sortedByDescending { it.first.length }
        for ((keyword, categoryName) in mappedKeywords) {
            if (parsedType.contains(keyword, ignoreCase = true)) {
                // A missing JSK category must be prepared as JSK, never silently
                // downgraded to an existing SK category through a shorter keyword.
                return categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
            }
        }
        return null
    }

    private fun cleanItemName(name: String): String {
        return name
            .replace(Regex("【[^】]*】"), "") // 去除【预约期包邮】等
            .replace(Regex("＜[^＞]*＞"), "") // 去除＜不死锦鲤·横公鱼＞等
            .replace(Regex("（定-?金）"), "") // 去除（定金）（定-金）
            .replace(Regex("（尾款）"), "") // 去除（尾款）
            .replace(Regex("CP先行"), "") // 去除CP先行
            .replace(Regex("定-?金"), "") // 去除独立的定金/定-金
            .replace(Regex("尾款"), "") // 去除独立的尾款
            .replace(Regex("^\\s*-\\s*"), "") // 去除开头的 -
            .replace(Regex("\\s+"), " ") // 合并多余空格
            .trim()
    }
}

internal fun importCents(value: Double): Long {
    require(value.isFinite() && value >= 0 && value <= 999999999.99) { "金额须非负、最多两位小数" }
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact()
}

internal fun importSum(values: List<Double>): Double =
    values.fold(BigDecimal.ZERO) { total, value -> total.add(BigDecimal.valueOf(value)) }.toDouble()

private fun importSubtract(first: Double, second: Double): Double =
    BigDecimal.valueOf(first).subtract(BigDecimal.valueOf(second)).toDouble()

/**
 * Largest-remainder allocation conserves source cents, including shares belonging to skipped
 * pieces.
 */
internal fun allocateImportMoney(total: Double, weights: List<Double>): List<Double> {
    val cents = importCents(total)
    if (weights.isEmpty()) return emptyList()
    require(weights.all { it.isFinite() && it >= 0 }) { "商品金额无效" }
    val normalized = if (weights.any { it > 0 }) weights else List(weights.size) { 1.0 }
    val weightSum =
        normalized.fold(BigDecimal.ZERO) { sum, weight -> sum.add(BigDecimal.valueOf(weight)) }
    val exact =
        normalized.map {
            BigDecimal.valueOf(cents)
                .multiply(BigDecimal.valueOf(it))
                .divide(weightSum, 16, RoundingMode.DOWN)
        }
    val allocated = exact.map { it.setScale(0, RoundingMode.DOWN).longValueExact() }.toMutableList()
    val remainder = (cents - allocated.sum()).toInt()
    exact.indices
        .sortedByDescending { exact[it].subtract(BigDecimal.valueOf(allocated[it])) }
        .take(remainder)
        .forEach { allocated[it]++ }
    return allocated.map { BigDecimal.valueOf(it, 2).toDouble() }
}

internal fun parseImportDate(value: String): Long? {
    for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")) {
        val position = ParsePosition(0)
        val date =
            SimpleDateFormat(pattern, Locale.CHINA)
                .apply { isLenient = false }
                .parse(value.trim(), position)
        if (date != null && position.index == value.trim().length) return date.time
    }
    return null
}

internal fun isImportItemReady(index: Int, items: List<ImportItemState>): Boolean {
    val item = items.getOrNull(index) ?: return false
    fun validMoney(row: ImportItemState): Boolean =
        try {
            require(row.committedItemId == null && row.sourceProblem == null)
            require(
                listOf(row.priceInput, row.shippingInput, row.paidInput).all {
                    it == null || OrderImportSource.money(it) is ImportMoney.Valid
                }
            )
            val total = importCents(importSum(listOf(row.price, row.shipping)))
            val paid = importCents(row.paidAmount)
            importCents(row.price)
            importCents(row.shipping)
            row.financialConfirmed &&
                total > 0 &&
                paid <= total &&
                (paid == 0L || parseImportDate(row.purchaseDate) != null)
        } catch (_: Exception) {
            false
        }
    fun validIdentity(row: ImportItemState) =
        row.name.isNotBlank() && row.brandId > 0 && row.categoryId > 0
    if (
        item.committedItemId != null ||
            item.sourceProblem != null ||
            listOf(item.priceInput, item.shippingInput, item.paidInput).any {
                it != null && (it.isBlank() || OrderImportSource.money(it) !is ImportMoney.Valid)
            }
    )
        return false
    if (!validMoney(item)) return false
    val paired = item.pairedWith
    if (paired != null) {
        val other = items.getOrNull(paired) ?: return false
        if (
            other.committedItemId != null ||
                other.sourceProblem != null ||
                other.pairedWith != index ||
                paired == index ||
                !validMoney(other)
        )
            return false
        if (
            setOf(item.paymentRole, other.paymentRole) !=
                setOf(PaymentRole.DEPOSIT, PaymentRole.BALANCE)
        )
            return false
        return validIdentity(if (item.paymentRole == PaymentRole.DEPOSIT) item else other)
    }
    if (!validIdentity(item)) return false
    if (item.paymentRole == PaymentRole.DEPOSIT)
        return try {
            item.manualBalance != null &&
                (item.balanceInput == null ||
                    OrderImportSource.money(item.balanceInput) is ImportMoney.Valid) &&
                importCents(item.manualBalance) > 0
        } catch (_: Exception) {
            false
        }
    // A lone balance cannot establish the original full price: it must be paired or explicitly
    // reclassified.
    return item.paymentRole != PaymentRole.BALANCE
}

/** Import drafts gain physical storage controls only for a confirmed, fully paid final order. */
internal fun isImportStorageAvailable(index: Int, items: List<ImportItemState>): Boolean {
    if (!isImportItemReady(index, items)) return false
    val item = items[index]
    val partner = item.pairedWith?.let { items[it] }
    if (partner == null && item.paymentRole == PaymentRole.DEPOSIT) return false
    val rows = listOfNotNull(item, partner)
    return runCatching {
            importCents(importSum(rows.map { it.paidAmount })) ==
                importCents(importSum(rows.flatMap { listOf(it.price, it.shipping) }))
        }
        .getOrDefault(false)
}

internal fun visibleOrderLines(state: TaobaoImportUiState): List<Pair<TaobaoOrder, Int>> =
    state.orders.flatMap { order ->
        order.items.indices
            .filter { index ->
                val row = order.items[index]
                val date = order.orderTime.take(10)
                (state.query.isBlank() ||
                    listOf(row.name, order.shopName, order.orderId, row.styleSpec).any {
                        it.contains(state.query, true)
                    }) &&
                    (state.shopFilter.isBlank() ||
                        order.shopName.contains(state.shopFilter, true)) &&
                    (state.dateFrom.isBlank() || date >= state.dateFrom) &&
                    (state.dateTo.isBlank() || date <= state.dateTo) &&
                    when (state.selectionFilter) {
                        "只看已选" -> "${order.orderId}:$index" in state.selectedItems
                        "交易成功" -> order.orderStatus == "交易成功"
                        "交易关闭" -> order.orderStatus == "交易关闭"
                        "日期未知" -> parseImportDate(order.orderTime) == null
                        else -> true
                    }
            }
            .map { order to it }
    }

internal fun importProblems(index: Int, items: List<ImportItemState>): List<String> {
    val item = items.getOrNull(index) ?: return listOf("资料不存在")
    if (item.committedItemId != null) return listOf("已导入")
    return buildList {
        item.sourceProblem?.let(::add)
        if (item.name.isBlank()) add("缺名称")
        if (item.brandId <= 0) add("品牌待映射")
        if (item.categoryId <= 0 && item.paymentRole != PaymentRole.BALANCE) add("缺类型")
        if (
            runCatching {
                    importCents(item.price)
                    importCents(item.shipping)
                    importCents(item.paidAmount)
                }
                .isFailure ||
                listOf(item.priceInput, item.shippingInput, item.paidInput).any {
                    it != null && OrderImportSource.money(it) !is ImportMoney.Valid
                }
        )
            add("金额缺失或格式异常")
        else if (
            importCents(importSum(listOf(item.price, item.shipping))) <= 0 ||
                importCents(item.paidAmount) >
                    importCents(importSum(listOf(item.price, item.shipping)))
        )
            add("金额需核对")
        if (item.paidAmount > 0 && parseImportDate(item.purchaseDate) == null) add("付款日期待核对")
        if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith == null) add("尾款待配对")
        if (
            item.paymentRole == PaymentRole.DEPOSIT &&
                item.pairedWith == null &&
                (item.manualBalance ?: 0.0) <= 0
        )
            add("尾款金额待补充")
        if (!item.financialConfirmed) add("财务尚未确认")
        if (isEmpty() && !isImportItemReady(index, items)) add("关联资料待核对")
    }
}

internal fun importNewItemCount(items: List<ImportItemState>): Int {
    val valid = items.indices.filter { isImportItemReady(it, items) }.toSet()
    return valid.count { index ->
        items[index].pairedWith?.let { it !in valid || index < it } ?: true
    }
}
