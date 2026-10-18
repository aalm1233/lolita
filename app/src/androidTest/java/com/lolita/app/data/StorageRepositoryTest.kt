package com.lolita.app.data

import androidx.room.Room
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.StorageRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageRepositoryTest {
    private lateinit var db: LolitaDatabase
    private lateinit var repository: StorageRepository
    private var brand = 0L
    private var category = 0L
    private var entrance = 0L
    private var bedroom = 0L

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LolitaDatabase::class.java).build()
        repository = StorageRepository(db)
        brand = db.brandDao().insertBrand(Brand(name = "测试品牌"))
        category = db.categoryDao().insertCategory(Category(name = "测试分类"))
        entrance = db.locationDao().insertLocation(Location(name = "玄关"))
        bedroom = db.locationDao().insertLocation(Location(name = "卧室"))
    }
    @After fun teardown() { db.close() }

    private suspend fun item(name: String, node: Long?, parent: Long? = null): Long =
        db.itemDao().insertItem(Item(brandId = brand, categoryId = category, name = name,
            description = "保留资料", status = ItemStatus.OWNED, locationId = entrance,
            storageNodeId = node, parentItemId = parent, size = "EU 37"))

    private suspend fun rejects(block: suspend () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("操作必须明确拒绝", rejected)
    }

    @Test fun rejectsOccupiedDeletionAndExplicitClearKeepsEveryItemAndParentLink() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        val main = item("玛丽珍鞋", shelf)
        val attached = item("配套鞋扣", cabinet, main)
        val originals = listOf(main, attached).map { requireNotNull(db.itemDao().getItemById(it)) }
        rejects { repository.deleteNode(cabinet) }
        assertEquals(2, repository.getNodes().size)
        repository.deleteNode(cabinet, clearContents = true)
        assertTrue(repository.getNodes().isEmpty())
        originals.forEach { original ->
            assertEquals(original.copy(locationId = null, storageNodeId = null), db.itemDao().getItemById(original.id))
        }
    }

    @Test fun movesCabinetDescendantsAndAttachedItemsTogetherWithoutChangingOtherFields() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        val main = item("鞋", shelf)
        val attached = item("鞋扣", cabinet, main)
        val original = listOf(main, attached).associateWith { requireNotNull(db.itemDao().getItemById(it)) }
        val impact = repository.previewMoveImpact(cabinet)
        assertEquals(2, impact.itemCount)
        assertEquals(2, impact.nodeCount)
        repository.moveNode(cabinet, bedroom, null)
        assertTrue(repository.getNodes().all { it.locationId == bedroom })
        original.forEach { (id, row) -> assertEquals(row.copy(locationId = bedroom), db.itemDao().getItemById(id)) }
    }

    @Test fun failedMoveRollsBackEveryDescendantAndItem() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        val shoe = item("鞋", shelf)
        repository.createNode(bedroom, null, "鞋柜")
        val before = repository.getNodes()
        val oldItem = db.itemDao().getItemById(shoe)
        rejects { repository.moveNode(cabinet, bedroom, null) }
        assertEquals(before, repository.getNodes())
        assertEquals(oldItem, db.itemDao().getItemById(shoe))
        rejects { repository.moveNode(cabinet, entrance, shelf) }
        assertEquals(before, repository.getNodes())
    }

    @Test fun bulkMoveValidatesDestinationAndPreservesUnselectedRecords() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val destination = repository.createNode(bedroom, null, "假发架")
        val selected = item("选中", cabinet)
        val untouched = item("未选中", cabinet)
        val old = db.itemDao().getItemById(untouched)
        rejects { repository.moveItems(setOf(selected), entrance, destination) }
        repository.moveItems(setOf(selected), bedroom, destination)
        assertEquals(bedroom, db.itemDao().getItemById(selected)!!.locationId)
        assertEquals(destination, db.itemDao().getItemById(selected)!!.storageNodeId)
        assertEquals(old, db.itemDao().getItemById(untouched))
    }

    @Test fun rejectsMissingParentCrossRoomAndFourthPathLevel() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        rejects { repository.createNode(entrance, 9999, "不存在") }
        rejects { repository.createNode(bedroom, cabinet, "跨房间") }
        rejects { repository.createNode(entrance, shelf, "第四级") }
        rejects { repository.createNode(entrance, null, " 鞋柜 ") }
        assertEquals(2, repository.getNodes().size)
    }

    @Test fun uniqueIndexProtectsRootSiblingsEvenWhenParentIsNull() = runBlocking {
        repository.createNode(entrance, null, "鞋柜")
        var rejected = false
        try { db.storageNodeDao().insert(StorageNode(locationId = entrance, name = "鞋柜")) }
        catch (_: SQLiteConstraintException) { rejected = true }
        assertTrue("根节点也必须由数据库唯一约束保护", rejected)
        repository.createNode(bedroom, null, "鞋柜")
        assertEquals(2, repository.getNodes().size)
    }

    @Test fun roomDeletionRequiresExplicitClearAndRetainsItemsAndAllBusinessFields() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        val shoe = item("鞋", shelf)
        val old = requireNotNull(db.itemDao().getItemById(shoe))
        val room = requireNotNull(db.locationDao().getLocationByIdSync(entrance))
        rejects { repository.deleteRoom(room) }
        assertNotNull(db.locationDao().getLocationByIdSync(entrance))
        repository.deleteRoom(room, clearContents = true)
        assertEquals(old.copy(locationId = null, storageNodeId = null), db.itemDao().getItemById(shoe))
        assertNull(db.locationDao().getLocationByIdSync(entrance))
        assertTrue(repository.getNodes().isEmpty())
        assertNotNull(db.locationDao().getLocationByIdSync(bedroom))
    }

    @Test fun renamingParentsRefreshesPathAndEditingNeverChangesTheItemLocationIds() = runBlocking {
        val cabinet = repository.createNode(entrance, null, "鞋柜")
        val shelf = repository.createNode(entrance, cabinet, "第二层")
        val shoe = item("鞋", shelf)
        val old = db.itemDao().getItemById(shoe)
        repository.renameNode(cabinet, "常穿鞋柜")
        assertEquals("玄关 → 常穿鞋柜 → 第二层", repository.pathFor(entrance, shelf,
            db.locationDao().getAllLocationsList(), repository.getNodes()))
        assertEquals(old, db.itemDao().getItemById(shoe))
    }
}
