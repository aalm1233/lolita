# 全局UI压缩 + 粒子效果重构设计

日期: 2026-02-22

## 目标

1. 压缩全局页面的顶部区域（搜索栏、筛选条件、TabRow、TopAppBar）和底部导航栏，把空间留给主体内容
2. 移除卡片上的粒子/发光效果（SkinCardGlow），所有粒子效果统一放到背景上
3. 卡片改为半透明（0.75f alpha），背景粒子可透过卡片隐约可见
4. 适当增加背景粒子数量

## 方案：直接压缩（方案A）

直接修改各组件的 padding/spacing 值，不引入额外抽象层。

## 一、空间压缩

| 组件 | 当前 | 目标 |
|------|------|------|
| 搜索栏垂直 padding | 4.dp | 2.dp |
| 搜索栏水平 padding | 16.dp | 12.dp |
| 筛选条件垂直 padding | 4.dp | 2.dp |
| 筛选条件 chip 高度 | 28.dp | 24.dp |
| 筛选条件水平 padding | 16.dp | 12.dp |
| 快速穿搭卡片垂直 padding | 8.dp | 4.dp |
| 快速穿搭卡片内部 padding | 12.dp | 8.dp |
| 列表项垂直间距 | 12.dp | 8.dp |
| 网格项间距 | 12.dp | 8.dp |
| StatsPage TabRow edge padding | 8.dp | 4.dp |
| GradientTopAppBar compact 垂直 padding | 4.dp | 2.dp |
| 底部导航栏高度 | 64.dp | 52.dp |

## 二、卡片粒子效果移除 + 透明度

- `LolitaCard.kt`: 移除 `skinCardGlow()` modifier，卡片背景 alpha 改为 0.75f
- `SkinCardGlow.kt`: 删除文件（不再被引用）
- 卡片 elevation: 4.dp → 2.dp
- 卡片容器颜色: `surface.copy(alpha = 0.75f)`

## 三、背景粒子增强

| 皮肤 | 当前数量 | 目标数量 | 粒子类型比例（不变） |
|------|---------|---------|-------------------|
| DEFAULT | 20 | 28 | 气泡40% / 花瓣30% / 星星30% |
| GOTHIC | 12 | 18 | 烟雾50% / 余烬50% |
| CHINESE | 10 | 16 | 云朵40% / 梅花60% |
| CLASSIC | 14 | 20 | 闪光50% / 钻石50% |

## 涉及文件

- `ItemListScreen.kt` — 搜索栏、筛选条件、列表/网格间距
- `StatsPageScreen.kt` — TabRow edge padding
- `LolitaNavHost.kt` — 底部导航栏高度
- `GradientTopAppBar.kt` — compact 模式垂直 padding
- `LolitaCard.kt` — 移除 skinCardGlow、加 alpha、降 elevation
- `SkinCardGlow.kt` — 删除
- `SkinBackgroundAnimation.kt` — 增加粒子数量
