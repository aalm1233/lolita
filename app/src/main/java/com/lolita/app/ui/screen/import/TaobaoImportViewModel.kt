package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoOrderParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.Price
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class ImportStep { SELECT, PREPARE, DETAIL, IMPORTING, RESULT }

enum class PaymentRole { DEPOSIT, BALANCE }

/**
 * 待导入商品的可编辑状态
 */
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
    val balanceDueDate: Long? = null
)

data class ImportResult(
    val importedCount: Int = 0,
    val mergedCount: Int = 0,  // 定金尾款合并数
    val skippedCount: Int = 0  // 未完善跳过数
)

/**
 * 预处理阶段发现的缺失数据项
 */
data class MissingDataItem(
    val name: String,
    val type: MissingDataType,
    val checked: Boolean = true,
    val extra: String = ""  // 附加信息，如 CategoryGroup
)

enum class MissingDataType { BRAND, CATEGORY }

data class TaobaoImportUiState(
    val orders: List<TaobaoOrder> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fileLoaded: Boolean = false,
    // Step flow
    val currentStep: ImportStep = ImportStep.SELECT,
    val importItems: List<ImportItemState> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currentItemIndex: Int = 0,
    // Prepare step: missing data
    val missingItems: List<MissingDataItem> = emptyList(),
    // Import result
    val importResult: ImportResult? = null
)

class TaobaoImportViewModel(application: Application) : AndroidViewModel(application) {

    private val brandRepository: BrandRepository = AppModule.brandRepository()
    private val categoryRepository: CategoryRepository = AppModule.categoryRepository()
    private val itemRepository: ItemRepository = AppModule.itemRepository()
    private val priceRepository: PriceRepository = AppModule.priceRepository()
    private val database: LolitaDatabase = AppModule.database()

    private val _uiState = MutableStateFlow(TaobaoImportUiState())
    val uiState: StateFlow<TaobaoImportUiState> = _uiState.asStateFlow()

    init {
        // 合并品牌和分类列表的加载，避免竞态
        viewModelScope.launch {
            combine(
                brandRepository.getAllBrands(),
                categoryRepository.getAllCategories()
            ) { brands, categories ->
                Pair(brands, categories)
            }.collect { (brands, categories) ->
                _uiState.value = _uiState.value.copy(brands = brands, categories = categories)
            }
        }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>()
                    .contentResolver.openInputStream(uri)
                    ?: throw Exception("无法读取文件")
                val orders = inputStream.use { TaobaoOrderParser.parse(it) }

                // 默认选中所有"交易成功"订单中的非意向金商品
                val selected = mutableSetOf<String>()
                orders.forEach { order ->
                    order.items.forEachIndexed { index, item ->
                        val key = "${order.orderId}:$index"
                        if (order.orderStatus == "交易成功" && !item.styleSpec.contains("意向金")) {
                            selected.add(key)
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    orders = orders,
                    selectedItems = selected,
                    isLoading = false,
                    fileLoaded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "解析失败: ${e.message}"
                )
            }
        }
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
            order.items.forEachIndexed { index, _ ->
                all.add("${order.orderId}:$index")
            }
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

    /**
     * 从选择步骤进入预处理步骤，扫描缺失的品牌和类型
     */
    fun proceedToPrepare() {
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
            missingItems.add(MissingDataItem(name = it, type = MissingDataType.BRAND))
        }
        missingCategories.entries.sortedBy { it.key }.forEach { (name, group) ->
            missingItems.add(MissingDataItem(name = name, type = MissingDataType.CATEGORY, extra = group))
        }

        if (missingItems.isEmpty()) {
            // 没有缺失数据，直接进入完善步骤
            buildDetailItems()
        } else {
            _uiState.value = _uiState.value.copy(
                currentStep = ImportStep.PREPARE,
                missingItems = missingItems
            )
        }
    }

    fun toggleMissingItem(index: Int) {
        val items = _uiState.value.missingItems.toMutableList()
        if (index in items.indices) {
            items[index] = items[index].copy(checked = !items[index].checked)
            _uiState.value = _uiState.value.copy(missingItems = items)
        }
    }

