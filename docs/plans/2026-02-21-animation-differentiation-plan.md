# 皮肤动画差异化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让四个皮肤在日常交互中有明显不同的动画"手感"，并增加持续性背景动画

**Architecture:** 扩展现有 `SkinAnimationProvider` 接口，新增点击反馈、页面切换、列表动画、持续背景动画四大模块。每个模块通过数据类配置 + Canvas 绘制实现皮肤差异化。

**Tech Stack:** Jetpack Compose Animation, Canvas API, Animatable, AnimationSpec, custom EnterTransition/ExitTransition

**Base package:** `com.lolita.app.ui.theme.skin`

---

### Task 1: 扩展 SkinAnimationProvider 接口和数据类

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt`

**Step 1: 添加新的数据类和枚举到 SkinAnimationProvider.kt**

在现有数据类之后添加：

```kotlin
enum class RippleStyle { SOFT, SHARP, INK, GLOW }
enum class AppearDirection { FROM_BOTTOM, FROM_LEFT, FADE_SCALE }

data class SkinClickFeedbackSpec(
    val pressScale: Float,
    val scaleAnimationSpec: AnimationSpec<Float>,
    val rippleColor: Color,
    val rippleDuration: Int,
    val rippleStyle: RippleStyle,
    val hasParticles: Boolean,
    val particleCount: Int
)

data class SkinNavigationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
    val hasOverlayEffect: Boolean,
    val overlayDuration: Int
)

data class SkinListAnimationSpec(
    val appearDirection: AppearDirection,
    val appearOffsetPx: Float,
    val staggerDelayMs: Int,
    val animationSpec: AnimationSpec<Float>,
    val flingFrictionMultiplier: Float  // 1.0 = default, >1 = more friction
)

data class SkinAmbientAnimationSpec(
    val backgroundEnabled: Boolean,
    val backgroundParticleCount: Int,
    val backgroundCycleDurationRange: IntRange,
    val backgroundAlphaRange: ClosedFloatingPointRange<Float>,
    val topBarDecorationAnimated: Boolean,
    val cardGlowEffect: Boolean
)
```

**Step 2: 扩展 SkinAnimationProvider 接口**

在接口中添加带默认实现的新属性：

```kotlin
interface SkinAnimationProvider {
    // 现有
    val skinTransition: SkinTransitionSpec
    val tabSwitchAnimation: TabSwitchAnimationSpec
    val cardAnimation: CardAnimationSpec
    val interactionFeedback: InteractionFeedbackSpec

    // 新增 - 带默认实现以保持向后兼容
    val clickFeedback: SkinClickFeedbackSpec
        get() = SkinClickFeedbackSpec(
            pressScale = interactionFeedback.pressScale,
            scaleAnimationSpec = spring(),
            rippleColor = interactionFeedback.rippleColor,
            rippleDuration = 400,
            rippleStyle = RippleStyle.SOFT,
            hasParticles = false,
            particleCount = 0
        )

    val navigation: SkinNavigationSpec
        get() = SkinNavigationSpec(
            enterTransition = fadeIn() + slideInHorizontally { it / 4 },
            exitTransition = fadeOut() + slideOutHorizontally { -it / 4 },
            popEnterTransition = fadeIn() + slideInHorizontally { -it / 4 },
            popExitTransition = fadeOut() + slideOutHorizontally { it / 4 },
            hasOverlayEffect = false,
            overlayDuration = 0
        )

    val listAnimation: SkinListAnimationSpec
        get() = SkinListAnimationSpec(
            appearDirection = AppearDirection.FROM_BOTTOM,
            appearOffsetPx = 60f,
            staggerDelayMs = 50,
            animationSpec = tween(300),
            flingFrictionMultiplier = 1.0f
        )

    val ambientAnimation: SkinAmbientAnimationSpec
        get() = SkinAmbientAnimationSpec(
            backgroundEnabled = false,
            backgroundParticleCount = 0,
            backgroundCycleDurationRange = 10000..15000,
            backgroundAlphaRange = 0.1f..0.2f,
            topBarDecorationAnimated = false,
            cardGlowEffect = false
        )
}
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL (默认实现保证向后兼容)

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinAnimationProvider.kt
git commit -m "feat(skin): extend SkinAnimationProvider with click, navigation, list, ambient specs"
```

---

### Task 2: 实现皮肤涟漪效果 SkinRippleEffect

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinRippleEffect.kt`

**Step 1: 创建涟漪效果 Composable**

```kotlin
package com.lolita.app.ui.theme.skin.animation

// Canvas-based ripple that replaces Material ripple
// Each skin gets a different visual style via RippleStyle enum

@Composable
fun SkinRippleEffect(
    interactionSource: MutableInteractionSource,
    spec: SkinClickFeedbackSpec,
    modifier: Modifier = Modifier
) {
    // Collect press interactions
    // For each press: record Offset, start Animatable 0→1
    // Draw on Canvas based on RippleStyle:
    //   SOFT: smooth circular gradient, feathered edges (Sweet)
    //   SHARP: hard-edged circle with slight jagged distortion (Gothic)
    //   INK: irregular blob shape using Perlin-like offsets (Chinese)
    //   GLOW: golden ring with outer glow (Classic)
    // Alpha fades out as progress → 1
}
```

