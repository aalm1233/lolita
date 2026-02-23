# Taobao Import Detail Back Confirmation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a confirmation dialog when the user tries to navigate back from the Detail step of Taobao import, preventing accidental loss of edited data.

**Architecture:** Single-file change to `ImportDetailContent` composable — add a `BackHandler` to intercept system back, change the nav icon click to show a dialog instead of calling `onBack` directly, and add an `AlertDialog` for confirmation.

**Tech Stack:** Jetpack Compose (Material3), `androidx.activity.compose.BackHandler`

---

### Task 1: Add back confirmation dialog to ImportDetailContent

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt:1-104`

**Step 1: Add the BackHandler import**

Add this import at the top of the file (after the existing imports, before line 33):

```kotlin
import androidx.activity.compose.BackHandler
```

**Step 2: Add dialog state and BackHandler inside ImportDetailContent**

Inside the `ImportDetailContent` function, after line 42 (`val snackbarHostState = remember { SnackbarHostState() }`), add:

```kotlin
    var showBackConfirmDialog by remember { mutableStateOf(false) }

    BackHandler { showBackConfirmDialog = true }
```

**Step 3: Change the navigation icon onClick**

At line 57, change:
```kotlin
                    IconButton(onClick = onBack) {
```
to:
```kotlin
                    IconButton(onClick = { showBackConfirmDialog = true }) {
```

**Step 4: Add the AlertDialog**

After the `Scaffold` closing brace (after line 103's `}`), before the function's closing `}`, add:

```kotlin
    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("确认返回") },
            text = { Text("已编辑的导入数据尚未保存，返回后将丢失所有修改。") },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirmDialog = false
                    onBack()
                }) { Text("返回") }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmDialog = false }) { Text("继续编辑") }
            }
        )
    }
```

**Step 5: Build to verify**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/lolita/app/ui/screen/import/ImportDetailScreen.kt
git commit -m "feat: add back confirmation dialog to Taobao import detail step"
```
