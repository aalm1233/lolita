package com.lolita.app.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Visible storage eligibility is separate from preserved raw references and deletion guards. */
class OwnedStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(block: suspend (LolitaDatabase, StorageRepository, ItemRepository, Long) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            db.brandDao().insertBrand(Brand(id=1,name="位置测试品牌"))
            db.categoryDao().insertCategory(Category(id=1,name="主裙"))
            db.locationDao().insertLocation(Location(id=1,name="卧室"))
            db.locationDao().insertLocation(Location(id=2,name="衣帽间"))
            val storage=StorageRepository(db)
            val shelf=storage.createNode(1,null,"衣柜")
            block(db,storage,ItemRepository(db.itemDao(),database=db),shelf)
        } finally { db.close() }
    }
    private fun item(id:Long,status:ItemStatus,room:Long?=1,node:Long?=null)=Item(id=id,brandId=1,categoryId=1,
        name="物品$id",description="保留原资料",status=status,locationId=room,storageNodeId=node,
        imageUrls=listOf("controlled-fixture-$id.png"),createdAt=100,updatedAt=200)
    private suspend fun reject(block:suspend ()->Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun allVisibleQueriesExcludeUnownedButIntegrityCountsRetainEveryReference()=runBlocking { fixture {db,_,_,shelf->
        ItemStatus.entries.forEachIndexed { i,status ->
            db.itemDao().insertItem(item(i+1L,status,node=shelf))
            db.itemDao().insertItem(item(i+11L,status,room=null))
        }
        val original=db.itemDao().getAllItemsList()
        val dao=db.itemDao()
        assertEquals(listOf(1L),dao.getItemsByLocationId(1).first().map {it.id})
        assertEquals(listOf(11L),dao.getItemsWithNoLocation().first().map {it.id})
        assertEquals(1,dao.countItemsWithNoLocation().first())
        assertEquals(1,dao.getItemCountsByLocation().first().single().count)
        assertEquals(listOf("controlled-fixture-1.png"),dao.getLocationItemImages().first().single().imageUrls)
        assertEquals(listOf(1L),dao.observeItemsInStorageNodes(listOf(shelf)).first().map {it.id})
        assertTrue(dao.observeRoomItemsWithoutStorage(1).first().isEmpty())
        assertEquals(5,dao.countItemsByLocation(1))
        assertEquals(5,dao.countItemsInStorageNodes(listOf(shelf)))
        assertEquals(original,dao.getAllItemsList())
    } }
    @Test fun paymentCompletionAndUndoRefreshSharedAccessoriesWithoutTouchingStoredLocations()=runBlocking { fixture {db,_,_,shelf->
        db.itemDao().insertItem(item(1,ItemStatus.OWNED,node=shelf))
        db.itemDao().insertItem(item(2,ItemStatus.OWNED,node=shelf).copy(parentItemId=1,includedInParentPrice=true))
        db.itemDao().insertItem(item(3,ItemStatus.OWNED,node=shelf).copy(parentItemId=1))
        val orders=OrderRepository(db,context)
        val price=orders.createOrder(Price(itemId=1,type=PriceType.DEPOSIT_BALANCE,totalPrice=100.0,deposit=20.0,balance=80.0),
            listOf(PaymentStage(priceId=0,name="定金",amount=20.0),PaymentStage(priceId=0,name="尾款",amount=80.0)))
        suspend fun visible()=db.itemDao().getItemsByLocationId(1).first().map {it.id}.toSet()
        assertEquals(setOf(3L),visible())
        orders.recordPayment(price,20.0,1_700_000_000_000)
        assertEquals(setOf(3L),visible())
        val tail=orders.recordPayment(price,80.0,1_700_000_000_000)
        assertEquals(setOf(1L,2L,3L),visible())
        orders.deletePayment(tail)
        assertEquals(setOf(3L),visible())
        assertEquals(3,db.itemDao().countItemsByLocation(1))
        (1L..3L).forEach {id->assertEquals(shelf,db.itemDao().getItemById(id)!!.storageNodeId)}
        // Explicit historic ownership is evidence; missing old payments are not fabricated debt.
        db.orderDao().updatePurchaseRecordStatus(1,PurchaseRecordStatus.HISTORICAL_OWNED)
        db.orderDao().refreshStatuses()
        assertEquals(setOf(1L,2L,3L),visible())
    } }
    @Test fun assignmentsRejectUnownedAndStaleOrMixedSubmissionsAtomicallyButKeepLegacyEdits()=runBlocking { fixture {db,storage,items,shelf->
        reject { items.insertItem(item(1,ItemStatus.PENDING_BALANCE,node=shelf)) }
        assertTrue(db.itemDao().getAllItemsList().isEmpty())
        db.itemDao().insertItem(item(1,ItemStatus.OWNED,node=shelf))
        db.itemDao().insertItem(item(2,ItemStatus.PENDING_BALANCE,node=shelf))
        val original=db.itemDao().getAllItemsList()
        reject { storage.moveItems(setOf(1,2),2,null) }
        assertEquals(original,db.itemDao().getAllItemsList())
        reject { items.updateItem(original.first {it.id==2L}.copy(locationId=2,storageNodeId=null)) }
        assertEquals(original,db.itemDao().getAllItemsList())
        val old=original.first {it.id==2L}
        items.updateItem(old.copy(description="只修改备注"))
        val edited=db.itemDao().getItemById(2)!!
        assertEquals(old.copy(description="只修改备注",updatedAt=edited.updatedAt),edited)
        assertEquals(2,storage.previewMoveImpact(shelf).itemCount)
        reject { storage.deleteNode(shelf) }
        // A stale editor cannot bypass the effective status of a currently bound order.
        db.priceDao().insertPrice(Price(id=10,itemId=1,type=PriceType.FULL,totalPrice=100.0))
        db.orderDao().insertBinding(OrderBinding(1,10))
        db.orderDao().refreshStatuses()
        reject { items.updateItem(original.first {it.id==1L}.copy(locationId=2,storageNodeId=null)) }
        storage.moveItems(setOf(2),null,null)
        assertNull(db.itemDao().getItemById(2)!!.locationId)
        assertNull(db.itemDao().getItemById(2)!!.storageNodeId)
    } }
}
