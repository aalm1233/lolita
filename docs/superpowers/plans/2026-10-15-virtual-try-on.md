# Virtual Try-On Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用内用固定虚拟模特与衣橱服饰、小物生成整套搭配图。

**Architecture:** 官方 OpenAI 多图编辑；Keystore 加密本机个人密钥；纯领域选择/提示词策略＋有界网络传输＋薄 Room 历史仓库。Compose ViewModel 使用 StateFlow，图片复用 ImageFileHelper；试衣历史独立于真实穿搭。

**Tech Stack:** Kotlin 2.1.0 / Compose BOM 2024.12.01 / Room 2.7 / Gson / HttpURLConnection / Android Keystore。

**Spec:** `docs/superpowers/specs/2026-10-15-virtual-try-on-design.md`

## Global Constraints

- Android compile/target35、min26、JVM17，中文界面、七皮肤；不新增依赖。
- 固定官方 HTTPS endpoint；n=1、quality=medium、size=1024x1536、output_format=png。
- 最多8件、每件1图，输入长边≤1536、每张≤10MiB、总≤40MiB；无付费自动重试。
- 密钥仅 Keystore 加密 noBackupFilesDir；不记录、不导出、不回填表单。
- DB26→27、备份6→7；旧备份缺失试衣数组为空，历史不影响账本/穿搭/归属。
- 用户 GO 授权连续实施；实现由当前代理完成，最终独立审阅。官方实调用需本机独立 API Key，目前未配置。

## Review Focus

- 图片缺失/删除与加载失败：阻止空图/部分图提交，保留选择。
- 取消/超时：无自动重试，无未引用文件，无把取消当成功。
- 从不同入口重复生成：全局互斥，准确保留每次选择快照。
- 无效/巨量/非图片响应：明确错误，无损既有数据，不泄漏错误体与密钥。
- 旧备份与共享图片路径：完整导出恢复新记录，删除仅清理无人引用文件。

---

### Task 1: Selection policy and official API protocol

**Files:** create `domain/model/TryOn.kt`, `data/remote/TryOnImageApi.kt`; tests `src/test/.../domain/model/TryOnTest.kt`, `src/test/.../data/remote/TryOnImageApiTest.kt`.

**Interfaces:** `TryOnSelection(itemId:Long,name:String,imagePath:String,role:TryOnRole)`; `TryOnRequest(selections:List<TryOnSelection>)`; policy validates 1–8 unique rows with ≥1 garment; prompt maps reference indices. `TryOnImageApi.generate(apiKey:String,images:List<ByteArray>,prompt:String):ByteArray` with cancel support and injectable connection factory.

- [ ] Write failing tests for accessory-only/duplicates/empty image/limit, role ordering and prompt reference mapping.
- [ ] Run `./gradlew testDebugUnitTest --tests '*TryOnTest'` and observe missing behavior.
- [ ] Implement policy and prompt with user names delimited as descriptive data.
- [ ] Write/run API tests using captured multipart transport for model/reference ordering and one request; cover auth, redirect, malformed/empty/oversize responses and cancellation.
- [ ] Implement bounded official API client; rerun focused tests, then commit.

### Task 2: Private configuration, image preparation, and default model

**Files:** create `data/tryon/TryOnSettings.kt`, `data/tryon/TryOnImages.kt`, `assets/tryon/model.png`; extend `ImageFileHelper.kt`; device tests `TryOnInfrastructureTest.kt`.

**Interfaces:** `TryOnSettings.hasKey()/saveKey(String)/readKey()/clearKey()`; image normalization accepts readable local Item paths and fixed asset, returns ordered JPEG bytes; generated PNG written through ImageFileHelper.

- [ ] Write device regression for encrypted key (no raw plaintext), replacement/clear, normalization and failed/invalid output cleanup.
- [ ] Run device test to see missing implementation failure.
- [ ] Implement Keystore storage with atomic replacement in noBackupFilesDir and bounded bitmap decode/encode.
- [ ] Generate neutral adult fixed-model reference from approved model image with built-in imagegen; save asset and provenance prompt/SHA256.
- [ ] Rerun device tests; commit.

### Task 3: Room results and complete backup compatibility

**Files:** create `TryOnResult.kt`, `TryOnDao.kt`, `TryOnRepository.kt`; modify `LolitaDatabase.kt`, `AppModule.kt`, `ImageFileHelper.kt`, `BackupManager.kt`, `BackupFormat.kt`, backup UI; generated schema27.

**Interfaces:** result contains id/name/imageUrl/itemIds/itemNames/model/createdAt; DAO observes/list/inserts/renames/deletes. Repository generates once under mutex, validates fresh Item image snapshots, prepares inputs, calls API, validates/writes PNG then inserts result with cancellation-safe cleanup. Results have no Item FK.

- [ ] Write failing Room/backup tests for preservation on migration, result history, deletion protection, stale source image, generation failure cleanup, duplicate request lock, old format6 empty history and ZIP roundtrip.
- [ ] Run focused device test; verify missing behavior.
- [ ] Add schema/migration/DAO/repository/AppModule; register image references.
- [ ] Wire export/import/preview/count/validation/image mapping/CSV; update format7 compatibility rule.
- [ ] Run focused tests and full JVM suite; commit.

### Task 4: Skin-aware trial workspace and navigation

**Files:** create `ui/screen/tryon/TryOnViewModel.kt`, `TryOnScreen.kt`, `TryOnPicker.kt`; modify `Screen.kt`, `LolitaNavHost.kt`, `SettingsScreen.kt`, `ItemDetailScreen.kt`, `CoordinateDetailScreen.kt`.

**Interfaces:** state contains owned wardrobe/category data, selection snapshot, generating phase/error, results and configured key flag. SavedStateHandle retains compact selection; no secrets/bitmap data. Entry optionally selects item or coordinate; the picker supports search, category, image and role choices.

- [ ] Write failing device tests for selection persistence across search/filter, switching source image, role correction, double click, failure retaining selection, successful result and deletion; test empty/configured-key behavior.
- [ ] Run to see missing feature.
- [ ] Implement ViewModel and screen with LolitaSection/cards, skin scroll, one reachable generate action, inline upload/cost copy, cancel and key dialog. Reuse full-screen preview component.
- [ ] Wire routes and entry callbacks; verify seven skins light/dark and large text in independent AVD; commit.

### Task 5: Integration review and release

**Files:** update AGENTS.md/version and verification docs; release artifacts outside tracked source.

- [ ] Run `assembleDebug testDebugUnitTest` and focused instrumentation on independent AVD; inspect real screenshots.
- [ ] Fresh whole-change review; fix important findings with meaningful regression checks.
- [ ] Run official live single-call only if API Key is configured; otherwise record exact unverified boundary and never represent samples as official-call results.
- [ ] Update version to 2.48.0 / 105 unless main has advanced; integrate onto current master without losing concurrent work.
- [ ] Commit source, `clean assembleRelease`, verify signature and same-signature update/data preservation. Publish GitHub/Gitee per AGENTS.md and verify anonymous asset SHA256 and update entry.
- [ ] Report real results, release links and external credential condition concisely.
