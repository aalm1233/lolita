# Background Image Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Run both paid image features independently of pages, retain task/results across restart, and release 2.51.0 with readable update notes.

**Architecture:** Room task records are the source of truth, with a user-started dataSync foreground service draining a serial queue. A coordinator owns submission, interruption recovery and image handoff; screens observe records. Backup format 8 includes tasks and pending previews, and never resumes paid work after import.

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, Room/KSP 2.7.0, coroutines 1.9.0, Android SDK 35/min 26; no new scheduling or rendering dependency.

**Spec:** `docs/superpowers/specs/2026-10-16-image-tasks-design.md` (approved 2026-10-16 07:44).

## Global Constraints

- Chinese UI, all seven skins, existing image/credential/API validation, no real paid calls during tests.
- Room 27→28, backup 7→8, old formats 1–7 missing tasks become empty. No changes to existing business rows.
- One explicitly confirmed request per task; no automatic paid retry on failure, restart, import or service recreation.
- Queue/service never owns credentials on disk. Provider fixed at submit, current provider-specific encrypted Key captured once before execution.
- Pending mannequin results remain until explicit attach/discard. Appending and task transition are one transaction.
- Android foreground service, bounded wake lock/timeout, START_NOT_STICKY; no boot auto-generation.
- Code tasks end in meaningful tests and commits. Formal release 2.51.0/109, clean build, same signature, dual distribution and upgrade verification.

## Review Focus

- Navigation between persistence and service start must not silently lose an accepted task; test by clearing submitting VM immediately.
- Crash after remote request/while publishing must not cause a second charge or orphan a committed result; pin restart and atomic handoff tests.
- Concurrent confirm/discard/import must not attach to stale/deleted/replaced Item rows or clear an owned file; pin Room race and restore tests.
- Deleting a business record or original image after task completion must not break future ZIP exports; release temporary source references at terminal states and test export.
- Service start rejection, notification denial, timeout and idle/new-submission race must expose a usable state and stop resources; pin controlled service tests.

---

### Task 1: Persisted task contract and migration

**Files:** Create `domain/model/ImageTask.kt`, `data/local/entity/ImageTask.kt`, `data/local/dao/ImageTaskDao.kt`, `data/repository/ImageTaskRepository.kt`; modify `data/local/LolitaDatabase.kt`, `data/local/converters/Converters.kt`, `di/AppModule.kt`. Tests: `domain/model/ImageTaskTest.kt`, Android `data/ImageTaskPersistenceTest.kt`.

**Interfaces:** `ImageTaskKind { TRY_ON, MANNEQUIN }`; `ImageTaskStatus { QUEUED, PREPARING, GENERATING, SAVING, AWAITING_CONFIRMATION, COMPLETED, FAILED, INTERRUPTED, CANCELLED, DISCARDED }`. Entity `ImageTask(id:String, kind, provider, title:String, requestJson:String, sourceImagePaths:List<String>, status, requestSent:Boolean, resultImagePath:String?, tryOnResultId:Long?, createdAt:Long, updatedAt:Long, error:String?)`. Request JSON contains version 1, garment ID/name/role snapshots and instructions, no paths/Key; ordered source paths live separately. Terminal requests may have no source paths. Repository exposes `tasks:Flow<List<ImageTask>>`, `observe(id):Flow<ImageTask?>`, `get(id)`, insertion, conditional queued claim and restart interruption. Source paths are released when no longer needed. Completed output paths are informational; only pending output is a protected image reference.

- [x] Write request validation/status tests (8/9 garment boundary, duplicate IDs, no required image refs for archived tasks, active states interrupted); run failing JVM target.
- [x] Add typed model/codec helpers `ImageTaskRequest.encode/decode`, `selections(paths)`, validation and protected-path helpers; use existing Gson and list converters.
- [x] Add entity/DAO/repository, 27→28 additive migration and registration; generated schema 28. Atomic claim uses affected-row conditional update.
- [x] Add migration sentinel/parallel claim/reference persistence tests with independent Room; run JVM and compile instrumented tests.
- [x] Commit contract and migration.

