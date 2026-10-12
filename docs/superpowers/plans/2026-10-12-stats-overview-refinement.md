# Statistics Overview Refinement Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans; independent price-entry changes may be delegated. Track the checks below.

**Goal:** Hide purchase-review content when no relevant records remain and make the statistics overview compact and readable.

**Architecture:** Retain existing Room queries and StatsViewModel data. Reuse the shared PurchaseReviewShortcut, skin-aware cards/sections and the authoritative review projection for individual price-page entries.

**Tech Stack:** Kotlin 2.1.0, Compose BOM 2024.12.01, Material3, Room/KSP.

**Spec:** User-approved design in this conversation (2026-10-12): four counts in one compact block; current-order actual spending as the main amount; all-recorded spending and reference average as detail rows; long brand names; consistent skin spacing; hide zero-review entries.

## Global Constraints

- Chinese UI; seven existing skins and dark variants; no new dependency or database change.
- Preserve original money, dates, historical purchase evidence and every existing navigation callback.
- ShowTotalPrice=false hides all money in the overview.
- Test only owned emulator-5556; do not install or mutate user emulator-5554.
- Preserve all preexisting working changes and private drafts.

## Review Focus

- A review queue changing from positive to zero must remove entry space, then reappear for new reviews.
- Complete shared-price accessories and unpriced wishlisted clothing must not advertise irrelevant review actions.
- Long brand names and large amounts at 320dp/large font must remain readable.
- Money privacy changes must remove amounts, including highest-spending detail.
- Owned/wishlist, review, brand and item-detail routes retain their raw IDs/parameters.

### Task 1: Review visibility

**Files:** ItemListScreen.kt, PriceManageScreen.kt, PriceViewModel.kt, PurchaseReviewEntryTest.kt, PriceManageScreenReviewTest.kt.

- [x] Change empty-queue expectation and add positive→zero→positive test; add complete shared-price/wishlist tests.
- [x] Run targeted real Compose tests and observe their expected failures.
- [x] Return early from shared shortcut at zero; subscribe price UI to the same item review projection and gate only review actions.
- [x] Re-run entry and purchase consistency tests.

### Task 2: Overview layout

**Files:** StatsScreen.kt, StatsOverviewScreenTest.kt, AGENTS.md.

- [x] Add real StatsContent tests using isolated Room/preferences: compact first-screen spending, privacy updates, raw routes and long-text layouts.
- [x] Run against existing layout; observe compact-layout/long-text failures.
- [x] Replace repeated stat cards with one compact section, one spending section, neutral detail rows and flexible brand rows; use one padding layer and skin tokens.
- [x] Replace full-screen spinner with a matching skeleton and preserve exact Double amount display.
- [x] Re-run tests, capture seven skin light/dark and large-font screenshots; inspect visual output.

### Task 3: Verification and delivery

- [x] Run testDebugUnitTest, assembleDebug and assembleDebugAndroidTest.
- [x] Run serial-scoped instrumentation on owned test AVD, including existing data/business regressions.
- [x] Refresh AGENTS.md with visibility/layout/test conventions and version 86 / 2.40.1.
- [x] Review normalized diff and ensure unrelated preexisting files retain their baseline hashes.
- [ ] Commit and push the verified changes under the existing session authorization; report verified result and visual artifacts.

## Verification record

- RED: entry suite 21 tests / 5 expected failures; old overview clipped long brand text; old first amount exceeded the 300dp layout budget.
- GREEN: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed; 86 unit tests, zero failures.
- Android: 108 tests across 19 classes passed on owned `Lolita_Business_Fix_Test` / emulator-5556, version 86 / 2.40.1. Includes existing migration, backup, business, reminders and new visible-entry/overview regressions.
- 71 real Compose previews captured: 56 large-font amount/brand cases, 14 normal light/dark skins and 1 compact default. Default, Chinese and Victorian layouts visually inspected; long brand and large amount layouts verified for all seven skins.
- Data audit script passed its limited migration/WAL preservation cases. No database/schema/backup-format change.
- Independent code review found no blocking issue. Adaptive skeleton columns were aligned with the actual compact layout after review.
- Original unrelated working-file hashes retained. User emulator-5554 was not modified.
