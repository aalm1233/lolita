# 删除后的迟到草稿保存修复

对应 `final-branch-review.md` 唯一剩余 Important。本轮为纯持久化与编辑状态保护，复用现有读取失败提示，不新增 UI、字段、表、图片策略或付费调用；未触碰根代理未提交的集成文件。

## 确定回归

新增 `ItemDraftDeletionRaceTest`，独立内存 Room、UUID 文件目录，真实 ItemRepository / ItemDraftRepository / ItemEditViewModel 与完整 JSON 导出。

- 先保存 target=1 草稿、删除 Item 1，再提交已捕获的相同草稿：必须拒绝，不能重建孤儿；target=null 的新物品草稿仍可存；数据库能完整导出。
- 从其他入口删除，真实 items Flow 已观察到删除后再 keepDraft：关闭旧物品、草稿与 SavedState 身份，不重连不存在目标，保存被拒绝，备份有效。
- 本编辑器删除后立即 keepDraft：删除需先阻止自动保存并按 draftMutex→Room 串行；返回后不能重建草稿，物品/草稿身份清空且备份有效。

第一轮仅将 repo 的目标断言算作有效 RED；另两项在 post-delete keepDraft 的 reconnectRecognition 异常处提前停止，没有冒充断言 RED。补上明确的初始目标存在/keepDraft 成功控制，并捕获删除后调用的异常，让断言直接核验孤儿状态。

`artifacts/wardrobe-device-2026-10-18/final-5586/draft-deletion-red2/instrumentation.log`：3 项真实目标断言全部失败，0.272s；初始控制全部通过。生产源码在 RED 固定前保持旧行为。

## 最小实现

- `ItemDraftRepository.save` 在同一 Room 事务内先核验非空 targetItemId 仍存在，再进行位置规范化、来源保护和 Upsert。目标不存在抛明确的 ItemDraftTargetDeletedException；新建草稿 target=null 不受影响。
- reconnectRecognition 对同一缺失目标使用相同异常，覆盖“save 成功后、reconnect 前被删除”的窗口。
- 自动保存遇目标删除关闭编辑身份，不反复保存错误草稿；进入 draftMutex 后重新核对 loading/error/discard 状态。
- keepDraft 遇目标删除关闭身份并正常退出；真实 items Flow 观察到当前物品不存在时，清空 item/draftId/SavedState、停止自动保存并复用读取失败提示。
- 本编辑器删除先占 isSaving 并关闭自动保存，再按 draftMutex→Room 执行删除；删除成功关闭编辑身份，正常失败则恢复原有效编辑状态。删除的已接受数据库操作不因页面等待取消而中断。
- 正常保存遇相同缺失异常同样关闭身份。备份对孤儿 target 的拒绝校验保持不变。

## 验证状态

storage 代理按协调窗口构建当前 Debug 主/测试 APK 并运行：3/3 真实 GREEN，0.32s。完整日志 `artifacts/wardrobe-device-2026-10-18/final-5586/historical-input-red/catalog-draft-green3-instrumentation.log`，同目录有 build.log、build-command.json、双 APK SHA、同签覆盖安装证据与源文件快照。本代理独立读完整日志，并逐字节核对 Repo、VM、测试源码与已测快照相同。

该实际运行同时完成本类的 target=null 新草稿控制、删除后正常 JSON 导出和 VM 退出控制；未把更早的 47 项、旧套件或另一次全 JVM 结果外推到本轮。根代理的新 Backup18 已实际 RED，待其修复后由 storage 统一全 JVM/双 APK 与必要生命周期/备份回归。根代理仍负责旧套件失败、最终 clean 正式构建、覆盖升级及双平台发布。本次没有真实付费调用、原工作区修改、数据库结构或备份拒绝规则变更。
