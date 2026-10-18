package com.lolita.app.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecognitionDeletionTest {
    @Test fun deletingTheItemAtomicallyRemovesItsDraftTasksAndBothKindsOfEvidence() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val database=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        try {
            val brand=database.brandDao().insertBrand(Brand(name="测试品牌"))
            val category=database.categoryDao().insertCategory(Category(name="测试类型"))
            val item=Item(id=1,brandId=brand,categoryId=category,name="待删除",description="",status=ItemStatus.OWNED)
            database.itemDao().insertItem(item)
            val draft=ItemDraft(dataJson=ItemDraftData(name=item.name,brandId=brand,categoryId=category).encode(),targetItemId=1)
            database.itemDraftDao().save(draft)
            val request=RecognitionRequest(draftId=draft.id,targetItemId=1,
                regions=listOf(RecognitionRegion("region","0".repeat(64),8,8)),baseline=emptyMap())
            val task=RecognitionTask(UUID.randomUUID().toString(),draft.id,1,request.encode(),listOf("/isolated-missing-region.png"))
            database.recognitionDao().insert(task)
            val candidate=RecognitionCandidate("name",RecognitionField.NAME,"待核对",RecognitionSource.TEXT,listOf("region"),"品名：待核对")
            val payload=RecognitionEvidencePayload(taskId=task.id,appearanceOnly=false,candidate=candidate).encode()
            listOf<Long?>(null,1L).forEach { itemId -> database.recognitionDao().insertEvidence(ItemEvidence(
                UUID.randomUUID().toString(),itemId,draft.id,RecognitionField.NAME,payload,listOf("/isolated-missing-region.png"))) }
            ItemRepository(database.itemDao(),database=database).deleteItem(item)
            assertNull(database.itemDao().getItemById(1))
            assertNull("Deleted item must not leave a dangling draft",database.itemDraftDao().get(draft.id))
            assertTrue(database.recognitionDao().getAll().isEmpty())
            assertTrue(database.recognitionDao().getEvidence().isEmpty())
        } finally {database.close()}
    }
}
