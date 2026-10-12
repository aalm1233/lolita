# Non-navigation icon personality implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Independent file ownership permits parallel execution.

**Goal:** Personalize all 46 non-navigation keys across seven skins.
**Architecture:** Keep the public providers and Canvas grid; split the grown control drawing file into action and direction/status responsibilities with a thin dispatcher.
**Tech Stack:** Kotlin / Jetpack Compose Canvas.
**Spec:** docs/superpowers/specs/2026-10-12-icon-details-design.md

## Global Constraints

24×24 grid; main stroke about 1.7; use style.stroke and tint; no background masks; no new dependencies; navigation byte identical; no versions or page layout changes. All remaining 46 keys receive deliberate family geometry, not only corner changes. Helpers are private to owned files; reuse public style.stroke. Workers do not run Gradle or commit while sharing a checkout.

## Review Focus

18dp clarity; differences in a same-color matrix; functional recognition; transparent/slashed strokes; Image/AddPhoto lower-half consistency. Task 4 uses existing Android tests and visual review to check these.

### Task 1: Action identity

Create app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedActionDrawings.kt only.
Produce internal fun DrawScope.drawPolishedActionIcon(key: IconKey, style: SkinIconStyle, tint: Color).
Keys: Add, Delete, Edit, Search, Sort, Save, Close, Share, FilterList, MoreVert, ContentCopy, Refresh, ViewAgenda, GridView, Apps, Gallery.
Read current PolishedControlIcons.kt and navigation for visual language. Rewrite all 16 deliberately in seven styles. Simple plus/check/X must remain familiar with materially different contour/pens; avoid merely adding decorative end dots. Share nodes and connecting strokes, grid tiles and spacing, sorting/filter bars must have deliberate construction. Existing recognizability remains. Use compact private helpers for geometric families without one generic ornament around every glyph.
- [x] Implement and self-review changed geometry against the spec.
- [x] Report file scope, a per-key treatment summary and potential visual concerns. Root performs compilation and Android matrix verification after all writers finish.

### Task 2: Content identity

Modify app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedContentIcons.kt only.
Preserve internal fun DrawScope.drawPolishedContentIcon(key: IconKey, style: SkinIconStyle, tint: Color).
Keys: Star, StarBorder, Image, Camera, AddPhoto, Link, LinkOff, Palette, FileOpen, CalendarMonth, Notifications, AttachMoney, Category, Location.
Strengthen every key in seven styles with distinct objects, materials, proportions and meaningful construction. Presently some folders, palettes, coins and pins only vary outer curves; remove that generic impression. Star variants must preserve filled/outline pair; Image/AddPhoto must preserve lower-half exact pixels at half alpha, draw frame only once, clipping for plus upper right. Retain unknown-background and tint rules.
- [x] Implement and self-review changed geometry against the spec.
- [x] Report all 14 treatments and concerns; root handles builds/Android after writers finish.

### Task 3: Direction and status identity

Create app/src/main/java/com/lolita/app/ui/theme/skin/icon/PolishedDirectionStatusDrawings.kt only.
Produce internal fun DrawScope.drawPolishedDirectionStatusIcon(key: IconKey, style: SkinIconStyle, tint: Color).
Keys: ArrowBack, ArrowForward, KeyboardArrowLeft, KeyboardArrowRight, ExpandMore, ExpandLess, ArrowDropDown, SwapVert, OpenInNew, CheckCircle, Warning, Error, Info, Visibility, VisibilityOff, Help.
Each seven-style shape must deliberately change silhouette/construction/stroke movement, not simply same arrow with minor endpoint dots. Direction remains obvious, paired arrows mirror consistently. Status punctuation stays large, frame shape reinforces theme rather than one arbitrary generic polygon across every status. VisibilityOff uses same themed eye silhouette as Visibility and true gaps/clipping at slash. No background masks; 18dp clear.
- [x] Implement and self-review all 16 against the spec.
- [x] Report treatment summary and concerns; no compilation while writers active.

### Task 4: Integration and actual visual verification

Root replaces PolishedControlIcons.kt with a thin exhaustive dispatcher to Tasks 1 and 3, updates SkinIconVisualTest overviews to emphasize remaining46; keep existing assertions and add family sheets for18/32dp. Update AGENTS and validation documentation.
- [x] Assemble debug, debug Android test APK and run unit tests.
- [x] Run SkinIconVisualTest only on new isolated AVD, preserve existing devices and signatures.
- [x] Inspect all four real groups in light/dark and18dp; obtain independent review and address material findings.
- [x] Verify navigation unchanged and all46 covered, integrate explicit owned files preserving unrelated file fingerprints.
