# 识图生命周期修复记录

工作区：`/Users/dengtong/.codex/worktrees/complete-wardrobe/lolita`。本报告对应 `recognition-root-review.md` 的全部 important 项。API/provider 请求与密钥由 AI 代理负责，Coordinator/数据库/备份/图片引用保护由根代理负责；本任务没有真实识图请求、付费调用或发布。

## 回归先行

- `reviews/recognition-root-fix-red.log`：368 个 JVM 用例，5 个新增用例按预期失败；分别证明无引文文字、无数值的单位文字、足长冒充内长、切尺码后的旧依赖及损坏 review 预检缺失。
- `RecognitionLifecycleTest`：独立内存 Room、独立 UUID 图片目录与受控结果函数。覆盖草稿保存后的迟到结果、部分采纳后的继续调整/幂等、AI 区域错误标为 TEXT、缺失草稿的 nullable identity、锁序、失败原子回滚、复制 AI 图后删除原物品、连续 review 编辑和应用/编辑保存竞态。测试不访问外网。
- `artifacts/wardrobe-device-2026-10-18/recognition-lifecycle/red9-instrumentation.log`：9 项真实设备断言全部按预期失败，已核对当时 DEX、APK SHA 与签名；不把编译失败记作 RED。
- `reviews/recognition-root-fix-green9-deletion-red.log`：9 项生命周期实际 GREEN；新增删除用例真实 RED（10 项中仅删除一项失败）。后续删除修复在下一轮设备前 13 项中已通过。
- `reviews/category-library-red-device.log`：一次性默认类型库 3 项，其中删除/改名与恢复屏障真实 RED，重复初始化控制项通过。生产修复后的下一轮前 13 项含此 3 项均通过。

## 修复边界与实现

以下领域、仓库、VM 与 UI 已落盘并经过下述验证；末尾另列最新独立评审补充项，未验证的内容不计作完成。

1. R1 锁序：生产入口统一为 `ItemEditViewModel.applyRecognitionTask`，先占编辑状态，再取得 `draftMutex`，最后进入 Room。回调只生成并持久草稿；交易成功后才发布 UI。自动保存和正常保存都遵循相同顺序。
2. R2 保存后迟到结果：提交草稿时把保留任务的 targetItemId 连接到实际保存 ID；重新编辑按 targetItemId 接回新的草稿。原 baseline 保留，只在明确采纳时重新比较当前值；不重发、不自动应用。
3. R3 AI 来源：按真实路径查询来源，并在 ItemDraft 外部图片列 `appearanceOnlyImagePaths` 保留复制来源。来源标记进入任务快照与证据 envelope，任务移除后仍可核验；保存生成相应 ImageProvenance。不会因新物品尚无 ID 或原物品被删除而变回文字事实。
4. R4 review 编辑：ViewModel 即时接收每次编辑，独立 mutex 顺序持久化最新 review。应用先等待保存队列；不再用全局 busy 丢弃普通输入，也不按每次旧 reviewJson 回灌编辑框。
5. R5 分次与幂等：APPLIED 结果继续提供核对。仓库维护 appliedValues，只允许调整本次已采纳值；用户之后手填的不同值仍冲突。同一任务/草稿/候选使用稳定证据 ID，保存时原子替换同来源已绑定证据。
6. R6 尺码依赖：改变款式/尺码时移除不适用选择；合并先排除不适用候选，再判断当前行的真实冲突。
7. R7 损坏 review：Task.validate 调用严格 Review.decode。UI 捕获损坏任务，允许移除重建；备份预检可拒绝整个非法状态。
8. G1 数值：TEXT 必须有引文。尺寸使用显式 target/basis/unit，并验证正数、顺序范围、原文对象、原文单位；足长与鞋内长、平铺与围度、人体适配与成衣实测互不冒充。保持原单位，不做换算或平铺乘二。六项服装尺寸进入 typed clothing 分支，未知继续待确认。DEN、层数、带数值的耐热说明额外核验单位原文。
9. G2 重试：失败/中断任务只能主动复用保留区域产生新的 PREPARED 任务，重新核对费用后才发送。
10. G3 来源预览：编辑器可查看已保存证据；区域可显式保留为商品图或资料图。默认不混入相册。价格候选始终不能采纳到业务；品牌/分类仍需明确现有 ID。

## 对根代理的接线

- DB30 `item_drafts.appearance_only_image_paths TEXT NOT NULL DEFAULT '[]'`；备份默认数组、ZIP 映射、图片完整性检查与 CSV 既有规则由根代理接线。
- `RecognitionDao.deleteEvidence(id)` 用于幂等与提交替换。
- 新证据 JSON 为 `{version:1,taskId,appearanceOnly,candidate}`；`RecognitionEvidencePayload.decode` 兼容原平铺候选，`ItemEvidence.validateRecognitionEvidence()` 为预检入口。
- ImageProvenance 的合法引用包括相册与资料图，不限相册。
- Settings UI 使用 `.label/.model` 和显式 provider 的独立 key；损坏服务选择显示未选择，需要用户明确恢复。最终请求/密钥 revision 与实际模型记录由根代理 Coordinator 核对。

## 最终验证与限制

