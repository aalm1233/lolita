# Task 5A — personal model library and editor

Scope: only new `ui/screen/personalmodel/*.kt`, their instrumentation tests, library/editor routes in `Screen.kt` / `LolitaNavHost.kt`, and the settings entry. Existing try-on/task dispatch remains owned by Task 5B. Shared `LolitaNavHost.kt` is excluded from this worker commit and will be committed by root to avoid staging Task 5B hunks. Worktree: `/Users/dengtong/.codex/worktrees/personal-model/lolita`, branch `codex/personal-model`.

## Implemented

- `PersonalModelViewModel` + `PersonalModelScreen`: observe confirmed models and a valid default; retain the builtin choice; full Fit image / full-screen zoom, rename, delete, default choice, create and adjust. Invalid saved defaults display builtin. Delete does not touch tasks/history.
- `PersonalModelEditViewModel`: UUID in SavedStateHandle and durable raw `PersonalModelDraftData` rows; never initialize/overwrite before a draft/adjustment/retry has loaded. Numeric raw strings persist even when invalid. Saves serialize through a mutex and submission waits for pending writes.
- Draft reference roles and effective task roles are separate. PHOTO excludes VIRTUAL_FACE; PARAMETERS excludes all personal photos while keeping switch-back draft references. Name is trimmed before constructing the validated request.
- Adjustment uses PHOTO originals or the accepted PARAMETERS model as VIRTUAL_FACE. The original accepted row is never updated; a newly generated result is submitted to the task center for independent confirmation.
- Photo Picker import copies to UUID internal storage, validates with `ImageFileHelper.isOwnedReadableImage`, and cleans only its new copy on failure/cancellation. A completed photo is adopted in a non-cancellable durable save. Remove/replace cleanup goes through repository reference protection. API 26/27 show PNG/JPEG guidance and failures give a resave/reselect action.
- Retry prefill waits for the original task; missing photo files preserve measurements, remove invalid references and prompt reselection. No automatic paid retry. Submitting is synchronously locked, captures immutable request/paths, and prevents repeated submission after acceptance.
- Editor uses existing seven-skin cards/sections/topbar/icons/shimmer/scroll registration and a consumed Scaffold/IME bottom action. Photo inputs are compact three-column slots, full-screen preview uses actual source images. No demonstration/template result image is shipped. Dirty back requires explicit discard; rotation does not discard. Load/copy/save/submit transitions guard back and editing.
- Added `Screen.PersonalModels` / `Screen.PersonalModelEdit` query route, appended factory defaults, settings entry. Successful submission replaces the editor route with task detail so back cannot replay accepted navigation.

## Verification and test intent

Authored tests before associated implementation intent, under the parent-authorized serialized-build workflow. **No isolated RED execution was available before source implementation; do not report a completed RED→GREEN cycle.** Parent owns the shared build/device slot; this worker has not started Gradle or changed device data.

Instrumentation classes:

- `PersonalModelEditViewModelTest` (10 cases): invalid raw restore; retryable raw draft save failure blocks submission; no overwrite during delayed draft read; PHOTO→PARAMETERS filtering; mixed/hidden role filtering; delayed retry prefill and once-only submission; missing retry photo reselection; cancelled copy; failed adoption cleans new physical file and preserves original; parameter adjustment preserves accepted virtual face. (See actual source/test count if additional checks are added.)
- `PersonalModelViewModelTest`: stale/deleted default fallback, trimmed rename, explicit default reset on removal.
- `PersonalModelScreenTest` (3 cases): real Compose parameter form/confirmation/navigation; confirmed library choose/rename/delete and builtin choice; seven-skin light/dark screenshots at 360dp normal and 320dp font scale 1.5. Screenshot output is `files/personal-model-contact-sheets/` on the test host.

Executed by this worker:

- `git diff --check` for owned tracked paths: exit 0.
- Initial Gradle attempt failed before build because the shell did not expose a Java runtime; reran with the Android Studio bundled JBR, without source changes.
- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_SERIAL=emulator-5570 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lolita.app.ui.screen.personalmodel.PersonalModelEditViewModelTest,com.lolita.app.ui.screen.personalmodel.PersonalModelViewModelTest,com.lolita.app.ui.screen.personalmodel.PersonalModelScreenTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`: **BUILD SUCCESSFUL in 1m 10s; 14 tests, 0 failures/errors/skips**. The run recompiles current app and instrumentation sources. Log: `task-5a-device.log`; actual generated JUnit XML was inspected under `app/build/outputs/androidTest-results/connected/debug/`.
- Existing unrelated compiler warning: `ItemImportMigrationTest.kt:46` infers an intersection type for a reified type parameter. No new owned-source compiler warning was shown.
- Device: task-created independent `Lolita_Personal_Model_Test` AVD, serial `emulator-5570`, Android 15. Parent verified the debug signing certificate before handoff. No uninstall, clearing or user-device operations were performed.
- Exported all **28 actual PNGs** to `docs/visuals/2026-10-16-personal-model/`; two JPG contact sheets are derived only from these screenshots. `verification.json` records run metadata and SHA256. Inspected both seven-skin contact sheets, DEFAULT light 360 and VICTORIAN dark 320/font1.5 at full resolution: no horizontal clipping or blocked generation action. Long content remains in the scroll area. These captures show parameter mode; PHOTO Picker interaction uses the real launcher but was not manually exercised against the system picker in this run.

The approved mockup is retained outside the project unchanged. Implemented hierarchy follows its library → editor → preview workflow, mode choice, compact photo row, required height/weight, expandable optional dimensions, name and one primary action. Skin typography and tokens determine the native rendering. System picker end-to-end operation and visual coverage of real user photos remain a final integrated/manual verification concern; VM tests cover cancellation/adoption boundaries and preserve original files.

## Remaining handoff

Task 5B must connect personal preview save/discard/retry and model picker/management entry in try-on, including deleted-model retry selection. Root/data workers own coordinator lifecycle, complete image validation, Room/backup and existing task behavior. Do not infer those are verified from this report.
