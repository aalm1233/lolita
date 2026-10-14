# 开发协作流程

## 项目事实

- 单模块 Android 应用，Kotlin 2.1.0 + Jetpack Compose (BOM 2024.12.01) + Material3。compileSdk 35, minSdk 26, targetSdk 35, JVM target 17。
- 数据流：`Entity (Room) -> DAO -> Repository -> ViewModel (StateFlow) -> Composable`
- DI 不是 Hilt/Dagger，是 `AppModule` 手写 service locator；`LolitaApplication.onCreate()` 初始化。ViewModel 通过构造参数默认值从 `AppModule` 取仓库。
- Room 注解处理用 **KSP**（不是 Kapt）。Schema 导出目录 `app/schemas/`。
- USB 设备测试套件：`bash run_device_test.sh`，验证以编译 + 设备测试为主。
- UI 文案使用中文。
## 开始工作前

- 先查看相关代码、数据流和现有实现，不直接拍脑袋改。
- 先看 `git status --short`，确认当前工作区是否有用户未提交改动。
- 如果任务跨越多个模块，先整理一个简短计划，再开始动手。
## 架构与实现约定

- 新 DAO 必须同步接到三处：`LolitaDatabase`、对应 `Repository`、`AppModule`。
- ViewModel 用 `MutableStateFlow`/`StateFlow` 管 UI 状态，异步放 `viewModelScope.launch`。
- Repository 保持薄封装，围绕 DAO 响应式查询 + 少量业务拼装，不塞重逻辑。
- `List<String>` 字段复用现有 Gson `TypeConverters`；新增枚举字段需补 `TypeConverter`。
- 风格、季节、来源等字符串属性支持重命名/删除时，Repository 必须级联更新所有引用该名称的 Item 记录。
- 领域层在 `domain/`：`domain/model/`、`domain/usecase/`（如 `MatchingEngine.kt`）。

## 数据库

- Room 数据库版本 26，26 个实体（含当前订单绑定 `OrderBinding`、付款阶段 `PaymentStage`、历史图鉴归档 `LegacyCatalogArchive`、恢复提醒日志 `RestoreJournal`、`SharedLibrarySyncState` 和 8 个 `Remote*` 实体）。历史图鉴在移除原表前逐字段归档，归档纳入备份。
- Migration 在 `LolitaDatabase.kt` 注册，20→21 的重建与数据迁移、21→22 的历史购买资料标记、22→23 的订单永久购买证据和提醒范围日志位于 `OrderMigration.kt`，每次改结构必须升 version + 写 Migration。
- 外键策略：子记录跟主记录删除用 `CASCADE`（Price→Item, Payment→Price）；被引用对象不该在使用中删除用 `RESTRICT`（Item→Brand, Item→Category, Item→Coordinate, Item→parent Item）。
- 新增图片字段统一走 `ImageFileHelper`（存到 `context.filesDir/images/`，UUID 文件名），删除记录时清理图片文件。
- 新功能状态若由历史数据推导，需考虑一次性历史刷新，优先批量 SQL 而非逐条循环，在 `LolitaApplication.onCreate()` 调用。

- 服饰与价格改造已实现：`docs/plans/2026-10-11-item-pricing-redesign.md`；实现与回归记录：`docs/item-pricing-implementation-2026-10-11.md`。

## 备份兼容

新增实体或字段时同步检查 `BackupManager`：
- 导出 / 导入 / 预览逻辑都要补
- 导入须兼容旧备份（缺失字段容错）
- 如适用则补 CSV 导出列

## 导航与页面

- 导航统一走 `Screen` sealed interface + `LolitaNavHost`，新增页面两边都补。
- 列表/详情/编辑页成套考虑。
- `GradientTopAppBar` 是标准顶栏，默认 `compact = true`，无明确理由不改。顶栏支持 Haze 毛玻璃效果：当 `skin.topBarBlurEnabled && LocalHazeState.current != null` 时使用 `hazeEffect`，否则回退到原始渐变。
- `LolitaNavHost` 外层 `Scaffold` 设 `contentWindowInsets = WindowInsets(0,0,0,0)`，`GradientTopAppBar` 内部处理 `statusBarsPadding()`；NavHost 应用外层 padding 后调用 `consumeWindowInsets(paddingValues)`，避免编辑页 IME 重复计算已占用的底栏空间。
- 底部导航栏支持 Haze 毛玻璃效果：通过 `skin.navBarBlurEnabled` 控制，启用时 `containerColor` 使用半透明 tint 色。
- `LocalHazeState` CompositionLocal 在 `LolitaNavHost` 中创建并提供，外层 `Box` 设 `hazeSource`，使内容可滚动到顶栏/底栏后方。
- 卡片复用 `LolitaCard`（已集成皮肤 `cardElevation` + `cardBorderStroke`）。支持 `variant` 参数：`CardVariant.DEFAULT`（默认）、`CardVariant.GALLERY`（无边框/无内边距/零阴影，图片主导）、`CardVariant.FEATURED`（高阴影/大内边距，突出展示）、`CardVariant.COMPACT`（低阴影/紧内边距/细边框，替代 raw Card）。
- `LolitaCard.contentPadding` 可覆盖变体默认内边距，避免内容层重复 padding；图文列表卡的图片贴边时传 `PaddingValues(0.dp)`，仅文字区保留一次内边距，图片顶部圆角使用皮肤 `cornerRadiusMedium`，网格骨架同步实际 0.8 宽高比。半透明卡片 tint 在公共组件内一次合成到当前 `surface`，完全透明卡片仍保持透明。
- 统计总览将四项数量收进单个衣橱概况区块；消费概览突出当前订单实付，全部已录实付与参考均价采用明细行，金额直接显示原始 Double 的两位小数，不通过 Float 动画改变显示值。金额、长品牌及最高实付信息需在小屏/大字体下完整排布；关闭金额设置时整块消费与最高实付隐藏。卡片只保留一层内边距，间距与圆角消费皮肤令牌，加载态使用匹配布局的骨架屏。真实 Compose 回归位于 `StatsOverviewScreenTest`（独立内存 Room、UUID 设置；含导航、实时隐私和浅暗七套皮肤截图）。 本轮预览归档在 `docs/visuals/2026-10-12-stats-overview-light.png` 和 `docs/visuals/2026-10-12-stats-overview-victorian-dark.png`。
- 详情页分区容器复用 `LolitaSection`（iOS Settings 风格：`SectionHeader` + 行内容 + 内嵌分割线，包裹在 `LolitaCard` 内）。新详情页优先用 `LolitaSection` 而非手动 `LolitaCard + SectionHeader`。
- 详情页分区标题复用 `SectionHeader`（左侧竖线 + 标题 + 可选操作按钮 + 分割线，皮肤感知）。所有详情页统一使用 `SectionHeader`，不使用裸 `Text(titleMedium/Bold)`。
- 图片展示区复用 `ImageFrame`（画框容器，皮肤感知边框/阴影/内边距）。
- 详情页信息区块用 `LolitaSection` 或 `LolitaCard + SectionHeader` 包裹，不用裸 Text + HorizontalDivider。
- Tab 页面沿用 `HorizontalPager + TabRow + SkinTabIndicator`。
- 首页今日穿搭入口使用 `TodayOutfitShortcut`：紧凑卡片、穿搭图标/单张缩略图、标题与状态副文案、记录/查看操作；两种状态共享布局，颜色/圆角/内边距消费皮肤令牌，并合并无障碍语义。
- 快捷穿搭记录支持名称/品牌搜索、分类筛选和已选缩略图移除；筛选不得清除隐藏的已选物品。控制区随网格滚动，底部保留数量保存按钮。编辑已有记录时保留照片、原日期及创建时间，读取失败或未完成时禁止保存。
- 完整穿搭编辑的日期、备注和关联服饰通栏，只有照片采用两列；卡片仅一层内边距，照片使用皮肤圆角。底部提供唯一“保存穿搭”按钮，消费 Scaffold Insets 后应用 IME padding，键盘打开时保存仍可达；保存条件和原照片、日期、关联、放弃更改逻辑保持。网格注册 `ObserveSkinScroll`。 完整编辑保留原记录并复制可编辑字段，创建时间不变、修改时间由 Repository 更新；读取原记录期间通过 `isLoading` 禁用保存，ViewModel 同时拒绝原记录未读取完成的提交。回归为 `OutfitLogEditViewModelTest`（独立内存 Room、真实照片，覆盖已有/新建/读取中/已删除）。
- 淘宝导入指南按“获取订单文件 / 回到应用导入”分组，完整保留七步正文及四项注意事项；使用皮肤卡片与 `SectionHeader`，没有真实截图时不绘制截图占位框。月份的紧凑布局保持既定设计。
- 服饰详情主图完整 Fit 预览，高度随屏幕受限，标题后优先展示当前订单价格/实付/待付摘要；无小物时显示紧凑新增/挂入操作，已有小物管理完整保留。`ImageGalleryPager` 支持可选 `contentScale`、`imageShape` 与 `imageBackgroundColor`，默认展示保持兼容。
- 首页顶栏保留搜索、四个 Tab 和筛选/排序/视图控制的一行布局。本轮列表、详情和穿搭实拍归档：`docs/visuals/2026-10-11-layout-refinement.png`。
- 选项多时（如品牌 200+）用可搜索对话框，不退回简单下拉框。

