# Task 2 and recognition editor bridge report

Worktree: `/Users/dengtong/.codex/worktrees/complete-wardrobe/lolita`; branch `codex/complete-wardrobe`.

## Changes

- `ItemRepository` validates an optional storage node exists and belongs to the selected room inside the existing insert/update transaction. Updates prune provenance only for removed album paths, in that same transaction. `ImageProvenanceDao` supplies `getForItem` and `delete(itemId,path)`.
- `BuiltInLibrary` explicitly excludes system brands from initialization and logo preparation, preserving the system unset-brand identity.
- Navigation adds optional `draftId` to `Screen.ItemEdit`, routes copied records into their exact draft and creates ItemEditViewModel through a real SavedStateHandle factory. Item details can open their storage room; Location management can open the selected room's detail.
- Distinct inventory summary counts all persisted Item identities (including children and wishes), separately labels main items, and shows seven families plus the unconfirmed group. Count rows navigate by stable family. Monetary calculations and privacy rules are unchanged.
- Explicit product import candidates now include full wigs, components, hosiery, leg warmers, shoes, petticoats, bags and handheld props. Mixed products retain multiple candidates and the existing per-piece confirmation. Existing categories are matched for the row but never assigned or rewritten a semantic by their name.
- Order import prepares missing categories with a visible semantic/default-role choice. Unknown semantics must be confirmed before creating a selected new category. Per-item category selection is searchable; category creation explicitly chooses a semantic. Unmatched brands use the stable unset-brand record rather than blocking registration. Shop matching is exact under the existing normalized brand matcher, rejects ambiguity and excludes system brands.
- XLSX preserves raw shoe systems/ranges/decimals such as `EU 37.5` and `23 至 25 cm`. Optional columns `物品类型/类型`, `尺码/鞋码`, `房间`, `收纳路径/收纳位置` and `专属规格JSON/规格JSON` support explicit mapping. Rooms and complete node paths use exact matching; missing or conflicting sources stay unset or at the explicitly known room and show a correction note. No fuzzy merges and no new CSV import.
- The order editor can supplement type-specific profiles, composition and storage. Final import saves those fields while retaining all amount allocation, paid-date, deposit/balance pairing and financial-confirmation guards.
- `WardrobeSpecifications.pattern` is a raw optional string in unreleased v1; old payloads default it to empty, strict unknown-field and version validation remain. Its editor/detail and recognition mapping are connected.

## Recognition bridge

`ItemEditViewModel.currentRecognitionValues(): Map<RecognitionField,String>` exposes current editable values without accounting, room or private-note fields.

`applyRecognitionSelection(expectedDraftId,baseline,candidates,selectedIds,variant=null,size=null,resolvedBrandId=null,resolvedCategoryId=null): RecognitionMerge` is suspending. It verifies the editor is loaded, the draft identity is still current, the draft has not been discarded and an existing target Item is still present. It calls domain RecognitionMerge against the latest state, keeps manual edits as conflicts, and maps only accepted fields into the StateFlow and durable draft. Brand/category values require a separately resolved existing id; raw AI text never creates those records. Profile fields must match the selected category's current profile. The returned accepted ids correspond to actual applied fields, for root evidence persistence.

`keepDraft()` now also persists an untouched blank editor, providing a durable identity for identifying a product before typing its name/type. An explicitly requested deleted draft errors instead of loading a different unfinished draft.

Root provides `ItemRecognitionPanel(viewModel,modifier)` and attaches accepted evidence/recognition cleanup inside ItemDraftRepository commit/discard. The Panel call is now present after the category selector and remains unchanged by the S1 patch below.

## Tests and evidence

- RED: `task-2-red.log`, expected missing `WardrobeInventoryCounts` and `explicitCategoryCandidates` interfaces before implementation.
- Unit coverage added: distinct all/main/family/unconfirmed inventory counts; separate explicit wearable candidates; raw shoe/sock size preservation; optional XLSX profile/path extraction.
- Independent Room regression additions: cross-room/missing storage rejection, exact removed-path provenance pruning, recognition-time manual edit conflicts, unknown brand rejection without record creation, draft mismatch and post-discard rejection.
- First GREEN attempt: `task-2-green.log`; production and Android-test compilation succeeded. Unit test compilation was blocked by root's then-new HTTP test importing unavailable java.awt/ImageIO/HttpServer. Root has replaced that fixture; full suite rerun pending.
- `git diff --check` passed at report creation.

