package com.lolita.app.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NewDraftStorageCommitTest {
    private suspend fun fixture(block:suspend (LolitaDatabase,ItemDraftRepository,Item)->Unit) {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,LolitaDatabase::class.java).build()
        try {
            db.brandDao().insertBrand(Brand(id=1,name="测试品牌"));db.categoryDao().insertCategory(Category(id=1,name="测试类型"))
            db.locationDao().insertLocation(Location(id=1,name="原房间"));db.locationDao().insertLocation(Location(id=2,name="新房间"))
            block(db,ItemDraftRepository(db,ItemRepository(db.itemDao(),database=db)),
                Item(brandId=1,categoryId=1,name="未保存物品",description="",status=ItemStatus.OWNED,locationId=1))
        } finally {db.close()}
    }
    @Test fun newDraftCommitRejectsItsOldRoomAfterTheNodeMovesEvenThoughAutosaveWasNormalized()=runBlocking {fixture {db,drafts,item->
        val node=db.storageNodeDao().insert(StorageNode(locationId=1,name="柜"))
        val draft=ItemDraft(dataJson=ItemDraftData(name=item.name,brandId=1,categoryId=1,locationId=1,storageNodeId=node).encode())
        drafts.save(draft)
        StorageRepository(db).moveNode(node,2,null)
        drafts.save(draft)
        assertEquals(2L,drafts.get(draft.id)!!.data().locationId)
        val result=runCatching {drafts.commit(draft.id,item.copy(storageNodeId=node))}
        assertTrue("The commit must check the actual passed Item as well as normalized draft JSON",result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("收纳位置"))
        assertTrue(db.itemDao().getAllItemsList().isEmpty())
        assertNotNull(drafts.get(draft.id))
    }}
    @Test fun newDraftCommitRejectsItsDeletedRoomWithAnActionableStorageMessage()=runBlocking {fixture {db,drafts,item->
        val draft=ItemDraft(dataJson=ItemDraftData(name=item.name,brandId=1,categoryId=1,locationId=1).encode())
        drafts.save(draft)
        db.locationDao().deleteLocation(db.locationDao().getLocationByIdSync(1)!!)
        drafts.save(draft)
        assertNull(drafts.get(draft.id)!!.data().locationId)
        val result=runCatching {drafts.commit(draft.id,item)}
        assertTrue(result.isFailure)
        assertTrue("Show why the old location must be reselected, rather than exposing a foreign-key error",
            result.exceptionOrNull()!!.message.orEmpty().contains("收纳位置"))
        assertTrue(db.itemDao().getAllItemsList().isEmpty())
        assertNotNull(drafts.get(draft.id))
    }}
}