- 淘宝商品导入使用已核验内置别名解析品牌，匹配现有记录时忽略大小写/空格/标点并拒绝歧义；通用品牌参数不作为真实品牌。类别根据明确参数或标题识别，混合套装在预览提示核对。详情图来自 `#v6_pc_content`，读取懒加载源、去重过滤页面占位图，最多50张、每张20MiB、累计80MiB，保留原图进入多张尺码表。旧 `sizeChartImageUrl` 保留，新增列表 `sizeChartImageUrls` 存后续图片，24→25只追加默认空列表；备份缺失字段补空数组、ZIP/预览/CSV与删除清理均包含新列表。编辑与详情可逐张放大，失败或放弃编辑清理本次新增图片。
- 一个商品包含明确“套装/款式/类型”时，淘宝导入可勾选拆分，逐件进入服饰编辑；不按颜色或尺码展开。每件独立推断类别、复制完整主图与详情到 UUID 文件，保存自动下一件，放弃暂停当前件。共享下载缓存仅由导入页拥有，退出不影响已保存记录。SavedStateHandle 保存紧凑队列与公共详情；每件稳定 `importToken` 与每次编辑 `editorToken` 分开，陈旧返回不会重放。Room25→26新增可空唯一 `items.import_token`，提交后未回传时恢复原记录，备份兼容缺失标识并拒绝重复标识；隐藏元数据不导出CSV。编辑器退出清理必须经 ImageFileHelper 的引用保护，保存中禁止返回。

- 详情读取兼容 `#v6_pc_content` 与新版 `.detail-desc`；新版只从详情图片的至多7层组件公开 `imageUrl` 取得懒加载原图，未解析完图片、缺失详情或加载失败时不得接受空/部分详情。仅含明确服饰名及“＋”组合的颜色分类可按单件去重，普通颜色与尺码不展开，不同明确前缀不得按类别合并，套餐价不下放单件。

- 淘宝参考报价读取现代 SKU 的明确规格组合，打开规格时优先实时面板；按套装/款式/类型关联物品，起价、区间价、定金、尾款及未确认款式不冒充全款。预览可逐件补填参考金额，紧凑队列恢复保留各件报价；来源说明的专用报价行可在设置价格页主动采用，仅填商品金额与默认全款阶段，确认保存才建订单、实付另行录入。旧的无款式页面报价不用于录价。

## 内置店铺图标与默认房间

- 离线清单位于 `app/src/main/assets/builtin/catalog.json`，包含从测试资料核验的 14 家店铺真实商标/官方头像与卧室、客厅、衣帽间、书房、玄关、储物间六张生成插图；来源、SHA256 和完整生成提示词见 `docs/builtin-assets-sources-2026-10-12.json`。未经核验的店铺保持首字回退，禁止生成仿制商标或按相似名称套图。
- `BuiltInLibrary` 在启动时一次性事务追加缺失默认房间、补充精确名称/核验别名匹配的空品牌图片。保留用户原图片及已有实体所有其他字段，不合并位置或品牌；本机 `builtin_library/initialized_v1` 在事务提交后保存，用户删除、改名或移除图片后重启不补回。
- `ImageFileHelper.copyAssetToInternalStorage` 将原素材复制为 `filesDir/images/` UUID 文件，实体只存正常本地路径，继续纳入现有 ZIP 图片清单与恢复校验。数据库版本和备份格式不变。
- `BrandRepository` 新增品牌可通过可选 `BuiltInLibrary` 自动提供精确匹配的默认图，插入失败清理新文件；编辑、去图不触发重新补图。`AppModule` 接线；`BackupManager` 可选初始化屏障位于现有串行操作锁之前，恢复后不得再追加默认数据。
- 真实商标使用 `BrandLogo` 共用完整 Fit 预览与中性浅色底，兼容横向字标及暗色皮肤中的透明黑色商标；管理列表和新增/编辑预览复用该组件。
- 实拍及真实旧包升级核对见 `docs/builtin-assets-2026-10-12.md`（含浅/暗房间与横向透明商标）；原测试资料 140 件服饰、215 个品牌及四个位置保全，图片逐项 SHA256 验证。
- `BuiltInLibraryTest` 使用独立内存 Room、UUID 设置和测试图片目录，覆盖历史资料保全、并发/重复初始化、删除/改名/去图、失败/取消清理、新品牌插入和 ZIP/恢复屏障。设备验证必须定向到独立 AVD，不能清理用户设备数据。
- 2.41.0 / 91 用 clean 正式构建完成14店铺与6房间素材发布验证；已发布2.40.5原包同签名覆盖升级，原24张表、140件服饰、215个品牌、4个位置及190张原图片保全，新图片SHA256和重启幂等核对通过。记录见 `docs/builtin-assets-release-2.41.0-verification.json`。

## 皮肤系统

