# Independent review: Task 3 storage / Task 4 immutable image tasks

Reviewed 2026-10-18 in `/Users/dengtong/.codex/worktrees/complete-wardrobe/lolita`. Read-only review of `storage-ai.diff`, the new source files omitted from that tracked-only diff, task 3/4 briefs/reports and the matching sections of the approved specification. No source changes, build, emulator/device action or paid request.

## Task 3 conclusion

**Specification:** Core storage integrity is compliant on inspection. Repository mutations are transactional; subtree moves include both descendant nodes and every located Item without changing parent/coordinate/order fields; deletion either rejects occupied content or explicitly clears only location references. The non-null `siblingScopeId` plus normalized-name unique index covers root siblings despite nullable parent ids. The shared validator rejects cycles, cross-room parents and a third node level (a fourth full-path level). Room details include mounted children and keep filtered selections. Templates are opt-in and verified before transactional adoption.

**Quality:** No critical or important implementation defect found in the reviewed storage persistence path. One minor original-spec shortcut is missing (S1). Existing independent Room and Compose test sources cover the main integrity contracts; their runtime/visual execution remains a verification step, rather than something this review establishes.

### S1 — minor: cannot register a new item from its cabinet with location prefilled

- **Location:** `app/src/main/java/com/lolita/app/ui/screen/item/LocationDetailScreen.kt:99-105`.
- **Trigger / effect:** From “玄关 → 鞋柜 → 第二层”, the available item action is “移入物品”, whose picker only contains existing `state.allItems` (`:229-245`). There is no “新增物品” route/callback. The approved “登记与详情 / 新增流程” explicitly says creating from a cabinet pre-fills the location; a user registering a new pair of shoes must leave this page, create it elsewhere and manually select the path again.
- **Suggested correction:** Provide a new-registration action carrying the current room/node identity into a fresh editor draft, while retaining the existing move-in flow.
- **Suggested verification:** Compose navigation from a compartment to a blank editor should show that exact room/node; cancelling must not create an Item, and saving should retain normal parent/order behavior.

## Task 4 conclusion

**Specification:** The principal paid-request architecture is implemented: explicit 1–8 subset selection without `take(8)`, role conflict confirmation, distinct full-wig/component modes, frozen v4 prompt/model/semantic/composition/input identities, actual selected head plus one product image, and permanent task-owned input/source references. v1/v2/v3 reject new role/semantic fields and continue on their old preparation/prompt paths. Static comparison against base `7a8d25f` confirmed the old TryOn multiline prompt literal and both legacy mannequin prompt literals are byte-identical; old-reference JSON construction also retains the same three keys for protocol <4.

**Quality:** **Changes requested** for A1, which allows deleting a previous task to invalidate an unrelated open generator draft's selected head. A2 is an additional nonblocking cleanup defect. No critical defect found. Real wig/hosiery/shoe fidelity remains explicitly unverified and correctly labeled as candidate/manual review work; no paid evaluation was performed for this review.

### A1 — important: reused head-template files have no ownership from the current generator draft

- **Primary locations:** `app/src/main/java/com/lolita/app/data/mannequin/WigHeadTemplateCatalog.kt:49-56`; `app/src/main/java/com/lolita/app/ui/screen/mannequin/MannequinViewModel.kt:179-189,203-207`.
- **Supporting paths:** The ViewModel only creates image-reference leases for crops (`:89-93,110,227-228`); no corresponding lease or durable owner exists for `selectedHeadTemplate.imagePath`. Task deletion removes the task owner then cleans all its input paths (`data/generation/ImageTaskCoordinator.kt:284-291`), and ImageFileHelper checks database/pin owners, not SavedStateHandle (`data/file/ImageFileHelper.kt:245-276`).
- **Trigger / effect:** Generate a task with head X, then open a new full-wig generator and select X. The catalog deliberately reuses the older task's exact head file. Use the generator's task-center button (`MannequinScreen.kt:63`) to delete that old completed task. With no other persisted owner or head lease, cleanup deletes the file still selected by the new draft. Returning to that draft leaves its displayed selection/confirmed view pointing to a missing file; submission fails in `validateImage` and the supposedly recoverable head choice is lost. Switching another generator away from the shared head can cause the same deletion through `discardLegacy`.
- **Suggested correction:** Acquire protection for the selected head before exposing the snapshot to the draft, transfer/release that protection on head selection changes, and give recoverable generator drafts durable input ownership where they must survive navigation/process restoration. Cleanup must retain a file referenced by another live/restorable draft, not only by submitted tasks.
- **Suggested verification:** Independent Room/real FileHelper test: seed a completed task owning X; open a second generator whose catalog reuses X; delete the original task; assert X remains readable and the new task can capture exactly X without reselecting. Also cover two drafts sharing X while one changes templates, followed by draft recreation. Use controlled submission, no network.

### A2 — minor: failed admission leaves newly copied built-in input files orphaned

- **Locations:** `app/src/main/java/com/lolita/app/data/generation/ImageTaskCoordinator.kt:126-132,157-167` (production injection at `di/AppModule.kt:78` copies to a new UUID file each time).
- **Trigger / effect:** A referenced garment path remains in Item.imageUrls but its physical file is missing, or v4 enqueue validation/DAO insertion fails. The coordinator has already copied the fixed model/dress-form image before hashing sources or entering `enqueue`, but there is no failure cleanup for that newly created path. No task row then owns it. Repeated retries accumulate orphan files (the bundled person image is approximately 1.7 MiB per copy), and database/disk failures amplify rather than reclaim this staging data.
- **Suggested correction:** Track only newly staged built-in copies through admission and clean them with reference protection when hashing/validation/enqueue fails; do not clean reused personal/template/item references. Transfer ownership only when the task insert commits.
- **Suggested verification:** Controlled coordinator test returning a real temporary model copy from `snapshotBuiltInImage`, then fail source validation or hashing before insert; assert no task was admitted and the new copy is removed, while original Item/model files remain. Add a transaction-insert failure variant.

## Review boundaries

- This review did not rerun the already reported 356 unit tests or APK builds.
- The task 3 and task 4 device tests are useful source-level evidence, but no independent AVD or rendered screenshot result was produced during this review.
- Root-owned backup/migration/recognition implementation was inspected only where needed to trace task/source ownership; this is not a separate comprehensive Task 5/6 review.
