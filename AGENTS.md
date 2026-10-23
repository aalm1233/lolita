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

## 新增功能的设计要求与约束

- **新增功能及既有功能改造必须先阅读并执行 [Lolita 产品与界面设计规范](docs/design-guidelines.md)**；任务开始时连同本文件阅读，并按规范末尾的设计记录与完成检查执行。纯后台或纯数据变更按实际影响执行适用条款。
- 新增用户可见功能必须按“艺术家角色绘制界面图 → 七套皮肤浅暗效果 → 实现 → 真实页面对照验收”推进，图稿先于实现并归档到本次设计文档。
- 设计阶段同步明确数据升级、旧备份兼容、图片缩略与完整展示、隐私和付费边界；只把本次实际涉及的场景纳入实现与验收，不凭规范擅自扩大功能范围。
- 共用设计要求统一维护在上述规范，具体架构、组件、构建和发布仍遵守本文件；新增或调整长期约定时同步两处，用户最新明确要求优先。
- 整体体验与视觉提案见 `docs/plans/2026-10-22-creative-experience-review.md`，包含2.66.4正式包模拟器走查、三页面概念稿和七皮肤浅暗方向。本轮仅设计，未实现；其中个人页金额隐私问题、空态动作、实验室与试衣布局均为待实施项，不取代既有规范或验收记录。
- 第二轮体验提案见 `docs/plans/2026-10-22-creative-experience-review-part2.md`：价格计划、任务结果、收纳、备份导出／恢复预览取消及导入指南走查，三页主稿与可检视的七皮肤浅暗稿。仅设计，未改应用；HTML预览检查不代替Compose验收，测试ZIP未执行恢复，第一轮金额隐私问题仍待修复。
- 上述两轮提案于2.67.0开始实施，最终采用范围与原生验收以 `docs/creative-design-implementation-2026-10-22.md` 为准；前两条保留原设计阶段记录。实施计划为 `docs/plans/2026-10-22-creative-design-implementation.md`。
- 第三轮功能体验见 `docs/plans/2026-10-22-order-import-experience-review.md`：2.67.0独立AVD实测淘宝订单重复导入、金额解析归零、多文件金额冲突、草稿回收、部分导入续办与320dp两倍字体问题，并提出核对/收纳衔接建议。本轮仅审查，未实现；截图与虚构订单证据在 `docs/visuals/2026-10-22-order-import-review/`，实施前仍须归档界面设计与七皮肤浅暗方案。

- 价格专项体验见 `docs/plans/2026-10-23-price-experience-review.md`：2.68.0独立AVD复现付款旋转丢输入、年历金额隐私遗漏、心愿录价转待付款及超额禁用缺反馈，并审查年历日期、历史补录和预算衔接；四场景提案与七皮肤浅暗归档在 `docs/visuals/2026-10-23-price-review/`。本轮仅审查／设计，未改应用；浏览器原型检查不替代原生验收。

## 架构与实现约定

- 新 DAO 必须同步接到三处：`LolitaDatabase`、对应 `Repository`、`AppModule`。
- ViewModel 用 `MutableStateFlow`/`StateFlow` 管 UI 状态，异步放 `viewModelScope.launch`。
- Repository 保持薄封装，围绕 DAO 响应式查询 + 少量业务拼装，不塞重逻辑。
- `List<String>` 字段复用现有 Gson `TypeConverters`；新增枚举字段需补 `TypeConverter`。
- 风格、季节、来源等字符串属性支持重命名/删除时，Repository 必须级联更新所有引用该名称的 Item 记录。
- 领域层在 `domain/`：`domain/model/`、`domain/usecase/`（如 `MatchingEngine.kt`）。

## 数据库

- Room 数据库版本33，42个实体；32→33仅追加六张订单导入会话／来源表，迁移在`OrderImportSessionMigration.kt`，备份格式14；31→32仅追加ImageTask的三个可空批次字段，迁移在`MaterialBatchMigration.kt`；30→31追加独立`ItemTryOnMaterial`、任务分层信息及试衣结果底图副本，迁移在`TryOnMaterialMigration.kt`；29→30增量追加`StorageNode`、`ItemDraft`、`RecognitionTask`、`ItemEvidence`、`ImageProvenance`及相关列，迁移在`WardrobeMigration.kt`。原个人模特、后台任务、账本及历史归档全部保留。
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
- 统计总览按四项衣橱概况→消费概览／最高实付→独立品类概况→品牌排序；品类保留完整登记摘要、八类数量和导航。消费概览突出当前订单实付，全部已录实付与参考均价采用明细行，金额直接显示原始 Double 的两位小数，不通过 Float 动画改变显示值。金额、长品牌及最高实付信息需在小屏/大字体下完整排布；关闭金额设置时整块消费与最高实付隐藏。卡片只保留一层内边距，间距与圆角消费皮肤令牌，加载态使用匹配布局的骨架屏。真实 Compose 回归位于 `StatsOverviewScreenTest`（独立内存 Room、UUID 设置；含导航、实时隐私和浅暗七套皮肤截图）。 本轮预览归档在 `docs/visuals/2026-10-12-stats-overview-light.png` 和 `docs/visuals/2026-10-12-stats-overview-victorian-dark.png`。
- 详情页分区容器复用 `LolitaSection`（iOS Settings 风格：`SectionHeader` + 行内容 + 内嵌分割线，包裹在 `LolitaCard` 内）。新详情页优先用 `LolitaSection` 而非手动 `LolitaCard + SectionHeader`。
- 价格／付款编辑共用 `EditorSaveBar`，只有底部文字保存；子Scaffold设零Insets，内容消费padding，保存栏处理IME。付款先填真实实付，再看购买参考；付款阶段可展开编辑，摘要保留金额与未知应付日期，不改自动补算或原账本。
- 衣橱真空态提供新增／明确选择淘宝导入方式／恢复备份，筛选空态提供清除搜索和属性筛选并保留页面拥有状态范围；相应空态不重复显示FAB。穿搭空态提供记录动作。`SkinEmptyState`支持可选操作及插画；`WardrobeEmptyIllustration`仅为装饰，原始RGBA与来源见 `docs/creative-illustration-sources-2026-10-22.json`，不代表用户库存。
- 备份页突出ZIP，JSON／CSV使用次级导出，恢复后果常显；`BackupExportResult`仅在ViewModel保留实际返回URI／文件名／格式，供用户主动只读打开，打开失败不抹掉导出成功。导出、预览与恢复互斥，原备份格式与校验不变。个人页金额摘要实时消费显示金额设置，隐藏时无文字或语义占位，开启显示两位小数并允许换行。
- 任务中心以可用结果缩略图及核对／结果／原因动作为主，缺图用类型回退，不把输入图当输出，不自动保存或重试；批次与全局任务栏保留。实验室的两项目的入口共享层级，服务状态只代表本机配置；试衣人物使用紧凑完整Fit且可放大，物品选择先于可展开服务设置。房间封面紧凑展示并可完整放大，描述和低频管理按需展开；订单文件按钮旁直达原指南。
- 底栏在系统导航Insets上方按图标与当前文字行高增高，不使用负向标签偏移或压缩系统字号。中华背景、云朵与花瓣降低透明度，普通卡片边框弱于主操作；其余皮肤与原图不变。原生定向用例包括 `CreativeExperienceScreenTest`、`CreativeNavigationScreenTest`、`BackupExportResultTest` 及相关价格／试衣／收纳／实验室回归。
- 详情页分区标题复用 `SectionHeader`（左侧竖线 + 标题 + 可选操作按钮 + 分割线，皮肤感知）。所有详情页统一使用 `SectionHeader`，不使用裸 `Text(titleMedium/Bold)`。
- 图片展示区复用 `ImageFrame`（画框容器，皮肤感知边框/阴影/内边距）。
- 详情页信息区块用 `LolitaSection` 或 `LolitaCard + SectionHeader` 包裹，不用裸 Text + HorizontalDivider。
- Tab 页面沿用 `HorizontalPager + TabRow + SkinTabIndicator`。
- 首页今日穿搭入口仅在“位置”Tab显示，服饰/套装/心愿及其他页面不重复展示；使用 `TodayOutfitShortcut`：紧凑卡片、穿搭图标/单张缩略图、标题与状态副文案、记录/查看操作；两种状态共享布局，颜色/圆角/内边距消费皮肤令牌，并合并无障碍语义。
- 快捷穿搭记录支持名称/品牌搜索、分类筛选和已选缩略图移除；筛选不得清除隐藏的已选物品。控制区随网格滚动，底部保留数量保存按钮。编辑已有记录时保留照片、原日期及创建时间，读取失败或未完成时禁止保存。
- 完整穿搭编辑的日期、备注和关联服饰通栏，只有照片采用两列；卡片仅一层内边距，照片使用皮肤圆角。底部提供唯一“保存穿搭”按钮，消费 Scaffold Insets 后应用 IME padding，键盘打开时保存仍可达；保存条件和原照片、日期、关联、放弃更改逻辑保持。网格注册 `ObserveSkinScroll`。 完整编辑保留原记录并复制可编辑字段，创建时间不变、修改时间由 Repository 更新；读取原记录期间通过 `isLoading` 禁用保存，ViewModel 同时拒绝原记录未读取完成的提交。回归为 `OutfitLogEditViewModelTest`（独立内存 Room、真实照片，覆盖已有/新建/读取中/已删除）。
- 淘宝订单导入及后续整理已按 `docs/plans/2026-10-22-order-import-complete-design.md` 实施，前置14页交互稿与七皮肤浅暗保留在 `docs/visuals/2026-10-22-order-import-design/`，原生验收及最终差异见 `docs/order-import-implementation-2026-10-23.md`。Room33/42实体/备份14/2.68.0；新导入付款日期未知留草稿，不能借历史补录接口放宽。
- 淘宝导入指南按“获取订单文件 / 回到应用导入”分组，完整保留七步正文及四项注意事项；使用皮肤卡片与 `SectionHeader`，没有真实截图时不绘制截图占位框。月份的紧凑布局保持既定设计。
- 服饰详情主图完整 Fit 预览，高度随屏幕受限，标题后优先展示当前订单价格/实付/待付摘要；无小物时显示紧凑新增/挂入操作，已有小物管理完整保留。`ImageGalleryPager` 支持可选 `contentScale`、`imageShape` 与 `imageBackgroundColor`，默认展示保持兼容。
- 服饰页三列网格中，无真实品牌的假发（稳定品类语义`family=WIG`，包含发片）省略整行品牌文字和图标，不留品牌占位空间。`ItemListUiState.unsetBrandId`来自`Brand.isUnsetBrand`系统身份，不按中文名称判断；同名真实品牌仍显示。其他品类、单列/两列与画廊保持现有显示，不改品牌记录或物品引用。
- 首页顶栏保留搜索、四个 Tab 和筛选/排序/视图控制的一行布局。本轮列表、详情和穿搭实拍归档：`docs/visuals/2026-10-11-layout-refinement.png`。
- 选项多时（如品牌 200+）用可搜索对话框，不退回简单下拉框。
- 服饰编辑和淘宝订单导入的尺码使用共用 `SizeSelector`：内置 XS/S/M/L/XL/XXL/均码，支持“未设置”和旁侧皮肤加号快速添加并选用。`SizeOptionsViewModel` 合并内置、DataStore 自定义项和 `ItemDao.getSizes()` 的历史尺码，去首尾空格并忽略大小写去重，不改写历史 Item.size。自定义项为本机 `AppPreferences.customSizes` 设置，已使用尺码继续随原 Item.size 备份/恢复并自动成为选项；数据库和备份格式不变。导入颜色、尺码各占一行，避免小屏大字挤压加号。`ItemSizeSelectorTest` 与 `OrderImportInteractionTest` 使用独立 Room/DataStore，覆盖选择/清空、添加/取消/去重、持久化、旧资料保全、逐件导入及七皮肤浅暗大字体。

- 淘宝商品导入使用已核验内置别名解析品牌，匹配现有记录时忽略大小写/空格/标点并拒绝歧义；通用品牌参数不作为真实品牌。类别根据明确参数或标题识别，混合套装在预览提示核对。详情图来自 `#v6_pc_content`，读取懒加载源、去重过滤页面占位图，最多50张、每张20MiB、累计80MiB，保留原图进入多张尺码表。旧 `sizeChartImageUrl` 保留，新增列表 `sizeChartImageUrls` 存后续图片，24→25只追加默认空列表；备份缺失字段补空数组、ZIP/预览/CSV与删除清理均包含新列表。编辑与详情可逐张放大，失败或放弃编辑清理本次新增图片。
- 一个商品包含明确“套装/款式/类型”时，淘宝导入可勾选拆分，逐件进入服饰编辑；默认不按颜色或尺码展开，用户可按下文主动选择完整规格组合。每件独立推断类别、复制完整主图与详情到 UUID 文件，保存自动下一件，放弃暂停当前件。共享下载缓存仅由导入页拥有，退出不影响已保存记录。SavedStateHandle 保存紧凑队列与公共详情；每件稳定 `importToken` 与每次编辑 `editorToken` 分开，陈旧返回不会重放。Room25→26新增可空唯一 `items.import_token`，提交后未回传时恢复原记录，备份兼容缺失标识并拒绝重复标识；隐藏元数据不导出CSV。编辑器退出清理必须经 ImageFileHelper 的引用保护，保存中禁止返回。

- 详情读取兼容 `#v6_pc_content` 与新版 `.detail-desc`；新版只从详情图片的至多7层组件公开 `imageUrl` 取得懒加载原图，未解析完图片、缺失详情或加载失败时不得接受空/部分详情。仅含明确服饰名及“＋”组合的颜色分类可按单件去重，普通颜色与尺码默认不展开，不同明确前缀不得按类别合并，套餐价不下放单件。

- 淘宝参考报价读取现代 SKU 的明确规格组合，打开规格时优先实时面板；按套装/款式/类型关联物品，起价、区间价、定金、尾款及未确认款式不冒充全款。预览可逐件补填参考金额，紧凑队列恢复保留各件报价；来源说明的专用报价行可在设置价格页主动采用，仅填商品金额与默认全款阶段，确认保存才建订单、实付另行录入。旧的无款式页面报价不用于录价。
- 商品款式放在「颜色分类」时，可主动开启「按颜色分类分别导入」并勾选需要的款式；默认关闭、初始不全选；旧颜色模式不展开尺码，选择尺码时按下文使用组合模式。`colorVariants`只保留导入会话候选，现有明确拆分优先；选中项按原顺序逐件编辑、默认心愿，名称包含规格原文，每件独立图片副本且完整保留详情。选择、逐款报价和开关随SavedState恢复，普通颜色不开关仍按单件；已保存记录不迁移。只有完整选中的实际SKU报价关联对应颜色项，其他报价留空。Room30/备份10不变，测试为`TaobaoProductColorStylesTest`、`TaobaoProductColorStylesScreenTest`及扩展的ViewModel/ItemImportRecovery回归。设计见`docs/plans/2026-10-19-taobao-color-styles.md`。

