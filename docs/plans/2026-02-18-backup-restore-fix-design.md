# 备份恢复功能修复设计

日期: 2026-02-18

## 问题

备份导出后卸载重装 app，导入恢复时显示"恢复完成"但数据实际为空。

### 根因

1. **导入未清空现有数据：** 重装后 `DatabaseCallback.onCreate()` 自动插入预设数据（201品牌、12分类、6风格、5季节）。导入时所有 DAO 使用 `OnConflictStrategy.ABORT`，预设数据 ID 冲突导致 `SQLiteConstraintException`，ABORT 策略在 Room 事务中可能导致整个事务静默回滚。
2. **计数器误导：** `imported` 计数器是内存变量，即使事务回滚仍显示成功数字。
3. **图片未备份：** 图片存储在 `filesDir/images/`，卸载时被系统删除，备份仅含数据库记录。

## 方案：清空导入 + 图片备份

### 备份格式

从纯 JSON 改为 ZIP：

```
lolita_backup_20260218_120000.zip
├── data.json       ← BackupData JSON
└── images/         ← 引用的图片文件
    ├── uuid1.jpg
    └── ...
```

向后兼容：导入时自动检测文件类型，ZIP 和旧 JSON 格式均支持。

### 导出流程 (`exportToZip()`)

1. 事务中收集所有数据库数据
2. 扫描实体中的图片路径，收集去重文件列表（Item.imageUrl, Item.sizeChartImageUrl, Coordinate.imageUrl, OutfitLog.imageUrls）
3. 创建 ZIP 写入 Downloads：写入 `data.json` + 复制存在的图片到 `images/`
4. 不存在的图片静默跳过
5. 保留现有 `exportToJson()` 和 `exportToCsv()` 不变

### 导入流程（改造 `importFromJson()`）

1. 检测文件类型（ZIP magic bytes `PK` 开头 vs JSON）
2. ZIP：解压 `data.json` 解析，解压 `images/` 到 `filesDir/images/`
3. JSON：直接解析（兼容旧格式）
4. **清空数据库**（事务内，按外键依赖倒序）：
   - `outfit_item_cross_refs` → `outfit_logs` → `payments` → `prices` → `items` → `coordinates` → `brands` → `categories` → `styles` → `seasons`
5. **插入备份数据**（事务内，按外键依赖正序）
6. **图片路径重映射：** 替换实体中旧路径前缀为当前 `filesDir/images/`
7. **导入后验证：** 查询 items 表行数与备份 itemCount 对比，不匹配则报错
8. 重新调度付款提醒

### UI 变更

- 导出区域新增"ZIP备份（含图片）"按钮
- 文件选择器 MIME 类型扩展：`application/json` + `application/zip`
- `BackupPreview` 新增 `imageCount` 字段
- 确认对话框增加警告："将清空当前所有数据并替换为备份数据"

### 错误处理

- 导入后验证数据实际写入，验证失败返回明确错误
- ZIP 解压/图片还原失败不中断数据库导入，仅在结果中提示
- 图片路径重映射失败的记录保留原路径（图片不显示但数据不丢失）

### 涉及文件

- `BackupManager.kt` — 核心改造：新增 exportToZip()，改造 importFromJson()，扩展 previewBackup()
- `BackupRestoreScreen.kt` — UI：新增 ZIP 导出按钮，扩展文件选择器，更新确认对话框
- `BackupPreview` / `ImportSummary` — 数据类扩展
