# Task Plan: 代码审计问题修复

## Goal
按优先级修复 code_audit_report.md 中的 58 个审计问题（12 高 / 26 中 / 20 低）

## Current Phase
Phase 6 complete — 全部完成

## Phases

### Phase 1: 数据丢失/核心功能失效 [complete]
最高优先级，涉及数据丢失和核心功能完全失效的问题。

- [x] **H-01** BackupData 补充 Style/Season 表（BackupManager.kt）
- [x] **H-10** BrandSelector disabled TextField 改为外层 Box clickable（ItemEditScreen.kt）
- [x] **H-11** 图片删除延迟到 saveItem 成功后执行（ItemEditScreen.kt + ItemViewModel.kt）
- [x] **H-06** POST_NOTIFICATIONS 运行时权限请求（PaymentReminderReceiver.kt + PaymentEditScreen.kt）
- [x] **H-07** 注册 BootCompletedReceiver 重启后重建提醒（新文件 + Manifest + PaymentDao）

### Phase 2: 内存泄漏/状态错误 [complete]
协程泄漏、UI 状态不刷新、默认值错误等问题。

- [x] **H-04** updateTotalPrice() 协程泄漏 — 保存 Job 引用（ItemViewModel.kt）
- [x] **H-05** PaymentCalendar loadData() 协程泄漏 — 保存 Job 引用（PaymentCalendarScreen.kt）
- [x] **H-08** ItemDetailScreen 编辑后不刷新 — repeatOnLifecycle RESUMED（ItemDetailScreen.kt）
- [x] **H-09** WishlistScreen FAB 默认状态改为 WISHED（Screen.kt + NavHost + ItemEditScreen）
- [x] **H-12** WishlistScreen 优先级颜色条 — Row 添加 IntrinsicSize.Min（WishlistScreen.kt）
- [x] **H-02** CategoryRepository 配饰类别分组修正（CategoryRepository.kt）
- [x] **H-03** DatabaseCallback.onCreate 同步插入预置数据（LolitaDatabase.kt）

### Phase 3: 备份/导入/日历修复 [complete]
备份恢复、CSV 导出、日历事件、提醒调度相关问题。

- [x] **M-01** insertPayment 日历事件条件判断（PaymentRepository.kt）
- [x] **M-02** updatePayment 已付款不创建日历事件（PaymentRepository.kt）
- [x] **M-05** importFromJson 事务包裹（BackupManager.kt）
- [x] **M-06** CSV 导出补充缺失表 + escapeCsv（BackupManager.kt）
- [x] **M-08** createFileInDownloads null 流处理（BackupManager.kt）
- [x] **M-09** markAsPaid 传递 itemName（PaymentCalendarScreen.kt）
- [x] **M-10** 备份导入后重建提醒调度（BackupManager.kt）
- [x] **M-16** 备份文件缓存解析结果（BackupManager.kt）

### Phase 4: ViewModel/Repository 修复 [complete]
数据层和 ViewModel 逻辑问题。

- [x] **M-03** deleteItem 清理图片文件（ItemRepository.kt）— 已在之前修复
- [x] **M-04** deleteOutfitLog 清理图片文件（OutfitLogRepository.kt）— 已在之前修复
- [x] **M-07** CoordinateRepository 事务内 Flow 改 suspend（CoordinateDao + CoordinateRepository.kt）
- [x] **M-12** 管理页面 loadXxx() 重复调用（Brand/Category ManageScreen + ViewModel）
- [x] **M-17** addCategory 增加 group 参数（CategoryManageViewModel.kt + CategoryManageScreen.kt）
- [x] **M-18** PriceEditViewModel 定金+尾款校验（PriceViewModel.kt）
- [x] **M-19** Price update 同步 Payment（PriceViewModel.kt + PaymentDao + PaymentRepository）
- [x] **M-20** deleteItem/deleteCoordinate 异常处理（ItemViewModel.kt + CoordinateViewModel.kt）
- [x] **M-21** CoordinateEditViewModel createdAt 为 0 检查（CoordinateViewModel.kt）
- [x] **M-25** isValid() 总价 > 0 检查（PriceViewModel.kt）— 与 M-18 合并修复

