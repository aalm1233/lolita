# Front Mannequin Image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 裙子详情生成正面人台预览，人工确认后追加图片。
**Architecture:** 复用现有图像 API/Key/归一化与共享生成锁；新增独立人台页与 VM；Room 事务只追加最新图片字段。
**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, Room/KSP, existing coroutines.
**Spec:** docs/superpowers/specs/2026-10-15-mannequin-front-design.md

## Global Constraints

- 仅正面；个人 API Key 不进入日志/Room/备份/SavedState，零自动付费重试。
- 七皮肤中文界面；原图/顺序/账本/归属不改变；schema27/backup7 不变。
- 1 张已存在 Item 图片 + 固定模板；补充说明 <=200字。
- 2.49.0/107 正式同签名完整发布，独立 AVD 不操作用户设备。
- 本轮清晰请求已授权条件性实施。实现选择由代理按既有架构决定，继续执行无需重复审批。

## Review Focus

- 生成返回前取消：落盘未交付图片必须清理，锁释放。
- 生成后记录并发编辑或删除：只追加到最新记录，不复活被删除服饰。
- 保存提交后回传取消/onCleared：不误删已引用图、重试幂等。
- 进程恢复预览路径：只接受本应用自有 images 文件，Item 身份不串。
- 全局生成与本地预览：既有试衣入口和底栏/快捷导航都不绕过付费锁。

### Task 1: Generation and atomic image persistence
Files: domain/model/MannequinRequest.kt; data/mannequin/MannequinGenerator.kt; data/tryon/ImageGenerationGate.kt; TryOnGenerator.kt; TryOnImages.kt; ItemRepository.kt; ItemDao.kt; AppModule.kt; JVM + Android data tests.
Interfaces: supportsMannequin(Category?):Boolean; MannequinRequest(itemId,imagePath,instructions).prompt():String; MannequinGenerator.generate(request,onPhase:(TryOnGenerationPhase)->Unit):String; isGenerating StateFlow<Boolean>; ItemRepository.appendGeneratedImage(itemId:Long,path:String).
- [ ] 写资格/长度/来源过期、共享锁、取消和落盘交付失败红测；事务测试断言并发字段/顺序保全、幂等、删除拒绝。
- [ ] 运行红测观察缺失功能失败，再实现最小管线和字段更新。
- [ ] 运行 JVM 全套；Android 真实 Room 测试交由统一设备验证。向 UI 任务提交准确接口与代码审阅。

### Task 2: Item detail entry and preview/save workflow
Files: ui/screen/mannequin/MannequinViewModel.kt / MannequinScreen.kt; common ApiKeyDialog; TryOnScreen.kt; Screen.kt; LolitaNavHost.kt; ItemDetailScreen.kt; VM/Compose tests.
Interfaces: 读取 Task1 API；Item Repository observeItem，Category flow；同一 Key callbacks；viewModel StateFlow + SavedStateHandle。
- [ ] 红测覆盖选择/补充说明/重复生成、预览恢复、保存与清理竞态及 Item 删除；Compose断言入口、预览、Key遮罩、取消/放弃确认。
- [ ] 实现独立路由、可选说明、共享配置对话框、完整预览及原子追加，生成失败明确重试。
- [ ] 验证七皮肤浅暗/大字/图片放大，底栏与快捷入口共享 gate。禁止预览未处理就再生成。
- [ ] 根代理审阅与针对发现修复。

### Task 3: Evidence, verification and release
Files: assets/mannequin/front-template.png; docs/visuals/2026-10-15-mannequin-tests/*; docs/mannequin-front-evaluation-2026-10-15.json; implementation doc; AGENTS.md; app/build.gradle.kts; release verification JSON.
- [ ] 保存3件4次样片与通用提示词/补充说明、原图ZIP条目、SHA、取舍理由；复制原模板资产保持原SHA。
- [ ] JVM 全套和 assembleDebug，独立 AVD 新功能及受影响试衣/备份/导航回归。
- [ ] 新代理完整分支审阅，修复负载关键发现再重验；核对 diff 仅本任务文件。
- [ ] 2.49.0/107 clean release；2.48.0同签名覆盖、数据库/图片保全；GitHub/Gitee发布/匿名附件与更新入口验证。
- [ ] 只提交本任务，集成 master 普通推送并核对远端，归档工作区。
