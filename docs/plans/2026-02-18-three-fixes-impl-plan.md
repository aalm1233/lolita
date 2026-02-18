# 三项功能修正 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix three issues: deposit-only import with manual balance, coordinate list price linked to settings, and style spec parsing algorithm optimization.

**Architecture:** Modify existing ViewModel/Screen/Parser layers. No new entities or DB migrations needed. Payment records created via existing PaymentRepository.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Material3

---

### Task 1: Add manual balance fields to ImportItemState

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:42-55`

**Step 1: Add fields to ImportItemState**

At line 54, add two new fields before the closing parenthesis:

```kotlin
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
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt
git commit -m "feat(import): add manualBalance and balanceDueDate to ImportItemState"
```

---

### Task 2: Add manual balance UI in ImportDetailScreen

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt:304-324`

**Step 1: Add manual balance input for unpaired DEPOSIT items**

After the existing paired-deposit readonly field block (line 324), add a new block for unpaired deposits. Insert before the `// 图片` comment at line 326:

```kotlin
                // 未配对定金：手动输入尾款信息
                if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith == null) {
                    var balanceText by remember(item) {
                        mutableStateOf(item.manualBalance?.let { String.format("%.2f", it) } ?: "")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("尾款信息（手动填写）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = balanceText,
                            onValueChange = { v ->
                                balanceText = v
                                onUpdate { it.copy(manualBalance = v.toDoubleOrNull()) }
                            },
                            label = { Text("尾款金额") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            prefix = { Text("¥") }
                        )
                        // 计划付款日期选择器
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = item.balanceDueDate
                        )
                        var showDatePicker by remember { mutableStateOf(false) }
                        val dueDateText = item.balanceDueDate?.let {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(it))
                        } ?: ""
                        OutlinedTextField(
                            value = dueDateText,
                            onValueChange = {},
                            label = { Text("计划付款日期") },
                            modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        if (showDatePicker) {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDatePicker = false
                                        datePickerState.selectedDateMillis?.let { millis ->
                                            onUpdate { it.copy(balanceDueDate = millis) }
                                        }
                                    }) { Text("确定") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                                }
                            ) { DatePicker(state = datePickerState) }
                        }
                    }
                }
```

Note: Need to add imports for `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`, `HorizontalDivider`, `clickable` at the top of the file. Also add `@OptIn(ExperimentalMaterial3Api::class)` if not already present.

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt
git commit -m "feat(import): add manual balance input UI for unpaired deposits"
```

---

### Task 3: Handle unpaired DEPOSIT in executeImport()

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:372-483`

**Step 1: Add PaymentRepository import and field**

Add import at top of file:
```kotlin
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.repository.PaymentRepository
```

Add to class fields (after line 98):
```kotlin
private val paymentRepository: PaymentRepository = AppModule.paymentRepository()
```

**Step 2: Update validation in executeImport()**

In `executeImport()`, update the validation filter (line 374-383). Add a third condition for unpaired DEPOSIT with manual balance:

```kotlin
val validIndices = allItems.indices.filter { idx ->
    val item = allItems[idx]
    if (item.paymentRole == PaymentRole.BALANCE && item.pairedWith != null) {
        item.price > 0
    } else if (item.paymentRole == PaymentRole.DEPOSIT && item.pairedWith == null) {
        // 未配对定金：需要品牌、分类、手动尾款金额和计划付款日期
        item.brandId > 0 && item.categoryId > 0
            && (item.manualBalance ?: 0.0) > 0 && item.balanceDueDate != null
    } else {
        item.brandId > 0 && item.categoryId > 0
    }
}.toSet()
```

**Step 3: Add unpaired deposit import logic**

In the transaction block, after the paired deposit/balance branch (line 438) and before the `else` branch (line 439), add a new branch:

