# 完整衣橱第一期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付七类物品的轻量登记、实际收纳、商品图局部识别、按语义生成展示图与八件完整试衣。

**Architecture:** 延续 Item → DAO → 薄 Repository → StateFlow ViewModel → Compose；稳定分类语义与有版本的类型化规格独立于显示名和购买归属。新增 Room 草稿、收纳节点、识图任务及图片来源；新请求固定实际输入，旧请求不变。所有持久资料同步覆盖备份及引用保护。

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Room/KSP, Gson, Android Keystore, OkHttp。

**Spec:** `docs/superpowers/specs/2026-10-18-complete-wardrobe-design.md`

## Global Constraints

- 用户 2026-10-18 明确要求“开始实现最新设计稿”；按第一期完整范围执行，第二、三期不混入此版本。
- 保存只要求名称和有效分类；未知品牌使用明确系统记录，未录价格不是零元。
- 七类穿戴物品；旧 CategoryGroup、Item id、parentItemId、Coordinate、订单、阶段、实付、原图与尺码保全。
- 旧分类无不可变内置标识，迁移全部 UNCONFIRMED；新建带稳定语义，历史通过明确确认升级，不按名称或宽泛正则自动重分类。
- 房间 → 柜架/收纳盒 → 层/格，最多三级完整路径；同父同名禁止；循环、超深、跨房间引用禁止。
- 最多八条单品参考，每条一张图，人物另算；超八件必须明确选择，不自动截断、不拼图。
- 识图最多五个裁切区域，只上传最终像素，无整图、EXIF、原路径；候选逐项确认，只回填对应草稿。
- 2026-10-18 11:22 用户明确禁止识图使用 OpenAI；原选型已撤回并阻止生产付费提交。用户已有火山方舟和DeepSeek，采用豆包Seed 2.1 Pro / DeepSeek Flash可选；识图密钥和生成设置继续分离。
- 现有生图服务、明确付费确认、持久任务、无自动重试保持；用户已授权本轮最多20次真实付费调用，当前0次。
- 五款现有头模候选保留“待验证”身份；每款短发/长直/长卷真实人工评审后才标记正式，不能用受控夹具冒充质量通过。
- Room 从实际 29 升至 30，备份从实际 9 升至 10；旧格式通过显式迁移补空，未知新枚举严格拒绝。
- 新 UI 中文、七皮肤、浅暗、小屏大字体；保留首页四 Tab 和底栏；独立 AVD 测试，不卸载或清用户设备。
- 默认提交推送和双平台发布已由 AGENTS 授权；只有可发布的完成范围通过验证后才能执行。

## Review Focus

1. 用户自定义类型与历史同名内置项：不得静默赋语义，重命名后 AI 规则仍稳定。
2. 异步识图期间草稿切换、删除或手动改值：迟到结果不能覆盖或串到另一件物品。
3. 柜架移动/删除与批量移动并发：事务内维持房间和节点一致，物品及账本不丢。
4. 排队后分类、图片、默认头模或人物改变：旧付费内容不可变，缺图/校验失败不替换输入。
5. 恢复旧包及新 ZIP：保全旧值、候选证据、实际模板输入和生成来源，不自动重发或应用。

---

### Task 1: 稳定品类与轻量物品登记

**Files:**
- Create: `app/src/main/java/com/lolita/app/domain/model/Wardrobe.kt`
- Create: `app/src/main/java/com/lolita/app/data/local/entity/ItemDraft.kt`
- Create: `app/src/main/java/com/lolita/app/data/local/dao/ItemDraftDao.kt`
- Create: `app/src/main/java/com/lolita/app/data/repository/ItemDraftRepository.kt`
- Modify: `data/local/entity/Category.kt`, `data/local/entity/Item.kt`, `data/repository/CategoryRepository.kt`, `data/repository/BrandRepository.kt`, `ui/screen/item/ItemViewModel.kt`, `ui/screen/item/ItemEditScreen.kt`, `ui/screen/item/ItemDetailScreen.kt`, `ui/screen/settings/CategoryManage*` (all paths below app/src/main/java/com/lolita/app/)
- Test: `app/src/test/java/com/lolita/app/domain/model/WardrobeTest.kt`, `app/src/androidTest/java/com/lolita/app/WardrobeRegistrationTest.kt`

