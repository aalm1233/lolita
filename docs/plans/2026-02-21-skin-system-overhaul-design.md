# 皮肤系统深度定制设计文档

日期：2026-02-21

## 概述

对现有 4 套皮肤（甜美粉/哥特暗黑/中华风韵/经典优雅）进行深度定制：
- 45 个 Material 图标全部替换为 Canvas 手绘图标，每套皮肤完全不同的绘制风格
- 4 类动画场景（皮肤切换、底栏切换、卡片列表、交互反馈）每套皮肤完全不同的动画语言
- 架构方案：皮肤引擎 + 接口抽象（方案 A）

## 一、图标体系架构

### 接口分组

```
SkinIconProvider (总接口)
├── NavigationIcons      // 底栏5个: Home, Wishlist, Outfit, Stats, Settings
├── ActionIcons          // 操作类12个: Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh
├── ContentIcons         // 内容类13个: Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category
├── ArrowIcons           // 方向类9个: ArrowBack, ArrowForward, KeyboardArrowLeft/Right, ExpandMore/Less, ArrowDropDown, SwapVert, OpenInNew
└── StatusIcons          // 状态类6个: CheckCircle, Warning, Error, Info, Visibility, VisibilityOff
```

统一签名：`@Composable fun IconName(modifier: Modifier, tint: Color)`

### 4 套皮肤绘制风格

| 皮肤 | 线条特征 | 装饰元素 | 填充方式 |
|------|---------|---------|---------|
| 甜美粉 | 圆润、2.5dp粗、圆端点 | 小心形、蝴蝶结、花瓣 | 柔和渐变填充 |
| 哥特暗黑 | 尖锐、1.5dp细、方端点 | 十字架、蔷薇刺、蝙蝠翼 | 描边为主、暗色阴影 |
| 中华风韵 | 毛笔笔触、粗细变化 | 云纹、祥云、梅花 | 水墨晕染效果 |
| 经典优雅 | 精致细线、1.8dp、对称 | 卷草纹、黑桃♠、皇冠 | 线框+局部金色填充 |

### 45 个图标完整清单

**NavigationIcons（5个）**

| 图标 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| Home | 圆顶小屋+心形窗+屋顶蝴蝶结 | 哥特尖塔城堡+十字窗+蔷薇藤 | 飞檐亭台+云纹门窗+梅枝 | 维多利亚庄园+拱门+卷草纹 |
| Wishlist | 饱满双心叠加+小翅膀 | 带刺蔷薇心+暗红滴血 | 如意结形心+祥云环绕 | 维多利亚花体心+皇冠顶饰 |
| Outfit | 衣架+蝴蝶结丝带 | 束腰胸衣轮廓+锁链 | 汉服交领轮廓+流苏 | 洛可可裙摆轮廓+蕾丝边 |
| Stats | 圆润饼图+星星装饰 | 暗色水晶球+裂纹 | 算盘/算筹+云纹框 | 怀表+罗马数字+齿轮 |
| Settings | 花朵形齿轮+中心心形 | 暗色齿轮+蝙蝠翼+十字 | 八卦/太极图案+云纹 | 精密齿轮组+皇冠中心 |

**ActionIcons（12个）**

| 图标 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| Add | 圆润+号+花瓣端点 | 尖锐十字+暗影 | 毛笔"十"字+墨点 | 精致十字+卷草端点 |
| Delete | 圆润垃圾桶+心形盖 | 骷髅头垃圾桶+骨头 | 焚纸炉+烟雾 | 古典垃圾桶+浮雕纹 |
| Edit | 圆润铅笔+星星尾迹 | 羽毛笔+墨水瓶+暗影 | 毛笔+砚台 | 钢笔+墨水瓶+卷纹 |
| Search | 圆润放大镜+心形把手 | 暗色单片眼镜+锁链 | 灯笼形放大镜+流苏 | 精致放大镜+雕花柄 |
| Sort | 圆润三横+蝴蝶结 | 尖锐三横+刺状端 | 毛笔三横+粗细变化 | 精致三横+卷草端 |
| Save | 圆润对勾+花瓣 | 哥特对勾+十字光 | 毛笔对勾+印章感 | 精致对勾+金框 |
| Close | 圆润×+花瓣散落 | 尖锐×+裂纹 | 毛笔×+墨迹飞溅 | 精致×+卷草 |
| Share | 圆润分享+心形节点 | 暗色蜘蛛网状分享 | 飞鸽传书轮廓 | 火漆信封+蜡封 |
| FilterList | 圆润漏斗+花瓣 | 暗色漏斗+蔷薇刺 | 筛子/竹筛+云纹 | 精致漏斗+卷草 |
| MoreVert | 三个小心形竖排 | 三个暗色菱形 | 三个墨点竖排 | 三个小黑桃竖排 |
| ContentCopy | 圆角双纸+心形角标 | 暗色双纸+蝙蝠角标 | 双宣纸+印章角标 | 精致双纸+蜡封角标 |
| Refresh | 圆润循环箭头+花瓣 | 暗色循环+蔷薇刺 | 太极旋转+云纹 | 精致循环+卷草箭头 |

