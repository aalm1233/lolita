# Backup Import Compatibility Fix — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix backup import so v2.26 (and older) backups can be imported into the current app version.

**Architecture:** Fix the JSON-level migration in `migrateJsonString()` to handle the `colors` string→array type change, add missing `catalogEntries` migration, restore `migrateBackupData()` as a post-deserialization safety net, and improve the failure fallback.

**Tech Stack:** Kotlin, Gson, Room, Android

---

### Task 1: Fix `ensureColorArray()` to handle string values starting with `[`

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:853-868`

**Step 1: Replace `ensureColorArray` implementation**

Replace the current method (lines 853-868) with:

```kotlin
private fun ensureColorArray(root: JsonObject, fieldName: String = "items") {
    val array = root.getAsJsonArray(fieldName) ?: return
    array.forEach { element ->
        val obj = element as? JsonObject ?: return@forEach
        when {
            !obj.has("colors") -> obj.add("colors", JsonArray())
            obj.get("colors").isJsonNull -> obj.add("colors", JsonArray())
            obj.get("colors").isJsonArray -> { }
            obj.get("colors").isJsonPrimitive -> {
                val str = obj.get("colors").asString
                if (str.startsWith("[")) {
                    try {
                        val parsed = JsonParser.parseString(str).asJsonArray
                        obj.add("colors", parsed)
                    } catch (_: Exception) {
                        obj.add("colors", JsonArray())
                    }
                } else if (str.isNotBlank()) {
                    val arr = JsonArray()
                    arr.add(str)
                    obj.add("colors", arr)
                } else {
                    obj.add("colors", JsonArray())
                }
            }
            else -> obj.add("colors", JsonArray())
        }
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: SUCCESS

**Step 3: Commit**

```
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: handle v2.26 colors string format in ensureColorArray"
```

---

### Task 2: Add `catalogEntries` migration and extend `ensureColorArray` to cover it

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:834-851`

**Step 1: Update `migrateJsonString()` to process `catalogEntries` and call `ensureColorArray` for both**

Replace `migrateJsonString()` (lines 834-851) with:

```kotlin
private fun migrateJsonString(json: String): Pair<String, Int> {
    return try {
        val root = JsonParser.parseString(json).asJsonObject
        ensureArrayField(root, "catalogEntries")
        ensureArrayField(root, "styles")
        ensureArrayField(root, "seasons")
        ensureArrayField(root, "locations")
        ensureArrayField(root, "sources")
        migrateArray(root, "items", migrateColor = true, migrateImageUrl = true)
        migrateArray(root, "coordinates", migrateColor = false, migrateImageUrl = true)
        migrateArray(root, "outfitLogs", migrateColor = false, migrateImageUrl = true)
        migrateArray(root, "catalogEntries", migrateColor = true, migrateImageUrl = true)
        ensureColorArray(root, "items")
        ensureColorArray(root, "catalogEntries")
        root.toString() to 0
    } catch (e: Exception) {
        Log.w("BackupManager", "JSON migration failed, using original", e)
        json to 1
    }
}
```

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: SUCCESS

**Step 3: Commit**

```
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: add catalogEntries to JSON migration for imageUrl/colors"
```

---

### Task 3: Restore `migrateBackupData()` as post-deserialization fallback

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:904-911`

**Step 1: Replace `migrateBackupData()` with fallback logic**

Replace lines 904-911 with:

```kotlin
private fun migrateBackupData(backupData: BackupData): BackupData {
    var changed = false
    val fixedItems = backupData.items.map { item ->
        if (item.colors.isEmpty() && item.colors is List<*>) {
            item
        } else {
            item
        }
    }
    return if (changed) backupData.copy(items = fixedItems) else backupData
}
```

Wait — `Item.colors` is now `List<String>`, so if Gson successfully parsed it (after `ensureColorArray` fix), it will already be a proper list. The fallback should handle the case where Gson somehow still produces a list with a single serialized-JSON-string element (e.g., `["[\"粉色\"]"]`). Replace with:

```kotlin
private fun migrateBackupData(backupData: BackupData): BackupData {
    val fixedItems = backupData.items.map { item ->
        val needsFix = item.colors.size == 1 && item.colors.first().let { it.startsWith("[") && it.endsWith("]") }
        if (needsFix) {
            try {
                val parsed = gson.fromJson<List<String>>(item.colors.first(), object : TypeToken<List<String>>() {}.type)
                item.copy(colors = parsed)
            } catch (_: Exception) {
                item
            }
        } else {
            item
        }
    }
    return if (fixedItems != backupData.items) backupData.copy(items = fixedItems) else backupData
}
```

Make sure `TypeToken` import exists: `import com.google.gson.reflect.TypeToken`

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: SUCCESS

**Step 3: Commit**

```
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: restore migrateBackupData fallback for colors string-in-list"
```

---

### Task 4: Improve `migrateJsonString()` failure fallback

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/file/BackupManager.kt:846-850`

**Step 1: Change the catch block to not silently return raw JSON**

In the `migrateJsonString()` catch block, instead of returning the raw unmigrated JSON (which will crash Gson), throw the exception so the outer `importBackup()` can handle it with proper rollback:

Replace:
```kotlin
    } catch (e: Exception) {
        Log.w("BackupManager", "JSON migration failed, using original", e)
        json to 1
    }
```

With:
```kotlin
    } catch (e: Exception) {
        Log.w("BackupManager", "JSON migration failed", e)
        throw Exception("备份格式迁移失败：${e.message}", e)
    }
```

**Step 2: Build to verify compilation**

Run: `./gradlew.bat assembleDebug`
Expected: SUCCESS

**Step 3: Commit**

```
git add app/src/main/java/com/lolita/app/data/file/BackupManager.kt
git commit -m "fix: throw on JSON migration failure instead of returning raw JSON"
```

---

### Task 5: Final build verification

**Step 1: Clean build**

Run: `./gradlew.bat clean assembleDebug`
Expected: SUCCESS

**Step 2: Verify no regressions in export**

Run: `./gradlew.bat assembleRelease`
Expected: SUCCESS (warning OK, no errors)
