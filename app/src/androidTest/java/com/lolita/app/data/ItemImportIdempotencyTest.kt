package com.lolita.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteConstraintException
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Brand
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.ItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A durable import receipt closes the commit-to-navigation crash window. */
@RunWith(AndroidJUnit4::class)
class ItemImportIdempotencyTest {
    private lateinit var database: LolitaDatabase
    private lateinit var repository: ItemRepository

    @Before fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        database.brandDao().insertBrand(Brand(id = 101, name = "幂等测试品牌", createdAt = 1))
        database.categoryDao().insertCategory(Category(id = 307, name = "幂等测试分类", createdAt = 2))
        repository = ItemRepository(database.itemDao(), database = database)
    }

    @After fun tearDown() { database.close() }

    private fun item(token: String?, name: String = "OP长款"): Item {
        val row = Gson().toJsonTree(Item(brandId = 101, categoryId = 307, name = name,
            description = "详情与尺码图保留", status = ItemStatus.WISHED, createdAt = 10,
            updatedAt = 11, imageUrls = listOf("原主图"), sizeChartImageUrl = "原尺码首图",
            sizeChartImageUrls = listOf("原尺码后图"))).asJsonObject
        if (token == null) row.add("importToken", JsonNull.INSTANCE) else row.addProperty("importToken", token)
        return Gson().fromJson(row, Item::class.java)
    }

    @Test fun retryAfterRepositoryRecreationReturnsCommittedItemWithoutOverwritingIt() = runBlocking {
        val id = repository.insertItem(item("batch-one:0"))
        val saved = requireNotNull(repository.getItemById(id))
        val recreated = ItemRepository(database.itemDao(), database = database)
        val retry = item("batch-one:0", "重复保存的新名称").copy(imageUrls = listOf("重试的新主图"))
        assertEquals(id, recreated.insertItem(retry))
        assertEquals(listOf(saved), database.itemDao().getAllItemsList())
    }

    @Test fun differentTokensAndNullTokensCreateIndependentItems() = runBlocking {
        val ids = listOf("batch-one:0", "batch-one:1", null, null).map { repository.insertItem(item(it)) }
        assertEquals(4, ids.toSet().size)
        assertEquals(4, database.itemDao().getAllItemsList().size)
    }

    @Test fun concurrentRetriesCommitOneItemAndReturnTheSameId() = runBlocking {
        val ids = coroutineScope {
            List(8) { async(Dispatchers.IO) { repository.insertItem(item("concurrent-batch:0")) } }.awaitAll()
        }
        assertEquals(1, ids.toSet().size)
        assertEquals(1, database.itemDao().getAllItemsList().size)
    }

    @Test fun directDaoInsertionCannotDuplicateANonNullImportToken() = runBlocking {
        database.itemDao().insertItem(item("unique-batch:0"))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.itemDao().insertItem(item("unique-batch:0", "第二件")) }
        }
        assertEquals(1, database.itemDao().getAllItemsList().size)
    }
}
