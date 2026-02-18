# Findings — 代码审计汇总

## 审计概览
- 审计员A（数据层）：17 个问题
- 审计员B（UI/交互层）：19 个问题
- 审计员C（系统集成层）：16 个问题
- 去重后总计：见下方分类

---

## Critical (1)

### C1: PaymentEditScreen 权限检查不阻断保存，导致重复保存
- 来源：审计员B #1
- 文件：`ui/screen/price/PaymentEditScreen.kt:60-78`
- 精确闹钟权限检查弹出对话框后，保存操作仍立即执行。calendarPermissionLauncher 回调中再次调用 performSave() 导致保存执行两次
- 修复：权限检查未通过时阻断保存，仅在权限回调中触发保存

---

## High (9)

### H1: season LIKE 模糊匹配导致误匹配
- 来源：审计员A #1
- 文件：`data/local/dao/ItemDao.kt:83-84`
- `LIKE '%春%'` 会误匹配"初春"等。SeasonRepository 依赖此查询更新/删除 Item 的 season
- 修复：使用精确的逗号分隔匹配模式

### H2: updateItemsSeason/clearItemsSeason 精确匹配与多值字段不兼容
- 来源：审计员A #2
- 文件：`data/local/dao/ItemDao.kt:80-81, 92-93`
- 这些 DAO 方法当前未被调用（Repository 自行实现了逻辑），但存在误用风险
- 修复：删除或修复这些方法

### H3: SeasonRepository 多表操作缺少事务保护
- 来源：审计员A #3
- 文件：`data/repository/SeasonRepository.kt:13-39`
- updateSeason/deleteSeason 涉及 seasons + items 两表，无 withTransaction
- 修复：用 database.withTransaction 包裹

### H4: StyleRepository 多表操作缺少事务保护
- 来源：审计员A #4
- 文件：`data/repository/StyleRepository.kt:13-23`
- 同 H3，且 itemDao 声明为可空类型是隐患
- 修复：用 withTransaction 包裹，itemDao 改为非空

### H5: ItemListScreen 删除失败时 errorMessage 未展示
- 来源：审计员B #2
- 文件：`ui/screen/item/ItemListScreen.kt` + `ItemViewModel.kt:305-313`
- RESTRICT 外键约束导致删除失败时，用户看不到任何错误提示
- 修复：在 ItemListScreen 中添加 errorMessage 的 AlertDialog 展示

### H6: OutfitLogEditScreen DatePickerDialog 点击外部关闭后无限弹出
- 来源：审计员B #3
- 文件：`ui/screen/outfit/OutfitLogEditScreen.kt:205-223`
- 使用 setOnCancelListener 而非 setOnDismissListener，点击外部关闭后 showDatePicker 未重置
- 修复：改用 setOnDismissListener

### H7: PriceEditViewModel loadPrice/loadItemStatus 竞态条件
- 来源：审计员B #4
- 文件：`ui/screen/price/PriceViewModel.kt:96-118`
- 两个独立协程用非原子 _uiState.value = 更新状态，可能互相覆盖
- 修复：使用 _uiState.update { it.copy(...) }

### H8: ItemListViewModel.loadItems 非原子状态更新与 loadPreferences 竞态
- 来源：审计员B #5
- 文件：`ui/screen/item/ItemViewModel.kt:133-153`
- loadItems 用 .value = 而 loadPreferences 用 .update{}，并发时 showTotalPrice 可能被覆盖
- 修复：统一使用 _uiState.update

### H9: SCHEDULE_EXACT_ALARM 在 Android 14+ 不默认授予，提醒静默降级
- 来源：审计员C #6
- 文件：`AndroidManifest.xml:10` + `notification/PaymentReminderScheduler.kt:51-65`
- 首次安装后所有提醒使用不精确闹钟，用户无感知
- 修复：scheduleReminder 返回结果类型，UI 层提示用户授权

---

## Medium (18)

### M1: ItemRepository.deleteItem 无事务保护
- 来源：审计员A #6 | 文件：`data/repository/ItemRepository.kt:36-50`
- 修复：withTransaction 包裹，paymentRepository/priceRepository 改非空

### M2: PaymentRepository.insertPayment 中 updatePayment 可能覆盖数据
- 来源：审计员A #7 | 文件：`data/repository/PaymentRepository.kt:55-57`
- 修复：用专门 DAO 方法只更新 calendarEventId