- 淘宝链接可主动开启「按颜色和尺码分别导入」，按颜色／款式分组勾选具体尺码组合，同色可选多码，支持搜索且保留隐藏选择；默认关闭／零预选，没有尺码时继续旧颜色模式；组合模式以所选完整规格为准，关闭时保留原明确部件拆分。公开SKU路径存在时只用完整路径，缺路径时仅提供公开规格候选，不断言库存；超过1000个候选或资料不完整时明确回退单件核对，不截取部分规格。每次最多30件。每件写入明确Item.size、独立报价和importToken；完整SKU报价不能下放同色其他码或其他款式。`selectedSkuId`和逐件尺码随紧凑SavedState恢复，新增元数据可空以兼容旧Gson会话；旧队列继续共享原尺码。Room30／备份10／原图片与默认心愿不变。设计与定向回归见`docs/plans/2026-10-20-taobao-size-combinations.md`。

## 订单导入会话与原物品补充（2.68.0）

- `OrderImportDao → OrderImportRepository → TaobaoImportViewModel` 保存版本化会话、文件和逐件原始输入；会话递增revision做CAS，旧写入不能覆盖新输入或复活已删除草稿。离页flush，恢复只查本机回执，不自动联网或提交。移除文件先冻结并确认影响范围，已保存物品与永久来源回执保留。
- `OrderImportSource` 保留金额原值并区分有效0/空白/异常，来源使用公开商品ID、原规格和规范件号；行重排不改变已识别单元的整数分分摊。完全相同来源合并，不同完整快照必须明确选用；历史无回执不按名称自动判重。同来源已删除时必须明确确认重新登记。
- 单次导入冻结操作ID／会话revision，Item、账本、来源与提交回执在同一事务写入。并发查重、金额累计范围、日期、配对与图片在事务内复核，失败整批回滚；成功未返回可按回执恢复。来源关联原物品只记来源，不新建Item或账本。本次新增与关联原物品分列，已删除新增保留历史数量。
- 订单文件最多10份、每份20MiB／10000行、数量1—100、会话展开2000件；表头显式匹配，长订单号保留文本，拒绝公式和精度已损失的数值订单号。POI前先验证ZIP条目、总解压量和压缩比例，不静默截断。
- 导入选择支持搜索、状态／店铺／日期与日期未知筛选；隐藏选择保留。逐件／列表与批量品牌／类型操作分开，财务确认不可批量跳过。金额、日期与尾款独立通栏；键盘弹出及金额更新后主动保持焦点字段可见，唯一底部主动作复用`EditorSaveBar`。
- 购买核对使用原`ItemPurchaseReview`投影，原因计数重叠，共享订单只处理一次；只跳转原价格／实付页面，不推算付款。收纳只使用本次新增且当前已拥有ID，状态或更新时间变化时拒绝陈旧批量移动。
- `Screen.ItemSourceSupplement`绑定原Item，复用现有公开网页提取；不开逐规格新建队列。候选保存在同一`ItemDraft`，图片列显式存储、JSON仅用索引。默认保留原字段，主动确认SKU后合并所选字段，图片按SHA追加且保留封面；已有编辑草稿只合并到草稿，正式保存才写`ItemSourceRecord`。CAS拒绝陈旧覆盖；候选价格只留证据，不写账本。材质等网页参数通过明确勾选追加备注，不擅自映射结构化规格。
- 六张新表与补充候选同步进入JSON／ZIP／预览、严格引用校验和图片保护；旧1—13备份新增集合为空。CSV只增加可读公开链接及来源明细，不输出内部来源键。定向用例：`OrderImportReliabilityTest`、`OrderImportSessionTest`、`ItemSourceSupplementTest`、`OrderImportSessionMigrationTest`、`OrderImportExperienceScreenTest`，以及原ItemDraft／商品队列／备份回归。真实淘宝账号、TalkBack人工朗读与付费服务不由受控测试冒充验证。

## 内置店铺图标与默认房间

- 离线清单位于 `app/src/main/assets/builtin/catalog.json`，包含从测试资料核验的 14 家店铺真实商标/官方头像与卧室、客厅、衣帽间、书房、玄关、储物间六张生成插图；来源、SHA256 和完整生成提示词见 `docs/builtin-assets-sources-2026-10-12.json`。未经核验的店铺保持首字回退，禁止生成仿制商标或按相似名称套图。
- `BuiltInLibrary` 在启动时一次性事务追加缺失默认房间、补充精确名称/核验别名匹配的空品牌图片。保留用户原图片及已有实体所有其他字段，不合并位置或品牌；本机 `builtin_library/initialized_v1` 在事务提交后保存，用户删除、改名或移除图片后重启不补回。
- `ImageFileHelper.copyAssetToInternalStorage` 将原素材复制为 `filesDir/images/` UUID 文件，实体只存正常本地路径，继续纳入现有 ZIP 图片清单与恢复校验。数据库版本和备份格式不变。
- `BrandRepository` 新增品牌可通过可选 `BuiltInLibrary` 自动提供精确匹配的默认图，插入失败清理新文件；编辑、去图不触发重新补图。`AppModule` 接线；`BackupManager` 可选初始化屏障位于现有串行操作锁之前，恢复后不得再追加默认数据。
- 真实商标使用 `BrandLogo` 共用完整 Fit 预览与中性浅色底，兼容横向字标及暗色皮肤中的透明黑色商标；管理列表和新增/编辑预览复用该组件。
- 实拍及真实旧包升级核对见 `docs/builtin-assets-2026-10-12.md`（含浅/暗房间与横向透明商标）；原测试资料 140 件服饰、215 个品牌及四个位置保全，图片逐项 SHA256 验证。
- `BuiltInLibraryTest` 使用独立内存 Room、UUID 设置和测试图片目录，覆盖历史资料保全、并发/重复初始化、删除/改名/去图、失败/取消清理、新品牌插入和 ZIP/恢复屏障。设备验证必须定向到独立 AVD，不能清理用户设备数据。
- 2.41.0 / 91 用 clean 正式构建完成14店铺与6房间素材发布验证；已发布2.40.5原包同签名覆盖升级，原24张表、140件服饰、215个品牌、4个位置及190张原图片保全，新图片SHA256和重启幂等核对通过。记录见 `docs/builtin-assets-release-2.41.0-verification.json`。

## 界面文案约定

- 通用记录使用「物品」，既定首页「服饰」Tab 保留；统一「心愿」「显示金额」「拥有状态」「类型」「关联主件／解除关联」。付款状态不表达物流收货；心愿合计为「心愿参考总价」，现有拥有比例为「已拥有占比」。
- 可选解释使用共用 `ExpandableInfo`，订单／记录明细、统计口径及备份详细数量默认收起。未知金额／日期、历史只读、超额、核对、删除／恢复后果、实际上传与费用确认必须在相关操作处直接可见，不能随帮助折叠。
- `uiErrorText` 保留可执行的中文业务校验，文件／授权失败给出恢复动作，内部路径、服务原文及异常链回退到操作结果提示；不改变存储的任务请求和冻结协议。`UiErrorTextTest` 验证私密详情不进入用户文字。
- 房间列表只显示名称、记录数与图片；有记录但无照片不能表达为空。用户房间描述保留并在详情展示。物品详情顶部为唯一主要订单摘要，历史订单及完整账本仍可查；年历年度金额可换行，月份原紧凑布局保持。
- 新安装默认来源使用「闲鱼」，不修改历史迁移或静默改名既有资料。完整 ZIP 含图片，JSON 仅数据，CSV 供查看且不能恢复；恢复确认先显示替换后果及缺图／核对警示，全部数量可展开。
- 设计、43 路由报告映射、七皮肤浅暗／小屏大字与真实 IME 验证见 `docs/plans/2026-10-22-ui-copy-optimization.md`、`docs/ui-copy-implementation-2026-10-22.md`。Room32／36实体、备份13、原图片及付费协议保持。

## 字体规格

- `Type.kt` 的 `lolitaTypography` 为七皮肤唯一文字规格来源，`SkinConfigs` 仅传入各自标题字体。主卡22/30sp、顶栏`appBarTitle`18/26、分区18/26、行标题16/24、正文与输入16/24、紧凑解释14/22、说明13/20、按钮15/22、`metadata`与底栏12/18；主功能／分区标题600、顶栏／普通行与按钮500、阅读正文400，字距0。普通行用`titleSmall`，`titleMedium`专门表达分区。
- 个人页“实验室”一级卡片标题仍用`titleSmall`16/24sp、Medium500及阅读字体，与属性管理等同级入口一致；不能因FEATURED容器就改成22sp主功能标题。公共顶栏18/26sp、Medium500，与18sp/600分区通过字重、位置区分。修正设计见`docs/plans/2026-10-21-compact-laboratory-titles.md`。
- 个人页“实验室”入口复用 `SettingsMenuItem`；FEATURED 仅保留表面装饰，内边距与普通入口共用 `cardInnerPadding`。40dp 图标底座、22dp 图标、12dp 图文间距、18dp 箭头及标题／说明列与属性管理等一致。独立 `IconKey.Laboratory` 经 Base 与七套实际 provider 提供不同烧瓶主体，绘制在 `PolishedLaboratoryIcon.kt`；其他51键不变。设计及测量回归见 `docs/plans/2026-10-22-laboratory-entry-alignment.md`。
- 标题保留皮肤字体；正文、输入、操作、元数据统一阅读字体，大号金额／数值用`numericReading`。不按2K原生像素倍增sp，不限制系统fontScale；高频控件采用最小高度自然增长。首页紧凑搜索14/22为保留一行布局的明确例外，微型年历与字体演示按用途单独处理。
- 首页标签采用无横向内边距的Tab内容插槽，搜索与右侧控制统一32dp。筛选数量角标保留12sp，按实际高度为图标上方预留空间，数量0/3/12与普通／两倍字体下保留符号下半部；不只负向偏移导致裁切。
- 设计与验收见`docs/plans/2026-10-21-typography-system.md`与`docs/typography-implementation-2026-10-21.md`；`TypographyScreenTest`验证真实2K／七皮肤浅暗层级与阅读字体、小屏两倍字和实际键盘。

## 皮肤系统

