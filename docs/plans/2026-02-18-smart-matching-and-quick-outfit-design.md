# 智能搭配推荐 + 快捷穿搭记录 设计文档

日期：2026-02-18

## 背景

用户日常使用中有两个主要痛点：
1. 选了一件主品后，缺少智能推荐能搭配的配饰/头饰/袜子等
2. 穿搭记录流程繁琐，容易忘记，导致使用率低

## 功能一：智能搭配推荐（向量相似度）

### 核心思路

将每件物品的属性（风格、颜色、季节）编码为数值向量，通过余弦相似度计算物品间的匹配程度。历史搭配记录作为额外加权因子。

### 向量编码

每件物品编码为多维向量，由三部分拼接：

- **风格维度**：每种风格一个位（甜系、古典、哥特...），有则 1，无则 0
- **颜色维度**：每种颜色一个位，有则 1，无则 0
- **季节维度**：春/夏/秋/冬/四季，各一个位

示例：粉色甜系春夏JSK → `[1,0,0,0,0,0, 1,0,0,..., 1,1,0,0,0]`

### 匹配逻辑

1. 用户选中一件物品 A
2. 计算 A 与所有其他 OWNED 状态物品的余弦相似度
3. 排除同 CategoryGroup 的物品（选了 JSK 不推荐另一件 JSK，推荐配饰）
4. 历史加权：A 和 B 曾在同一个 Coordinate 或 OutfitLog 中出现过，相似度 × 1.3
5. 按分数降序，分 Category 展示 Top 5

### 入口

- ItemDetailScreen 新增「推荐搭配」按钮
- 点击后进入 RecommendationScreen，按分类分组展示推荐列表

### 新增组件

| 组件 | 职责 |
|------|------|
| `MatchingEngine` | 向量编码 + 余弦相似度计算 |
| `RecommendationRepository` | 查询历史搭配关系、获取物品数据 |
| `RecommendationViewModel` | 驱动推荐 UI 状态 |
| `RecommendationScreen` | 展示按分类分组的推荐结果 |

## 功能二：桌面 Widget 快捷穿搭记录

### 技术选型

Jetpack Glance（Compose Widget 框架），与项目现有 Compose 技术栈一致。

新增依赖：`androidx.glance:glance-appwidget` + `glance-material3`

### Widget 设计（4×2 格）

- 今日日期显示
- 今日已记录穿搭：显示物品缩略图（最多 3 张）
- 「记录今日穿搭」按钮 → 跳转 QuickOutfitLogScreen
- 已记录时按钮变为「查看/编辑」

### 简化版穿搭记录（QuickOutfitLogScreen）

与现有 OutfitLogEditScreen 的区别：

| 项目 | OutfitLogEditScreen | QuickOutfitLogScreen |
|------|-------------------|---------------------|
| 日期 | 可选任意日期 | 锁定今天 |
| 物品选择 | 普通列表 | 网格，最近穿过的排前面 |
| 备注 | 必填区域 | 可选，默认折叠 |
| 入口 | 穿搭Tab → 新增 | Widget / 首页快捷卡片 |

### 首页快捷卡片

ItemListScreen 顶部新增「今日穿搭」卡片：
- 未记录：显示提示 + 「记录」按钮
- 已记录：显示缩略图 + 「查看」按钮

### 每日提醒通知

- 设置页新增「每日穿搭提醒」开关 + 时间选择（默认晚 8 点）
- 通知点击跳转 QuickOutfitLogScreen
- 今天已有记录则不发通知
- 使用 AppPreferences（DataStore）存储配置
- 复用现有 AlarmManager 基础设施

### 新增组件

| 组件 | 职责 |
|------|------|
| `OutfitWidget` | Glance Widget 布局 |
| `OutfitWidgetReceiver` | Widget 广播接收器 |
| `QuickOutfitLogScreen` | 简化版穿搭记录 UI |
| `QuickOutfitLogViewModel` | 驱动快捷记录状态 |
| `DailyOutfitReminderScheduler` | 每日提醒调度 |
| `DailyOutfitReminderReceiver` | 通知广播接收器 |

## 实现优先级

1. 先做智能搭配推荐（不依赖新框架，风险低）
2. 再做快捷穿搭记录 + Widget（涉及 Glance 新依赖）
3. 每日提醒通知最后做（复用现有基础设施，相对简单）