### M3: PriceDao.getTotalSpending 多 Price 重复计算
- 来源：审计员A #8 | 文件：`data/local/dao/PriceDao.kt:43`
- 修复：明确业务规则，添加唯一索引或只取最新 Price

### M4: Price totalPrice 与 deposit+balance 一致性未在数据库层保证
- 来源：审计员A #9 | 文件：`data/local/entity/Price.kt:24-49`
- 修复：备份恢复时也调用 normalizePrice

### M5: 枚举反序列化静默降级掩盖数据损坏
- 来源：审计员A #10 | 文件：`data/local/converters/Converters.kt:17-58`
- WISHED 被降级为 OWNED 会改变业务含义
- 修复：使用 UNKNOWN 枚举值保留原始状态

### M6: season/style 字段存储纯文本未与表建立外键
- 来源：审计员A #11 | 文件：`data/local/entity/Item.kt:76-80`
- 架构设计问题，导致重命名/删除需手动同步

### M7: ItemDao.updateItemsStyle 对多值 style 字段处理存疑
- 来源：审计员A #5 | 文件：`data/local/dao/ItemDao.kt:77-78`
- 修复：确认 style 是否永远单值，明确注释

### M8: CoordinateEditViewModel.save() 未设置 createdAt/updatedAt
- 来源：审计员B #6 | 文件：`ui/screen/coordinate/CoordinateViewModel.kt:214-230`
- 修复：显式设置 System.currentTimeMillis()

### M9: PaymentManageScreen 未展示 errorMessage
- 来源：审计员B #7 | 文件：`ui/screen/price/PaymentManageScreen.kt`
- 修复：添加 Snackbar 或 AlertDialog

### M10: SettingsScreen 直接用 AppPreferences，写操作可能被取消
- 来源：审计员B #8 | 文件：`ui/screen/settings/SettingsScreen.kt:42-44`
- 修复：创建 SettingsViewModel 用 viewModelScope

### M11: SearchScreen/PaymentCalendarScreen/StatsScreen 独立版为死代码
- 来源：审计员B #9 | 文件：多处
- 修复：删除未使用的 Composable 包装器

### M12: ItemEditScreen loadItem 与 updateStatus 执行顺序依赖不明确
- 来源：审计员B #10 | 文件：`ui/screen/item/ItemEditScreen.kt:58-63`
- 修复：在 loadItem 中增加 defaultStatus 参数

### M13: CoordinateListContent ViewModel 作用域与延迟加载问题
- 来源：审计员B #11 | 文件：`ui/screen/coordinate/CoordinateListScreen.kt:63-67`
- 修复：用 LaunchedEffect 触发加载而非 init

### M14: ItemListScreen LaunchedEffect 缺少 Tab 2 处理
- 来源：审计员B #12 | 文件：`ui/screen/item/ItemListScreen.kt:77-82`
- 修复：添加 else 分支

### M15: TaobaoOrderParser 未关闭 InputStream，workbook 未用 use 保护
- 来源：审计员C #1 | 文件：`data/file/TaobaoOrderParser.kt:19-80`
- 修复：inputStream.use + XSSFWorkbook.use

### M16: BackupManager.importFromJson 静默吞掉所有异常，数据关联错误风险
- 来源：审计员C #3 | 文件：`data/file/BackupManager.kt:158-170`
- 修复：区分 SQLiteConstraintException 和其他异常

### M17: BackupManager.exportToJson 未在事务中读取，备份数据可能不一致
- 来源：审计员C #14 | 文件：`data/file/BackupManager.kt:41-52`
- 修复：用 database.withTransaction 包裹所有查询

### M18: BootCompletedReceiver 两次独立查询存在数据不一致 + CoroutineScope 不受控
- 来源：审计员C #4, #15 | 文件：`data/notification/BootCompletedReceiver.kt:20-41`
- 修复：合并为单次查询；添加 withTimeout(9000) 保护

---

## Low (14)

### L1: CategoryRepository.getCategoryByName 全表加载后内存过滤
- 来源：审计员A #12 | 文件：`data/repository/CategoryRepository.kt:26-29`

### L2: BrandRepository 与 CategoryRepository 删除异常类型不一致
- 来源：审计员A #13 | 文件：`data/repository/BrandRepository.kt:20-24`

