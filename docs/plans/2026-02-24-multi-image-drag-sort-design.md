# 多图片支持 + 穿搭内物品拖拽排序 设计文档

日期：2026-02-24

## 概述

两个用户体验增强功能：
1. 物品（Item）和穿搭组合（Coordinate）支持多张图片（最多 5 张）
2. 穿搭组合内的物品支持拖拽排序

## 功能一：多图片支持

### 方案

将 Item 和 Coordinate 的 `imageUrl: String?` 改为 `imageUrls: List<String>`，用现有 Gson TypeConverter 序列化为 JSON 数组。与 OutfitLog 的 `imageUrls` 字段保持一致。

### 数据层变更

**Item 实体：**
- `imageUrl: String?` → `imageUrls: List<String> = emptyList()`
- 复用现有 `StringListConverter`

**Coordinate 实体：**
- `imageUrl: String?` → `imageUrls: List<String> = emptyList()`

**DB Migration v14→v15：**
- Item 表：将 `image_url` 非空值转为 JSON 数组 `["原值"]`，空值转为 `"[]"`，重命名列为 `image_urls`
- Coordinate 表：同样的转换逻辑
- Item 表：新增 `coordinate_order INTEGER NOT NULL DEFAULT 0`

**BackupManager：**
- 导出/导入适配新字段
- 导入时兼容旧格式（`imageUrl` 单值 → 转为列表）

### UI 层变更

**编辑页面（ItemEditScreen / CoordinateEditScreen）：**
- 单图片区域改为横向可滚动图片列表 + 添加按钮
- 布局：`LazyRow`，每个图片卡片可点击查看大图、长按删除，末尾 "+" 添加按钮
- 图片数量达到 5 张时隐藏添加按钮
- 第一张图片作为封面
- 支持拖拽调整图片顺序

**详情页面（ItemDetailScreen / CoordinateDetailScreen）：**
- 顶部图片区域改为 `HorizontalPager`，支持左右滑动浏览
- 底部加页码指示器（小圆点）
- 点击图片可全屏查看（全屏 Dialog + 左右滑动）
- 只有一张或零张图片时行为不变

**列表页面：**
- 缩略图始终显示 `imageUrls[0]`，无需改动列表布局

## 功能二：穿搭内物品拖拽排序

### 方案

在 Item 表新增 `coordinateOrder: Int = 0` 字段，拖拽后批量更新 order 值。

### 数据层变更

**Item 实体：**
- 新增 `coordinateOrder: Int = 0`

**DAO 变更：**
- `ItemDao`：新增 `updateCoordinateOrders(List<Pair<Long, Int>>)` 批量更新排序
- `CoordinateDao`：`getCoordinateWithItems` 查询结果按 `coordinate_order ASC` 排序

### UI 层变更

**CoordinateDetailScreen：**
- 物品列表支持长按触发拖拽
- 拖拽时物品卡片轻微抬起（elevation + scale），带皮肤对应阴影色
- 松手后自动保存新顺序到数据库
- 实现：`detectDragGesturesAfterLongPress` + `animateItemPlacement`，不引入第三方库

**CoordinateEditScreen：**
- 同样支持拖拽排序，保存时一并写入

**排序逻辑：**
- 新物品加入穿搭时，`coordinateOrder` 设为当前最大值 + 1
- 拖拽交换后重新计算所有物品 order 值（0, 1, 2, ...）
- `CoordinateDetailViewModel` 新增 `reorderItems(fromIndex, toIndex)` 方法

## 影响范围

- 实体：Item, Coordinate
- Migration：v14→v15
- DAO：ItemDao, CoordinateDao
- Repository：ItemRepository, CoordinateRepository
- ViewModel：ItemEditViewModel, CoordinateEditViewModel, CoordinateDetailViewModel
- Screen：ItemEditScreen, ItemDetailScreen, CoordinateEditScreen, CoordinateDetailScreen
- BackupManager：导出/导入兼容
- ImageFileHelper：删除逻辑适配多图片
