# 我的Lolita - 项目规划

## 项目概述
一款用于管理个人Lolita服饰信息的安卓手机APP，支持添加已购买或想要购买的Lolita服饰信息。

---

## 历史迭代: Phase 6 - 功能修复与增强 ✅ 已完成

### 需求概述
1. **修复**: 价格信息直接显示在衣服详情页（当前只有"点击图标管理"的提示，没有实际价格数据）
2. **修复**: 价格录入日期功能丢失（Price实体缺少录入日期字段，PriceEditScreen无日期选择器）
3. **改造**: 套装从设置页移到底部导航新页签，支持勾选多件衣服直接创建套装
4. **新增**: 衣服新增颜色、季节、风格三个字段

---

### Phase 6 任务列表

#### Task 1: 衣服详情页显示价格信息 [修复]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- ItemDetailScreen 当前只显示"点击图标管理此服饰的价格和付款信息"提示文字
- 需要在详情页直接展示该服饰的价格摘要（总价、付款状态等）

**实施方案**:
- ItemEditViewModel 中加载该 item 的价格数据 (通过 PriceRepository.getPricesWithPaymentsByItem)
- ItemEditUiState 新增 prices 字段
- ItemDetailScreen 的"价格信息"区域直接显示价格摘要（总价、类型、已付/未付）
- 保留"管理价格"按钮用于跳转到完整价格管理页

**涉及文件**:
- `ui/screen/item/ItemViewModel.kt` (ItemEditUiState + ItemEditViewModel)
- `ui/screen/item/ItemDetailScreen.kt`

---

#### Task 2: 价格录入日期功能 [修复]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- Price 实体只有 created_at（自动生成），没有用户可编辑的"录入日期/购买日期"字段
- PriceEditScreen 没有日期选择器

**实施方案**:
- Price 实体新增 `purchase_date` 字段 (Long?, 可选)
- 数据库版本升级 version 1→2，添加 Migration
- PriceEditUiState 新增 purchaseDate 字段
- PriceEditViewModel 新增 updatePurchaseDate 方法
- PriceEditScreen 添加日期选择器
- PriceManageScreen 的 PriceCard 显示购买日期
- ItemDetailScreen 价格摘要也显示购买日期

**涉及文件**:
- `data/local/entity/Price.kt`
- `data/local/LolitaDatabase.kt` (Migration)
- `ui/screen/price/PriceViewModel.kt` (PriceEditUiState + PriceEditViewModel)
- `ui/screen/price/PriceEditScreen.kt`
- `ui/screen/price/PriceManageScreen.kt`

---

#### Task 3: 套装移至底部导航新页签 + 勾选创建 [改造]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- 当前套装管理在设置页中，需要移到底部导航栏作为独立页签
- 当前底部导航有5个Tab: 服饰/愿望单/穿搭/搜索/设置
- 需要替换或新增一个Tab给套装
- 创建套装时需要支持勾选多件衣服

**实施方案**:
- 底部导航栏: 将"搜索"Tab替换为"套装"Tab（搜索功能移到设置页或服饰页顶部搜索栏）
- 底部导航: 服饰 / 愿望单 / 套装 / 穿搭 / 设置
- 设置页移除"套装管理"入口，新增"搜索"入口
- CoordinateEditScreen 改造: 新增服饰勾选列表
  - 加载所有已拥有的服饰列表
  - 支持 Checkbox 多选
  - 保存时同时更新选中服饰的 coordinate_id
- CoordinateEditViewModel 改造:
  - 新增 allItems / selectedItemIds 状态
  - save/update 时批量更新 item.coordinateId

**涉及文件**:
- `ui/navigation/LolitaNavHost.kt` (底部导航栏修改)
- `ui/screen/settings/SettingsScreen.kt` (移除套装入口，新增搜索入口)
- `ui/screen/coordinate/CoordinateEditScreen.kt` (新增服饰勾选)
- `ui/screen/coordinate/CoordinateViewModel.kt` (CoordinateEditViewModel 改造)

---

#### Task 4: 衣服新增颜色、季节、风格字段 [新增]
**状态**: ✅ 已完成
**优先级**: P1

**实施方案**:
- Item 实体新增三个字段:
  - `color` (String?, 可选) — 颜色
  - `season` (String?, 可选) — 季节 (春/夏/秋/冬/四季)
  - `style` (String?, 可选) — 风格 (甜系/古典/哥特/田园/中华/其他)
- 数据库 Migration 添加三列
- ItemEditUiState 新增三个字段
- ItemEditViewModel 新增三个 update 方法
- ItemEditScreen 新增三个选择器/输入框
- ItemDetailScreen 显示这三个字段
- ItemListScreen 卡片可选显示颜色/风格标签