**ContentIcons（13个）**

| 图标 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| Star | 圆润五角星+闪烁光点 | 暗色五芒星+光晕 | 梅花五瓣+花蕊 | 精致星形+皇冠顶 |
| StarBorder | 圆润空心星+虚线 | 暗色空心五芒星 | 空心梅花+虚线 | 精致空心星+细线 |
| Image | 圆角相框+心形山 | 暗色画框+蝙蝠山 | 卷轴画+山水 | 椭圆画框+风景 |
| Camera | 圆润相机+心形快门 | 暗色相机+十字准星 | 暗箱/画匣+云纹 | 古典相机+黄铜质感 |
| AddPhoto | 圆角相框+花瓣加号 | 暗色画框+十字加号 | 卷轴+毛笔加号 | 精致画框+卷草加号 |
| Link | 圆润链环+心形 | 暗色锁链+铁锈 | 玉佩连环+丝带 | 精致链环+金色 |
| LinkOff | 断开链环+碎心 | 断裂锁链+火花 | 断玉+墨迹 | 断链+褪色 |
| Palette | 圆润调色盘+花瓣色块 | 暗色调色盘+血色 | 砚台+墨色渐变 | 精致调色盘+金边 |
| FileOpen | 圆角文件夹+蝴蝶结 | 暗色古书+锁扣 | 竹简/卷轴展开 | 精致文件夹+蜡封 |
| CalendarMonth | 圆角日历+心形日期 | 暗色日历+十字标记 | 黄历/皇历+云纹 | 精致日历+金边 |
| Notifications | 圆润铃铛+蝴蝶结 | 暗色钟+蝙蝠 | 编钟+流苏 | 精致铃铛+皇冠 |
| AttachMoney | 圆润$+花瓣装饰 | 暗色$+锁链 | 铜钱/元宝形 | 精致$+卷草 |
| Category | 圆润四格+各含小心形 | 暗色四格+各含暗符 | 四格+梅兰竹菊 | 精致四格+各含纹饰 |

**ArrowIcons（9个）**

| 图标 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| ArrowBack | 圆润左箭头+花瓣尾 | 尖锐哥特箭头+暗影 | 毛笔左箭头+墨迹 | 精致左箭头+卷草 |
| ArrowForward | 圆润右箭头+花瓣尾 | 尖锐哥特箭头 | 毛笔右箭头 | 精致右箭头+卷草 |
| KeyboardArrowLeft | 圆润<+花瓣 | 尖锐< | 毛笔< | 精致< |
| KeyboardArrowRight | 圆润>+花瓣 | 尖锐> | 毛笔> | 精致> |
| ExpandMore | 圆润V+花瓣 | 尖锐V+暗影 | 毛笔V+墨点 | 精致V+卷草 |
| ExpandLess | 圆润^+花瓣 | 尖锐^+暗影 | 毛笔^+墨点 | 精致^+卷草 |
| ArrowDropDown | 圆润下三角+花瓣 | 尖锐下三角 | 毛笔下三角 | 精致下三角 |
| SwapVert | 圆润上下箭头+心形 | 尖锐上下箭头+十字 | 毛笔上下箭头+云纹 | 精致上下箭头+卷草 |
| OpenInNew | 圆润外链+花瓣 | 暗色外链+蝙蝠翼 | 飞鸟出框+云纹 | 精致外链+卷草 |

**StatusIcons（6个）**

| 图标 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| CheckCircle | 花环内对勾 | 暗色圆+哥特对勾 | 玉璧内对勾 | 金框圆+精致对勾 |
| Warning | 圆润三角+心形叹号 | 暗色三角+骷髅 | 令牌形+毛笔叹号 | 精致三角+金边叹号 |
| Error | 圆润圆+花瓣× | 暗色圆+裂纹× | 破碎玉璧+墨× | 精致圆+金边× |
| Info | 圆润圆+花体i | 暗色圆+哥特i | 玉璧+篆体i | 精致圆+衬线i |
| Visibility | 圆润眼睛+心形瞳孔 | 暗色眼睛+竖瞳 | 凤眼+云纹 | 精致眼睛+睫毛细节 |
| VisibilityOff | 圆润闭眼+花瓣 | 暗色闭眼+缝线 | 闭凤眼+墨迹 | 精致闭眼+斜线 |

### 文件组织

```
ui/theme/skin/icon/
├── SkinIconProvider.kt          // 总接口 + 5个子接口
├── BaseSkinIconProvider.kt      // 基类，默认fallback到Material图标
├── IconKey.kt                   // 图标枚举键
├── sweet/SweetIcons.kt          // 甜美粉全部45个图标Canvas实现
├── gothic/GothicIcons.kt        // 哥特暗黑
├── chinese/ChineseIcons.kt      // 中华风韵
└── classic/ClassicIcons.kt      // 经典优雅
```

## 二、动画体系架构

### 接口定义

