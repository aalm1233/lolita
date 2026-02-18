# 智能搭配推荐 + 快捷穿搭记录 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Lolita 服饰管理 App 添加两个核心功能：基于向量相似度的智能搭配推荐，以及桌面 Widget + 简化流程的快捷穿搭记录。

**Architecture:** 智能搭配推荐通过 MatchingEngine 将物品属性编码为向量并计算余弦相似度，结合历史搭配记录加权。快捷穿搭记录使用 Jetpack Glance 实现桌面 Widget，配合简化版记录页面和每日通知提醒。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Jetpack Glance (Widget), AlarmManager, DataStore, Material3

---

## Part 1: 智能搭配推荐

### Task 1: 添加历史搭配查询 DAO 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/OutfitLogDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt`

**Step 1: 在 OutfitLogDao 添加查询所有搭配关系的方法**

在 `OutfitLogDao.kt` 的 `interface OutfitLogDao` 内添加：

```kotlin
@Query("SELECT DISTINCT item_id FROM outfit_item_cross_ref WHERE outfit_log_id IN (SELECT outfit_log_id FROM outfit_item_cross_ref WHERE item_id = :itemId) AND item_id != :itemId")
suspend fun getCoOccurringItemIds(itemId: Long): List<Long>
```

**Step 2: 在 ItemDao 添加按状态获取带分类的物品列表**

在 `ItemDao.kt` 的 `interface ItemDao` 内添加：

```kotlin
@Query("SELECT * FROM items WHERE status = :status AND id != :excludeId ORDER BY updated_at DESC")
suspend fun getOwnedItemsExcluding(status: ItemStatus, excludeId: Long): List<Item>
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/OutfitLogDao.kt app/src/main/java/com/lolita/app/data/local/dao/ItemDao.kt
git commit -m "feat(dao): add co-occurring items query and filtered items query"
```

---

### Task 2: 实现 MatchingEngine 向量编码与余弦相似度

**Files:**
- Create: `app/src/main/java/com/lolita/app/domain/usecase/MatchingEngine.kt`

**Step 1: 创建 MatchingEngine**

```kotlin
package com.lolita.app.domain.usecase

import com.lolita.app.data.local.entity.Item
import kotlin.math.sqrt

data class ItemVector(
    val itemId: Long,
    val vector: DoubleArray
)

data class MatchScore(
    val item: Item,
    val score: Double
)

class MatchingEngine {

    companion object {
        // 预定义的风格列表（与数据库 styles 表对应）
        val STYLES = listOf("甜系", "古典", "哥特", "田园", "中华", "其他")
        // 预定义的颜色列表
        val COLORS = listOf(
            "白色", "黑色", "粉色", "红色", "蓝色", "紫色",
            "绿色", "黄色", "棕色", "米色", "灰色", "酒红",
            "藏蓝", "薄荷", "奶茶", "多色"
        )
        // 预定义的季节列表
        val SEASONS = listOf("春", "夏", "秋", "冬", "四季")
    }

    fun encode(item: Item): ItemVector {
        val styleVec = STYLES.map { if (item.style == it) 1.0 else 0.0 }
        val colorVec = COLORS.map { if (item.color?.contains(it) == true) 1.0 else 0.0 }
        val seasonVec = SEASONS.map { s ->
            if (item.season?.split(",")?.any { it.trim() == s } == true) 1.0 else 0.0
        }
        return ItemVector(item.id, (styleVec + colorVec + seasonVec).toDoubleArray())
    }

    fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) return 0.0
        val dot = a.zip(b).sumOf { (x, y) -> x * y }
        val normA = sqrt(a.sumOf { it * it })
        val normB = sqrt(b.sumOf { it * it })
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (normA * normB)
    }

    fun recommend(
        target: Item,
        candidates: List<Item>,
        coOccurringItemIds: Set<Long>,
        historyBoost: Double = 1.3,
        topN: Int = 5
    ): List<MatchScore> {
        val targetVec = encode(target)
        return candidates.map { candidate ->
            val candidateVec = encode(candidate)
            var score = cosineSimilarity(targetVec.vector, candidateVec.vector)
            if (candidate.id in coOccurringItemIds) {
                score *= historyBoost
            }
            MatchScore(candidate, score)
        }
        .filter { it.score > 0.0 }
        .sortedByDescending { it.score }
        .take(topN)
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/domain/usecase/MatchingEngine.kt
git commit -m "feat: implement MatchingEngine with vector encoding and cosine similarity"
```