- **7 套皮肤**：`DEFAULT` / `GOTHIC` / `CHINESE` / `CLASSIC` / `NAVY` / `COUNTRY` / `VICTORIAN`。
- 新页面/交互复用皮肤感知组件，不做脱节的普通 Material 页面。
- 新增图标需同时补：`BaseSkinIconProvider` + **7 个**皮肤专属 provider（`SweetIconProvider`, `GothicIconProvider`, `ChineseIconProvider`, `ClassicIconProvider`, `NavyIconProvider`, `CountryIconProvider`, `VictorianIconProvider`）+ `IconKey` 枚举。图标是 Canvas 手绘，不是 Material icon。
- 七套图标通过 `PolishedSkinIconProvider` 共用完整的 51 个 `IconKey`；绘制入口 `PolishedSkinIcon` 按导航、内容、操作/箭头/状态分派到 `PolishedNavigationIcons`、`PolishedContentIcons`、`PolishedControlIcons`；后者仅分派到 `PolishedActionDrawings` 与 `PolishedDirectionStatusDrawings`，分别管理操作及方向/状态造型。旧的田园/维多利亚装饰图标实现已移除。 七套保留统一网格与入口，各绘制组按皮肤维护主体轮廓：甜美饱满曲线、哥特尖拱切角、中华飞檐交领、古典对称收束、海军灯塔舷窗、田园宽檐围裙、维多利亚拱形层叠。导航、内容、高频操作都需体现造型语言；不得仅改圆角/微内纹或给同一主符号套装饰框。五个导航造型沿用已认可版本；其余 46 个键在七套皮肤中均需保持不同的主体构件/轮廓/笔势，包括加号、保存、分享、九宫格、下拉及显隐。基础控件保持直接语义，不能只加端点小装饰。
- 图标使用 `SkinIconCanvas` 的 24×24 逻辑坐标，主线宽 1.7，主体保留约 2 单位安全边距；调用方尺寸优先，非正方形区域居中等比缩放。七套底栏图标统一 26dp，选中使用皮肤 accent，未选中使用 `onSurfaceVariant`。
- 功能图标保留明确语义（加号、垃圾桶、刷新箭头、图表、齿轮）；风格通过全套图标的主体造型、工具构件与笔势体现，避免在 24dp 中叠加盾牌/吊牌/缎带缩小功能符号。全部颜色来自 tint，派生透明度必须乘原 tint.alpha，不使用固定奶油色/象牙色；实心/空心星标共用同一轮廓。
- 图标回归：`SkinIconVisualTest` 在 Android 上通过真实七套 provider 渲染全部 51 个图标，检查留白、18/24/26/32dp 与矩形尺寸、tint 和完全透明输入；输出亮/暗完整矩阵和中文概览至 app 私有目录 `files/skin-icon-contact-sheets/`；本轮概览归档在 `docs/visuals/2026-10-11-skin-icons-light.png` 和 `docs/visuals/2026-10-11-skin-icons-dark.png`。 2026-10-12 个性恢复新增 26dp 浅暗中文概览及导航/操作/内容/箭头/状态五组导出；归档在 `docs/visuals/2026-10-12-icon-personality-*.png`，说明见 `docs/icon-personality-2026-10-12.md`。 2.40.4 / 89 使用 clean Release 在独立 AVD 上从已发布 2.40.3 覆盖升级，同签名并保留皮肤设置；正式包七套导航与皮肤预览实拍亦归档在该前缀下。添加照片采用单次差集裁切，避免半透明边框重复合成；真实 provider 半透明回归比较图片与添加照片下半部未改边框/山形像素。非导航扩展回归逐键比较七套真实绘制，禁止相同位图回退；所有分组额外导出 18/32dp 浅色对照，小尺寸辨认与艺术差异仍须看实际矩阵。扩展说明与归档为 `docs/icon-details-2026-10-12.md`、`docs/visuals/2026-10-12-icon-details-*.png`。2.40.5 / 90使用clean Release从2.40.4同签名覆盖升级并保留皮肤设置，正式包完整51×7仪器化回归通过；43张实际矩阵与已审阅Debug图逐像素一致，35个导航单元与上一正式包一致。验证测试宿主/导出目录的临时适配不得留入正式源码。定向运行：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lolita.app.ui.theme.skin.icon.SkinIconVisualTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`。设备旧包与新包必须先核对签名；UTP 在安装失败后也可能自动卸载旧包，测试前保全模拟器数据或使用独立 AVD，禁止用卸载绕过签名冲突。
- 新增列表/切换/过渡效果优先复用现有 skin 动画体系（`SkinClickable`, `SkinItemAppear`, `SkinNavigationOverlay` 等）。
- 滚动容器通过 `ObserveSkinScroll(ScrollableState)` 向 NavHost 的 `SkinScrollActivity` 注册；按来源合并拖动/惯性状态，离开 composition 只移除自身，禁止多个列表直接写同一个全局 Boolean。位置、套装列表/网格、服饰列表/网格/画廊、穿搭、首页心愿、统计总览、付款年历、消费分布/趋势、愿望分析及首页/统计 pager 均已接入。背景粒子按实际 Canvas 尺寸初始化，使用帧时钟与实际时间更新（最高约 60Hz），滚动期间冻结并保留画面，恢复时不得重置/跳跃。
- `SkinItemAppear` 的进度仅在 `graphicsLayer` 中读取，避免逐帧重组卡片。`SkinFlingBehavior` 用独立上帧位置计算位移，原始释放速度只经摩擦衰减，遇到未消费位移立即停止并保留余速。懒列表的索引使用 `itemsIndexed`，不要在逐卡片 composition 中扫描 `indexOf`。
- 滚动回归位于 `SkinFlingBehaviorTest`（七套皮肤双向惯性、边界）、`SkinItemAppearTest`（图层更新不重组）、`SkinScrollActivityTest`（多来源/取消/离开）、`SkinBackgroundAnimationTest`（暂停/恢复真实绘制）与 `ScrollPageTest`（带图片真实页面的拖动、惯性、清理、导航，独立内存 Room/UUID 设置）。验证记录见 `docs/scroll-performance-fix-2026-10-12.md`。

### 背景插图与桌面标识（2.46.0）

- 七套边角插图位于 `res/drawable-nodpi/bg_art_*.png`，保留原始 RGBA；来源、SHA256 和完整提示词见 `docs/skin-art-assets-sources-2026-10-14.json`。`SkinArtBackground` 在 NavHost 的 Haze source 中仅提供一个静态底层，居中 Crop 保持比例；页面 Scaffold 透明，卡片、编辑底部、WebView 和图片表面保留原背景。
- `artBackgroundRes/Alpha/AlphaDark/TintDark` 四令牌由七工厂独立设置；暗色按原图亮度映射柔和皮肤墨色并保留 alpha。复用 `LolitaShimmerImage` 的可选 `colorFilter` 和 `showFailurePlaceholder=false`，无 shimmer、揭示或失败问号，无时间动画和滚动状态。
- 桌面图标使用 108 原生 Vector 衣橱挂裙，主体位于 66 安全圆，v26 两层 adaptive、v33 普通/round 加透明负空间 monochrome；每日穿搭通知使用独立 `ic_notification`。七套功能图标精修金额、相机、关联、纸片、搜索及方向等 15 键，保留五导航轮廓和全部 51×7 身份。
- `SkinArtBackgroundTest` 核对七资源透明留白、真实浅暗与横屏、静态绘制和失败回退；`LauncherArtVisualTest` 使用真实 Drawable/Canvas 核对 24/48dp 的圆形、圆角及浅暗主题裁切。设备验证仅在独立 AVD，使用原有 `SkinIconVisualTest` 完整矩阵与粒子/滚动回归。

### 视觉精致化令牌

`LolitaSkinConfig` 包含以下视觉令牌，新组件/页面应消费它们而非硬编码：

| 令牌 | 用途 | 示例值（DEFAULT / VICTORIAN） |
|------|------|-------------------------------|
| `cardElevation` | 卡片阴影 | 1dp / 3dp |
| `cardBorderStroke` | 卡片边框（null = 无边框） | null / gold 1dp |
| `imageFrameElevation` | 图片区阴影 | 2dp / 4dp |
| `imageFrameStroke` | 图片边框 | null / gold 1dp |
| `imageFramePadding` | 图片与边框间距 | 0dp / 3dp |
| `sectionAccentColor/Dark` | 分区标题竖线颜色 | Pink400 / deepRose |
| `sectionAccentWidth` | 竖线宽度 | 3dp |
| `sectionDividerColor/Dark` | 分区分割线颜色 | Pink200 / gold |
| `sectionDividerHeight` | 分割线粗细 | 1dp |
| `spacingSmall/Medium/Large/ExtraLarge` | 8pt 间距系统 | 4/8/16/24dp |
| `cornerRadiusSmall/Medium/Large` | 圆角半径 | 8/16/24dp / 6/12/16dp |
| `spatialSpring` / `effectsSpring` | 统一弹簧动画 | 0.75,400 / 0.8,300 |
| `cardContainerColor/Dark` | 卡片表面色（替代运行时 alpha） | White(0.75) / cream(0.8) |
| `cardInnerPadding` / `cardGap` | 卡片内边距 / 卡片间距 | 16dp / 20dp |
| `topBarBlurEnabled/Alpha/Tint/Dark` | 顶栏毛玻璃 | 0.72 / 0.82 |
| `navBarBlurEnabled/Alpha/Tint/Dark` | 底栏毛玻璃 | 0.75 / 0.85 |
| `dialogBlurEnabled/Alpha` | 对话框毛玻璃 | 0.65 / 0.72 |
| `galleryCardElevation/Border/InnerPadding` | 画廊卡片变体令牌 | 0dp/null/0dp / 0dp/gold0.3/0dp |
| `featuredCardElevation/Border/InnerPadding` | 精选卡片变体令牌 | 2dp/null/20dp / 6dp/gold1/24dp |
| `compactCardElevation/Border/InnerPadding` | 紧凑卡片变体令牌 | 0.5dp/null/8dp / 1dp/gold0.5/10dp |
| `cardBorderStroke/Dark` | 卡片边框（含暗色变体） | null / gold1dp |
| `imageFrameStroke/Dark` | 图片边框（含暗色变体） | null / gold0.7dp |
| `galleryCardBorderStroke/Dark` | 画廊卡片边框暗色变体 | null / gold0.3dp |
| `featuredCardBorderStroke/Dark` | 精选卡片边框暗色变体 | null / gold1dp |
| `compactCardBorderStroke/Dark` | 紧凑卡片边框暗色变体 | null / gold0.5dp |

### 加载态与图片

- **骨架屏**：列表页全屏加载状态用 `ShimmerRect`/`ShimmerLine`/`ShimmerCircle`（`ui/screen/common/SkeletonShapes.kt`）构建骨架卡片，替换 `CircularProgressIndicator`。每个卡片类型有对应 `XxxCardSkeleton` 私有组合函数（定义在各自屏幕文件内）。
- **ShimmerTheme**：`ui/theme/shimmer/ShimmerTheme.kt` 中 `skinShimmerTheme()` 从 `cardContainerColor` + `accentColor` 派生，通过 `LocalShimmerTheme` 在 `LolitaTheme` 中提供。
- **LolitaShimmerImage**：`ui/screen/common/LolitaShimmerImage.kt`，皮肤感知的 `CoilImage` 封装，集成 `ShimmerPlugin`（加载态闪烁）+ `CircularRevealPlugin`（出场圆形揭示动画）+ 首字母失败回退。**所有图片加载统一用 `LolitaShimmerImage`，不再直接使用 Coil `AsyncImage`。**
- **特殊场景**：全屏缩放查看器（`ImageGalleryPager`/`FullScreenImageViewer`/`ImagePreviewDialog`）和编辑页图片设 `circularRevealEnabled = false`（缩放/编辑需即时显示）。
- **保留 CPI 的场景**：内联按钮 spinner（保存/同步操作指示器，如 `BackupRestoreScreen`、`TaobaoImportScreen`）保持 `CircularProgressIndicator`，不用骨架屏。
- **依赖**：`compose-shimmer:1.3.2`（骨架屏 `Modifier.shimmer()`）、`landscapist-coil:2.4.7` + `landscapist-placeholder:2.4.7` + `landscapist-animation:2.4.7`（图片加载 + 闪烁 + 圆形揭示）。

新增视觉令牌时需同步：`LolitaSkinConfig` 数据类 + **7 个**皮肤工厂函数 + dark mode 变体。

### 暗色模式适配规范

- **卡片/图片边框**：`LolitaCard` 和 `ImageFrame` 通过 `isSystemInDarkTheme()` 自动解析 `*Dark` 变体边框令牌。有边框的皮肤（GOTHIC/CHINESE/CLASSIC/VICTORIAN）暗色变体使用更亮的色调以保证在深色背景上可见。
- **状态颜色**（已付/逾期）：使用 `paidColor()` / `overdueColor()` 可组合函数，暗色模式使用 Material 300 色阶（更亮）。
- **优先级颜色**（HIGH/MEDIUM/LOW）：暗色模式使用更亮的粉色/黄色/绿色变体。
- **图标着色**：设置页/属性管理页图标使用 `if (isDark) bright300 else normal500` 模式。
- **导航栏未选中图标**：使用 `MaterialTheme.colorScheme.onSurfaceVariant`（自动适配深浅模式）。
- **筛选/排序强调色**：使用 `skin.accentColor` / `skin.accentColorDark` 而非硬编码橙色。
- **Canvas 图表文字**：圆环中心金额和折线坐标文字跟随 `onSurface` / `onSurfaceVariant`，Paint 的 remember key 包含主题颜色与文字像素尺寸；网格用 `outlineVariant`，曲线默认使用主题 primary。
- **错误色**：使用 `MaterialTheme.colorScheme.error` / `onError` 而非硬编码 `0xFFD32F2F`。
- **渐变覆盖层文字**（`Color.Black.copy(alpha) + Color.White`）：图片上方的价格/日期标签仍使用黑底白字，这是正确的设计。

### Hero 动画（SharedTransitionLayout）

- **`SharedTransitionLayout`** 包裹 `LolitaNavHost` 的 `Scaffold`，通过 `CompositionLocal` 向子组件提供 `SharedTransitionScope` 和 `AnimatedVisibilityScope`。
- **`heroTransitionEnabled`** 皮肤令牌控制是否启用 hero 动画（默认 `true`）。
- **`Modifier.heroSharedElement(key)`**：封装 `sharedElement` + 皮肤令牌检查 + 作用域空值保护的组合修饰符。定义在 `ui/screen/common/HeroSharedElement.kt`。
- **修饰符顺序规则**：尺寸修饰符 → `heroSharedElement()` → `clip()`。尺寸必须在 `sharedElement` 之前，`clip` 必须在之后。
- **`ImageGalleryPager`** 接受 `sharedTransitionKey` 参数，仅对第一页（`page == 0`）应用 hero 动画。
- **5 种实体的 hero 动画**：Item、Coordinate、Catalog、OutfitLog（仅单图模式）、Location。WishlistScreen 的卡片也导航到 ItemDetail 并使用相同的 `itemImage-${id}` key。
- **详情页路由过渡**：使用 hero 动画的路由（ItemDetail、CoordinateDetail、CatalogDetail、OutfitLogDetail、LocationDetail）使用 `fadeIn/fadeOut` 替代默认的 slide 过渡，避免与 hero 动画冲突。
- **`@OptIn(ExperimentalSharedTransitionApi::class)`**：BOM 2024.12.01 → Compose Animation 1.7.x，API 仍为实验性（但 API 冻结，与 1.10.0 稳定版相同）。
- **共享元素 key 命名**：`itemImage-${id}`、`coordinateImage-${id}`、`catalogImage-${id}`、`outfitLogImage-${id}`、`locationImage-${id}`。
- **GalleryPreviewDialog 边缘情况**：通过画廊预览对话框导航到详情页时不触发 hero 动画（对话框不在 `SharedTransitionScope` 内），这是可接受的降级。

### 后续优化方向

- 逐皮肤深化装饰层（如 `SkinDecorationProvider` 接口，华丽皮肤覆写提供 Canvas 角落花纹/画框金边等）
- 对话框皮肤化 + 毛玻璃（`dialogBlurEnabled`/`dialogBlurAlpha` 令牌已就绪，待创建 `LolitaDialog` 组件）
- `ImageFrame` 应用到详情页图片区域
- 皮肤切换 Crossfade 过渡
- 高级毛玻璃（Liquid Glass，API 33+ shader 效果）

## 构建与验证

```bash
./gradlew assembleDebug        # 改代码后至少跑一次
./gradlew assembleRelease      # 出包时跑
./gradlew clean assembleDebug  # 清理后构建
./gradlew clean assembleRelease # 出 release 包前务必 clean
```

- Release 签名需 `local.properties` 中配置 `STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，密钥文件为项目根 `lolita-release.jks`。
- Release 输出：`app/build/outputs/apk/release/app-release.apk`。
- 编译失败先修到通过再给结论。
- **增量编译缓存陷阱**：Kotlin 增量编译可能未正确重编译跨文件依赖（如 `VictorianIconProvider` 依赖 `VictorianDecorativeIcons`），导致 release 包运行时回退到基类默认行为（图标变成 Material 默认图标）。debug 包因编译路径不同可能不受影响。**出 release 包前必须 `clean`**，遇到"debug 正常但 release 异常"的症状优先尝试 `clean assembleRelease`。

