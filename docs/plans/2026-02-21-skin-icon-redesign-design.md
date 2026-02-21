# 皮肤图标结构性差异化重设计

日期：2026-02-21

## 概述

对4套皮肤（Sweet/Gothic/Chinese/Classic）的8个图标进行结构性差异化重设计，使每套皮肤的图标从基本形状上就有明显区别，而非仅添加小装饰。

同时修复以下问题：
- `SortOption.kt` 中排序图标仍直接使用 Material Icon，未走 SkinIcon
- `ItemListScreen` 视图切换按钮三种状态全部错误使用 `IconKey.Sort`
- `CoordinateListScreen` 视图切换直接使用 Material Icon
- 新增 3 个 IconKey：`ViewAgenda`、`GridView`、`Apps`

## 涉及图标（8个）

### 现有图标重设计（5个）
- ArrowBack（返回箭头）
- Add（添加）
- FilterList（过滤）
- Sort（排序）

### 新增图标（3个）
- ViewAgenda（1列/列表视图）
- GridView（2列/网格视图）
- Apps（3列/密集视图）

## 设计规范

所有图标均为 24dp Canvas 绘制，使用 `size.minDimension` 作为基准尺寸 `s`。

---

### 1. ArrowBack（返回箭头）

| 皮肤 | 设计 |
|------|------|
| Sweet | 丝带箭头——箭头主体是一条飘逸的缎带，尾端自然卷曲成蝴蝶结的一半，箭尖圆润 |
| Gothic | 匕首箭头——尖锐的哥特式匕首轮廓，箭杆两侧有对称小荆棘/倒刺，线条硬朗无圆角 |
| Chinese | 书法撇笔——模拟毛笔"撇"笔画，起笔重收笔轻，箭尖处有飞白效果（断续线） |
| Classic | 卷纹箭头——线条末端延伸为维多利亚式卷纹（volute），箭尖处有小涡旋装饰 |

### 2. Add（添加）

| 皮肤 | 设计 |
|------|------|
| Sweet | 花朵加号——十字四端各绽开一片花瓣，中心小圆点（花蕊），整体像四瓣花，线条圆润饱满 |
| Gothic | 铁十字——Iron Cross 造型，四臂从中心向外展开逐渐变宽，末端平切，臂边缘有微小锯齿 |
| Chinese | 篆书"十"——模拟篆刻印章中"十"字写法，笔画粗壮方正，起收笔处有刀刻感（方角） |
| Classic | 百合十字——四臂末端各有小百合花饰（fleur-de-lis 尖端），线条优雅对称，带粗细渐变 |

### 3. FilterList（过滤）

| 皮肤 | 设计 |
|------|------|
| Sweet | 蛋糕层叠——三层蛋糕轮廓，最宽在上逐层收窄，顶部小樱桃圆点，每层边缘微波浪形 |
| Gothic | 倒三角锁链——三横线端点由垂直锁链环连接，形成倒三角铁栅栏形状，线条末端有尖刺 |
| Chinese | 折扇——三横线呈扇面弧形排列（上宽下窄），底部汇聚于扇骨轴心，线条有毛笔粗细变化 |
| Classic | 枝形烛台——三横线递减，中央纵向细线串联（烛台主干），横线末端有小卷纹球饰 |

### 4. Sort（排序）

| 皮肤 | 设计 |
|------|------|
| Sweet | 糖果阶梯——三条递减横线右端各有小圆球（棒棒糖头），线条圆润 |
| Gothic | 阶梯尖塔——三条递减横线右端向上延伸出尖塔尖顶，左端有小蝙蝠翼装饰 |
| Chinese | 山水层叠——三条递减横线画成远山轮廓（微起伏弧线），最长最淡最短最浓，体现水墨远近 |
| Classic | 罗马柱阶梯——三条递减横线左端对齐于纵向细线（柱身），横线末端有小圆球装饰（柱头） |

### 5. ViewAgenda（1列/列表视图）

| 皮肤 | 设计 |
|------|------|
| Sweet | 两个圆角矩形上下堆叠（像饼干），边缘微波浪，右上角各有小爱心装饰 |
| Gothic | 两个尖角矩形上下堆叠（像墓碑），四角尖锐无圆角，矩形内有细十字纹理 |
| Chinese | 两个竖卷轴上下排列，矩形两端有卷轴轴头（小圆），线条有毛笔粗细变化 |
| Classic | 两个带边框矩形上下堆叠，边框线条带衬线末端，矩形内有细横线纹理（像书页） |

### 6. GridView（2列/网格视图）

| 皮肤 | 设计 |
|------|------|
| Sweet | 四个圆角小方块（2×2），间距较大，角落极度圆润，像软糖 |
| Gothic | 四个菱形（2×2），尖角朝上，菱形之间由细线连接形成铁窗格效果 |
| Chinese | 四个方块排列成"田"字，线条模拟窗棂/格子窗木框效果，交叉处略粗 |
| Classic | 四个方块（2×2），每个方块内有微小对角卷纹装饰，边框线条均匀优雅 |

### 7. Apps（3列/密集视图）

| 皮肤 | 设计 |
|------|------|
| Sweet | 九个小圆点（3×3），像彩色糖珠排列，圆点大小略有变化增加活泼感 |
| Gothic | 九个小菱形/钻石（3×3），尖锐几何排列，整齐冷峻 |
| Chinese | 九个小方点（3×3），模拟围棋棋盘星位点，点的形状微方 |
| Classic | 九个小圆点（3×3），每个点外围有极细圆环装饰（像珍珠），排列精致对称 |

---

## 代码变更范围

### 接口层
- `SkinIconProvider.kt` — `ActionIcons` 接口新增 `ViewAgenda`、`GridView`、`Apps` 三个方法
- `IconKey.kt` — 新增 `ViewAgenda`、`GridView`、`Apps` 三个枚举值
- `SkinIcon.kt` — `when` 块新增三个 case

### 基础实现
- `BaseSkinIconProvider.kt` — 新增 `ViewAgenda`、`GridView`、`Apps` 的 Material Icon 回退实现

### 皮肤实现（重设计 + 新增）
- `SweetIconProvider.kt` — 重写 ArrowBack、Add、FilterList、Sort + 新增 ViewAgenda、GridView、Apps
- `GothicIconProvider.kt` — 同上
- `ChineseIconProvider.kt` — 同上
- `ClassicIconProvider.kt` — 同上

### 屏幕修复
- `SortOption.kt` — `Icons.AutoMirrored.Filled.Sort` → `SkinIcon(IconKey.Sort)`
- `ItemListScreen.kt` — 视图切换按钮改用 `IconKey.ViewAgenda`/`GridView`/`Apps`
- `CoordinateListScreen.kt` — Material `ViewAgenda`/`GridView`/`Apps` → `SkinIcon`
