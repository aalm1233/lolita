# Root recognition: independent read-only review

Reviewed 2026-10-18 at working-tree HEAD `e750d1a`, including root's untracked recognition implementation. Scope: RecognitionRepository/Coordinator, recognition domain models, recognition UI and ItemDraftRepository's new hooks. The existing ItemEdit bridge was read only as a lock/identity boundary; its field mapping is not reviewed here. No production changes, Gradle/build, device launch or paid request.

Reference: approved specification, “AI 识图补充物品资料”, especially “确认与合并 / 服务与资料保存”. Provider transport replacement is in progress and outside this review.

## Conclusion

**Not ready to enable recognition submission.** No critical accounting-write defect found: the allowlist excludes accounting/storage state, PRICE_NOTE is ineligible, requests/results persist independently, recovery does not resubmit, and accepted evidence is separate from the album. However there are important lifecycle/concurrency defects and incomplete recovery/measurement/source-review behavior below. A green parser/build does not establish these untested application-level contracts.

## Actual bugs

### R1 — important / P1: application holds the Room transaction while awaiting the editor lock

- Location: `data/repository/RecognitionRepository.kt:84-93`.
- `apply()` enters `database.withTransaction` before invoking the suspending `applyToDraft` callback. The real editor callback acquires the editor draft mutex; editor autosave/keep/save already acquire that mutex before entering a Room transaction. An autosave can hold the draft mutex while its initial DAO read is suspended, then recognition obtains the database transaction and waits for that mutex; autosave resumes and queues its draft-save transaction behind recognition. Neither can complete. The UI remains busy and later saves also stall.
- Fix: define one lock order shared by apply/autosave/save. Do not hold a database writer transaction while acquiring an arbitrary editor callback lock; coordinate the editor merge and persistent task/evidence update under a consistent outer operation boundary. Preserve atomic failure behavior rather than merely removing the transaction.
- Reproduction/regression: independent Room plus a gated query executor. After a loaded editor's name update acquires its normal draft mutex, pause autosave's `ItemDraftDao.get`; start real `RecognitionRepository.apply` until its callback waits for the editor; release the read and allow autosave to queue `ItemDraftRepository.save`. Both operations must finish within a deadline and persist the merged draft/evidence once. This interleaving was traced statically, not run in this review.

### R2 — important / P1: saving an Item strands an unfinished recognition task and its paid result

- Locations: `data/repository/ItemDraftRepository.kt:29-35`; `data/recognition/RecognitionCoordinator.kt:58-62`; `data/local/dao/RecognitionDao.kt:9`.
- Commit attaches already accepted evidence and deletes the ItemDraft, but does nothing with PREPARED/RUNNING/AWAITING_REVIEW tasks. The recognition dialog permits returning to the editor during RUNNING, and the editor may save normally. A late response then changes the old task to AWAITING_REVIEW even though its draft was deleted. Reopening the Item creates another draft id; the only task observer filters by that new draft id, so the paid result and retained crop files are inaccessible. Saving an already returned but unreviewed result produces the same loss without any network race.
- `confirmation`/`apply` also compare `get(draftId)?.targetItemId == task.targetItemId` (`RecognitionRepository.kt:68,89`): for a new-item task, missing draft and null target compare equal, so those repository checks do not themselves reject a deleted new draft.
- Fix: make the save policy explicit and atomic: retain/retarget resumable recognition ownership with the saved Item, or require an explicit resolution before deleting its draft. A missing draft must always fail identity validation. Cancel/cleanup and task ownership must not be left to an unreachable UI entry.
- Regression: use a controlled recognizer deferred result; submit from a valid new editor, return to edit and save, release the recognizer, reopen. Either saving was explicitly blocked/resolved, or the exact result/regions remain reachable for that Item with zero new request. Also cover AWAITING_REVIEW at save, and a deleted draft with `targetItemId=null`.

### R3 — important / P2: copied known-AI photos lose appearance-only enforcement

