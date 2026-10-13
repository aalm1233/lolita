# 淘宝商品逐件拆分导入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 一个链接勾选多个明确物品，按顺序进入现有编辑页逐件核对保存，每件保留全部详情原图。

**Architecture:** Parser 提取明确款式/套装候选，不按颜色和尺码展开。导入 ViewModel 维护可恢复的进度，下载原图一次，每次进入编辑前复制到独立 UUID 文件；每件编辑器独立拥有图片。保存后返回导入页自动继续下一件，放弃编辑暂停，可继续当前件或退出剩余；已保存件不回滚。

**Tech Stack:** Kotlin、Compose、StateFlow、SavedStateHandle、Room26（新增可空导入幂等标识） 与 ImageFileHelper。

**Spec:** 用户已选择“勾选后逐件核对保存”；所有拆分物品保留详情。

## Global Constraints

- Room25→26新增Item可空唯一import_token，以识别已提交但未回传的编辑；旧记录默认NULL，JSON备份自动包含且旧备份容错。隐藏幂等标识不作为业务CSV列。现有多图继续参与备份、删除和恢复。
- 沿用皮肤组件、中文文案和现有编辑校验；默认心愿、不生成价格或付款。
- 详情最多50张，单图20MiB，总下载80MiB；候选最多30个。
- 2.45.0 / 96 新功能正式发布，两平台同包，凭据仅本机仓库外。

## Review Focus

- 颜色、尺码或所有SKU组合不得被误当成独立物品。
- 重复点击、旧编辑结果或重组不得重复推进和新增记录。
- 第一件保存后放弃第二件不得删除第一件图片。
- 下载、复制失败/取消必须清理尚未转交编辑器的文件。
- 进程恢复保留进度；缺失缓存明确报错，不静默重复创建。

### Task 1: 候选解析

- [x] 新增 TaobaoProductVariant(id,name,category)，草稿 variants/importToken/editorToken/batchIndex/batchTotal。
- [x] 从现代 skuData.skuV3Data 内明确“套装/款式/类型”属性提取候选，参数套装为降级；排除颜色/尺码。
- [x] 分类按候选独立推断，不用商品标题给配件错误分类。
- [x] 先补失败回归，再实现，覆盖6候选、单件、错误SKU、去重、上限。

### Task 2: 下载与可恢复进度

- [x] TaobaoProductViewModel(app,SavedStateHandle) 维护选择、缓存、当前token/index、已保存数。
- [x] edit(onReady) 开始/继续批次，复制当前件全部图片后转交；editorReturned(result,onReady) 校验token，保存则自动下一件，放弃则暂停。
- [x] 缓存原图从不直接写入Item，批次完成/退出只清理缓存；每件复制图片沿用编辑器所有权。
- [x] 验证进度恢复、重复事件、暂停、全成功和失败清理。

### Task 3: UI与导航

- [x] 预览显示物品勾选列表，单件维持原入口；空选择禁用提交。
- [x] ItemEdit保存/返回回传token结果；编辑页显示第几件/总件数。
- [x] 导入页面自动继续，暂停支持继续，完成显示已保存数量。
- [x] 实测勾选2件、顺序保存、各28详情、放弃/重启/删除一件保全另一件。

### Task 4: 验证与发布

- [x] 构建与适当回归、审阅、版本约定更新、提交推送。
- [x] 从正式源码clean Release、同签名覆盖验证、GitHub/Gitee发布与匿名摘要/更新协议检查。

## 审阅后补充

保存提交后、结果回传前被关闭的编辑必须查询持久化import_token，并恢复已保存结果；重复提交同token返回原记录。编辑器清理改为引用保护，防止提交后取消损坏已保存图片。SavedState只保存一份公共详情与紧凑队列，避免30件重复正文超过Android状态大小限制。
