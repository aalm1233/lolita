# 小红书视图过滤修正设计

## 问题描述

当前小红书视图(Gallery View)没有正确过滤愿望单数据。用户在"服饰"标签页切换到小红书视图时,仍然会看到愿望单(WISHLIST)状态的物品。

## 问题根源

`ItemListViewModel` 中 `galleryCardDataList` 的构建逻辑使用了 `itemCardDataList`,而 `itemCardDataList` 是基于 `filteredItems` 构建的。但在构建 gallery 数据时,代码直接使用了 `itemCardDataList` 而没有考虑当前的状态过滤。

当前问题代码位置:
- `loadPreferences()` line 200-202
- `loadItems()` line 251-257
- `setViewMode()` line 509-510
- `shuffleGalleryItems()` line 519

## 期望行为

小红书视图应该遵循当前标签页的过滤状态:
- 在"服饰"标签页(status = OWNED):只显示已拥有的物品
- 在"愿望单"标签页(status = WISHLIST):只显示愿望单物品
- 在其他标签页:遵循对应的状态过滤

## 解决方案

### 方案选择

采用**直接使用 filteredItems** 的方案,因为:
1. 代码改动最小
2. `filteredItems` 已经包含了所有过滤条件(状态、分类、季节、风格、颜色、品牌、搜索)
3. Gallery 只需要额外筛选"有图片"的物品
4. 性能好,不需要额外的过滤逻辑

### 实现细节

**1. 创建辅助方法**

添加 `buildGalleryCardDataList()` 方法:
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

**2. 修改四处调用点**

- `loadPreferences()`: 当 viewMode 切换到 GALLERY 时调用辅助方法
- `loadItems()`: 初始加载时,如果是 GALLERY 模式调用辅助方法
- `setViewMode()`: 用户切换到 GALLERY 视图时调用辅助方法
- `shuffleGalleryItems()`: 用户点击刷新时调用辅助方法

### 数据流

```
所有物品 (items)
  ↓
应用过滤条件 (applyFilters)
  ↓
filteredItems (包含状态、分类、季节、风格、颜色、品牌、搜索过滤)
  ↓
筛选有图片的物品 (filter { imageUrls.isNotEmpty() })
  ↓
构建 ItemCardData
  ↓
打乱顺序 (shuffled)
  ↓
galleryCardDataList
```

## 影响范围

- 文件: `app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt`
- 修改方法: `loadPreferences()`, `loadItems()`, `setViewMode()`, `shuffleGalleryItems()`
- 新增方法: `buildGalleryCardDataList()`

## 测试验证

1. 在"服饰"标签页切换到小红书视图,验证只显示 OWNED 状态的物品
2. 在"愿望单"标签页切换到小红书视图,验证只显示 WISHLIST 状态的物品
3. 应用其他过滤条件(季节、风格、颜色等),验证小红书视图正确反映过滤结果
4. 点击刷新按钮,验证打乱顺序功能正常且不会引入其他状态的物品