- Location: `ui/screen/item/recognition/ItemRecognitionPanel.kt:106-124`, especially `:111`.
- Generated-source detection queries provenance only through `editor.item?.id`. “复制新建” carries the same registered photo paths into a new draft, whose `editor.item` is null; selecting a photo already marked AI_GENERATED on the source Item therefore sets `appearanceOnly=false`. TEXT dimensions/material/size candidates from that generated photo can cross the normal boundary. The chosen source is known locally, so this is not the unavoidable case of an unlabelled external album image.
- Fix: carry immutable provenance into copied drafts or resolve known provenance by actual referenced path/source owner, and OR that mandatory restriction with an optional manual AI-source flag. Do not let a new registration identity erase known generated origin.
- Regression: save Item A with an AI_GENERATED provenance row for path X; copy A to a new draft; select X and confirm a crop. Assert the stored request region is appearanceOnly and a TEXT material/centimeter response cannot be offered as factual data. Ordinary photos and manually marked external AI inputs remain supported.

### R4 — important / P2: asynchronous review persistence can drop edits and replace them with stale data

- Locations: `ui/screen/item/recognition/ItemRecognitionViewModel.kt:32-36,50`; `ItemRecognitionPanel.kt:151-152,169-185`.
- Every keystroke and choice goes through a global `action` that silently returns while busy. Local `review` is still changed, but the dropped change is never persisted. When the earlier Room write emits, `remember(task.id, task.reviewJson)` reinitializes local review to the older JSON, so text, selections or resolved brand/category revert. ReviewChoice and ReferenceChoice can still be used while another review write is busy.
- Fix: keep an authoritative editable review with ordered/latest-state persistence; do not drop a user edit because a different snapshot is saving, and do not reset newer local state from an older persisted emission. Serialize apply after pending review state is resolved.
- Regression: gate the first review write, type a multi-character replacement and make a second choice before releasing it; then navigate/recreate. The full final text, selections and resolved ids must remain, with no transient stale reset or lost character. Also interleave a pending review write with Apply.

### R5 — important / P2: applying one selected field closes all remaining suggestions

- Locations: `data/repository/RecognitionRepository.kt:99-103`; `ItemRecognitionPanel.kt:166,189-191`.
- The terminal status is calculated from `review.selectedIds - acceptedIds`, not from the complete candidate/review state. For ten suggestions, selecting and successfully applying one makes the task APPLIED. The UI then stops rendering every candidate, including the nine not yet selected. The user cannot return to the comparison page to continue adjusting, as required by the spec.
- The comment that evidence is idempotent is also unsupported: accepted candidate ids are not recorded, and each apply inserts a new random evidence id (`:95-97`). While another selected field keeps the task in AWAITING_REVIEW, an already accepted candidate whose value still equals baseline can be reselected and insert duplicate evidence. Opening APPLIED review without tracking accepted identities would extend that duplication path.
- Fix: preserve editable/reviewable candidates after partial application and track task/candidate application identity separately from current checkbox selection. Enforce evidence idempotency in persistence.
- Regression: select 1/3 fields, Apply, reopen, select another, Apply; all three remain inspectable and each accepted candidate has exactly one evidence record. Include a same-as-baseline value plus one conflicting candidate and repeated Apply.

### R6 — important / P2: changing the size row leaves unremovable, incompatible selections in the merge

- Locations: `ItemRecognitionPanel.kt:169-177`; `domain/model/ItemRecognition.kt:114-119`.
- Select row M and its inner length, then change the actual row to L. M remains in selectedIds but its checkbox is disabled because it is no longer eligible. Selecting L leaves both values grouped; merge compares their differing values before filtering applicability and rejects both. A user must switch back to M to clear a disabled selection before choosing L, although the active row is already unambiguous.
- Fix: reconcile dependent selections when row/variant changes, or exclude inactive row candidates before conflict grouping and show their actual unselected status. Genuine contradictions within the selected row must still conflict.
- Regression: M=23cm/L=24cm; select M then switch to L and select L. Apply must return L=24cm only, without requiring switching back. Repeat for variants and ensure same-row conflicting images still cannot silently overwrite one another.

### R7 — important / P2: malformed restored review JSON can crash the comparison UI

- Locations: `data/local/entity/RecognitionTask.kt:27-34`; `ItemRecognitionPanel.kt:150-152`; validation dependency `data/file/WardrobeBackupValidation.kt:29-30`.
- Task validation checks request/result but never reviewJson. The backup validator accepts such a task, while the UI decodes reviewJson inside composition without error handling. A structurally valid AWAITING_REVIEW task with `reviewJson="{bad"` can therefore restore successfully and crash the screen when opened; the original draft/crops remain in the database but are not usable through the editor.
- Fix: validate persisted review against the task's candidate ids at the same boundary as request/result; render a recoverable review error for corrupted/unsupported local state instead of throwing from composition.
- Regression: restore an otherwise valid task with malformed JSON, unknown candidate id and wrong review field type. Import must reject atomically or UI must preserve the draft/crops and permit rebuilding review; no crash, automatic apply or new request.