---

### Task 3: 实现 RecommendationRepository

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/repository/RecommendationRepository.kt`

**Step 1: 创建 RecommendationRepository**

```kotlin
package com.lolita.app.data.repository

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.OutfitLogDao
import com.lolita.app.data.local.dao.CoordinateDao
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus

class RecommendationRepository(
    private val itemDao: ItemDao,
    private val outfitLogDao: OutfitLogDao,
    private val coordinateDao: CoordinateDao
) {
    suspend fun getOwnedItemsExcluding(itemId: Long): List<Item> {
        return itemDao.getOwnedItemsExcluding(ItemStatus.OWNED, itemId)
    }

    suspend fun getCoOccurringItemIds(itemId: Long): Set<Long> {
        val fromOutfitLogs = outfitLogDao.getCoOccurringItemIds(itemId)
        // Also get items from same coordinate
        val item = itemDao.getItemById(itemId)
        val fromCoordinate = if (item?.coordinateId != null) {
            val coordWithItems = coordinateDao.getCoordinateWithItemsList(item.coordinateId)
            coordWithItems?.items?.map { it.id }?.filter { it != itemId } ?: emptyList()
        } else emptyList()
        return (fromOutfitLogs + fromCoordinate).toSet()
    }

    suspend fun getItemById(itemId: Long): Item? {
        return itemDao.getItemById(itemId)
    }
}
```

**Step 2: 在 AppModule 注册 RecommendationRepository**

在 `app/src/main/java/com/lolita/app/di/AppModule.kt` 添加：

```kotlin
private val _recommendationRepository by lazy {
    RecommendationRepository(database.itemDao(), database.outfitLogDao(), database.coordinateDao())
}
fun recommendationRepository() = _recommendationRepository
```

需要在文件顶部添加 import：
```kotlin
import com.lolita.app.data.repository.RecommendationRepository
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/RecommendationRepository.kt app/src/main/java/com/lolita/app/di/AppModule.kt
git commit -m "feat: add RecommendationRepository with co-occurring items lookup"
```

---

### Task 4: 实现 RecommendationViewModel

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/RecommendationViewModel.kt`

**Step 1: 创建 ViewModel**

```kotlin
package com.lolita.app.ui.screen.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.CategoryGroup
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.repository.CategoryRepository
import com.lolita.app.data.repository.RecommendationRepository
import com.lolita.app.domain.usecase.MatchingEngine
import com.lolita.app.domain.usecase.MatchScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val targetItem: Item? = null,
    val recommendations: Map<String, List<MatchScore>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class RecommendationViewModel(
    private val recommendationRepository: RecommendationRepository =
        com.lolita.app.di.AppModule.recommendationRepository(),
    private val categoryRepository: CategoryRepository =
        com.lolita.app.di.AppModule.categoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    private val matchingEngine = MatchingEngine()

    fun loadRecommendations(itemId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val target = recommendationRepository.getItemById(itemId)
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "物品不存在") }
                        return@launch
                    }
                val candidates = recommendationRepository.getOwnedItemsExcluding(itemId)
                val coOccurring = recommendationRepository.getCoOccurringItemIds(itemId)

                // Get target's category group to exclude same group
                val targetCategory = categoryRepository.getCategoryById(target.categoryId)
                val targetGroup = targetCategory?.group

                // Filter out same category group items
                val filtered = if (targetGroup != null) {
                    val sameCategoryIds = candidates.filter { candidate ->
                        val cat = categoryRepository.getCategoryById(candidate.categoryId)
                        cat?.group == targetGroup
                    }.map { it.id }.toSet()
                    candidates.filter { it.id !in sameCategoryIds }
                } else candidates

                val scores = matchingEngine.recommend(target, filtered, coOccurring)

                // Group by category name
                val grouped = scores.groupBy { score ->
                    val cat = categoryRepository.getCategoryById(score.item.categoryId)
                    cat?.name ?: "其他"
                }

                _uiState.update {
                    it.copy(
                        targetItem = target,
                        recommendations = grouped,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
```

