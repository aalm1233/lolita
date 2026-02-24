# 现有功能优化设计文档

日期：2026-02-24
版本：v2.13 → v2.14

## 概述

对现有功能进行三个方向的优化：数据完整性修复、皮肤一致性补全、错误处理完善。共 7 项修复，按风险分层执行。

## 第一批：数据完整性修复（2 项）

### 1.1 Coordinate 编辑替换图片时清理旧文件

**问题：** `CoordinateRepository.updateCoordinateWithItems()` 更新 Coordinate 时，如果用户更换了图片，旧图片文件留在磁盘上造成泄漏。

**文件：** `CoordinateRepository.kt`

**方案：**
- 在 `updateCoordinateWithItems` 方法中，事务开始前查询旧 Coordinate 的 imageUrl
- 事务成功后，比较新旧 imageUrl，如果不同且旧的非空，调用 `ImageFileHelper.deleteImage()` 清理旧文件
- 同样检查 CoordinateViewModel 的 `update()` 方法中是否有类似问题

**参考模式：** `CoordinateRepository.deleteCoordinate()` 已有的图片清理逻辑（事务外删除）

### 1.2 PriceViewModel.loadPrice paymentDate 逻辑修正

**问题：** `loadPrice()` 用 `minByOrNull { it.createdAt }` 取第一笔 payment 的日期，语义不清晰。

**文件：** `PriceViewModel.kt` 第 110-111 行

**方案：**
- 改为 `minByOrNull { it.dueDate ?: Long.MAX_VALUE }` 按到期日排序
- 更符合"最早需要付款的日期"的业务语义

## 第二批：皮肤一致性补全（3 项）

### 2.1 PriceManageScreen 补全列表动画

**问题：** LazyColumn 缺少 `SkinFlingBehavior` 和 `SkinItemAppear`，与其他列表屏幕不一致。

**文件：** `PriceManageScreen.kt` 第 75-80 行

**方案：**
- 添加 `val flingBehavior = rememberSkinFlingBehavior()` 到 LazyColumn
- LazyColumn 添加 `flingBehavior = flingBehavior` 参数
- 列表项添加 `.skinItemAppear(index)` modifier

**参考模式：** `ItemListScreen.kt` 第 370-400 行

### 2.2 PaymentManageScreen 补全列表动画

**问题：** 同 2.1，LazyColumn 缺少皮肤动画。

**文件：** `PaymentManageScreen.kt` 第 74-80 行

**方案：** 同 2.1，添加 `SkinFlingBehavior` + `SkinItemAppear`

### 2.3 PaymentCalendarScreen Card → LolitaCard + SkinClickable

**问题：** 4 处使用 Material3 `Card`，MonthCard 用 `.clickable()` 而非 `SkinClickable`。

**文件：** `PaymentCalendarScreen.kt`

**修改点：**
- 第 272 行 YearHeader Card → LolitaCard
- 第 378-387 行 MonthCard Card + `.clickable()` → LolitaCard + SkinClickable
- 第 472-477 行 PaymentInfoCard Card → LolitaCard
- 第 240 行 Empty State Card → LolitaCard
- 保持现有颜色逻辑和边框样式

## 第三批：错误处理完善（2 项）

### 3.1 ItemListScreen 删除 FK 错误友好提示

**问题：** 删除物品时如果被 Coordinate 引用（FK RESTRICT），`SQLiteConstraintException` 的原始错误信息直接显示给用户。

**文件：** `ItemViewModel.kt` 第 479-487 行

**方案：**
- 在 `deleteItem` 的 catch 中特判 `android.database.sqlite.SQLiteConstraintException`
- 显示友好提示："此服饰已被套装引用，无法删除。请先从套装中移除后再试。"
- 其他异常保持现有的 `e.message ?: "删除失败"` 逻辑

### 3.2 BackupManager 导入日历事件失败反馈

**问题：** 导入时重建日历事件失败只 `Log.e`，用户无感知。

**文件：** `BackupManager.kt` 第 266-287 行

**方案：**
- 在日历事件重建循环中收集失败数量（`var calendarFailCount = 0`）
- 导入结果中附带日历事件状态，如 "导入完成，但有 N 个日历事件创建失败（可能需要授予日历权限）"
- 修改 `importFromJson` 返回值或回调，将 calendarFailCount 传递给 UI 层

## 执行顺序

1. 数据完整性修复（1.1 → 1.2）
2. 错误处理完善（3.1 → 3.2）
3. 皮肤一致性补全（2.1 → 2.2 → 2.3）

## 版本规划

- versionName: 2.13 → 2.14（功能优化）
- versionCode: 27 → 28
- 完成后执行 `./gradlew.bat assembleRelease` 验证构建