### Task 2: Background execution and atomic results

**Files:** Create `data/generation/ImageTaskCoordinator.kt`, `data/generation/ImageTaskRunner.kt`, `data/generation/ImageTaskService.kt`, `data/generation/ImageTaskNotifications.kt`; modify `TryOnGenerator.kt`, `MannequinGenerator.kt`, `TryOnSettings.kt`, `LolitaApplication.kt`, manifest, AppModule and image helper. Tests: JVM runner tests and Android `ImageTaskExecutionTest.kt` / `ImageTaskServiceTest.kt`.

**Interfaces:** Coordinator `submitTryOn(List<TryOnSelection>,provider):String`, `submitMannequin(MannequinRequest,provider):String`, `cancel(id)`, `confirmMannequin(id)`, `discard(id)`, `delete(id)`, `withRestorePaused(block)`. Operations run in application-owned scope so caller disposal does not cancel accepted mutations. Runner `runQueue(onChanged:suspend(ImageTask?)->Unit)` is service owned, serial, await startup recovery. Add a defaulted suspend `beforeRequest` hook to generators to durably mark requestSent before HTTP; task-specific save callbacks atomically publish results/status. `readCredentials(provider)` reads exact provider without changing global selection.

- [x] Write tests for one claim/request, stage ordering, cancellation before/after request, provider switching and process interruption; observe zero automatic retries and exact call counts.
- [x] Implement app-scope submit/confirm/discard/delete and service-scope runner. Capture provider credentials once. Save TryOnResult and task completion together; save mannequin preview and pending ownership together. NonCancellable handoff must not delete committed output.
- [x] Implement dataSync service with immediate foreground notification, click intent `navigate_to=image_tasks`, bounded wake lock, 12-minute per-task watchdog, onTimeout cancellation, START_NOT_STICKY and clean idle stop. Start exceptions produce visible interruption; service only processes accepted queue.
- [x] Register service/permissions/channel, start one-time interruption recovery at Application startup, and add task image reference protection. Restore barrier cancels/joins execution and serializes short local commits without lock inversion.
- [x] Run controlled transport/real Room tests including VM disposal, empty-queue/new-submit race, service start rejection, lock-screen continuation, successful output handoff, deleted Item and confirm/discard race. No production test hooks that bypass credentials/API routing.
- [x] Commit background execution.

### Task 3: Task backup compatibility

**Files:** Modify `data/file/BackupFormat.kt`, `BackupManager.kt`, `ui/screen/settings/BackupRestoreScreen.kt`, backup tests; add `ImageTaskBackupFormatTest.kt` and Android `ImageTaskBackupTest.kt`.

**Interfaces:** `BackupData.imageTasks:List<ImageTask> = emptyList()`, format 8/schema 28, `BackupPreview.imageTaskCount`. Snapshot/clear/import/verify/count/CSV include tasks. Protected image refs include queued/active source images and pending output only. ZIP remaps actual task refs structurally; completed output maps through business image map if present, otherwise null. Archived tasks clear unused source refs, so deleted photos cannot break ZIP. Import normalizes queued/active states to INTERRUPTED and never starts the service.

- [x] Write failing format tests for old 1–7 defaults, unknown enums/request versions, malformed task/path arrays and interrupted restore.
- [x] Add strict validation and all backup integration points, using coordinator restore barrier only for database replacement; export uses a consistent snapshot without waiting for HTTP.
- [x] Test real ZIP round-trip pending preview and confirmed image order, sentinel preservation, failure rollback and import during blocked generation. Verify no post-import old writes and no automatic request.
- [x] Run JVM/Android backup targets and commit.

### Task 4: Nonblocking generation pages and task center