**Step 2: 在 CategoryRepository 添加 getCategoryById（如果不存在）**

检查 `app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt`，如果没有 `getCategoryById` 方法则添加：

```kotlin
suspend fun getCategoryById(id: Long): Category? {
    return categoryDao.getCategoryById(id)
}
```

同样检查 `CategoryDao` 是否有 `getCategoryById`，没有则添加：

```kotlin
@Query("SELECT * FROM categories WHERE id = :id")
suspend fun getCategoryById(id: Long): Category?
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/RecommendationViewModel.kt app/src/main/java/com/lolita/app/data/repository/CategoryRepository.kt app/src/main/java/com/lolita/app/data/local/dao/CategoryDao.kt
git commit -m "feat: add RecommendationViewModel with category-grouped results"
```

---

### Task 5: 实现 RecommendationScreen UI

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/item/RecommendationScreen.kt`

**Step 1: 创建推荐结果展示页面**

```kotlin
package com.lolita.app.ui.screen.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.domain.usecase.MatchScore
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    itemId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToItem: (Long) -> Unit,
    viewModel: RecommendationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.loadRecommendations(itemId)
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("推荐搭配") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                compact = true
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator(color = Pink400)
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(uiState.error ?: "未知错误")
                }
            }
            uiState.recommendations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text("暂无推荐搭配", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.recommendations.forEach { (categoryName, scores) ->
                        item {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(scores) { matchScore ->
                            RecommendationItemCard(
                                matchScore = matchScore,
                                onClick = { onNavigateToItem(matchScore.item.id) }
                            )
                        }
                        item { HorizontalDivider(color = Pink100, thickness = 1.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationItemCard(matchScore: MatchScore, onClick: () -> Unit) {
    LolitaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            if (matchScore.item.imageUrl != null) {
                AsyncImage(
                    model = File(matchScore.item.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Pink100
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("?", color = Pink400)
                    }
                }
            }
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = matchScore.item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchScore.item.style?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    matchScore.item.color?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Score badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Pink400.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${(matchScore.score * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Pink400,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/RecommendationScreen.kt
git commit -m "feat: add RecommendationScreen with grouped item cards"
```

---

### Task 6: 注册导航路由并在 ItemDetailScreen 添加入口

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt`

**Step 1: 在 Screen.kt 添加 Recommendation 路由**

在 `TaobaoImport` 之后添加：

```kotlin
data object Recommendation : Screen {
    override val route = "recommendation/{itemId}"
    fun createRoute(itemId: Long) = "recommendation/$itemId"
}
```

**Step 2: 在 LolitaNavHost.kt 注册 RecommendationScreen**

在导航图中添加 composable 注册（参考其他带 itemId 参数的页面）：

```kotlin
composable(
    route = Screen.Recommendation.route,
    arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
    enterTransition = { fadeIn() + slideInHorizontally { it } },
    exitTransition = { fadeOut() + slideOutHorizontally { it } }
) { backStackEntry ->
    val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
    RecommendationScreen(
        itemId = itemId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToItem = { id -> navController.navigate(Screen.ItemDetail.createRoute(id)) }
    )
}
```

需要添加 import：
```kotlin
import com.lolita.app.ui.screen.item.RecommendationScreen
```

**Step 3: 在 ItemDetailScreen 添加「推荐搭配」按钮**

在 ItemDetailScreen 的参数中添加：
```kotlin
onNavigateToRecommendation: (Long) -> Unit = {}
```

在「创建时间」DetailRow 之前（HorizontalDivider 之后，约 line 471），添加按钮：

```kotlin
// 推荐搭配按钮
if (item.status == ItemStatus.OWNED) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedButton(
            onClick = { onNavigateToRecommendation(item.id) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Pink400),
            border = BorderStroke(1.dp, Pink400)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("推荐搭配")
        }
    }
    HorizontalDivider(color = Pink100, thickness = 1.dp)
}
```

需要添加 import：
```kotlin
import com.lolita.app.data.local.entity.ItemStatus
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.AutoAwesome
```

**Step 4: 在 LolitaNavHost 中传递 onNavigateToRecommendation 回调**

找到 ItemDetailScreen 的 composable 注册处，添加参数：

```kotlin
onNavigateToRecommendation = { itemId ->
    navController.navigate(Screen.Recommendation.createRoute(itemId))
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemDetailScreen.kt
git commit -m "feat: wire up recommendation screen with navigation and detail entry point"
```

**Step 6: 构建验证**

```bash
./gradlew.bat assembleDebug
```

预期：BUILD SUCCESSFUL。如果有编译错误，根据错误信息修复。

---

## Part 2: 快捷穿搭记录

### Task 7: 添加 Glance 依赖

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: 在 dependencies 块中添加 Glance 依赖**

在 `// DataStore Preferences` 之后添加：

```kotlin
// Glance (App Widget)
implementation("androidx.glance:glance-appwidget:1.1.1")
implementation("androidx.glance:glance-material3:1.1.1")
```

**Step 2: Sync & Commit**

```bash
./gradlew.bat dependencies --configuration debugRuntimeClasspath | head -5
git add app/build.gradle.kts
git commit -m "build: add Jetpack Glance dependencies for widget"
```

---

### Task 8: 添加 OutfitLogDao 今日查询方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/OutfitLogDao.kt`
- Modify: `app/src/main/java/com/lolita/app/data/repository/OutfitLogRepository.kt`

**Step 1: 在 OutfitLogDao 添加今日穿搭查询**

```kotlin
@Transaction
@Query("SELECT * FROM outfit_logs WHERE date BETWEEN :dayStart AND :dayEnd LIMIT 1")
suspend fun getOutfitLogByDay(dayStart: Long, dayEnd: Long): OutfitLogWithItems?
```

**Step 2: 在 OutfitLogRepository 添加 getTodayOutfitLog 方法**

```kotlin
suspend fun getTodayOutfitLog(): OutfitLogWithItems? {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val dayStart = calendar.timeInMillis
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
    return outfitLogDao.getOutfitLogByDay(dayStart, dayEnd)
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/OutfitLogDao.kt app/src/main/java/com/lolita/app/data/repository/OutfitLogRepository.kt
git commit -m "feat(dao): add today's outfit log query"
```

---

### Task 9: 实现 QuickOutfitLogViewModel

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/outfit/QuickOutfitLogViewModel.kt`

**Step 1: 创建 ViewModel**

```kotlin
package com.lolita.app.ui.screen.outfit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.OutfitItemCrossRef
import com.lolita.app.data.local.entity.OutfitLog
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.OutfitLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickOutfitLogUiState(
    val items: List<Item> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val note: String = "",
    val showNote: Boolean = false,
    val existingLogId: Long? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class QuickOutfitLogViewModel(
    private val outfitLogRepository: OutfitLogRepository =
        com.lolita.app.di.AppModule.outfitLogRepository(),
    private val itemRepository: ItemRepository =
        com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickOutfitLogUiState())
    val uiState: StateFlow<QuickOutfitLogUiState> = _uiState.asStateFlow()

    init { loadData() }

    private fun loadData() {
        viewModelScope.launch {
            // Load owned items
            itemRepository.getItemsByStatus(ItemStatus.OWNED).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
        viewModelScope.launch {
            // Check if today already has a log
            val todayLog = outfitLogRepository.getTodayOutfitLog()
            if (todayLog != null) {
                _uiState.update {
                    it.copy(
                        existingLogId = todayLog.outfitLog.id,
                        selectedItemIds = todayLog.items.map { item -> item.id }.toSet(),
                        note = todayLog.outfitLog.note
                    )
                }
            }
        }
    }

    fun toggleItem(itemId: Long) {
        _uiState.update {
            val newSet = it.selectedItemIds.toMutableSet()
            if (itemId in newSet) newSet.remove(itemId) else newSet.add(itemId)
            it.copy(selectedItemIds = newSet)
        }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun toggleShowNote() {
        _uiState.update { it.copy(showNote = !it.showNote) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                if (state.selectedItemIds.isEmpty()) {
                    _uiState.update { it.copy(isSaving = false, error = "请至少选择一件物品") }
                    return@launch
                }
                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                if (state.existingLogId != null) {
                    // Update existing
                    val log = OutfitLog(
                        id = state.existingLogId,
                        date = today,
                        note = state.note
                    )
                    outfitLogRepository.updateOutfitLogWithItems(
                        log, state.selectedItemIds.toList()
                    )
                } else {
                    // Create new
                    val log = OutfitLog(date = today, note = state.note)
                    outfitLogRepository.insertOutfitLogWithItems(
                        log, state.selectedItemIds.toList()
                    )
                }
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
```

**Step 2: 确保 OutfitLogRepository 有 insertOutfitLogWithItems 和 updateOutfitLogWithItems**

检查 `OutfitLogRepository.kt`，如果缺少则添加：

```kotlin
suspend fun insertOutfitLogWithItems(log: OutfitLog, itemIds: List<Long>) {
    val logId = outfitLogDao.insertOutfitLog(log)
    itemIds.forEach { itemId ->
        outfitLogDao.insertOutfitItemCrossRef(OutfitItemCrossRef(itemId = itemId, outfitLogId = logId))
    }
}

suspend fun updateOutfitLogWithItems(log: OutfitLog, itemIds: List<Long>) {
    outfitLogDao.updateOutfitLog(log)
    // Remove old cross refs and re-insert
    val existing = outfitLogDao.getAllOutfitItemCrossRefsList().filter { it.outfitLogId == log.id }
    existing.forEach { outfitLogDao.deleteOutfitItemCrossRef(it) }
    itemIds.forEach { itemId ->
        outfitLogDao.insertOutfitItemCrossRef(OutfitItemCrossRef(itemId = itemId, outfitLogId = log.id))
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/QuickOutfitLogViewModel.kt app/src/main/java/com/lolita/app/data/repository/OutfitLogRepository.kt
git commit -m "feat: add QuickOutfitLogViewModel with today-aware save logic"
```

---

### Task 10: 实现 QuickOutfitLogScreen UI

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/screen/outfit/QuickOutfitLogScreen.kt`

**Step 1: 创建简化版穿搭记录页面**

```kotlin
package com.lolita.app.ui.screen.outfit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lolita.app.data.local.entity.Item
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.theme.Pink100
import com.lolita.app.ui.theme.Pink400
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickOutfitLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuickOutfitLogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onNavigateBack()
    }

    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text(if (uiState.existingLogId != null) "编辑今日穿搭" else "记录今日穿搭") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = uiState.selectedItemIds.isNotEmpty() && !uiState.isSaving
                    ) {
                        Text("保存", color = if (uiState.selectedItemIds.isNotEmpty()) Pink400
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                compact = true
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today's date (read-only)
            Surface(
                color = Pink400.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = dateFormat.format(Date()),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Pink400,
                    fontWeight = FontWeight.Medium
                )
            }

            // Collapsible note
            Row(
                modifier = Modifier.clickable { viewModel.toggleShowNote() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("备注", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (uiState.showNote) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.showNote) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = { viewModel.updateNote(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("今天的穿搭心得...") },
                    maxLines = 3
                )
            }

            // Selected count
            Text(
                text = "已选 ${uiState.selectedItemIds.size} 件",
                style = MaterialTheme.typography.labelMedium,
                color = Pink400
            )

            // Error
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // Item grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.items) { item ->
                    QuickItemCard(
                        item = item,
                        isSelected = item.id in uiState.selectedItemIds,
                        onClick = { viewModel.toggleItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickItemCard(item: Item, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Pink400 else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = File(item.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Pink100
                ) {}
            }
            if (isSelected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Pink400
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(2.dp))
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/outfit/QuickOutfitLogScreen.kt
git commit -m "feat: add QuickOutfitLogScreen with grid selection and collapsible note"
```

---

### Task 11: 注册 QuickOutfitLog 路由并添加首页快捷卡片

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListViewModel.kt` (或新建一个轻量 ViewModel)

**Step 1: 在 Screen.kt 添加路由**

在 `Recommendation` 之后添加：

```kotlin
data object QuickOutfitLog : Screen {
    override val route = "quick_outfit_log"
}
```

**Step 2: 在 LolitaNavHost.kt 注册 QuickOutfitLogScreen**

```kotlin
composable(
    route = Screen.QuickOutfitLog.route,
    enterTransition = { fadeIn() + slideInHorizontally { it } },
    exitTransition = { fadeOut() + slideOutHorizontally { it } }
) {
    QuickOutfitLogScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

需要添加 import：
```kotlin
import com.lolita.app.ui.screen.outfit.QuickOutfitLogScreen
```

**Step 3: 在 ItemListViewModel 添加今日穿搭状态**

在 `ItemListUiState` 中添加字段：
```kotlin
val todayOutfitItemImages: List<String?> = emptyList(),
val hasTodayOutfit: Boolean = false,
val todayOutfitLogId: Long? = null
```

在 ViewModel 的 init 块中添加加载逻辑：
```kotlin
viewModelScope.launch {
    val todayLog = com.lolita.app.di.AppModule.outfitLogRepository().getTodayOutfitLog()
    _uiState.update {
        it.copy(
            hasTodayOutfit = todayLog != null,
            todayOutfitLogId = todayLog?.outfitLog?.id,
            todayOutfitItemImages = todayLog?.items?.take(3)?.map { item -> item.imageUrl } ?: emptyList()
        )
    }
}
```

**Step 4: 在 ItemListScreen 添加「今日穿搭」快捷卡片**

在 ItemListScreen 的参数中添加：
```kotlin
onNavigateToQuickOutfit: () -> Unit = {}
```

在 TabRow 之后、category group filter 之前添加：

```kotlin
// 今日穿搭快捷卡片
val todayImages = uiState.todayOutfitItemImages
LolitaCard(
    onClick = onNavigateToQuickOutfit,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.hasTodayOutfit) {
            // Show thumbnails
            todayImages.forEach { imageUrl ->
                if (imageUrl != null) {
                    AsyncImage(
                        model = java.io.File(imageUrl),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("查看今日穿搭", style = MaterialTheme.typography.labelMedium, color = Pink400)
        } else {
            Icon(Icons.Default.AddCircleOutline, contentDescription = null,
                tint = Pink400, modifier = Modifier.size(24.dp))
            Text("记录今日穿搭", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp))
        }
    }
}
```

**Step 5: 在 LolitaNavHost 传递 onNavigateToQuickOutfit 回调**

找到 ItemListScreen 的 composable 注册处，添加：
```kotlin
onNavigateToQuickOutfit = { navController.navigate(Screen.QuickOutfitLog.route) }
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt app/src/main/java/com/lolita/app/ui/screen/item/ItemListViewModel.kt
git commit -m "feat: add quick outfit log route, home card, and today status"
```

---

### Task 12: 实现 Glance Widget

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/widget/OutfitWidget.kt`
- Create: `app/src/main/java/com/lolita/app/ui/widget/OutfitWidgetReceiver.kt`
- Create: `app/src/main/res/xml/outfit_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: 创建 Widget 元数据 XML**

创建 `app/src/main/res/xml/outfit_widget_info.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="3600000"
    android:initialLayout="@layout/widget_loading"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

创建 `app/src/main/res/layout/widget_loading.xml`（Glance 需要 initialLayout）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="加载中..." />
</FrameLayout>
```

在 `app/src/main/res/values/strings.xml` 添加（如果没有此文件则创建）：
```xml
<string name="widget_description">记录今日穿搭</string>
<string name="app_name">我的Lolita</string>
```

**Step 2: 创建 OutfitWidget**

```kotlin
package com.lolita.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lolita.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.*

class OutfitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Query today's outfit from DB
        val outfitLogRepo = com.lolita.app.di.AppModule.outfitLogRepository()
        val todayLog = outfitLogRepo.getTodayOutfitLog()
        val hasOutfit = todayLog != null
        val itemCount = todayLog?.items?.size ?: 0

        provideContent {
            WidgetContent(hasOutfit = hasOutfit, itemCount = itemCount)
        }
    }
}

@Composable
private fun WidgetContent(hasOutfit: Boolean, itemCount: Int) {
    val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)
    val today = dateFormat.format(Date())
    val pink = ColorProvider(android.graphics.Color.parseColor("#FF69B4"))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = today,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (hasOutfit) {
            Text(
                text = "今日已记录 $itemCount 件穿搭",
                style = TextStyle(fontSize = 13.sp, color = pink)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "点击查看/编辑",
                style = TextStyle(fontSize = 12.sp)
            )
        } else {
            Text(
                text = "今天穿了什么？",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pink)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "点击记录今日穿搭",
                style = TextStyle(fontSize = 12.sp)
            )
        }
    }
}
```

**Step 3: 创建 OutfitWidgetReceiver**

```kotlin
package com.lolita.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class OutfitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OutfitWidget()
}
```

**Step 4: 在 AndroidManifest.xml 注册 Widget**

在 `</application>` 之前添加：

```xml
<receiver
    android:name=".ui.widget.OutfitWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/outfit_widget_info" />
</receiver>
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/widget/ app/src/main/res/xml/outfit_widget_info.xml app/src/main/res/layout/widget_loading.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml
git commit -m "feat: add Glance-based outfit recording widget"
```

---

## Part 3: 每日穿搭提醒通知

### Task 13: 在 AppPreferences 添加提醒配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt`

**Step 1: 添加提醒开关和时间偏好**

在 `AppPreferences` 类中添加：

```kotlin
import androidx.datastore.preferences.core.intPreferencesKey

val outfitReminderEnabled: Flow<Boolean> = context.dataStore.data
    .map { it[OUTFIT_REMINDER_ENABLED] ?: false }

val outfitReminderHour: Flow<Int> = context.dataStore.data
    .map { it[OUTFIT_REMINDER_HOUR] ?: 20 }

suspend fun setOutfitReminderEnabled(enabled: Boolean) {
    context.dataStore.edit { it[OUTFIT_REMINDER_ENABLED] = enabled }
}

suspend fun setOutfitReminderHour(hour: Int) {
    context.dataStore.edit { it[OUTFIT_REMINDER_HOUR] = hour }
}
```

在 companion object 中添加：

```kotlin
private val OUTFIT_REMINDER_ENABLED = booleanPreferencesKey("outfit_reminder_enabled")
private val OUTFIT_REMINDER_HOUR = intPreferencesKey("outfit_reminder_hour")
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt
git commit -m "feat: add outfit reminder preferences to AppPreferences"
```

---

### Task 14: 实现每日穿搭提醒调度器和接收器

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/notification/DailyOutfitReminderScheduler.kt`
- Create: `app/src/main/java/com/lolita/app/data/notification/DailyOutfitReminderReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: 创建 DailyOutfitReminderScheduler**

参考 `PaymentReminderScheduler` 的模式：

```kotlin
package com.lolita.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class DailyOutfitReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(hour: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, DailyOutfitReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Repeating daily
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancel() {
        val intent = Intent(context, DailyOutfitReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val REQUEST_CODE = 9999
    }
}
```

**Step 2: 创建 DailyOutfitReminderReceiver**

```kotlin
package com.lolita.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lolita.app.R
import com.lolita.app.ui.MainActivity
import kotlinx.coroutines.runBlocking

class DailyOutfitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Check if today already has a log
        val hasLog = runBlocking {
            try {
                val repo = com.lolita.app.di.AppModule.outfitLogRepository()
                repo.getTodayOutfitLog() != null
            } catch (e: Exception) { false }
        }
        if (hasLog) return // Already recorded, skip notification

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "穿搭提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "每日穿搭记录提醒" }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "quick_outfit_log")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("今天穿了什么？")
            .setContentText("记录一下今天的穿搭吧~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "outfit_reminder"
        private const val NOTIFICATION_ID = 8888
    }
}
```

**Step 3: 在 AndroidManifest.xml 注册接收器**

在 Widget receiver 之后添加：

```xml
<receiver android:name=".data.notification.DailyOutfitReminderReceiver" android:enabled="true" />
```

**Step 4: 在 BootCompletedReceiver 中重新调度穿搭提醒**

找到 `BootCompletedReceiver`，在 onReceive 中添加：

```kotlin
// Reschedule daily outfit reminder if enabled
val appPreferences = AppModule.appPreferences()
runBlocking {
    appPreferences.outfitReminderEnabled.first().let { enabled ->
        if (enabled) {
            val hour = appPreferences.outfitReminderHour.first()
            DailyOutfitReminderScheduler(context).schedule(hour)
        }
    }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/notification/DailyOutfitReminderScheduler.kt app/src/main/java/com/lolita/app/data/notification/DailyOutfitReminderReceiver.kt app/src/main/AndroidManifest.xml app/src/main/java/com/lolita/app/data/notification/BootCompletedReceiver.kt
git commit -m "feat: add daily outfit reminder scheduler and receiver"
```

---

### Task 15: 在设置页添加穿搭提醒配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt`

**Step 1: 添加提醒开关和时间选择**

在 SettingsScreen 的 `// Display settings section` 之前添加：

```kotlin
// 穿搭提醒设置
Spacer(modifier = Modifier.height(8.dp))
Text(
    "穿搭提醒",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp)
)

val outfitReminderEnabled by appPreferences.outfitReminderEnabled.collectAsState(initial = false)
val outfitReminderHour by appPreferences.outfitReminderHour.collectAsState(initial = 20)

SettingsToggleItem(
    title = "每日穿搭提醒",
    description = "每天 ${outfitReminderHour}:00 提醒记录穿搭",
    icon = Icons.Default.Notifications,
    iconTint = Color(0xFFE57373),
    checked = outfitReminderEnabled,
    onCheckedChange = { enabled ->
        coroutineScope.launch {
            appPreferences.setOutfitReminderEnabled(enabled)
            val scheduler = DailyOutfitReminderScheduler(/* context from AppModule */)
            if (enabled) scheduler.schedule(outfitReminderHour)
            else scheduler.cancel()
        }
    }
)
```

需要添加 import：
```kotlin
import androidx.compose.material.icons.filled.Notifications
import com.lolita.app.data.notification.DailyOutfitReminderScheduler
```

注意：需要从 AppModule 获取 context 来创建 scheduler：
```kotlin
val scheduler = DailyOutfitReminderScheduler(com.lolita.app.di.AppModule.context())
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/settings/SettingsScreen.kt
git commit -m "feat: add daily outfit reminder toggle in settings"
```

---

### Task 16: 最终构建验证

**Step 1: 完整构建**

```bash
./gradlew.bat clean assembleDebug
```

预期：BUILD SUCCESSFUL

**Step 2: 检查所有新文件是否已提交**

```bash
git status
```

预期：working tree clean

**Step 3: 如有编译错误，逐一修复并提交**

常见问题检查清单：
- import 是否完整
- AppModule 中新 repository 的依赖是否正确
- Glance 版本兼容性
- DAO 方法返回类型是否匹配
- Navigation 参数类型是否正确
