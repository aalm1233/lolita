# 备份恢复修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix backup/restore so data survives uninstall-reinstall, and include image files in backups.

**Architecture:** Change backup format from JSON to ZIP (data.json + images/). Fix import by clearing all tables before inserting. Add image path remapping and post-import verification. Maintain backward compatibility with old JSON backups.

**Tech Stack:** Room, Gson, java.util.zip, Android MediaStore, Kotlin Coroutines

---

### Task 1: Add `clearAllTables()` helper and fix import logic in BackupManager

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:146-204`

**Step 1: Add clearAllTables private method**

Add this method to `BackupManager` class, before `createFileInDownloads`:

```kotlin
private suspend fun clearAllTables() {
    // Delete in reverse FK dependency order
    database.outfitLogDao().deleteAllOutfitItemCrossRefs()
    database.outfitLogDao().deleteAllOutfitLogs()
    database.paymentDao().deleteAllPayments()
    database.priceDao().deleteAllPrices()
    database.itemDao().deleteAllItems()
    database.coordinateDao().deleteAllCoordinates()
    database.brandDao().deleteAllBrands()
    database.categoryDao().deleteAllCategories()
    database.styleDao().deleteAllStyles()
    database.seasonDao().deleteAllSeasons()
}
```

**Step 2: Add deleteAll methods to each DAO**

Add `@Query("DELETE FROM <table>") suspend fun deleteAll<Entity>()` to each DAO:

- `BrandDao.kt`: `@Query("DELETE FROM brands") suspend fun deleteAllBrands()`
- `CategoryDao.kt`: `@Query("DELETE FROM categories") suspend fun deleteAllCategories()`
- `StyleDao.kt`: `@Query("DELETE FROM styles") suspend fun deleteAllStyles()`
- `SeasonDao.kt`: `@Query("DELETE FROM seasons") suspend fun deleteAllSeasons()`
- `CoordinateDao.kt`: `@Query("DELETE FROM coordinates") suspend fun deleteAllCoordinates()`
- `ItemDao.kt`: `@Query("DELETE FROM items") suspend fun deleteAllItems()`
- `PriceDao.kt`: `@Query("DELETE FROM prices") suspend fun deleteAllPrices()`
- `PaymentDao.kt`: `@Query("DELETE FROM payments") suspend fun deleteAllPayments()`
- `OutfitLogDao.kt`: `@Query("DELETE FROM outfit_logs") suspend fun deleteAllOutfitLogs()`
- `OutfitLogDao.kt`: `@Query("DELETE FROM outfit_item_cross_refs") suspend fun deleteAllOutfitItemCrossRefs()`

**Step 3: Rewrite `importFromJson()` with clear-first logic**

Replace the entire `importFromJson` method body. Key changes:
1. Parse backup data (from cache or file) — same as before
2. Inside `database.withTransaction`:
   - Call `clearAllTables()` first
   - Insert all data in FK-dependency order (no try-catch per row needed since table is empty)
   - Payments: still clear `calendarEventId`
3. After transaction: verify by querying `database.itemDao().getItemCount()` vs `backupData.items.size`
4. If mismatch: return `Result.failure(Exception("数据验证失败：期望 ${backupData.items.size} 条服饰，实际 $actualCount 条"))`
5. Reschedule reminders — same as before

Add to `ItemDao.kt`:
```kotlin
@Query("SELECT COUNT(*) FROM items")
suspend fun getItemCount(): Int
```

**Step 4: Commit**

```bash
git add -A && git commit -m "fix(backup): clear database before import to prevent silent rollback"
```

---

### Task 2: Add `exportToZip()` method to BackupManager

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

**Step 1: Add zip import statements**

Add at top of file:
```kotlin
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
```

**Step 2: Add `collectImagePaths()` helper**

```kotlin
private fun collectImagePaths(backupData: BackupData): Set<String> {
    val paths = mutableSetOf<String>()
    backupData.items.forEach { item ->
        item.imageUrl?.let { paths.add(it) }
        item.sizeChartImageUrl?.let { paths.add(it) }
    }
    backupData.coordinates.forEach { coord ->
        coord.imageUrl?.let { paths.add(it) }
    }
    backupData.outfitLogs.forEach { log ->
        log.imageUrls.forEach { paths.add(it) }
    }
    return paths
}
```

**Step 3: Add `exportToZip()` method**

```kotlin
suspend fun exportToZip(): Result<Uri> = withContext(Dispatchers.IO) {
    try {
        val backupData = database.withTransaction {
            BackupData(
                brands = database.brandDao().getAllBrandsList(),
                categories = database.categoryDao().getAllCategoriesList(),
                coordinates = database.coordinateDao().getAllCoordinatesList(),
                items = database.itemDao().getAllItemsList(),
                prices = database.priceDao().getAllPricesList(),
                payments = database.paymentDao().getAllPaymentsList(),
                outfitLogs = database.outfitLogDao().getAllOutfitLogsList(),
                outfitItemCrossRefs = database.outfitLogDao().getAllOutfitItemCrossRefsList(),
                styles = database.styleDao().getAllStylesList(),
                seasons = database.seasonDao().getAllSeasonsList()
            )
        }

        val imagePaths = collectImagePaths(backupData)
        val jsonBytes = gson.toJson(backupData).toByteArray()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "lolita_backup_${timestamp}.zip"

        // Build ZIP in memory
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Write data.json
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonBytes)
            zos.closeEntry()

            // Write image files
            imagePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val entryName = "images/${file.name}"
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }

        val uri = createFileInDownloads(fileName, "application/zip", baos.toByteArray())
        Result.success(uri)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