实现要点：
- 使用 `LaunchedEffect` 监听 `interactionSource.interactions` 中的 `PressInteraction.Press`
- 每次按压创建一个 `RippleInstance(center: Offset, progress: Animatable<Float>)`
- Canvas 中遍历活跃的 ripple instances 绘制
- SOFT: `drawCircle` with `RadialGradient`, large feather
- SHARP: `drawCircle` + 在边缘用 `drawLine` 画 6-8 条短锯齿线
- INK: `drawPath` 用 sin/cos 偏移生成不规则圆形
- GLOW: `drawCircle` 两层 — 内层实心金色，外层大半径低透明度光晕

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinRippleEffect.kt
git commit -m "feat(skin): add SkinRippleEffect with 4 style variants"
```

---

### Task 3: 实现点击粒子效果 SkinClickParticles

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinClickParticles.kt`

**Step 1: 创建点击粒子 Composable**

```kotlin
package com.lolita.app.ui.theme.skin.animation

// Lightweight particle system triggered on click
// Particles spawn at click position and animate outward

data class ClickParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var alpha: Float, var scale: Float,
    var rotation: Float
)

@Composable
fun SkinClickParticles(
    trigger: State<Offset?>,  // non-null = spawn particles at this position
    spec: SkinClickFeedbackSpec,
    skinType: SkinType,
    modifier: Modifier = Modifier
) {
    // When trigger changes to non-null:
    //   Create particleCount particles at trigger position
    //   Each particle gets random velocity outward
    //   Animate with withFrameMillis for ~600ms
    // Draw per skinType:
    //   Sweet: small hearts (reuse drawSweetHeartParticle from SweetAnimationProvider)
    //   Gothic: angular shards (small triangles)
    //   Chinese: ink dots (circles with varying size)
    //   Classic: golden sparkle dots
}
```

实现要点：
- 粒子数量固定（3-5个），用 `remember { mutableStateListOf<ClickParticle>() }` 管理
- `LaunchedEffect(trigger.value)` 启动动画循环
- 每帧更新位置、透明度衰减、缩放衰减
- Sweet 心形复用 `SweetAnimationProvider` 中的 `drawSweetHeartParticle`，提取为公共函数
- Gothic 三角形用 `Path` + `drawPath`
- Chinese 墨点用不同大小的 `drawCircle`
- Classic 金色光点用 `drawCircle` + 外层光晕

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinClickParticles.kt
git commit -m "feat(skin): add SkinClickParticles with per-skin particle shapes"
```

---

### Task 4: 升级 SkinClickable 集成涟漪和粒子

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinClickable.kt`

**Step 1: 重写 SkinClickable**

将现有的纯缩放动画升级为完整的点击反馈系统：

```kotlin
@Composable
fun Modifier.skinClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val skin = LolitaSkin.current
    val feedback = skin.animations.clickFeedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) feedback.pressScale else 1f,
        animationSpec = feedback.scaleAnimationSpec,
        label = "skinPressScale"
    )

    // Track click position for particles
    val clickPosition = remember { mutableStateOf<Offset?>(null) }

    return this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    clickPosition.value = offset
                    // ... handle press/release
                },
                onTap = { onClick() }
            )
        }
        .drawWithContent {
            drawContent()
            // Ripple and particles drawn as overlay
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
```

注意：需要用 `Box` 包裹来叠加 `SkinRippleEffect` 和 `SkinClickParticles`，或者改用 `drawWithContent` 在同一个 Modifier 链中绘制。推荐后者以避免额外的布局层级。

**Step 2: 验证编译并手动测试**

Run: `./gradlew.bat assembleDebug`
手动测试：切换四个皮肤，点击卡片/按钮，确认涟漪和粒子效果各不相同

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/component/SkinClickable.kt
git commit -m "feat(skin): upgrade SkinClickable with ripple effects and click particles"
```

---

### Task 5: 四个皮肤 Provider 实现 clickFeedback 配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`

**Step 1: Sweet — 弹性心形反馈**

```kotlin
override val clickFeedback = SkinClickFeedbackSpec(
    pressScale = 0.88f,
    scaleAnimationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
    rippleColor = Pink400,
    rippleDuration = 500,
    rippleStyle = RippleStyle.SOFT,
    hasParticles = true,
    particleCount = 4
)
```

**Step 2: Gothic — 锐利碎片反馈**

```kotlin
override val clickFeedback = SkinClickFeedbackSpec(
    pressScale = 0.95f,
    scaleAnimationSpec = tween(100, easing = LinearEasing),
    rippleColor = GothicPurple,
    rippleDuration = 350,
    rippleStyle = RippleStyle.SHARP,
    hasParticles = true,
    particleCount = 5
)
```

**Step 3: Chinese — 水墨晕染反馈**

```kotlin
override val clickFeedback = SkinClickFeedbackSpec(
    pressScale = 0.92f,
    scaleAnimationSpec = tween(250, easing = FastOutSlowInEasing),
    rippleColor = Color(0xFF2C2C2C),  // 墨色
    rippleDuration = 600,
    rippleStyle = RippleStyle.INK,
    hasParticles = true,
    particleCount = 3
)
```