## 本机安卓模拟器调试

- Apple Silicon 本机 AVD：`Lolita_API_35`（Android 15 / API 35，Google APIs ARM64，Pixel 7）。
- `bash scripts/debug_emulator.sh` 启动模拟器、等待系统就绪、构建 Debug APK、安装并打开应用；默认使用 `emulator-5554`，保留模拟器数据；安装前核对 AVD 名称、包名、签名和版本，拒绝降版且不卸载/清数据。
- SDK 默认路径 `~/Library/Android/sdk`，可用 `ANDROID_HOME` 覆盖；AVD 名可用 `LOLITA_AVD` 覆盖，端口用 `LOLITA_EMULATOR_PORT`，序列号用 `LOLITA_EMULATOR_SERIAL`（回退 `ANDROID_SERIAL`）；配置必须一致。日志默认 `/tmp/lolita-emulator-5554.log`，端口变化时同步变化。
- Android Studio 的 Device Manager 可启动同一 AVD，再选择它运行/调试 app。
- macOS 启动脚本通过 `launchctl submit` 独立管理模拟器进程，避免调试命令结束后进程被回收；使用 host GPU 和冷启动。
- 现有 USB 自动化脚本按 1116×2480 真机坐标编写，不能直接作为 Pixel 7 模拟器的验证结果；模拟器使用定向启动、UI 检查与 logcat 验证。