**涉及文件**:
- `data/local/entity/Item.kt`
- `data/local/LolitaDatabase.kt` (Migration)
- `ui/screen/item/ItemViewModel.kt`
- `ui/screen/item/ItemEditScreen.kt`
- `ui/screen/item/ItemDetailScreen.kt`
- `ui/screen/item/ItemListScreen.kt` (可选)

---

## 当前迭代: Phase 7 - 代码审查问题修复

### 审查概况
四位审核员并行审查 + 交叉验证，共发现 17 严重 / 28 中等 / 20+ 建议

---

### P0 — 立即修复（数据安全）

#### Task 7.1: 移除 fallbackToDestructiveMigration [严重]
**状态**: ✅ 已修复
**文件**: `data/local/LolitaDatabase.kt:62`
**问题**: 找不到Migration路径时会清空所有用户数据，纯本地APP不可接受
**修复**: 移除 `fallbackToDestructiveMigration()`，设置 `exportSchema = true`

#### Task 7.2: 所有DAO的insert改为ABORT策略 [严重]
**状态**: ✅ 已修复
**文件**: `ItemDao.kt:52`, `CoordinateDao.kt:24`, `PriceDao.kt:25`, `PaymentDao.kt:29`, `OutfitLogDao.kt:29`
**问题**: REPLACE策略会先DELETE再INSERT，触发CASCADE级联删除关联数据
**修复**: 全部改为 `OnConflictStrategy.ABORT`

#### Task 7.3: 补全所有删除确认对话框 [严重]
**状态**: ✅ 已修复
**文件**:
- `BrandManageScreen.kt:92` — 删除按钮跳过确认，直接执行（确认对话框已实现但未调用）
- `CategoryManageScreen.kt:92` — 同上
- `ItemEditScreen.kt:217-234` — 删除无确认
- `PriceManageScreen.kt:83` — 删除价格无确认（会级联删除Payment）
- `PaymentManageScreen.kt:114-117` — 删除付款无确认
**修复**: Brand/Category改为调用showDeleteConfirm；其余添加确认对话框

#### Task 7.4: save/update操作加事务保护 [严重]
**状态**: ✅ 已修复
**文件**:
- `CoordinateViewModel.kt:159-178` — save() 插入Coordinate+更新Item不在事务中
- `CoordinateViewModel.kt:181-211` — update() 同上
- `OutfitLogViewModel.kt:233-281` — save() 操作CrossRef不在事务中
**修复**: 使用 `database.withTransaction { }` 包裹

#### Task 7.5: 签名密码移到local.properties [严重]
**状态**: ✅ 已修复
**文件**: `app/build.gradle.kts:21-26`
**问题**: storePassword/keyPassword明文硬编码，提交公开仓库会泄露
**修复**: 移到 `local.properties`，通过 `project.properties[]` 读取

---

### P1 — 尽快修复（功能缺陷）

#### Task 7.6: 修复Flow collect竞态和isLoading卡死 [严重]
**状态**: ✅ 已修复
**文件**:
- `CoordinateViewModel.kt:136-141` — loadCoordinate用.collect持续监听，覆盖originalSelectedItemIds
- `ItemViewModel.kt:151-203` — 三个并发collect竞态，item为null时isLoading卡死
- `OutfitLogViewModel.kt:183-196` — 创建全新UiState对象而非copy，竞态风险
**修复**: collect改.first()；使用_uiState.update{}原子操作；item为null时也设isLoading=false

#### Task 7.7: Brand/Category删除前检查引用 [中等]
**状态**: ✅ 已修复
**文件**: `BrandRepository.kt`, `CategoryRepository.kt`
**问题**: 有Item引用时删除会抛SQLiteConstraintException崩溃
**修复**: 删除前查询是否有Item引用

#### Task 7.8: 套装编辑标识已关联其他套装的服饰 [中等]
**状态**: ✅ 已修复
**文件**: `CoordinateEditScreen.kt:138-183`
**问题**: 已属于其他套装的服饰可被勾选，会"偷走"别人的服饰且无提示
**修复**: 显示已关联套装名称或给出警告

#### Task 7.9: 补全错误处理和对话框状态 [中等]
**状态**: ✅ 已修复
**文件**:
- `CoordinateEditScreen.kt:53-62` — 保存失败无反馈（onFailure未处理）
- `ItemDetailScreen.kt:76-82` — 删除失败时确认对话框不关闭
**修复**: 添加onFailure处理；删除前先关闭对话框

