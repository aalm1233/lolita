# 皮肤动画差异化设计

日期：2026-02-21

## 背景

当前四个皮肤虽然有独立的 `SkinAnimationProvider`，但日常交互动画差异太微妙（按压缩放仅 0.92~0.97），用户难以感知。同时缺少持续性的皮肤特色动画，导致皮肤切换后除了颜色变化，"手感"几乎相同。

## 目标

1. 让每个皮肤在日常使用中有明显不同的"手感"和视觉反馈
2. 增加持续性皮肤特色动画，让皮肤个性一眼可见
3. 覆盖所有主要交互：点击、页面切换、列表滚动

## 设计决策

- 优先视觉效果，可接受一定性能开销（不明显卡顿即可）
- 全面实现所有动画类型，不做简化

---

## 第一部分：点击反馈系统

### 架构变更

扩展 `SkinClickable` 为完整的点击反馈系统：

```
SkinClickable (新)
├── 缩放动画 (差异化更大)
├── 涟漪效果 (替代 Material Ripple，每个皮肤独立实现)
└── 点击粒子 (点击时产生的装饰粒子)
```

### 四皮肤点击反馈设计

| 皮肤 | 缩放 | 涟漪效果 | 点击粒子 |
|------|------|----------|----------|
| Sweet | 0.88, 弹簧(阻尼0.5) | 粉色半透明圆形扩散，边缘柔和 | 3-5个小心形从点击处飘出 |
| Gothic | 0.95, 线性快速(100ms) | 暗紫色锐利边缘扩散，带轻微锯齿感 | 碎片/裂纹从点击处向外射出 |
| Chinese | 0.92, 缓动(easeOut) | 墨色晕染扩散，边缘不规则如水墨 | 墨点溅射效果 |
| Classic | 0.94, 平滑(300ms) | 金色光环扩散，边缘带光晕 | 金色光点缓慢消散 |

### 技术实现

- 涟漪效果用 `Canvas` + `Animatable` 绘制，替代 Material 的 `indication`
- 粒子系统复用现有 `SkinTransitionOverlay` 的粒子逻辑，简化为轻量版
- 通过 `SkinAnimationProvider` 提供配置

---

## 第二部分：页面切换/导航动画

### 架构变更

为 `NavHost` 配置皮肤特定的进入/退出动画：

```kotlin
NavHost(
    enterTransition = skinNavigationSpec.enterTransition,
    exitTransition = skinNavigationSpec.exitTransition,
    popEnterTransition = skinNavigationSpec.popEnterTransition,
    popExitTransition = skinNavigationSpec.popExitTransition
)
```

### 四皮肤页面切换设计

| 皮肤 | 进入动画 | 退出动画 | 返回动画 | 附加效果 |
|------|----------|----------|----------|----------|
| Sweet | 从中心放大(0.85→1) + 淡入 | 缩小(1→0.9) + 淡出 | 从左滑入 + 淡入 | 转场时有花瓣粒子飘落覆盖层 |
| Gothic | 从中间垂直撕裂展开 | 向中间合拢消失 | 从左侧阴影中浮现 | 转场时有暗色碎片飞散 |
| Chinese | 水墨从右侧泼洒覆盖后晕开 | 墨色从边缘收拢 | 卷轴从左展开 | 转场时有墨滴溅射 |
| Classic | 翻书效果(右→左翻页) | 反向翻页 | 反向翻页(左→右) | 转场时有金色光泽扫过 |

### Tab 切换动画

| 皮肤 | 滑动手感 | 指示器动画 |
|------|----------|------------|
| Sweet | 弹簧过冲，轻盈 | 指示器弹性移动，到达时有轻微弹跳 |
| Gothic | 阻尼较大，沉重 | 指示器瞬移，带阴影拖尾 |
| Chinese | 流畅如水，无过冲 | 指示器如墨迹流动，有晕染过渡 |
| Classic | 平滑精确，机械感 | 指示器匀速滑动，带金色光晕 |

### 技术实现

