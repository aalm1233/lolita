# 完整衣橱第一期窄范围最终复审

复审日期：2026-10-18。当前已提交 HEAD `17912a5`，包含 `7d24a87`、`4be3469`、`d615582`、`19437d1`，并检查当前 working tree 的 TryOn v4 门槛及 Room/备份/协调器集成。未修改产品源码、index 或分支，未运行 Gradle/AVD、读取凭据或付费调用。

## 仍存在的 Important

### 迟到的编辑草稿保存能在物品删除后重新创建孤儿草稿，继而阻断备份

主要位置：`app/src/main/java/com/lolita/app/data/repository/ItemDraftRepository.kt:28`（事务内仅校验草稿内容及规范化位置，`:38` 无条件 Upsert）。真实入口：`app/src/main/java/com/lolita/app/ui/screen/item/ItemViewModel.kt:837` 的自动保存、`:908` 的 keepDraft，以及 `:1698` 的删除操作。

现在 `ItemRepository.deleteItem` 会正确删掉相应草稿和识图记录，但 `ItemDraftRepository.save` 没有在同一事务内验证非空 `targetItemId` 仍然存在。删除完成后，一个已经捕获的自动保存仍可以把旧草稿重新插入。编辑器自己的 `deleteItem` 也没有像 discardDraft 那样先关闭自动保存、取得 draftMutex 或清掉草稿身份；删除导致 observeHierarchy 更新 StateFlow 时，仍可能满足 hasUnsavedChanges 的自动保存条件。

最小确定顺序（真实 Room 即可验证，不需要 UI、图片或网络）：

```kotlin
drafts.save(draftWithTargetItem1)
items.deleteItem(item1)
check(database.itemDraftDao().get(draftWithTargetItem1.id) == null)
drafts.save(draftWithTargetItem1) // 模拟已捕获、删除后才到达的自动保存
```

按当前源码，最后一步成功重新插入 `targetItemId=1` 的草稿，而 Item 1 已不存在。新加的 `WardrobeBackupValidation` 在 targets 存在性检查中拒绝它，导致后续完整 JSON/ZIP 备份整体失败。现有 `RecognitionDeletionTest` 只覆盖删除事务本身，没有覆盖删除后的迟到 save。

建议最小修复：`ItemDraftRepository.save` 在持有同一 Room 事务时拒绝不存在的非空目标；编辑器自身删除同时关闭自动保存并串行处理草稿身份。回归覆盖“删除后迟到 save 不得重建草稿”，并确认数据库仍可完整导出。备份的孤儿拒绝应继续保留。

证据范围：只读静态调用链及确定的顺序复现说明；本轮未执行新的 Room 测试。已直接通知 root。

## 原四项复核结果

| 原问题 | 当前复核结论 |
| --- | --- |
| 收纳移动/删除遗漏持久草稿 | 已解决。moveNode/deleteNode/deleteRoom 在原图事务内同步草稿位置，保留其他字段；ItemDraftRepository.save 再规范化已有节点和房间，防止陈旧自动保存复活已删除引用。 |
| known semantic 改 MAIN 绕过基础服装门槛 | 已解决。v4 除角色外要求已确认语义属于 CLOTHING 且不是 PETTICOAT；null/UNCONFIRMED 和旧协议保留兼容。TryOnFoundationSemanticTest 覆盖角色修改及兼容对照。 |
| 陈旧草稿覆盖后来的图片/位置，封面操作洗基线 | 已解决。编辑基线独立保存，不随实时 Item 身份刷新；恢复与 commit 均核对，commit 在同事务内检查。封面操作只在原基线及其他字段未变化时推进基线。 |
| 旧版生成预览不保存 AI 来源 | 已解决。appendGeneratedImage 在相册追加的同一事务写 AI_GENERATED/legacy_display，来源查询能继续限制为外观建议。 |

## 附加核对

- Storage 的四类 Item 位置 SQL 均用 `MAX(updated_at + 1, now)` 推进编辑基线，含时钟落后场景；没有通过移动后补写相同时间放行旧草稿。
- 备份对非空 ItemDraft.targetItemId 的不存在目标已拒绝，且校验在恢复提交前。此拒绝正确，但还需上面的写入端保护，避免应用自己制造这种状态。
- WardrobeCategoryLibrary 使用进程 mutex、事务插入和持久初始化标记；删除/编辑入口及生产 BackupManager 初始化屏障均等待 marker。既有同名历史分类不静默改语义，删除/改名不再重补。
- sourceResourcesMissing 在 executor 读取凭据前和 coordinator 执行前均被阻止；无图 JSON 保留原快照并标缺失，活动恢复转中断；重试入口及重建页不会悄悄套用当前模板。
- `d615582` 只扩大 DeepSeek 已观测的惰性响应元数据白名单并限制其类型/常量；依然要求单条 assistant/output_text、完成状态、无工具输出、严格字段协议，未见因此重新开放操作型输出或自动重试。中文提示调整未改变字段 allowlist。

## 验证限度与结论

已只读查看现有 `room43-instrumentation.log`（OK 43）及 `catalog-final19/instrumentation.log`（OK 19），其中包含草稿基线、封面基线、legacy 来源、收纳草稿和初始化定向回归；现有 JVM 汇总记录为 386 tests、0 failures/errors。它们是已有运行证据，本轮没有重跑，也不证明上面的迟到 save 已被覆盖；未将更早 UI 失败日志报告成当前仍存在的问题。

本次只对请求的修复与相关保护分支作窄范围复审，未重新审全部第一期视觉、真实质量、正式包升级或发布。

**结论：原四项已解决；仍需修复上述迟到 save 的目标身份检查并做定向 Room 回归后再合入。**


## 根代理后续处理（原报告保留）

唯一剩余的迟到草稿问题已由fec65b3修复：ItemDraftRepository.save同事务拒绝已删除target；编辑器删除串行关闭autosave身份。ItemDraftDeletionRaceTest真实RED3→GREEN3，最后114包同源53项设备（含此3项）全部通过，390项JVM通过。上述Important不再是当前未解决项。最终证据：artifacts/wardrobe-device-2026-10-18/final-5586/legacy-final114/。
