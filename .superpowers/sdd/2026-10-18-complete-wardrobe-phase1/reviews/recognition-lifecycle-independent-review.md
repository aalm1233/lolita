# Recognition lifecycle independent review

Reviewer: storage_recon. Implementer: catalog_recon. Read-only review of ItemDraftRepository, RecognitionRepository, ItemEditViewModel, ItemRecognitionViewModel, ItemRecognitionPanel, ItemRepository deletion, and domain validation. None of these production files were edited by the reviewer.

## Finding

- **P2: Register each new dialog scroll container with ObserveSkinScroll.** ItemRecognitionPanel.kt uses independent scrolling for the existing-image LazyColumn (line121), confirmation LazyRow (142), task-region LazyRow (around184), evidence-preview verticalScroll (around230), saved-evidence LazyColumn (245), and searchable-reference LazyColumn (266). Only the main recognition dialog registers its scroll state. Dragging/flinging a child container does not activate the main scroll state, so background animation continues and the AGENTS convention is not met. Hold the corresponding ScrollState/LazyListState for each container and register it independently.

## Boundaries checked

- The task apply entry reserves editing state before waiting for draftMutex, then enters Room. Draft/evidence changes commit before UI publication; failed inserts do not publish uncommitted facts. Autosave and ordinary save use the same lock order.
- Draft commit retargets retained tasks to the saved item. Reopening reconnects them to the new editor draft while preserving the original baseline and unapplied result. Coordinator updates the current row rather than replacing it with a stale task snapshot.
- Partial acceptance remains reviewable. Repository-owned appliedValues replace client history. Stable evidence identity plus commit replacement avoids duplicate origins. Variant/size changes filter inapplicable choices. AI restrictions follow copied images and evidence envelopes.
- Item deletion removes related drafts/tasks/unbound evidence; late Coordinator results update only an existing RUNNING row. Same-SHA region paths may repeat after ZIP mapping while region identities/counts remain strict. The pending new ZIP regression will verify that boundary with real files.

No further blocking lifecycle/atomicity issue was found in the source reviewed. This is not a claim of exhaustive correctness. The reviewer fixed and executed the9-case actual RED (9 assertion failures); catalog separately reported the same9 GREEN. The reviewer independently ran20 backup/persistence cases GREEN against that later installed APK. Four additional cases remain unexecuted and do not inherit that result.

Follow-up: the implementer has fixed the scroll finding. Independently re-read current source: all eight scrolling paths now hold and register their own ScrollState/LazyListState, and task blocks use key(task.id). The P2 is closed in source; subsequent actual UI verification is still pending. The four added backup cases were subsequently executed as part of24/24 GREEN; that result does not cover the pending two UI cases.
