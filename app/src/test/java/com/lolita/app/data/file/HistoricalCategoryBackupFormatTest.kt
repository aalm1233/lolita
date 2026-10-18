package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.TryOnRole
import org.junit.Assert.*
import org.junit.Test

/** Exercise the public backup parser; no new inference API is required for the RED run. */
class HistoricalCategoryBackupFormatTest {
    private val gson = Gson()

    private fun data(categories: List<Category>) = BackupData(
        brands = emptyList(), categories = categories, coordinates = emptyList(), items = emptyList(),
        prices = emptyList(), payments = emptyList(), outfitLogs = emptyList(), outfitItemCrossRefs = emptyList(),
        backupDate = 700, appVersion = "2.55.0", formatVersion = 10, schemaVersion = 30
    )

    private fun json(categories: List<Category>, version: Int = 10): JsonObject =
        gson.toJsonTree(data(categories)).asJsonObject.apply {
            addProperty("formatVersion", version)
            if (version < 10) {
                addProperty("schemaVersion", 29)
                getAsJsonArray("categories").forEach {
                    it.asJsonObject.remove("categorySemantic")
                    it.asJsonObject.remove("defaultWearRole")
                }
            }
        }

    @Test fun formatsOneThroughNineRecognizeClearNamesWhilePreservingEveryOtherCategoryField() {
        val original = listOf(
            Category(id = 7, name = "JSK", createdAt = 101, isPreset = true),
            Category(id = 8, name = " op ", createdAt = 102),
            Category(id = 9, name = "SK", createdAt = 103),
            Category(id = 10, name = "其他头饰", createdAt = 104, group = CategoryGroup.ACCESSORY),
            Category(id = 11, name = " ｊｓｋ ", createdAt = 105),
            Category(id = 12, name = "JSK / OP", createdAt = 106),
            Category(id = 13, name = "我的裙子", createdAt = 107)
        )
        val expected = listOf(
            original[0].copy(categorySemantic = CategorySemantic.JSK),
            original[1].copy(categorySemantic = CategorySemantic.OP),
            original[2].copy(categorySemantic = CategorySemantic.SK),
            original[3].copy(categorySemantic = CategorySemantic.OTHER_ACCESSORY, defaultWearRole = TryOnRole.HEAD),
            original[4].copy(categorySemantic = CategorySemantic.JSK),
            original[5], original[6]
        )
        for (version in 1..9) {
            assertEquals("Historical format $version must recognize explicit names", expected,
                BackupFormat.parse(json(original, version).toString()).categories)
        }
    }

    @Test fun versionTenFrom255RecognizesUnconfirmedNamesWithoutTouchingItemsOrOtherBackupData() {
        val category = Category(id = 7, name = "JSK", createdAt = 101, isPreset = true)
        val original = data(listOf(category)).copy(
            brands = listOf(Brand(id = 11, name = "原品牌", createdAt = 100)),
            items = listOf(Item(id = 20, brandId = 11, categoryId = 7, name = "原名称", description = "原备注",
                status = ItemStatus.OWNED, createdAt = 102, updatedAt = 103, size = "旧M",
                colors = listOf("[浅粉]", "深粉"), imageUrls = listOf("/images/second.png", "/images/first.png")))
        )
        val expected = original.copy(categories = listOf(category.copy(categorySemantic = CategorySemantic.JSK)))
        val parsed = BackupFormat.parse(gson.toJson(original))
        assertEquals(expected, parsed)
        // A preview and a later restore both use parse; replay keeps the exact normalized meaning.
        assertEquals(expected, BackupFormat.parse(gson.toJson(parsed)))
    }

    @Test fun confirmedMeaningAndExistingWearRoleArePreservedDuringRecognition() {
        val categories = listOf(
            Category(id = 7, name = "JSK", createdAt = 101, categorySemantic = CategorySemantic.SK,
                defaultWearRole = TryOnRole.OUTER),
            Category(id = 8, name = "其他头饰", createdAt = 102, categorySemantic = CategorySemantic.BONNET),
            Category(id = 9, name = " 其他头饰 ", createdAt = 103, defaultWearRole = TryOnRole.OTHER),
            Category(id = 10, name = "jsk", createdAt = 104, defaultWearRole = TryOnRole.HEAD)
        )
        assertEquals(listOf(
            categories[0], categories[1],
            categories[2].copy(categorySemantic = CategorySemantic.OTHER_ACCESSORY),
            categories[3].copy(categorySemantic = CategorySemantic.JSK)
        ), BackupFormat.parse(json(categories).toString()).categories)
    }

    @Test fun recognizableNamesDoNotMakeUnknownSemanticOrRoleTokensValid() {
        val base = json(listOf(Category(id = 7, name = "JSK", createdAt = 101)))
        val invalidSemantic = base.deepCopy().apply {
            getAsJsonArray("categories")[0].asJsonObject.addProperty("categorySemantic", "FUTURE_SEMANTIC")
        }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(invalidSemantic.toString()) }
        val invalidRole = base.deepCopy().apply {
            getAsJsonArray("categories")[0].asJsonObject.addProperty("defaultWearRole", "FUTURE_ROLE")
        }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(invalidRole.toString()) }
        for (field in listOf("categorySemantic", "name")) {
            val invalidNull = base.deepCopy().apply {
                getAsJsonArray("categories")[0].asJsonObject.add(field, JsonNull.INSTANCE)
            }
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.parse(invalidNull.toString()) }
        }
    }
}