**Files:** Modify both generation ViewModels/Screens, Screen/NavHost/MainActivity/SettingsScreen; create `ui/screen/tasks/ImageTaskViewModel.kt`, `ImageTaskScreen.kt`, `ImageTaskDetailScreen.kt`. Update relevant try-on/mannequin tests; add `ImageTaskScreenTest.kt`, `ImageTaskNavigationTest.kt`.

**Interfaces:** Both VMs accept submit functions returning task ID, observe latest submitted task, retain editable draft, and no longer own network jobs/preview files. SavedState stores compact task ID/draft only. Add `Screen.ImageTasks` and `Screen.ImageTaskDetail.createRoute(id:String)`; `onNavigateToTasks` / `onTaskSubmitted(id)` callbacks. Task center/detail use AppModule task repository/coordinator through VM. Retry opens the existing generator with garment IDs and current source images, requiring user confirmation; it does not silently resubmit stale paths.

- [x] Write VM/navigation tests proving clearing page retains task and pending image, all four bottom tabs/quick-outfit work during generation, and notifications route once after configuration change.
- [x] Replace page-owned generation/preview flow with submission and observed status, remove global navigation generation guard, preserve short editing/configuration safeguards and original selection logic. Add personal and generation-page task entry.
- [x] Build task center filters and detail with stage/elapsed status, Fit/zoom image, explicit confirm/discard/cancel/delete, controlled error and retry navigation. Reuse standard topbar/cards/section/image/scroll components and existing ViewAgenda icon. Handle absent Item/result and stale buttons through coordinator guards.
- [x] Run real Compose tests covering pending-save after restart, cancelled task, navigation, seven skins light/dark at 320dp and 2x font; inspect screenshots.
- [x] Commit UI.

### Task 5: Latest version notes

**Files:** `domain/model/AppRelease.kt`, `data/remote/GiteeReleaseApi.kt`, `GitHubReleaseApi.kt`, `ui/screen/settings/AppUpdateDialog.kt`; extend release parser/VM/host tests.

**Interfaces:** `AppRelease.releaseNotes:String = ""`. Parsers copy highest stable release body with null/missing handling within existing 1MiB response bound. Dialog scrolls text content, keeps buttons reachable, and shows notes for manual current-version check. Existing silent auto-check rules remain.

- [x] Add failing parser tests for highest release body, null/missing/blank, long multiline/Markdown and legacy GitHub body. Run test targets.
- [x] Implement body extraction and readable plain-text presentation, no HTML execution/new library; scroll using skin spacing and standard section heading. Empty text uses specified fallback.
- [x] Extend Compose host tests for manual equal version, long notes scroll and large-font download/close reachability; run existing automatic-check tests.
- [x] Commit notes support.

### Task 6: Whole-feature verification and formal release

**Files:** Build version, AGENTS, implementation/verification/release documents, visual evidence.

- [x] Run complete JVM suite and assembleDebug; run affected independent AVD instrumentation after verifying identity/version/signature and preserving test fixture data. Fix failures before claiming success.
- [x] Request fresh whole-branch code review; resolve actionable feedback and rerun affected checks, with crash/result/restore boundaries as focus.
- [x] Update AGENTS and release notes for 2.51.0/109. Commit/integrate source onto master and verify ordinary push.
- [x] `clean assembleRelease`; verify package/version/nondebuggable/original cert, overwrite upgrade from 2.50.0 on owned AVD and compare all preexisting rows/images/preferences. Archive evidence.
- [x] Publish source tag and matching signed APK/SHA256SUMS to GitHub and staged Gitee, anonymously download/hash before marking Gitee formal. Verify both notes/attachments/highest update endpoint/HEAD and source SHA.
- [x] Commit verification receipts, push, archive task worktree preserving required artifacts, and deliver download/update link with validation summary.

Self-review: All approved spec sections map to tasks 1–6; five review-focus conditions have explicit regression ownership. Interfaces are shared here to prevent mismatched callback/state naming. The user already authorized implementation and formal release; continue without another scope approval prompt.