## Root integration / limits

- Root still owns database/schema/backup and recognition evidence hooks. This task does not alter those files or launch any paid request.
- Root should include `WardrobeRegistrationTest`, parser/import tests and inventory tests in independent-AVD verification. Actual seven-skin, small-screen and large-font interaction screenshots remain required.
- Raw specification filtering is textual, without unit or shoe-system conversion. XLSX optional profile JSON is strict versioned data, not arbitrary extra fields.

## Final GREEN (before Panel insertion)

`JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest compileDebugAndroidTestKotlin` completed successfully (`task-2-green-final.log`). Full unit suite: **355 tests, zero failures and errors**. Android instrumentation sources compile. This includes Inventory 1, explicit import candidates 2 and order parser 6 cases; new Room storage/provenance/recognition bridge tests still await independent-AVD execution. Gradle is released.

`ImageProvenanceDao` originated in root's unstaged new persistence files; its `getForItem`/`delete` additions are intentionally left in the working tree for root's persistence commit instead of staging another agent's file.

Commit: `5e703f6 feat: connect wardrobe imports inventory and recognition drafts` (20 owned source/test files only).

## S1 follow-up: contextual new-item registration

- `Screen.ItemEdit` carries optional `defaultLocationId`, `defaultStorageNodeId` and stable `defaultFamily` query arguments. NavHost parses them and uses the existing real SavedStateHandle factory.
- Room, cabinet and compartment detail expose `onAddItem(roomId: Long, nodeId: Long?)`, with a separate “新增物品” action. Creating there pre-fills the current room/node. Moving existing items remains available.
- The home family filter's add action and the statistics family result list carry the selected family into the editor. The type picker initially filters that family, visibly labels the hint and still permits switching to any other family. No arbitrary subtype is selected and no historical category semantics change.
- Entry defaults participate in editor identity. Contextual new entries never fall back to an unrelated `latestNew` draft; explicit SavedStateHandle/draft-id restoration takes precedence over route defaults and retains user edits. Changing editor context clears its previous in-memory draft pointer without deleting the old durable draft.
- Room/node defaults are checked against current storage data before the editor finishes loading; missing or cross-room nodes fail the load. Failure clears the newly allocated, unpersisted draft pointer so retry remains possible. Existing Item edits ignore all new-entry defaults. No Item/order/image identifiers are rewritten.
- RED evidence: `s1-red-device.log`, three real independent-Room tests actually failed in the old production code (missing location prefill, missing cross-room rejection, unrelated family draft resumed). These are runtime assertion failures, not missing-interface compilation failures.
- Added regression coverage: contextual save retains room/node without creating a price; an explicitly restored draft preserves user-changed location; unrelated drafts remain recoverable; footwear's initial type filter can be changed to wig; route arguments retain stable numeric/enum identities.
- Full build: `storage-crop-final-device-build-3.log`, 357 JVM tests, zero failures/errors, `assembleDebug` and `assembleDebugAndroidTest` succeeded. One device fixture collided with the preset “玛丽珍鞋” created during VM initialization; its test-only type label is now unique and the next test-APK build succeeded (`storage-crop-final-device-build-4.log`).
- Final device verification: `artifacts/wardrobe-device-2026-10-18/round4-targeted-instrumentation.log`, **53 tests passed**, including the complete `WardrobeRegistrationTest` (11) and `WardrobeCategoryEntryTest` (1), on the independent `Lolita_Wardrobe_Test_35` AVD at `emulator-5582`. This confirms all three formerly failing S1 scenarios and the location/draft-preservation controls. No app data reset or paid request.
- No recognition provider changes or paid requests; root's Panel insertion and storage's device/UI fixes are preserved and excluded from this patch's own staged hunks.
- Latest final device rerun remains **53/53 passed** (`artifacts/wardrobe-device-2026-10-18/final-targeted-instrumentation.log`, 20.495s). Commit: `e750d1a fix: prefill contextual wardrobe registration without mixing drafts` (11 owned source/test files, root Panel insertion left unstaged). Shared Git index is released.
