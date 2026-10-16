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

## Review repair round 1 — strict compressed-image validation

The reviewer identified that API26/27 BitmapFactory accepts `SkCodec::kIncompleteInput`; verified in AOSP Android8 `BitmapFactory.cpp`453–467: <https://android.googlesource.com/platform/frameworks/base/+/android-8.0.0_r34/core/jni/android/graphics/BitmapFactory.cpp>. A legal EOI suffix does not prove that all JPEG entropy was present. The previous terminator-only API26/27 integrity claim above is superseded by this repair.

Implemented separate pure Kotlin `PersonalImageIntegrity`, `PersonalJpegIntegrity` and `PersonalPngIntegrity` files. JPEG consumes Huffman entropy through every expected MCU/block, including baseline, grayscale, legal progressive initial/refinement scans, EOB runs, zero runs and restart intervals. It tracks only nonzero coefficient masks, with no pixel decode, IDCT or color conversion. PNG verifies all chunk CRCs and complete zlib inflation against exact scanline size, including Adam7. Final rendering still uses Android's decoder. No native library or runtime dependency was added.

Important experimental finding: the new damaged-body/valid-EOI real-JPEG Android regression also failed on API35 when only ImageDecoder's partial listener was used. Strict validator correctly rejected the bytes, but `isOwnedReadableImage` accepted them. A synthetically restored EOI can produce decoder success with missing rows filled, without raising a partial callback. JPEG/PNG now pass the strict integrity gate on **all** API levels before platform decoding. API28+ other supported formats still use ImageDecoder. API26/27 new personal-model source input accepts only strictly checked JPEG/PNG; GIF/WebP/HEIF and other formats fail closed. Root owns the explicit user hint to convert these files to PNG/JPEG. This limit only applies to new personal-model source photos; existing clothing/outfit image loading and generated parameter-model PNG behavior are unchanged.

Budgets are explicit and enforced before allocation/decompression or scan traversal:

- Source bytes: at most 80 MiB; dimensions 1–16,384 on each axis and at most **36,000,000 pixels**, applied in helper to every format and independently inside JPEG/PNG parsing.
- PNG expanded scanline data: at most 256 MiB, processed in an 8 KiB buffer. Exact expected count and zlib completion are mandatory.
- Progressive JPEG nonzero masks: at most 4,194,304 Long entries total (32 MiB); no IDCT or per-pixel allocations.
- Across all JPEG scans: at most 256,000,000 expected coefficient visits, computed before traversing each scan. This also bounds repeated empty refinement/EOB scans whose encoded size can be tiny. Large or unusually complex valid photos may require resizing; root owns the user-visible resize hint.

Tests / reproduction:

