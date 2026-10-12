# 第一轮页面视觉优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 整理穿搭编辑、价格付款和导入指南的视觉主次。
**Architecture:** 仅修改 Composable 布局与界面内展开状态，现有 ViewModel、仓库和账本规则保持。三个任务编辑不同文件，根代理统一编译、设备核对与整合。
**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, Android API 35。
**Spec:** docs/superpowers/specs/2026-10-12-visual-first-pass-design.md

## Global Constraints

- 用户认可月份的紧凑设计；不得修改 PaymentCalendarScreen、图标、首页顶栏、统计总览。
- 只修改各自任务文件，中文文案、现有七套皮肤；无新依赖、数据库、ViewModel、Repository 或版本号改动。
- 无 Git、提交、发布、构建或 adb 操作；根代理统一验证。保留所有既有状态判断、保存、日期、照片、付款和提醒业务。
- 不为可逆的低影响视觉改动编写镜像实现的测试。现有真实 Room/Compose 回归和真实页面截图用于验证；如有确切行为回归才补有意义的测试。

## Review Focus

- 小屏和大字体：备注、长名称和金额不截断。
- 编辑原穿搭：照片、日期和关联保持，取消不保存。
- 未跟踪账目：未知余额/日期和只读边界不被折叠隐藏，付款/阶段按钮权限保持。
- 多笔记录：计划和实付同 ID 也有独立 key/语义，长清单能滚动。
- 七套皮肤暗色：标题、分隔、正文层次可读。

### Task 1: 穿搭编辑

**Files:** Modify `app/src/main/java/com/lolita/app/ui/screen/outfit/OutfitLogEditScreen.kt`
**Interfaces:** 继续使用 OutfitLogEditViewModel.uiState、updateDate/updateNote/addImage/removeImage/toggleItemSelection/save；不修改 ViewModel。

- [x] 阅读界面、ViewModel 和 QuickOutfitLogScreen 的现有保存/皮肤布局。
- [x] 日期、备注和关联服饰通栏，只有照片采用两列；去除卡片内部第二层 padding。日期紧凑，照片使用皮肤圆角，备注可舒展输入。
- [x] 底部明确“保存穿搭”操作，键盘开启可达；继续使用原保存回调与有效性限制，避免保存中重复触发。顶部保存若保留则为次级操作。原图、日期、关联和放弃更改对话框保留。
- [x] 自查小屏/大字体、照片删除与选择操作；写任务报告，列出可供根代理截图的路径和需要验证的边界。

### Task 2: 价格付款

**Files:** Modify `app/src/main/java/com/lolita/app/ui/screen/price/PriceManageScreen.kt`; `app/src/main/java/com/lolita/app/ui/screen/price/PaymentManageScreen.kt`
**Interfaces:** PriceManageViewModel / PaymentManageViewModel、PurchaseRecordNotice 和原回调/权限判断保持兼容；保留现有 testTag。

- [x] 阅读两屏、PricingComponents 与已有 PriceManageScreenReviewTest / PaymentManageScreenTest。
- [x] 当前订单摘要优先展示状态和金额；详细解释用局部 rememberSaveable 展开，关键历史状态、只读和未知余额提示始终可见。主要实付操作突出，核对/归档/解除等次级操作保留，确认对话框与按钮条件不得改变。
- [x] 付款计划与实际付款各自组成紧凑记录组；金额、日期、状态有固定阅读顺序，弱化每条记录的重复标题/外框。编辑、撤销、新增阶段和全部旧未付记录依旧可达，使用相互独立 stage/payment key 和原 testTag。若按记录展开，摘要必须始终展示金额、日期未知和只读状态。
- [x] 保持超付、计划超额、未知付款日期等警示和历史付款语义；保留金额精度与提醒保存权限流程。
- [x] 自查按钮/数据/语义保全，写报告，列出根代理应验证的状态。

### Task 3: 导入指南

**Files:** Modify `app/src/main/java/com/lolita/app/ui/screen/import/TaobaoImportGuideScreen.kt`
**Interfaces:** TaobaoImportGuideScreen(onBack: () -> Unit) 保持。

- [x] 阅读现有七步和导入页文件格式文案。
- [x] 删除 ScreenshotPlaceholder 和无效绘图 imports，保留现有功能说明、现有步骤和注意事项；按“获取订单文件 / 回到应用导入”分组，序号仅用于真实步骤。
- [x] 用 LolitaSection 或 LolitaCard + SectionHeader、皮肤间距与圆角呈现紧凑文字；不虚构截图，不新增未经验证的淘宝导出能力或入口承诺。
- [x] 自查内容完整和深浅色，写报告；低影响文字布局不增加机械测试。

### Task 4: 根代理验证与整合

- [x] assembleDebug testDebugUnitTest assembleDebugAndroidTest；设备使用本轮独立 AVD，禁止卸载绕过签名冲突，不操作主 5554 及已有 5556/5558。
- [x] 运行既有 PriceManageScreenReviewTest、PaymentManageScreenTest、PurchaseReviewConsistencyTest、QuickOutfitLogViewModelTest 与账目回归；视觉截图含穿搭编辑、正常/历史付款、导入指南和至少甜美浅色/维多利亚暗色。
- [x] 独立代理审查三个任务的规格与质量，修复实际发现的问题后复验。
- [x] 更新 AGENTS.md 的相关页面约定和验证记录，保留实拍；把本轮改动同步回用户工作区、保留无关文件。不在本轮发布或修改版本。

## 发布收尾（用户后续授权）

用户在第一轮完成后明确要求全部收尾提交发布，此节覆盖此前本轮不发布的约束。

- [x] 以真实 Room 测试复现完整穿搭编辑重写创建时间，再做最小修复；保留日期、照片与关联。
- [x] 版本升至 2.40.3 / 88，完成单测、定向设备回归与独立审查。
- [x] 最终源码 clean 构建签名 Release，核对与 2.40.2 签名一致并验证覆盖升级。
- 发布操作：仅整合本轮文件，保留用户无关改动；提交、推送 master 和 v2.40.3，创建正式 Release，校验线上 APK 摘要。执行结果以 GitHub Release 的线上状态为最终凭据。
