# 三功能设计：穿搭日记图片改大 / 品牌商标图 / 颜色多选

日期：2026-02-22

## 功能一：穿搭日记图片改大

纯 UI 调整，不涉及数据层。

| 页面 | 文件 | 当前 | 改为 |
|------|------|------|------|
| OutfitLogListScreen 列表卡片图 | OutfitLogListScreen.kt | height 160dp | height 220dp |
| OutfitLogDetailScreen 多图 | OutfitLogDetailScreen.kt | width 220dp, ratio 0.75f | width 300dp, ratio 0.75f |
| OutfitLogEditScreen 编辑网格 | OutfitLogEditScreen.kt | 不变 | 不变 |

## 功能二：品牌商标图

### 数据层

- Brand 实体新增 `logoUrl: String? = null`
- DB migration（当前版本+1）：`ALTER TABLE brand ADD COLUMN logoUrl TEXT`
- BackupManager 导入兼容无 logoUrl 的旧备份（字段可空）
- 图片存储复用 `ImageFileHelper`，存到 `filesDir/images/`

### UI 改动

- BrandManageScreen：编辑/新增对话框增加图片选择按钮；列表项左侧显示 logo 小圆图（32dp），无 logo 时显示品牌名首字占位
- ItemEditScreen：品牌选择器对话框中品牌项前显示 logo 小图
- ItemDetailScreen：品牌名旁显示 logo 小图
- ItemListScreen 卡片：品牌名旁带 logo 小图
- 其他引用品牌的地方（统计、筛选等）：品牌名前带 logo 小图

## 功能三：颜色多选

### 数据层

- Item 实体：`color: String?` → `colors: String? = null`，存储 JSON 数组如 `["粉色","白色"]`
- 复用现有 `List<String>` Gson TypeConverter
- DB migration：旧 `color` 单值转为 JSON 数组（`"粉色"` → `["粉色"]`，null 保持 null）
- 筛选逻辑：从精确匹配改为"包含任一选中颜色"

### 预定义颜色

白色 #FFFFFF、黑色 #000000、粉色 #FFB6C1、红色 #FF0000、蓝色 #4169E1、紫色 #8A2BE2、绿色 #228B22、黄色 #FFD700、米色 #F5F5DC、棕色 #8B4513、灰色 #808080、酒红 #722F37、藏蓝 #003153

用户可自定义添加颜色（输入名称 + 选取色值）。

### UI 改动

- ItemEditScreen：颜色输入框替换为 FlowRow 色块+文字选择器，点击切换选中，末尾"+"按钮添加自定义颜色
- ItemDetailScreen：颜色显示从单文本改为多个色块标签
- 筛选器：颜色筛选改为多选 Chip