**Interfaces:**
- Produces: `WardrobeFamily` (UNCONFIRMED,CLOTHING,WIG,HOSIERY,FOOTWEAR,ACCESSORY,BAG,HANDHELD), `CategorySemantic` stable subtypes deriving `family`, `defaultWearRole: TryOnRole`, `displayMode: WardrobeDisplayMode`.
- Produces: `Category.categorySemantic: CategorySemantic = UNCONFIRMED`, `Category.defaultWearRole: TryOnRole? = null` (explicit override); `Item.specificationsJson: String = ""`, `Item.composition: ItemComposition = UNCONFIRMED`, `Item.storageNodeId: Long? = null`.
- Produces: `WardrobeSpecifications` version 1 typed aggregate: wig, hosiery, footwear, petticoat, accessory, bag, handheld; `encode/decode` with strict known version/keys and optional raw-string values. All profiles retained when type changes.
- Produces: `ItemDraft` keyed UUID identity with targetItemId nullable, versioned editor payload plus candidate/evidence references; repository flows and transaction save/discard. SavedStateHandle retains draft identity; no secrets.
- Consumes: Task 3 `StorageNode` only for nullable Item FK; root coordinates Room wiring.

- [ ] Step 1: Add tests for full-wig renamed semantics, historical unknown, shoe `EU 37`/sock `23 至 25 cm` raw preservation, profile switch retaining both types, blank optional values and unsupported spec version rejection.
- [ ] Step 2: Run targeted test; confirm missing feature RED before production edits.
- [ ] Step 3: Implement semantic and typed profiles; category creation/confirmation shows affected count; preset additions never modify old names; system unknown brand stays outside builtin-logo matching.
- [ ] Step 4: Implement searchable family/type selector and dynamic optional profile form, only name/type required; durable draft persists image references and all profiles, copy strips orders/import token/tasks. Detail uses LolitaSection and same profiles.
- [ ] Step 5: Verify real Room registration, read failure/save guard, draft restore and discard cleanup, history/parent/order preservation; commit only owned files.

### Task 2: 找物、导入与统计

**Files:** `ui/screen/item/ItemViewModel.kt`, `ui/screen/item/ItemListContent.kt`, filter components, `data/file/TaobaoProductParser.kt`, spreadsheet import files, `ui/screen/stats/StatsOverviewScreen.kt`, corresponding tests (actual related filenames established by code search).

**Interfaces:** consumes Task 1 semantic and profiles; Task 3 `StorageRepository.paths`.

- [ ] Step 1: Add failing tests that family/type/location filters include matching attached items exactly once, search matches category/color/full path but excludes private description, shoe/profile filters preserve hidden selections, category counts use distinct id.
- [ ] Step 2: Run tests RED; extend existing filter UI with horizontal families and searchable type/profile filters without changing top navigation.
- [ ] Step 3: Extend explicit Taobao and xlsx category/spec/location mappings; ambiguous locations stay unresolved for user selection, sync/public imports preserve local profiles/storage.
- [ ] Step 4: Add counts for all seven families with main/all labels; verify spending/privacy follows existing accounting; run tests GREEN and commit.

### Task 3: 房间柜架层格与模板采纳

**Files:**
- Create: `data/local/entity/StorageNode.kt`, `data/local/dao/StorageNodeDao.kt`, `data/repository/StorageRepository.kt`, `domain/model/StorageTree.kt`, storage UI components and tests (under existing com/lolita/app package).
- Modify: `ui/screen/settings/LocationManage*`, `ui/screen/item/LocationDetail*`, `data/repository/LocationRepository.kt`.

**Interfaces:**
- Produces: `StorageNode(id, locationId, parentId, name, imageUrl, sortOrder, createdAt, updatedAt)`; parent nullable under room, parent child maximum depth 2.
- Produces: `StorageRepository.observeNodes(): Flow<List<StorageNode>>`, `pathFor(locationId, nodeId, locations, nodes): String`, `createNode`, `renameNode`, `moveNode`, `deleteNode(clearContents: Boolean)`, `moveItems(itemIds,locationId,nodeId)` transactions with all affected descendants.
- Consumes: nullable `Item.storageNodeId`; root owns Room/AppModule/backup integration.

