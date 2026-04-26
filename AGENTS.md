# 开发协作流程

## 项目事实

- 单模块 Android 应用，Kotlin 2.1.0 + Jetpack Compose (BOM 2024.12.01) + Material3。compileSdk 35, minSdk 26, targetSdk 35, JVM target 17。
- 数据流：`Entity (Room) -> DAO -> Repository -> ViewModel (StateFlow) -> Composable`
- DI 不是 Hilt/Dagger，是 `AppModule` 手写 service locator；`LolitaApplication.onCreate()` 初始化。ViewModel 通过构造参数默认值从 `AppModule` 取仓库。
- Room 注解处理用 **KSP**（不是 Kapt）。Schema 导出目录 `app/schemas/`。
- USB 设备测试套件：`bash run_device_test.sh`（需 `export MSYS_NO_PATHCONV=1`），验证以编译 + 设备测试为主。
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

- Room 数据库版本 18，23 个实体（含 `CatalogEntry`、`SharedLibrarySyncState`、6 个 `Remote*` 远程同步实体）。
- Migration 定义在 `LolitaDatabase.kt` 内联，每次改结构必须升 version + 写 Migration。
- 外键策略：子记录跟主记录删除用 `CASCADE`（Price→Item, Payment→Price）；被引用对象不该在使用中删除用 `RESTRICT`（Item→Brand, Item→Category, Item→Coordinate）。
- 新增图片字段统一走 `ImageFileHelper`（存到 `context.filesDir/images/`，UUID 文件名），删除记录时清理图片文件。
- 新功能状态若由历史数据推导，需考虑一次性历史刷新，优先批量 SQL 而非逐条循环，在 `LolitaApplication.onCreate()` 调用。

## 备份兼容

新增实体或字段时同步检查 `BackupManager`：
- 导出 / 导入 / 预览逻辑都要补
- 导入须兼容旧备份（缺失字段容错）
- 如适用则补 CSV 导出列

## 导航与页面

- 导航统一走 `Screen` sealed interface + `LolitaNavHost`，新增页面两边都补。
- 列表/详情/编辑页成套考虑。
- `GradientTopAppBar` 是标准顶栏，默认 `compact = true`，无明确理由不改。
- `LolitaNavHost` 外层 `Scaffold` 设 `contentWindowInsets = WindowInsets(0,0,0,0)`，`GradientTopAppBar` 内部处理 `statusBarsPadding()`。
- 卡片复用 `LolitaCard`（已集成皮肤 `cardElevation` + `cardBorderStroke`）。
- 详情页分区标题复用 `SectionHeader`（左侧竖线 + 标题 + 可选操作按钮 + 分割线，皮肤感知）。
- 图片展示区复用 `ImageFrame`（画框容器，皮肤感知边框/阴影/内边距）。
- 详情页信息区块（基本信息/描述/尺码/价格/记录等）用 `LolitaCard` + `SectionHeader` 包裹，不用裸 Text + HorizontalDivider。
- Tab 页面沿用 `HorizontalPager + TabRow + SkinTabIndicator`。
- 选项多时（如品牌 200+）用可搜索对话框，不退回简单下拉框。

## 皮肤系统

- **7 套皮肤**：`DEFAULT` / `GOTHIC` / `CHINESE` / `CLASSIC` / `NAVY` / `COUNTRY` / `VICTORIAN`。
- 新页面/交互复用皮肤感知组件，不做脱节的普通 Material 页面。
- 新增图标需同时补：`BaseSkinIconProvider` + **7 个**皮肤专属 provider（`SweetIconProvider`, `GothicIconProvider`, `ChineseIconProvider`, `ClassicIconProvider`, `NavyIconProvider`, `CountryIconProvider`, `VictorianIconProvider`）+ `IconKey` 枚举。图标是 Canvas 手绘，不是 Material icon。
- 新增列表/切换/过渡效果优先复用现有 skin 动画体系（`SkinClickable`, `SkinItemAppear`, `SkinNavigationOverlay` 等）。

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

新增视觉令牌时需同步：`LolitaSkinConfig` 数据类 + **7 个**皮肤工厂函数 + dark mode 变体。

### 后续优化方向

- 逐皮肤深化装饰层（如 `SkinDecorationProvider` 接口，华丽皮肤覆写提供 Canvas 角落花纹/画框金边等）
- 其他详情页（`CoordinateDetailScreen`、`CatalogDetailScreen`、`OutfitLogDetailScreen`、`LocationDetailScreen`）应用同样的 `LolitaCard` + `SectionHeader` 模式
- 对话框皮肤化
- `ImageFrame` 应用到详情页图片区域

