# 代码逻辑与联动修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复本轮审计确认的 9 项 P2 与 1 项 P3，保留已有资料并发布 2.47.1 / 105。

**Architecture:** 继续使用 Room → Repository → StateFlow → Compose；在错误产生的边界修正实付分摊、日期转换、初始化和图片所有权。数据库版本及备份格式不变，不批量重写已有付款日期或金额。

**Tech Stack:** Kotlin 2.1.0、Compose、Room/KSP、JUnit、Android 仪器化测试。

**Spec:** 用户已确认的本轮 10 项审计结论及“全部修复”指令。

## Global Constraints

- 保留主工作区现有未跟踪文件；只提交本次修复。
- 中文界面、皮肤组件及所有当前订单/历史购买证据不变。
- 设备测试仅使用独立 AVD，禁止卸载或清理用户设备数据。
- 单测及 Debug 构建、定向真实 Room/Compose 回归、独立代码复核后，clean 正式构建并验证同签名覆盖升级与双平台分发。

## Review Focus

- 未收货已付订单、关闭或退款订单的源实付证据不得被状态静默归零。
- 上海月初零点、西时区和夏令时日期框往返保持所选本地日期。
- 已有/新建编辑器重复加载保留草稿，失败可重试、换实体可重新加载。
- 图片放弃、移除、替换、跳过与保存后清理不得误删持久引用或旧图片。
- 当前备份特殊颜色原样恢复，旧格式迁移仍兼容；统计下钻精确到标签或季节 token。

## Task 1: 淘宝订单实付与规格解析

Files: TaobaoImportViewModel.kt、TaobaoOrderParser.kt、ImportDetailScreen.kt；新增或扩展原始订单导入与解析测试。

- [x] 先复现“卖家已发货/实付110/商品100/邮费10”导入为零实付、JSK连写及前缀尺码问题。
- [x] 始终保留源实付证据；规范化类别并按最长类型优先提取、在类型前处理尺码。
- [x] 跑测试确认实付110/待付0、原数量分摊与配对逻辑不回退。

## Task 2: 编辑器草稿、图片生命周期与穿搭取消关联

Files: ItemViewModel.kt、CoordinateViewModel.kt、OutfitLogViewModel.kt、相应编辑屏幕及图片相关 Repository；淘宝导入图片由 Task 1 负责。

- [x] 用真实 Room 复现重复加载覆盖已有/新建草稿；验证已选非 OWNED 仍可移除。
- [x] 初始化按实体身份幂等，读取失败可重试；跟踪本次新增图片，放弃/移除/保存后走引用保护清理。
- [x] 测试保存保全旧字段、原照片、创建时间；清理不误删已保存及共享图片。

## Task 3: 日期、备份、统计筛选与年历时钟

Files: PricingComponents.kt、ImportDetailScreen.kt日期框（与Task 1按代码区块分工）、BackupFormat.kt、ItemDao.kt、PaymentCalendarScreen.kt；新增日期转换领域工具及相应回归。

- [x] 先复现当前格式括号颜色变值、精确标签混入包含名称、日期跨月、年历时钟冻结。
- [x] 日期框统一 LocalDate↔UTC 日首边界，保存本地日首；现代备份颜色保持原样，旧格式显式迁移。
- [x] 风格精确匹配、季节按逗号 token 匹配；年历统一使用可更新时钟及前台/时间变更信号。
- [x] 核验月初/时区/夏令时、括号颜色及旧备份、标签特殊字符、推进时钟后的月年逾期一致。

## Task 4: 桌面入口、集成与发布

Files: OutfitWidget.kt、MainActivity.kt或共用导航 Intent 工具与测试；AGENTS.md、app/build.gradle.kts、发布记录。

- [x] 桌面组件复用快捷穿搭 Intent，验证冷/热启动消费且恢复不重复。
- [x] 全部任务独立复核、修正复核发现后，运行完整 JVM 与定向设备套件。
- [x] 更新至2.47.1 / 105，刷新约定与10项覆盖记录，精确暂存并提交。
- [ ] clean assembleRelease；同签名覆盖2.47.0并逐表/图片/设置核对。
- [ ] 推送 master/源码标签；GitHub、Gitee附件匿名下载摘要和公开更新入口核验；提交发布证据。