- [ ] Step 1: Add RED tests for duplicate siblings, cross-room/missing parent, cycle/depth, rename path refresh, occupied delete rejection, explicit clear preserving Items, move descendants and attached items atomically.
- [ ] Step 2: Implement model/DAO/repository with FK RESTRICT and SQLite unique sibling indexes including nullable root parent.
- [ ] Step 3: Add room detail cabinet/child navigation, record counts including attachments, search, multi-select move and explicit affected-count confirmation. Item editor uses shared location/node picker, room changes clear old node explicitly.
- [ ] Step 4: Add opt-in four-template picker using offline image assets; never reset existing BuiltInLibrary flag. Generate each source image with full provenance and checksum; templates are illustrations.
- [ ] Step 5: Run independent Room and Compose tests; commit owned files.

### Task 4: 不可变试衣与按语义展示图

**Files:** `domain/model/TryOn.kt`, `domain/model/MannequinRequest.kt`, `domain/model/ImageTask.kt`, `data/tryon/TryOnGenerator.kt`, `data/mannequin/MannequinGenerator.kt`, `data/generation/ImageTaskCoordinator.kt`, `data/generation/ImageTaskRunner.kt`, `ui/screen/tryon/*`, `ui/screen/mannequin/*`, `data/local/entity/TryOnResult.kt`; new `domain/model/WigHeadTemplate.kt` and catalog assets/tests.

**Interfaces:**
- Consumes: Task 1 CategorySemantic / defaultWearRole / displayMode, Task 5 image provenance persistence.
- Produces: new TryOnRole values WIG,HAIR_COMPONENT,HOSIERY,LEG_WARMER,SHOES,BAG,NECK_CHEST,PETTICOAT,HANDHELD; PETTICOAT is not a base garment.
- Produces: strict request v4 with immutable prompt version, category semantic, selected actual images and roles, model snapshot, optional `WigHeadTemplateSnapshot(id,name,version,sha256,imagePath,width,height,view)`.
- Produces: `TryOnResult.requestSnapshotJson: String?`; old histories remain null, v1/v2/v3 retain old prompts and roles.

- [ ] Step 1: RED tests for wig hair replacement/face preservation, sock occlusion/no skirt shortening, pair composition, sole petticoat rejection, role conflicts and explicit replacement; old snapshots unchanged.
- [ ] Step 2: Implement stable semantic role suggestion and conflict UI; selecting same exclusive role asks replacement/return; wardrobe selection may exceed eight but generation requires explicit subset 1..8, no take(8).
- [ ] Step 3: Implement semantic display prompts: full wig with selected head + one product image, hair component only, footwear/hosiery original composition, legacy dress mode. Reject unconfirmed/new wrong semantic and input changes before submission.
- [ ] Step 4: Add five named candidate templates with preview/single selection, saved draft preference and exact two-input preview. Validate source SHA, persist UUID input before queuing; history never depends on mutable bundled file.
- [ ] Step 5: Verify queued snapshots survive default/template/category changes, missing files blocked, old modes compatible; no real paid call without explicit budget. Commit owned files.

### Task 5: Room 30、图片来源与备份 10

**Files:** `data/local/LolitaDatabase.kt`, new `data/local/WardrobeMigration.kt`, `data/local/converters/WardrobeConverters.kt`, `di/AppModule.kt`, `data/file/BackupFormat.kt`, `data/file/BackupManager.kt`, `data/file/ImageFileHelper.kt`, new `data/local/entity/ImageProvenance.kt` and DAO/repository, related tests.

**Interfaces:** consumes all new models, drafts, task request refs; produces `ImageProvenance(imagePath,sourceType,mode,createdAt)` keyed imagePath, no task FK; original unspecified image paths remain ORIGINAL without rewriting albums.

- [ ] Step 1: RED tests restoring v9 with new fields empty/unknown and original fields unchanged; invalid enum/version/cross-room/cyclic/new request reference rejected before DB mutation.
- [ ] Step 2: Register new DAO/entity/Converters/AppModule and migration 29→30 using additive columns/tables, no blanket semantic updates. Export schema.
- [ ] Step 3: Update JSON/ZIP/preview/restore/CSV/all-field comparisons; templates actual input and recognized evidence persistent image refs participate in leases/dedup/cleanup. Non-image JSON missing refs explicitly resource-missing, no substituted template.
- [ ] Step 4: Record AI origin transactionally when appending result; task deletion cannot erase source. Preserve original cover and sync/public local fields.
- [ ] Step 5: Independent migration/backup roundtrip with old schema fixtures and shared images; commit after GREEN.

