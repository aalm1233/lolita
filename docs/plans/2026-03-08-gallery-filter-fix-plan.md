# 小红书视图过滤修正实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修正小红书视图(Gallery View)使其正确遵循当前标签页的过滤状态,不再显示愿望单数据在服饰标签页中

**Architecture:** 在 ItemListViewModel 中添加 buildGalleryCardDataList() 辅助方法,基于 filteredItems 而非 itemCardDataList 构建 gallery 数据,确保 gallery 视图完全遵循当前的所有过滤条件

**Tech Stack:** Kotlin, Jetpack Compose, MVVM, StateFlow

---

## Task 1: 添加 buildGalleryCardDataList 辅助方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:289-307`

**Step 1: 在 buildItemCardDataList 方法后添加新的辅助方法**

在 ItemListViewModel 类中,在 `buildItemCardDataList()` 方法(line 289-307)之后添加新方法:

```kotlin
private fun buildGalleryCardDataList(
    filteredItems: List<Item>,
    brandNames: Map<Long, String>,
    brandLogoUrls: Map<Long, String?>,
    categoryNames: Map<Long, String>,
    itemPrices: Map<Long, Double>,
    showPrice: Boolean
): List<ItemCardData> {
    return filteredItems
        .filter { it.imageUrls.isNotEmpty() }
        .map { item ->
            ItemCardData(
                item = item,
                brandName = brandNames[item.brandId],
                brandLogoUrl = brandLogoUrls[item.brandId],
                categoryName = categoryNames[item.categoryId],
                itemPrice = itemPrices[item.id],
                showPrice = showPrice
            )
        }
        .shuffled()
}
```

**Step 2: 验证代码语法**

确认新方法添加在正确位置,参数类型与现有代码一致。

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "feat: add buildGalleryCardDataList helper method

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 修改 loadPreferences 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:189-207`

**Step 1: 修改 viewMode 监听逻辑**

将 line 195-206 的代码替换为:

```kotlin
    private fun loadPreferences() {
        viewModelScope.launch {
            appPreferences.showTotalPrice.collect { show ->
                _uiState.update { it.copy(showTotalPrice = show, itemCardDataList = buildItemCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, show)) }
            }
        }
        viewModelScope.launch {
            appPreferences.viewMode.collect { mode ->
                _uiState.update {
                    it.copy(
                        viewMode = mode,
                        galleryCardDataList = if (mode == ViewMode.GALLERY) {
                            buildGalleryCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                        } else emptyList()
                    )
                }
            }
        }
    }
```

**Step 2: 验证修改**

确认替换后的代码使用了新的 `buildGalleryCardDataList()` 方法,并传入 `filteredItems` 而非 `itemCardDataList`。

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: use filteredItems in loadPreferences for gallery view

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 修改 loadItems 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:209-263`

**Step 1: 修改 galleryCardDataList 构建逻辑**

将 line 251-257 的代码替换为:

```kotlin
                        galleryCardDataList = if (it.viewMode == ViewMode.GALLERY) {
                            buildGalleryCardDataList(sorted, data.brandMap, data.brandLogoMap, data.categoryMap, data.priceMap, currentState.showTotalPrice)
                        } else emptyList()
```

**Step 2: 验证修改**

确认新代码直接使用 `sorted` (即 filteredItems)构建 gallery 数据,移除了原有的 `isEmpty()` 检查逻辑。

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: use filteredItems in loadItems for gallery view

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 修改 setViewMode 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:505-515`

**Step 1: 修改视图模式切换逻辑**

将 line 505-515 的代码替换为:

```kotlin
    fun setViewMode(mode: ViewMode) {
        _uiState.update {
            it.copy(
                viewMode = mode,
                galleryCardDataList = if (mode == ViewMode.GALLERY) {
                    buildGalleryCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice)
                } else emptyList()
            )
        }
        viewModelScope.launch { appPreferences.setViewMode(mode) }
    }
```

**Step 2: 验证修改**

确认使用 `it.filteredItems` 而非 `it.itemCardDataList` 构建 gallery 数据。

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: use filteredItems in setViewMode for gallery view

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 修改 shuffleGalleryItems 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:517-521`

**Step 1: 修改刷新逻辑**

将 line 517-521 的代码替换为:

```kotlin
    fun shuffleGalleryItems() {
        _uiState.update {
            it.copy(galleryCardDataList = buildGalleryCardDataList(it.filteredItems, it.brandNames, it.brandLogoUrls, it.categoryNames, it.itemPrices, it.showTotalPrice))
        }
    }
```

**Step 2: 验证修改**

确认使用 `it.filteredItems` 而非 `it.itemCardDataList` 重新构建并打乱 gallery 数据。

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt
git commit -m "fix: use filteredItems in shuffleGalleryItems

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 手动测试验证

**Step 1: 构建并安装应用**

```bash
./gradlew.bat assembleDebug
```

安装生成的 APK 到测试设备。

**Step 2: 测试服饰标签页**

1. 打开应用,进入"服饰"标签页
2. 切换到小红书视图(Gallery View)
3. 验证:只显示 OWNED 状态的物品,不显示愿望单物品

**Step 3: 测试愿望单标签页**

1. 切换到"愿望单"标签页
2. 切换到小红书视图
3. 验证:只显示 WISHLIST 状态的物品

**Step 4: 测试其他过滤条件**

1. 在服饰标签页应用季节/风格/颜色过滤
2. 切换到小红书视图
3. 验证:gallery 视图正确反映过滤结果

**Step 5: 测试刷新功能**

1. 在小红书视图中点击刷新按钮
2. 验证:物品顺序打乱,但不会引入其他状态的物品

**Step 6: 记录测试结果**

如果所有测试通过,继续下一步。如果发现问题,返回修改代码。

---

## Task 7: 构建 Release 版本

**Step 1: 清理并构建 Release APK**

```bash
./gradlew.bat clean assembleRelease
```

**Step 2: 验证构建成功**

确认 `app/build/outputs/apk/release/` 目录下生成了 APK 文件。

**Step 3: 最终 Commit**

```bash
git add -A
git commit -m "chore: bump version for gallery filter fix

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

注意:根据 CLAUDE.md,每次功能更新需要在 `app/build.gradle.kts` 中递增 versionCode 和更新 versionName。如果需要版本更新,请在此步骤前修改版本号。

---

## 完成

所有修改已完成并测试通过。小红书视图现在正确遵循当前标签页的过滤状态。
