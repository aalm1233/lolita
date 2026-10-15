package com.lolita.app.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.domain.usecase.SpendingBreakdown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemTagFilterTest {
    private lateinit var database: LolitaDatabase
    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 1, name = "回归品牌"))
        database.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        Unit
    }
    @After fun tearDown() { database.close() }
    private suspend fun item(id: Long, style: String? = null, season: String? = null) {
        database.itemDao().insertItem(Item(id = id, name = "服饰$id", brandId = 1, categoryId = 1,
            description = "", status = ItemStatus.OWNED, style = style, season = season))
    }
    @Test fun styleDrillDownUsesExactNameIncludingSqlWildcards() = runBlocking {
        item(1, style = "古典"); item(2, style = "新古典")
        item(3, style = "100%_古典"); item(4, style = "100XY古典")
        assertEquals(listOf(1L), database.itemDao().getItemsByStyle("古典").first().map { it.id })
        assertEquals(listOf(3L), database.itemDao().getItemsByStyle("100%_古典").first().map { it.id })
    }
    @Test fun seasonDrillDownMatchesTrimmedCommaTokensWithoutWildcardExpansion() = runBlocking {
        item(1, season = "春, 夏"); item(2, season = "早春"); item(3, season = " 冬 , \t春\r\n ")
        item(4, season = "春日,夏"); item(5, season = "雨%季_特别, 夏"); item(6, season = "雨天季A特别")
        assertEquals(setOf(1L, 3L), database.itemDao().getItemsBySeason("春").first().map { it.id }.toSet())
        assertEquals(setOf(1L, 4L, 5L), database.itemDao().getItemsBySeason("夏").first().map { it.id }.toSet())
        assertEquals(listOf(5L), database.itemDao().getItemsBySeason("雨%季_特别").first().map { it.id })
    }

    @Test fun seasonDrillDownAgreesWithSpendingBucketsForUnicodeWhitespace() = runBlocking<Unit> {
        val seasons = listOf("\u3000春\u3000,\u00a0夏\u00a0", "\u00a0春\u00a0", "早春", "\u3000冬\u00a0,\u3000春\u00a0")
        seasons.forEachIndexed { index, season -> item(index + 1L, season = season) }
        val buckets = SpendingBreakdown.seasons(seasons.map { it to 100.0 })
        val spring = buckets.single { it.name == "春" }
        val summer = buckets.single { it.name == "夏" }
        assertEquals(200.0, spring.amount, 0.0)
        assertEquals(50.0, summer.amount, 0.0)
        assertEquals(setOf(1L, 2L, 4L), database.itemDao().getItemsBySeason(spring.filterValue!!).first().map { it.id }.toSet())
        assertEquals(listOf(1L), database.itemDao().getItemsBySeason(summer.filterValue!!).first().map { it.id })
    }
}
