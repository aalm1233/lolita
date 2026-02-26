# 淘宝导入指南页面设计

日期：2026-02-26

## 背景

淘宝导入订单功能流程较复杂，用户可能不清楚如何从淘宝导出订单 Excel 文件。需要在 app 内提供一个指导文档，帮助用户完成导出操作。

## 设计决策

- **展示方式**：全屏新页面（独立 Screen）
- **入口位置**：TaobaoImportScreen 顶部导航栏 actions 区域的帮助图标按钮
- **文档范围**：仅覆盖"如何从淘宝导出订单 Excel 文件"，不讲解 app 内操作
- **实现方案**：纯 Compose 原生组件，融入皮肤系统
- **图片存储**：res/drawable 内置资源，初期用占位框，后续手动替换截图

## 页面结构

### 导航

- 新增 `Screen.TaobaoImportGuide` 路由
- `GradientTopAppBar`（compact = true），标题 "导入指南"，左侧返回按钮
- 内容区域：`LazyColumn` 滚动浏览

### 入口

- TaobaoImportScreen 的 GradientTopAppBar actions 添加 HELP 皮肤图标按钮
- SELECT / PREPARE / DETAIL 步骤可见，IMPORTING / RESULT 步骤隐藏

## 文档内容

### 1. 导出步骤（PC 端）

讲解如何在淘宝网页版导出订单：
- 登录淘宝 → 已买到的宝贝
- 找到导出/下载订单的入口
- 选择时间范围、导出格式
- 下载 .xlsx 文件
- 关键步骤配截图占位

### 2. 注意事项

- 仅支持 .xlsx 格式
- 建议按需选择时间范围，避免文件过大
- 导出的文件需要传输到手机上（微信/QQ 发送、云盘等）

## 截图占位框样式

- 灰色背景 `Color(0xFFF0F0F0)`，圆角 8dp
- 虚线边框，颜色 `Color(0xFFCCCCCC)`
- 内部居中提示文字（如 "截图：已买到的宝贝页面"）
- 固定高度 200dp，宽度填满
- 后续替换：将截图放入 `res/drawable`，代码中替换占位框为 `Image(painterResource(R.drawable.xxx))`

## 新增皮肤图标

- `IconKey.HELP`：圆圈内问号
- 实现位置：BaseSkinIconProvider + SweetIconProvider / GothicIconProvider / ChineseIconProvider / ClassicIconProvider / NavyIconProvider
- 各皮肤风格适配（Sweet 圆润、Gothic 尖锐、Chinese 书法感、Classic 优雅衬线、Navy 流畅）