## USB 设备测试

```bash
bash run_device_test.sh            # 运行 UI 自动化测试套件
```

- 测试基于 `adb` + `uiautomator` + `screencap`，无需额外依赖。
- 前提：USB 连接 Android 设备（debug APK 已安装，USB 调试已开启），分辨率 1116×2480、480dpi；其他尺寸拒绝运行。用 `ANDROID_SERIAL` 指定 USB 目标，未指定时仅自动选取唯一已授权本机 USB 设备，拒绝模拟器与网络传输。
- 验证方式：按 UI 树边界点击与选中状态核对，保存截图、UI 树和前台活动证据，logcat 仅检测本次运行。FAIL 返回 1，前提错误返回 2，未执行为 SKIP；滚动证据单独记录。
- **Compose 限制**：部分内容可能无法被 `uiautomator dump` 读取，缺少证据应失败或明确跳过，不凭截图文件大小或固定 PASS 认定成功。底部点击限定在可点击祖先与手势区上方的安全交集。
- `ADB` 路径默认 `~/Library/Android/sdk/platform-tools/adb`（macOS）或 `C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe`（Windows），可通过环境变量 `ADB` 覆盖。
- 结果输出：`test_results.txt`，每次截图、UI 树、活动日志存入独立 `test_screenshots/usb-*/`。可用 `LOLITA_RESULTS_FILE`、`LOLITA_SCREENSHOT_DIR`、`LOLITA_UI_WAIT_SECONDS` 覆盖。

### 测试覆盖范围（14 组）

| 组 | 测试内容 | 验证方式 |
|----|----------|----------|
| G1 | 应用启动、底部 4 tab 和首页顶部 4 tab | UI 树文字 + 截图 + 前台活动 |
| G2 | 首页/穿搭/统计/个人切换 | 选中状态 + 页面文字 |
| G3 | 位置/服饰/套装/心愿切换 | 顶部选中状态 |
| G4 | 新增服饰入口与回退 | 编辑页标题 + 回退选中状态 |
| G5 | 新建套装入口与回退 | 编辑页标题 + 回退选中状态 |
| G6 | 心愿页新增入口与回退 | 页面证据；保存记录 SKIP |
| G7 | 个人页加载；滚动单独记录 | 文本/位置变化，未溢出或无位移证据为 SKIP |
| G8 | 统计页加载；滚动单独记录 | 文本/位置变化，未溢出或无位移证据为 SKIP |
| G9 | 穿搭日记页加载 | 标题 + 底部选中状态 |
| G10 | 首页心愿页加载 | 顶部选中状态 |
| G11 | 空白编辑页返回 | 进入编辑页并返回原页签 |
| G12 | 本次应用崩溃与 ANR | 带开始时间的应用日志 |
| G13 | 备份选择器打开及返回 | 系统文件活动 + 返回标题；实际恢复与数据核对 SKIP |
| G14 | 淘宝选择器打开及返回 | 系统文件活动 + 返回标题；Excel 解析、下一步及导入 SKIP |

### 设备坐标参考（1116×2480 @ 480dpi）

- 底部导航为首页/穿搭/统计/个人 4 项；脚本从 UI 树定位可点击边界，点击 y 必须小于 2372，不能复用旧 5 项导航的 x 坐标。
- 顶部为位置/服饰/套装/心愿；脚本从 UI 树精确标签和边界定位，不复用旧图鉴入口。
- FAB：(984, 2168)
- 设置页菜单（视觉 y 需 +350 左右为实际触摸坐标）：属性管理 ≈ y=850 / 数据备份与恢复 ≈ y=1050 / 淘宝订单导入 ≈ y=1200
- 备份恢复页需向下滚动（swipe 1800→1000）才能露出导入区，「选择备份文件」按钮中心：(557, 2100)
- 淘宝导入页「选择文件」按钮中心：(557, 1469)
- 系统文件选择器中文件项（网格视图卡片中心）：(275, 800)
- 淘宝导入页底部「下一步」按钮中心：(906, 2209)
- 不同分辨率/密度设备需调整坐标

### 坐标定位技巧

在维护测试脚本时，若遇到 `adb shell input tap` 点击无响应但截图显示 UI 正常的情况，通常是**视觉坐标与实际触摸坐标存在系统级偏差**（本设备偏差约 350px）。推荐定位方法：

