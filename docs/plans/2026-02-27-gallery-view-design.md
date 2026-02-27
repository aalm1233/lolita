# 图鉴画廊视图设计

日期：2026-02-27

## 概述

在服饰 tab（ItemListScreen）新增第三种视图模式「画廊」，以瀑布流形式展示服饰图片，提供沉浸式视觉浏览体验。点击卡片可进入全屏大图预览，支持缩放和左右翻页。

## 架构

### 入口与数据流

- 画廊作为 ItemListScreen 的第三种视图模式（网格 / 列表 / 画廊）
- 数据流不变：`ItemDao → ItemRepository → ItemListViewModel → ItemListScreen`
- 复用现有筛选体系（状态、分类、季节、风格、颜色、品牌、文本搜索）
- 筛选条件在三种视图间共享，切换视图不重置筛选
- 无图片的服饰在画廊模式下自动过滤隐藏

### 新增依赖

- `me.saket.telephoto:zoomable`（纯 Modifier，无 Coil 依赖，与现有 Coil 2.7.0 零冲突）

### ViewModel 变更

- `ItemListViewModel` 新增 `viewMode: StateFlow<ViewMode>` 字段（枚举：GRID / LIST / GALLERY）
- 新增 `shuffledItems: StateFlow<List<Item>>` 仅画廊模式使用
- 视图模式持久化到 `AppPreferences`（DataStore）

## 瀑布流卡片设计

- 布局：`LazyVerticalStaggeredGrid`，2 列，水平间距 8.dp，垂直间距 8.dp
- 图片：Coil AsyncImage，保持原始宽高比，圆角裁剪（跟随皮肤 shape）
- 多图服饰只显示第一张图片
- 信息叠加：半透明渐变遮罩覆盖图片底部，叠加名称（单行，白色）和品牌（单行，白色 70% 透明度，小字号）
- 交互：`SkinClickable` 包裹，带皮肤风格按压反馈
- 动画：`SkinItemAppear` 交错入场，`SkinFlingBehavior` 滚动摩擦

## 大图预览

- 实现：全屏 Dialog（黑色半透明背景）+ `HorizontalPager`
- 初始位置：定位到被点击的服饰
- 缩放：`Modifier.zoomable()`（Telephoto）支持双击缩放、捏合缩放、拖拽平移
- 翻页：左右滑动切换上/下一件服饰（仅未缩放状态可翻页）
- 底部信息：服饰名称 + 品牌，白色文字居中
- 进入详情：点击底部信息区域跳转 ItemDetail
- 关闭：点击背景或下滑手势
- 页码：底部小圆点指示器，显示当前位置 / 总数
- 数据源与当前瀑布流筛选结果一致

## 视图切换

- 三态切换按钮：网格 → 列表 → 画廊
- 画廊图标：皮肤图标系统新增画廊图标（Canvas 绘制，5 个皮肤各自实现）
- 切换带皮肤风格过渡动画

## 画廊随机排序

- 每次切换到画廊模式时，对当前筛选结果随机 shuffle
- 切换回网格/列表恢复正常排序
- 筛选条件变化时重新 shuffle
- 恢复到画廊模式时重新 shuffle（每次都是新鲜顺序）