- 本代理最新 `JAVA_HOME=... ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` 成功；381 JVM、0 失败/错误，双 APK 构建成功。完整日志 `reviews/recognition-root-final-green-build.log`。
- `reviews/recognition-root-final-green-device.log` 的前 13 项（Lifecycle 9、Deletion 1、CategoryLibrary 3）全部实际通过。后续 Mannequin 旧 fixture 有两项超时（缺 v4 模板快照，已补真实 asset 注入，待再验证），随后 Nav 触及旧 AVD 5582 中间版 v30 schema 导致进程中止；不将这轮宣称为 54 项全绿。根代理已安排 fresh AVD 5586，未清理原 AVD 数据。
- 共享物理图片路径不再要求 distinct，逻辑 region.id 仍唯一、数量严格对应。2 项 JVM 用例通过；storage 代理 24 项真实 ZIP/持久化回归通过，包括两个不同 region.id 对应同 SHA 资源、恢复共享新 UUID、最后引用移除才清理。
- 最新独立评审补充已修：原始 `baselineUpdatedAt` 独立保留，不随实时 Item 更新替换；恢复旧草稿时阻止导入旧 UI，保存时在同 Room 事务再检查真实基线。差异或缺失基线时保留草稿并阻止覆盖，允许明确保留退出，或经二次确认放弃旧草稿并重载。5 项 `ItemDraftBaselineTest`（含旧人台图来源及正常保存控制）真实 RED/GREEN；重载路径亦验证保留最新图片/位置后可正常保存。
- `appendGeneratedImage` 在相同事务记录 `AI_GENERATED / legacy_display`，幂等追加与现有 AI 任务来源信息保留，`isAppearanceOnly` 真实 Room 断言通过。
- `ItemDraftRepository.save` 仅按最新节点/房间规范化 dataJson 两个位置字段，原始基线、其他业务值、路径、标识与时间保留。storage 代理同步节点/房间动作中的草稿，并以单调 updatedAt 更新实际物品，防止旧编辑保存撤销移动。
- `NewDraftStorageCommitTest` 2 项中跨房间节点的既有 ItemRepository guard 控制项通过；room-only 删除的中文提示真实 RED（`reviews/new-draft-storage-red-device.log`，2 测 1 失败），补同事务房间存在校验后已在 storage 最新 fresh5586 43 项 Room 中 GREEN。此批还包含 Baseline5、Storage8、StorageDraft4、Backup/Persistence24，43 项 0 失败（`final-5586/final45/room43-instrumentation.log`）。同轮全量 JVM 386 项、0 失败/错误/跳过，双 APK 构建成功 16s（`storage-timestamp-ui-final-green-build.log`）。本代理独立读完整输出、验证 JVM JSON，并核对新增两类测试源 SHA 与固定 APK 测试快照一致。
- 旧 Mannequin/TryOn UI fixture 均改用明确稳定语义，历史未知同名测试仍保持 UNCONFIRMED。Mannequin 主件 fixture 提供真实模板 asset 快照并核对模板+原图，配饰仍只用原图；等待根代理在 fresh5586 最终 10 类复跑，不把旧 5582 进程崩溃算通过。

## 最终视觉对照稿与流程差异

`docs/visuals/complete-wardrobe-2026-10-18/final-review.html` 覆盖 7 状态 × 7 皮肤 × 浅暗 98 幅；`skin-tokens.json` 保存真实皮肤令牌及源文件 SHA256，`design-notes.md` 说明内容与边界。支持 320/360、1/1.3/2 倍文字和模拟键盘；已在 Cua 原生 Chrome 实际查看浅色及维多利亚暗色小屏/大字/键盘，并选择全部验证 98 幅及本地素材加载。

此稿显著标注“初版实现后补充的最终视觉对照方案”，供下一轮视觉修整前审阅；没有追认初版满足前置图要求，亦未用真实测试截图冒充图稿。图稿的样例与真实 Android 截图分别归档；没有为补图调用付费模型或继续改产品视觉。


最终设备补充：storage 的 `final-5586/final45/ui3-native-viewport.log` 实际 3/3 GREEN，21.035s。本代理独立读完整输出。使用真实 540dpi / 320dp 与 fontScale2，在 Activity 创建前设置，finally 恢复设备；检查实际 Text density，原生窗口图像区域、逐行 advance、末字符与无省略。完整 Screen → Panel → 两个 VM → Room 的部分采纳、真实保存与重入证据亦通过。旧 hasVisualOverflow 的父约束宽误报已用严格实际行边界替换，未改 Panel 生产。

收尾自检追加 Cover 边界：实时 observer 只刷新 Item 对象时，表单仍可能持有旧字段；随后本页设封面不得把旧表单基线洗为新值。新增 `aLiveObservedItemAndOwnCoverActionCannotLaunderTheOldEditableBaseline` 在旧分支真实 1/1 RED（`final45/baseline-cover-red.log`），生产只补“私有基线必须等于操作前 Item.updatedAt”条件。

最终本代理重新执行全量 `testDebugUnitTest assembleDebug assembleDebugAndroidTest`：386 JVM、0 失败/错误/跳过，双 APK GREEN 10s（`reviews/recognition-cover-final-green-build.log`）。核对新旧签名、当前 versionCode113 与 fresh AVD 名称后 install -r，19 个实际设备用例全部 GREEN 1.711s：Baseline6 / Lifecycle9 / CategoryLibrary3 / Deletion1。完整日志、APK SHA、证书、源码快照位于 `artifacts/wardrobe-device-2026-10-18/final-5586/catalog-final19/`。主 APK SHA256 `2f77a10a7481138c632448e543c40a3757a3e23a13bbf6636c2334451d0405ad`；测试 APK `ba610ed03e43945208bea534196603587f37e08b4676833b9d3dadffe8e5829f`。

本代理交接时没有再改生产源码。storage 使用相同新包继续汇合 44 Room + 3 UI，后续结果由其报告，不预记通过。根代理还需最终旧十类导航套件、独立复审、正式包升级与发布验收；这些未执行工作不计入本子任务已验证结论。没有付费请求、原工作区修改、发版或清理原 AVD 数据。

实现提交：`4be3469 fix: preserve recognition drafts and review ownership`。仅提交本代理 owned 路径；根代理的 Panel 接入单独 hunk、AppModule/Application/数据库/备份/Coordinator 等仍留根代理接线。
