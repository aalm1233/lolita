package com.lolita.app.data.file

import com.google.gson.Gson
import com.lolita.app.data.local.entity.TryOnResult
import org.junit.Assert.*
import org.junit.Test

class TryOnBackupFormatTest {
    private val gson = Gson()
    private fun backup() = BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    @Test fun oldVersionSixBackupRestoresWithoutInventingTryOnHistory() {
        val old = gson.toJsonTree(backup()).asJsonObject.apply {
            addProperty("formatVersion",6); addProperty("schemaVersion",26); remove("tryOnResults")
        }
        assertTrue(BackupFormat.parse(old.toString()).tryOnResults.isEmpty())
    }
    @Test fun resultSnapshotSurvivesEvenWhenOriginalItemHasBeenDeleted() {
        val result = TryOnResult(7,"花鸟龙月搭配","/images/result.png",listOf("33","30"),listOf("裙子","发箍"),"gpt-image-2",123)
        val backup = backup().copy(tryOnResults = listOf(result))
        assertEquals(backup,BackupFormat.parse(gson.toJson(backup)))
    }
    @Test fun incompleteNewBackupAndInvalidHistoryAreRejected() {
        val missing = gson.toJsonTree(backup()).asJsonObject.apply { remove("tryOnResults") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(missing.toString()) }
        val bad = backup().copy(tryOnResults = listOf(TryOnResult(7,"搭配","",listOf("33"),emptyList(),"gpt-image-2",123)))
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(gson.toJson(bad)) }
    }
}
