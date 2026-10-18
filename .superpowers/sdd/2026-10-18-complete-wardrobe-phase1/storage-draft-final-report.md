# Storage draft integrity and edit baseline verification

Worktree: `/Users/dengtong/.codex/worktrees/complete-wardrobe/lolita`, branch `codex/complete-wardrobe`.

## Defects reproduced before fixes

The first three real Room tests reproduced stale `ItemDraft.dataJson` references after moving a subtree, deleting a node (including a draft-only node), and deleting a room through LocationRepository. Normal BackupManager export rejected the resulting graph. The actual RED source snapshots, 3 failing assertions and commands are retained under `artifacts/wardrobe-device-2026-10-18/final-5586/storage-ui-red/`.

StorageRepository now updates affected draft JSON in the same Room transaction as node/item mutation. A moved subtree changes the drafts’ room to the node’s new room; deleting a node clears its draft node while retaining an existing room; deleting a room clears both references, including drafts without a committed Item. All other draft data, identity, baseline, timestamps, appearance metadata and image bytes are preserved exactly. Catalog owns the complementary ItemDraftRepository.save normalization, preventing a still-open editor from recreating a deleted/cross-room storage reference.

A fourth actual Room RED reproduced a remaining conflict: bulk-moving an Item between two still-valid nodes left its updatedAt unchanged, so the old draft could commit and restore its previous node. The failing assertion and exact pre-fix ItemDao/test are retained in `final-5586/storage-timestamp-red/`.

All four storage UPDATE queries now assign `updated_at = MAX(updated_at + 1, :updatedAt)`, with a default wall-clock parameter preserving call compatibility. This advances the edit baseline even when an existing timestamp is ahead of the clock. Other business fields are compared exactly. The new regression seeds a future timestamp, moves the Item, verifies the old node still exists, rejects the stale draft commit, and verifies the Item/draft/files remain unchanged after rejection. Existing move/delete tests now permit only that specifically asserted timestamp advance, rather than weakening whole-row comparisons.

## Final actual execution

Dedicated fresh AVD `Lolita_Wardrobe_Final_35`, serial `emulator-5586`, API35 ARM64 Pixel7. No uninstall or data clearing; main/test same certificate `b7832feba195d280dfa51957f8f6a38946d82a7d633ef3e2a11b23c04f01a8d2`, version113, install-r only. 5582 historical Room evidence remains preserved; 5584 release-upgrade device was not used.

Final fixed APKs: main SHA256 `2f77a10a7481138c632448e543c40a3757a3e23a13bbf6636c2334451d0405ad`, test SHA256 `ba610ed03e43945208bea534196603587f37e08b4676833b9d3dadffe8e5829f`. Catalog’s final build completed 386 JVM tests with zero failures/errors/skips and both APKs. Without rebuilding or reinstalling, the same fixed APK passed **44/44 Room tests in 3.374s**, including StorageRepository8, StorageDraft4, ItemDraftBaseline6, NewDraftStorageCommit2, WardrobeBackup16 and RecognitionPersistence8; then **3/3 actual Compose UI tests in 21.267s**. These results do not claim old navigation/migration/provider suites were rerun in this command.

Exact commands, raw logs, source SHA snapshots, APK evidence and parsed counts: `artifacts/wardrobe-device-2026-10-18/final-5586/final47/`. No provider transport, paid API, real key or release operation was used. Gradle and the AVD were released to root after execution.
