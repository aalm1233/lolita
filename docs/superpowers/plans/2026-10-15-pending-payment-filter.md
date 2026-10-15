# 服饰待付款筛选修复实施计划

> 使用 superpowers:executing-plans 内联实施，完成后独立代码复核。

**Goal:** 服饰顶部待付款入口包含 PENDING_PAYMENT 与 PENDING_BALANCE。
**Architecture:** 沿用 ItemListViewModel 的组合筛选及 Room 实时订阅；将尾款专用开关名称改为待付款开关，匹配两类状态。只修改页面筛选文案和筛选逻辑，不改变实体、状态投影、账本、备份或各服饰细分状态。
**Tech Stack:** 现有 Kotlin 2.1.0 / Compose BOM 2024.12.01 / Material3。
**Spec:** 本轮用户指出服饰“待补尾款”筛选无法筛出“待付款”。

## Global Constraints / Review Focus

- 两类待付款均包含主件和小物；OWNED/WISHED/PENDING_REVIEW不得混入。
- 搜索、品牌、分类组、季节、风格、颜色、排序及显示模式继续组合。
- 付清/撤销实付后通过原有 Room Flow 实时移除/恢复，共价小物同步。
- 顶部入口文案“待付款”，选中/取消可达；保持皮肤样式与已有状态含义。
- 原数据、金额与状态枚举不迁移；按项目默认完整发布。

### Task 1: 复现与最小修复

**Files:** ItemViewModel.kt / ItemListScreen.kt；新增 PendingPaymentFilterTest.kt。
- [x] 真实内存 Room/UUID 设置构建 ItemListViewModel 回归，先运行两类状态包含测试，确认旧实现漏筛。
- [x] 将 filterPendingBalanceOnly/togglePendingBalanceOnly/pendingBalanceOnly 重命名为待付款含义，谓词纳入两类；顶部筛选改文案。
- [x] 真实 Compose 顶部入口测试、属性/搜索组合、列表/网格/画廊，以及真实账本付清/撤销对主件和共价小物的实时更新。
- [x] assembleDebug、177项JVM、目标设备测试及相关购买资料/订单业务回归，独立复核；同步AGENTS、2.46.4/103及实拍。

### Task 2: 正式发布

- [x] 提交源码并合入master，从该源码clean assembleRelease，核对包名/版本/非调试/原正式签名。
- [x] 独立AVD从2.46.3同签名覆盖升级，29张表逐字段、图片SHA和设置保全。
- [x] 双平台正式APK与SHA256SUMS、匿名完整下载/HEAD/源码标签和最高正式更新验证，提交发布证据并推送。

全部完成，正式源码63ebf65，发布验收见 `docs/release-2.46.4.md`。