**Step 4: Classic — 金色光环反馈**

```kotlin
override val clickFeedback = SkinClickFeedbackSpec(
    pressScale = 0.94f,
    scaleAnimationSpec = tween(300, easing = LinearOutSlowInEasing),
    rippleColor = Color(0xFFD4AF37),  // 金色
    rippleDuration = 450,
    rippleStyle = RippleStyle.GLOW,
    hasParticles = true,
    particleCount = 4
)
```

**Step 5: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt \
       app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt
git commit -m "feat(skin): add clickFeedback specs to all 4 skin animation providers"
```

---

### Task 6: 实现皮肤导航过渡动画

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationTransitions.kt`

**Step 1: 创建导航过渡工具**

为每个皮肤创建自定义 EnterTransition/ExitTransition。简单效果直接用 Compose 内置组合，复杂效果（撕裂、水墨、翻书）用自定义实现。

```kotlin
package com.lolita.app.ui.theme.skin.animation

// Sweet: scale + fade
fun sweetEnterTransition(): EnterTransition =
    fadeIn(tween(350)) + scaleIn(
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        initialScale = 0.85f
    )

fun sweetExitTransition(): ExitTransition =
    fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f)

fun sweetPopEnterTransition(): EnterTransition =
    fadeIn(tween(300)) + slideInHorizontally(
        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    ) { -it / 3 }

fun sweetPopExitTransition(): ExitTransition =
    fadeOut(tween(250)) + slideOutHorizontally(tween(300)) { it / 3 }

// Gothic: vertical split / shadow emerge
fun gothicEnterTransition(): EnterTransition =
    fadeIn(tween(400, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))) +
    expandVertically(tween(500), expandFrom = Alignment.CenterVertically)

fun gothicExitTransition(): ExitTransition =
    fadeOut(tween(300)) +
    shrinkVertically(tween(400), shrinkTowards = Alignment.CenterVertically)

fun gothicPopEnterTransition(): EnterTransition =
    fadeIn(tween(500, easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f))) +
    slideInHorizontally(tween(500)) { -it / 2 }

fun gothicPopExitTransition(): ExitTransition =
    fadeOut(tween(400)) + slideOutHorizontally(tween(400)) { it / 2 }

// Chinese: horizontal slide with ink feel
fun chineseEnterTransition(): EnterTransition =
    fadeIn(tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) +
    slideInHorizontally(tween(450, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) { it / 2 }

fun chineseExitTransition(): ExitTransition =
    fadeOut(tween(350)) + slideOutHorizontally(tween(400)) { -it / 3 }

fun chinesePopEnterTransition(): EnterTransition =
    fadeIn(tween(400)) + slideInHorizontally(tween(450)) { -it / 2 }

fun chinesePopExitTransition(): ExitTransition =
    fadeOut(tween(350)) + slideOutHorizontally(tween(400)) { it / 3 }

// Classic: page-turn feel via horizontal slide + slight scale
fun classicEnterTransition(): EnterTransition =
    fadeIn(tween(380, easing = LinearOutSlowInEasing)) +
    slideInHorizontally(tween(400, easing = LinearOutSlowInEasing)) { it }

fun classicExitTransition(): ExitTransition =
    fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { -it }

fun classicPopEnterTransition(): EnterTransition =
    fadeIn(tween(380)) + slideInHorizontally(tween(400)) { -it }

fun classicPopExitTransition(): ExitTransition =
    fadeOut(tween(300)) + slideOutHorizontally(tween(350)) { it }
```

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationTransitions.kt
git commit -m "feat(skin): add per-skin navigation transition functions"
```

---

### Task 7: 四个皮肤 Provider 实现 navigation 配置 + 集成到 NavHost

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: 在每个 Provider 中 override navigation**

Sweet:
```kotlin
override val navigation = SkinNavigationSpec(
    enterTransition = sweetEnterTransition(),
    exitTransition = sweetExitTransition(),
    popEnterTransition = sweetPopEnterTransition(),
    popExitTransition = sweetPopExitTransition(),
    hasOverlayEffect = true,
    overlayDuration = 350
)
```

Gothic/Chinese/Classic 同理，调用 Task 6 中对应的函数。

**Step 2: 修改 LolitaNavHost 使用皮肤导航动画**

将 `LolitaNavHost.kt:147-154` 中硬编码的过渡动画替换为皮肤感知版本：

```kotlin
val skin = LolitaSkin.current
val navSpec = skin.animations.navigation