#### Task 7.10: SearchScreen和StatsScreen添加返回按钮 [严重]
**状态**: ✅ 已修复
**文件**: `SearchScreen.kt:62-66`, `StatsScreen.kt:91-96`
**问题**: 从设置页进入，只能靠系统返回键
**修复**: 添加onBack参数和返回按钮

---

### P2 — 版本内修复（体验优化）

#### Task 7.11: 暗色模式适配 [中等]
**状态**: ✅ 已修复
**文件**: `LolitaNavHost.kt:99`, `GradientTopAppBar.kt:22`, `Theme.kt:41-62`
**问题**: 底部导航栏硬编码白色、渐变TopAppBar固定颜色、DarkColors缺surfaceVariant
**修复**: 使用MaterialTheme.colorScheme.surface；根据暗色模式切换渐变色

#### Task 7.12: Android 15兼容性 [中等]
**状态**: ✅ 已修复
**文件**: `Theme.kt:75`, `MainActivity.kt`
**问题**: window.statusBarColor在API 35已弃用，缺少enableEdgeToEdge()
**修复**: 添加enableEdgeToEdge()，移除statusBarColor设置

#### Task 7.13: 替换弃用API [中等]
**状态**: ✅ 已修复
**问题**: 全项目Icons.Default.ArrowBack已弃用；enum.values()应改entries
**修复**: 批量替换

---

## 关键决策记录

| 日期 | 决策内容 | 理由 |
|------|----------|------|
| 2025-02-12 | 个人独立开发模式 | 项目规模适合，简化协作流程 |
| 2025-02-12 | 使用Jetpack Compose | 现代化UI框架，学习资源丰富 |
| 2025-02-12 | 仅本地存储 | 无需服务器，降低复杂度 |
| 2025-02-12 | UI风格：Lolita甜美风格 | 符合目标用户审美 |
| 2026-02-13 | 套装替换搜索Tab | 底部导航5个位置已满，套装比搜索更高频，搜索移到设置页 |
| 2026-02-13 | 颜色/季节/风格用可选字段 | 不强制填写，降低录入门槛 |
| 2026-02-13 | 数据库Migration而非destructive | 保留用户已有数据 |

---

## 当前迭代: Phase 8 - 前端界面设计优化

### 需求概述
1. **按钮样式与图标**: 给所有按钮添加统一样式和图标，提升可操作性
2. **套装展示优化**: 套装列表卡片信息太少，详情页服饰展示单调
3. **服饰页展示优化**: 服饰列表卡片信息密度不够，详情页布局可改进
4. **Title占比过高**: GradientTopAppBar在列表页占用过多垂直空间，减少title区域高度

---

### Phase 8 任务列表

#### Task 8.1: 缩小TopAppBar高度，减少title占比 [优化]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- GradientTopAppBar使用标准Material3 TopAppBar，默认高度64dp
- 在列表页（服饰/套装/穿搭/愿望单/设置）title区域占比过高
- 详情页有返回按钮+标题+操作按钮，高度合理，不需要改

**实施方案**:
- 列表页TopAppBar: 改用MediumTopAppBar的紧凑模式或自定义高度
- 方案: 在GradientTopAppBar中添加compact参数，compact=true时使用更小的padding和字体
- 列表页title字体从titleLarge降为titleMedium
- 减少TopAppBar内部垂直padding

**涉及文件**:
- `ui/screen/common/GradientTopAppBar.kt`
- 所有使用GradientTopAppBar的列表页Screen

---

#### Task 8.2: 服饰列表页(ItemListScreen)展示优化 [优化]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- ItemCard缩略图72dp偏小，无图片时占位符不够美观
- 品牌/类型标签样式可以更精致
- 缺少颜色/风格等新字段的展示
- 筛选栏FilterChip没有图标

**实施方案**:
- FilterChip添加leadingIcon（全部用GridView图标，已拥有用Checkmark，愿望单用Heart）
- ItemCard缩略图增大到80dp，无图片占位符改为服饰类型首字母+渐变背景
- 品牌/类型标签前添加小图标（品牌用Store图标，类型用Label图标）
- 如有颜色字段，在卡片上显示颜色圆点
- 状态badge添加图标（已拥有用Check，愿望单用Heart）

**涉及文件**:
- `ui/screen/item/ItemListScreen.kt`

---

#### Task 8.3: 服饰详情页(ItemDetailScreen)展示优化 [优化]
**状态**: ✅ 已完成
**优先级**: P1