    fun toggleAllMissingItems(checked: Boolean) {
        _uiState.value = _uiState.value.copy(
            missingItems = _uiState.value.missingItems.map { it.copy(checked = checked) }
        )
    }

    /**
     * 确认预处理：创建勾选的缺失数据，然后进入完善步骤
     */
    fun confirmPrepare() {
        val checkedItems = _uiState.value.missingItems.filter { it.checked }
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
                            brandRepository.insertBrand(Brand(name = item.name))
                        }
                        MissingDataType.CATEGORY -> {
                            val group = if (item.extra == "ACCESSORY") CategoryGroup.ACCESSORY
                                else CategoryGroup.CLOTHING
                            categoryRepository.insertCategory(Category(name = item.name, group = group))
                        }
                    }
                }
                // 等待 Flow 更新品牌和分类列表
                val brands = brandRepository.getAllBrands().first()
                val categories = categoryRepository.getAllCategories().first()
                _uiState.value = _uiState.value.copy(
                    brands = brands,
                    categories = categories,
                    isLoading = false
                )
                buildDetailItems()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "创建数据失败: ${e.message}"
                )
            }
        }
    }

    fun goBackToSelectFromPrepare() {
        _uiState.value = _uiState.value.copy(currentStep = ImportStep.SELECT, missingItems = emptyList())
    }

    /**
     * 构建 ImportItemState 列表并进入完善步骤
     */
    private fun buildDetailItems() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) return

        val brands = _uiState.value.brands
        val categories = _uiState.value.categories

        val importItems = selected.map { item ->
            val matchedBrand = matchBrand(item.shopName, brands)
            val matchedCategory = matchCategory(item.parsedType, categories)
            val role = detectPaymentRole(item.name)

            ImportItemState(
                originalItem = item,
                name = cleanItemName(item.name),
                brandId = matchedBrand?.id ?: 0L,
                categoryId = matchedCategory?.id ?: 0L,
                color = item.parsedColor ?: "",
                size = item.parsedSize ?: "",
                price = item.price,
                purchaseDate = item.orderTime,
                styleSpec = item.styleSpec,
                paymentRole = role
            )
        }

        // 自动匹配定金尾款
        val matched = autoMatchDepositBalance(importItems)

        _uiState.value = _uiState.value.copy(
            currentStep = ImportStep.DETAIL,
            importItems = matched,
            currentItemIndex = 0
        )
    }

    private fun guessCategoryGroup(parsedType: String): String {
        val accessoryKeywords = listOf("头饰", "蝴蝶结", "帽子", "KC", "发带", "发夹",
            "包", "鞋", "袜", "手套", "项链", "耳环", "戒指", "胸针", "腰链")
        return if (accessoryKeywords.any { parsedType.contains(it, ignoreCase = true) })
            "ACCESSORY" else "CLOTHING"
    }

    fun goBackToSelect() {
        _uiState.value = _uiState.value.copy(currentStep = ImportStep.SELECT, missingItems = emptyList())
    }

    fun updateImportItem(index: Int, update: (ImportItemState) -> ImportItemState) {
        val items = _uiState.value.importItems.toMutableList()
        if (index in items.indices) {
            items[index] = update(items[index])
            _uiState.value = _uiState.value.copy(importItems = items)
        }
    }

    fun setCurrentItemIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentItemIndex = index)
    }

    fun onLocalImageSelected(itemIndex: Int, uri: Uri?) {
        if (uri == null || itemIndex < 0) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localPath = ImageFileHelper.copyToInternalStorage(getApplication(), uri)
                updateImportItem(itemIndex) { it.copy(imageUrl = localPath) }
            } catch (_: Exception) { }
        }
    }

    /**
     * 执行批量导入 — 使用 ImportItemState 中的预匹配结果
     */
    fun executeImport() {
        val allItems = _uiState.value.importItems
        val validIndices = allItems.indices.filter { idx ->
            val item = allItems[idx]
            if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null) {
                // 已配对的尾款项只需价格有效
                item.price > 0
            } else {
                // 普通项和未配对尾款项需要品牌和分类
                item.brandId > 0 && item.categoryId > 0
            }
        }.toSet()
        if (validIndices.isEmpty()) return

        _uiState.value = _uiState.value.copy(currentStep = ImportStep.IMPORTING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val processedIndices = mutableSetOf<Int>()
                var importedCount = 0
                var mergedCount = 0

                database.withTransaction {
                    for (index in allItems.indices) {
                        if (index !in validIndices || index in processedIndices) continue
                        val importItem = allItems[index]

                        val pairedIdx = importItem.pairedWith
                        val isPaired = pairedIdx != null && pairedIdx in allItems.indices
                            && pairedIdx in validIndices
                            && allItems[pairedIdx].pairedWith == index

                        if (isPaired && importItem.paymentRole != null) {
                            // 定金尾款合并导入 — 使用定金项作为主数据源
                            val depositItem = if (importItem.paymentRole == PaymentRole.DEPOSIT)
                                importItem else allItems[pairedIdx!!]
                            val balanceItem = if (importItem.paymentRole == PaymentRole.BALANCE)
                                importItem else allItems[pairedIdx!!]
                            val mainItem = depositItem
                            val totalPrice = depositItem.price + balanceItem.price

                            val itemId = itemRepository.insertItem(
                                Item(
                                    name = mainItem.name,
                                    brandId = mainItem.brandId,
                                    categoryId = mainItem.categoryId,
                                    color = mainItem.color.ifBlank { null },
                                    size = mainItem.size.ifBlank { null },
                                    imageUrl = mainItem.imageUrl,
                                    status = ItemStatus.OWNED,
                                    description = ""
                                )
                            )
                            priceRepository.insertPrice(
                                Price(
                                    itemId = itemId,
                                    type = PriceType.DEPOSIT_BALANCE,
                                    totalPrice = totalPrice,
                                    deposit = depositItem.price,
                                    balance = balanceItem.price,
                                    purchaseDate = parseDateToMillis(mainItem.purchaseDate)
                                )
                            )
                            processedIndices.add(index)
                            processedIndices.add(pairedIdx!!)
                            importedCount++
                            mergedCount++
                        } else {
                            // 普通商品导入
                            val itemId = itemRepository.insertItem(
                                Item(
                                    name = importItem.name,
                                    brandId = importItem.brandId,
                                    categoryId = importItem.categoryId,
                                    color = importItem.color.ifBlank { null },
                                    size = importItem.size.ifBlank { null },
                                    imageUrl = importItem.imageUrl,
                                    status = ItemStatus.OWNED,
                                    description = ""
                                )
                            )
                            priceRepository.insertPrice(
                                Price(
                                    itemId = itemId,
                                    type = PriceType.FULL,
                                    totalPrice = importItem.price,
                                    purchaseDate = parseDateToMillis(importItem.purchaseDate)
                                )
                            )
                            processedIndices.add(index)
                            importedCount++
                        }
                    }
                }

                val skipped = allItems.size - validIndices.size
                _uiState.value = _uiState.value.copy(
                    currentStep = ImportStep.RESULT,
                    importResult = ImportResult(
                        importedCount = importedCount,
                        mergedCount = mergedCount,
                        skippedCount = skipped
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    currentStep = ImportStep.DETAIL,
                    errorMessage = "导入失败: ${e.message}"
                )
            }
        }
    }

    private fun parseDateToMillis(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            format.parse(dateStr)?.time
        } catch (_: Exception) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                format.parse(dateStr)?.time
            } catch (_: Exception) { null }
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

    /**
     * 自动匹配定金尾款配对
     * 多维度评分: 核心名称匹配 > URL匹配 > 店铺匹配 > 关键词重叠
     * 核心名称 = 去除所有噪音后的产品系列名
     */
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
                val urlBal = bal.originalItem.productUrl
                val shopBal = bal.originalItem.shopName

                var score = 0

                // 核心名称完全相同 → 强匹配
                if (coreNames[dIdx].isNotBlank() && coreNames[dIdx] == coreNames[bIdx]) {
                    score += 10
                }
                // 核心名称包含关系 → 中匹配
                else if (coreNames[dIdx].isNotBlank() && coreNames[bIdx].isNotBlank()) {
                    val shorter = if (coreNames[dIdx].length <= coreNames[bIdx].length)
                        coreNames[dIdx] else coreNames[bIdx]
                    val longer = if (coreNames[dIdx].length > coreNames[bIdx].length)
                        coreNames[dIdx] else coreNames[bIdx]
                    if (shorter.length >= 3 && longer.contains(shorter)) score += 7
                }

                // 关键词重叠匹配 — 处理名称结构不同但产品相同的情况
                if (score < 7 && keywords[dIdx].isNotEmpty() && keywords[bIdx].isNotEmpty()) {
                    val overlap = keywords[dIdx].intersect(keywords[bIdx])
                    val smaller = minOf(keywords[dIdx].size, keywords[bIdx].size)
                    if (smaller > 0 && overlap.size.toFloat() / smaller >= 0.6f && overlap.size >= 2) {
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

    /**
     * 从原始商品名中提取核心产品名（系列名），去除所有噪音
     */
    private fun extractCoreName(name: String): String {
        return name
            .replace(Regex("【[^】]*】"), "")              // 【预约期包邮】【定金】【尾款】
            .replace(Regex("＜[^＞]*＞"), "")              // ＜不死锦鲤·横公鱼＞
            .replace(Regex("[《》]"), "")                   // 《旋转虎虎》→ 旋转虎虎（保留内容）
            .replace(Regex("（[^）]*）"), "")               // （定金）（定-金）（长短款）
            .replace(Regex("\\([^)]*\\)"), "")             // (208+800/183+730)
            .replace(Regex("定-?金"), "")
            .replace(Regex("尾款"), "")
            .replace(Regex("意向"), "")
            .replace(Regex("正式"), "")
            .replace(Regex("预约"), "")
            .replace(Regex("CP先行"), "")
            .replace(Regex("加购小物"), "")
            .replace(Regex("\\d+团"), "")                  // 2团
            .replace(Regex("\\d+批"), "")
            .replace(Regex("[一二三四五六七八九十]+团"), "")  // 一团
            .replace(Regex("[一二三四五六七八九十]+批"), "")  // 六批
            .replace(Regex("需有[^*｜|]*"), "")             // 需有定金 / 需有一团定金
            .replace(Regex("需补[^*｜|]*"), "")             // 需补尾款
            .replace(Regex("需要有[^*｜|]*"), "")
            .replace(Regex("路德\\s*"), "")                 // "路德 金鳞流光" → "金鳞流光"
            .replace(Regex("\\d+\\.\\d+日[^*｜|]*"), "")    // 12.5日截
            .replace(Regex("\\d+月\\d+日[^*｜|]*"), "")     // 1月2日-2月2日
            .replace(Regex("春季新款|秋冬纯棉|慢团|成团再贩|2024再贩|页面"), "")
            .replace(Regex("[｜|*·]"), " ")                 // 分隔符转空格
            .replace(Regex("\\s+"), " ")                   // 合并空格
            .trim()
            .trimStart('-', ' ')
            .trimEnd('-', ' ')
    }

    /**
     * 从核心名称中提取有意义的关键词（用于模糊匹配）
     * 过滤掉通用词，保留产品系列名、款式名等
     */
    private fun extractKeywords(coreName: String): Set<String> {
        val stopWords = setOf(
            "原创", "lolita", "Lolita", "LOLITA", "洋装", "连衣裙", "套装",
            "复古", "优雅", "华丽", "刺绣", "新款", "春季", "秋冬", "纯棉",
            "页面", "时间", "开始", "截止"
        )
        return coreName.split(Regex("[\\s+\\-]"))
            .map { it.trim() }
            .filter { it.length >= 2 && it.lowercase() !in stopWords.map { s -> s.lowercase() } }
            .toSet()
    }

    /**
     * 判断两个店铺名是否属于同一品牌（处理同品牌不同店铺名的情况）
     */
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

    /**
     * 手动配对两个项
     */
    fun manualPair(indexA: Int, indexB: Int) {
        val items = _uiState.value.importItems.toMutableList()
        if (indexA !in items.indices || indexB !in items.indices) return
        // 先解除旧配对
        items[indexA].pairedWith?.let { old ->
            if (old in items.indices) items[old] = items[old].copy(pairedWith = null)
        }
        items[indexB].pairedWith?.let { old ->
            if (old in items.indices) items[old] = items[old].copy(pairedWith = null)
        }
        items[indexA] = items[indexA].copy(pairedWith = indexB)
        items[indexB] = items[indexB].copy(pairedWith = indexA)
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /**
     * 手动设置订单的定金/尾款角色
     */
    fun setPaymentRole(index: Int, role: PaymentRole?) {
        val items = _uiState.value.importItems.toMutableList()
        if (index !in items.indices) return
        val oldRole = items[index].paymentRole
        // 角色变更时先解除旧配对
        if (oldRole != role && items[index].pairedWith != null) {
            val pairedIdx = items[index].pairedWith!!
            if (pairedIdx in items.indices) {
                items[pairedIdx] = items[pairedIdx].copy(pairedWith = null)
            }
            items[index] = items[index].copy(pairedWith = null)
        }
        items[index] = items[index].copy(paymentRole = role)
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /**
     * 取消配对
     */
    fun unpair(index: Int) {
        val items = _uiState.value.importItems.toMutableList()
        if (index !in items.indices) return
        val pairedIdx = items[index].pairedWith ?: return
        items[index] = items[index].copy(pairedWith = null)
        if (pairedIdx in items.indices) {
            items[pairedIdx] = items[pairedIdx].copy(pairedWith = null)
        }
        _uiState.value = _uiState.value.copy(importItems = items)
    }

    /**
     * 在导入流程中新增类型
     */
    fun addCategory(name: String, group: CategoryGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categoryRepository.insertCategory(Category(name = name, group = group))
                val categories = categoryRepository.getAllCategories().first()
                _uiState.value = _uiState.value.copy(categories = categories)
            } catch (_: Exception) { }
        }
    }

    // --- 智能匹配逻辑 ---

    private fun matchBrand(shopName: String, brands: List<Brand>): Brand? {
        return brands.firstOrNull { brand ->
            shopName.contains(brand.name, ignoreCase = true) ||
                brand.name.contains(shopName, ignoreCase = true)
        }
    }

    private fun matchCategory(parsedType: String?, categories: List<Category>): Category? {
        if (parsedType == null) return null
        // 精确匹配
        categories.firstOrNull { it.name.equals(parsedType, ignoreCase = true) }
            ?.let { return it }
        // 关键词包含匹配
        val typeMap = mapOf(
            "OP" to listOf("OP", "开襟OP", "堆褶OP", "堆褶开襟OP"),
            "SK" to listOf("SK", "拼色SK"),
            "JSK" to listOf("JSK"),
            "斗篷" to listOf("斗篷", "罩衫斗篷"),
            "其他头饰" to listOf("头饰", "蝴蝶结头饰")
        )
        for ((categoryName, keywords) in typeMap) {
            if (keywords.any { parsedType.contains(it, ignoreCase = true) }) {
                categories.firstOrNull { it.name == categoryName }?.let { return it }
            }
        }
        return null
    }

    private fun cleanItemName(name: String): String {
        return name
            .replace(Regex("【[^】]*】"), "")          // 去除【预约期包邮】等
            .replace(Regex("＜[^＞]*＞"), "")          // 去除＜不死锦鲤·横公鱼＞等
            .replace(Regex("（定-?金）"), "")            // 去除（定金）（定-金）
            .replace(Regex("（尾款）"), "")              // 去除（尾款）
            .replace(Regex("CP先行"), "")               // 去除CP先行
            .replace(Regex("定-?金"), "")               // 去除独立的定金/定-金
            .replace(Regex("尾款"), "")                 // 去除独立的尾款
            .replace(Regex("^\\s*-\\s*"), "")           // 去除开头的 -
            .replace(Regex("\\s+"), " ")               // 合并多余空格
            .trim()
    }
}