- 页面切换通过 `AnimatedContentTransitionScope` 实现
- 复杂效果（撕裂、水墨、翻书）需要自定义 `EnterTransition`/`ExitTransition`，用 Canvas 绘制遮罩层
- Tab 指示器需要自定义 `TabRowIndicator` 组件

---

## 第三部分：列表滚动/项目出现动画

### 架构变更

新增 `skinItemAppear` Modifier：

```kotlin
LazyColumn {
    items {
        modifier = Modifier
            .skinItemAppear(index)  // 皮肤特定的出现动画
            .animateItem()          // 保留用于重排序
    }
}
```

### 四皮肤列表项出现动画

| 皮肤 | 出现方式 | 时序 | 附加效果 |
|------|----------|------|----------|
| Sweet | 从下方漂浮上升 + 淡入 | 弹簧过冲，stagger 60ms | 出现时有微小的粉色光点闪烁 |
| Gothic | 从阴影中浮现(透明度+轻微放大) | 快速线性，stagger 30ms | 出现时边缘有阴影收缩效果 |
| Chinese | 从左侧挥入(水平位移+淡入) | 缓动曲线，stagger 80ms | 出现时有墨迹渐显的边缘效果 |
| Classic | 从下方优雅升起(垂直位移+淡入) | 平滑无过冲，stagger 50ms | 出现时有金色光晕从边缘扫过 |

### 滚动惯性/手感

通过自定义 `FlingBehavior` 实现：

| 皮肤 | 惯性特征 | 参数 |
|------|----------|------|
| Sweet | 轻盈弹性，容易过冲后回弹 | 高初速衰减，低摩擦 |
| Gothic | 沉重阻尼，快速停止 | 低初速衰减，高摩擦 |
| Chinese | 流水般顺滑，自然停止 | 中等衰减，中等摩擦 |
| Classic | 精确可控，匀速减速 | 线性衰减，中高摩擦 |

### 技术实现

- `skinItemAppear` 使用 `LaunchedEffect` + `Animatable` 控制动画
- 追踪每个 item 的"首次可见"状态，避免滚动回来时重复播放
- `FlingBehavior` 通过自定义 `DecayAnimationSpec` 实现

---

## 第四部分：持续性皮肤特色动画

### 架构变更

新增全局背景动画层：

```
LolitaApp
└── Box
    ├── SkinBackgroundAnimation()  // 底层：持续背景动画
    ├── Scaffold(...)              // 中层：主内容
    └── SkinForegroundEffects()    // 顶层：前景装饰(可选)
```

### 四皮肤持续动画设计

| 皮肤 | 背景动画 | 顶栏装饰动画 | 空状态动画 | 卡片装饰 |
|------|----------|--------------|------------|----------|
| Sweet | 淡粉色气泡缓慢上升 + 偶尔飘过的花瓣 | 花朵(✿)轻微摇曳，呼吸式缩放 | 大花瓣缓慢飘落，心形漂浮 | 悬停时边缘有粉色光晕脉动 |
| Gothic | 暗色烟雾/迷雾缓慢流动 | 十字架(✝)微微发光脉动 | 蝙蝠剪影偶尔飞过，暗色粒子漂浮 | 边缘阴影呼吸式明暗变化 |
| Chinese | 水墨云纹缓慢流动变形 | 祥云(☁)如水墨晕染般变化 | 墨滴缓慢滴落晕开，山水意境 | 边缘有淡墨晕染效果 |
| Classic | 金色光斑缓慢漂移，如阳光透过窗户 | 装饰符号(♠)有金属光泽流动 | 金色粒子优雅旋转，羽毛飘落 | 边框有金色光泽缓慢移动 |

### 动画参数

| 皮肤 | 背景粒子数 | 动画周期 | 透明度范围 |
|------|------------|----------|------------|
| Sweet | 8-12个气泡 + 3-5花瓣 | 8-15秒循环 | 0.1-0.3 |
| Gothic | 3-4团烟雾 | 12-20秒循环 | 0.08-0.2 |
| Chinese | 2-3层云纹 | 15-25秒循环 | 0.05-0.15 |
| Classic | 5-8个光斑 | 10-18秒循环 | 0.1-0.25 |

