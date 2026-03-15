# 开发协作流程

后续在这个项目里，默认按下面这套流程执行，除非用户在当次对话里明确要求改动。

## 0. 项目事实

- 这是一个单模块 Android 应用，技术栈是 Kotlin + Jetpack Compose + Material3。
- 当前数据流默认按这条链路理解：`Entity (Room) -> DAO -> Repository -> ViewModel (StateFlow) -> Composable`。
- 依赖注入不是 Hilt / Dagger，而是 `AppModule` 手写 service locator；`LolitaApplication.onCreate()` 负责初始化。
- ViewModel 默认通过构造参数从 `AppModule` 取仓库，保持现有风格，不随手改成别的注入方式。
- Room schema 导出目录是 `app/schemas/`。
- 当前 Room 注解处理使用的是 `KSP`，不是 `Kapt`。
- 目前没有稳定的自动化测试套件，默认以编译验证为主。
- UI 文案默认使用中文。

## 1. 开始工作前

- 先查看相关代码、数据流和现有实现，不直接拍脑袋改。
- 先看 `git status --short`，确认当前工作区是否有用户未提交改动。
- 如果任务跨越多个模块，先整理一个简短计划，再开始动手。

## 2. 功能开发流程

- 先最小范围定位改动点，再做实现，尽量避免无关重构。
- 涉及导航时，同时检查：
  - `Screen.kt`
  - `LolitaNavHost.kt`
  - 对应列表 / 详情 / 编辑页面
- 涉及 Room 数据结构时，同时检查：
  - 实体、DAO、Repository
  - `LolitaDatabase.kt`
  - migration
  - `app/schemas/`
- 涉及备份恢复时，同时检查：
  - `BackupManager.kt`
  - 预览计数
  - JSON / ZIP / CSV 导入导出
- 涉及“可转化”为衣橱数据的功能时，默认检查图片复制、关联关系回写、失败回滚。

## 2.1 架构与实现约定

- 新 DAO 要同步接到：
  - `LolitaDatabase`
  - 对应 `Repository`
  - `AppModule`
- 新 ViewModel 继续使用 `MutableStateFlow` / `StateFlow` 管理 UI 状态，异步逻辑放在 `viewModelScope.launch`。
- Repository 默认保持“薄封装”风格，优先围绕 DAO 提供响应式查询和少量业务拼装，不随手塞进过重逻辑。
- `List<String>` 字段优先复用现有 Gson `TypeConverters` 方案。
- 枚举字段如果新增类型，需要同步补 `TypeConverter`。
- 风格、季节、来源这类字符串属性如果支持重命名或删除，要同步检查历史数据级联更新或清空逻辑。

## 2.2 导航与页面约定

- 导航统一走 `Screen` + `LolitaNavHost`，新增页面时两边都要补。
- 列表 / 详情 / 编辑页通常要成套考虑，不只改单页入口。
- `GradientTopAppBar` 是标准顶栏，默认保持 `compact = true`，没有明确理由不要改成别的样式。
- 卡片内容优先复用 `LolitaCard`。
- 有 tab 的页面优先沿用 `HorizontalPager + TabRow + SkinTabIndicator`。
- 有搜索量较大的选择器，优先参考现有“可搜索对话框”模式，不默认退回简单下拉框。

## 2.3 皮肤系统约定

- 项目有 5 套皮肤：`DEFAULT` / `GOTHIC` / `CHINESE` / `CLASSIC` / `NAVY`。
- 新页面或新交互优先复用现有皮肤感知组件，避免做成和项目视觉体系脱节的普通 Material 页面。
- 如果新增图标，需要同时检查：
  - `BaseSkinIconProvider`
  - 5 个皮肤专属 icon provider
  - `IconKey`
- 如果新增明显的列表、切换或过渡效果，优先复用现有 `skin` 动画体系，而不是单独造一套。

## 3. 验证要求

- 只要改了代码，默认至少跑一次 `:app:assembleDebug`。
- 用户要求出包、刷新版本号或验证发布链路时，再跑 `:app:assembleRelease`。
- 如果编译失败，先修到编译通过，再给结论。
- 如果没法跑某项验证，要明确说明原因，不假装已验证。
- 常用命令默认参考：
  - `./gradlew.bat assembleDebug`
  - `./gradlew.bat assembleRelease`
  - `./gradlew.bat clean assembleDebug`

## 4. 发布流程

- 用户要求“刷新版本号”时，默认在当前版本基础上递增一版：
  - `versionCode + 1`
  - `versionName` 按当前数字版本顺延
- 版本号语义默认参考：
  - 新功能优先升 minor
  - 常规修复优先升 patch
  - 大改或不兼容改动再考虑升 major
- 出正式包后，确认：
  - `app/build/outputs/apk/release/app-release.apk`
  - `output-metadata.json`
- 如果 release 构建只有 warning 但不阻塞出包，需要在结果里说明。

## 5. 提交流程

- 默认在构建通过后再提交。
- commit message 使用清晰的英文短句，优先采用 `feat:` / `fix:` / `chore:` 前缀。
- 不把以下缓存目录提交进仓库：
  - `.gradle-user-home/`
  - `.kotlin/`
- 不主动清理或覆盖用户已有改动，除非用户明确要求。

## 5.1 数据一致性规则

- 新增实体或字段时，默认同步考虑备份兼容：
  - `BackupManager` 的导出
  - 导入
  - 预览
  - 如适用则补 CSV
- 新增图片字段时，统一走 `ImageFileHelper`，并考虑删除记录后的图片清理。
- 外键策略保持现有风格：
  - 子记录跟随主记录删除时用 `CASCADE`
  - 被引用对象不应在使用中删除时用 `RESTRICT`
- 如果新功能的状态是由历史数据推导出来的，需要考虑一次性历史数据刷新，优先用批量 SQL，而不是逐条循环。

## 6. 回复方式

- 先说结果，再补验证情况和剩余风险。
- 对大改动按“功能 / 验证 / 风险”总结，不堆文件清单。
- 如果发现可疑问题但这轮没有顺手修，要明确点出来。