## 构建与验证

```bash
./gradlew.bat assembleDebug        # 改代码后至少跑一次
./gradlew.bat assembleRelease      # 出包时跑
./gradlew.bat clean assembleDebug  # 清理后构建
./gradlew.bat clean assembleRelease # 出 release 包前务必 clean
```

- Release 签名需 `local.properties` 中配置 `STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，密钥文件为项目根 `lolita-release.jks`。
- Release 输出：`app/build/outputs/apk/release/app-release.apk`。
- 编译失败先修到通过再给结论。
- **增量编译缓存陷阱**：Kotlin 增量编译可能未正确重编译跨文件依赖（如 `VictorianIconProvider` 依赖 `VictorianDecorativeIcons`），导致 release 包运行时回退到基类默认行为（图标变成 Material 默认图标）。debug 包因编译路径不同可能不受影响。**出 release 包前必须 `clean`**，遇到"debug 正常但 release 异常"的症状优先尝试 `clean assembleRelease`。

## USB 设备测试

```bash
export MSYS_NO_PATHCONV=1          # Git Bash 路径转换修复（必须）
bash run_device_test.sh            # 运行 UI 自动化测试套件
```

- 测试基于 `adb` + `uiautomator` + `screencap`，无需额外依赖。
- 前提：USB 连接 Android 设备（debug APK 已安装，USB 调试已开启）。
- 验证方式：截图大小对比判断页面切换，UI 树文字检查，logcat 崩溃检测。
- **Compose 限制**：`uiautomator dump` 对 Compose 页面仅能获取导航标签等静态文字，无法可靠检测动态内容；页面变化判断依赖截图像素差异。
- `ADB` 路径默认 `C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe`，可通过环境变量 `ADB` 覆盖。
- 结果输出：`test_results.txt`，截图存 `test_screenshots/`。

### 测试覆盖范围（14 组）

| 组 | 测试内容 | 验证方式 |
|----|----------|----------|
| G1 | 应用启动、底部导航 5 tab 可见、顶部 4 tab 可见 | UI 树文字 + 截图 |
| G2 | 底部导航切换（首页→愿望单→穿搭→统计→个人→首页） | 截图大小变化 |
| G3 | 首页 4 个 top tab 切换（位置/服饰/套装/图鉴） | 截图大小变化 |
| G4 | 服饰 tab FAB 打开新增服饰页 | 截图大小变化 |
| G5 | 套装 tab FAB 打开新增套装页 | 截图大小变化 |
| G6 | 图鉴 tab FAB 打开新增图鉴页 | 截图大小变化 |
| G7 | 个人/设置页加载与滚动 | 截图大小变化 |
| G8 | 统计页加载与滚动 | 截图大小变化 |
| G9 | 穿搭日志页加载 | 截图 |
| G10 | 愿望单页加载 | 截图 |
| G11 | 编辑页返回导航 | 截图大小变化 |
| G12 | 崩溃与 ANR 检测 | logcat |
| G13 | 数据备份与恢复（滚动至导入区→打开文件选择器→安全返回） | 截图大小变化 + activity 检测 |
| G14 | 淘宝订单导入（选择 Excel → 订单列表 → 下一步） | 截图大小变化 |

### 设备坐标参考（1116×2480 @ 480dpi）

- 底部导航 y=2330（手势导航区域 y≥2372 会被系统拦截）：首页 x=102 / 愿望单 x=330 / 穿搭 x=558 / 统计 x=786 / 个人 x=1014
- 顶部 tab y=159：位置 x=207 / 服饰 x=381 / 套装 x=555 / 图鉴 x=720
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

- 刷新版本号：`versionCode + 1`，`versionName` 顺延（当前 versionCode=46, versionName="2.29.0"）。
- 新功能升 minor，常规修复升 patch，大改/不兼容升 major。

## 提交

- 构建通过后再提交。
- commit message 用英文短句 + `feat:` / `fix:` / `chore:` 前缀。
- 不提交 `.gradle-user-home/`、`.kotlin/`。

## AGENTS.md 维护

- 每次功能更新、架构变更或新增约定后，**必须刷新 `AGENTS.md`**，确保文档与代码同步。
- 新增组件、令牌、接口、页面、测试等均需在对应章节补充说明。
- 若不确定是否需要更新，默认更新。