1. **先截图**：`adb shell screencap -p /sdcard/screen.png && adb pull ...`
2. **用 Python + Pillow 分析像素**：找到目标控件（如粉色按钮）的精确像素 bounds：
   ```python
   from PIL import Image
   img = Image.open('screen.png')
   pixels = img.load()
   # 遍历查找目标颜色像素，输出 min/max x/y
   ```
3. **以像素 bounds 的中心作为 tap 坐标**，而不是靠肉眼估算。

> 注意：Compose 页面的 `LolitaCard` 等组件的 clickable 区域可能比视觉边界更大，导致相邻菜单项的点击区域重叠。遇到"点击 A 却进入 B"时，应通过像素分析确认 B 的实际 clickable 范围，并选择 A 的坐标时尽量避开重叠区（例如选更靠下的安全区域）。

## 版本号

- 刷新版本号：`versionCode + 1`，`versionName` 顺延（当前 versionCode=99, versionName="2.46.0"）。
- 新功能升 minor，常规修复升 patch，大改/不兼容升 major。

## 提交、推送与发布

- 开发任务完成且必要验证通过后，默认完成本次改动的提交和推送；用户明确要求暂不提交或推送时遵从。已经授权的步骤连续执行，不在完成代码后遗漏提交、推送，也不重复请求同一授权。
- 应用代码改动默认执行完整发布流程（正式同签名 APK、GitHub 与 Gitee 同步 Release、附件和更新入口验证），让用户通过正常检查更新和覆盖安装测试；不以本地试用包代替发布。用户明确要求暂不发布时遵从。纯文档更新仍按下文例外处理。
- 提交前检查 `git status --short` 和暂存区差异，只暂存本次任务文件或改动；保留用户已有未提交内容及换行格式，不使用 `git add .` 混入无关改动，不以 reset/clean 清理用户工作区。
- commit message 使用英文短句与 `feat:` / `fix:` / `chore:` 前缀。不提交 `.gradle-user-home/`、`.kotlin/`、本机签名配置、密钥或发布令牌。
- 应用源码仓库为 `https://github.com/aalm1233/lolita`，当前默认发布分支为 `master`。隔离开发分支使用 `codex/` 前缀，验证完成后将本次提交集成到目标分支，再推送；用户指定其他分支时以其要求为准。
- 推送前核对远端状态，使用普通推送，遇到非快进先处理远端新增提交，不强制覆盖。推送后核对远端目标分支包含本次提交，最终反馈提交号和推送结果，不能把本地提交当成已推送。
- 纯文档更新无需增加应用版本号、重新构建 APK 或创建 Release；核对文档差异后提交、推送即可。代码改动仍须先通过相应构建和验证。

### 应用改动默认发布流程

1. 更新 `versionCode` / `versionName` 和 `AGENTS.md`，按改动运行构建及必要回归，先提交正式源码。
2. 从该源码执行 `clean assembleRelease`，核对包名、版本、非调试属性及原正式签名；正式 APK 必须支持同签名覆盖安装。签名参数沿用本机配置，不进入仓库。
3. 将正式包命名为 `lolita-<version>.apk`，生成 `SHA256SUMS`。创建指向该源码提交的 GitHub 标签 `v<major>.<minor>.<patch>`，推送源码目标分支及标签；不改写已发布标签。
4. 在 GitHub 和 Gitee 同步发布同一个已签名 APK 与 `SHA256SUMS`。GitHub 保留旧版应用的检查入口；Gitee 为新版应用的国内检查与下载渠道。只推送 Git 代码或标签不等于发布完成。
5. Gitee 仓库 `https://gitee.com/aalm1233/lolita` 仅维护分发说明和附件，标签指向其分发说明，Release 正文注明 GitHub 源码提交，不能宣称 Gitee 标签对应完整应用源码。
6. Gitee Release 先标为预发布，上传附件后匿名完整下载并核对 SHA256，再改为正式。确认公开版本 API、稳定 APK 地址及 HEAD 跳转链可用；不保存 CDN 临时链接。详细规则见下文“Gitee 版本检查与 APK 分发”。
7. 验证两个平台的正式 Release、附件版本与摘要一致，再反馈发布页和 APK 链接。令牌仅通过本机环境或仓库外临时文件使用，不打印、不写入源码或 APK；任务结束清理临时凭据。

- 经用户授权，Gitee 发布令牌持久存放于仓库外 `/Users/dengtong/.config/lolita/gitee-release-token`（目录 700、文件 600）。发布脚本从该文件读取，不打印内容，不复制到仓库、APK 或附件；只清理临时凭据副本，保留此本机配置。

## AGENTS.md 维护

- 每次功能更新、架构变更或新增约定后，**必须刷新 `AGENTS.md`**，确保文档与代码同步。
- 新增组件、令牌、接口、页面、测试等均需在对应章节补充说明。
- 若不确定是否需要更新，默认更新。

## 数据完整性验证

- 审计报告：`docs/data-integrity-audit-2026-10-11.md`；升级与备份用例：`docs/data-integrity-test-plan.md`。
- 运行 `python3 scripts/audit_data_integrity.py` 可用临时 SQLite 复现迁移与 WAL 快照问题；退出码 1 表示数据保留断言失败，2 表示工具错误。不能代替 Android Room 和图片恢复测试。
- 升级与备份验收需独立基准逐字段、关联、图片 SHA256 核对；USB G13 仅验证文件选择器，不证明无损恢复。

- 父表重建迁移使用 `MigrationDataGuard` 保全价格、付款、穿搭关联和图鉴链接，兼容外键开启/关闭；12→13 无需重建时不创建临时快照。
- 备份格式 version 5、schemaVersion 23；完整 JSON/ZIP 覆盖 24 个业务/缓存实体，`RestoreJournal` 为本机恢复操作日志不进入备份。未知格式/字段/枚举及损坏数据必须拒绝，旧字段通过 `BackupFormat` 显式迁移。
- ZIP 使用图片 SHA256 清单；恢复先创建独立图片文件，再在 Room 事务内写入并逐字段核对，不覆盖在线主库、不替换旧图片目录。旧图片保留，共享图片删除需检查所有持久引用。
- 恢复后的闹钟/日历处理由事务提交的 `RestoreJournal` 支持启动重试；数据库失败不删除旧日历。普通账本修改使用 `affectedPriceIdsJson` 限定订单范围，并合并未完成日志；恢复、启动和重启显式恢复须升级为全量（null 范围）。`ReminderCleanup` 按付款 ID 与日历指针去重，保留同阶段不同旧日历指针，权限失败重试不得无限累积。
- 数据回归：`./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug`，仪器化测试资源为 `app/schemas/`；用例定义在 `BackupFormatTest`、`StageAccountingTest`、`ImportAccountingTest`、`DataIntegrityTest` 和 `OrderBusinessTest`；快捷穿搭筛选与已有照片/日期保全回归位于 `QuickOutfitLogFilterTest` 和 `QuickOutfitLogViewModelTest`（独立内存 Room）。

- 历史颜色/图片单值转换使用 `MigrationJsonLists`，通过 JSON 编码保留引号、反斜线和换行，禁止手工拼接 JSON 或全局去除合法转义。

## 服饰、小物与付款业务

