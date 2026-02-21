# 全功能一致性检查设计文档

日期：2026-02-21

## 背景

对全部功能做一致性检查，确保功能关联性和一致性。通过 5 个维度的深度探索，发现了 4 大类问题（导航系统无问题）。

## 执行方案

按严重度分层修复：数据完整性 → UI 一致性 → ViewModel 模式统一 → 死代码清理。

---

## 第一层：数据完整性修复

### 1.1 备份导入时日历事件丢失

- **文件**: `BackupManager.kt`
- **问题**: 导入时将所有 Payment 的 `calendarEventId` 置 null，日历事件永久丢失
- **修复**: 导入后遍历有 `dueDate` 的未付款 Payment，调用 `CalendarEventHelper.createEvent()` 重建日历事件，并更新 `calendarEventId`

### 1.2 级联删除时日历事件成为孤儿

- **文件**: `PriceRepository.kt`
- **问题**: Price 被删除时 Payment 被 CASCADE 删除，但 `CalendarEventHelper.deleteEvent()` 未被调用
- **修复**: 在 `PriceRepository.deletePrice()` 中，删除前先查询关联的 Payment，逐个清理日历事件和提醒

### 1.3 淘宝导入付款无提醒

- **文件**: `TaobaoImportViewModel.kt`
- **问题**: 创建 Payment 时 `reminderSet` 默认 false，且不调用 `PaymentReminderScheduler`
- **修复**: 导入完成后，对有 `dueDate` 的 Payment 提供选项让用户选择是否设置提醒

### 1.4 备份导入不清理旧图片

- **文件**: `BackupManager.kt`
- **问题**: `clearAllTables()` 后旧图片文件残留在 `filesDir/images/`
- **修复**: 在 `clearAllTables()` 前清空 `images/` 目录

### 1.5 CSV 导出缺少新字段

- **文件**: `BackupManager.kt`
- **问题**: Item CSV 缺少 `color`、`season`、`style`、`size`、`sizeChartImageUrl`；Coordinate CSV 缺少 `imageUrl`
- **修复**: 补全 CSV 导出的列定义和数据写入

### 1.6 BootCompletedReceiver 提醒清理

- **文件**: `BootCompletedReceiver.kt`
- **问题**: 已付款的 Payment 如果在重启前未取消提醒，重启后孤儿闹钟仍会触发
- **修复**: 确认查询条件只重新调度 `isPaid = false && reminderSet = true` 的提醒

---

## 第二层：UI 组件一致性

### 2.1 Card → LolitaCard

- `PriceManageScreen`、`PaymentManageScreen`、`OutfitLogDetailScreen` 中的 `Card()` 替换为 `LolitaCard()`

### 2.2 空列表 → EmptyState

- `FilteredItemListScreen`、`RecommendationScreen`、`PaymentManageScreen` 中的 `Text("暂无数据")` 替换为 `EmptyState()` 组件

### 2.3 补充 SwipeToDeleteContainer

- `WishlistScreen`、`OutfitLogListScreen`、`PriceManageScreen`、`PaymentManageScreen` 添加滑动删除

### 2.4 补充加载状态

- `WishlistScreen`、`PriceManageScreen`、`PaymentManageScreen` 添加 `CircularProgressIndicator`

### 2.5 硬编码颜色 → 主题色

- `SwipeToDeleteContainer` 中 `Color(0xFFFF5252)` → `MaterialTheme.colorScheme.error`
- `SettingsScreen` 图标颜色保留（功能区分色）
- `WishlistScreen` 优先级颜色保留（语义色）

---

## 第三层：ViewModel-Screen 模式统一

### 3.1 PaymentEditScreen 补充 UnsavedChangesHandler

- **文件**: `PaymentEditScreen.kt`
- ViewModel 已有 `hasUnsavedChanges`，Screen 中添加 `UnsavedChangesHandler` 调用

### 3.2 统一错误处理

- 编辑屏幕统一使用 ViewModel 的 `uiState.error` + `AlertDialog`
- 管理屏幕保持 `SnackbarHost`（非阻塞错误）
- `CoordinateEditScreen` 从 SnackbarHost 改为 AlertDialog

### 3.3 统一保存逻辑

- `ItemEditScreen` 从回调模式改为 `Result<T>` 模式

### 3.4 统一验证方式

- `ItemEditScreen` 和 `CoordinateEditScreen` 的内联验证改为调用 `viewModel.isValid()`

---

## 第四层：死代码清理

### 4.1 移除未使用的 DAO 方法

- `ItemDao`: `getItemsByCoordinate()`、`getItemsByBrand()`、`getItemsByCategory()`
- `StyleDao`: `getPresetStyles()`
- `SeasonDao`: `getPresetSeasons()`

### 4.2 移除未使用的 Repository 方法

- `BrandRepository`: `getPresetBrands()`
- `CategoryRepository`: `getPresetCategories()`