### L3: DatabaseCallback.onCreate 与 Migration 预设数据重复定义
- 来源：审计员A #14 | 文件：`data/local/LolitaDatabase.kt`

### L4: OutfitLog/Item 删除时图片清理失败被静默吞掉
- 来源：审计员A #15 | 文件：`data/repository/OutfitLogRepository.kt:34-37`

### L5: Item.updatedAt 在 insertItem 时未刷新
- 来源：审计员A #16 | 文件：`data/repository/ItemRepository.kt:31`

### L6: AppModule lazy 字段直接引用其他 lazy 字段（循环依赖风险）
- 来源：审计员A #17 | 文件：`di/AppModule.kt:30`

### L7: TaobaoImportScreen onNavigateToDetail 为死参数
- 来源：审计员B #13 | 文件：`ui/navigation/LolitaNavHost.kt:387-395`

### L8: 多处 SimpleDateFormat 未用 remember 缓存
- 来源：审计员B #14 | 文件：多处

### L9: PriceManageViewModel.deletePrice 静默吞掉异常
- 来源：审计员B #15 | 文件：`ui/screen/price/PriceViewModel.kt:75-83`

### L10: WishlistScreen FAB 参数命名不清晰
- 来源：审计员B #16 | 文件：`ui/screen/item/WishlistScreen.kt:63-64`

### L11: CoordinateDetailViewModel 方法内直接访问 AppModule
- 来源：审计员B #17 | 文件：`ui/screen/coordinate/CoordinateViewModel.kt:138-143`

### L12: BackupManager.openInputStream 未用 use 保护
- 来源：审计员C #2 | 文件：`data/file/BackupManager.kt:148`

### L13: ImageFileHelper.deleteImage 未验证路径在应用目录内
- 来源：审计员C #7 | 文件：`data/file/ImageFileHelper.kt:42-45`

### L14: cancelReminder 使用 FLAG_UPDATE_CURRENT 而非 FLAG_NO_CREATE
- 来源：审计员C #10 | 文件：`data/notification/PaymentReminderScheduler.kt:85-99`

---

## 补充发现（不影响功能但值得关注）

- CalendarEventHelper.getPrimaryCalendarId 未过滤日历写入权限（审计员C #5）
- ImageFileHelper.downloadFromUrl 缺少 URL scheme 验证（审计员C #8）
- PaymentReminderReceiver 未显式声明 exported=false（审计员C #9）
- TaobaoOrderParser XSSFWorkbook DOM 模式大文件 OOM 风险（审计员C #11）
- BackupManager.createFileInDownloads 在 API 28 以下不可用（审计员C #12）
- NotificationChannelSetup importance 为 DEFAULT（审计员C #13）
- BackupRestoreScreen Snackbar 消息可能被覆盖（审计员B #19）
- OutfitLogEditScreen LazyVerticalGrid 布局问题（审计员B #18）

---

## 交叉审视结论（新审计 vs 旧审计 b9da601 修复后）

### 已修复确认（旧审计问题未再出现）
旧 H-01(BackupData缺Style/Season), H-02(ensurePresetCategories), H-03(onCreate竞态),
H-04(updateTotalPrice协程泄漏), H-05(PaymentCalendar协程泄漏), H-07(重启丢提醒),
H-08(Detail编辑不刷新), H-09(Wishlist默认OWNED), H-10(BrandSelector点击),
H-11(图片提前删除), H-12(颜色条高度0), 及多数旧 Medium/Low 问题

### 修复不完整/复发
| 旧问题 | 新问题 | 说明 |
|--------|--------|------|
| 旧H-06 POST_NOTIFICATIONS | 新H9 SCHEDULE_EXACT_ALARM | 权限体系仍有缺口 |
| 旧M-05 importFromJson无事务 | 新M16 importFromJson吞异常 | 加了事务但异常处理仍有问题 |
| 旧M-20 deleteItem无异常处理 | 新H5 errorMessage未展示 | 加了catch但UI未展示 |
| 旧L-02 style/season无外键 | 新M6+H1+H3+H4 | 架构问题未解决，衍生更多问题 |

### 全新发现（27个）
C1, H1-H8, M1-M5, M7-M15, M17-M18, L1-L14 均为本次新发现
