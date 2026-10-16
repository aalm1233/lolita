# Personal Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建照片／参数两种专属全身模特，明确保存后接入现有虚拟试衣，并保持旧资料、任务和备份兼容。

**Architecture:** 新增独立个人模特资料与持久草稿，使用现有串行图像任务生成待确认预览。v2 请求快照区分人物参考和服饰引用；保存人物、删除和备份共用 Room 事务与 ImageFileHelper 引用保护。UI 复用七套皮肤、现有服务 Key 与任务中心。

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, Room/KSP, StateFlow, DataStore, Android API 26–35, JVM 17。

**Spec:** `docs/superpowers/specs/2026-10-16-personal-model-design.md`（2026-10-16 13:38 用户批准）

## Global Constraints

- 输出 1024×1536 单张完整全身照；头脚完整、自然站姿、普通中性背景。
- 身高、体重必填；胸围、腰围、臀围、肩宽、内侧腿长可选；补充描述最多 200 字。
- 照片方式至少脸部照或正面全身照其一；参数方式不上传隐藏照片，用虚拟面容。
- 仅已确认底图用于试衣；调整另存，历史结果不级联删除；不自动付费重试。
- Room 28→29，备份 8→9；保留 v1 三字段任务与旧备份兼容；密钥不进记录或备份。
- 所有新图走 ImageFileHelper UUID 文件、完整解码与引用保护；设备验证仅使用独立 AVD。
- 不动原有未跟踪文件；此计划的实施方法采用当前会话内并行子代理，避免再次请求已获得的开发授权。

## Review Focus

- 照片方式切成参数后再提交：隐藏本人照片不得进入请求或上传清单（任务 1、3、5）。
- 排队后删除／改默认人物：请求仍用提交时底图，删除不破坏该图及历史结果（任务 2、3）。
- 待确认生成＋退出／重启／备份：输出和确认所需原照均保留，确认保存幂等（任务 2、3、4）。
- 旧版完成／待确认 v1 任务恢复：不能按 v2 解释路径或自动重新付费（任务 1、4）。
- 编辑器旋转、字段尚未读完、图片复制取消：不覆盖草稿、不提交半成品、不清理其他记录图片（任务 2、5）。

## File structure and shared interfaces

The interfaces below are fixed across workers; backward-compatible defaults preserve existing test constructors.

- `domain/model/PersonalModel.kt`: `PersonalModelMode { PHOTO, PARAMETERS }`, `PersonalModelPhotoRole { FACE, FRONT, SIDE, VIRTUAL_FACE }`, `PersonalModelAppearance { FEMALE, MALE, NEUTRAL }`; `BodyMeasurements(heightCm: Double, weightKg: Double, bustCm: Double? = null, waistCm: Double? = null, hipCm: Double? = null, shoulderCm: Double? = null, inseamCm: Double? = null)`; `PersonalModelRequest(name: String, mode: PersonalModelMode, measurements: BodyMeasurements, photoRoles: List<PersonalModelPhotoRole> = emptyList(), appearance: PersonalModelAppearance = FEMALE, instructions: String = "")` with strict encode/decode, validation, `prompt(): String`, and `validatePaths(paths: List<String>)`.
- `domain/model/ImageTask.kt`: `TryOnModelSnapshot(personalModelId: Long, name: String)`; append nullable `personalModel: PersonalModelRequest?`, `tryOnModel: TryOnModelSnapshot?` to `ImageTaskRequest`. v1 stays byte/field compatible. v2 personal generation uses sources in `photoRoles` order; v2 personal try-on uses sources `[modelImage, garmentImages...]`. Add `modelImagePath(paths): String?`; `selections(paths)` extracts only garments. Expose `retainsSourceImages` at task level for personal pending/retry inputs.
- `data/local/entity/PersonalModel.kt`: accepted model `id: Long`, `name`, `requestJson`, `sourceImagePaths: List<String>`, `imageUrl`, `provider`, `model`, `createdAt`, `updatedAt`; `request(): PersonalModelRequest`, `protectedImagePaths(): Set<String>`.
- `data/local/entity/PersonalModelDraft.kt`: UUID `id: String`, `dataJson: String`, `sourceImagePaths: List<String>`, `updatedAt`. Draft JSON has raw input strings and roles, never embedded paths, keys or a bitmap. This separate draft table implements process-safe photo ownership; backup 9 includes it.
- `data/local/dao/PersonalModelDao.kt`: `observe(): Flow<List<PersonalModel>>`, `get(id: Long): PersonalModel?`, `getAll(): List<PersonalModel>`, `insert(model): Long`, `update(model): Int`, `delete(id)`, `deleteAll()`; same DAO has `getDraft(id)`, `getDrafts()`, `upsertDraft(draft)`, `deleteDraft(id)`, `deleteAllDrafts()`.
- `data/repository/PersonalModelRepository.kt`: `observe()`, `get(id)`, `rename(id, name)`, `delete(id)`, draft read/save/delete; file cleanup after commit. `AppPreferences.defaultPersonalModelId: Flow<Long?>`, `setDefaultPersonalModelId(id: Long?)`. Validate default against observed records; nonexistent ids display built-in.
- `ImageTaskCoordinator`: `submitPersonalModel(request, paths, provider): String`, `confirmPersonalModel(id): Long`, `publishPersonalModel(id, bytes): String`; `submitTryOn(selected, provider, personalModelId: Long? = null)`. Confirm returns already-saved id idempotently. `ImageTask.personalModelId: Long?` stores accepted result id; append person snapshot to `TryOnResult` with default null id/name.
- `TryOnImages.preparePersonalModel(paths)` prepends layout template, `prepareWithPersonalModel(modelPath, garmentPaths)` uses accepted reference. New API `generatePersonalModel(credentials, images, prompt)` accepts 1–5 inputs without weakening old generate 2–9 contract.
- `Screen.PersonalModels`, `Screen.PersonalModelEdit(modelId: Long? = null, retryTaskId: String? = null)`; task detail handles preview and saving, so a separate preview route is unnecessary. Library item opens full-size image and actions.

