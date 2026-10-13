package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import org.junit.Assert.*
import org.junit.Test

class BackupImportTokenTest {
    private fun backup(): JsonObject = Gson().toJsonTree(BackupData(
        brands = listOf(Brand(id = 101, name = "品牌", createdAt = 1)),
        categories = listOf(Category(id = 307, name = "分类", createdAt = 2)),
        coordinates = emptyList(),
        items = listOf(Item(id = 9001, brandId = 101, categoryId = 307,
            name = "服饰", description = "详情", status = ItemStatus.WISHED,
            createdAt = 3, updatedAt = 4, imageUrls = listOf("主图"),
            sizeChartImageUrl = "尺码首图", sizeChartImageUrls = listOf("尺码后图"))),
        prices = emptyList(), payments = emptyList(), outfitLogs = emptyList(),
        outfitItemCrossRefs = emptyList(), backupDate = 5
    )).asJsonObject

    @Test fun oldBackupWithoutImportTokenKeepsItsRecordAndImages() {
        val json = backup().apply { addProperty("schemaVersion", 25) }
        json["items"].asJsonArray[0].asJsonObject.remove("importToken")
        val restored = BackupFormat.parse(json.toString()).items.single()
        val row = Gson().toJsonTree(restored).asJsonObject
        assertTrue(row["importToken"] == null || row["importToken"].isJsonNull)
        assertEquals(9001L, restored.id)
        assertEquals(listOf("主图"), restored.imageUrls)
        assertEquals("尺码首图", restored.sizeChartImageUrl)
        assertEquals(listOf("尺码后图"), restored.sizeChartImageUrls)
    }

    @Test fun currentBackupPreservesTheDurableImportReceiptOnRoundTrip() {
        val json = backup().apply { addProperty("schemaVersion", 26) }
        json["items"].asJsonArray[0].asJsonObject.addProperty("importToken", "batch-uuid:0")
        val restored = BackupFormat.parse(json.toString())
        val exported = Gson().toJsonTree(restored).asJsonObject
        assertEquals("batch-uuid:0", exported["items"].asJsonArray[0].asJsonObject["importToken"].asString)
        assertEquals(restored, BackupFormat.parse(exported.toString()))
    }

    @Test fun multipleItemsWithoutImportReceiptsRemainValid() {
        val json = backup()
        val first = json["items"].asJsonArray[0].asJsonObject
        first.remove("importToken")
        json["items"].asJsonArray.add(first.deepCopy().apply { addProperty("id", 9002) })
        val restored = BackupFormat.parse(json.toString())
        assertEquals(listOf(9001L, 9002L), restored.items.map { it.id })
        assertTrue(restored.items.all { it.importToken == null })
    }

    @Test fun duplicateNonNullImportReceiptsAreRejectedBeforeRestore() {
        val json = backup()
        val first = json["items"].asJsonArray[0].asJsonObject
        first.addProperty("importToken", "duplicate-batch:0")
        json["items"].asJsonArray.add(first.deepCopy().apply { addProperty("id", 9002) })
        val exception = assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        assertTrue(exception.message.orEmpty().contains("导入标识"))
    }
}