**Step 4: Commit**

```bash
git add -A && git commit -m "feat(backup): add ZIP export with image files"
```

---

### Task 3: Update import to support ZIP format with image restoration

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt`

**Step 1: Add `isZipFile()` helper**

```kotlin
private fun isZipFile(uri: Uri): Boolean {
    val bytes = ByteArray(2)
    context.contentResolver.openInputStream(uri)?.use { it.read(bytes) } ?: return false
    return bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() // "PK"
}
```

**Step 2: Add `parseZipBackup()` helper**

Reads ZIP, extracts images to `filesDir/images/`, parses `data.json`, returns `Pair<BackupData, Int>` (data + image count).

```kotlin
private fun parseZipBackup(uri: Uri): Pair<BackupData, Int> {
    val imagesDir = File(context.filesDir, "images")
    if (!imagesDir.exists()) imagesDir.mkdirs()
    var imageCount = 0
    var jsonString: String? = null

    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "data.json" -> {
                        jsonString = zis.bufferedReader().readText()
                    }
                    entry.name.startsWith("images/") && !entry.isDirectory -> {
                        val fileName = entry.name.substringAfter("images/")
                        if (fileName.isNotBlank()) {
                            File(imagesDir, fileName).outputStream().use { out ->
                                zis.copyTo(out)
                            }
                            imageCount++
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    } ?: throw Exception("无法读取文件")

    val data = gson.fromJson(jsonString ?: throw Exception("ZIP中未找到data.json"), BackupData::class.java)
    return Pair(data, imageCount)
}
```

**Step 3: Add `remapImagePaths()` helper**

Replaces old directory prefix in all image paths with current `filesDir/images/`:

```kotlin
private fun remapImagePaths(backupData: BackupData): BackupData {
    val imagesDir = File(context.filesDir, "images").absolutePath
    fun remap(path: String?): String? {
        if (path == null) return null
        val fileName = File(path).name
        return "$imagesDir/$fileName"
    }
    return backupData.copy(
        items = backupData.items.map { it.copy(imageUrl = remap(it.imageUrl), sizeChartImageUrl = remap(it.sizeChartImageUrl)) },
        coordinates = backupData.coordinates.map { it.copy(imageUrl = remap(it.imageUrl)) },
        outfitLogs = backupData.outfitLogs.map { it.copy(imageUrls = it.imageUrls.map { url -> remap(url) ?: url }) }
    )
}
```

**Step 4: Update `importFromJson()` to handle both formats**

At the start of `importFromJson()`, replace the backup data parsing section:

```kotlin
var imageCount = 0
val backupData: BackupData

if (cachedBackupUri == uri && cachedBackupData != null) {
    backupData = cachedBackupData!!
    imageCount = cachedImageCount
} else if (isZipFile(uri)) {
    val (data, count) = parseZipBackup(uri)
    backupData = remapImagePaths(data)
    imageCount = count
} else {
    val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        ?: return@withContext Result.failure(Exception("无法读取文件"))
    backupData = gson.fromJson(jsonString, BackupData::class.java)
}
cachedBackupData = null
cachedBackupUri = null
cachedImageCount = 0
```

Add cache field: `private var cachedImageCount: Int = 0`

**Step 5: Update `previewBackup()` to handle ZIP**

Replace the parsing logic in `previewBackup()`:

```kotlin
val backupData: BackupData
var imageCount = 0

if (isZipFile(uri)) {
    val (data, count) = parseZipBackup(uri)
    backupData = remapImagePaths(data)
    imageCount = count
} else {
    val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        ?: return@withContext Result.failure(Exception("无法读取文件"))
    backupData = gson.fromJson(jsonString, BackupData::class.java)
}
cachedBackupData = backupData
cachedBackupUri = uri
cachedImageCount = imageCount
```

**Step 6: Commit**

```bash
git add -A && git commit -m "feat(backup): support ZIP import with image restoration and path remapping"
```

---

### Task 4: Extend data classes and add `imageCount` to ImportSummary/BackupPreview

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:251-274`

**Step 1: Add `imageCount` to `BackupPreview`**

```kotlin
data class BackupPreview(
    // ... existing fields ...
    val imageCount: Int = 0,   // NEW
    val backupDate: Long,
    val backupVersion: String
)
```

Update `totalCount` getter — do NOT include imageCount in totalCount (images are files, not DB records).

**Step 2: Add `imageCount` to `ImportSummary`**

```kotlin
data class ImportSummary(
    val totalImported: Int,
    val totalSkipped: Int,
    val totalErrors: Int = 0,
    val imageCount: Int = 0,   // NEW
    val backupDate: Long,
    val backupVersion: String
)
```

**Step 3: Update `previewBackup()` return to include imageCount**

In the `Result.success(BackupPreview(...))` call, add `imageCount = imageCount`.

**Step 4: Update `importFromJson()` return to include imageCount**

In the `Result.success(ImportSummary(...))` call, add `imageCount = imageCount`.

**Step 5: Commit**

```bash
git add -A && git commit -m "feat(backup): add imageCount to BackupPreview and ImportSummary"
```

---

### Task 5: Update BackupRestoreScreen UI

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/settings/BackupRestoreScreen.kt`

**Step 1: Add `exportZip()` to ViewModel**

```kotlin
fun exportZip() {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isExporting = true, message = null)
        backupManager.exportToZip().fold(
            onSuccess = { _uiState.value = _uiState.value.copy(message = "ZIP备份成功！文件已保存到下载目录（含图片）") },
            onFailure = { _uiState.value = _uiState.value.copy(message = "备份失败: ${it.message}") }
        )
        _uiState.value = _uiState.value.copy(isExporting = false)
    }
}
```

**Step 2: Update `confirmImport()` success message**

Change the success message to include image count:

```kotlin
onSuccess = { summary ->
    val imageMsg = if (summary.imageCount > 0) "，恢复 ${summary.imageCount} 张图片" else ""
    _uiState.value = _uiState.value.copy(
        message = "恢复完成！导入 ${summary.totalImported} 条数据$imageMsg"
    )
},
```

**Step 3: Add ZIP export card in the export section**

Add a new Card BEFORE the CSV card (after the JSON card, around line 186):

```kotlin
Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("导出为ZIP（含图片）", style = MaterialTheme.typography.bodyLarge)
        Text("包含所有数据和图片，推荐用于完整备份", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { viewModel.exportZip() },
            enabled = !uiState.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("导出ZIP备份")
        }
    }
}
```

**Step 4: Update file picker MIME types**

Change line 222 from:
```kotlin
filePickerLauncher.launch(arrayOf("application/json"))
```
to:
```kotlin
filePickerLauncher.launch(arrayOf("application/json", "application/zip"))
```

**Step 5: Update import section description text**

Change line 219-220 from:
```kotlin
Text("从JSON备份恢复", style = MaterialTheme.typography.bodyLarge)
Text("选择之前导出的JSON备份文件，已有数据不会被覆盖", style = MaterialTheme.typography.bodySmall)
```
to:
```kotlin
Text("从备份恢复", style = MaterialTheme.typography.bodyLarge)
Text("选择之前导出的备份文件（支持ZIP和JSON格式），将清空当前数据并替换为备份数据", style = MaterialTheme.typography.bodySmall)
```

**Step 6: Update confirm dialog to show image count and warning**

In the AlertDialog text column, add after `Text("穿搭日记: ${p.outfitLogCount} 条")`:

```kotlin
if (p.imageCount > 0) {
    Text("图片: ${p.imageCount} 张")
}
HorizontalDivider(color = Pink100, modifier = Modifier.padding(vertical = 4.dp))
Text("共 ${p.totalCount} 条数据")
Spacer(Modifier.height(8.dp))
Text(
    "⚠ 恢复将清空当前所有数据",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error
)
```

Remove the existing `HorizontalDivider` + `Text("共 ${p.totalCount} 条数据")` lines (258-259) since we moved them above.

**Step 7: Commit**

```bash
git add -A && git commit -m "feat(backup): add ZIP export button, update import UI with warning"
```

---

### Task 6: Final integration and build verification

**Step 1: Verify all DAO deleteAll methods are added**

Check each DAO file has its `deleteAll` method. Full list:
- `BrandDao.kt`: `deleteAllBrands()`
- `CategoryDao.kt`: `deleteAllCategories()`
- `StyleDao.kt`: `deleteAllStyles()`
- `SeasonDao.kt`: `deleteAllSeasons()`
- `CoordinateDao.kt`: `deleteAllCoordinates()`
- `ItemDao.kt`: `deleteAllItems()` + `getItemCount()`
- `PriceDao.kt`: `deleteAllPrices()`
- `PaymentDao.kt`: `deleteAllPayments()`
- `OutfitLogDao.kt`: `deleteAllOutfitLogs()` + `deleteAllOutfitItemCrossRefs()`

**Step 2: Build the project**

```bash
./gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 3: Fix any compilation errors and commit**

```bash
git add -A && git commit -m "fix(backup): resolve build issues"
```