```kotlin
interface SkinAnimationProvider {
    val skinTransition: SkinTransitionSpec
    val tabSwitchAnimation: TabSwitchAnimationSpec
    val cardAnimation: CardAnimationSpec
    val interactionFeedback: InteractionFeedbackSpec
}
```

### 4 套皮肤动画语言

| 场景 | 甜美粉 | 哥特暗黑 | 中华风韵 | 经典优雅 |
|------|--------|---------|---------|---------|
| 皮肤切换 | 粉色心形粒子从中心扩散覆盖 | 暗影从边缘蔓延吞噬+十字架闪烁 | 水墨从一角晕染铺满全屏 | 旧纸翻页效果+金色光线扫过 |
| 底栏切换 | 选中项弹跳+小心形冒泡上浮 | 选中项暗红光晕脉动+蔷薇刺生长 | 选中项水墨圈晕开+云纹飘动 | 选中项金框优雅展开+卷草纹延伸 |
| 卡片进入 | 从小弹大(overshoot)+轻微旋转 | 从暗处淡入+暗影拖尾 | 水墨笔触从左向右"写"出卡片 | 从下方优雅滑入+金边渐显 |
| 交互反馈 | 粉色涟漪+按下时微缩弹回 | 暗红裂纹扩散+轻微震颤 | 水墨溅射涟漪 | 金色光圈扩散+按压浮雕效果 |

### 动画曲线特征

| 皮肤 | 缓动曲线 | 时长倾向 | 特征 |
|------|---------|---------|------|
| 甜美粉 | FastOutSlowIn + overshoot | 较短 300-400ms | 活泼弹跳感 |
| 哥特暗黑 | LinearOutSlowIn | 较长 500-700ms | 缓慢沉重，戏剧性 |
| 中华风韵 | CubicBezier(0.25,0.1,0.25,1.0) | 中等 400-500ms | 行云流水，自然流畅 |
| 经典优雅 | EaseInOutCubic | 中等 350-450ms | 从容不迫，精致克制 |

### 数据类

```kotlin
data class SkinTransitionSpec(
    val durationMs: Int,
    val overlay: @Composable (progress: Float) -> Unit
)

data class TabSwitchAnimationSpec(
    val indicatorAnimation: AnimationSpec<Float>,
    val selectedEffect: @Composable (selected: Boolean) -> Modifier,
    val particleEffect: (@Composable (Offset) -> Unit)?
)

data class CardAnimationSpec(
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition,
    val staggerDelayMs: Int,
    val enterDurationMs: Int
)

data class InteractionFeedbackSpec(
    val pressScale: Float,
    val rippleColor: Color,
    val rippleAlpha: Float,
    val customRipple: (@Composable (Offset, Float) -> Unit)?
)
```

### 文件组织

```
ui/theme/skin/animation/
├── SkinAnimationProvider.kt
├── sweet/SweetAnimations.kt
├── gothic/GothicAnimations.kt
├── chinese/ChineseAnimations.kt
└── classic/ClassicAnimations.kt
```

## 三、皮肤配置扩展与组件改造

### LolitaSkinConfig 扩展

新增两个字段：
- `icons: SkinIconProvider` — 图标提供者
- `animations: SkinAnimationProvider` — 动画提供者

### 需要改造的公共组件

| 组件 | 改造内容 |
|------|---------|
| GradientTopAppBar | navigationIcon 使用 skin.icons；装饰符号改为 Canvas 绘制 |
| LolitaCard | 添加 AnimatedVisibility，使用 skin.animations.cardAnimation |
| NavigationBar（底栏） | 图标替换为 skin.icons.navigation.*；选中态添加皮肤动画 |
| SwipeToDeleteContainer | 删除背景动画按皮肤定制 |
| 所有 Icon() 调用 | 替换为 skin.icons.*.*() |

### 新增公共组件

- `SkinClickable` — 皮肤感知的点击修饰符
- `SkinRippleIndication` — 自定义涟漪指示
- `SkinTransitionOverlay` — 皮肤切换过渡覆盖层

### 文件组织

```
ui/theme/skin/component/
├── SkinClickable.kt
├── SkinRippleIndication.kt
└── SkinTransitionOverlay.kt
```

## 四、迁移策略

### 分层实施

- Phase 1: 基础设施搭建（接口定义、LolitaSkinConfig扩展、BaseSkinIconProvider默认fallback）
- Phase 2: 逐皮肤实现图标（甜美粉→哥特→中华→经典，每套45个Canvas实现）
- Phase 3: 动画系统（交互反馈→卡片列表→底栏切换→皮肤切换过渡）
- Phase 4: 全局替换（逐屏替换 Icon() 调用，移除 Material Icons import）

### Fallback 机制

BaseSkinIconProvider 基类默认用 Material 图标实现所有方法，子类逐个覆盖为 Canvas 实现，未覆盖的仍正常显示。

### 辅助函数

提供 `SkinIcon(key, modifier, tint)` 统一入口，简化全局替换。

### 性能考量

- 复杂图标用 `remember` 缓存 Path 对象
- 粒子动画限制最大粒子数 ≤ 20
- 皮肤切换 overlay 使用独立 Canvas 层
