# 淘宝多文件合并导入设计

日期: 2026-02-18

## 背景

淘宝导出订单有时间范围限制，用户需要多个 xlsx 文件才能覆盖全部订单。不同时间段的导出文件之间可能有重复订单。

## 方案：一次多选文件，合并后进入现有流程

### 改动点

**1. 文件选择器：单选 → 多选**
- `ActivityResultContracts.OpenDocument()` → `OpenMultipleDocuments()`
- MIME 类型不变
- 回调从 `Uri?` 变为 `List<Uri>`

**2. TaobaoOrderParser：新增 `parseMultipleFiles()`**
- 逐个调用现有 `parse()` 解析每个文件
- 按订单号（orderId）去重，相同订单号保留第一个文件中的版本
- 返回合并后的 `List<TaobaoOrder>`

**3. ViewModel：`onFileSelected()` 适配多文件**
- 参数从 `Uri` 改为 `List<Uri>`
- 调用 `parseMultipleFiles()`
- 解析失败时提示哪个文件出错
- 后续流程（SELECT → PREPARE → DETAIL → RESULT）完全不变

### UI 提示

解析完成后在订单列表顶部显示"已解析 N 个文件，共 X 个订单（去重 Y 个）"。

### 涉及文件

- `TaobaoOrderParser.kt` — 新增 parseMultipleFiles()
- `TaobaoImportViewModel.kt` — onFileSelected() 适配多文件
- `TaobaoImportScreen.kt` — 文件选择器改多选，添加解析统计提示
