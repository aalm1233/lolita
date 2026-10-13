# 淘宝款式报价修复 Implementation Plan

> **For agentic workers:** 使用 superpowers:executing-plans 逐项实施，继续复用当前隔离工作树。

**Goal:** 守望者男款 SET 正确读取 658 元，未知款式不继承页面 449 起，并明确录价入口。

**Architecture:** 从现代 SKU 的 selectedPath / simpleSkuHead 获取带规格的报价，打开规格面板时优先使用实时 DOM 的选中名称与报价。报价作为可核对来源文本保存；款式字段与紧凑恢复队列保留各自报价。价格页主动采用参考报价后由用户保存，保持现有订单账本与付款规则。

**Tech Stack:** Kotlin、Gson、Compose、Room 26、WebView。

**Spec:** 用户反馈男款 SET=658、询问录价入口；复用现有逐件勾选保存及默认正式发布授权。

## Global Constraints
- 不创建实付，不把起价、区间价或定金推断为全款。
- 未关联明确款式的报价不分配给拆分物品；不按颜色/尺码展开。
- 仅保存来源文字，数据库和备份格式保持兼容。
- 正式同签名 2.45.1 / 97，clean release，GitHub/Gitee 同步。

## Review Focus
- 初始规格报价 658 与页面起价 449 并存，男款只得到 658。
- 实时规格切换后不能把新价格贴到旧的款式。
- 区间、起价、定金、无法确认的组合保持待核对。
- 保存状态恢复后，各拆分物品价格保持独立。
- 使用报价之前/取消录价不产生订单或付款，已有价格不得覆盖。

### Task 1: 报价与拆分
Files: TaobaoProductParser.kt、新 TaobaoProductQuote.kt、TaobaoProductBatch.kt、对应 JVM tests。
- [x] 编写失败回归：真实脱敏 SKU 男款 658、OP 待核对，页面 449 不传播；实时规格优先；范围/定金拒绝。
- [x] 运行失败回归。
- [x] 添加 Draft/Variant 的 referenceQuote；解析规格与价格，描述只附当前物品报价。
- [x] 紧凑队列保留 referenceQuote 并恢复正确描述，测试通过。

### Task 2: 核对与录价
Files: TaobaoProductScreen.kt、TaobaoProductViewModel.kt、PriceEditScreen.kt、PriceViewModel.kt、ItemDetailScreen.kt。
- [x] 提取加入实时可见 SKU 快照，预览每件提供参考金额输入；无效金额禁止开始。
- [x] 新价格页读取明确来源报价，提供“使用参考报价”按钮；仅填商品金额和全款阶段，实际保存沿用账本。
- [x] 详情无订单入口显示“录入价格”；设备回归验证未保存无订单、采用 658、保存阶段正确。

### Task 3: 正式发布
- [x] assembleDebug + 完整 JVM + 定向独立设备验证。
- [x] 提交推送，clean assembleRelease；检查签名与覆盖升级数据保全。
- [x] 发布 GitHub/Gitee 及附件、匿名下载哈希验证；GitHub最新正式API通过，Gitee匿名更新API实测403限流，记录外部限制。
- [x] 文档记录与工作区保全核对。
