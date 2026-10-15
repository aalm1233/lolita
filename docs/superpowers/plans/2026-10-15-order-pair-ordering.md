# 订单导入定金尾款相邻排序实施计划

> For agentic workers: 使用 superpowers:executing-plans 在当前会话内实施，并在完成后进行独立代码复核。

**Goal:** 已识别的有效配对在逐件和批量中相邻展示，顺序为定金、对应尾款。
**Architecture:** `importDisplayOrder(items: List<ImportItemState>): List<Int>` 仅生成展示索引，不重排 ViewModel 原数组。配对组放在两者最早的原位置，其他记录保持原相对顺序。导航按展示位置，草稿/回调/配对编号按原始索引；动态配对保留当前记录。
**Tech Stack:** 现有 Kotlin 2.1.0、Compose BOM 2024.12.01、Material3。
**Spec:** 用户本轮要求“把识别到的定尾放到一起”；本会话交互修正说明即规格。

## Global Constraints

- 只有双向、异角色、范围合法的定金尾款配对可以成组；不凭排序建立配对。
- 逐件、批量、筛选后的相对顺序共用展示索引。金额、来源合计、导入业务及原始数组不改变。
- 遵循已有草稿、IME、七皮肤及小屏可达约定；代码改动完成正常双平台正式发布。

## Review Focus

- 原表尾款早于定金，默认第一件仍应为定金。
- 更换、解除或角色修正时当前商品不跳转；导航方向根据展示方向。
- 筛选只限制显示，不能更改原始索引或建立隐藏配对。
- 越界、自指、单向、同角色配对不遗漏/重复记录。
- 下一件说明、计数、装饰剩余数量均按展示顺序，编号与配对提示保持一致。

### Task 1: 展示索引与真实导航

**Files:** 创建 ImportDisplayOrder.kt、ImportDisplayOrderTest.kt；修改 ImportDetailScreen.kt、ImportFloatingComponents.kt、OrderImportInteractionTest.kt。
**Interfaces:** 产生 `importDisplayOrder(List<ImportItemState>): List<Int>`；页面仅消费它映射原记录。

- [x] 先写真实 Compose 回归，复现非相邻、尾款在前的配对导航错误；运行目标测试确认失败。
- [x] 写纯排序测试：空/无配对、多组/倒序、非法配对、输入不变、配对更换/解除、数据草稿无影响。运行确认 helper 缺失。
- [x] 实现 O(n) 展示索引及页面映射；保持当前原索引，下一件提示明确原编号。按展示位置切换，批量筛选同源。
- [x] 运行 assembleDebug、全部 JVM 和 OrderImportInteractionTest；确认零失败；复核后补 AGENTS.md 和实际截图。

### Task 2: 正式发布

- [x] 升至 2.46.3 / 102，提交正式源码，合入 master。
- [x] 从源码 clean assembleRelease，核对原签名/版本/非调试；独立 AVD 从2.46.2同签名覆盖升级，逐字段/图片/设置保全。
- [x] 标签指向源码，双平台发布 APK/SHA256SUMS，匿名下载、HEAD、最高正式更新及源码标签验证；提交验收记录。

全部完成，正式源码1b73aa4，发布证据见 `docs/release-2.46.3.md`。