NavHost(
    navController = navController,
    startDestination = Screen.ItemList.route,
    modifier = Modifier.padding(paddingValues),
    enterTransition = { navSpec.enterTransition },
    exitTransition = { navSpec.exitTransition },
    popEnterTransition = { navSpec.popEnterTransition },
    popExitTransition = { navSpec.popExitTransition }
) {
```

注意：`skin` 已在 `LolitaNavHost` 第98行获取，直接使用即可。

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/*.kt \
       app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat(skin): integrate per-skin navigation transitions into NavHost"
```

---

### Task 8: 实现导航过渡覆盖层效果

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationOverlay.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: 创建导航覆盖层 Composable**

在页面切换时显示皮肤特色的粒子/效果覆盖层：

```kotlin
@Composable
fun SkinNavigationOverlay(
    isTransitioning: Boolean,
    skinType: SkinType,
    modifier: Modifier = Modifier
) {
    // 当 isTransitioning 为 true 时播放覆盖层动画
    // Sweet: 花瓣从上方飘落
    // Gothic: 暗色碎片从中心飞散
    // Chinese: 墨滴从边缘溅射
    // Classic: 金色光泽从左向右扫过
    // 动画持续时间由 ambientAnimation spec 控制
}
```

实现要点：
- 监听 `navController.currentBackStackEntryAsState()` 变化来检测页面切换
- 使用 `Animatable(0f)` 控制覆盖层进度
- Canvas 绘制，每个皮肤 5-8 个粒子
- 覆盖层在 `Scaffold` 内容之上，用 `Box` 叠加

**Step 2: 在 LolitaNavHost 中集成覆盖层**

在 `Scaffold` 的 content 区域用 `Box` 包裹 `NavHost` 和 `SkinNavigationOverlay`：

```kotlin
} { paddingValues ->
    Box {
        NavHost(...) { ... }
        SkinNavigationOverlay(
            isTransitioning = isNavigating,
            skinType = skin.skinType
        )
    }
}
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinNavigationOverlay.kt \
       app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat(skin): add navigation transition overlay effects per skin"
```

---

### Task 9: 实现列表项出现动画 Modifier

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinItemAppear.kt`

**Step 1: 创建 skinItemAppear Modifier**

```kotlin
package com.lolita.app.ui.theme.skin.animation

@Composable
fun Modifier.skinItemAppear(index: Int): Modifier {
    val skin = LolitaSkin.current
    val spec = skin.animations.listAnimation
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * spec.staggerDelayMs).toLong())
        animProgress.animateTo(1f, animationSpec = spec.animationSpec)
    }

    val offsetX = when (spec.appearDirection) {
        AppearDirection.FROM_LEFT -> spec.appearOffsetPx * (1f - animProgress.value)
        else -> 0f
    }
    val offsetY = when (spec.appearDirection) {
        AppearDirection.FROM_BOTTOM -> spec.appearOffsetPx * (1f - animProgress.value)
        else -> 0f
    }
    val scale = when (spec.appearDirection) {
        AppearDirection.FADE_SCALE -> 0.9f + 0.1f * animProgress.value
        else -> 1f
    }
    val alpha = animProgress.value

    return this
        .graphicsLayer {
            translationX = offsetX
            translationY = offsetY
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}
```

实现要点：
- `index` 用于计算 stagger delay，只在首次出现时播放
- 使用 `graphicsLayer` 而非 `offset` + `scale` 以获得更好的性能（不触发重新布局）
- `LaunchedEffect(Unit)` 确保只播放一次

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinItemAppear.kt
git commit -m "feat(skin): add skinItemAppear modifier for staggered list item animations"
```

---

### Task 10: 四个皮肤 Provider 实现 listAnimation 配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`

**Step 1: Sweet — 弹簧漂浮上升**

```kotlin
override val listAnimation = SkinListAnimationSpec(
    appearDirection = AppearDirection.FROM_BOTTOM,
    appearOffsetPx = 80f,
    staggerDelayMs = 60,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),
    flingFrictionMultiplier = 0.7f  // 低摩擦，轻盈
)
```

**Step 2: Gothic — 快速阴影浮现**

```kotlin
override val listAnimation = SkinListAnimationSpec(
    appearDirection = AppearDirection.FADE_SCALE,
    appearOffsetPx = 40f,
    staggerDelayMs = 30,
    animationSpec = tween(250, easing = LinearOutSlowInEasing),
    flingFrictionMultiplier = 1.5f  // 高摩擦，沉重
)
```

**Step 3: Chinese — 水墨挥入**

```kotlin
override val listAnimation = SkinListAnimationSpec(
    appearDirection = AppearDirection.FROM_LEFT,
    appearOffsetPx = 100f,
    staggerDelayMs = 80,
    animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
    flingFrictionMultiplier = 1.0f  // 中等
)
```

**Step 4: Classic — 优雅升起**

```kotlin
override val listAnimation = SkinListAnimationSpec(
    appearDirection = AppearDirection.FROM_BOTTOM,
    appearOffsetPx = 60f,
    staggerDelayMs = 50,
    animationSpec = tween(350, easing = LinearOutSlowInEasing),
    flingFrictionMultiplier = 1.3f  // 中高摩擦，精确
)
```

