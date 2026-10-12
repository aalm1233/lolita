# 七套图标个性恢复 Implementation Plan

**Goal:** 七套皮肤整套图标恢复可见造型身份，实际 24/26dp 同色仍有差异。
**Spec:** `docs/superpowers/specs/2026-10-12-icon-personality-design.md`
**Architecture:** 原有 provider → PolishedSkinIcon → 三组 DrawScope 绘制入口；独立三文件并行，不改业务或 API。
**Tech Stack:** Kotlin 2.1 / Compose Canvas / Android API 35。

## Global Constraints

- 现有 51 key 与七套 provider 全部保留；禁止回退 Material、位图、固定色、装饰背板缩小主符号。
- 24×24、主线宽 1.7、光学留白、tint/alpha、矩形缩放契约保持。
- 简单箭头/加号保持直接语义，主题以端部/笔触体现；其他主体不能只差圆角或细内纹。
- 实现代理只编辑各自绘制文件；不改共用入口、测试、文档、版本，不运行 Gradle/ADB/Git。根代理统一验证与整合。
- 这是可逆外观改动，复用真实渲染回归，不添加镜像实现的美观测试。

## Review Focus

- 18/24dp 下图标不能被边框或细节挤小：现有 bounds/size 回归与真实矩阵审查。
- 古典/维多利亚、甜美/田园的同名图标有不同主体：同色横向图像审查。
- 暗色与透明 tint 不泄漏固定色：现有 tint/alpha 渲染回归。
- 星标实/空心轮廓同形、刷新/删除/断链/状态含义可辨：源码与真实矩阵审查。
- 输入为 40×24 矩形仍等比居中，调用尺寸优先：现有矩形与尺寸回归。

### Task 1: 导航

**Modify:** `app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedNavigationIcons.kt`
**Interface:** `internal fun DrawScope.drawPolishedNavigationIcon(key: IconKey, style: SkinIconStyle, tint: Color)`。

- [x] 阅读现有五个导航与规格；每套 Home/Wishlist/Outfit/Stats/Settings 用主题主体表达，尤其分开四组原重复造型。
- [x] 保留入口和功能含义，helper 私有，皮肤分支可读；自查所有七套、24×24、tint、安全边距。
- [x] 写独立任务报告，说明形态决策、已实现 key 与需真实渲染复核处。

### Task 2: 内容

**Modify:** `app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedContentIcons.kt`
**Interface:** `internal fun DrawScope.drawPolishedContentIcon(key: IconKey, style: SkinIconStyle, tint: Color)`。

- [x] 十四个 key 全部设计审阅，位置/分类/相机/图片/日历/钱币/通知/链接等有各套物件结构；不能只给矩形改角。
- [x] 实/空心星标共用同一 Path；AddPhoto 主加号明确；LinkOff 断链明确；透明输入与原 tint.alpha 保全。
- [x] 保留入口、私有 helper；自查与报告。

### Task 3: 操作、箭头、状态

**Modify:** `app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedControlIcons.kt`
**Interface:** `internal fun DrawScope.drawPolishedControlIcon(key: IconKey, style: SkinIconStyle, tint: Color)`。

- [x] 审阅全部三十二个 key，删除/搜索/编辑/复制/刷新/视图等常用工具在主体上增强主题；简单符号用连贯端部与笔触。
- [x] 功能不替换成无关装饰物；主符号不叠盾牌/缎带/花环背板，单轮廓状态牌可保留语义框。
- [x] 保留入口、私有 helper；自查与报告。

### Task 4: 根代理验证与整合

**Modify:** `SkinIconVisualTest.kt` 的导出概览；`AGENTS.md` 的图标造型约定。
**Create:** `docs/icon-personality-2026-10-12.md` 与 `docs/visuals/2026-10-12-icon-personality-*.png`。

- [x] 三项实现逐项规格/质量审查，统一视觉语法；修复真实图像中的拥挤、截断、辨识和相似问题。
- [x] 在现有 render 流程增加 26dp 浅暗中文概览导出、内容组中文概览；不改变几何/tint 断言。
- [x] `assembleDebug testDebugUnitTest assembleDebugAndroidTest`；自建独立 AVD，核对签名，安装 -r，仅运行 `SkinIconVisualTest`。
- [x] 查看七套全部 51 key 浅暗完整图，18/26/32dp 和矩形；按图修正后复验，只在新改动或失败时重复。
- [x] 刷新 AGENTS 与验证文档；完整独立审查；将自己的文件同步到主工作区，逐字节保留无关文件；展示对照与 Debug，不自动提交/发布。