```kotlin
} else if (importItem.paymentRole == PaymentRole.DEPOSIT
    && importItem.pairedWith == null
    && importItem.manualBalance != null && importItem.manualBalance > 0) {
    // 未配对定金 + 手动尾款
    val balance = importItem.manualBalance
    val totalPrice = importItem.price + balance

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
    val priceId = priceRepository.insertPrice(
        Price(
            itemId = itemId,
            type = PriceType.DEPOSIT_BALANCE,
            totalPrice = totalPrice,
            deposit = importItem.price,
            balance = balance,
            purchaseDate = parseDateToMillis(importItem.purchaseDate)
        )
    )
    // 定金 Payment（已付）
    paymentRepository.insertPayment(
        Payment(
            priceId = priceId,
            amount = importItem.price,
            dueDate = parseDateToMillis(importItem.purchaseDate) ?: System.currentTimeMillis(),
            isPaid = true,
            paidDate = parseDateToMillis(importItem.purchaseDate)
        )
    )
    // 尾款 Payment（未付）
    paymentRepository.insertPayment(
        Payment(
            priceId = priceId,
            amount = balance,
            dueDate = importItem.balanceDueDate!!,
            isPaid = false
        )
    )
    processedIndices.add(index)
    importedCount++
```

Note: `priceRepository.insertPrice()` currently returns `Unit`. Check if it needs to return the inserted ID. If it doesn't, modify `PriceDao` to add an `@Insert` method that returns `Long`, and update `PriceRepository.insertPrice()` to return `Long`. Alternatively, query the price after insert.

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A
git commit -m "feat(import): handle unpaired deposit with manual balance and payment creation"
```

---

### Task 4: Link coordinate list price display to showTotalPrice setting

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt:21-29,50-101`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt:251-258,357-372`

**Step 1: Add showPrice to CoordinateListUiState**

In `CoordinateListUiState` (line 21-29), add field:

```kotlin
data class CoordinateListUiState(
    val coordinates: List<Coordinate> = emptyList(),
    val itemCounts: Map<Long, Int> = emptyMap(),
    val itemImagesByCoordinate: Map<Long, List<String?>> = emptyMap(),
    val priceByCoordinate: Map<Long, Double> = emptyMap(),
    val showPrice: Boolean = false,
    val columnsPerRow: Int = 1,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
```

**Step 2: Read AppPreferences in CoordinateListViewModel**

Add import and constructor parameter:

```kotlin
import com.lolita.app.data.preferences.AppPreferences
```

Add to constructor:
```kotlin
class CoordinateListViewModel(
    private val coordinateRepository: CoordinateRepository = com.lolita.app.di.AppModule.coordinateRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val appPreferences: AppPreferences = com.lolita.app.di.AppModule.appPreferences()
) : ViewModel() {
```

**Step 3: Collect showTotalPrice in loadCoordinates()**

In `loadCoordinates()`, add `appPreferences.showTotalPrice` to the combine flow. Wrap the existing combine with one more level:

```kotlin
private fun loadCoordinates() {
    viewModelScope.launch {
        combine(
            combine(
                coordinateRepository.getAllCoordinates(),
                coordinateRepository.getItemCountsByCoordinate()
            ) { a, b -> Pair(a, b) },
            combine(
                itemRepository.getAllItems(),
                priceRepository.getItemPriceSums()
            ) { a, b -> Pair(a, b) },
            appPreferences.showTotalPrice
        ) { (coordinates, itemCounts), (allItems, priceSums), showPrice ->
            // ... existing mapping logic ...
            CoordinateListUiState(
                coordinates = coordinates,
                itemCounts = countMap,
                itemImagesByCoordinate = imageMap,
                priceByCoordinate = coordPriceMap,
                showPrice = showPrice,
                columnsPerRow = _uiState.value.columnsPerRow,
                isLoading = false
            )
        }.collect { state ->
            _uiState.value = state
        }
    }
}
```

**Step 4: Pass showPrice to card composables in CoordinateListScreen**

Add `showPrice` parameter to `CoordinateCard` and `CoordinateGridCard` signatures. In the call sites, pass `uiState.showPrice`.

In `CoordinateCard` (line 251-258), wrap price display:
```kotlin
if (showPrice && totalPrice > 0) {
    Text(
        "¥%.0f".format(totalPrice),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Pink400
    )
}
```

In `CoordinateGridCard` (line 357-372), wrap price display:
```kotlin
if (showPrice && totalPrice > 0) {
    Surface(...) { Text(...) }
}
```

**Step 5: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateViewModel.kt \
      app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt
git commit -m "feat(coordinate): link list price display to showTotalPrice setting"
```

---

### Task 5: Enhance noise removal in TaobaoOrderParser

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/TaobaoOrderParser.kt:90-117`

**Step 1: Replace parseStyleSpec() cleaning section**

Replace the existing cleaning block (lines 93-102) with enhanced noise removal:

```kotlin
private fun parseStyleSpec(spec: String): ParsedStyle {
    if (spec.isBlank()) return ParsedStyle()
    val cleaned = spec
        // 括号类噪声
        .replace(Regex("【[^】]*】"), "")           // 【现货】【全款预约】
        .replace(Regex("\\[[^\\]]*]"), "")          // [一批尾款] [定金]
        .replace(Regex("\\{[^}]*}"), "")            // {尾款} {定金} {慢团}
        .replace(Regex("<[^>]*>"), "")              // <慢团> <定金>
        .replace(Regex("（[^）]*(?:尾款|定金|不单售|备注|售后)[^）]*）"), "") // （尾款）（不单售）
        .replace(Regex("\\([^)]*(?:尾款|定金|不单售|备注|售后)[^)]*\\)"), "") // (尾款)
        // 点缀噪声
        .replace(Regex("·(?:尾款|定金|现货)"), "")   // ·尾款 ·定金
        .replace(Regex("β款"), "")
        // 支付/批次噪声
        .replace(Regex("[一二三四五六七八九十\\d]+批"), "")  // 一批 六批 2批
        .replace(Regex("[一二三四五六七八九十\\d]+团"), "")  // 一团 2团
        .replace(Regex("(?:仅|只)?(?:定-?金|尾款|现货|预约|全款|意向金)"), "")
        // 备注类
        .replace(Regex("色码以定金为准"), "")
        .replace(Regex("(?:自行)?备注尺码|尺码(?:请)?备注|单品尺码请备注"), "")
        .replace(Regex("发货地址[^;/]*"), "")
        .replace(Regex("(?:发货|收货)(?:以|为)?(?:尾款)?地址[^;/]*"), "")
        .replace(Regex("注意[^;/]*"), "")
        .replace(Regex("需有[^;/]*"), "")
        .replace(Regex("需要有[^;/]*"), "")
        .replace(Regex("需补[^;/]*"), "")
        .replace(Regex("还需补[^;/]*"), "")
        // 日期时间模式
        .replace(Regex("\\d+\\.\\d+(?:晚上|上午|下午)?[^;/]{0,10}"), "")
        .replace(Regex("\\d+月\\d+日[^;/]*"), "")
        // 杂项
        .replace(Regex("本体"), "")
        .replace(Regex("正常长度"), "")
        .replace(Regex("不退不换不售后"), "")
        .replace(Regex("福袋[^;/]*"), "")
        .replace(Regex("单品[^;/]*"), "")
        .replace(Regex("按拍付顺序发"), "")
        .replace(Regex("仅[^;/]*期间[^;/]*"), "")
        .replace(Regex("物流情况"), "")
        .replace(Regex("年前年后"), "")
        .trim()
    // ... rest of parsing continues below
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/TaobaoOrderParser.kt
git commit -m "fix(parser): enhance noise removal for style spec parsing"
```

---

### Task 6: Implement multi-level splitting and smart classification

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/TaobaoOrderParser.kt:104-117,119-180`

**Step 1: Update keyword lists**

Add new type keywords (after line 135):
```kotlin
private val typeKeywords = listOf(
    // 基础类型
    "OP", "SK", "JSK", "SET", "FS",
    // 裙类变体
    "开襟OP", "堆褶OP", "堆褶开襟OP", "拼色SK", "罩裙", "半裙", "格裙", "长裙", "连衣裙",
    // 套装
    "Fullset", "FullSet", "fullset", "FULLSET", "华丽套装",
    // 上装
    "衬衫", "上衣", "外套", "长外套", "大衣", "罩衫", "斗篷", "罩衫斗篷", "胸衣", "蝙蝠胸衣",
    // 下装
    "短裤", "南瓜裤",
    // 配饰 — 头部
    "头饰", "蝴蝶结头饰", "帽子", "贝雷帽", "KC", "发箍", "发带", "发夹", "边夹", "头纱",
    // 配饰 — 身体
    "腰封", "拖尾腰封", "印花钢骨腰封", "手袖", "接袖", "袖子",
    // 配饰 — 其他
    "包", "包包", "吊坠", "项链", "耳环", "戒指", "胸针"
)
```

Add new color keywords (extend the existing list):
```kotlin
private val colorKeywords = listOf(
    // 基础色
    "黑色", "白色", "红色", "粉色", "蓝色", "绿色", "紫色", "黄色", "灰色", "棕色",
    "黑", "白", "红", "粉", "蓝", "绿", "紫", "黄", "灰", "棕",
    // 深浅变体
    "深蓝", "浅蓝", "天蓝", "藏蓝", "深绿", "浅绿", "墨绿", "军绿",
    "深紫", "浅紫", "暗红", "酒红", "玫红", "粉红",
    // 特殊色名
    "绀色", "苔绿", "生成色", "香槟色", "白金色", "月银色", "深海色",
    "龙骨酒红色", "金色", "银色", "玫瑰金", "肤色",
    "米白", "米黄", "米色", "卡其", "驼色", "咖啡",
    // 双色组合
    "黑白", "蓝黑", "灰黑", "绿金", "粉绿", "黑粉",
    // x/×分隔的双色
    "黑x红", "黑x青", "白×蓝", "黑x红色", "黑x青色", "白×蓝色",
    // 系列色名
    "织金", "白玫瑰", "黑玫瑰", "黑夕", "白昼", "蓝暮", "紫夜", "白金", "深海",
    // 配色/拼色表达
    "配色", "拼色", "撞色", "混色", "渐变",
    // 图色等特殊值
    "图色"
)
```

**Step 2: Replace the splitting and classification logic**

Replace the part splitting and classification (lines 104-116) with multi-level splitting + smart classification:

```kotlin
    // 阶段2: 多级分割
    val primaryParts = cleaned.split(Regex("[;/]")).map { it.trim() }.filter { it.isNotBlank() }
    val allParts = primaryParts.flatMap { part ->
        // 次级分割：空格和 -- （但不拆分 + 连接的复合类型）
        part.split(Regex("\\s+|--")).map { it.trim() }.filter { it.isNotBlank() }
    }

    var type: String? = null
    var size: String? = null
    var color: String? = null

    for (part in allParts) {
        classifyPart(part)?.let { (t, s, c) ->
            if (t != null && type == null) type = t
            if (s != null && size == null) size = s
            if (c != null && color == null) color = c
        }
    }
    return ParsedStyle(type, size, color)
}
```

**Step 3: Add classifyPart() method**

Add new private method after `parseStyleSpec()`:

```kotlin
/**
 * 智能分类单个 part，返回 Triple(type?, size?, color?)
 * 按优先级尝试：纯尺寸 → 精确类型 → 后缀类型 → 包含类型 → 前缀尺寸 → 颜色 → 兜底
 */
private fun classifyPart(part: String): Triple<String?, String?, String?>? {
    if (part.isBlank()) return null

    // 1. 纯尺寸
    if (isSizeKeyword(part)) return Triple(null, part, null)

    // 2. 精确类型匹配
    if (typeKeywords.any { part.equals(it, ignoreCase = true) }) {
        return Triple(part, null, null)
    }

    // 3. 后缀类型匹配 (如 "粉色柳波芙jsk", "深海色FS")
    for (suffix in typeSuffixes) {
        if (part.endsWith(suffix, ignoreCase = true) && part.length > suffix.length) {
            val prefix = part.substring(0, part.length - suffix.length)
            val extractedColor = if (isColorKeyword(prefix)) prefix else null
            return Triple(part, null, extractedColor)
        }
    }

    // 4. 包含类型匹配（长关键词优先）
    val sortedTypeKeywords = typeKeywords.sortedByDescending { it.length }
    for (keyword in sortedTypeKeywords) {
        if (part.contains(keyword, ignoreCase = true)) {
            val remaining = part.replace(Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE), "").trim()
            val extractedColor = if (remaining.isNotBlank() && isColorKeyword(remaining)) remaining else null
            return Triple(keyword, null, extractedColor)
        }
    }

    // 5. 前缀尺寸提取 (如 "M半裙", "L白色JSK", "XL上衣")
    val sizePrefix = extractLeadingSize(part)
    if (sizePrefix != null) {
        val remaining = part.substring(sizePrefix.length).trim()
        if (remaining.isNotBlank()) {
            val sub = classifyPart(remaining)
            return Triple(sub?.first, sizePrefix, sub?.third ?: (if (isColorKeyword(remaining)) remaining else null))
        }
        return Triple(null, sizePrefix, null)
    }

    // 6. 颜色匹配
    if (isColorKeyword(part)) return Triple(null, null, part)

    // 7. 兜底：归为颜色
    return Triple(null, null, part)
}

/**
 * 尝试从 part 开头提取尺寸（如 "M半裙" → "M", "Lady80白色" → "Lady80"）
 */
private fun extractLeadingSize(part: String): String? {
    // Lady + 数字
    Regex("^(Lady\\d+)", RegexOption.IGNORE_CASE).find(part)?.let {
        if (it.value.length < part.length) return it.value
    }
    // 标准尺寸字母
    Regex("^(XS|XXL|XXXL|2XL|3XL|XL|S|M|L)", RegexOption.IGNORE_CASE).find(part)?.let {
        if (it.value.length < part.length) return it.value
    }
    return null
}
```

**Step 4: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/file/TaobaoOrderParser.kt
git commit -m "feat(parser): implement smart splitting and classification for style spec"
```

---

### Task 7: Validate parsing improvements with test script

**Files:**
- Modify: `test_parse.py` (already exists at project root)

**Step 1: Update test script to match new algorithm**

Update the Python script `test_parse.py` to replicate the new three-phase algorithm (enhanced noise removal, multi-level splitting, smart classification). Run against all 9 test xlsx files.

Key changes to replicate:
- Enhanced noise removal patterns (matching Task 5)
- Multi-level splitting: primary by `;/`, secondary by spaces and `--`
- Smart classification with `classifyPart()` logic (matching Task 6)
- Updated keyword lists

**Step 2: Run test and compare results**

Run: `python test_parse.py > test_output_new.txt 2>&1`

Compare with previous results. Target metrics:
- 无类型 rate should drop from 79% to < 30%
- 无颜色 rate should drop from 14% to < 10%
- 兜底颜色 rate should drop from 24% to < 15%

**Step 3: Iterate if needed**

If specific patterns still fail, add targeted fixes:
- Missing type keywords → add to `typeKeywords`
- Missing color keywords → add to `colorKeywords`
- New noise patterns → add to cleaning phase

**Step 4: Clean up test files**

```bash
rm test_parse.py test_output.txt test_output_new.txt
git add -u
git commit -m "chore: clean up test scripts"
```

---

### Task 8: Update ViewModel category matching for new parsed types

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt:754-773`

**Step 1: Expand typeMap in matchCategory()**

The new parser will extract more specific type keywords (e.g., "帽子", "发带", "胸针", "衬衫"). Update the `typeMap` to map these to database categories:

```kotlin
private fun matchCategory(parsedType: String?, categories: List<Category>): Category? {
    if (parsedType == null) return null
    // 精确匹配
    categories.firstOrNull { it.name.equals(parsedType, ignoreCase = true) }
        ?.let { return it }
    // 关键词包含匹配（扩展映射）
    val typeMap = mapOf(
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
        "上衣" to listOf("上衣", "胸衣", "蝙蝠胸衣")
    )
    for ((categoryName, keywords) in typeMap) {
        if (keywords.any { parsedType.equals(it, ignoreCase = true) }) {
            categories.firstOrNull { it.name == categoryName }?.let { return it }
        }
    }
    // 后缀匹配兜底
    for ((categoryName, keywords) in typeMap) {
        if (keywords.any { parsedType.contains(it, ignoreCase = true) }) {
            categories.firstOrNull { it.name == categoryName }?.let { return it }
        }
    }
    return null
}
```

**Step 2: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportViewModel.kt
git commit -m "feat(import): expand category matching for new parsed type keywords"
```

---

### Task 9: Final build verification

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Verify no regressions**

Check that all modified files compile without warnings related to our changes.

**Step 3: Final commit (if any remaining changes)**

```bash
git status
# If clean, no action needed
```