### Phase 5: UI 修复 [complete]
UI 层面的交互、显示、键盘等问题。

- [x] **M-11** SCHEDULE_EXACT_ALARM 权限 UI 引导（PaymentEditScreen.kt）
- [x] **M-13** 四个管理页面添加编辑功能（Brand/Category/Style/Season ManageScreen + ViewModel）
- [x] **M-14** 添加对话框 trim() 处理（4 个 ManageViewModel）
- [x] **M-15** 添加失败不关闭对话框（4 个 ManageViewModel）
- [x] **M-22** DatePickerCard DisposableEffect（OutfitLogEditScreen.kt）
- [x] **M-23** LazyColumn/LazyGrid items 添加 key（10 处修复）
- [x] **M-24** OutfitLogDetailScreen 移除确认对话框（OutfitLogDetailScreen.kt）
- [x] **M-26** PaymentEditScreen 数字键盘（PaymentEditScreen.kt）
- [x] **L-07** 删除 PLACEHOLDER 注释（StyleManageScreen.kt + SeasonManageScreen.kt）— 顺带修复

### Phase 6: 低优先级修复 [complete]
代码质量、一致性、性能优化等。

- [x] **L-01** getTotalSpending 过滤愿望单物品（PriceDao.kt）
- [x] **L-02** Style/Season 重命名时同步更新 Item 记录（StyleRepository + SeasonRepository + ItemDao）
- [x] **L-03** 移除死代码 Screen.CoordinateList（Screen.kt）
- [x] **L-04** AppModule.backupManager 改 lazy 单例（AppModule.kt）
- [x] **L-05** ImageFileHelper.deleteImage 改 suspend + IO（ImageFileHelper.kt）
- [x] **L-06** payment.id.toInt() 溢出保护（PaymentReminderScheduler.kt + Receiver）
- [x] **L-07** 删除 PLACEHOLDER 注释（StyleManageScreen.kt + SeasonManageScreen.kt）— Phase 5 已修复
- [x] **L-08** BackupRestoreViewModel 改 StateFlow 模式（BackupRestoreScreen.kt）
- [x] **L-09** onFileSelected loading 状态保护（BackupRestoreScreen.kt）— 与 L-08 合并修复
- [ ] **L-10** errorMessage 改 Channel/SharedFlow（4 个 ManageScreen）— 跳过，影响极小
- [x] **L-11** totalPrice 解析失败抛异常（PriceViewModel.kt）
- [x] **L-12** loadCoordinate 防重复调用（CoordinateViewModel.kt）
- [x] **L-13** deleteOutfitLog 改直接按 ID 删除（OutfitLogViewModel.kt + OutfitLogDao + Repository）
- [x] **L-14** deletePrice/deletePayment 异常处理（PriceViewModel.kt）
- [x] **L-15** loadOutfitLog 用 copy 而非全新对象（OutfitLogViewModel.kt）
- [x] **L-16** 日期 TextField 点击打开选择器（PriceEditScreen.kt + PaymentEditScreen.kt）
- [x] **L-17** SimpleDateFormat remember 缓存（PriceManageScreen.kt + ItemDetailScreen.kt）
- [x] **L-18** PaymentEditScreen 移除冗余 OnCancelListener（PaymentEditScreen.kt）
- [x] **L-19** SimpleDateFormat 线程安全（CoordinateListScreen.kt）
- [x] **L-20** ItemEditScreen 保存按钮表单有效性检查（ItemEditScreen.kt）

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| 分 6 个阶段按优先级修复 | 确保数据丢失问题最先解决 |
| H-06/H-07 放 Phase 1 | 通知功能在 Android 13+ 完全失效，影响面大 |
| M-13 编辑功能放 Phase 5 | 功能增强类，非 bug 修复，优先级较低 |
| 低优先级问题单独一个 Phase | 可选择性修复，不阻塞核心修复 |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| (none) | | |