**Step 5: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/*.kt
git commit -m "feat(skin): add listAnimation specs to all 4 skin providers"
```

---

### Task 11: 集成 skinItemAppear 到主要列表屏幕

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/coordinate/CoordinateListScreen.kt` (如果存在)

**Step 1: 在 ItemListScreen 的 LazyColumn items 中添加 skinItemAppear**

找到 LazyColumn 中的 items/itemsIndexed 调用，在每个 item 的根 Modifier 上添加：

```kotlin
items(filteredItems) { item ->
    // 需要改为 itemsIndexed 以获取 index
}
// 改为：
itemsIndexed(filteredItems) { index, item ->
    ItemCard(
        item = item,
        modifier = Modifier.skinItemAppear(index),
        ...
    )
}
```

**Step 2: 对 WishlistScreen 和 OutfitLogListScreen 做同样的修改**

每个屏幕的 LazyColumn 都需要：
1. 改用 `itemsIndexed`（如果还没有的话）
2. 在 item 根 Composable 上添加 `.skinItemAppear(index)`

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt
git commit -m "feat(skin): integrate skinItemAppear into main list screens"
```

---

### Task 12: 实现自定义滚动惯性 SkinFlingBehavior

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinFlingBehavior.kt`

**Step 1: 创建皮肤感知的 FlingBehavior**

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lolita.app.ui.theme.LolitaSkin

@Composable
fun rememberSkinFlingBehavior(): FlingBehavior {
    val skin = LolitaSkin.current
    val friction = skin.animations.listAnimation.flingFrictionMultiplier
    val defaultFling = ScrollableDefaults.flingBehavior()

    return if (friction == 1.0f) {
        defaultFling
    } else {
        remember(friction) {
            SkinFlingBehavior(frictionMultiplier = friction)
        }
    }
}

// 自定义 FlingBehavior 实现
// frictionMultiplier < 1: 低摩擦，滑得更远 (Sweet)
// frictionMultiplier > 1: 高摩擦，快速停止 (Gothic)
class SkinFlingBehavior(
    private val frictionMultiplier: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val adjustedVelocity = initialVelocity / frictionMultiplier
        // 使用 exponentialDecay 配合调整后的速度
        // 返回剩余速度
    }
}
```

实现要点：
- 使用 `AnimationState` + `animateDecay` + `exponentialDecay(frictionMultiplier)` 实现
- Sweet (0.7f): 滑动距离更远，有轻盈感
- Gothic (1.5f): 快速停止，有沉重感
- Chinese (1.0f): 默认行为
- Classic (1.3f): 略快停止，精确感

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinFlingBehavior.kt
git commit -m "feat(skin): add SkinFlingBehavior with per-skin friction"
```

---

### Task 13: 集成 SkinFlingBehavior 到列表屏幕

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt`

**Step 1: 在每个屏幕的 LazyColumn 中添加 flingBehavior**

```kotlin
val flingBehavior = rememberSkinFlingBehavior()

LazyColumn(
    flingBehavior = flingBehavior,
    ...
) {
```

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/item/WishlistScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogListScreen.kt
git commit -m "feat(skin): integrate SkinFlingBehavior into list screens"
```

---

### Task 14: 实现持续背景动画基础框架

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt`

**Step 1: 创建背景动画 Composable 框架**

```kotlin
package com.lolita.app.ui.theme.skin.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.SkinType

// 粒子基类
abstract class AmbientParticle {
    abstract var x: Float
    abstract var y: Float
    abstract var alpha: Float
    abstract fun update(deltaMs: Long, width: Float, height: Float)
    abstract fun DrawScope.draw()
    abstract fun reset(width: Float, height: Float)
}

@Composable
fun SkinBackgroundAnimation(
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.ambientAnimation

    if (!spec.backgroundEnabled) return

    // 检测系统"减少动画"设置
    val reduceMotion = LocalReduceMotion.current

    if (reduceMotion) return

    val particles = remember(skin.skinType) {
        createParticles(skin.skinType, spec.backgroundParticleCount)
    }

    var lastFrameTime by remember { mutableLongStateOf(0L) }

    Canvas(modifier.fillMaxSize()) {
        val currentTime = System.nanoTime() / 1_000_000L
        val delta = if (lastFrameTime == 0L) 16L else (currentTime - lastFrameTime).coerceAtMost(32L)
        lastFrameTime = currentTime

        particles.forEach { particle ->
            particle.update(delta, size.width, size.height)
            with(particle) { draw() }
        }
    }

    // 使用 LaunchedEffect + withFrameMillis 驱动重绘
    LaunchedEffect(skin.skinType) {
        while (true) {
            withFrameMillis { }  // 触发 recomposition
        }
    }
}

private fun createParticles(skinType: SkinType, count: Int): List<AmbientParticle> {
    return when (skinType) {
        SkinType.SWEET -> List(count) { SweetBubbleParticle() }
        SkinType.GOTHIC -> List(count) { GothicSmokeParticle() }
        SkinType.CHINESE -> List(count) { ChineseCloudParticle() }
        SkinType.CLASSIC -> List(count) { ClassicSparkleParticle() }
    }
}
```

实现要点：
- 单个 Canvas 绘制所有粒子，避免多个独立动画的开销
- `withFrameMillis` 驱动帧循环，Canvas 每帧重绘
- delta time 限制在 32ms 以内，防止后台恢复时粒子跳跃
- 系统减少动画设置时直接 return，不渲染

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinBackgroundAnimation.kt
git commit -m "feat(skin): add SkinBackgroundAnimation framework with particle system"
```

---

### Task 15: 实现 Sweet 背景粒子 — 气泡 + 花瓣

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetParticles.kt`

**Step 1: 实现 SweetBubbleParticle**

```kotlin
package com.lolita.app.ui.theme.skin.animation.particles

class SweetBubbleParticle : AmbientParticle() {
    override var x = 0f
    override var y = 0f
    override var alpha = 0f
    private var radius = 0f
    private var speed = 0f
    private var wobblePhase = 0f
    private var wobbleSpeed = 0f

    override fun reset(width: Float, height: Float) {
        x = Random.nextFloat() * width
        y = height + Random.nextFloat() * 100f  // 从底部开始
        radius = 4f + Random.nextFloat() * 8f
        speed = 0.3f + Random.nextFloat() * 0.5f  // 缓慢上升
        alpha = 0.1f + Random.nextFloat() * 0.15f
        wobblePhase = Random.nextFloat() * 2f * PI.toFloat()
        wobbleSpeed = 0.001f + Random.nextFloat() * 0.002f
    }

    override fun update(deltaMs: Long, width: Float, height: Float) {
        y -= speed * deltaMs  // 上升
        wobblePhase += wobbleSpeed * deltaMs
        x += sin(wobblePhase) * 0.3f  // 左右摇摆

        if (y < -radius * 2) reset(width, height)  // 超出顶部则重置
    }

    override fun DrawScope.draw() {
        drawCircle(
            Pink400.copy(alpha = alpha),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

class SweetPetalParticle : AmbientParticle() {
    // 花瓣：用 Path 画椭圆形花瓣，带旋转
    // 从顶部缓慢飘落，左右摇摆幅度更大
    // 旋转角度随时间变化
    override fun reset(width: Float, height: Float) { ... }
    override fun update(deltaMs: Long, width: Float, height: Float) { ... }
    override fun DrawScope.draw() {
        // drawPath 画花瓣形状，带 rotate transform
    }
}
```

实现要点：
- 气泡：8-12个，从底部缓慢上升，带左右摇摆
- 花瓣：3-5个，从顶部缓慢飘落，带旋转
- 花瓣形状用 `Path` 画椭圆 + `cubicTo` 做尖端
- 颜色使用 `Pink400` 的低透明度版本

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/SweetParticles.kt
git commit -m "feat(skin): implement Sweet bubble and petal ambient particles"
```

---

### Task 16: 实现 Gothic 背景粒子 — 烟雾

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicParticles.kt`

**Step 1: 实现 GothicSmokeParticle**

```kotlin
class GothicSmokeParticle : AmbientParticle() {
    // 烟雾：3-4团，大半径(60-120dp)，极低透明度
    // 使用 RadialGradient 从中心到边缘渐变
    // 缓慢水平漂移 + 呼吸式透明度变化
    // 周期 12-20秒

    private var baseAlpha = 0f
    private var breathPhase = 0f
    private var breathSpeed = 0f
    private var driftSpeed = 0f
    private var radius = 0f

    override fun update(deltaMs: Long, width: Float, height: Float) {
        x += driftSpeed * deltaMs
        breathPhase += breathSpeed * deltaMs
        alpha = baseAlpha + sin(breathPhase) * baseAlpha * 0.5f

        // 漂出屏幕后从另一侧进入
        if (x > width + radius) x = -radius
        if (x < -radius) x = width + radius
    }

    override fun DrawScope.draw() {
        drawCircle(
            Brush.radialGradient(
                listOf(
                    Color.Black.copy(alpha = alpha),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/GothicParticles.kt
git commit -m "feat(skin): implement Gothic smoke ambient particles"
```

---

### Task 17: 实现 Chinese 背景粒子 — 水墨云纹

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChineseParticles.kt`

**Step 1: 实现 ChineseCloudParticle**

```kotlin
class ChineseCloudParticle : AmbientParticle() {
    // 云纹：2-3层，使用多个重叠的椭圆模拟水墨云
    // 形状用 Path + cubicTo 画不规则云形
    // 极缓慢水平漂移，透明度在 0.05-0.15 之间呼吸
    // 周期 15-25秒
    // 颜色：深灰/墨色 Color(0xFF2C2C2C)

    private var cloudPath = Path()
    private var scaleX = 1f
    private var scaleY = 1f

    override fun reset(width: Float, height: Float) {
        // 生成随机云形 Path
        // 位置在屏幕中上部
    }

    override fun DrawScope.draw() {
        withTransform({
            translate(x, y)
            scale(scaleX, scaleY)
        }) {
            drawPath(cloudPath, Color(0xFF2C2C2C).copy(alpha = alpha))
        }
    }
}
```

实现要点：
- 云形用 3-4 个重叠的 `cubicTo` 弧线构成
- 缓慢变形效果通过 scaleX/scaleY 的微小呼吸变化实现
- 透明度极低(0.05-0.15)，营造若隐若现的水墨感

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ChineseParticles.kt
git commit -m "feat(skin): implement Chinese ink cloud ambient particles"
```

---

### Task 18: 实现 Classic 背景粒子 — 金色光斑

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicParticles.kt`

**Step 1: 实现 ClassicSparkleParticle**

```kotlin
class ClassicSparkleParticle : AmbientParticle() {
    // 光斑：5-8个，模拟阳光透过窗户的效果
    // 大小不一(10-40dp)，使用 RadialGradient 金色→透明
    // 缓慢漂移，透明度呼吸变化
    // 颜色：金色 Color(0xFFD4AF37)

    private var radius = 0f
    private var breathPhase = 0f

    override fun DrawScope.draw() {
        drawCircle(
            Brush.radialGradient(
                listOf(
                    Color(0xFFD4AF37).copy(alpha = alpha),
                    Color(0xFFD4AF37).copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/particles/ClassicParticles.kt
git commit -m "feat(skin): implement Classic golden sparkle ambient particles"
```

---

### Task 19: 四个皮肤 Provider 实现 ambientAnimation 配置

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SweetAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/GothicAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ChineseAnimationProvider.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/ClassicAnimationProvider.kt`

**Step 1: Sweet**

```kotlin
override val ambientAnimation = SkinAmbientAnimationSpec(
    backgroundEnabled = true,
    backgroundParticleCount = 15,  // 10 bubbles + 5 petals
    backgroundCycleDurationRange = 8000..15000,
    backgroundAlphaRange = 0.1f..0.3f,
    topBarDecorationAnimated = true,
    cardGlowEffect = true
)
```

**Step 2: Gothic**

```kotlin
override val ambientAnimation = SkinAmbientAnimationSpec(
    backgroundEnabled = true,
    backgroundParticleCount = 4,
    backgroundCycleDurationRange = 12000..20000,
    backgroundAlphaRange = 0.08f..0.2f,
    topBarDecorationAnimated = true,
    cardGlowEffect = true
)
```

**Step 3: Chinese**

```kotlin
override val ambientAnimation = SkinAmbientAnimationSpec(
    backgroundEnabled = true,
    backgroundParticleCount = 3,
    backgroundCycleDurationRange = 15000..25000,
    backgroundAlphaRange = 0.05f..0.15f,
    topBarDecorationAnimated = true,
    cardGlowEffect = true
)
```

**Step 4: Classic**

```kotlin
override val ambientAnimation = SkinAmbientAnimationSpec(
    backgroundEnabled = true,
    backgroundParticleCount = 7,
    backgroundCycleDurationRange = 10000..18000,
    backgroundAlphaRange = 0.1f..0.25f,
    topBarDecorationAnimated = true,
    cardGlowEffect = true
)
```

**Step 5: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/*.kt
git commit -m "feat(skin): add ambientAnimation specs to all 4 skin providers"
```

---

### Task 20: 集成 SkinBackgroundAnimation 到 LolitaNavHost

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt`

**Step 1: 在 Scaffold content 中添加背景动画层**

在 `LolitaNavHost.kt` 的 Scaffold content lambda 中，用 `Box` 包裹 NavHost，在底层添加 `SkinBackgroundAnimation`：

```kotlin
} { paddingValues ->
    Box {
        // 底层：持续背景动画
        SkinBackgroundAnimation(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )

        // 中层：主内容
        NavHost(
            navController = navController,
            startDestination = Screen.ItemList.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { navSpec.enterTransition },
            exitTransition = { navSpec.exitTransition },
            popEnterTransition = { navSpec.popEnterTransition },
            popExitTransition = { navSpec.popExitTransition }
        ) {
            // ... existing composable routes
        }

        // 顶层：导航过渡覆盖层 (Task 8 已添加)
        SkinNavigationOverlay(...)
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/navigation/LolitaNavHost.kt
git commit -m "feat(skin): integrate SkinBackgroundAnimation into LolitaNavHost"
```

---

### Task 21: 实现顶栏装饰动画

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/components/GradientTopAppBar.kt` (或实际路径)

**Step 1: 找到 GradientTopAppBar 中的装饰符号绘制位置**

当前装饰符号（✿ ✝ ☁ ♠）是静态绘制的。需要在 `ambientAnimation.topBarDecorationAnimated == true` 时添加动画。

**Step 2: 为装饰符号添加动画**

```kotlin
// 在 GradientTopAppBar 中
val skin = LolitaSkin.current
val animateDecorations = skin.animations.ambientAnimation.topBarDecorationAnimated

if (animateDecorations) {
    val infiniteTransition = rememberInfiniteTransition(label = "topBarDeco")

    // 呼吸式缩放
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // 轻微旋转摇曳 (Sweet 花朵)
    val wobbleRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobbleRotation"
    )

    // 发光脉动 (Gothic 十字架)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
}
```

根据皮肤类型选择不同的动画效果：
- Sweet: `breathScale` + `wobbleRotation` 应用到花朵符号
- Gothic: `glowAlpha` 应用到十字架符号
- Chinese: `breathScale` 应用到祥云（模拟晕染变化）
- Classic: 金属光泽效果（用 `animateFloat` 控制渐变偏移）

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/components/GradientTopAppBar.kt
git commit -m "feat(skin): add animated decorations to GradientTopAppBar per skin"
```

---

### Task 22: 实现卡片装饰效果

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt`
- Modify: `app/src/main/java/com/lolita/app/ui/components/LolitaCard.kt` (或实际路径)

**Step 1: 创建卡片光晕 Modifier**

```kotlin
package com.lolita.app.ui.theme.skin.animation

@Composable
fun Modifier.skinCardGlow(): Modifier {
    val skin = LolitaSkin.current
    if (!skin.animations.ambientAnimation.cardGlowEffect) return this

    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cardGlowProgress"
    )

    return this.drawWithContent {
        drawContent()
        // 根据皮肤类型绘制不同的边缘效果
        when (skin.skinType) {
            SkinType.SWEET -> {
                // 粉色光晕脉动：在边缘画半透明粉色描边，alpha 随 glowProgress 变化
            }
            SkinType.GOTHIC -> {
                // 阴影呼吸：在底部和右侧画加深的阴影，alpha 随 glowProgress 变化
            }
            SkinType.CHINESE -> {
                // 淡墨晕染：在边缘画不规则的墨色渐变
            }
            SkinType.CLASSIC -> {
                // 金色光泽移动：画一条金色渐变线从左到右扫过边框
                val sweepX = size.width * glowProgress
                drawLine(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFFD4AF37).copy(alpha = 0.3f), Color.Transparent),
                        startX = sweepX - 40f,
                        endX = sweepX + 40f
                    ),
                    start = Offset(sweepX, 0f),
                    end = Offset(sweepX, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}
```

**Step 2: 在 LolitaCard 中集成**

```kotlin
// LolitaCard.kt
@Composable
fun LolitaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.skinCardGlow(),
        shape = skin.cardShape,
        ...
    ) {
        content()
    }
}
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinCardGlow.kt \
       app/src/main/java/com/lolita/app/ui/components/LolitaCard.kt
git commit -m "feat(skin): add per-skin card glow/decoration effects"
```

---

### Task 23: 实现自定义 Tab 指示器

**Files:**
- Create: `app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinTabIndicator.kt`
- Modify: 使用 TabRow 的屏幕（ItemListScreen, StatsPageScreen 等）

**Step 1: 创建皮肤感知的 Tab 指示器**

```kotlin
package com.lolita.app.ui.theme.skin.animation

@Composable
fun SkinTabIndicator(
    tabPositions: List<TabPosition>,
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    val skin = LolitaSkin.current
    val spec = skin.animations.tabSwitchAnimation

    // 动画化指示器位置
    val indicatorOffset by animateDpAsState(
        targetValue = tabPositions[selectedTabIndex].left,
        animationSpec = spec.indicatorAnimation,  // 已有的皮肤特定动画规格
        label = "tabIndicatorOffset"
    )
    val indicatorWidth = tabPositions[selectedTabIndex].width

    // 根据皮肤绘制不同风格的指示器
    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        when (skin.skinType) {
            SkinType.SWEET -> {
                // 圆角粉色条，到达时有弹跳效果（由 spring spec 自动实现）
            }
            SkinType.GOTHIC -> {
                // 锐利的紫色条 + 阴影拖尾效果
                // 拖尾：在当前位置之前画一个渐变的半透明条
            }
            SkinType.CHINESE -> {
                // 墨迹流动效果：不规则边缘的指示条
                // 用 Path + cubicTo 画波浪形边缘
            }
            SkinType.CLASSIC -> {
                // 金色条 + 外层光晕
                // 两层：底层宽光晕，上层窄实线
            }
        }
    }
}
```

**Step 2: 在 TabRow 中替换默认指示器**

找到 ItemListScreen 和 StatsPageScreen 中的 `TabRow`，替换 indicator 参数：

```kotlin
TabRow(
    selectedTabIndex = selectedTab,
    indicator = { tabPositions ->
        SkinTabIndicator(
            tabPositions = tabPositions,
            selectedTabIndex = selectedTab
        )
    }
) { ... }
```

**Step 3: 验证编译**

Run: `./gradlew.bat assembleDebug`

**Step 4: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/theme/skin/animation/SkinTabIndicator.kt \
       app/src/main/java/com/lolita/app/ui/screen/item/ItemListScreen.kt \
       app/src/main/java/com/lolita/app/ui/screen/stats/StatsPageScreen.kt
git commit -m "feat(skin): add custom SkinTabIndicator with per-skin visual styles"
```

---

### Task 24: 最终集成验证和调优

**Files:**
- 所有已修改的文件

**Step 1: 完整编译**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 手动测试清单**

逐一切换四个皮肤，验证以下场景：

- [ ] 点击卡片：涟漪效果和粒子效果与皮肤匹配
- [ ] 点击按钮：缩放手感与皮肤匹配
- [ ] 页面导航（进入详情页）：过渡动画与皮肤匹配
- [ ] 页面返回：返回动画与皮肤匹配
- [ ] Tab 切换：指示器动画与皮肤匹配
- [ ] 列表滚动：惯性手感与皮肤匹配
- [ ] 列表首次加载：项目出现动画与皮肤匹配
- [ ] 背景动画：持续粒子效果可见且不干扰内容
- [ ] 顶栏装饰：装饰符号有动画效果
- [ ] 卡片装饰：边缘有皮肤特色效果
- [ ] 性能：滚动流畅，无明显掉帧

**Step 3: 调优参数**

根据测试结果调整：
- 粒子透明度（太明显则降低，看不见则提高）
- 动画时长（太快则延长，太慢则缩短）
- 缩放幅度（太夸张则收敛，无感则加大）
- 背景粒子数量（太密则减少，太稀则增加）

**Step 4: Commit**

```bash
git add -A
git commit -m "feat(skin): complete animation differentiation system - all 4 skins"
```