- `OrderBinding` 对 itemId 和 priceId 唯一约束，仅绑定一个当前订单；旧 Price/Payment 原记录完整保留，多价格与异常金额等待核对。愿望单无实付证据的历史预算不自动绑定。
- `Price.shipping` 已包含在 totalPrice 中；阶段金额与实际付款分离。新输入最多两位小数，计算使用 `OrderAccounting` 整数分，历史 Double 原值不改写。
- `OrderLedger` 在 domain/usecase 中集中校验、事务记账、状态刷新及提交后的提醒恢复；`OrderRepository` 为薄封装。修改价格和阶段不得重建实付，删除阶段不得减少未付余额。
- 完整账本（`TRACKED`）付清=已拥有，未付清=待付款；定金尾款仍用待补尾款文案。有当前订单时服饰编辑不能手动覆盖状态；无价格不能默认零元付清。
- Item.parentItemId 与 includedInParentPrice 支持一层小物关系，独立于 Coordinate；禁止自挂、循环、多层关系与删除有小物的主件。共用价格的小物继承主件状态，独立计价的小物单独记账。
- 价格页只允许设置/编辑唯一当前订单；加号用于阶段和实付。主件编辑与详情支持搜索挂载、新建、解绑；列表默认主件，搜索/筛选保留小物。
- 价格与付款页先展示状态、订单金额、实付和余额；价格明细、记账和购买资料说明可展开，历史未知余额、日期缺失、只读及超额提示必须常显。`PurchaseRecordNotice` 继续复用于价格/付款编辑，展开只影响解释文案。付款计划、实付和保留的旧未付记录采用紧凑分组，每条仍为独立 Lazy item，分别使用 `stage-`、`payment-`、`legacy-payment-` key，并保留原回归 testTag；只有组头显示 `SectionHeader`，首尾圆角与分隔线消费皮肤令牌。
- `StageAccounting` 将未指定阶段的实付按计划顺序投影抵扣，不修改账本；阶段提醒/日历合计不得超过订单整体余额，历史计划超额保留原值供核对。日期未知的实付保持 null，不用应付或创建日期伪造月支出。到期日未知的阶段不设置提醒；提醒代理 ID 为负阶段 ID，缺失 ID 哨兵不可用 -1。
- 淘宝订单数量拆分，实付和邮费按整数分分配且逐项确认，保留来源订单与原始金额；不凭名称自动合并服饰或小物。

- 消费分布只保留当前维度的数据订阅（`flatMapLatest`），加载态与最终态须由同一 collector 顺序发布，切换方法仅更新选择，避免空数据快速切换卡在加载状态。消费趋势用年度 StateFlow 与固定 DAO 订阅组合，切换不得累积旧 collector。统计基于当前绑定订单的实付；空风格/季节纳入未设置分组，多季节通过 `SpendingBreakdown` 按整数分均分并保全合计。日期未知的实付由 `getUndatedSpending` 单列提示，不归入任何年份。回归位于 `SpendingBreakdownTest` 与 `SpendingStatisticsViewModelTest`（独立内存 Room，覆盖付款修改、维度/年份切换及实付日期）。

- 历史付款资料：`Price.purchaseRecordStatus` 永久保存购买证据，`OrderBinding.purchaseRecordStatus` 为当前查询投影，插入绑定与修改标记必须在 DAO 事务内同步两者；区分 `TRACKED`、`HISTORICAL_OWNED`、`NEEDS_REVIEW`。缺少付款记录不等于已付 0 元；历史已购保留 `OWNED`，待核对使用 `PENDING_REVIEW`，均不投影为欠款/逾期或发送尾款提醒。账目不完整的实付/余额显示未知，消费统计只计原始实付证据，不推断金额或日期。
- 21→22 将明确 `OWNED` 且实付资料不足的旧绑定保留为历史已购；无付款、无阶段且已购证据丢失的绑定标为待核对。22→23 与格式 1～4 备份迁移仅将已有绑定证据复制到对应 Price，无绑定旧价格为 `NEEDS_REVIEW`；归档、共用主件价格、解绑和重绑不得从服饰状态或其他订单推断证据。格式 5 必须保存 Price 标记并验证与绑定一致，CSV 和预览同步覆盖归档订单证据。
- 历史购买回归位于 `HistoricalPurchaseTest`：覆盖 20/21→22、仅旧计划/部分实付的已购证据、待核对确认、正常新欠款、归档再绑定、备份恢复、日期未知补录/编辑、参考信息不自动记账与原始金额保全。`HISTORICAL_OWNED` 的阶段与价格只读，可补录/更正真实付款，核对账本并启用跟踪后再管理计划与提醒。
- 付款提醒恢复与 `PaymentReminderReceiver` 均需检查当前绑定为 `TRACKED`，避免历史已购/待核对订单的旧闹钟再次发出欠款提醒；历史补录的日期初始为空，用户选择真实付款日期或明确勾选“付款日期记不清”；未知日期保持 paidDate=null，旧必填 dueDate 使用 0 占位，计入消费总额/分布但不归入年/月。正常新付款仍要求实际日期；编辑原日期未知的真实付款可保留 null。
- 历史购买修复与源备份验收说明：`docs/historical-purchase-fix-2026-10-11.md`。旧程序已生成的付款无原始来源证据时不得自动删除；测试库纠正通过原始 ZIP 官方恢复并独立逐字段/图片 SHA256 核对。

- 付款补录页展示服饰、来源、订单总价、已录实付与保留的淘宝订单说明；“填入参考金额”仅提供可编辑参考，不自动保存或据总价生成实付。`OrderRepository.recordHistoricalPayment` 明确限定当前历史已购绑定，整数分校验不变，完整保留历史已购证据。

- `ItemDao.getPurchaseReviews()` 通过单条 Room SQL 投影 `ItemPurchaseReview`：无当前订单的非愿望服饰、当前订单非 `TRACKED`、当前真实付款日期未知均纳入资料核对。共价小物按主件订单判断，独立小物按自己判断；归档原账本不改写，有完整当前订单时不因归档资料重复纳入。首页、`status_review` 清单、详情与统计统一订阅 `ItemRepository.getPurchaseReviews()`，展示相同件数与核对原因，不受搜索/分类过滤影响。首页及统计的 `PurchaseReviewShortcut` 仅在待核对件数大于 0 时展示；核对完成清空后实时隐藏，新待办出现时恢复。价格页的共价主件核对及无价服饰状态核对也按同源单项投影控制，愿望服饰和完整账本不显示无关核对按钮。
- 服饰详情顶部 `ItemOrderSummary` 明确提供“核对购买资料”，共用价格的小物直接进入主件价格核对；价格页也提供“前往主件核对”。无绑定旧价格必须先选定当前订单，确认继续复用 `OrderLedger`；完全无价格时提供“核对服饰状态”进入既有编辑页，确认拥有后仍保留价格资料待办，不生成价格或实付。有当前绑定或共价的小物不得手动覆盖订单状态。
- 入口与购买资料核对回归位于 `PurchaseReviewEntryTest`、`ReviewItemListViewModelTest`、`PurchaseReviewConsistencyTest` 和 `PriceManageScreenReviewTest`，覆盖零待办隐藏、正数与零动态切换、主件跳转、首页/清单/详情/统计一致联动、历史确认及部分/全部补录后待办保留、明确核对账本后移除、未知日期更正与价格/阶段/实付原值保全，使用独立内存 Room及独立设置。

