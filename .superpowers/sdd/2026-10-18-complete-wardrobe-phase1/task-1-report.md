# Task 1 implementation report

Date: 2026-10-18. Worktree: `/Users/dengtong/.codex/worktrees/complete-wardrobe/lolita`, branch `codex/complete-wardrobe`.

## Delivered

- Stable `WardrobeFamily`, `CategorySemantic`, `WardrobeDisplayMode`, `ItemComposition` and strict version-1 typed specifications. Full wig/components, hosiery/leg warmers and petticoat/dress/basic clothes have different stable semantics. Renaming does not change the semantic or display mode.
- Category has `categorySemantic` and optional `defaultWearRole`; historical categories remain unconfirmed. Preset initialization inserts only missing names and never edits existing rows. Classification changes with referenced items require an explicit affected-count confirmation. New custom categories select a family/subtype and a default wear role.
- Brand has nullable unique `systemKey`; `unset_brand` is created transactionally without taking over an existing same-name brand. System entries cannot be renamed, relabeled as regular brands or deleted. Creation bypasses builtin logo matching; root handles explicit builtin-loop filtering.
- Item has `specificationsJson`, `composition` and nullable indexed `storageNodeId` FK. Original size, images, identifiers, parent/coordinate ownership and order records remain unchanged.
- Searchable family/type picker, optional per-type specifications and composition fields; only name and category are required. Detail displays confirmed profile values, composition and a dynamic storage path. All seven profiles remain saved when changing the category.
- Durable `ItemDraft` with UUID identity, target item, versioned raw editor payload and separate photo/chart/evidence paths. Edits autosave, keep-on-exit flushes, explicit discard removes only the draft, and Item save consumes the draft in the same database transaction. Process restoration locates the draft by SavedStateHandle identity, existing item or latest unfinished new registration. Independent injected Room tests do not accidentally use the application's production draft/storage repository.
- Registration copy creates a new draft and shares protected image references; it copies core fields/specifications but removes parent/coordinate/location/import token, all order/payment links and generation identities.
- Seven-family horizontal filtering, searchable category filter, raw size/location filters and family-specific raw specifications filter. Explicit filters include mounted children and label their parent relationship. Search covers name, brand, category, colors and storage path, excludes private notes, and each Item appears once.

## Owned source files

`domain/model/Wardrobe.kt`; `data/local/entity/{Category,Brand,Item,ItemDraft}.kt`; `data/local/dao/ItemDraftDao.kt`; `data/repository/{CategoryRepository,BrandRepository,ItemDraftRepository}.kt`; `ui/screen/item/{ItemViewModel,ItemEditScreen,ItemDetailScreen,ItemListScreen,WardrobeFields}.kt`; `ui/screen/settings/{CategoryManageViewModel,CategoryManageScreen,BrandManageViewModel,BrandManageScreen}.kt`.

Tests: `app/src/test/java/com/lolita/app/domain/model/WardrobeTest.kt`, `app/src/androidTest/java/com/lolita/app/WardrobeRegistrationTest.kt`.

## Root integration contract

- Root owns `LolitaDatabase`, Room migration/schema, converters, AppModule, backup and image reference protection. Entities and DAO/repository methods are implemented; wiring can use `ItemDraftRepository(database, itemRepository)` and `itemDraftDao()`.
- `ItemDraft`: `id`, `targetItemId`, `dataJson`, `imagePaths`, `sizeChartImagePaths`, `evidenceImagePaths`, `updatedAt`; `data()`, `validate()`, `protectedImagePaths()`.
- DAO: `observeDrafts/getAll/get/getForItem/save/delete/deleteAll`.
- Repository: `observeDrafts/get/getForItem/latestNew/save/commit/discard/copyFromItem`.
- Root recognition hooks belong inside repository `commit` (attach evidence after obtaining saved Item id, before deleting draft) and `discard` (remove unbound recognition data before deleting draft). Existing evidence paths and recognition-session ids are preserved during ordinary autosave.
- Editor state exposes `draftId`, `specifications`, `composition`, `storageNodeId`, `storageNodes`. ViewModel constructor accepts optional `draftRepository`, `storageRepository`, `SavedStateHandle`; root will add recognition merge functions.
- Navigation: `ItemEditScreen.draftId` selects a specific copied/new draft through `selectDraft()`. Root adds the optional query parameter to `Screen.ItemEdit` and injects a real SavedStateHandle factory. `ItemDetailScreen.onCopyToNew(String)` opens that draft; `onNavigateToLocation(Long)` opens its room.
- Root should explicitly skip `brand.systemKey != null` in builtin brand-logo initialization/preparation, even though the current unset name does not match the 14-logo catalog.
- Root Task 2 still handles import semantic confirmations, stats and CSV. `CategoryRepository.insertCategory` permits UNCONFIRMED for compatibility with existing import call sites; new category management explicitly requires semantics.

## Verification evidence

- Initial RED: `.superpowers/sdd/2026-10-18-complete-wardrobe-phase1/task-1-red.log`: compileDebugUnitTestKotlin failed because the new Wardrobe/Category APIs and the other agents' new interfaces did not exist. No production implementation preceded that run.
- Copy RED: `task-1-copy-red.log`: targeted test compile failed solely for the not-yet-implemented `registrationCopy` API.
- GREEN command: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`.
- `task-1-green.log`: production compiled, 338 unit tests ran; one StorageTree fixture failure and one AI android-test constructor error reported and sent to the owning agents.
- `task-1-green-final.log`: production compiled; full unit suite **339 tests, zero failures/errors**, including **6 WardrobeTest** cases. Android-test compilation was blocked only by newly added `WigHeadTemplateSelectionTest.kt:43` missing Item.status, reported to AI agent. WardrobeRegistrationTest compiled without its own errors in that run, but has **not been executed on a device**.
- `git diff --check` passed.

No paid requests, release builds, device data operations or original-workspace edits were performed.

## Remaining verification / limits

- Root must finish navigation/recognition hooks, rerun all compilation after integration, run `WardrobeRegistrationTest` on the independent AVD and perform seven-skin/small-screen/large-font UI review.
- The specifications filter currently matches raw strings (including original units and ranges). It does not normalize or convert shoe systems, units or numeric ranges.
- Real photo deletion behavior relies on root's ItemDraft/evidence reference-protection wiring; the registration tests currently exercise draft persistence/discard identity with sentinel image paths rather than manipulating any user's image directory.
- Existing editor tests asserting the old “添加服饰/编辑服饰” titles will need product-consistent updates if they fail; the requested new labels are “添加物品/编辑物品”.

Commit: `6f49924 feat: add stable wardrobe registration and durable item drafts` (20 owned source/test files only; root integration and other agents' files were not staged).
