# Recognition UI device verification

Added `WardrobeRecognitionFlowTest.kt`, three actual Android Compose tests using the real ItemEditScreen, ItemRecognitionPanel, editor/recognition ViewModels, independent Room and UUID photo/preferences directories. In-process candidate fixtures use a throwing key reader and controlled transport; no actual recognition submission or network request occurs.

## Behavior verified through actual controls

The workflow switches A/S to B/M and back, checks that incompatible candidates become disabled/unselected, adopts one measurement first, reopens an already APPLIED task to adopt the remaining common color, saves using the actual editor save button, recreates the editor, and opens the stored evidence using its real button. Both evidence rows, source-region paths and adopted values survive; original album ordering, name, notes and absence of a Price record are asserted. The repository is used to arrange the initial task/result and inspect persisted outcomes; it does not replace the actions under test.

Two additional test cases each loop all seven real skins, light and dark respectively. They require the actual region image pixels, the enabled apply control, expected selected/disabled candidate state and complete unellipsized apply-label glyph bounds. Each exports two actual native-window screenshots, for **28 PNGs total** at 1080×2400.

## Native viewport and diagnostics

Android Dialog has its own window. LocalDensity alone did not change that window’s native font configuration, so the final test rule sets the dedicated emulator to 320dp width / font_scale2.0 and the correct native night mode before launching the test Activity. Its finally block restores the previous density override, font preference and night mode, and waits for Resources to match both transitions. Final read-only verification confirms physical420dpi with no override, font_scale1.0 and night=no restored.

Earlier failed diagnostic runs are preserved. Duplicate image semantics were resolved by selecting the meaningful outer image node; Compose-root capture cannot see the native Dialog surface, so final capture and source-green pixel assertions use UiAutomation.takeScreenshot. TextLayoutResult.hasVisualOverflow was diagnosed as a paragraph-width false positive (actual glyph right326.057 within a 328px box while paragraph maxWidth was524). The final test asserts all line glyph bounds, no ellipsis, complete text and actual Dialog fontScale2 instead. No production apply-button change was required.

## Final result and artifacts

On `Lolita_Wardrobe_Final_35` / `emulator-5586`, the final fixed same-signature version113 APK passed **3/3 in21.267s** after the same package’s **44/44 Room in3.374s**. Full logs/commands/source hashes/APK hashes: `artifacts/wardrobe-device-2026-10-18/final-5586/final47/`. Main SHA256 `2f77a10a7481138c632448e543c40a3757a3e23a13bbf6636c2334451d0405ad`; test SHA256 `ba610ed03e43945208bea534196603587f37e08b4676833b9d3dadffe8e5829f`.

Final 28 screenshot originals and SHA256 manifest: `final47/recognition-ui-native/`. `contact-sheet-light.jpg` and `contact-sheet-dark.jpg` are labelled overviews derived only from those verified original screenshots. All seven skins, both modes and both key pages are complete; the source green pixels are checked in the actual visible surface. Full default/light apply and Victorian/dark region images, plus both complete contact sheets, were visually inspected. This is actual rendered product evidence rather than generated design artwork.

Only this dedicated AVD was configured for the native UI tests. Existing devices were not cleared/uninstalled, real preferences were not modified by fixtures, no actual API transport or paid call was made, and all shared build/device resources were released after the run.

## Subsequent old-generation regression closure

Root assigned the original generation/navigation coverage after final47. The actual frozen run completed127:112 passed,14 failed and1 explicit opt-in socket case skipped. Raw results are retained under final-5586/legacy-regression. The10 old UI/VM/navigation classes contain66 tests; related request/backup classes contain61. No success is inferred from adb’s zero exit status when AndroidJUnitRunner prints failures.

Eight MannequinScreen failures originated in an old fixture with unconfirmed Category semantic, so the disabled generation/key button correctly did not open a dialog; the Key field’s real label was unchanged. Fixtures now explicitly confirm OP/KC/SK and assert KC in the submitted subject. Old single-product title/action selectors and TryOn’s completed-selection label were aligned with the current interface; no production UI or eligibility guard was changed. The complete two affected UI classes passed15/15 in75.698s. Existing legacy LocalDensity presentation tests are not presented as new native double-font Dialog evidence.

The skipped controlled socket case was explicitly enabled with a random-port server bound only to host127.0.0.1 (the emulator uses its10.0.2.2 host alias). It completed1/1 in1.081s, observed exactly one controlled POST, and never contacted an official API. Command, server metadata and APK evidence are under legacy-corrections.

Five original backup failures exposed global SHA dedup collapsing old business and pending-output identity. Root narrowed dedup to exclusive actual v4 inputs and recognition regions, preserving old business/output path identities and all ownership guards. All15 old ImageTaskBackup/PersonalModelBackup cases passed after the fix. Root’s new input-dedup control was clarified to keep an existing business image identity independent; only exclusive head inputs must merge. A new18th backup case then captured a genuine historical-missing-input ZIP-export failure before root’s minimal exemption. Both failed stages remain retained.

Catalog’s new draft deletion tests reached three explicit target assertions before its fix (RED2,0.272s), then all3 passed (0.32s). An earlier two-case exception classification was ambiguous; the fixture added initial-state controls and caught post-delete exceptions to prove all three actual target failures. Reports do not count those earlier exceptions as separate passing evidence.

The final full `testDebugUnitTest assembleDebug assembleDebugAndroidTest` completed in12s:390 JVM tests,0 failures/errors/skips, stable source SHA before/after build and both APKs. Dedicated emulator-5586 received same-signature version113 with install-r, then **52/52 passed in6.282s**: WardrobeBackup18, ImageTaskBackup9, PersonalModelBackup6, ItemDraftDeletionRace3, RecognitionLifecycle9, ItemDraftBaseline6 and RecognitionDeletion1. Main SHA `03dc649e7d7e7a2c9e394c87b0f60e843d850e32496bc6f46e8cb6a0b120aac1`; test SHA `68dd0e222fc7937184e986666f0da977d9191212824249bca819221009ef628e`. The original127 and new47 were not silently claimed rerun on this final package; only affected groups were repeated. Complete counts/commands/source/APK hashes: final-5586/legacy-final and legacy-verification-rollup.json.

This follow-up changed tests only in this agent’s scope, adding no user interface or product flow; no new concept artwork was needed. Gradle and5586 were released to root for the separate version114 clean Release procedure. No release operation, data clearing, uninstall or real paid API was performed by this agent.
