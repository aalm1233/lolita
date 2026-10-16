# Task 5B — existing try-on / task UI integration

## Scope and implementation

- Preserved the existing two-argument builtin submit injection and all original constructor argument positions. Appended model/default flows use empty safe defaults; production NavHost explicitly supplies AppModule flows.
- Try-on now shows this request's model, a searchable builtin/personal picker and management entry. Switching preserves garment/image/role selection. Model list and default must both load before generation. Invalid normal defaults fall back to builtin; explicitly captured missing retry models block generation until manual reselection. Manual choice and retry consumption persist in SavedStateHandle.
- Confirmation captures provider/model id/image path/name. A stale same-id restored image is rejected at VM confirmation and passed as expectedModelImagePath to coordinator's four-argument submission.
- Personal pending results use full Fit + existing zoom viewer, explicit save/default/adjust/discard. Adjust routes to personal editor and keeps the original pending result. Save/default failures retain the accepted model and offer preference-only retry; completed models also allow setting default after reopening.
- Task kind labels/retry dispatch are exhaustive. Completed personal preview looks up the accepted PersonalModel, so deleted records never display a stale task path. Append is mannequin-only. Saved personal models are reachable through management.
- Reused LolitaSection/ImageFrame/LolitaShimmerImage, skin spacing and ObserveSkinScroll.

## Test intent / TDD evidence

Test intentions were added before their production changes for deleted personal retry, explicit choice restoration, personal confirmation/default failure and pending adjustment. Root's brief authorized source-only progression while another worker owned the exclusive Gradle slot. Therefore no unobserved red run is claimed. Additional tests exercise first-flow-load gating, captured model submission, same-id changed image rejection, personal retry navigation, seven-skin picker/preview screenshots and existing-flow compatibility.

## Validation

- `git diff --check` for all owned source/test paths: passed.
- Initial Gradle invocation could not locate a default Java runtime; no compilation ran. Reran with `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Genuine integration RED: `assembleDebug assembleDebugAndroidTest` failed only at `TryOnViewModel.kt:104`, because coordinator still exposed only 2/3-argument overloads while captured-image submission requires 4. Evidence: `/tmp/personal-model-task5b-red.log`. Root owns and is adding the missing four-argument boundary.
- ADB read-only check confirmed target `emulator-5570` is `Lolita_Personal_Model_Test`.
- No paid API calls, device clear/uninstall, backend/backup/AppModule edits or subagents.

## Integration notes

LolitaNavHost is shared with Task 5A; root requested it be excluded from worker commits and will commit all navigation glue together. Only the existing try-on/task factory, callbacks, retry dispatch plus required AppModule import were edited by 5B.

## Device validation rounds

- JDK 17 `assembleDebug assembleDebugAndroidTest`: GREEN, 40 seconds. Log `/tmp/personal-model-task5b-green-build.log`. Only pre-existing warnings outside owned source paths.
- Six focused classes: TryOnViewModelTest, TryOnScreenTest, TryOnNavigationTest, ImageTaskScreenTest, ImageTaskNavigationTest and ImageTaskUpgradeNavigationTest. 42 tests, 41 passed; only personal picker screenshot helper failed because `onRoot()` matched both activity and dialog. Log `/tmp/personal-model-task5b-device-1.log`, full original results `/tmp/personal-model-task5b-device-1-results`.
- Replaced the single-root selector and reran only the failed matrix: 1/1 GREEN, 34 seconds. Visual QA then caught that Compose's dialog `captureToImage()` captured underlying activity pixels rather than the dialog window. These intermediate picker images are rejected as evidence. Changed only picker export to `uiAutomation.takeScreenshot()` and requested a serialized short rerun. No business assertions failed in either round.
- Personal model card 14 images and pending task preview/actions 28 images visually checked in three seven-skin contact sheets. 320dp / 2x text, all seven light/dark skins; controls wrap and remain scroll-reachable. Images are explicit independent solid-color fixtures, not generated faces or paid-service samples.
- Compared information flow with approved `personal-model.html`: full Fit preview, named person/measurements, default toggle, save and adjust-as-new actions retained; actual app uses existing seven-skin components. Default initially remains unchecked so saving alone does not replace the user's chosen default without an explicit selection.

- Final picker export rerun using genuine UIAutomation window screenshot: 1/1 GREEN, 37 seconds (`/tmp/personal-model-task5b-device-3.log`). Re-exported all 14 picker PNGs; individually checked default light and the full 14-cell contact sheet. Search field, selected personal model, builtin option, close/manage actions and visible IME are correctly captured. Intermediate background crops have been replaced.
- Final artifact archive: `docs/visuals/2026-10-16-personal-model/tryon/` (28 PNGs), `tasks/` (28 PNGs), four seven-skin contact sheets and `task5b-validation.json` with dimensions/SHA256/provenance.
- Final `git diff --check` for owned paths: passed. Final source is the same production source exercised by the 42-case run; only screenshot helper changed in the subsequent corrected matrix reruns.

## Outcome and concerns

Task 5B's UI requirements are implemented and all 42 focused cases have passing evidence across the original run and corrected matrix reruns. No paid API/device clear/uninstall was used. No remaining known functional issue within this scope. The full app unit/instrumentation suite is root's serialized integration responsibility; this report does not claim that wider suite passed. Root separately found and stabilized an unrelated BudgetBackupFormatTest expected-timestamp fixture in its first full JVM run.

Owned source/tests and screenshot archive are committed separately from shared NavHost glue, as requested. Root must include the current shared NavHost diff (actual flows, picker/manage entry, confirmed-model library entry and exhaustive personal retry route) in the integration commit.
