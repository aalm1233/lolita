# 搭配、分享与预算 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 主件搭配可锁定替换并保存，套装和日记可分享图片，月度预算连接真实付款。
**Architecture:** Kotlin/Compose + Room + AppModule。搭配与预算计算放 domain/usecase，状态用 StateFlow；分享生成独立缓存 PNG 并经 FileProvider 系统分享。
**Tech Stack:** Existing Kotlin 2.1 / Compose / Room KSP / Coil.
**Spec:** 用户选择三项功能并回复 go，按已提出范围直接实施。

## Global Constraints
中文 UI，七套皮肤组件，不清用户设备数据，不提交用户既有改动。新月预算实体必须迁移、导出、导入、预览及 CSV 支持。金额以分计算。正式发布不在本轮范围。

## Review Focus
- 服饰已归属套装：保存前显式确认移入，事务内核对快照。
- JSK/SK 可补上衣，OP 不自动补上衣；自定义分类不被全组排除。
- 月边界和付款日期：实付按 paidDate，阶段只算剩余，历史和未知日期金额单列。
- 分享无照片、长中文名字和取消：可预览、可失败重试、不可泄露备注与金额。
- 旧备份缺少预算：兼容为空，新备份检查重复月份和金额。

### Task 1: 搭配
Files: new FullLookEngine/LookBuilderViewModel/LookBuilderScreen; route replaces old recommendation entry; new transactional LookRepository.
- [x] 写规则测试并确认 RED：角色互补、已拥有过滤、自定义属性、锁定/替换稳定性。
- [x] 实现候选按角色排序，主件固定，单角色手动选择/跳过，可换一套，中文推荐原因。
- [x] 实现套装保存，验证记录尚存在和归属未变化；展示移入确认，保存成功导航详情。
- [x] 单元与真实内存 Room 保存测试 GREEN。

### Task 2: 分享
Files: ShareCardContent/ShareCardRenderer/ShareCardDialog + detail buttons.
- [x] 先写隐私与渲染测试：只含标题图片服饰品牌，不传原备注价格位置。
- [x] 生成皮肤颜色的 PNG，完整 Fit 图片，长文本换行，缺图回退；缓存按 UUID 写入。
- [x] 预览后系统分享，可关闭品牌；加载或实体缺失时禁止入口，异常可重试。
- [x] Android 渲染与入口测试 GREEN。

### Task 3: 预算
Files: MonthlyBudget/BudgetDao/BudgetRepository/BudgetAccounting/BudgetScreen; database/AppModule/BackupFormat/BackupManager/StatsPage.
- [x] RED 金额测试：已付日期、部分实付、跨月、无日期、超支、输入两位小数。
- [x] 新表 month TEXT PK + amountCents INTEGER，数据库 24，预算每月可设置/删除。
- [x] 展示当月实付、剩余待付、预计余额及付款明细，未设置预算与超支状态；隐私设置实时生效。
- [x] format 6 支持预算与旧备份，CSV 和预览补齐。
- [x] 单元、迁移、备份往返和真实 Compose 回归 GREEN。

### Task 4: 集成与交付
- [x] version 2.43.0 / 93 和 AGENTS 项目事实。
- [x] assembleDebug、testDebugUnitTest、独立 AVD 定向 Android 测试和页面截图。
- [x] 独立代码审阅，修复影响正确性的发现。
- [x] 仅提交本轮差异，集成 master，普通 push 并核对远端包含提交。