- Added `scripts/GeneratePersonalImageFixtures.java`, run under JDK17 source mode. It generates and independently ImageIO-decodes 15 nonpersonal baseline/progressive RGB/gray JPEGs, noninterlaced/Adam7 PNGs in RGBA/indexed/gray/1-bit modes, complete 6000×6001 JPEG/PNG over the pixel budget, and a legal 36MP progressive JPEG with 14 sparse AC scans exceeding the work budget. Resources and SHA256 manifest are in `app/src/test/resources/personal-image-integrity/`.
- Initially attempted ImageIO generation inside Android Gradle unit-test sources; Android's compile classpath intentionally lacks java.desktop. Moved fixture generation to the standalone JDK script and made unit tests resource-only. No AWT runtime dependency remains in app or unit-test sources.
- Independent Kotlin2.1/JUnit4.13.2 compiler runner writes classes only in `/tmp`, avoiding shared Gradle cache races while another worker owns the build. Initial behavior RED replicated the old suffix-only check: 6 tests, 4 failures, including real JPEG entropy truncation + EOI and shortened PNG IDAT + recomputed CRC + valid IEND. `/tmp/personal-integrity-red-behavior.log`.
- Additional RED/GREEN caught valid PNG one-byte IDAT splitting: initial 1 failure in 9 tests, corrected inflater needsInput handling; `/tmp/personal-integrity-idat-red.log`.
- Pixel/work budget behavior RED: previously accepted complete over-budget fixtures; 2 failures in 11 tests. `/tmp/personal-integrity-work-budget-red.log`. After explicit budgets, final independent runner: **OK (11 tests)**, `/tmp/personal-integrity-green.log`.
- Exclusive Gradle focused run before the last two budget cases: `./gradlew testDebugUnitTest --tests com.lolita.app.data.file.PersonalImageIntegrityTest`: **9 tests, 0 failures/errors/skips**, BUILD SUCCESSFUL3s, `/tmp/personal-integrity-gradle-green.log`. Root final full-suite run must include the final 11-case test class.
- New Android test reads the real verified `builtin/brands/with_puji.jpg`, creates a broken UUID-owned file by retaining header plus partial entropy and appending legal EOI, checks strict validator and actual `isOwnedReadableImage` paths. Initial API35 result: 7/8 passed, damaged input wrongly accepted. Strict all-API JPEG/PNG fix then passed **8/8**, no failures/errors/skips, together with `assembleDebug`, BUILD SUCCESSFUL16s: `/tmp/personal-integrity-device-green.log`. Budget changes afterward are pure validation predicates covered by the final independent 11-case JVM run; root final suite/build covers integration.
- Remaining 13 real packaged JPEGs are independently checked by the JVM suite and each damaged entropy variant is rejected. Additional hand-built restart/progressive-zero-run JPEGs were ImageIO-decoded during initial fixture preparation, and remain regression vectors. No personal photo, paid generation call, new AVD, data clear or uninstall was used.

Self-review: API26/27 still lacks a matching hardware/emulator execution environment, but the former permissive suffix branch has been removed. Its shared pure Kotlin strict gate is exercised directly on JVM and within the API35 owned-file chain. Actual old-platform decoder behavior is no longer relied upon to detect incomplete JPEG/PNG data. Resource limits fail closed; they do not make automated paid retries.

## Review repair round 2 — primary JPEG versus containing file

Verified the compatibility finding against official [Motion Photo format](https://developer.android.com/media/platform/motion-photo-format) and [Ultra HDR format](https://developer.android.com/media/platform/hdr-image-format): Motion Photo appends video after the primary image (with optional image padding), and Ultra HDR permits an additional gain-map image while legacy readers display the primary SDR JPEG. Requiring primary EOI to coincide with whole-file EOF wrongly rejected these containers.

Removed only `at == bytes.size` at the JPEG EOI check. Every expected primary scan/MCU/block must still finish correctly before EOI is accepted. The validator then stops at primary EOI and ignores the containing-file remainder; it does not certify or decode a video/gain-map payload. The existing **80 MiB bound applies to the complete file**, and all pixel, memory and entropy-work limits remain. PNG's IEND/zlib/CRC rules are unchanged.

Added two focused JVM regressions using all 13 real packaged JPEG primary images plus a legal progressive primary. Appended fixtures explicitly model container structure: padding plus `ftyp`/`mdat` boxes, a second JPEG as a gain-map payload, and a combined gain-map/video tail. They are synthetic suffix fixtures, not camera-recorded Motion Photos or certified Ultra HDR files. Valid primary JPEGs are accepted with those suffixes; damaged primary entropy plus synthetic EOI and the same suffixes (including a complete second JPEG) remains rejected.

Independent Kotlin2.1/JUnit runner RED: 13 cases, one failure in new valid-container acceptance, `/tmp/personal-integrity-container-red.log`. GREEN after the narrow EOI fix: **OK (13 tests)**, 0.314s, `/tmp/personal-integrity-container-green.log`. No device/Gradle operation was run while worker5B owned that slot. Root will include the 13-case class in the upcoming exclusive full `testDebugUnitTest assembleDebug assembleDebugAndroidTest` integration run; shared-Gradle evidence is pending that run. The already-passing device suite is not repeated for this parser boundary change.
