# 最终备份图片边界独立复核

## 范围与结论

只读检查根代理最新 `BackupManager.deduplicatedInputPaths` / `buildImageArchiveNames`、`sourceResourcesMissing` 历史输入再导出、ZIP 路径重写和 `WardrobeBackupTest` 第18项。没有改产品代码、运行构建、启动设备或调用付费服务。

发现1项 Important；根代理已接受并新增第19项独立 Room 回归，当前记录时尚待该项真实 RED→最小修复→GREEN。其余本次限定范围没有发现确定缺陷，不将只读检查表述为整份备份实现验收通过。

审阅快照：`BackupManager.kt` SHA256 `3a1072d3aa43ea4d2662b29bdfe565e5cd0a0dc67ec87b83dd841762bc641a68`；`WardrobeBackupTest.kt` SHA256 `cc6c4ca6791a84215f0787f3397df9d08a0e65f12e89647e7614edca961b79af`。此快照已含新增第19项测试，产品 helper 仍为修复前行为。

## Important：旧已完成结果会把已阻止的缺失输入重新变成必需资源

位置：`app/src/main/java/com/lolita/app/data/file/BackupManager.kt:838-839`。

`historicalInputPaths` 将所有任务的 `resultImagePath` 都放进必需路径，从显式标为 `sourceResourcesMissing` 的历史输入豁免集合中扣除。但 `ImageTask.protectedImagePaths` 只保护待确认任务的输出；`mapImagePaths:912-915` 也明确已完成结果仅为信息性路径。

合法触发过程：旧任务A完成并保留结果路径R；R随后成为任务B的冻结输入且已没有业务图片引用。通过纯JSON恢复后，R缺失使B显式标为 `sourceResourcesMissing=true`。A的信息性 `resultImagePath=R` 仍使R被当作必需文件，导致其余有效资料无法再次导出ZIP。现象由集合运算和调用链直接确认，本代理没有冒充已实际运行此复现。

建议：历史输入豁免仍排除业务、未标记任务保护引用及真正待确认输出；不要仅因已完成任务的信息性 result 路径撤销豁免。去重集合对所有 result 的保守排除可以保留，它防止输出身份合并，不需要随此修复放宽。

回归：第19项 `informationalCompletedResultDoesNotMakeABlockedMissingInputMandatoryAgain` 保留原请求与账本，缺失输入明确标记后ZIP导出、预览、再恢复成功且缺失数仍大于0；继续保留第18项的“未标记输入不能豁免”和“缺失业务相册不能豁免”控制。

## 已核对的身份与拒绝边界

- SHA去重仅作用于冻结任务输入、识图区域和证据输入，且减去业务路径、旧任务来源和所有结果路径；业务和待确认输出仍按原路径生成独立归档UUID，不因同字节合并身份。
- 历史豁免只由已显式阻止的任务 source/product-original 产生；业务集合仍包含物品、位置、草稿、识图证据、来源记录、个人模特及其他业务资料。未标记任务的保护引用继续要求存在。
- ZIP仅允许明确的历史缺失绝对路径保持为元数据；普通引用缺条目仍拒绝。已纳入ZIP的条目保留manifest内容校验，激活时为实际条目分配本机UUID。
- ZIP预览/恢复继续计算未纳入包内的历史输入缺失数量，不将“可备份”误报为“原任务可重试”。执行器的资源缺失保护由其他回归覆盖，本轮没有重跑。
- 第18项先拒绝未标记缺失输入、再验证显式阻止后重备份，并额外删除业务相册验证不能豁免，符合本次产品边界。

## 证据边界

草稿删除竞态另在 `fec65b3` 完成3项真实 RED→GREEN；本报告不是其新的修改。storage代理的 `final-5586/legacy-final/summary.json` 已独立读取，列明52/52 GREEN，包含当时18项WardrobeBackup及本代理草稿/生命周期/基线/删除回归；它不包含本轮新增的第19项，因此不能据此关闭上面的 Important。


## 根代理后续处理（原报告保留）

第19项真实RED1于informational-history-red114/instrumentation.log，目标断言确认已完成任务信息路径仍误算必需。historicalInputPaths只额外排除AWAITING_CONFIRMATION输出后，最终114同源53设备全过（含备份19），JDK17的390项JVM及双DebugAPK构建通过。未放宽业务图、未标记输入或待确认输出完整性。上述Important已解决；正式签名升级与发布另行核验。
