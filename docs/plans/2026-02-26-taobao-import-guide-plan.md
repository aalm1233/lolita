# 淘宝导入指南页面 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在淘宝导入页面添加帮助按钮，点击打开全屏指南页面，讲解如何从淘宝导出订单 Excel 文件。

**Architecture:** 新增 `TaobaoImportGuideScreen` Compose 页面，通过 `Screen.TaobaoImportGuide` 路由导航。在 `TaobaoImportScreen` 的三个步骤（SELECT/PREPARE/DETAIL）的 `GradientTopAppBar` actions 区域添加 HELP 皮肤图标按钮。

**Tech Stack:** Kotlin, Jetpack Compose, Canvas (皮肤图标), Room (无变更)

---

### Task 1: 新增 HELP 图标到皮肤系统

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/IconKey.kt:14`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIconProvider.kt:63-70`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/BaseSkinIconProvider.kt:155-168`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SkinIcon.kt:65-71`

**Step 1: Add `Help` to `IconKey` enum**

在 `IconKey.kt` 的 Status 行末尾添加 `Help`：

```kotlin
// Status
CheckCircle, Warning, Error, Info, Visibility, VisibilityOff, Help
```

**Step 2: Add `Help` to `StatusIcons` interface**

在 `SkinIconProvider.kt` 的 `StatusIcons` interface 中添加：

```kotlin
@Composable fun Help(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
```

**Step 3: Add default `Help` to `BaseStatusIcons`**

在 `BaseSkinIconProvider.kt` 的 `BaseStatusIcons` 类中添加（使用 `Icons.Filled.HelpOutline`）：

```kotlin
@Composable override fun Help(modifier: Modifier, tint: Color) =
    Icon(Icons.Filled.HelpOutline, null, modifier, tint)
```

需要添加 import: `import androidx.compose.material.icons.filled.HelpOutline`

**Step 4: Add `Help` case to `SkinIcon` composable**

在 `SkinIcon.kt` 的 Status 区域末尾添加：

```kotlin
IconKey.Help -> icons.status.Help(modifier, tint)
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/
git commit -m "feat: add Help icon key to skin icon system"
```

---

### Task 2: 实现 5 个皮肤的 Help 图标

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/SweetIconProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/GothicIconProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ChineseIconProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/ClassicIconProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/icon/NavyIconProvider.kt`

**Step 1: Sweet — 圆润问号 + 心形装饰**

在 `SweetStatusIcons` 类中添加 `Help` override。圆圈用 `sweetStroke`，问号用圆润笔画，顶部小心形替代圆点。

**Step 2: Gothic — 尖锐问号 + 十字装饰**

在 `GothicStatusIcons` 类中添加 `Help` override。圆圈用 `gothicStroke`（Butt cap, Miter join），问号用尖锐直线段，顶部小十字替代圆点。

**Step 3: Chinese — 书法问号 + 云纹装饰**

在 `ChineseStatusIcons` 类中添加 `Help` override。圆圈用 `chineseStroke`，问号用毛笔感曲线，顶部小云纹替代圆点。

**Step 4: Classic — 优雅问号 + 衬线装饰**

在 `ClassicStatusIcons` 类中添加 `Help` override。圆圈用 `classicStroke`，问号用优雅曲线，底部加小衬线装饰。

**Step 5: Navy — 流畅问号 + 波浪装饰**

在 `NavyStatusIcons` 类中添加 `Help` override。圆圈用 `navyStroke`，问号用流畅曲线，底部加小波浪装饰。

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/icon/
git commit -m "feat: implement Help icon for all 5 skins"
```

---

### Task 3: 新增路由和指南页面

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/Screen.kt`
- Create: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportGuideScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add route to Screen sealed interface**

在 `Screen.kt` 的 `TaobaoImport` 后面添加：

```kotlin
data object TaobaoImportGuide : Screen {
    override val route = "taobao_import_guide"
}
```

**Step 2: Create TaobaoImportGuideScreen**

创建 `TaobaoImportGuideScreen.kt`，包含：

- `GradientTopAppBar`（compact = true），标题 "导入指南"，左侧返回按钮
- `LazyColumn` 内容区域
- **概述卡片**：简要说明功能用途
- **导出步骤卡片**：PC 端淘宝导出订单的步骤说明
  - 每个关键步骤后放截图占位框
- **注意事项卡片**：.xlsx 格式要求、时间范围建议、文件传输方式
- **截图占位框组件** `ScreenshotPlaceholder(hint: String)`：
  - 灰色背景 `Color(0xFFF0F0F0)`，圆角 8dp
  - 虚线边框 `Color(0xFFCCCCCC)`
  - 居中提示文字
  - 高度 200dp，宽度填满

**Step 3: Add composable to LolitaNavHost**

在 `LolitaNavHost.kt` 的 TaobaoImport composable 后面添加：

```kotlin
// Taobao Import Guide
composable(Screen.TaobaoImportGuide.route) {
    TaobaoImportGuideScreen(
        onBack = { navController.popBackStack() }
    )
}
```

需要添加 import: `import com.lolita.app.ui.screen.\`import\`.TaobaoImportGuideScreen`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/Screen.kt
git add app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportGuideScreen.kt
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: add TaobaoImportGuideScreen with route and navigation"
```

---

### Task 4: 在淘宝导入页面添加帮助按钮入口

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: Add `onNavigateToGuide` callback to TaobaoImportScreen**

修改 `TaobaoImportScreen` 函数签名，添加 `onNavigateToGuide: () -> Unit` 参数。

**Step 2: Add Help icon button to SELECT step (OrderSelectContent)**

在 `OrderSelectContent` 的 `GradientTopAppBar` 添加 `actions` 参数：

```kotlin
actions = {
    IconButton(onClick = onNavigateToGuide) {
        SkinIcon(IconKey.Help)
    }
}
```

需要将 `onNavigateToGuide` 传递到 `OrderSelectContent`。

**Step 3: Add Help icon button to PREPARE step (ImportPrepareContent)**

同样在 `ImportPrepareContent` 的 `GradientTopAppBar` 添加 `actions`，传递 `onNavigateToGuide`。

**Step 4: Add Help icon button to DETAIL step (ImportDetailContent)**

`ImportDetailContent` 在 `ImportDetailScreen.kt` 中。修改其函数签名添加 `onNavigateToGuide` 参数，在 `GradientTopAppBar` 添加 `actions`。

**Step 5: Wire up navigation in LolitaNavHost**

修改 `LolitaNavHost.kt` 中 TaobaoImport composable，添加导航回调：

```kotlin
TaobaoImportScreen(
    onBack = { navController.popBackStack() },
    onNavigateToDetail = { _ -> navController.popBackStack() },
    onNavigateToGuide = { navController.navigate(Screen.TaobaoImportGuide.route) }
)
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat: add Help button to Taobao import top bars"
```

---

### Task 5: 版本号更新 & Release 构建

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Bump version**

新功能，minor version bump。更新 `versionCode` +1，`versionName` 相应更新。

**Step 2: Release build**

```bash
./gradlew.bat assembleRelease
```

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for Taobao import guide feature"
```
