# 位置功能设计文档

日期：2026-02-22

## 概述

去掉服饰页面的「全部」tab，新增「位置」tab 作为默认首页。为所有服饰新增位置属性，在设置中新增位置管理，支持配置位置的名称、描述和图片。位置 tab 以卡片列表展示各位置信息，点击进入详情页查看该位置下的服饰。

## 决策记录

- Tab 顺序：位置 → 已拥有 → 套装
- 存储方式：外键关联（Item.locationId → Location.id）
- 删除策略：SET NULL（删除位置后关联服饰的 locationId 置空）
- 展示形式：卡片列表 + 独立详情页
- 未分配服饰：在位置列表末尾显示「未分配」虚拟卡片

## 数据层

### Location 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, autoGenerate) | 主键 |
| name | String | 位置名称 |
| description | String | 位置描述 |
| imageUrl | String? | 图片路径（ImageFileHelper） |
| sortOrder | Int = 0 | 排序 |
| createdAt | Long | 创建时间 |
| updatedAt | Long | 更新时间 |

### Item 表变更

- 新增 `locationId: Long? = null`，外键指向 Location，ON DELETE SET NULL
- DB migration v6→v7

### LocationDao

- `getAll(): Flow<List<Location>>` — 按 sortOrder 排序
- `getById(id): Flow<Location?>`
- `insert(location): Long`
- `update(location)`
- `delete(location)`
- `getItemCountByLocationId(locationId): Flow<Int>`

### ItemDao 新增

- `getItemsByLocationId(locationId): Flow<List<Item>>`
- `getItemsWithNoLocation(): Flow<List<Item>>`

### LocationRepository → AppModule lazy singleton

## UI 层

### 1. ItemListScreen tab 改造

- 移除「全部」tab
- 新 tab 顺序：「位置」→「已拥有」→「套装」
- HorizontalPager 3 页：
  - Page 0：LocationListContent（新）
  - Page 1：已拥有服饰列表（filterByStatus(OWNED)）
  - Page 2：套装列表（CoordinateListContent）
- 默认 initialPage = 0

### 2. LocationListContent（位置卡片列表）

- LazyColumn 展示所有位置
- 每个位置用 LolitaCard：位置图片 + 名称 + 描述（最多2行）+ 服饰数量
- 列表末尾「未分配」虚拟卡片（无图片，显示未分配服饰数量）
- SkinItemAppear 动画 + SkinFlingBehavior 滚动
- 点击 → 导航到 LocationDetail

### 3. LocationDetail 详情页

- GradientTopAppBar（compact = true），标题为位置名称
- 顶部：位置大图 + 描述
- 下方：该位置服饰列表/网格（复用 item 列表展示逻辑）
- 点击服饰 → ItemDetail
- 「未分配」详情页标题为「未分配」

### 4. LocationManageScreen（设置 → 位置管理）

- 复用 BrandManage 模式：列表 + FAB 添加
- 每项显示：缩略图 + 名称 + 描述 + 服饰数量
- 点击编辑：dialog 修改名称、描述、图片（拍照/相册）
- 删除：确认 dialog，提示「关联的 N 件服饰将变为未分配」
- 支持拖拽排序（sortOrder）

### 5. ItemEditScreen 改造

- 新增「位置」下拉选择器，可选「无」（null）
- 放在 brand/category 选择器附近

### 6. 导航新增

- `Screen.LocationDetail(locationId: Long)` — locationId = -1 表示未分配
- `Screen.LocationManage` — 设置子页面

## 备份兼容

- BackupManager 导出/导入新增 Location 表
- 旧备份缺失 Location 数据时跳过（兼容）
- Item 的 locationId 缺失时默认 null

## 皮肤图标

- 新增 `IconKey.Location`
- BaseSkinIconProvider 默认实现
- 4 皮肤各自实现：
  - Sweet：房子 + 心形窗户
  - Gothic：哥特尖顶建筑
  - Chinese：中式屋檐
  - Classic：维多利亚式衣柜

## 版本号

- versionCode +1
- versionName 2.1.1 → 2.2