- **7 套皮肤**：`DEFAULT` / `GOTHIC` / `CHINESE` / `CLASSIC` / `NAVY` / `COUNTRY` / `VICTORIAN`。
- 新页面/交互复用皮肤感知组件，不做脱节的普通 Material 页面。
- 新增图标需同时补：`BaseSkinIconProvider` + **7 个**皮肤专属 provider（`SweetIconProvider`, `GothicIconProvider`, `ChineseIconProvider`, `ClassicIconProvider`, `NavyIconProvider`, `CountryIconProvider`, `VictorianIconProvider`）+ `IconKey` 枚举。图标是 Canvas 手绘，不是 Material icon。
- 七套图标通过 `PolishedSkinIconProvider` 共用完整的 52 个 `IconKey`；绘制入口 `PolishedSkinIcon` 按导航、内容、操作/箭头/状态分派到 `PolishedNavigationIcons`、`PolishedContentIcons`、`PolishedControlIcons`；后者仅分派到 `PolishedActionDrawings` 与 `PolishedDirectionStatusDrawings`，分别管理操作及方向/状态造型。旧的田园/维多利亚装饰图标实现已移除。 七套保留统一网格与入口，各绘制组按皮肤维护主体轮廓：甜美饱满曲线、哥特尖拱切角、中华飞檐交领、古典对称收束、海军灯塔舷窗、田园宽檐围裙、维多利亚拱形层叠。导航、内容、高频操作都需体现造型语言；不得仅改圆角/微内纹或给同一主符号套装饰框。五个导航造型沿用已认可版本；其余 47 个键在七套皮肤中均需保持不同的主体构件/轮廓/笔势，包括加号、保存、分享、九宫格、下拉及显隐。基础控件保持直接语义，不能只加端点小装饰。
- 图标使用 `SkinIconCanvas` 的 24×24 逻辑坐标，主线宽 1.7，主体保留约 2 单位安全边距；调用方尺寸优先，非正方形区域居中等比缩放。七套底栏图标统一 26dp，选中使用皮肤 accent，未选中使用 `onSurfaceVariant`。
- 功能图标保留明确语义（加号、垃圾桶、刷新箭头、图表、齿轮）；风格通过全套图标的主体造型、工具构件与笔势体现，避免在 24dp 中叠加盾牌/吊牌/缎带缩小功能符号。全部颜色来自 tint，派生透明度必须乘原 tint.alpha，不使用固定奶油色/象牙色；实心/空心星标共用同一轮廓。
- 图标回归：`SkinIconVisualTest` 在 Android 上通过真实七套 provider 渲染全部 52 个图标，检查留白、18/24/26/32dp 与矩形尺寸、tint 和完全透明输入；输出亮/暗完整矩阵和中文概览至 app 私有目录 `files/skin-icon-contact-sheets/`；本轮概览归档在 `docs/visuals/2026-10-11-skin-icons-light.png` 和 `docs/visuals/2026-10-11-skin-icons-dark.png`。 2026-10-12 个性恢复新增 26dp 浅暗中文概览及导航/操作/内容/箭头/状态五组导出；归档在 `docs/visuals/2026-10-12-icon-personality-*.png`，说明见 `docs/icon-personality-2026-10-12.md`。 2.40.4 / 89 使用 clean Release 在独立 AVD 上从已发布 2.40.3 覆盖升级，同签名并保留皮肤设置；正式包七套导航与皮肤预览实拍亦归档在该前缀下。添加照片采用单次差集裁切，避免半透明边框重复合成；真实 provider 半透明回归比较图片与添加照片下半部未改边框/山形像素。非导航扩展回归逐键比较七套真实绘制，禁止相同位图回退；所有分组额外导出 18/32dp 浅色对照，小尺寸辨认与艺术差异仍须看实际矩阵。扩展说明与归档为 `docs/icon-details-2026-10-12.md`、`docs/visuals/2026-10-12-icon-details-*.png`。2.40.5 / 90使用clean Release从2.40.4同签名覆盖升级并保留皮肤设置，正式包完整51×7仪器化回归通过；43张实际矩阵与已审阅Debug图逐像素一致，35个导航单元与上一正式包一致。验证测试宿主/导出目录的临时适配不得留入正式源码。定向运行：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lolita.app.ui.theme.skin.icon.SkinIconVisualTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`。设备旧包与新包必须先核对签名；UTP 在安装失败后也可能自动卸载旧包，测试前保全模拟器数据或使用独立 AVD，禁止用卸载绕过签名冲突。
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

### 发布测试按代码影响范围执行

- 每次发布以“上一已发布版本对应的源码 → 本次待发布源码”的差异为依据，包含本次将发布的未提交改动；沿实际调用和数据流确定受影响功能及必要依赖，只执行这些范围的测试。
- 默认不跑全量 JVM、全量仪器化或完整 USB 套件；优先按测试类或方法定向执行。各章节列出的回归用例和历史验证记录是选例依据，不是每次发布的必跑清单。
- 页面改动只验收受影响页面、入口与关键状态；七套皮肤浅暗、小屏、大字及键盘验证仅覆盖本次改变的界面或组件。共用组件、主题、导航或业务底层改变时，按实际调用方扩大覆盖，不自动重测所有页面。
- 数据库、迁移、备份、持久设置、启动初始化或图片引用/清理逻辑改变时，才补相应升级、备份往返、失败保护及数据/图片保全验证；核对范围按实际影响确定。纯文案或布局改动不重复全库逐字段、全部图片 SHA256 或无关备份回归。
- 同一最终代码状态下已通过的相关用例不重复执行；后续修改或失败暴露新影响时，只重跑受影响用例并补必要覆盖。发布记录简要说明“改动 → 影响范围 → 所选用例与结果”，未涉及部分注明原因即可。
- Debug 构建、clean Release 构建、正式签名、覆盖安装启动及分发校验仍遵守既有发布流程；纯文档更新仅核对文档差异，不构建、不跑应用测试、不出包。全量回归仅在用户明确要求，或基础改动确实影响全应用时执行，并说明依据。

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

- 刷新版本号：`versionCode + 1`，`versionName` 顺延（当前 versionCode=144, versionName="2.69.0"）。
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

1. 更新 `versionCode` / `versionName` 和 `AGENTS.md`，完成规定构建，并按“发布测试按代码影响范围执行”选择定向回归，先提交正式源码。
2. 从该源码执行 `clean assembleRelease`，核对包名、版本、非调试属性及原正式签名；正式 APK 必须支持同签名覆盖安装。签名参数沿用本机配置，不进入仓库。
   正式构建使用仅含已提交源码的隔离 checkout 或 `git archive` 目录，签名配置从本机受保护位置引用；核对APK中项目素材的路径及字节与该源码一致，依赖生成的素材单独核验。不能把工作区未跟踪素材混入正式包，也不通过移动或删除用户原文件来规避。
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
- 备份格式version13、schemaVersion32，覆盖除本机`RestoreJournal`外35个业务/缓存实体；旧1–12通过`BackupFormat`显式迁移，新字段缺失补空或未确认。未知格式/字段/枚举及损坏数据拒绝，新衣橱引用和证据由`WardrobeBackupValidation`校验。
- ZIP 使用图片 SHA256 清单；恢复先创建独立图片文件，再在 Room 事务内写入并逐字段核对，不覆盖在线主库、不替换旧图片目录。旧图片保留，共享图片删除需检查所有持久引用。
- 草稿相册/尺码图及冻结展示任务的商品原图兼容本机历史文件名，继续校验精确私有路径、非软链与图片完整性；模板、派生裁切、识图证据和个人照片保持UUID规则。导出和JSON资源可用性检查复用`validateFrozenTaskImages`，不因当前相册变化误标历史任务缺图。`LegacyDraftBackupTest`覆盖真实草稿/任务准入后的JSON/ZIP往返与失败保全，说明见`docs/zip-backup-legacy-images-2026-10-18.md`。
- 恢复后的闹钟/日历处理由事务提交的 `RestoreJournal` 支持启动重试；数据库失败不删除旧日历。普通账本修改使用 `affectedPriceIdsJson` 限定订单范围，并合并未完成日志；恢复、启动和重启显式恢复须升级为全量（null 范围）。`ReminderCleanup` 按付款 ID 与日历指针去重，保留同阶段不同旧日历指针，权限失败重试不得无限累积。
- 数据回归按上述发布影响范围选取测试类或方法，不默认执行全量 `testDebugUnitTest` / `connectedDebugAndroidTest`；仪器化测试资源为 `app/schemas/`；用例定义在 `BackupFormatTest`、`StageAccountingTest`、`ImportAccountingTest`、`DataIntegrityTest` 和 `OrderBusinessTest`；快捷穿搭筛选与已有照片/日期保全回归位于 `QuickOutfitLogFilterTest` 和 `QuickOutfitLogViewModelTest`（独立内存 Room）。

- 历史颜色/图片单值转换使用 `MigrationJsonLists`，通过 JSON 编码保留引号、反斜线和换行，禁止手工拼接 JSON 或全局去除合法转义。

## 服饰、小物与付款业务

- 2.69.0价格体验前两批：价格／实付编辑使用导航条目的SavedStateHandle保存紧凑草稿及账本指纹，幂等加载、恢复重读、变化拒绝、ViewModel持有保存且完成后防重复。PriceEditorFactory／PaymentEditorFactory通过createSavedStateHandle接线；金额上限、阶段合计差值与真实日期就地反馈，保持原账本事务与ID。Room33／42实体、备份14不变。
- 订单总览突出下一笔，直接记付款，付款进度使用StageAccounting投影并区分实付与应付日期；历史资料按确认拥有、补录、核对跟踪推进。详情可直接记账，低频管理展开；新建入口明确“建立购买订单”及移出心愿后果，普通全款简化，手动计划切换不静默重拆。
- 金额隐私覆盖详情、订单／原账本、付款年历摘要／月份／明细及语义。主动编辑／核对临时显示金额并预告，返回不改设置。年历只导航到实付编辑并预选阶段，不直接写付款；预算待付按itemId进入当前订单，实付按priceId进入所属账本，含日期待定和归档记录。BudgetEntry新增仅查询投影的身份，不改实体或备份。年历玻璃底色使用当前surface，月份保留四列与正常48dp，随fontScale自然增高并显式微型行高；预算月份用皮肤箭头与可换行居中标题，避免两倍字挤压。GlassSurface新增可选glassTint，其他调用默认保持原样。
- 本次设计见`docs/plans/2026-10-23-price-experience-implementation.md`，原生对照与最终测试／发布结果见`docs/price-experience-implementation-2026-10-23.md`；定向用例`PricingExperienceTest`及既有价格补算／历史核对／付款／预算回归，使用独立Room与DataStore。

- `OrderBinding` 对 itemId 和 priceId 唯一约束，仅绑定一个当前订单；旧 Price/Payment 原记录完整保留，多价格与异常金额等待核对。愿望单无实付证据的历史预算不自动绑定。
- `Price.shipping` 已包含在 totalPrice 中；阶段金额与实际付款分离。新输入最多两位小数，计算使用 `OrderAccounting` 整数分，历史 Double 原值不改写。
- `PriceDraftAutofill` 在可编辑草稿中用十进制补算唯一空白阶段，或以完整阶段合计减邮费反推商品金额；自动结果可随来源修改重算，用户接管后不覆盖。加载旧订单不补算，历史只读/加载/保存保护保持；已有手动阶段切换类型不清空，明确使用模板才重设，保留当前商品金额。新建空计划切换类型即使用对应结构。保存才写账，实付、ID、日期和提醒沿用原规则。`PriceDraftAutofillTest` 和 `PriceAutofillIntegrationTest` 覆盖双向补算、手动冲突、精度、原账本及七皮肤浅暗小屏大字。
- `OrderLedger` 在 domain/usecase 中集中校验、事务记账、状态刷新及提交后的提醒恢复；`OrderRepository` 为薄封装。修改价格和阶段不得重建实付，删除阶段不得减少未付余额。
- 完整账本（`TRACKED`）付清=已拥有，未付清=待付款；定金尾款仍用待补尾款文案。有当前订单时服饰编辑不能手动覆盖状态；无价格不能默认零元付清。
- 服饰顶部“待付款”筛选使用 `filterPendingPaymentOnly` / `togglePendingPaymentOnly`，同时包含 `PENDING_PAYMENT` 和 `PENDING_BALANCE`，并显示主件及小物；OWNED/WISHED/PENDING_REVIEW不纳入。搜索、品牌、分类组、季节、风格、颜色及排序继续叠加，列表/网格/画廊同源；账本付清或撤销实付后通过Room Flow实时移除/恢复，共价小物同步。仅统一筛选入口，保留各服饰细分状态，不迁移实体或改写账本。`PendingPaymentFilterTest` 使用独立内存Room与UUID设置，4项真实ViewModel/Compose回归覆盖包含/取消、属性搜索与视图、付清撤销及共价小物、实际顶部文案与选中状态；记录见 `docs/pending-payment-filter-2026-10-15.md`。
- Item.parentItemId 与 includedInParentPrice 支持一层小物关系，独立于 Coordinate；禁止自挂、循环、多层关系与删除有小物的主件。共用价格的小物继承主件状态，独立计价的小物单独记账。
- 价格页只允许设置/编辑唯一当前订单；加号用于阶段和实付。主件编辑与详情支持搜索挂载、新建、解绑；列表默认主件，搜索/筛选保留小物。
- 价格与付款页先展示状态、订单金额、实付和余额；价格明细、记账和购买资料说明可展开，历史未知余额、日期缺失、只读及超额提示必须常显。`PurchaseRecordNotice` 继续复用于价格/付款编辑，展开只影响解释文案。付款计划、实付和保留的旧未付记录采用紧凑分组，每条仍为独立 Lazy item，分别使用 `stage-`、`payment-`、`legacy-payment-` key，并保留原回归 testTag；只有组头显示 `SectionHeader`，首尾圆角与分隔线消费皮肤令牌。
- `StageAccounting` 将未指定阶段的实付按计划顺序投影抵扣，不修改账本；阶段提醒/日历合计不得超过订单整体余额，历史计划超额保留原值供核对。日期未知的实付保持 null，不用应付或创建日期伪造月支出。到期日未知的阶段不设置提醒；提醒代理 ID 为负阶段 ID，缺失 ID 哨兵不可用 -1。
- 淘宝订单数量拆分，实付和邮费按整数分分配且逐项确认，保留来源订单与原始金额；不凭名称自动合并服饰或小物。
- 订单导入默认逐件填写，可随时切换批量编辑并保留原始金额草稿、付款类型和配对。逐件页复用 `ImportStackCard` 的装饰卡缘、`ImportSingleProgress` 的下一件说明及 `ImportActionDock` 的悬浮操作区；主卡/操作区使用 `LolitaCard.FEATURED`，卡缘使用 COMPACT，间距、圆角及导航弹簧消费皮肤令牌。装饰没有输入或重复语义，最后一件、批量、键盘打开或字号大于 1.4 时隐藏；导航动画只在 `graphicsLayer` 读取进度。金额与付款优先展示，服饰资料可折叠，缺失名称/品牌/分类自动展开；配对尾款继续继承定金资料。底部消费导航栏/IME Insets，切换商品清除焦点并保留未完成输入。
- 定金尾款相邻展示由 `importDisplayOrder` 生成原索引序列：仅将双向、异角色且合法范围的配对按“定金→尾款”放在两者最早原位置；未配对保留原相对顺序。逐件、批量及筛选共用序列，导航/进度/装饰剩余数量使用展示位置，编辑/图片/配对及卡片编号仍使用原索引。更换、解除或修改角色后重排但保留当前原记录。不得直接重排 `importItems` 或凭排序建立配对。`ImportDisplayOrderTest` 的7项排序回归及真实Compose的倒序配对导航、批量筛选纠错和动态重排回归覆盖此约定；记录见 `docs/order-pair-ordering-2026-10-15.md`。
- `OrderImportInteractionTest` 使用独立内存 Room：16 项真实 Compose 回归覆盖逐件/批量、卡缘数量、资料折叠和缺失字段、金额草稿、配对及核对重置、来源订单合计、320dp/两倍字体及真实键盘；导出七皮肤浅暗实拍并检查暗色进度文字亮度。实现与实拍见 `docs/floating-order-cards-2026-10-15.md`；定向测试须使用独立 AVD，不操作用户设备数据。

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
- 首页与统计的`PurchaseReviewShortcut`同时消费`showTotalPrice`：关闭价格显示时隐藏整张购买资料提醒及占位，重开后按原待办数量显示，零待办继续隐藏；只控制提醒可见性，不改写购买资料或核对状态。显示设置说明同步覆盖列表价格、消费统计及购买资料提醒。
- 存放位置资格使用`ItemStatus.supportsStorage`（仅OWNED）：房间/未分配/柜架明细、数量、缩略图、移入选择及位置搜索筛选一致排除未拥有物品；详情和编辑隐藏其位置入口。付款与撤销付款通过既有账本状态实时刷新，共价小物继承主件，独立计价按自身，历史已确认拥有仍保留拥有证据。原位置引用保留但不在未拥有期间展示；新建/更换位置和批量分配在Repository事务内校验最新有效状态。删除与整柜移动的完整性计数继续覆盖全部原引用，不能沿用可见数量绕过保护。淘宝订单导入先建无位置物品并完成账本，在同一事务刷新拥有状态后只为OWNED应用位置；资料区位置选择要求已确认且整单付清。

- 拥有状态与资料完整性必须分开：确认历史已购只更新购买证据并保留 `OWNED`，不能将资料待办移除；部分或全部补录都不自动切换 `TRACKED`。用户明确核对付款完整及余额一致后才解除账本待办，日期未知的实付仍单列待补；未知日期不得自动补造，也不影响已明确核对余额的正常付款跟踪。统计按当前订单去重计笔，快捷核对按服饰计件并明确单位。
- 历史核对入口覆盖仍有余额的正常账本，包括部分实付；“确认历史已购”不生成付款。“核对账本并启用跟踪”展示总价、已录实付与计算待付，要求用户确认真实付款已全部录入且计算余额等于实际待付；`confirmTrackedOrder` 在领域层也拒绝未确认请求。回归位于 `PersistentOrderEvidenceTest`，覆盖归档小物共用价格后重绑、订单切换、22→23 原值保全、归档证据备份恢复及确认失败事务不变。
- 付款通知使用 `PaymentReminderIdentity`（阶段代理 ID、价格 ID、到期日、提前天数）与本机 `PaymentReminderDeliveryStore` 持久送达记录。同一配置成功送达后，重启或修改其他订单不得重复补发；权限关闭或发送失败不记送达。应用和日历使用一致的提前天数，日历最少提前一分钟。策略与清理回归位于 `PaymentReminderPolicyTest`、`ReminderCleanupTest`、`ReminderJournalTest` 和 `PaymentReminderDeliveryStoreTest`（独立 SharedPreferences，覆盖送达持久化）。
- 今日穿搭用 Room Flow 订阅本地当天半开区间 `[start, nextDayStart)`，同日多条按日期、ID 倒序选择最新记录；保存、编辑、删除自动刷新。跨日、回到前台与系统日期/时间/时区变更须切换查询范围，次日边界用 Calendar 计算以兼容夏令时。回归位于 `TodayOutfitViewModelTest`。
- 统计概览分别展示“当前订单实付”和包含归档订单的“全部已录实付”；后者仅汇总真实已付记录，归档、共用主件价格不得删减原实付。显示当前缺付款资料订单数；“订单参考均价”明确来自当前已拥有订单总价。分布、趋势继续使用当前绑定订单口径，回归位于 `SpendingOverviewTest`。

## 提醒联动与残余改动回归

### 2.47.1 逻辑联动修复约定

- Material3 日期框统一通过 `domain/time/DatePickerDates.kt` 的 `localDateToPickerMillis` / `pickerMillisToLocalDate` 转换本地日期与 UTC 日首；已有实付/阶段日期不批量改写。尾款导入弹窗每次打开读取最新草稿。
- 普通服饰、套装、完整穿搭编辑按实体身份幂等初始化；旋转或重入不覆盖草稿，换实体取消旧读取，失败可重试且原记录未读到不得当新建保存。保存由 ViewModel 持有任务，屏幕取消等待不取消提交及身份记录，重试不重复新建；保留原记录字段与创建时间。读取/保存期间禁用提交和相关编辑；`ColorSelector`、`SizeSelector` 的 `enabled` 默认 true，编辑器按忙碌状态传入。
- 编辑器和订单导入跟踪本轮新增图片，移除/替换/放弃/跳过时经 `ImageFileHelper.cleanupUnreferencedImages` 清理；原图仅提交后清理。旋转的 composition dispose 不视为放弃，已保存及共享引用始终保护。`copyToInternalStorage` 在失败及取消返回边界清理未交付副本。
- 淘宝订单源实付分摊不以“交易成功”判断是否归零；关闭/退款显示明确核对提示并保留源状态/实付证据。连写规格先提取有效尺码、最长类型优先，规范类别不得把 JSK 回退为 SK。
- 统计风格下钻使用精确名称，季节按去首尾空白后的逗号 token 等值查询，不使用包含或 SQL 通配符匹配。当前备份颜色数组原样保留，旧 scalar/双重编码迁移仅用于格式 1。
- 付款年历时钟、时区与 Room 查询组合；前台/时间广播/最近到期边界刷新同一时间快照，月份、全年与明细逾期一致，不因单纯刷新时间累积查询订阅。桌面穿搭组件使用现有快捷穿搭导航参数。
- 回归位于 `TaobaoOrderParserTest`、`TaobaoOrderImportEvidenceTest`、`ImageCopyCancellationTest`、`EditorLifecycleTest`、`DatePickerDatesTest`、`PricingDateFieldTest`、`ItemTagFilterTest`、`PaymentCalendarClockTest` 及扩展的备份/订单交互测试；记录见 `docs/audit-logic-fixes-2026-10-15.md`。

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

## 虚拟试衣实验（2.48.0）

- 试衣穿戴改造（2.58.0）：新页面显式传入200字内穿戴要求（含空字符串），v4请求复用instructions、promptSnapshot，promptVersion=2；旧1–3请求及v4修订1原样读取执行。新提示词按已选角色处理裙撑体积、衣层接触、BNT前后位置、鞋袜和承重；保留身份、体型、骨骼长度及整体站姿，只容许实际鞋履和持物需要的最小局部姿态调整。描述随SavedState、确认、任务、历史和重试保全，改动后旧付费确认拒绝；Room30/备份10不变。设计与验证见 `docs/plans/2026-10-19-tryon-physics.md`。不以受控测试宣称真实生图物理准确性已验证。

- 个人页、已拥有／心愿物品及套装详情进入 `Screen.TryOn`，内置成年虚拟模特或已确认个人模特＋1–8件服饰/小物；支持搜索分类、每件选图与穿戴位置纠正。`ItemStatus.supportsVirtualTryOn`统一ViewModel、Coordinator和普通Generator的已拥有＋心愿准入；选择器支持状态筛选，鞋履、基础袜装与袜套依稳定品类映射原角色，至少一件基础服装和冲突规则保持。筛选保留隐藏选择，SavedStateHandle仅存紧凑选择快照，不存密钥；试衣不改变物品状态、订单或位置。
- `TryOnGenerator` 用进程共用Mutex防重复调用，上传前校验最新Item仍属于已拥有或心愿且图片归属/本地文件完整。`TryOnImages` 处理EXIF、长边1536及透明底白色合成；`TryOnImageApi` 仅调用官方HTTPS `/v1/images/edits`，型号 `gpt-image-2.5-sunburst`，medium/1024×1536/n=1/PNG；无付费自动重试，取消等待不承诺服务端停止计费。
- 个人API Key在界面填写、更换或删除；Android Keystore AES-GCM加密，密文AtomicFile存于noBackupFilesDir，不回填、不记录、不进入Room或备份。ChatGPT订阅与普通API余额分别计费；没有真实Key时不宣称付费联调通过。
- Room26→27仅增加 `TryOnResult`/`TryOnDao`，结果由薄 `TryOnRepository` 独立持久化，原服饰删除后保留名称/ID快照，不改变账本/穿搭/归属。生成PNG需完整尾块、可解码及大小/尺寸校验，取消或失败清理未保存文件；删除复用图片引用保护。
- 备份格式7/schema27完整支持试衣历史JSON/ZIP/CSV/预览/恢复；旧1–6格式缺失历史数组视为空。新增DAO同步接LolitaDatabase、Repository和AppModule。
- 回归包含TryOn领域/API/Generator JVM测试及独立AVD上的Infrastructure/Persistence/ViewModel/Screen测试，真实Compose核对七皮肤浅暗、大字、Key遮罩与图片缩放。2.48.0阶段的人台仅为探索样片；用户选择只保留正面、停止三视图，后续正面实验功能见2.49.0章节。旧样片仍有腰线与纹样重绘，不替代原图，不声称正式API已实测；说明见 `docs/virtual-mannequin-exploration-2026-10-15.md`，完整提示词与来源见 `docs/virtual-mannequin-front-round2-2026-10-15.json`。

## 裙子正面人台图实验（2.49.0）

- JSK/OP/SK及固定中文裙类、CLOTHING分组且有图片的详情进入 `Screen.Mannequin(itemId)`，不限制拥有状态，不按商品名猜类别。仅选一张已有参考图，可填200字内补充说明；不做三视图。固定空白模板 `assets/mannequin/front-template.png` 来源与SHA见 `docs/mannequin-front-evaluation-2026-10-15.json`，三件四次内置试验包含独立手袖误混入及说明纠正记录，不能代替付费API实测。
- `MannequinGenerator` 与 `TryOnGenerator` 共用 `ImageGenerationGate.process`、API实例和加密Key。上传前复核当前Item/Category/原图归属；模板和原图共两张，经既有EXIF/尺寸/白底归一化。无自动付费重试，取消不承诺停止服务端计费。
- 独立 `MannequinViewModel/Screen` 持有预览，完整Fit/放大后明确“添加到裙子图片”才保存；未处理预览不能再次生成。`ItemRepository.appendGeneratedImage` 强制Room事务读取最新行，只更新图片列表/修改时间，幂等追加到末尾，保留原主图顺序与最新其他字段，删除后拒绝保存。无新实体、schema或备份格式，已追加图片沿用现有Item导出/恢复/删除清理；不建TryOnResult历史。
- SavedState只存Item身份、参考图、说明和待保存预览路径；恢复须校验本应用images目录、精确绝对路径、完整PNG/尺寸/解码及Item身份，不接管或删除被拒路径。保存任务独立于UI等待；清理通过引用保护，删除Item在事务内取最新图片集合，防旧快照遗漏新图。
- NavHost在内容组合前观察同一个entry VM，恢复/生成/保存/待处理预览及离开状态均阻止底栏和快捷穿搭。系统/顶栏返回在恢复未完成时禁用；取消或保存先完成原entry离开，再消费排队快捷意图，pop按entry身份保护。Key配置与试衣共用遮罩对话框，不回填旧Key。
- 回归为 `MannequinRequestTest`、`MannequinGeneratorTest`、独立AVD的 `MannequinInfrastructureTest/MannequinPersistenceTest/MannequinApplicationCleanupTest` 和 `MannequinViewModelTest/MannequinScreenTest/MannequinDetailEntryTest/MannequinNavigationTest`；真实Room校验并发最新字段、幂等追加、旧快照删除、ZIP图片顺序与原图保全，七皮肤浅暗320dp/2倍字体及受影响试衣回归。实现与验证见 `docs/mannequin-front-implementation-2026-10-15.md`。


## 即梦 / Seedream 图像服务（2.50.0）

- 虚拟试衣及正面人台图共用服务切换：`ImageGenerationProvider` 保留 GPT，新增即梦 5.0 Pro（火山方舟 `doubao-seedream-5-0-pro-260628`）。新安装默认即梦；存在旧 OpenAI 密文而无选择记录时保留 GPT。两家密钥分别 Keystore AES-GCM 加密存放于 noBackupFilesDir，切换或删除一家的 Key 不影响另一家；不回填、不进备份或 APK。
- `TryOnSettings.readCredentials()` 原子读取服务与密钥，生成器全程使用该快照，并在上传前校验页面确认的服务；试衣历史写入实际模型型号。`changes` 通知两个保留的 ViewModel 同步状态。生成中、保存中及人台待处理预览禁止改服务或密钥。
- `ImageGenerationApi` 路由到各官方接口。`SeedreamImageApi` 仅 POST 官方 HTTPS `/api/v3/images/generations`，有序 PNG/JPEG Data URL、1024×1536、PNG、b64_json、stream=false。2.60.2起按用户已在方舟开启隐式水印的配置统一发送watermark=false，关闭右下角显式标识；隐式水印由服务端控制，不虚构请求字段，返回PNG原字节保存。试衣、单品展示和专属模特共用该设置，历史图片不回写。复用各入口原图片数量及总量限制；无自动付费重试、重定向或结果URL抓取，完整PNG校验和确认追加流程不变。SetLimitExceeded 明确提示安全体验模式／用量上限。适配记录见 `docs/plans/2026-10-20-seedream-implicit-watermark.md`，三类入口的受控请求回归为 `SeedreamImageApiTest`、`MannequinApiTest`、`PersonalModelApiTest`。
- Room27／备份7及图片归属不变。实际付费联调在用量限制调整后成功返回1张图（76.29秒，2张输入）；不能推论所有印花准确。验证、来源与样片见 `docs/seedream-provider-2026-10-16.md`，回归包含 API／不可变快照 JVM、独立AVD真实Keystore双Key保全、双VM同步及七皮肤浅暗服务选择、原生成与导航测试。

## 后台图像任务与版本说明（2.51.0）

- 任务中心与素材批次共用`ImageTaskQueueActions`：停止后续仅处理确认时仍QUEUED且未发请求的冻结ID，当前执行继续；多选舍弃默认零选择，支持全选、完整Fit预览和放大。`stopQueued`与`discardPending`经Coordinator应用作用域、统一状态锁和Room事务提交，跳过已保存/删除/状态变化项，不扫入后来新建任务。批次原“停止未完成”先整体停止队尾再取消当前读者，避免逐条取消间隙领取后续；原图、已确认资料与共享图片/备份租约继续保护。Room32/备份13不变，前置图稿和受控回归见`docs/plans/2026-10-21-task-queue-controls.md`，真实付费请求0次。

- 全局 `ImageTaskBarViewModel` 在 `LolitaNavHost` 根层订阅持久任务；排队、执行和待确认时立即在底栏上方显示 `ImageTaskBar`，不依赖任务中心VM或进入页面。无活动/待确认即收起，显示真实阶段与队列/待确认数，点击进入任务中心；使用COMPACT卡片/皮肤图标，布局为页面留出空间，不暴露物品名称或图片，不改执行/通知/重试协议。回归覆盖启动前隐藏、跨页实时更新、点击、待确认与终态收起。
- 虚拟试衣与人台图提交到持久 `ImageTask`，由 `ImageTaskCoordinator` 的应用作用域接收，`ImageTaskRunner` / `ImageTaskService` 串行执行。页面仅观察并提交，离开、底栏和快捷穿搭不再受全局生成/预览状态限制；短暂提交防双击，付费确认固定展示的服务，配置变化时拒绝陈旧确认。旧版 SavedState 预览单独验证、查看及明确追加/舍弃，不因 VM 销毁删除。
- 个人页、两种生成页和通知进入 `Screen.ImageTasks` / `ImageTaskDetail`。展示真实阶段，不伪造百分比；试衣结果仍写独立历史，人台结果停在待确认，只有明确确认才事务追加最新 Item 图片末尾。删除任务不删除已保存业务结果；重新发起只回填当前服饰/图片到生成页，再次明确确认。保留2.50普通生成页route pattern与SavedState键，重试使用独立路由；重试种子只消费一次，重建保留编辑或主动清空的说明。
- 服务为用户主动启动的 dataSync 前台服务，持续通知、有限唤醒锁、每任务 12 分钟超时、onTimeout 停止及 `START_NOT_STICKY`，不从开机广播发起付费调用。请求前先持久化 `requestSent`，一个任务只领取一次；重启与备份恢复把未结束任务改中断，保留证据而不自动重发。取消本地等待不保证服务端停止计费。服务会话只领取自身接收的UUID，停止/销毁/前台启动失败先关闭领取入口，再在应用作用域等待读图退出并中断所属队列；迟到旧启动通过Intent中的确切任务ID拒绝/接收，不扫新任务。通知按Room紧凑投影显示真实阶段与排队数，唤醒锁只在领取时续期。
- 任务固定服务，执行时原子捕获该服务的本机加密 Key；修改全局选择不影响已提交任务。密钥不进入任务、Intent、SavedState、日志和备份。现有两家官方 API、原图归属/PNG 校验及共享生成互斥继续保留；试衣结果与任务完成、预览引用与待确认状态分别原子提交。
- Room27→28仅追加 `image_tasks`，DAO/薄 Repository/AppModule 接线及新枚举转换齐全。备份8包含任务 JSON/ZIP/CSV/预览/逐字段校验，旧1–7缺少数组视为空。完成任务的结果路径仅为信息，不强制备份已删除图片；排队/运行源图及待确认 PNG 才是任务持久图片引用。
- 恢复先校验结构及暂存/本机完整 PNG，再通过协调层取消并等待旧执行退出；仅恢复屏障的等待保持 NonCancellable，保证页面离开后备份锁和 ZIP 暂存仍保留到恢复完成。图片保护租约在 Room 快照/导入引用事务内获取，导出/恢复结束后释放并重新核对延迟删除；避免取消任务或丢弃预览删掉备份正在使用的图片。恢复只清理本次激活且无引用的文件，不删除导入前旧图片。
- `AppRelease.releaseNotes` 从同一次 Gitee 最高正式发布查询读取 body，遗留 GitHub 解析兼容正文；手动检查当前版本也可查看，缺失正文明确回退。对话框正文可滚动/选择，操作区适配大字，仍保持静默自动检查、下载探测和可信链接规则。
- 回归包括 `ImageTaskTest`、`ImageTaskPersistenceTest/ExecutionTest/ServiceTest/BackupTest`、`ImageTaskBackupFormatTest`、任务中心/导航及现有生成页真实 Compose 测试、`AppUpdateNotesTest`。所有模拟器操作必须定向到独立 AVD，受控传输不能冒充真实付费 API 或锁屏 HTTP 联调；发布另做 clean 同签名覆盖升级及原表/图片/设置保全。

- 2.51.0 / 109 使用 clean Release 从已发布2.50.0同签名覆盖升级，原30表逐字段、172张图片SHA256及设置原字节保全；GitHub/Gitee匿名完整下载、HEAD、最高正式更新入口与正文验证通过。正式包任务中心与五段更新正文实拍位于 `docs/visuals/2026-10-16-image-tasks/release-*.png`；回执见 `docs/image-tasks-release-2.51.0-verification.json`。

### 生成任务网络与息屏诊断（2.51.1）

- 两家官方图像 API 仅通过本地 `ImageGenerationFailureKind` 传递可信分类，任务中心保存固定中文网络、超时、鉴权、额度、服务错误说明；不保存原始服务错误、异常 cause、URL 或密钥。请求已发出时说明可能继续处理或计费且不自动重试。队列、服务会话、Room 28 和备份 8 格式不变。
- 任务中心、虚拟试衣和人台生成页共用“后台与息屏”帮助，读取当前电池优化、系统后台限制和网络状态，回到前台刷新。按钮仅打开标准电池优化列表与本应用详情；vivo 指引为电池→后台耗电管理→允许高后台耗电。前台服务与 CPU 锁不能保证绕过 Doze 或厂商网络限制，禁止盲目加 WiFi 锁或自动重发付费请求。
- 回归包含真实 loopback HTTP 断连、超时、API→coordinator→Room 原因保留、标准设置入口和帮助对话框。独立 AVD 受控 Doze 与实际付费息屏生成的结果均不能冒充 vivo 真机已修复；详细测试和复杂搭配评估见 `docs/image-generation-screen-off-2026-10-16.md`。

- 2.51.1 / 110 clean 正式包从已发布2.51.0同签名覆盖升级，31张表全字段、172张图片及归档设置字节保全，GitHub/Gitee匿名下载、SHA256、HEAD和最高正式更新入口核验完成。真实 Seedream 实验52次请求/51张图后保留原提示词，不能将这批定性评估或 AVD 息屏结果表述为 vivo 网络已根治；回执 `docs/image-generation-release-2.51.1-verification.json`。

## 服饰图片封面（2.52.0）

- 服饰详情滑动图片后可“设为封面”，首张显示禁用的“当前封面”；保存完成回到新封面，原图和已追加的人台图均可选。列表、心愿、位置缩略图和详情继续统一读取 `imageUrls` 首项，不新增封面字段、数据库迁移或备份格式。
- `ItemRepository.setCoverImage` 在 Room 事务中读取最新行，校验所选路径仍在该服饰图片里，只更新图片顺序和修改时间。仅把一个选中引用移到首位，保留其他引用的相对顺序与重复项；当前封面不重复写入，删除或不再归属的图片拒绝，和人台图追加串行，不能用详情旧快照覆盖整条服饰。
- `ItemEditViewModel` 用 StateFlow 管理设置中/错误，异步由 viewModelScope 持有；身份改变后不回写旧结果，不覆盖未保存草稿。`ImageGalleryPager.onPageChanged` 为可选回调，只报告停稳的页，原调用保持兼容。
- 回归为 `ItemCoverTest` 的真实详情→ViewModel→Room 操作，以及 `MannequinPersistenceTest` 的最新字段、并发追加、重复引用、幂等/删除/外部图片拒绝和带封面的 ZIP 顺序恢复。图片本体不复制、不删除，不调用图像服务。
- 2.52.0 / 111 clean 正式构建、254 项 JVM 与 21 项独立 AVD 回归通过；从 2.51.1 同签名覆盖升级，31 张表全字段、172 张原图和设置原字节保全。发布回执见 `docs/item-image-cover-release-2.52.0-verification.json`。


## 我的模特（2.53.0）

- 个人页与试衣页进入“我的模特”。照片方式至少脸部或正面全身一张，可补侧面；参数方式生成虚拟面容，身高体重必填、围度等可选。PHOTO→PARAMETERS切换保留本机草稿，但提交只发送当前模式的有效引用，不能夹带隐藏本人照。人物名称仅本地管理，不进入生图提示词。
- 原照选入即复制到 `filesDir/images/` UUID 文件；`PersonalModelDraft` 保存严格版本化原始字符串和可枚举图片引用，旋转/重建保留，显式放弃才删。参数调整可明确沿用已确认虚拟底图，调整保存为新人物。所有删除经 `ImageFileHelper` 的事务引用保护。
- Room28→29追加两表和可空人物快照列；`PersonalModelDao` 同时管理模特/草稿，经薄 Repository、AppModule、StateFlow 接线。`TryOnResult.model` 仍是API模型名，人物id/name为历史快照，不设级联外键。默认id为本机DataStore，完整恢复成功后清空，防止导入复用Long id指向另一个人。
- `PERSONAL_MODEL` 后台任务使用严格v2不可变请求，v1三字段任务继续兼容。新生成操作专用API允许1–5输入，旧试衣/人台入口仍2–9。固定模板只供布局；生成预览保留原照，显式确认在同一事务创建模特和完成任务，重复确认幂等；失败/中断/取消保留原照供人工核对重试，绝不自动付费重发。
- 个人试衣只上传已确认底图＋服饰。生产入口在同一Room事务核对确认时id与底图路径并捕获引用；恢复后同id换图拒绝陈旧确认。排队后删除/改默认/改名不改本次人物。删除人物保留历史；重试已删除人物先让用户重选。
- 备份9包含个人模特、草稿、原照、已确认底图、待确认/异常任务源图，JSON/ZIP/CSV/预览/恢复均接通；旧1–8补空。ZIP基于完整源路径生成确定性UUID归档名，避免同名不同源合并；不改变旧包读取。恢复前严格校验记录、图片和结果归属，活动任务只转中断。
- 个人参考JPEG/PNG在平台解码前严格检查压缩流，避免截断熵数据＋伪EOI被当完整图；完整主JPEG允许Motion Photo/UltraHDR附加尾部（只验证主静态图）。API26/27只接受完整JPEG/PNG；28+其他格式继续ImageDecoder完整读取。预算为80MiB/36MP/单边16384，PNG展开256MiB、JPEG系数访问256M与渐进状态32MiB上限；此限制不改变原服饰读取器。
- 回归：`PersonalModelRequestTest`、`PersonalImageIntegrityTest`、`PersonalModelApiTest`、`PersonalModelGeneratorTest`，以及独立AVD的`PersonalModelPersistenceTest/ExecutionTest/BackupTest`、个人编辑/管理、试衣/任务/导航Compose。真实七皮肤浅暗小屏大字图在`docs/visuals/2026-10-16-personal-model/`。真人相似度和身体尺寸还原尚未付费实测，不可用色块/内置图/草图宣称已验证。
- 2.53.0 / 112 clean正式构建、297项JVM及148项设备回归通过；从2.52.0同签名覆盖升级，旧表字段、172张原图和设置字节保全。GitHub/Gitee正式发布及匿名完整APK/校验文件下载、SHA256、HEAD通过。Gitee公开列表仍HTTP403，正式包一次真实检查显示拒绝访问/频繁请求，最新版本和完整正文未验证；直接下载可用。回执与实拍见`docs/personal-model-release-2.53.0-verification.json`及`docs/visuals/2026-10-16-personal-model/manual/release-update-error.png`。

## 无杆人台与小物展示（2.54.0）

- 裙类沿用明确 JSK/OP/SK 与中文裙类准入，固定正面模板保留完整躯干和2:3画幅，去掉支撑杆/圆底座；提示词禁止以裁裙摆或加长裙身掩盖支架。素材与完整imagegen编辑提示见`docs/mannequin-accessory-assets-2026-10-16.json`。
- `CategoryGroup.ACCESSORY`（含自定义名、独立和挂载小物）生成单独商品展示图，仅上传所选1张原图，不使用人台模板；保留原本成对/成组，不补造配套裙装。准入不根据Item名称或parentItemId推断，CLOTHING非裙仍不开放。
- 新MANNEQUIN严格v3请求保存`MannequinSubject`分类id/名称/展示类型；v1/v2原样兼容，v2个人模特编码不写新null字段。Room仍29、备份仍9；JSON/ZIP包含请求快照并恢复幂等追加原物品图片，保留封面顺序。
- 页面确认固定原图、说明、分类、服务及keyRevision，变动后重新确认；Coordinator事务与Generator准备前后核对当前分类和图片。`prepareAccessory`只处理1张原图，专用`generateMannequin`允许1–2输入，原试衣2–9、个人模特1–5边界不变。
- 回归覆盖`MannequinSubjectTaskTest`、单图基础设施/API、确认变化、JSON/ZIP往返和旧任务；七皮肤浅暗320dp/两倍字体实拍在`docs/visuals/2026-10-16-mannequin-accessories/`。色块是布局夹具，无付费API生成品质试验。

- 2.54.0 /113 clean正式包原签名核验通过，307项JVM及相关设备回归通过；2.53.0覆盖升级与实际详情/小物页面操作后33表、198件、172图及设置逐字节保全。GitHub/Gitee均已正式发布，同一APK/校验文件匿名完整下载、SHA256及HEAD通过；按2026-10-16 19:21单独授权仅删除Gitee2.44.0 APK（保留旧发布/校验及本机/GitHub副本）。Gitee正式发布后公开API仍HTTP403，正式包本阶段一次真实检查仍显示拒绝访问/频繁请求，不冒充最新版本与完整正文已验证。状态见`docs/mannequin-accessories-release-2.54.0-verification.json`。

## 完整衣橱第一期（2.55.0–2.55.2）

- 七类穿戴物品共用Item；`CategorySemantic`固定业务身份，分类名称、默认穿戴位置与购买父子关系独立。按用户最新要求，2.55.1以`HistoricalCategoryRecognition`完整名称白名单自动识别历史JSK等12种旧预设及明确别名，NFKC/大小写/空白归一，只补未确认语义，保留已确认类型、角色覆盖及原ID/名称/分组/创建时间；不合并记录，不按子串猜混合类。独立`historical_semantics_v1`标记使已安装2.55.0也可一次刷新，事务提交后落标，后续改名不再推断；旧备份在严格解析后使用同规则。`WardrobeCategoryLibrary`原初始化标记继续独立控制预设追加，删除或改名不重建分类。规则与验证见`docs/historical-category-recognition-2026-10-18.md`。
- 登记仅要求名称和有效分类；未设置品牌使用受保护的系统键，不假造商标。`WardrobeSpecifications`保留所有品类原字符串及单位，切类型不清已填值；详细规格默认折叠，按需展开。`ItemDraft`保存图片与基线修改时间，提交事务拒绝覆盖其他页面的新资料；复制剥离订单、实付、导入身份、购买父子关系和待执行任务。
- `StorageNode`在房间下最多两层，唯一键使用归一化名称与明确父级哨兵；移动/删除/批量迁移事务同步Item与持久编辑草稿的房间/节点，物品修改时间单调增加。新DAO已接数据库、薄Repository和AppModule。四款示意插图仅主动采纳，完整提示词与SHA见`docs/storage-assets-2026-10-18.json`。
- 共用`ImageRegionCropDialog`提供EXIF、缩放/平移、裁切、旋转/还原和最终预览；只存UUID裁切副本，原图不改。识图至多5区、每区3MiB、合计12MiB，发送前核对精确JPEG bytes/SHA/尺寸；不发送路径或EXIF。
- **识图不使用OpenAI**。`RecognitionSettings`独立选择方舟豆包Seed2.1Pro `doubao-seed-2-1-pro-260915` 或DeepSeek `deepseek-flash`；各自Key通过Keystore AES-GCM存noBackup，不自动读取生图Key、不回填、不进任务/日志/备份/APK。固定本次服务、型号、区域、草稿基线后明确确认，无付费自动重试。
- 识图只给可核对候选；款式、实际尺码行、明文/外观来源、原文、对象/口径/单位都须匹配。平铺宽不乘二，足长不当内长，图片标价不建账。人工修改优先，采纳只入草稿，最终保存才写物品；已采纳证据和AI图来源独立保留，复制或删任务不能洗掉来源。锁序固定草稿Mutex→Room事务。
- 试衣新v4冻结实际输入SHA、服务提示词、人物、品类/组成与所选头模；超过8件由用户明确选子集，裙撑及已知非服装语义不能充当基础服装。五款头模仅候选，逐款真实短/长评估不等于定版；无长直发样图时明确保留待验证。
- 新任务实际输入和原模板纳入图片引用保护与ZIP，任务独占输入按SHA去重；已有业务图片和待确认输出保留独立身份，避免相同字节折叠所有权。外部无图JSON保留历史快照并标`sourceResourcesMissing`，执行前拒绝缺资源任务，不替换当前默认模板；后续ZIP仅允许这类独占历史输入缺失，预览继续报告缺图，业务图和未标记输入仍须完整。旧v1/v2/v3仍读原分支。生成图只在确认后追加末尾并写`ImageProvenance`，原封面不变。
- Room29→30、备份9→10完整覆盖新资料、预览、适用CSV和图片引用；恢复活动任务转中断，不自动付费发送。实现范围、视觉流程差异、实际测试与付费质量限制见`docs/complete-wardrobe-implementation-2026-10-18.md`；后补98幅视觉方案不冒充初版前置图，真实受控UI截图另归档。
- 2.55.0 /114 clean正式构建、390项JVM与53项最终定向设备回归通过；2.54.0覆盖升级保全31张原表196列、198件物品、172张原图与原设置，重启幂等。GitHub/Gitee正式APK匿名下载与校验通过；经本轮明确授权仅清理Gitee2.44.1和2.45.0旧APK，其他发布/校验及GitHub/本机副本保留。本轮公开更新API为200，正式包一次手动检查正确显示最新2.55.0和完整说明，历史403不抹除。真实调用停于18/20，所有头模长发压短/截断，继续候选。发布tag保留f9e5278，feafa0f仅补跟踪APK已含的收纳清单；完整源码读取该提交或更新master。回执`docs/complete-wardrobe-release-2.55.0-verification.json`。
- 2.55.1 /115修正历史分类人工负担：394项JVM、36项定向设备及clean正式构建通过；2.55.0同签名覆盖升级正确识别旧12分类、198件物品，38表254列除预期类型字段外保全，172图SHA和原设置字节不变，重启幂等。两端正式发布及匿名下载、SHA/HEAD通过；按本次另行授权仅移除Gitee2.55.0 APK，旧发布/校验/GitHub和本机副本保留。公开更新入口及正式包一次手动检查均正确显示2.55.1和完整说明。本次无付费调用。回执`docs/historical-category-release-2.55.1-verification.json`。

### 录入与识图减负（2.55.2）

- 物品录入默认照片、名称/类型与尺码/颜色/位置；详细规格及品牌、备注等补充资料折叠，已填值有摘要，切换类型不清旧规格。品牌选填，唯一保存按钮置底并正确消费Insets/IME；普通返回保留持久草稿，放弃是独立明确操作。
- 识图在一个工作页完成选图、实际JPEG预览、明确付费确认和结果核对；默认整图，框选可选。整图超过3MiB保留原副本进入框选，绝不通过放宽上限发送。每张实际预览进入视区后才开放发送；来源、服务/密钥revision、task快照和实际bytes提交时再核对。详细结果按需展开、逐项采纳后返回草稿，未采纳结果继续保留。
- 已准备图可事务原位换区，旧图片经引用保护清理；AI来源只收紧，不因复选/换图洗掉。Android JPEG编码产生的颜色配置不得误判为用户元数据；裁切输出显式转sRGB并移除metadata，API仍严格拒绝不合规输入，原图不改。受控验证和本轮真实调用分别记录。

- 2.55.2 /116录入与识图减负：397项JVM、78项最终去重定向设备回归（含修复后重跑）及clean正式构建通过；1项live默认opt-in跳过，另按本轮明确授权执行2次豆包native真实识图均成功、无重试，不冒充普遍准确率。2.55.1同签名覆盖升级及重启保全38表254列、198件物品、172图SHA与3设置文件原字节。GitHub/Gitee正式附件匿名完整下载、SHA及HEAD通过，公开更新与实际签名app手动检查正确显示2.55.2和完整说明；仅按本轮另行授权清理Gitee2.55.1 APK，旧发布/校验/GitHub/本机副本保留。回执`docs/entry-recognition-release-2.55.2-verification.json`。

### 展示图与封面修复（2.55.3）

- 详情调用`ItemEditViewModel.loadDetail`读取持久物品，不恢复、创建或重连编辑草稿；编辑`loadItem`继续恢复原草稿并检查基线，加载身份区分DETAIL/EDITOR。生成图确认后仍追加相册末尾，用户明确“设为封面”只重排相册，保留原图、来源、订单及旧草稿；详情操作后的旧编辑基线继续拒绝覆盖。
- 衬衫BLOUSE新增独立正面人台策略和提示词，保留领口、袖型、袖口及下摆，下身留空，不补裙裤；两输入仍为固定躯干人台与原图。不改持久品类身份、旧裙装提示词或冻结任务。其他非裙服装本轮不扩展。详情入口和工作页统一要求稳定语义，按实际展示模式给中文标签，不按历史group猜测。
- 展示任务入队前错误使用`MannequinAdmissionFailure`固定阶段消息，不显示路径、凭据、服务原文或原因链；取消原样传播，已入队后的服务中断不误标为“未发送”。设计与验证见`docs/superpowers/plans/2026-10-18-mannequin-generation-cover-repair.md`。

- 旧商品源可经`isOwnedReadableProductImage`保留旧文件名，但仅当MANNEQUIN当前物品精确引用时启用；私有images直系路径、非软链、完整性、尺寸与解码继续检查。派生裁切、模板、TRY_ON和个人照片保持原严格UUID规则。API26/27的WebP限制本轮未变；不得将Android15受控回归描述为用户手机全部故障已复现。
- 单品、人台及头模展示的`Item.productReferenceImages`统一商品相册与旧首张／新增详情图列表，按原序去空去重；`productDetailImages`只为来源筛选排除相册重复。页面可切商品图／详情图、完整Fit／放大，并沿用现有裁切，切分组不清选择；详情仅有图片时同样开放入口。ViewModel、普通Generator和Coordinator事务均要求精确引用，裁切副本保持严格路径校验。选择、裁切、服务或密钥变动使旧确认失效；已接受任务继续执行冻结输入，结果确认后追加相册末尾，不设封面、不改详情原图。Room30／备份10不变，设计及回归见`docs/plans/2026-10-20-tryon-wishlist-detail-images.md`。
- 编辑页须等草稿恢复、冲突检查与识图重连完成后才结束`isLoading`；ViewModel测试清理须等待Job结束再关闭Room，避免后台DAO访问已关闭数据库。
- 2.55.3 /117展示图与封面修复：400项JVM、144项最终普通设备回归通过，2项默认opt-in跳过；25张私有历史图另行本机显式核对全部可读并入队，0远端/付费请求。clean原签名2.55.2覆盖升级及重启保全38表254列、198件物品、172图SHA和3份设置字节。GitHub/Gitee同一正式APK匿名完整下载、SHA/HEAD与源码标签通过；发布时公开更新及正式app手动检查正确显示2.55.3和完整说明。按本轮明确授权仅移除Gitee2.55.2 APK，旧发布/校验/GH/本机副本保留。回执`docs/mannequin-repair-release-2.55.3-verification.json`。
- 用户另行要求扩大本轮旧APK清理后，已额外移除Gitee的15个旧APK共827828220字节，执行时保留2.55.3、2.54.0、2.53.0；所有旧发布/校验/GitHub/本机副本保留，独立2.55.2清理不重复计入。清理后保留版精确owner元数据和APK HEAD通过，公开列表额外复核403按失败记录，不能描述为最新查询通过。本次范围不自动授权未来清理。回执`docs/gitee-apk-cleanup-2026-10-18.json`。


## 日常入口、智能补算与全局任务栏（2.56.0）

- 今日穿搭仅首页位置Tab；`PriceDraftAutofill`双向补算唯一缺项并保留人工输入；`ImageTaskBarViewModel`在NavHost根层观察，排队/进行/待确认即显示全局皮肤任务条，不依赖进入任务中心。Room30/备份10/业务图片和付费任务协议不变。
- 2.56.0 /119：412项JVM及27项最终去重设备回归通过（切换类型保留反推总额修复后重跑8项价格/报价）；七皮肤浅暗320dp、两倍字体与真实键盘见`docs/visuals/2026-10-18-daily-price-tasks/`。clean正式包从2.55.4同签名覆盖升级并重启，38表254列/198件/172图SHA及3设置原字节保全。源码与标签已推送，GitHub/Gitee正式同一APK及校验附件下载通过，未删除旧资产。Gitee公开版本API与延时复核均403，实际app手动检查亦显示拒绝访问/频繁请求；正式附件HEAD200及直接下载可用，不能宣称应用检查更新已成功。回执`docs/daily-price-tasks-release-2.56.0-verification.json`。


### 三列假发品牌显示（2.56.1）

- 2.56.1 /120仅显示层修复：三列无真实品牌假发/发片省略品牌文字和图标，`unsetBrandId`依赖系统身份；同名真实品牌及其他品类/列数保持原显示，不改Room30/备份10或物品引用。
- `assembleDebug`与clean正式构建通过；独立5586临时视觉夹具核对无品牌/真实品牌/同名真实品牌及非假发、单列/两列/三列、七皮肤浅暗及320dp两倍字体，原数据库字节已复原。未新增镜像显示逻辑的测试。独立5584从2.56.0同签名覆盖升级与重启，38表254列/198件/172图SHA及3设置原字节保全。源码标签及GitHub/Gitee正式同一APK/校验文件下载核验通过；公开更新接口与复核403、实际app检查亦失败，稳定附件HEAD200可直接下载；未清理旧资产。设计/实拍见`docs/plans/2026-10-18-wig-grid-brand.md`，回执`docs/wig-grid-brand-release-2.56.1-verification.json`。


### 追加灰瞳玫瑰头模（2.56.2）

- 经用户明确许可随源码及APK公开，追加第六款`slate-rose` / 灰瞳·玫瑰妆v1；原五款ID、版本、SHA及图片原字节保持。仅新增离线候选，选择/放大不调用付费服务，不替换已有偏好与历史任务快照。原参考照片不入库或APK，提示词与来源摘要归档`docs/visuals/2026-10-18-slate-rose-head/`。
- 选择页提示改为“以下均为候选”，不硬编码数量；仍须真实短/长假发评审，不能把本轮空白头模及受控UI验证当作佩戴保真通过。Room30/备份10及图像任务协议不变；完整过程见`docs/plans/2026-10-18-slate-rose-head.md`。
- 2.56.2 /121：Debug与clean正式构建、6项最终去重定向设备回归及七皮肤浅暗/320dp两倍字/完整放大实拍通过；无应用内付费调用。2.56.1同签名覆盖及重启保全38表254列、198件、172图SHA与3设置字节。源码与标签已推送，GitHub/Gitee同一正式APK及校验文件匿名下载、HEAD和源码对应核对通过；公开更新及复核403、实际应用检查失败，稳定附件HEAD200可直接下载。未清理旧资产。回执`docs/slate-rose-head-release-2.56.2-verification.json`。


### 统一 API 配置与头模界面（2.57.0）

- 个人页头像后提供“API 配置”；生图、我的模特、识图工作页的配置入口都导航到`Screen.AiConfiguration`。同页管理生图即梦/OpenAI及识图豆包/DeepSeek四项，分别选择两类默认服务；不自动共用或拷贝密钥。`AiConfigurationRepository`复用原`TryOnSettings/RecognitionSettings`加密文件、别名和revision，AppModule提供；`AiConfigurationViewModel`只保留状态，不保存密钥输入。
- 四项本机旧配置无需迁移或重填。密钥输入仅非Saveable短期内存，掩码且不回填，不进Room、日志、备份、APK；修改只针对明确服务，固定中文错误，保留陈旧确认及已提交任务服务快照。配置不自动付费试连，Room30/备份10及全部原图保持。
- 头模界面只显示名称、选中状态、完整Fit/放大与实际输入核对；不展示候选、评审、内部版本标记。旧“蓝紫妆·原候选”按稳定ID仅在显示层映射“蓝紫妆”，原目录、偏好与历史快照不改写。质量评估保留在开发记录，不因删除文案宣称评审通过。设计及验证见`docs/plans/2026-10-19-api-configuration.md`。

- 2.57.0 /122统一配置及头模文案：35项定向设备回归、Debug和clean正式构建通过；早期412项JVM实际结果保留，未作为后续全量要求。2.56.2同签名覆盖启动、原noBackup配置摘要及正式配置页通过；未重复无关全库/图片升级审计。GitHub/Gitee正式附件匿名下载、SHA/HEAD通过，源码标签`03403a4`已推送。电脑公开查询200并选中2.57.0，正式app一次检查仍显示通用网络失败，不能宣称应用更新成功，直接APK可用。回执`docs/api-configuration-release-2.57.0-verification.json`。

- 2.57.1 /123购买资料提醒跟随价格显示：6项定向设备回归及Debug、clean正式构建通过；只在同一最终应用代码下修正视觉宿主后重跑受影响的两项。320dp两倍字体下首页/统计七皮肤浅暗确认关闭后整卡及占位隐藏，重开原待办保留。2.57.0同签名覆盖启动及GitHub/Gitee正式附件匿名下载、SHA/HEAD通过，源码标签`4be5d43`已推送；纯显示改动未重复无关全库、图片、密钥或备份审计。电脑公开更新查询200；正式app一次检查仍为通用网络失败，具体原因未确认，直接APK可用。回执`docs/review-price-visibility-release-2.57.1-verification.json`。

- 2.57.2 /124未付清物品不进入存放位置：13项定向设备回归、Debug与clean正式构建通过，详情/编辑/柜架/导入的七皮肤浅暗、320dp两倍字实拍归档于`docs/visuals/2026-10-19-owned-storage/`。2.57.1同签名覆盖启动及原位置引用三列逐值保全，未重复无关全库/图片/密钥审计；GitHub/Gitee正式附件匿名完整下载、SHA/HEAD与源码标签`4785a51`已核验，源码已推送。本轮按用户确认忽略网络限制，不重复手动检查更新或修网络；公开查询单独记录。回执`docs/owned-storage-release-2.57.2-verification.json`。

- 2.58.0 / 125：试衣穿戴要求和角色物理关系指引；49项JVM、22项设备定向用例及clean正式包覆盖升级通过。七皮肤浅暗、小屏IME与正式包实拍归档在`docs/visuals/2026-10-19-tryon-physics/`，双平台正式发布和匿名下载校验回执为`docs/tryon-physics-release-2.58.0-verification.json`。本轮真实付费生成0次，复杂穿戴视觉质量尚未实测。

- 2.58.1 试衣规则修订：即梦新请求使用按角色条件生成的中文规则，OpenAI保留英文规则；`promptVersion`当前10，v4历史1–10冻结提示词原样执行，旧v1–v3不重建。宽腰封以实际材质和躯干曲面闭合围穿，侧片后绕、允许遮住原裙腰结，强调接触阴影与衣料受力，不以位置正确代替穿戴质感验收。Room30/备份10/图片和UI不变；真实样本及限制见`docs/tryon-physics-live-evaluation-2026-10-19.md`，受控传输不代替真实质量评估。
- 2.58.1 /126：最终修订10的53项JVM、1项冻结执行/历史/ZIP设备回归及clean正式包覆盖升级通过，源码`2d0884fe`和标签已推送，GitHub/Gitee同一APK匿名下载、SHA/HEAD通过。真实调用52/68后按用户要求停止，16次未用；精简修订11因漏掉底图长裤未采用，腰封贴合、五金及复杂搭配仍有质量限制。回执`docs/tryon-physics-release-2.58.1-verification.json`；后续100次是发布后另起的明确额度，不合并旧余量。

- 追加第七款`faceless-ivory` / 素面·无五官v1：象牙白树脂、完整头型/耳/颈/底座，无眼眉鼻口和妆容，小头型留出假发空间。仅追加离线目录与素材，保留原六款、偏好及历史快照；选图/放大不付费，实际假发继续原两输入及明确确认，不添加隐藏三视图调用。设计与来源见`docs/plans/2026-10-20-faceless-head.md`；真实样本中面部保持空白，但长编发仍有补造和比例问题，不声称普遍佩戴保真。

- 2.59.0 /127颜色分类按需导入：48项JVM、14项定向设备回归通过，七皮肤浅暗与320dp两倍字体／实际金额键盘已核对。最终包从源码`30fb69f6`的git archive执行clean构建，35份项目素材及2份依赖素材逐项匹配；初次混入本机未跟踪素材的中间包已撤回替换，用户原文件保留，不改源码标签或旧发布。独立新AVD从2.58.1同签名覆盖及启动通过；GitHub/Gitee最终同一APK匿名下载、SHA/HEAD与源码标签核验通过，公开更新API选中2.59.0。相同应用源码的中间包一次实际检查仍为通用网络失败，素材重打包后未重复检查；不宣称应用检查更新已成功。无付费调用、数据库或备份变化，回执`docs/taobao-color-styles-release-2.59.0-verification.json`。

- 2.60.0 /128：试衣可选已拥有及心愿，含鞋履／袜装；单品展示可选详情整图或框选，原状态、订单、封面及详情图保留。46项定向JVM与62项最终去重设备回归、七皮肤浅暗／真实320dp两倍字／IME通过；真实弹窗按系统浅暗分别验收，原设置已恢复。源码`2a5012e7`归档clean原签名构建，35份项目素材、2份依赖与2份生成profile核验，通过2.59.0覆盖启动。GitHub／Gitee同一正式APK匿名完整下载、SHA／HEAD及源码标签通过，公开更新选中2.60.0；未重跑应用内网络检查，不把公开接口成功代作应用检查成功。无付费AI调用、迁移或备份格式变化，详情任务ZIP／裁切与冻结执行已定向覆盖；无关头模工作保留且未打入包，未删旧资产。回执`docs/tryon-sources-release-2.60.0-verification.json`。

- 2.60.1 /129追加无五官头模：6项最终去重定向设备回归、七皮肤浅暗／320dp两倍字／完整放大、Debug和clean原签名正式构建通过；独立新AVD从2.60.0覆盖及两次启动保留明确构造的旧头模选择字节，原六款目录和图片不变，36份项目素材与Git一致。源码及标签`0ac698f0`已推送，GitHub／Gitee正式同一APK及校验文件匿名下载、SHA／HEAD通过。Gitee初次因1GB配额HTTP400受阻；用户本轮明确授权后，仅删除2.55.3的Gitee APK即腾够空间，2.55.4、旧发布／校验、GitHub及本机副本保留。公开版本查询HTTP200并选中2.60.1，不把公开接口成功当作应用检查成功。100次新额度已用17次、余83次；三视图／分步腰封实验仍未稳定改善，生产生成流程未变。未重跑应用内网络检查，回执`docs/faceless-head-release-2.60.1-verification.json`。

- 2026-10-20穿戴证据续测：沿用100次本轮额度，本次新增15次（14次Seedream、1次builtin image_gen），累计32次、余68次，无自动重试。整件／部位坐标、仅修侧片、腰部近景、侧前方诊断、灰模后外观映射、1536×2304输出均未同时守住贴合与原扣具；builtin对照把两扣变三扣。目标厘米／硬度仍未知，同名不同配色未当作第二视角。实验未采纳到应用，2.60.1／请求协议／Room30／备份10及既有确认保持；本轮仅提交研究文档，未重建或发布。结果与输入输出摘要见`docs/plans/2026-10-20-wearing-evidence-study.md`及`docs/tryon-evidence-study-2026-10-20.json`。

- 2.60.2 /130生图水印适配：Seedream三类入口统一关闭显式水印，依赖用户已开启的方舟隐式水印配置；11项定向JVM、Debug、隔离源码clean原签名Release及2.60.1覆盖启动通过，正式DEX参数已核对。36份项目assets与Git字节一致，7张drawable的透明度及非透明RGB保持（AAPT优化透明RGB和编码）。GitHub/Gitee同一APK匿名完整下载、SHA/HEAD、源码标签及公开最高正式版本查询通过；未做付费生图或隐式水印检测，不把公开查询当作应用内检查成功。仅按本轮明确授权删除Gitee的2.55.4旧APK，旧发布/校验/GitHub及本机副本保留。Room30/备份10/图片处理与UI不变；可选启动截图ADB传输失败已单独保留，必要覆盖升级及设备APK摘要核验通过。回执`docs/seedream-watermark-release-2.60.2-verification.json`。

- 2026-10-20试衣网站调研：官方服务、论文与作者实现对照见`docs/tryon-web-research-2026-10-20.md`。优先研究原部件变形／受保护合成及FASHN Try-On Max、Style3D工作流；真实多视图与AI补图分开，像素条件不等同像素不变或物理仿真。CtrlVTON作者仓库尚未提供推理代码与权重；服务支持配饰不代表目标腰封已通过。此次付费生成0次，累计仍32／100、余68次，未改变应用或接入新服务；纯文档核对并提交推送，不构建或出包。


- 2.61.0 /131：淘宝链接支持主动按颜色／款式＋尺码组合多选，每件保存独立size和精确报价；60项定向JVM、19项去重设备回归及七皮肤浅暗／真实320dp两倍字／金额键盘通过。源码`822519aa`归档clean原签名正式构建，36份项目素材及依赖／生成profile核验，从2.60.2覆盖安装与两次启动通过。GitHub／Gitee同一正式APK匿名完整下载、SHA／HEAD、源码标签及清理后公开最高正式版本查询通过，未重复应用内网络检查。无付费调用、Room30／备份10或历史记录变化；回执`docs/taobao-size-combinations-release-2.61.0-verification.json`。用户本轮先批准2.56.0、随后扩大为仅保留最近两份，已清理13份Gitee旧APK共987915300字节，保留2.61.0和2.60.2及全部发布页／校验、GitHub／本机副本；不自动授权未来清理，详见`docs/gitee-apk-cleanup-2026-10-20.json`。

- 2026-10-20腰封部件原型：三组局部草图各用builtin image_gen与当前Seedream共享API对照，共新增6次，累计38／100、余62次，无自动重试。手工网格、独立双扣取样、原部件保护及受限低频光照已跑通；750个映射采样与12份最终RGBA保护检查通过，但接缝、真实贴合和自动标注未达通用接入标准，未改App或隐藏新增付费步骤。2倍示例明确放大人物底图，最终检查相对放大基线；源码照片及派生图保持私有。方法、原输入版本与后续本地修正分别见`docs/plans/2026-10-20-controlled-waist-parts.md`和`docs/controlled-waist-parts-evaluation-2026-10-20.json`。本轮仅提交研究文档，不构建或出包。

- 2026-10-20三维能力调研：物品GLB查看、照片建模、可调人体、独立换装及布料仿真分层说明见`docs/3d-capabilities-research-2026-10-20.md`。当前Item／PersonalModel／ImageTask仍只有二维图片与参数请求，没有三维资产或引擎；建议先验证本地GLB、统一人体与固定站姿装配。SceneView最新依赖与现有工具链需隔离验证；混元新服务入口迁往TokenHub。可旋转整套人物、自动绑骨骼与可独立换装／合身预测分别验收，不自动补造背面或厘米。新增付费调用0次，未上传新服务、改应用或沿用旧额度测试；纯文档核对后提交推送，不构建或发布APK。

- 2026-10-20宠物式二维预览评估：用户改为先评估序列帧，真实三维路线停止。方案见`docs/pet-style-wardrobe-preview-research-2026-10-20.md`；借鉴基准形象、分组生成、配准和连续性检查，拟先验证正面及左右前方三张搭配图。Work宠物v2的16方向是注视方向，不是身体转台；192×208格子不能代替服饰细节图，宠物一致性也不等同商品保真。尚未生成新方向样本、创建宠物或接入App，付费调用0次；三维示意素材与概念保留本机忽略目录，未形成Android查看器，不打入源码或正式包。

- 2026-10-20主视单品抽层与融合：用户另授权20次，本组builtin image_gen用5次、余15次，不合并旧100次额度且无自动重试；主视透明单品、斜视纯抽层对照与3张人物融合已完成，两张主视融合均保留双扣。原图是偏侧视角，校正后主体对称合理，不能以原透视轮廓判定改款；单品按腰封及所附系带／链条／绳端整体核对，排除人台与页面装饰。人物与裙纹仍有重绘，不能宣称原像素保护、普遍保真或主视路线因果优势。方法与摘要见`docs/plans/2026-10-20-rectified-single-item-fusion.md`、`docs/rectified-single-item-fusion-evaluation-2026-10-20.json`；源图及派生图保留本机私有目录，未接App或改Room／备份，纯文档核对提交推送，不出包。

- 2026-10-20主视单品素材App设计：方案见`docs/plans/2026-10-20-item-tryon-material-app-design.md`，界面主流程、保存复用、七皮肤浅暗及关键状态图在`docs/visuals/2026-10-20-item-tryon-material/`。建议物品详情独立“试衣素材”、人工核对后复用，并以显式“加配饰”支持已有穿搭底图加一件腰饰；完整试衣基础服装校验继续保留。当前正式接口透明能力未验证，现有铺白管线不能冒充透明素材；拟新增素材实体、任务v5、结果底图副本及Room31／备份11，均未实施。来源链不混入只允许相册路径的ImageProvenance。HTML概念交互和14种皮肤排布校验通过，不是Compose／真服务验收。本轮0次生图，独立20次试验仍用5次；纯设计提交推送，不构建出包。

## 参考图细节保真（2.61.1）

- `TryOnImages` 在既有EXIF校正、最长边1536及透明白底后优先无损PNG，单张PNG超过4MiB退回原JPEG90，仍校验单张10MiB／总40MiB和输入数量。原文件不变、元数据不上传；三类生图共用，无新增请求、提示词或任务协议变化。
- `ReferenceImageEncoding` 按完整PNG签名选择两家传输的MIME／后缀，混合上传保留顺序与字节。`TryOnInfrastructureTest` 覆盖细柄逐像素、九图、高熵回退、EXIF和既有缩放白底；两家API回归覆盖混合封装。设计和边界见`docs/plans/2026-10-20-reference-detail-preservation.md`。手工腰封网格／保护合成仍为研究原型，不能宣称自动贴合已合入。

- 2.61.1 /132：参考图在既有缩放／方向／白底后优先无损PNG，超4MiB回退JPEG90，两家生图按实际内容封装。21项JVM、16项去重设备回归、Debug及提交源码归档clean原签名Release通过，2.61.0覆盖安装与两次启动通过；36份项目素材、依赖和生成profile核验。源码`c4ac3c62`及标签已推送，GitHub／Gitee同一正式APK和校验文件匿名下载、SHA／HEAD及公开更新状态分别记录；未重复应用内网络检查，未删除旧附件。本轮0次付费调用，累计研究38/100、余62；只证明输入保真，不宣称腰封自动贴合已解决或真实生成质量已提升。Room30／备份10、UI、任务提示词与协议不变，回执`docs/reference-detail-release-2.61.1-verification.json`。


### 主视单品素材与加配饰（2.62.0）

- 详情独立“试衣素材”区仅对稳定类型`WAIST_ACCESSORY`开放，已拥有／心愿可用；相册、封面、账本与位置不变。即梦先生成主视图，用户核对后另行确认图层拆分费用，选透明单品层并完成三项人工核对才成为当前素材。原透视摆正后主体对称可以合理，链条、系带、绳端按同一语义商品保留，不按连通块计件。七套皮肤复用标准分区、图片和图标；完整Fit、缩放及浅深检查底不写入图片。
- 首版素材创建限定真实验证过的Seedream 5.0 Pro；`layer_decomposition=true`返回底图及最多16个透明层，按实际输出张数计费，费用页最多17张与请求次数分开说明。失败／取消／中断不自动重试，可重新确认费用复用冻结主视图。切换服务使旧确认失效，不允许把即梦分层伪装成发送给其他服务。OpenAI透明编辑适配器只有协议层实现，缺少真实密钥验证，素材创建入口未开放它。
- 当前确认素材可用于完整换装或“加配饰”；完整试衣仍要求基础服装，加配饰明确选择一张已穿衣的历史图／个人模特和一件确认腰饰。人物／原裙纹可能重画，不宣称物理合身、像素保护或普遍保真。融合结果自动进入试衣历史，保存独立底图副本供对照；底图名称在v5快照中，不混作个人模特身份。
- 新请求v5冻结实际PNG、哈希、用途、服务型号、提示词和底图来源；旧v1～4不改语义。Seedream多图融合从透明素材生成铺白发送副本，费用页预览并发送同一字节，原透明文件不变。新输入超4MiB仅等比缩小PNG；素材、待确认层、冻结输入和对照图接入统一引用保护。替换采用当前素材身份核对，旧任务继续使用自己的输入；删除原物品不改绑／新建，接受后的试衣不依赖原实体仍存在。
- Room31与备份11包含素材实体、任务层清单／成功输出张数和结果底图；旧1～10补默认，ZIP恢复保留透明字节／底图并映射路径，CSV和预览同步新增素材。`ImageProvenance`继续仅用于原商品相册来源。
- 设计与概念图先归档于`docs/plans/2026-10-20-item-tryon-material-app-design.md`，实施／受控设备与真实服务分开记录于`docs/item-material-implementation-2026-10-21.md`。真实Compose七皮肤浅暗、320dp两倍字、真实键盘及NavHost底栏图见`docs/visuals/2026-10-21-item-material-native/`。新100张额度仅用4次请求／6张成功输出（含分层底图）、余94，无自动重试，私有照片与派生图不入公开包。

- 2.62.0 /133正式交付：源码`be57547d`与标签已推送，提交源码归档clean原签名Release通过；独立AVD从2.61.1覆盖并两次启动，35张旧表原字段与11张原图片保全，真实正式包可从既有心愿腰饰详情进入素材页。36份项目assets、7张背景RGBA及依赖／生成profile核验，测试插画与私有照片未入主包。GitHub／Gitee同一APK和SHA文件匿名完整下载／HEAD通过，Gitee公开最高正式版本选中2.62.0；未重跑应用内网络检查、未删旧附件。首版创建限即梦；新100张额度用4次请求／6张成功输出（含分层底图），余94，无自动重试。回执`docs/item-material-release-2.62.0-verification.json`。

### 批量整理素材任务流（2.63.0）

- 本轮按用户要求只做素材批量整理，不新增批量融合。任务中心“新建素材批次”进入`Screen.MaterialBatch`，一次主动选择1至30件已拥有／心愿腰饰，默认零选择；无素材的物品详情只预选当前件，可继续增加。搜索／筛选保留隐藏选择，逐卡换图／框选，集中核对主视图、选择透明单品层和三项核对后批量保存；有素材的详情仍进原管理与单件融合入口。
- `MaterialBatchViewModel`使用StateFlow及SavedStateHandle；费用确认冻结全部实际输入、服务、补充要求和操作标识。主视生成与分层各自统一确认N次请求，分层最多17N张输出含底图；失败项独立重新确认，不自动重试或跨阶段计费。全部输入验证和任务插入事务完成后才启动服务，操作标识确定任务ID以防重复接受；批量保存验证当前素材身份，任何一项失效则整体回退。
- Room31→32只追加`image_tasks.batch_id/batch_name/batch_position`可空列，36实体不变，旧任务不推断分组。任务中心按批次归并，子阶段沿用同一位置及物品；原v1～5请求与单件流程保留。已接受主视图在进入分层后仍由批次任务保护，ZIP保存并映射到新路径，供回看。备份12兼容旧1～11缺失批次，CSV追加三列，恢复活动任务转中断而不重发。
- 图稿先于实现，七皮肤浅暗与真实320dp两倍字体／IME、真实NavHost验证见`docs/plans/2026-10-21-material-batch-flow.md`及`docs/material-batch-implementation-2026-10-21.md`；批量事务／重复提交／独立失败／原资料保全／ZIP与回看图保护、迁移和旧单件执行／取消清理均定向覆盖。新增真实付费调用0次，沿用2.62.0已验证的即梦适配器，不以受控测试宣称更多品类保真或融合质量提升。

- 2.63.0／134正式交付：源码`f43762d2`及标签已推送，定向批量／原单件执行与清理回归、Debug及提交归档clean原签名Release通过；63张真实测试截图、七皮肤浅暗／320dp两倍字／真实IME／NavHost与正式App入口核对。2.62.0同签名覆盖并两次启动保全36张旧表原列与11张原图SHA；36份项目assets、7背景RGBA及依赖／profile核验。GitHub／Gitee同一APK匿名完整下载、SHA／HEAD及公开最高正式版本查询通过，未重跑应用内网络检查、未删旧附件。新增付费0次，仅批量整理素材，回执`docs/material-batch-release-2.63.0-verification.json`。

### 服饰与小物批量素材（2.64.0）

- 用户要求解除腰饰限定：`supportsMaterialPreparation`覆盖除待确认外全部稳定品类，服装、假发／发片、袜装、鞋履、头饰配饰、包袋、手持物可混合1～30件整理。状态仍为已拥有／心愿，类型不按可编辑名称猜测。工作台增加七类筛选和名称／品牌／类型搜索，隐藏选择保留；卡片显示真实类别及单件／双／组。详情入口及已有素材管理同步开放。
- 新通用素材专用请求v6／提示词修订2冻结语义、默认穿戴角色及组合；服装保留领袖下摆／裙撑、发片不补整顶、鞋具保留必要侧向、单只不补双，明确双／组与附属件作为同一前景。旧v1～5不改，旧腰饰分层及新腰饰整理仍沿用v5。后续分层／保存／已确认素材复用复核类型，v6再核对组合；原完整试衣继续要求基础服装，原“加腰饰”用途未扩大。
- Room32／36实体和图片字段不变；备份13表达v6用途，兼容旧1～12的请求和分组，拒绝将v6伪装成旧格式。完整ZIP仍保存并映射各物品冻结输入／预览／素材，恢复不重发。新状态不触碰原照片、账本、位置、拥有状态或穿过记录。重进排队分层的批次优先打开实际分层阶段。
- 定向协议、备份、混合队列／保存／ZIP、旧任务、组合变化、完整试衣复用、搜索／筛选、七皮肤浅暗与真实320dp两倍字／IME回归见`docs/material-categories-implementation-2026-10-21.md`。前置图稿与范围见`docs/plans/2026-10-21-material-all-categories.md`，原生截图为受控运输和原创矢量夹具，不冒充模型质量。
- 本轮真实请求4次：衬衫与靴子主视成功、靴子分层成功（含底图2张）、衬衫分层超时且不重试，新增成功输出4张。独立100张额度累计8次请求／10张已知成功输出；超时最多17张按保守预留，合计占用27、保守余73，实际服务端是否计费未知。其他类别未逐项真实测试，模型仍会重绘细节。私有照片与输出不入公开源码／APK，记录`docs/material-categories-service-evaluation-2026-10-21.json`。

- 2.64.0／135正式交付：源码`94721aa5`及标签已推送，定向跨品类／旧请求与批次／ZIP／完整试衣复用、七皮肤浅暗／320dp两倍字／真实IME通过。提交归档clean原签名Release从2.63.0覆盖并两次启动，36旧表原列、v5批次与14图SHA保全；36主包assets、7背景RGBA及依赖／profile核验，测试夹具未入主包。正式App服装／鞋履及裙装详情批次入口已核对；GitHub／Gitee同一APK匿名完整下载、SHA／HEAD及公开最高版本查询通过。未重跑应用内网络检查、未删旧附件；本轮4次真实请求含1次分层超时未重试，保守余73张。回执`docs/material-categories-release-2.64.0-verification.json`。

### 实验室与统一展示图（2.65.0）

- 个人页一级“实验室”卡片集中提供批量生成展示图、虚拟试衣、我的模特、假发头模图、识图录入、任务中心和统一API配置入口；业务上下文内快捷入口保留。`Screen.Laboratory`与`LaboratoryViewModel`使用现有薄仓库流，假发选择按稳定WIG_HEAD身份且有参考图片，可搜索，不按名称猜类型。
- 详情只保留一个普通“生成展示图”入口，预选当前物品进入已有批量素材流程；已确认预览可进入管理、设封面及试衣。普通服装人台与小物旧入口退出详情；全头假发戴头模保留独立MANNEQUIN/WIG_HEAD逻辑，原历史任务/待确认/重试不改协议。
- `ImageTaskCoordinator.setMaterialCover`事务复核当前素材ID、类型/组合、透明图完整性和SHA，再从最新相册置首共享路径并写AI_GENERATED来源。主视/透明层确认不自动改封面，显式设置保留原照片与其他资料；重复设置幂等，移除/替换素材和删除任务不能误删相册引用。相册、素材与ZIP共用原引用保护和路径映射，Room32/36实体、备份13不变。
- 前置四页/七皮肤浅暗/320dp图稿与设计记录：`docs/plans/2026-10-21-laboratory-display-flow.md`。18项去重定向设备回归与Debug通过，实际五组七皮肤浅暗及320dp两倍字/真实IME归档在`docs/visuals/2026-10-21-laboratory-native/`；实施记录为`docs/laboratory-display-implementation-2026-10-21.md`。本轮不新增真实付费请求，入口迁移与设封面仅本地操作，受控图片不冒充生成品质。

- 2.65.0／136正式交付：源码`9b9f7a7f`与标签已推送，18项去重定向设备回归、Debug及提交归档clean原签名Release通过；真实七皮肤浅暗／320dp两倍字／IME与正式App入口已核对。2.64.0覆盖及重复启动保全36旧表、原批次及14图SHA（本轮未捕获可选设置字节前基准，不声称设置逐字节验证）；36主包assets、7背景RGBA及依赖／profile核验。GitHub／Gitee正式附件匿名完整下载、SHA／HEAD与公开最高正式版本查询通过；Gitee首次发布请求超时后安全恢复，未删旧附件、未重跑App内手动网络检查。新增生图0次，保守余73张；回执`docs/laboratory-display-release-2.65.0-verification.json`。

### 队列停止与批量舍弃（2.66.0）

- 任务中心与素材批次新增停止后续排队和多选舍弃，当前执行可继续；整批停止先原子处理队尾，再取消当前读者。候选默认零选择，支持全选/清空、完整Fit和放大；状态变化项跳过，原图/已保存资料/共享图片保留。设计与20项去重定向设备回归、三组七皮肤浅暗及真实320dp两倍字见`docs/task-queue-controls-implementation-2026-10-21.md`。
- 2.66.0／137源码`b0a43f69`及标签已推送，Debug与提交归档clean原签名Release通过；2.65.0覆盖及重复启动保全36旧表、原任务/批次、14图SHA和两份持久设置，36主包assets/7背景RGBA及依赖/profile核验。GitHub/Gitee同一APK匿名完整下载、SHA/HEAD、源码标签和公开最高版本查询通过；未重跑App内手动网络检查、未删旧附件。Room32/备份13/旧v1～6不变，本轮真实生图0次。回执`docs/task-queue-controls-release-2.66.0-verification.json`。


## 统一字体与2K适配（2.66.1）

- Type.kt统一七皮肤字号、行高和字重，公共顶栏／分区、高频列表与设置条目消费同一角色，阅读／输入／操作及金额保持统一字体；首页四标签与控制同排，筛选角标按真实高度预留空间。统计摘要优先，完整品类明细独立分区，原隐私与导航保留。设计和实施见`docs/plans/2026-10-21-typography-system.md`、`docs/typography-implementation-2026-10-21.md`。
- 2.66.1／138：源码`0325e7af`与标签已推送，14项去重定向设备回归、Debug及提交归档clean原签名Release通过。1440×3200七皮肤浅暗、320dp两倍字体／真实IME、数量0/3/12与独立设计角色复核通过；2.66.0覆盖及两次启动保留中华皮肤与3份既有设置SHA。36主包assets、7背景RGBA及依赖／profile与提交源码一致；纯字体布局不重复全库／图片／备份审计。GitHub／Gitee正式同一APK与校验文件匿名完整下载、SHA／HEAD及公开最高正式版本查询通过；未重跑App手动网络检查，未删旧附件、无新生图调用，Room32／备份13不变。回执`docs/typography-release-2.66.1-verification.json`。


- 2.66.2／139收敛实验室导航：个人入口与属性管理同为16/24sp、Medium500及阅读字体，公共顶栏18/26sp、Medium500，主功能22sp及其他规格保持。源码`8b5f383f`与标签已推送，4项最终去重定向设备方法、Debug与提交归档clean原签名Release通过；七皮肤浅暗／320dp两倍字／真实IME与50张相关原生截图、实际入口及同级样式测量通过。2.66.1覆盖及重复启动保全中华皮肤与3份设置SHA，正式实拍已核对；独立AVD的System UI ANR以保留数据的重启解决，未改应用逻辑或放宽IME断言。GitHub／Gitee正式同一附件匿名完整下载、SHA／HEAD及公开最高版本查询通过；未删旧资产、未重复App手动网络检查或无关全库／图片／备份审计，无新生成调用。设计与回执见`docs/plans/2026-10-21-compact-laboratory-titles.md`、`docs/compact-laboratory-titles-release-2.66.2-verification.json`。


- 2.66.3／140 全界面文案优化：按43路由报告精简重复解释，统一心愿／金额／拥有与类型语义，低频明细折叠、必要后果与费用常显。源码`f6f65ee5`和标签已推送；148项去重设备方法、3项JVM及Debug／提交归档clean Release通过，七皮肤浅暗、真实320dp两倍字与IME及正式页面已核对。2.66.2原签名覆盖两次启动保留中华皮肤与3份设置SHA；36项目assets／7背景RGBA与依赖／profile通过。GitHub／Gitee同一APK和校验文件匿名完整下载、SHA／HEAD及公开最高正式版本已确认；未删旧附件、未清用户数据、真实付费0次。应用内实际检查结果单独记录，不以公开接口代替。实施与回执见`docs/ui-copy-implementation-2026-10-22.md`、`docs/ui-copy-release-2.66.3-verification.json`。


- 2.66.4／141实验室入口对齐与独立图标：源码`de28bff2`及标签已推送，实验室共用同级SettingsMenuItem文字列和内边距，七套新增独立烧瓶图标。clean Debug／提交归档clean Release、3项定向设备方法通过；52×7真实图标尺寸／tint／透明与七皮肤浅暗入口坐标、320dp两倍字／IME通过，原51键5矩阵共1,785单元与2.66.3逐像素一致。原签名2.66.3覆盖与两次启动保留中华皮肤及3份设置SHA，正式包标题／说明与属性入口左边缘均234px。36项目assets／7背景RGBA及依赖／profile、双渠道正式附件匿名完整下载／SHA／HEAD与公开最高版本通过；App实际更新检查另存回执，未删旧附件、未清用户数据、无付费生成。记录见`docs/laboratory-entry-alignment-implementation-2026-10-22.md`及`docs/laboratory-entry-release-2.66.4-verification.json`。

- 2.67.0／142两轮体验设计落地：源码`94b13c23`及标签已推送，金额隐私、唯一文字保存、空态插画与入口、实验室／试衣、备份URI结果、任务卡、收纳和底栏大字已实现。28项去重定向设备方法、七皮肤浅暗／真实320dp两倍字／IME及Debug、提交归档clean Release通过；2.66.4原签名覆盖两次启动保留3份设置摘要，37项目assets／7背景RGBA及依赖／profile通过。GitHub与Gitee同一正式APK匿名完整下载／SHA／HEAD通过；Gitee公开版本API403，App一次实际检查显示暂时拒绝访问或请求频繁，未冒充检查成功或更改网络代码。未删旧附件、未清用户资料，应用付费调用0次；内置image_gen装饰插画1次已归档提示词和原RGBA。记录与回执见`docs/creative-design-implementation-2026-10-22.md`、`docs/creative-design-release-2.67.0-verification.json`。

- 2.68.0／143订单导入完整设计实施：源码`fa7797c3`及标签已推送，持久会话／来源查重与冲突／金额原值／原子回执／品牌与原因核对／后续收纳／原物品补资料已实现。43项去重设备方法、12项新解析/可靠性JVM及原备份/记账定向回归、184张真实七皮肤浅暗／320dp两倍字／IME与Debug、提交归档clean原签名Release通过。2.67.0覆盖两次启动保全36旧表原字段、14图SHA及3份设置，Room33／42表／备份14；37项目assets／7背景RGBA及依赖/profile核验。GitHub／Gitee同一正式APK与校验文件匿名完整下载／SHA／HEAD通过，源码标签及公开最高正式版本2.68.0核验，正式App一次手动检查正确显示最新2.68.0和完整更新说明。Gitee初次1GB配额HTTP400后，本轮仅按用户明确授权删除2.60.2旧APK，释放80,947,492字节，旧发布／校验／GitHub及本机副本保留；清理回执`docs/gitee-apk-cleanup-2026-10-23.json`，不自动授权未来清理。实现与当前回执见`docs/order-import-implementation-2026-10-23.md`、`docs/order-import-release-2.68.0-verification.json`；应用真实付费0次。

- 2.69.0／144价格体验前两批：源码`6dc28e92`与标签已推送；输入草稿／恢复核验、金额隐私、记账反馈、订单总览／实付／建单及年历预算衔接落地。33项去重设备方法、22项JVM、252张七皮肤浅暗／320dp两倍字及真实IME／完整App旋转和进程恢复通过。Debug及提交归档clean原签名Release通过，2.68.0覆盖两次启动保留6张相关表原字段和3份设置SHA，37项目assets／7背景及依赖/profile核验。GitHub／Gitee同一正式APK及校验文件匿名完整下载／SHA／HEAD、源码标签与公开最高正式版本通过；正式App一次手动检查正确显示最新2.69.0及六项更新说明。Gitee初次1GB配额HTTP400后，仅按本轮用户明确授权删除2.61.0旧APK（80,963,876字节），原发布／校验／GitHub和本机副本保留；清理回执`docs/gitee-apk-cleanup-2.61.0-2026-10-23.json`，不授权未来清理。Room33／42实体／备份14不变，应用付费调用0次。实施与当前回执见`docs/price-experience-implementation-2026-10-23.md`、`docs/price-experience-release-2.69.0-verification.json`。
