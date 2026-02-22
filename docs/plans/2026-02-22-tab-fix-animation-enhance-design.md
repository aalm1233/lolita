# Tab指示器修复 & 动画效果增强 设计文档

日期: 2026-02-22

## 概述

三个任务：
1. 修复 SkinTabIndicator 遮挡Tab文字的bug
2. 增强每个皮肤的卡片动画效果
3. 增强背景粒子效果（更多数量、更多种类、更高可见度）

## 1. SkinTabIndicator 修复

### 问题根因

Material3 的 `TabRow` 使用 `Constraints.fixed(tabRowWidth, tabRowHeight)` 测量 indicator composable。
`SkinTabIndicator` 的 `Canvas` 使用 `height(3.dp)`，但被固定约束覆盖，实际高度为整个 TabRow 高度（~48dp）。
绘制代码使用 `size.height` 以为是 3dp，实际是 48dp，导致指示器填满整个 Tab 区域遮挡文字。

中华风影响较小：其波浪路径只覆盖底部区域，且颜色半透明。

### 修复方案

在 Canvas 绘制时使用固定的指示器高度（3dp 转 px），将所有绘制定位到 Canvas 底部：

```kotlin
val indicatorH = 3.dp.toPx()
val topY = size.height - indicatorH
```

所有四个皮肤的绘制代码使用 `indicatorH` 和 `topY` 替代 `h` 和 `0f`。

### 涉及文件

- `SkinTabIndicator.kt`

## 2. 卡片动画增强

### 当前问题

- Classic 的金色扫光线是唯一明显可见的卡片动画
- 其他三个皮肤只有微弱的边框脉冲（alpha 0.1~0.15）

### 新设计

| 皮肤 | 效果 | 描述 |
|------|------|------|
| DEFAULT | 流动爱心光点 | 粉色光点沿卡片边缘顺时针流动，光点处绘制小爱心+光晕，alpha 0.3~0.5 |
| GOTHIC | 暗影脉冲 | 四角暗紫色阴影向内扩散，呼吸式脉动，边缘血红微光闪烁，alpha 0.2~0.35 |
| CHINESE | 水墨晕染 | 右下角向左上扩散的墨色径向渐变，呼吸式扩散，alpha 0.15~0.3 |
| CLASSIC | 金色扫光 | 保持不变 |

### 涉及文件

- `SkinCardGlow.kt` — 重写 DEFAULT/GOTHIC/CHINESE 的绘制逻辑

## 3. 背景粒子增强

### 参数调整

| 皮肤 | 当前数量 | 新数量 | 当前Alpha | 新Alpha |
|------|---------|--------|-----------|---------|
| DEFAULT | 15 | 20 | 0.1~0.3 | 0.15~0.4 |
| GOTHIC | 4 | 12 | 0.08~0.2 | 0.12~0.3 |
| CHINESE | 3 | 10 | 0.05~0.15 | 0.1~0.25 |
| CLASSIC | 7 | 14 | 0.1~0.25 | 0.15~0.35 |

### 新增粒子类型

**DEFAULT — SweetStarParticle**
- 五角星形状，随机闪烁
- 大小 8~15px，alpha 脉冲变化
- 缓慢漂浮
- 混合比例：气泡 40% + 花瓣 30% + 星星 30%

**GOTHIC — GothicEmberParticle**
- 暗红色余烬/火花，从底部缓慢上升
- 大小 3~8px，带渐变拖尾
- 上升过程逐渐变暗消失
- 混合比例：烟雾 50% + 余烬 50%

**CHINESE — ChinesePlumBlossomParticle**
- 梅花花瓣形状（5瓣），缓慢飘落
- 大小 10~18px，带旋转和左右摇摆
- 混合比例：云纹 40% + 梅花 60%

**CLASSIC — ClassicDiamondParticle**
- 菱形/钻石形状，金色渐变
- 大小 6~12px，闪烁式脉冲（快速亮灭）
- 缓慢漂浮
- 混合比例：星光 50% + 钻石 50%

### 涉及文件

- `SweetAnimationProvider.kt` — 更新 backgroundParticleCount 和 backgroundAlphaRange
- `GothicAnimationProvider.kt` — 同上
- `ChineseAnimationProvider.kt` — 同上
- `ClassicAnimationProvider.kt` — 同上
- `SkinBackgroundAnimation.kt` — 更新 createParticles 混合新粒子类型
- 新增 `particles/SweetStarParticle.kt`
- 新增 `particles/GothicEmberParticle.kt`
- 新增 `particles/ChinesePlumBlossomParticle.kt`
- 新增 `particles/ClassicDiamondParticle.kt`

## 完整文件变更清单

1. `SkinTabIndicator.kt` — 修复绘制坐标
2. `SkinCardGlow.kt` — 重写 DEFAULT/GOTHIC/CHINESE 卡片动画
3. `SweetAnimationProvider.kt` — 更新粒子参数
4. `GothicAnimationProvider.kt` — 更新粒子参数
5. `ChineseAnimationProvider.kt` — 更新粒子参数
6. `ClassicAnimationProvider.kt` — 更新粒子参数
7. `SkinBackgroundAnimation.kt` — 更新粒子创建逻辑
8. 新增 `particles/SweetStarParticle.kt`
9. 新增 `particles/GothicEmberParticle.kt`
10. 新增 `particles/ChinesePlumBlossomParticle.kt`
11. 新增 `particles/ClassicDiamondParticle.kt`
12. `app/build.gradle.kts` — 版本号 bump