### 性能优化策略

- 背景动画使用单个 `Canvas` 绑定 `withFrameMillis`，避免多个独立动画
- 粒子数量固定，使用对象池复用
- 透明度保持较低，减少过度绘制影响
- 支持系统"减少动画"无障碍设置自动降级

---

## 第五部分：架构与接口设计

### SkinAnimationProvider 接口扩展

```kotlin
interface SkinAnimationProvider {
    // === 现有 ===
    val transitionSpec: SkinTransitionSpec
    val cardEnterSpec: SkinCardEnterSpec
    val tabSwitchSpec: SkinTabSwitchSpec
    val interactionSpec: SkinInteractionSpec

    // === 新增 ===
    val clickFeedbackSpec: SkinClickFeedbackSpec
    val navigationSpec: SkinNavigationSpec
    val listAnimationSpec: SkinListAnimationSpec
    val ambientAnimationSpec: SkinAmbientAnimationSpec

    // 工厂方法
    fun createRippleEffect(): SkinRippleEffect
    fun createClickParticles(): SkinClickParticles
    fun createBackgroundAnimation(): SkinBackgroundAnimation
    fun createItemAppearAnimation(): SkinItemAppearAnimation
}
```

### 新增数据类

```kotlin
// 点击反馈配置
data class SkinClickFeedbackSpec(
    val pressScale: Float,
    val scaleAnimationSpec: AnimationSpec<Float>,
    val rippleColor: Color,
    val rippleDuration: Int,
    val rippleStyle: RippleStyle,  // SOFT/SHARP/INK/GLOW
    val hasParticles: Boolean,
    val particleCount: Int
)

// 页面切换配置
data class SkinNavigationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val popEnterTransition: EnterTransition,
    val popExitTransition: ExitTransition,
    val hasOverlayEffect: Boolean,
    val overlayDuration: Int
)

// 列表动画配置
data class SkinListAnimationSpec(
    val appearDirection: AppearDirection,  // FROM_BOTTOM/FROM_LEFT/FADE_SCALE
    val appearOffset: Dp,
    val staggerDelay: Int,
    val animationSpec: AnimationSpec<Float>,
    val flingBehavior: FlingBehaviorSpec
)

// 持续动画配置
data class SkinAmbientAnimationSpec(
    val backgroundEnabled: Boolean,
    val backgroundParticleCount: Int,
    val backgroundCycleDuration: IntRange,
    val backgroundAlphaRange: ClosedFloatingPointRange<Float>,
    val topBarDecorationAnimated: Boolean,
    val cardHoverEffect: Boolean
)
```

### 文件结构

```
ui/theme/skin/
├── SkinAnimationProvider.kt          // 接口扩展
├── animation/
│   ├── SkinRippleEffect.kt           // 涟漪效果基类+4实现
│   ├── SkinClickParticles.kt         // 点击粒子基类+4实现
│   ├── SkinBackgroundAnimation.kt    // 背景动画基类+4实现
│   ├── SkinItemAppearAnimation.kt    // 列表项动画
│   ├── SkinNavigationTransitions.kt  // 页面切换动画
│   └── SkinFlingBehavior.kt          // 滚动惯性
├── DefaultSkinAnimationProvider.kt   // 更新
├── GothicSkinAnimationProvider.kt    // 更新
├── ChineseSkinAnimationProvider.kt   // 更新
└── ClassicSkinAnimationProvider.kt   // 更新
```

### 向后兼容

- 所有新增接口方法提供默认实现
- 新动画系统可逐步集成
- 保留现有 `SkinClickable` 作为简化版入口

---

## 实现优先级建议

1. **点击反馈系统** — 用户最频繁的交互，差异化收益最大
2. **持续背景动画** — 一眼可见的皮肤特色，不需要交互就能感知
3. **列表项出现动画** — 浏览列表是主要使用场景
4. **页面切换动画** — 实现复杂度最高，但视觉冲击力强
5. **滚动惯性/Tab指示器** — 锦上添花的细节打磨