### Task 1: Domain requests, typed validation and v1/v2 compatibility

**Files:** create `domain/model/PersonalModel.kt`, modify `domain/model/ImageTask.kt`, `domain/model/TryOn.kt`; tests `PersonalModelRequestTest.kt`, `ImageTaskTest.kt`, `TryOnTest.kt`.

**Interfaces:** produces the domain contracts above. `TryOnRequest.prompt(personalModel: Boolean = false)` keeps old no-argument behavior.

- [ ] Add failing tests: `parameterModeRejectsPersonalPhotos`, `photoModeRequiresFaceOrFront`, `optionalMeasurementsStayUnknown`, `nonFiniteAndInvalidValuesFail`, `promptSeparatesLayoutFromIdentity`, `v1StillDecodesExactly`, `v2TryOnSeparatesModelFromGarments`.
- [ ] Run `./gradlew testDebugUnitTest --tests '*PersonalModelRequestTest' --tests '*ImageTaskTest' --tests '*TryOnTest'`; verify new behavior fails before implementation.
- [ ] Implement the named types and strict whitelisted parsing. Use centralized broad bounds: height 50–260 cm, weight 10–500 kg, circumferences 10–350 cm, shoulders 10–120 cm, inseam 10–180 cm; support decimal values. Do not validate fixed body ratios.
- [ ] Run the same tests to PASS, then commit this task's domain files and tests.

### Task 2: Room records, persistent drafts, selection and image ownership

**Files:** create `PersonalModel`/`PersonalModelDraft` entities, DAO and repository; modify `LolitaDatabase.kt`, `data/local/converters/Converters.kt`, `ImageTask.kt` entity, `TryOnResult.kt`, `ImageFileHelper.kt`, `AppPreferences.kt`, `AppModule.kt`.

**Interfaces:** produces DAO/repository/preferences contracts above. Model and draft reference queries participate in `deleteImage`'s same Room transaction. `AppModule.personalModelRepository()` returns repository.

- [ ] Write `PersonalModelPersistenceTest` covering 28→29 preserving old rows, rename only changes name/time, draft references protecting copies, deleting an accepted model while a task protects its image, missing default fallback and original history preservation.
- [ ] Run targeted instrumentation on an explicitly selected independent AVD, confirming missing schema/DAO fails before implementation.
- [ ] Add additive migration and KSP schema 29; extend old entities with nullable default columns. Keep no foreign key from historical try-on/task snapshots to mutable personal models. Implement repository cleanup after transaction and DataStore selection.
- [ ] Store editor raw strings in a strict versioned draft format, images only in enumerable `sourceImagePaths`; preserve drafts on process recreation; cleanup exclusively on explicit discard or successful ownership transfer.
- [ ] Run targeted tests and `assembleDebug` after integrating task 3 dependencies; commit data and schema files.

### Task 3: Generation, durable confirmation and personal try-on

**Files:** modify `ImageTaskCoordinator.kt`, `ImageTaskRunner.kt`, `ImageTaskDao.kt`, task entity, `TryOnImages.kt`, `TryOnGenerator.kt`, `ImageGenerationApi.kt`, `SeedreamImageApi.kt`, `TryOnImageApi.kt`, `AppModule.kt`; create `data/personalmodel/PersonalModelGenerator.kt` if needed to keep runner small.

**Interfaces:** consumes tasks 1–2. Coordinator methods above are application-scope accepted operations; new executor constructor parameters appended with defaults for existing tests.

- [ ] Add failing JVM/API tests for one-template generation only through the new operation, no hidden photos, provider snapshot and no automatic retry; add device tests for preview/source publication, duplicate confirmation, restore/stop/cancel boundaries and queued model deletion.
- [ ] Run targeted JVM tests then device task tests; confirm new expectations fail.
- [ ] Implement explicit PERSONAL_MODEL task kind, immutable v2 metadata and path grouping, startup/retry source retention for personal tasks, and atomic confirm→accepted model plus task result id. Preserve existing mannequin cleanup semantics.
- [ ] Snapshot personal try-on reference at submission in a Room transaction, pin it through execution, pass its identity-aware prompt, and persist person id/name in TryOnResult. Do not reload current default at execution time.
- [ ] Expose new per-operation API entrypoint with unchanged HTTPS, 32MiB PNG, normalization and error handling. First template controls layout only; explicit original photographs govern identity. Use existing shared generation gate.
- [ ] Run targeted JVM and independent AVD tests to PASS; commit pipeline files.

