package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lolita.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    private fun data() = BackupData(
        brands = listOf(Brand(id = 101, name = "品牌", createdAt = 100)),
        categories = listOf(Category(id = 307, name = "分类", createdAt = 101)),
        coordinates = emptyList(),
        items = listOf(Item(id = 9001, brandId = 101, categoryId = 307, name = "服饰", description = "备注", status = ItemStatus.OWNED, createdAt = 102, updatedAt = 103)),
        prices = listOf(Price(id = 8, itemId = 9001, type = PriceType.FULL, totalPrice = 1234.56, createdAt = 104)),
        payments = listOf(Payment(id = 9, priceId = 8, amount = 1234.56, dueDate = 105, createdAt = 106)),
        outfitLogs = emptyList(), outfitItemCrossRefs = emptyList(), backupDate = 107
    )
    private fun json() = Gson().toJsonTree(data()).asJsonObject

    @Test fun preservesEveryFieldOnRoundTrip() {
        assertEquals(data(), BackupFormat.parse(Gson().toJson(data())))
    }

    @Test fun migratesHistoricalDefaultsWithoutLosingTimestamps() {
        val json = json()
        json.remove("formatVersion"); json.remove("schemaVersion")
        val item = json["items"].asJsonArray[0].asJsonObject
        item.remove("priority"); item.remove("imageUrls"); item.remove("coordinateOrder"); item.remove("updatedAt")
        item.remove("colors"); item.addProperty("color", "粉色")
        json["categories"].asJsonArray[0].asJsonObject.remove("group")
        listOf("styles", "seasons", "locations", "sources").forEach { json.remove(it) }
        val restored = BackupFormat.parse(json.toString())
        assertEquals(listOf("粉色"), restored.items.single().colors)
        assertEquals(102L, restored.items.single().updatedAt)
        assertEquals(ItemPriority.MEDIUM, restored.items.single().priority)
        assertEquals(CategoryGroup.CLOTHING, restored.categories.single().group)
        assertTrue(restored.items.single().imageUrls.isEmpty())
    }

    @Test fun rejectsFutureVersionsAndUnknownFields() {
        val json = json(); json.addProperty("formatVersion", 99)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        json.addProperty("formatVersion", BackupFormat.VERSION)
        json["items"].asJsonArray[0].asJsonObject.addProperty("futureData", "不可丢失")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsBrokenAssociationsDuplicateKeysAndFractionalIds() {
        val json = json(); val item = json["items"].asJsonArray[0].asJsonObject
        item.addProperty("brandId", 999)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        item.addProperty("brandId", 101)
        json["items"].asJsonArray.add(item.deepCopy())
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        json["items"].asJsonArray.remove(1); item.addProperty("id", 1.5)
        assertThrows(ArithmeticException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsCorruptColorsAndUnknownEnums() {
        val json = json(); json.remove("formatVersion")
        val item = json["items"].asJsonArray[0].asJsonObject
        item.addProperty("colors", "[broken")
        assertThrows(RuntimeException::class.java) { BackupFormat.parse(json.toString()) }
        item.add("colors", JsonParser.parseString("[]")); item.addProperty("status", "FUTURE")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
    }

    @Test fun rejectsIncompleteModernBackupAndInvalidAmounts() {
        val json = json(); json.remove("remoteItems")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(json.toString()) }
        val second = json(); second["prices"].asJsonArray[0].asJsonObject.addProperty("totalPrice", -1)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(second.toString()) }
        val third = json(); third["items"].asJsonArray[0].asJsonObject.remove("priority")
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(third.toString()) }
    }

    @Test fun migratesLegacyPurchaseDateToPaymentWithoutDuplicatingExistingPayments() {
        val json = json(); json.remove("formatVersion")
        json["prices"].asJsonArray[0].asJsonObject.addProperty("purchaseDate", 456L)
        val existing = BackupFormat.parse(json.toString())
        assertEquals(data().payments, existing.payments)
        json["payments"].asJsonArray.remove(0)
        val restored = BackupFormat.parse(json.toString())
        assertEquals(456L, restored.payments.single().paidDate)
        assertEquals(1234.56, restored.payments.single().amount, 0.0)
        assertTrue(restored.payments.single().isPaid)
    }
}
