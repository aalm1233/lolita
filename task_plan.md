# Task Plan: 专业代码审计

## Goal
组建专业代码审计团队，每个成员独立审计完整代码库，发现功能性问题，互相审视后制定修复计划。

## Status: `complete`

## Phase 1: 独立审计（并行）
| 审计员 | 职责范围 | 状态 |
|--------|----------|------|
| 审计员A | 数据层：Room DB、DAO、Repository、Migration、TypeConverter、数据模型 | ✅ 完成 (17问题) |
| 审计员B | UI/交互层：Screen、ViewModel、Navigation、状态管理、用户交互逻辑 | ✅ 完成 (19问题) |
| 审计员C | 系统集成层：权限、日历、通知、备份恢复、淘宝导入、文件处理 | ✅ 完成 (16问题) |

## Phase 2: 交叉审视
- [x] 汇总三位审计员的独立发现
- [x] 去重、分类、评估严重度
- [x] 写入 findings.md

## Phase 3: 修复计划
- [x] 按严重度排序所有确认问题
- [x] 制定修复方案和实施顺序
- [x] 评估修复影响范围

### 修复批次 1: Critical + High（数据正确性 & 核心功能）

| # | 问题 | 文件 | 方案 |
|---|------|------|------|
| C1 | PaymentEditScreen 权限不阻断保存 | PaymentEditScreen.kt | 权限未通过时 return；回调中才保存 |
| H1 | season LIKE 模糊匹配误匹配 | ItemDao.kt | 改用精确逗号分隔匹配 |
| H2 | updateItemsSeason/clearItemsSeason 死代码 | ItemDao.kt | 删除未使用的方法 |
| H3 | SeasonRepository 无事务 | SeasonRepository.kt | 注入 DB，withTransaction |
| H4 | StyleRepository 无事务 | StyleRepository.kt | 注入 DB，withTransaction；itemDao 改非空 |
| H5 | 删除失败 errorMessage 未展示 | ItemListScreen.kt | 添加 AlertDialog |
| H6 | DatePicker 无限弹出 | OutfitLogEditScreen.kt | setOnCancelListener → setOnDismissListener |
| H7 | PriceEditVM 竞态 | PriceViewModel.kt | _uiState.value= → _uiState.update{} |
| H8 | ItemListVM 竞态 | ItemViewModel.kt | 同上，所有 .value= 改 .update{} |
| H9 | SCHEDULE_EXACT_ALARM 静默降级 | PaymentReminderScheduler.kt + UI | 返回结果类型，UI 提示授权 |

### 修复批次 2: Medium（数据一致性 & 资源管理）

| # | 问题 | 文件 | 方案 |
|---|------|------|------|
| M1 | ItemRepository.deleteItem 无事务 | ItemRepository.kt | withTransaction；Repository 改非空 |
| M2 | insertPayment updatePayment 覆盖 | PaymentRepository.kt | 专用 DAO 方法更新 calendarEventId |
| M3 | getTotalSpending 多 Price 重复 | PriceDao.kt | 明确业务规则，加唯一索引或取最新 |
| M4 | Price totalPrice 一致性 | PriceRepository.kt | 备份恢复也调用 normalizePrice |
| M5 | 枚举降级掩盖损坏 | Converters.kt | 添加 UNKNOWN 枚举值 |
| M8 | Coordinate 未设 createdAt | CoordinateViewModel.kt | 显式设置 currentTimeMillis() |
| M9 | PaymentManage 未展示 error | PaymentManageScreen.kt | 添加 Snackbar/AlertDialog |
| M10 | Settings 写操作可能取消 | SettingsScreen.kt | 创建 SettingsViewModel |
| M11 | 死代码 | SearchScreen 等 | 删除未使用 Composable |
| M15 | TaobaoParser 未关闭流 | TaobaoOrderParser.kt | inputStream.use + workbook.use |
| M16 | importFromJson 吞异常 | BackupManager.kt | 区分 ConstraintException 和其他 |
| M17 | exportToJson 无事务 | BackupManager.kt | withTransaction 包裹 |
| M18 | BootReceiver 数据不一致 | BootCompletedReceiver.kt | 合并查询 + withTimeout |

### 修复批次 3: Low（代码质量 & 一致性）
L1-L14 共 14 个问题，按文件就近原则在批次 1/2 修复时顺带处理

---

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
