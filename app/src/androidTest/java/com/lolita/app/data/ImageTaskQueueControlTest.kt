package com.lolita.app.data

import com.lolita.app.data.file.BackupManager
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ImageTaskQueueControlTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture)->Unit) {
        val f=MaterialTestFixture()
        try { f.init();withTimeout(45_000){block(f)} } finally { f.close() }
    }
    private fun batch()=MaterialBatchIdentity(UUID.randomUUID().toString(),"队列管理回归")
    @Test fun stopWaitingTailKeepsCurrentAndDoesNotSweepLaterSubmissions()=runBlocking { fixture { f ->
        val items=f.batchItems(4);val group=batch()
        val ids=f.coordinator.submitMaterialBatch(group,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.frontOutput={entered.complete(Unit);release.await();f.opaque}
        val drain=f.scope.launch{f.runner.runQueue()};entered.await()
        val current=f.db.imageTaskDao().getAll().single{it.status.isActive}
        val snapshot=ids.filter{it!=current.id}
        val later=f.coordinator.submitMaterialBatch(batch(),UUID.randomUUID().toString(),f.batchInputs(items.take(1)),ImageGenerationProvider.SEEDREAM)
        assertEquals(3,f.coordinator.stopQueued(snapshot))
        assertEquals(ImageTaskStatus.GENERATING,f.db.imageTaskDao().get(current.id)!!.status)
        assertTrue(snapshot.all{f.db.imageTaskDao().get(it)!!.let{t->t.status==ImageTaskStatus.CANCELLED && !t.requestSent}})
        assertEquals(ImageTaskStatus.QUEUED,f.db.imageTaskDao().get(later.single())!!.status)
        assertEquals(0,f.coordinator.stopQueued(snapshot))
        release.complete(Unit);drain.join()
        // The controlled service-start callback does not admit later IDs into the old session.
        f.runner.runQueue()
        assertEquals(2,f.frontCalls);assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,f.db.imageTaskDao().get(current.id)!!.status)
        assertEquals(items,f.db.itemDao().getAllItemsList().sortedBy{it.id})
    } }
    @Test fun staleStopSkipsAlreadyClaimedAndBatchScopeExcludesOtherTasks()=runBlocking { fixture { f ->
        val items=f.batchItems(2);val a=batch();val b=batch()
        val first=f.coordinator.submitMaterialBatch(a,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        val other=f.coordinator.submitMaterialBatch(b,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.frontOutput={entered.complete(Unit);release.await();f.opaque}
        val drain=f.scope.launch{f.runner.runQueue()};entered.await()
        assertEquals(1,f.coordinator.stopQueued(first+other,a.id))
        assertTrue(other.all{f.db.imageTaskDao().get(it)!!.status==ImageTaskStatus.QUEUED})
        f.coordinator.stopQueued(other,b.id);release.complete(Unit);drain.join();assertEquals(1,f.frontCalls)
    } }
    @Test fun cancellingEntireBatchStopsTailBeforeJoiningCurrentReader()=runBlocking { fixture { f ->
        val items=f.batchItems(5);val group=batch()
        val ids=f.coordinator.submitMaterialBatch(group,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        val entered=CompletableDeferred<Unit>()
        f.frontOutput={entered.complete(Unit);awaitCancellation()}
        val drain=f.scope.launch{f.runner.runQueue()};entered.await()
        f.coordinator.cancelMaterialBatch(group.id,ids);drain.join()
        assertEquals(1,f.frontCalls);assertTrue(ids.all{f.db.imageTaskDao().get(it)!!.status==ImageTaskStatus.CANCELLED})
        f.runner.runQueue();assertEquals(1,f.frontCalls)
    } }
    @Test fun discardMixedStagesSkipsSavedAndPreservesSharedAlbumAndZip()=runBlocking { fixture { f ->
        val items=f.batchItems(3);val group=batch()
        val front=f.coordinator.submitMaterialBatch(group,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        val layers=f.coordinator.submitLayerBatch(group,UUID.randomUUID().toString(),f.batchLayers(front.take(2)));f.runner.runQueue()
        val savedTask=f.db.imageTaskDao().get(layers[0])!!
        f.coordinator.confirmMaterialBatch(group.id,listOf(BatchMaterialConfirmation(savedTask.id,savedTask.resultImagePath!!,null)),true)
        val shared=f.db.imageTaskDao().get(layers[1])!!.resultImagePath!!
        val album=f.db.itemDao().getItemById(items[1].id)!!
        f.db.itemDao().updateItem(album.copy(imageUrls=album.imageUrls+shared))
        val before=f.db.itemDao().getAllItemsList();val materials=f.db.itemTryOnMaterialDao().getAll()
        assertEquals(2,f.coordinator.discardPending(front+layers+"missing",group.id))
        assertEquals(0,f.coordinator.discardPending(front+layers,group.id))
        assertEquals(materials,f.db.itemTryOnMaterialDao().getAll());assertEquals(before,f.db.itemDao().getAllItemsList())
        assertTrue(File(shared).isFile);assertTrue(File(savedTask.resultImagePath!!).isFile)
        assertTrue(listOf(front[2],layers[1]).all{f.db.imageTaskDao().get(it)!!.let{t->t.status==ImageTaskStatus.DISCARDED && t.resultImagePath==null && t.layersJson=="[]"}})
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try {
            manager.importBackup(zip).getOrThrow()
            assertEquals(1,f.db.itemTryOnMaterialDao().getAll().size)
            assertEquals(2,f.db.imageTaskDao().getAll().count{it.status==ImageTaskStatus.DISCARDED})
            val restored=f.db.itemDao().getItemById(album.id)!!.imageUrls.last();assertTrue(File(restored).isFile)
            assertEquals(imageSha256(shared),imageSha256(restored))
        } finally { f.context.contentResolver.delete(zip,null,null) }
    } }
    @Test fun bulkRollbackDoesNotDeleteAnyPreviewOrPartiallyStopQueue()=runBlocking { fixture { f ->
        val group=batch();val ids=f.coordinator.submitMaterialBatch(group,UUID.randomUUID().toString(),f.batchInputs(f.batchItems(3)),ImageGenerationProvider.SEEDREAM)
        val sql=f.db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_stop BEFORE UPDATE ON image_tasks WHEN NEW.status='CANCELLED' AND OLD.batch_position=1 BEGIN SELECT RAISE(ABORT,'controlled'); END")
        assertTrue(runCatching{f.coordinator.stopQueued(ids)}.isFailure)
        assertTrue(ids.all{f.db.imageTaskDao().get(it)!!.status==ImageTaskStatus.QUEUED})
        sql.execSQL("DROP TRIGGER fail_stop");f.runner.runQueue()
        val before=ids.map{f.db.imageTaskDao().get(it)!!}
        sql.execSQL("CREATE TRIGGER fail_discard BEFORE UPDATE ON image_tasks WHEN NEW.status='DISCARDED' AND OLD.batch_position=1 BEGIN SELECT RAISE(ABORT,'controlled'); END")
        assertTrue(runCatching{f.coordinator.discardPending(ids)}.isFailure)
        assertEquals(before,ids.map{f.db.imageTaskDao().get(it)!!});assertTrue(before.all{File(it.resultImagePath!!).isFile})
    } }
}