### Task 6: 局部识图、证据与草稿合并

**Files:** new `domain/model/ItemRecognition.kt`, `data/local/entity/RecognitionTask.kt`, DAO/repository, `data/recognition/*`, `data/remote/ItemRecognitionApi.kt`, `ui/screen/item/recognition/*`; item editor integrates entry without changing unique save action.

**Interfaces:**
- Consumes: Task 1 durable draft identity/profile and Task 5 image refs/backup.
- Produces: protocol v1 typed field candidates (field enum, raw value, TEXT/VISUAL/UNKNOWN, evidence text and region id, variant/size row/measurement qualifiers, confirmation); task keyed UUID with draftId/targetItemId and immutable 1..5 region refs; result never automatically applies.
- Produces: independent AES/GCM keys per provider in noBackup/Keystore; Ark `https://ark.cn-beijing.volces.com/api/v3/responses` with `doubao-seed-2-1-pro-260915`, DeepSeek `https://api.deepseek.com/responses` with `deepseek-flash`; provider-specific JSON schema and strict local validation, no tools, redirects or retries.

- [ ] Step 1: RED tests for crop-only upload/no EXIF/path, five-region bound, strict response parsing/refusal/malformed results, no visual numeric/material/price facts, no price/accounting merge, variant/size-row gating and immutable draft race.
- [ ] Step 2: Shared rectangle crop UI with pan/zoom/corners/rotation/reset and actual upload preview; decode limits, reencode final crop pixels to fresh UUID JPEG; original unchanged.
- [ ] Step 3: Independent recognition settings/service/fee confirmation and resumable task persisted before request; startup marks sent/incomplete interrupted, never automatically resend. Cancellation keeps draft/regions.
- [ ] Step 4: Result comparison with evidence preview, edits and individual checkboxes; TEXT empty ordinary values may preselect, existing/conflict/VISUAL/style/season do not; apply rechecks live draft against submission base and target identity. Brand/category references require explicit resolution, never auto-create.
- [ ] Step 5: Persist accepted evidence and kept crop refs; protected cleanup of rejected/abandoned temporary sources; process recreation roundtrip; run controlled HTTP and real Room/Compose tests GREEN and commit.

### Task 7: 完整验证与正式交付

**Files:** AGENTS.md, version configuration, implementation/verification records, visual evidence.

- [ ] Step 1: Run `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`; fix failures to green.
- [ ] Step 2: Run new migration/backup/editor/storage/recognition/AI/navigation Compose tests on independent AVD, plus existing accounting and image-task suites; seven skins light/dark/small/large-font visuals.
- [ ] Step 3: Fresh reviewer checks entire branch against spec and Review Focus; fix important issues with regression tests.
- [ ] Step 4: Real paid image assessment only within separately granted budget; retain explicit unverified limitations, do not mark candidate templates formally validated by fixtures.
- [ ] Step 5: Update version and AGENTS, commit source, `clean assembleRelease`, verify signature and old released APK nondestructive upgrade with full table/image/settings baseline.
- [ ] Step 6: Integrate/push master normally; publish identical APK/checksums GitHub+Gitee per existing scripts and anonymous verification; report exact achieved scope and outstanding externally blocked verification.

## 官方接口依据（2026-10-18 核验）

- [火山方舟模型](https://docs.volcengine.com/docs/ark/model-list?lang=zh)、[图片理解](https://docs.volcengine.com/docs/ark/image-understanding?lang=zh)、[Responses](https://docs.volcengine.com/docs/ark/create-model-responses-api?lang=zh)。豆包Seed 2.1 Pro需要在账号开通模型权限。
- [DeepSeek视觉能力](https://api-docs.deepseek.com/zh-cn/guides/vision/)、[Responses](https://api-docs.deepseek.com/zh-cn/api/create-response/)、[价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)。当前deepseek-flash支持图片，deepseek-v4-pro不支持；别名实际返回型号记录在任务。
- 详见 `docs/recognition-provider-2026-10-18.md`，未通过文档推定用户账号已开权限或真实识别质量通过。
