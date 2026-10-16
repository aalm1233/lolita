# Task 2 — personal-model persistence

Implemented in `/Users/dengtong/.codex/worktrees/personal-model/lolita`, branch `codex/personal-model`.

## Delivered contracts

- Room 29: `personal_models`, `personal_model_drafts`, nullable task accepted-model id and nullable historical try-on model id/name. No historical foreign keys. Additive 28→29 migration registered; KSP schema 29 generated.
- `PersonalModelDao` provides accepted-model and draft operations; `PersonalModelRepository(database, deleteImage)` exposes observe/get/getAll/rename/delete/getDraft/getDrafts/saveDraft/deleteDraft. Rename trims and permits 1–60 characters, modifies only name/update time. Removal cleanup happens after committed transaction. Draft replacement only releases removed references.
- `PersonalModelDraftData` version 1 stores raw name/measurement/instruction strings, mode/appearance and ordered roles. Decode rejects unknown/missing keys, unknown enums, noninteger/unknown versions and duplicate roles. Incomplete raw input is preserved. No image path keys or credentials. Entity validates UUID plus enumerable role/path cardinality and duplicate paths.
- `AppPreferences.defaultPersonalModelId` and nullable setter use an independent Long DataStore preference. UI must resolve it against observed existing models and show built-in when absent; preference is not a foreign key.
- `ImageFileHelper.deleteImage` checks model and draft references in the same existing Room transaction. `isOwnedReadableImage` requires precise context-files image path, canonical parent ownership, UUID filename, 1–80 MiB and bounded complete decode. API 28+ ImageDecoder rejects partial input. API 26/27 conservatively accepts PNG/JPEG/WebP/GIF complete terminators/container size and BitmapFactory decode; API 26/27 device coverage was unavailable.
- Personal generation task sources remain protected in awaiting-confirmation/failed/interrupted/cancelled states until explicit discard/completion/ownership transfer. v1 behavior remains unchanged. Existing provider enum converter already handles the new entity: no Converters mutation was necessary.

## RED / GREEN evidence

All commands used `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

1. RED before production implementation:
   `./gradlew compileDebugAndroidTestKotlin`
   Log `/tmp/personal-model-task2-red.log`: BUILD FAILED in 6s on expected absent PersonalModel/PersonalModelDraft/Repository, schema migration, DAO and new task/history fields. Compilation-level RED was explicitly permitted by the task.
2. GREEN draft JVM:
   `./gradlew testDebugUnitTest --tests com.lolita.app.domain.model.PersonalModelDraftDataTest`
   Log `/tmp/personal-model-task2-green.log`: BUILD SUCCESSFUL in 13s; 2 draft tests pass.
3. A simultaneous cross-worker Gradle build caused a `dexBuilderDebug` desugar graph FileNotFound. Log `/tmp/personal-model-task2-build.log`; production compile succeeded, but Android test compile had not yet executed. All workers then serialized Gradle access. This was not counted as successful verification.
4. Rename boundary RED on the independent AVD:
   `ANDROID_SERIAL=emulator-5570 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lolita.app.data.PersonalModelPersistenceTest#renameOnlyChangesNameAndUpdatedTimeAndDeletingModelPreservesHistory -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
   `/tmp/personal-model-task2-rename-red.log`: expected rejection of 61-character name failed; changed repository limit to 60.
5. First whole-device run passed 6/7; valid image ownership failed. Diagnostic `/tmp/personal-model-task2-ownership-diagnostic.log` showed Java canonical root `/data/data` differs from normal Context path `/data/user/0`. Fixed ownership comparison to exact Context path plus canonical parent (same structure as preexisting generated-image checker); removed diagnostic duplication.
6. Final GREEN:
   `ANDROID_SERIAL=emulator-5570 ./gradlew connectedDebugAndroidTest assembleDebug -Pandroid.testInstrumentationRunnerArguments.class=com.lolita.app.data.PersonalModelPersistenceTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
   `/tmp/personal-model-task2-device-green.log`: Starting/Finished 7 tests on Lolita_Personal_Model_Test(AVD) - 15; BUILD SUCCESSFUL in 12s. XML confirms tests=7 failures=0 errors=0 skipped=0. `assembleDebug` passed in the same invocation.
7. Task 1 worker independently serialized the full bare `testDebugUnitTest` suite before the device run: 39 suites, 276 tests, no failures/errors/skips, includes these 2 draft tests. Root has that worker's report/evidence.

AVD identity was verified with `adb -s emulator-5570 emu avd name`: `Lolita_Personal_Model_Test`. No other device was selected, cleared or uninstalled.

## Covered behaviors

Migration old-task/history field preservation; null new snapshots; foreign-key integrity; rename only name/time, name limit and editable request reconstruction; history survives accepted-model deletion; draft survives DB reopen; persisted draft protects photo; explicit discard releases it; draft replacement releases only removed photo; accepted ownership transfer preserves image; queued personal try-on snapshot protects deleted accepted model image until task deletion; pending/failure/interruption/cancellation personal-generation inputs stay protected; built-in default for missing id; exact path/UUID/readable image checks; strict raw draft round trip and decode rejection.

## Integration / self-review notes

- Root owns AppModule repository wiring and task pipeline. The old `ImageTaskDao.interruptUnfinished` SQL forcibly cleared all sources; root was notified to preserve PERSONAL_MODEL source images there. Task-level retention cannot compensate for SQL clearing the list.
- The coordinator must insert accepted model before dropping task/draft ownership and mark task.personalModelId transactionally for idempotent confirmation; these fields/APIs are ready.
- Root/UI must resolve default ids against current model observation, prevent premature draft overwrites, and omit hidden PHOTO roles from submitted PARAMETERS request. Draft intentionally preserves raw hidden state; request construction enforces effective input.
- API26/27 format integrity path was inspected but not run on a matching device; current independent AVD is API35. API28+ complete ImageDecoder path is device tested.
