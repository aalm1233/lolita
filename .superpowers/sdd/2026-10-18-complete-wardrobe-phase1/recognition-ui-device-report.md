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