- 拥有状态与资料完整性必须分开：确认历史已购只更新购买证据并保留 `OWNED`，不能将资料待办移除；部分或全部补录都不自动切换 `TRACKED`。用户明确核对付款完整及余额一致后才解除账本待办，日期未知的实付仍单列待补；未知日期不得自动补造，也不影响已明确核对余额的正常付款跟踪。统计按当前订单去重计笔，快捷核对按服饰计件并明确单位。
- 历史核对入口覆盖仍有余额的正常账本，包括部分实付；“确认历史已购”不生成付款。“核对账本并启用跟踪”展示总价、已录实付与计算待付，要求用户确认真实付款已全部录入且计算余额等于实际待付；`confirmTrackedOrder` 在领域层也拒绝未确认请求。回归位于 `PersistentOrderEvidenceTest`，覆盖归档小物共用价格后重绑、订单切换、22→23 原值保全、归档证据备份恢复及确认失败事务不变。
- 付款通知使用 `PaymentReminderIdentity`（阶段代理 ID、价格 ID、到期日、提前天数）与本机 `PaymentReminderDeliveryStore` 持久送达记录。同一配置成功送达后，重启或修改其他订单不得重复补发；权限关闭或发送失败不记送达。应用和日历使用一致的提前天数，日历最少提前一分钟。策略与清理回归位于 `PaymentReminderPolicyTest`、`ReminderCleanupTest`、`ReminderJournalTest` 和 `PaymentReminderDeliveryStoreTest`（独立 SharedPreferences，覆盖送达持久化）。
- 今日穿搭用 Room Flow 订阅本地当天半开区间 `[start, nextDayStart)`，同日多条按日期、ID 倒序选择最新记录；保存、编辑、删除自动刷新。跨日、回到前台与系统日期/时间/时区变更须切换查询范围，次日边界用 Calendar 计算以兼容夏令时。回归位于 `TodayOutfitViewModelTest`。
- 统计概览分别展示“当前订单实付”和包含归档订单的“全部已录实付”；后者仅汇总真实已付记录，归档、共用主件价格不得删减原实付。显示当前缺付款资料订单数；“订单参考均价”明确来自当前已拥有订单总价。分布、趋势继续使用当前绑定订单口径，回归位于 `SpendingOverviewTest`。

## 提醒联动与残余改动回归

- 每日穿搭提醒配置通过 `AppPreferences.outfitReminderConfig` 原子读取 enabled/hour/minute；旧设置没有 minute 时默认 0，保存时间必须调用 `setOutfitReminderTime`，更新后从最新配置 `reschedule()`，不能用 UI 旧快照安排闹钟。
- 每日提醒使用 `DailyOutfitReminderPolicy` 按本地日期、时区及墙上时间计算下一次，广播校验当前配置和目标日期；关闭、改时间、已有今日穿搭或当天已成功送达时不再次通知。送达回执保存在独立本机设置，不改服饰/账本或穿搭记录。
- 设置页开启提醒前申请 Android 13+ 通知权限，拒绝、应用总通知关闭或穿搭提醒渠道关闭时提供对应系统通知设置入口；UI 和 Receiver 共用 `NotificationChannelSetup.canShowOutfitNotifications`；时间输入保留分钟且使用 Compose 对话框。通知的快捷穿搭入口由 `MainActivity` 冷/热启动统一消费，导航恢复时不重复打开。
- 日历创建需要 READ+WRITE，删除仅需要 WRITE。只选 `CAL_ACCESS_CONTRIBUTOR` 及以上日历；Events 与 Reminders 使用批量操作和回引用共同提交。无权限/无可写日历可仅保留应用闹钟；Provider 实际操作失败必须保留 `RestoreJournal` 重试，不能当成同步成功。
- 本轮残余改动业务审计与验证记录：`docs/remaining-changes-business-audit-2026-10-11.md`。外部日历测试使用独立内存 Provider，每日设置测试使用独立 DataStore，禁止读写用户数据库和系统真实日历。

- 付款管理同一个 LazyColumn 混合显示付款阶段与真实实付，key 必须分别使用 `stage-${id}` / `payment-${id}`，不能直接共用两张表各自的 Long 主键。`PaymentManageScreenTest` 用独立内存 Room 与真实 Compose 渲染相同 ID 的两组记录，覆盖同屏及往返滚动，并核对原 ID/金额不变。

## Gitee 版本检查与 APK 分发

- 国内公开分发仓库为 `https://gitee.com/aalm1233/lolita`，应用源码仍在 GitHub。个人页“检查更新”读取匿名 Gitee 发布列表，筛选非预发布的三段数字版本并取最高；`ReleaseVersion` 按整数比较，支持 `v` 前缀，本机较新不提供降级。发布令牌不得进入源码或 APK。
- API 查询最多 5 页 × 20 条、总 JSON 1MiB、整体 15 秒；若历史超过上限则失败，不猜最高版本。Gitee Release assets 不要求 GitHub 独有的 state/draft/html_url 或缺失的 size 字段。
- `GiteeReleaseApi -> AppUpdateRepository -> AppUpdateViewModel (StateFlow)`，AppModule 提供仓库；MainActivity 持有全局更新 ViewModel，个人页复用，AppUpdateHost 在根主题内显示对话框。关闭后迟到结果不重新弹出。
- 新启动自动检查一次，过程静默；仅新版且明确 APK 通过 HEAD 探测才提示。无新版、无 APK、超时、解析或链接异常不提示。ViewModel 阻止旋转/重组重复请求，新进程恢复仍检查；不放 Application/onResume 或后台调度。自动转手动共用查询，已取得 Release 时立即显示并取消探测。
- 探测连接/读取各 3 秒、整体 8 秒，最多 3 次 HTTPS 重定向；只接受指定 Gitee/GitHub 资产主机，最终 200、非空 APK/二进制/ZIP 类型。Gitee 实际 CDN 为 foruda.gitee.com，APK 类型可为 application/zip。自动检查整体 15 秒；浏览器使用原稳定附件地址，不保存临时 CDN 地址。
- 新版对话框提供发布页和 APK 链接，由外部浏览器下载与系统覆盖安装；优先 `lolita-<version>.apk`，否则只接受唯一可信 APK。链接限制 HTTPS、本仓库、本标签，不猜下载地址。
- Gitee 发版先暂标预发布，上传同签名 APK 和 SHA256SUMS，匿名完整下载核验后改正式；普通仓库单附件 100M、总附件 1G。Gitee 标签对应分发说明，源代码以 GitHub 为准。
- 不申请安装权限、不在应用内下载/安装、不新增更新元数据或修改业务备份。发布继续保持同一签名、递增 versionCode，并使用 `v<major>.<minor>.<patch>` 稳定标签。
- 回归：`ReleaseVersionTest`、`GiteeReleaseApiTest`、`ReleaseDownloadProbeTest`、`AppUpdateViewModelTest`、真实 Compose 的 `AppUpdateHostTest`；发布与行为记录见 `docs/release-download-and-auto-check-2026-10-12.md`。

## 搭配、分享与月度预算（2.43.0）

- 详情推荐入口进入整套搭配：本地按角色补齐、支持自定义属性、锁定/跳过/替换，保存前明确确认服饰移入新套装，事务拒绝过期归属快照。
- 套装与穿搭详情支持分享预览，缓存 PNG 经 FileProvider 调起系统分享；仅包含标题、图片、服饰名称和可选品牌，不读取金额、备注、渠道与位置。每六件分页，消费当前皮肤的浅暗配色。
- 统计第六页签为预算：每月独立额度，本月实付按 paidDate 归月，剩余阶段复用 STAGE_BALANCES；此前待付计入预计余额，未定日期的待付和实付单列。金额隐私设置实时生效。
- 新实体 MonthlyBudget、BudgetDao、BudgetRepository 接到数据库及 AppModule；23→24 只新增预算表，备份格式 6 支持预算 JSON/ZIP/CSV/预览/恢复，格式 1–5 缺失预算兼容为空。
