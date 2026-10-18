package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WardrobeBackupFormatTest {
    private val gson = Gson()
    private fun root() = gson.toJsonTree(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())).asJsonObject

    @Test fun versionTenRequiresNewTablesAndOldFormatNineSuppliesThemWithoutClassifyingHistory() {
        val old = root().apply {
            addProperty("formatVersion",9); addProperty("schemaVersion",29)
            listOf("storageNodes","itemDrafts","recognitionTasks","itemEvidence","imageProvenance").forEach(::remove)
        }
        val parsed = gson.toJsonTree(BackupFormat.parse(old.toString())).asJsonObject
        for (name in listOf("storageNodes","itemDrafts","recognitionTasks","itemEvidence","imageProvenance")) assertEquals(JsonArray(), parsed[name])
        val latest = root().apply { addProperty("formatVersion",10); addProperty("schemaVersion",30) }
        assertEquals(10, BackupFormat.parse(latest.toString()).formatVersion)
        for (name in listOf("storageNodes","itemDrafts","recognitionTasks","itemEvidence","imageProvenance"))
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(latest.deepCopy().apply { remove(name) }.toString()) }
    }

    @Test fun unknownSemanticAndMalformedProfileAreRejectedBeforeRestore() {
        val base = root().apply {
            add("brands",gson.toJsonTree(listOf(Brand(id=1,name="原品牌"))))
            add("categories",gson.toJsonTree(listOf(Category(id=1,name="原类型"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFormat.parse(base.deepCopy().apply { getAsJsonArray("categories")[0].asJsonObject.addProperty("categorySemantic","NEW_UNKNOWN_TYPE") }.toString())
        }
    }
    @Test fun anEditingDraftCannotRestoreWithANonexistentTargetItem() {
        val invalid=root().apply {
            add("itemDrafts",gson.toJsonTree(listOf(ItemDraft(
                targetItemId=77,dataJson=ItemDraftData(name="保留未完成编辑").encode()))))
        }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(invalid.toString()) }
    }

}
