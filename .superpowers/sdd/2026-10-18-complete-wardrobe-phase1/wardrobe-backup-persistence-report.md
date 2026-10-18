# Wardrobe backup and recognition persistence verification

Owned files: `WardrobeBackupTest.kt` (12 tests) and `RecognitionPersistenceTest.kt` (6 tests). New tests only; no root production files edited.

The 18 tests compiled into the existing 11:47:56 AndroidTest APK and ran on dedicated emulator-5582: 13 controls passed, 5 assertions failed as real RED. Earlier 53-device/357-JVM evidence does not cover these tests. No GREEN claim is made before root production fixes are built and rerun.

Fixtures use unique ContextWrapper directories, in-memory real Room, actual owned UUID image bytes and actual public BackupManager JSON/ZIP entry points. MediaStore export URIs are tracked and deleted only by their creating fixture; ImageFileHelper configuration is restored to AppModule on cleanup. Real generation/recognition network transports are never called. Controlled result functions are in-process and use no real credentials.

Backup coverage includes nested storage topology, old v9 defaults, every new table, draft/evidence/source image SHA and shared-path mapping, completed v4 actual built-in head input plus source-original snapshot, keepSourceImages, stable order/payment fields and album ordering, provenance after task deletion, pre-write rejection of unknown semantics/cycles/cross-room/orphan or ineligible evidence/missing region images/recognition hash mismatch, and rejection of changed immutable v4 input bytes.

Persistence coverage includes region identities/hash/dimensions, five-region limit rollback and removal cleanup; saved review/result and evidence across repository recreation, accepted draft commit then task discard without adding evidence to the album; stale confirmation/key-change zero calls; process recovery and restore-barrier cancellation without auto resend; mutated crop input rejected before the controlled transport.

## Actual RED execution

Used only the already-installed main/test APKs after AVD, package, signer and DEX class verification. No install or APK output changes were performed while borrowing the short device window. Main/test signer matched the established debug certificate. Logs and exact command: `artifacts/wardrobe-device-2026-10-18/backup-persistence/red-instrumentation.log`, `red-summary.json`, `red-command.json`, `red-installed-apks.json`, `red-installed-signatures.txt`.

Completed 18 tests: 13 passed, 5 failed. Actual failures:

1. Missing draft evidence region accepted by preview.
2. Recognition region content hash mismatch accepted by preview.
3. Completed v4 immutable input overwritten but JSON export accepted it.
4. VISUAL brand guess accepted as adopted evidence by preview.
5. Modified recognition crop bytes reached the injected result function once (expected zero).

Root owns all five production fixes. Device/Gradle window released to AI settings GREEN. Both actual JSON/ZIP graph round trips, old-v9 defaulting, topology, order/image-order preservation, provenance after task removal and all other persistence controls passed. Latest source additionally checks both preview and import even if preview erroneously succeeds, tracks unexpected successful export URIs for cleanup, and strengthens the same v9 test with a legacy v1 image-task row missing keepSourceImages; these minor test refinements await next compiled GREEN.

## First production-fix verification (intermediate)

The later `recognition-lifecycle-red9` main/test APK was signature/version checked and installed with `-r` on the same dedicated AVD, then the exact two backup/persistence classes ran:18 completed,7 passed,11 failed. All6 persistence cases passed, including changed crop bytes rejected before the injected transport; completed-v4 input tampering was also rejected by both exports. The11 backup cases stopped at the initial legal-fixture export because the new measurement protocol correctly requires explicit target/basis/unit on adopted hair-length evidence. The fixture has now been synchronized with its explicit quoted text `商品实测发长60 cm` using `HAIR_LENGTH` / `PRODUCT_MEASURED` / `CM`; no production validator was weakened. This intermediate run is preserved as `green18-instrumentation.log` and `green18-summary.json`, whose contents explicitly do not claim GREEN. A new compiled run is pending.

Two additional provider controls are now source-complete in RecognitionPersistenceTest (8 cases; owned total20): the DeepSeek request reads only its own provider key and stores the returned actual model; changing revision or selected provider during key reading rejects submission before Room claim or transport, preserving the exact PREPARED unsent row. They use controlled functions only and have not yet run.

## Twenty-case GREEN

After the explicit measurement fixture synchronization, the current20-case APK was checked against catalog’s installed main SHA `7b0dcbdb8b89b251fff6a2ba34b7a92832908b9f201ca3b9462aed45dec877cd` and test SHA `43b1f06c88e5b156fd3bab428745f4d157b9004e39ab66eb9553162f63bd7ba6`, the matching debug certificate and main version113. DEX contained all20 methods. Direct exact-class instrumentation on emulator-5582 completed **20/20 GREEN in6.392s**, including both new provider controls. No build or install was performed for this run. `green20-*` preserves command, actual installed APK evidence, test source snapshots and all20 results.

Four further backup cases are now source-complete (total24: Backup16+Persistence8) but not run: external JSON missing the completed-v4 actual head preserves its frozen request and paths with `sourceResourcesMissing=true` and no service restart; head preference JSON and ZIP round trips keep metadata with `preference-only` and exclude unreferenced template bytes; ZIP SHA dedup maps two different owned source paths to one fresh UUID; two same-byte recognition regions retain distinct IDs/metadata after dedup and removing one region preserves the shared image until its final reference is removed. These new cases do not inherit the20-case GREEN.

## Final24-case verification

The final missing-resource-flag APK main SHA `c2d25a4a9cc475c00bac5c247a9c70f339083e5a480efd6a43f5eb071fc52da0`, test SHA `41c9aa917847cfafbfdf4c4fb50257bc81e75bc1d99a33f99d6a9f1d01116a39`, signer/version113 and all24 DEX test methods were independently verified. Exact two-class direct instrumentation on emulator-5582 completed **24/24 GREEN in3.991s**. All four added backup boundaries passed. `green24-*` contains installed APK copies/references, full source snapshots, exact command, raw instrumentation and parsed results. No installation or build was performed for this run. Real provider calls remained zero.

## Final fresh-AVD regression with draft baseline and storage fixes

All24 backup/persistence cases were rerun in the final fixed44-case Room command on fresh emulator-5586 / Lolita_Wardrobe_Final_35. All44 passed in3.374s, followed by actualUI3 in21.267s on the same installed APKs (main2f77a10a…/testba610ed0…). The valid existing-item draft fixture explicitly records its original Item.updatedAt baseline; the production stale-draft guard is not relaxed. Final source/command/APK evidence and accurate47-case combined count are in final-5586/final47/. No real transport or paid request occurred.

## Later legacy ZIP identity and missing-history regressions

The subsequent legacy suite found5 actual backup failures: global SHA merging changed old pending/business identity, including a prevalidation rejection that prevented an import barrier from being entered. Root narrowed dedup to task-exclusive v4 actual inputs and recognition regions without weakening ownership guards. Current WardrobeBackup has18 cases (root owns the two later additions/fixture refinement). The18th case had a preserved actual1/1 RED: explicitly blocked historical input prevented all later ZIP export. Root’s final exemption preserves only flagged task-exclusive missing source/original paths while business/pending/unmarked missing inputs still reject. Final **Wardrobe18 + old ImageTaskBackup9 + Personal6** all passed within52/52 in6.282s on emulator-5586; current build390 JVM0fail/error/skip. See final-5586/legacy-final and historical-input-red. The earlier24/47 evidence remains historical rather than being reused for these new cases.