**问题分析**:
- 无图片时占位区域200dp太高，浪费空间
- DetailRow样式单调，label和value之间缺少视觉区分
- 价格信息区域"管理价格"按钮只有Info图标，不够直观

**实施方案**:
- 无图片占位区域缩小到120dp，添加相机图标
- DetailRow添加左侧小图标（品牌→Store，类型→Label，套装→Star，颜色→Palette，季节→WbSunny，风格→Style）
- "管理价格"按钮改为带文字的OutlinedButton: "管理价格 >"
- 删除按钮添加文字确认提示样式

**涉及文件**:
- `ui/screen/item/ItemDetailScreen.kt`

---

#### Task 8.4: 套装列表页(CoordinateListScreen)展示优化 [优化]
**状态**: ✅ 已完成
**优先级**: P0

**问题分析**:
- CoordinateCard信息太少，只有名称+描述+件数
- 没有展示套装内服饰的缩略图预览
- 卡片视觉层次不够丰富

**实施方案**:
- CoordinateCard添加服饰缩略图预览行（最多显示4个小圆形头像，多余显示+N）
- 需要ViewModel提供每个套装的服饰图片列表
- 卡片底部添加创建时间
- 空套装显示"点击添加服饰"提示

**涉及文件**:
- `ui/screen/coordinate/CoordinateListScreen.kt`
- `ui/screen/coordinate/CoordinateViewModel.kt` (CoordinateListViewModel需提供服饰图片数据)
- `data/local/dao/ItemDao.kt` (可能需要新查询)

---

#### Task 8.5: 套装详情页(CoordinateDetailScreen)展示优化 [优化]
**状态**: ✅ 已完成
**优先级**: P1

**问题分析**:
- CoordinateItemCard只显示名称+描述+状态，没有缩略图
- 信息卡片(CoordinateInfoCard)样式单调
- 移除按钮只有Delete图标，容易误触

**实施方案**:
- CoordinateItemCard添加服饰缩略图（48dp，左侧）
- CoordinateItemCard添加品牌/类型标签
- CoordinateInfoCard添加装饰图标
- 移除按钮改为SwipeToDismiss或添加确认文字

**涉及文件**:
- `ui/screen/coordinate/CoordinateDetailScreen.kt`
- `ui/screen/coordinate/CoordinateViewModel.kt` (需要加载品牌/类型名称)

---

#### Task 8.6: 全局按钮样式统一与图标添加 [优化]
**状态**: ✅ 已完成
**优先级**: P1

**问题分析**:
- AlertDialog的确认/取消按钮只有文字，没有图标
- 编辑页的保存按钮样式不统一
- 设置页菜单项已有图标（做得好），但其他页面的操作按钮缺少图标

**实施方案**:
- 所有AlertDialog确认按钮添加图标（删除→Delete，确认→Check，取消→Close）
- 编辑页保存按钮统一为FilledButton + Check图标
- 底部导航图标优化：套装从Star改为更贴切的图标（如Layers或ViewModule）
- FAB统一样式：圆角方形(RoundedCornerShape(16.dp))替代纯圆形，更现代

**涉及文件**:
- 所有包含AlertDialog的Screen文件
- 所有编辑页Screen (ItemEditScreen, CoordinateEditScreen, OutfitLogEditScreen, PriceEditScreen, PaymentEditScreen)
- `ui/navigation/LolitaNavHost.kt` (底部导航图标)

---

## 关键决策记录

| 日期 | 决策内容 | 理由 |
|------|----------|------|
| 2025-02-12 | 个人独立开发模式 | 项目规模适合，简化协作流程 |
| 2025-02-12 | 使用Jetpack Compose | 现代化UI框架，学习资源丰富 |
| 2025-02-12 | 仅本地存储 | 无需服务器，降低复杂度 |
| 2025-02-12 | UI风格：Lolita甜美风格 | 符合目标用户审美 |
| 2026-02-13 | 套装替换搜索Tab | 底部导航5个位置已满，套装比搜索更高频，搜索移到设置页 |
| 2026-02-13 | 颜色/季节/风格用可选字段 | 不强制填写，降低录入门槛 |
| 2026-02-13 | 数据库Migration而非destructive | 保留用户已有数据 |
| 2026-02-13 | TopAppBar紧凑模式 | 列表页减少title占比，给内容更多空间 |
| 2026-02-13 | FAB改圆角方形 | 更现代的Material3风格 |

---

## 项目信息
- **开发模式**: 个人独立开发
- **技术栈**: Jetpack Compose + Room Database
- **存储方案**: 仅本地存储 (Room Database)
- **UI风格**: Lolita甜美风格