## Important specification gaps

### G1 — measurement basis/units and textual qualification are not locally enforced

- Locations: `domain/model/ItemRecognition.kt:8-24,40-51,93-97`.
- Dimension eligibility is merely a `cm|mm|厘米|毫米` substring for a few fields; other dimensional fields have no unit guard. Neither candidate schema nor review records measurement target/basis in a form the app validates. Thus SHOE_HEEL=`未提供 cm` is eligible, and SHOE_INNER_LENGTH=`适合足长 23 cm` is eligible despite the spec explicitly distinguishing foot length from inner length. A TEXT NAME with empty quote also preselects when current is empty, despite lacking the clear textual evidence required for default selection.
- These are not assurances an instruction prompt can provide. Preserve raw qualifiers, and represent/confirm measurement target, basis, unit and range before accepting a dimension; ambiguous or malformed data should remain quoted/unselected. TEXT preselection needs a nonblank verifiable quote. The current allowlist also has no clothing length/circumference/table-row fields, so a clothing size table cannot retain those recognized measurements as a structured row.
- Regression: flat width vs circumference, body fit vs garment dimensions, foot length vs inner length, mm vs cm, ranges, absent units and literal `未提供 cm`; none may become another measurement's fact. Test clear numeric evidence still works, and empty-quote TEXT never auto-selects.

### G2 — interrupted/failed tasks retain crops but provide no explicit reuse/retry path

- Locations: `ItemRecognitionPanel.kt:159-165,191`; `RecognitionRepository.kt:65-67`; `data/local/dao/RecognitionDao.kt:16`.
- Startup interrupts PREPARED/RUNNING tasks. FAILED/INTERRUPTED content only exposes thumbnails and “移除此识图记录”; submit and region removal are restricted to PREPARED. The user cannot explicitly resubmit the preserved selection or return it to editable preparation, despite the error telling them to resubmit. They must discard and recrop up to five regions.
- Regression: recreate/cancel/fail with five regions; zero requests happen automatically, but a visible deliberate retry/reprepare action preserves the exact regions and opens a fresh fee confirmation before issuing exactly one new request.

### G3 — confirmed evidence has no post-save viewing entry

- Locations: `ItemRecognitionPanel.kt:54,87-100,193-198`; `data/local/dao/RecognitionDao.kt:18`; `ItemDraftRepository.kt:33-34`.
- Accepted evidence is attached correctly to Item, but UI only reads tasks for the current draft. There is no UI consumer of observeEvidence(itemId). After saving and reopening, the source text/crop viewer is gone with the old draft entry, so the promised later source audit is unavailable. The optional “保留为商品图/资料图” action is also absent.
- Regression: apply a quoted field, save, close/reopen the Item and inspect its source; exact quote and crop must be available without rerunning recognition or mixing the crop into the album. Discard task history and confirm the accepted evidence survives. Optional album adoption must be explicit.

## Verification performed and boundaries

- Read-only source/dataflow tracing and searches of every evidence UI consumer; no bridge-mapping review and no provider transport review.
- Used the already built domain classes in Java source-file mode (in-memory probe, no Gradle or repository source edit). Actual outputs:

```text
malformedLengthEligible=true
footLengthAsInnerLengthEligible=true
textWithoutQuotePreselected=true
afterRowChangeValues={}, conflicts=[m, l]
```

- The controlled Room/concurrent UI regressions above are proposed reproductions, not executed test claims. Existing 357 unit / 53 targeted device successes predate these missing regression scenarios.
- Snapshot SHA256: RecognitionRepository `0442ede52d8cf6ec113a321e101a15505c2f8b14bd606c6402f68e44c0b48505`; Panel `67ab91c7d1afbd4b4b3f77db2fe923bbd9393fe0987dd080c32c00762fa8116f`; Recognition VM `c26459ae99e43970470181e8aa473fd5a99fcb9901fc43a8d1796931c02b78fe`; domain ItemRecognition `923d24bae092b2ec8b3972d7ff08011c32fcc9d79c0a0059f78ab2224f817245`; ItemDraftRepository `6e6d7352fe1d0cd4fe61e71fbcf40a3a5e247bec76dc7fdcf2e92b2619a265a7`. Parent may be fixing these concurrently; findings describe this captured source snapshot.