### Task 4: Complete backup 9 and restore mapping

**Files:** modify `BackupManager.kt`, `BackupFormat.kt`, backup result/preview UI if count fields require it; tests `PersonalModelBackupFormatTest.kt`, `PersonalModelBackupTest.kt`, existing task/try-on backup tests.

**Interfaces:** consumes tasks 1–3; exports `personalModels` and `personalModelDrafts`; schema 29, format 9.

- [ ] Write failing tests for formats 1–8 defaulting arrays/person snapshot, v1 pending mannequin restoration, strict v2 validation, JSON/ZIP roundtrip including accepted/reference/draft/pending-source images, mapped paths, and unknown enums/nonfinite values rejection.
- [ ] Run targeted JVM and independent Room/ZIP tests to observe failure.
- [ ] Extend all snapshot/clear/insert/preview/count/CSV/required-array/field-comparison/image-mapping paths. Pending model source images and result stay protected; interrupted personal requests keep necessary input references but never execute automatically.
- [ ] During restore invalidate stale local defaults safely and use existing restore barrier and reference leases; reject mismatched task/result ownership without deleting prior files.
- [ ] Re-run existing and new backup tests to PASS; commit backup files.

### Task 5: Real seven-skin library, editor, task preview and try-on picker

**Files:** create `ui/screen/personalmodel/PersonalModelViewModel.kt`, `PersonalModelScreen.kt`, `PersonalModelEditViewModel.kt`, `PersonalModelEditScreen.kt`; modify navigation `Screen.kt`/`LolitaNavHost.kt`, `ui/screen/settings/SettingsScreen.kt`, `TryOnViewModel.kt`/`TryOnScreen.kt`, task screens/VM.

**Interfaces:** consumes coordinator/repository/default preference APIs; shared draft DTO is versioned and only path arrays contain files. `PersonalModelScreen(onBack, onCreate, onAdjust, viewModel)`; `PersonalModelEditScreen(onBack, onTask, viewModel)`. Navigation loads identity once via ViewModel, not recomposition resets.

- [ ] Add ViewModel/Compose tests for invalid input, PHOTO→PARAMETERS excluding retained hidden photos, process/draft restoration, photo copy cancellation, retry prefill waiting, task submission once, save preview idempotence and deleted model retry requiring reselection.
- [ ] Implement library and editor with existing top bar, sections/cards/images, Photo Picker and image preview; use skin spacing, scroll observation, scaffold/IME consumption. Name / height / weight, optional measurements and description, photo roles and parameter-only appearance. Dirty exit confirms explicit discard; rotation is not discard.
- [ ] Extend task detail for new previews, meaningful save/discard and confirmed model navigation. Extend retry using current photo/parameter sources, never auto-submit. Preserve all old route patterns.
- [ ] Add model picker and manage entry to try-on, retain item selection while switching characters, display this request's selected person; UI selection validation prevents generating with deleted/missing images. New calls preserve existing VM test constructor defaults.
- [ ] Run focused Compose and VM tests on independent AVD; export actual light/dark and 320dp/large font screenshots. Compare primary flow to approved mockup; commit UI/navigation files.

### Task 6: Integration review, full regression and release

**Files:** docs implementation/verification records, AGENTS.md new feature facts, build version metadata and release notes; source fixes only if evidence requires.

- [ ] Run `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`; inspect totals/failures, then targeted connected tests for personal model, old generation/navigation, task service and backup on independent AVD.
- [ ] Fresh independent review checks spec coverage, corrupt import data, path ownership, concurrency, old constructor compatibility, no embedded credentials and no privacy regression; fix actionable findings and re-run affected checks.
- [ ] With no user portrait or new paid test authorization supplied, explicitly report likeness/target measurements as unverified; controlled transfer and built-in demonstration do not prove real-person results. Do not reuse prior complex-outfit API budget for a different experiment.
- [ ] Update implementation records and AGENTS.md. For normal authorized release use 2.53.0 / 112, `clean assembleRelease`, original signing identity and independent AVD 2.52.0→2.53.0 upgrade, table/image/settings preservation. Publish via the established GitHub/Gitee flow and validate public APK/SHA256/update notes; preserve source credentials outside repo.
- [ ] Commit verification and integration; report installable result, actual evidence and remaining visual-generation limits concisely.

并行开发期间主分支已加入服饰封面功能并占用 2.52.0 / 111；已合入该变更，本功能顺延为 2.53.0 / 112。

## Self-review

All spec sections are assigned: input/identity (1), persistence/defaults/drafts (2), asynchronous generation and fixed character try-on (3), complete compatibility (4), seven-skin UI and retry flows (5), verification/delivery (6). The five Review Focus conditions each have an owning test task. New interfaces append defaults where old construction must compile. Persistent drafts use one additional table as an implementation choice to make process-safe image protection concrete; they are included in full backup 9.
