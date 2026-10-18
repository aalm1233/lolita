# Task 6 shared rectangular image crop

## Public APIs

```kotlin
@Composable
fun ImageRegionCropDialog(
    sourcePath: String,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit
)

ImageRegionCropper.preview(sourcePath): CropPreview
ImageRegionCropper.crop(context, sourcePath, region: NormalizedCropRect,
    rotationDegrees: Float = 0f): CropOutput
```

`NormalizedCropRect(left, top, right, bottom)` contains finite values in `[0,1]` and refers to the entire EXIF-corrected, manually rotated image. It does not refer to the viewport or uncorrected source file. `FULL` selects the entire transformed image. `CropOutput.path`, `width`, `height` describe the actual derived JPEG, with `sourceWidth`, `sourceHeight`, `sampled` for explaining resolution limits. Integer geometry is isolated in `CropGeometry`/`CropPixelRect`/`CropDimensions`.

## Behavior

- Source path is read-only. The cropper performs no API calls and never modifies the source or its EXIF.
- All eight EXIF orientations (including mirroring) and manual rotation are mapped into visible coordinates. UI supports 90-degree turns, a +/-15-degree fine correction slider, four-corner resizing, rectangle movement, one-finger image panning outside the rectangle and two-finger image zoom/pan. Whole-image, reset and fit-to-screen controls are provided.
- Preview decoding is capped at 1.6 million pixels. Crop decoding uses `BitmapRegionDecoder` on only the inverse-mapped selected region, capped at 6 million decoded pixels. Final files have at most a 4096px edge and 8 million pixels; output never enlarges a decoded source. UI zoom is explicitly described as inspection only.
- Final pixels are drawn into a new bitmap and JPEG-compressed, stripping source EXIF/GPS/camera/comments. The sanitized cache temporary is copied through `ImageFileHelper` to a UUID file in `files/images/`, then deleted.
- Users must generate and inspect the actual derived file before confirmation. Its actual dimensions and any downsampling are displayed. Confirmation transfers exactly that file to the caller. Cancellation/disposal and returning to adjustment clean owned derived files via reference protection; the caller owns a confirmed returned path and must persist or clean it.
- UI consumes skin spacing, corners, Material theme colors, `ImageFrame`, `LolitaShimmerImage` and `ObserveSkinScroll`. It introduces no icons or paid operations.

## Verification

- Tests were added before production code. Root recorded missing interfaces in `task-6-api-red.log`.
- Root's `task-6-api-green-final.log` reports successful `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`. `CropGeometryTest` XML reports 2 tests, 0 failures/errors at 2026-10-18T02:43:16.
- Native Android tests `ImageRegionCropperTest`: green selected pixels with red/blue excluded pixels, precise dimensions, UUID JPEG ownership, unchanged source SHA, EXIF removal, 90-degree rotation, EXIF rotation, 10-degree fine correction, EXIF mirror and long-image region decoding. These tests are compiled but still await execution on the root's independent AVD.
- Real Compose tests `ImageRegionCropDialogTest`: preview required before confirmation; cancellation clears the real derived file; dragging both rectangle corners returns only the actual 100x100 green region; source SHA preserved. Compiled/execution status must be checked against root's final build after all test changes.
- A nullable-parent-file warning in the native crop test was changed to `requireNotNull` after the successful build. No Gradle or emulator was launched by this agent.

No claim of native pixel or gesture test success is made before AVD execution. AI output-quality assessment remains separate from deterministic crop verification.

## Native execution update

On fresh `Lolita_Wardrobe_Test_35` / `emulator-5582`, all five native crop pixel tests and both real Compose crop tests passed in round2. The original orientation assertion was corrected after evidence showed Android ExifInterface's neutral UNDEFINED compatibility value; tests now also reject actual Exif data bytes. The Compose cancellation test explicitly advances the test clock before awaiting asynchronous disposal, while continuing to require deletion and unchanged source SHA. Native green-region and actual two-corner dragging, 90°/10°/EXIF transforms, long image decoding and metadata stripping passed.

Additional actual-preview pixel assertions and screenshot exports were added after round2 and await the root's final integration rebuild. All logs and APK/signature evidence are under `artifacts/wardrobe-device-2026-10-18/`; no paid endpoint was called. The initial implementation commit is `e5b173e`; final device fixes remain unstaged until final verification.

## Final device verification — supersedes preliminary pending status

- Implementation commit `e5b173e`; actual-device fixes and all-old-fields migration regression commit `f629251`.
- New dedicated `Lolita_Wardrobe_Test_35` / `emulator-5582`, API35 ARM64 Pixel7; persistent launchctl job, same-signer version113 install-r only, no old AVD/USB/uninstall/data clearing or paid requests.
- Fresh build `storage-crop-final-device-build-3.log`: 357 JVM tests, 0 failures/errors, Debug and AndroidTest APKs successful. Latest test-host-only APK rebuild `storage-crop-dialog-host-build.log` also successful.
- Final exact 14-class instrument run: **53 tests, 0 failures**, 20.495 seconds. Evidence: `artifacts/wardrobe-device-2026-10-18/final-targeted-instrumentation.log` and structured summary/command. Includes all storage/crop/migration tests, full WardrobeRegistration/CategoryEntry, AI ownership/admission and independent settings tests.
- Real device exposed cabinet/room action overlap due to Box content; explicit Columns fixed it. Real touches/search/affected-count move now pass. Large-font LazyColumn queries use scroll-to-node. Seven skins × light/dark, 320dp/2x text, selection and footer reachability pass with correct theme Surface host.
- Native crop green-only pixels/sourceSHA/metadata stripping, 90°/10°/EXIF/mirror/long-image cases pass. Actual rendered preview pixel is green; two-corner gesture returns exactly 100×100 JPEG; cancel removes derived file, source unchanged. Test-only zero-sized Dialog host reproduced twice and diagnosed; fillMaxSize Box fixes it, independent crop2 plus final53 pass. All failed runs retained; none counted as success.
- Curated **16 actual screenshots** (14 storage, 2 native crop previews) and SHA/source manifest: `artifacts/wardrobe-device-2026-10-18/verified-screenshots/` and `verified-screenshots.json`. Compose-root Dialog blank images are historical diagnostics, not final screenshots.
- Migration test covers all 30 v29 tables / 195 old columns, all FK relationships, six rooms, image SHA, new defaults and integrity check; passes on the dedicated AVD.
- Catalog's room/node-prefilled new-item entry is integrated and tested; its remaining S1 code is committed by its owner. Root retains DB/backup/recognition/provider integration ownership. Missing WardrobeEditorRecognitionTest was reported; no claim of later recognition UI/provider completion.
- Gradle, AVD and shared index released; emulator and app data retained. Full truth/history is in the artifact README.
