package com.lolita.app.data

import com.lolita.app.data.file.BackupManager
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MaterialBatchWorkflowTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture)->Unit){val f=MaterialTestFixture();try{f.init();withTimeout(45000){block(f)}}finally{f.close()}}
    private fun identity()=MaterialBatchIdentity(UUID.randomUUID().toString(),"测试素材批次")
    @Test fun entireBatchQueuesOnceAndPartialFailureDoesNotBlockOtherItemsOrAutoStartLayers()=runBlocking{fixture{f->
        val items=f.batchItems();val inputs=f.batchInputs(items);val batch=identity();val op=UUID.randomUUID().toString()
        val before=f.db.itemDao().getAllItemsList()
        val ids=f.coordinator.submitMaterialBatch(batch,op,inputs,ImageGenerationProvider.SEEDREAM)
        assertEquals(3,f.serviceStarts);assertEquals(0,f.frontCalls)
        assertEquals(ids,f.coordinator.submitMaterialBatch(batch,op,inputs,ImageGenerationProvider.SEEDREAM));assertEquals(3,f.serviceStarts)
        f.frontOutput={if(f.frontCalls==2)throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL) else f.opaque}
        f.runner.runQueue();val tasks=ids.map{f.db.imageTaskDao().get(it)!!}
        assertEquals(2,tasks.count{it.status==ImageTaskStatus.AWAITING_CONFIRMATION});assertEquals(1,tasks.count{it.status==ImageTaskStatus.FAILED})
        assertEquals(0,f.layerCalls);f.runner.runQueue();assertEquals(3,f.frontCalls)
        val ready=tasks.filter{it.status==ImageTaskStatus.AWAITING_CONFIRMATION}.map{it.id}
        val layerIds=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(ready))
        assertEquals(0,f.layerCalls);f.runner.runQueue();assertEquals(2,f.layerCalls)
        val layers=layerIds.map{f.db.imageTaskDao().get(it)!!}
        assertTrue(layers.all{it.batchId==batch.id && it.batchName==batch.name && it.generatedImageCount==2})
        f.coordinator.confirmMaterialBatch(batch.id,layers.map{BatchMaterialConfirmation(it.id,it.resultImagePath!!,null)},true)
        assertEquals(2,f.db.itemTryOnMaterialDao().getAll().size);assertEquals(before,f.db.itemDao().getAllItemsList());assertEquals(0,f.fusionCalls)
    }}
    @Test fun changedInputAndDatabaseRollbackNeverStartAPartialPaidBatch()=runBlocking{fixture{f->
        val inputs=f.batchInputs(f.batchItems());val batch=identity();val op=UUID.randomUUID().toString()
        File(inputs[1].input.imagePath).appendBytes(byteArrayOf(9))
        assertTrue(runCatching{f.coordinator.submitMaterialBatch(batch,op,inputs,ImageGenerationProvider.SEEDREAM)}.isFailure)
        assertEquals(0,f.serviceStarts);assertTrue(f.db.imageTaskDao().getAll().isEmpty())
        val valid=f.batchInputs(f.db.itemDao().getAllItemsList())
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_batch_insert BEFORE INSERT ON image_tasks WHEN NEW.batch_position=1 BEGIN SELECT RAISE(ABORT,'controlled batch rollback'); END")
        assertTrue(runCatching{f.coordinator.submitMaterialBatch(batch,op,valid,ImageGenerationProvider.SEEDREAM)}.isFailure)
        assertEquals(0,f.serviceStarts);assertTrue(f.db.imageTaskDao().getAll().isEmpty());assertEquals(0,f.frontCalls)
    }}
    @Test fun staleMaterialInOneRowRollsBackEverySaveAndKeepsAllCandidates()=runBlocking{fixture{f->
        val items=f.batchItems(2);val batch=identity()
        val ids=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        val layers=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(ids));f.runner.runQueue()
        val snapshot=layers.map{val t=f.db.imageTaskDao().get(it)!!;BatchMaterialConfirmation(it,t.resultImagePath!!,null)}
        val replacement=f.confirmed()
        assertTrue(runCatching{f.coordinator.confirmMaterialBatch(batch.id,snapshot,true)}.isFailure)
        assertEquals(listOf(replacement),f.db.itemTryOnMaterialDao().getAll())
        assertTrue(snapshot.all{File(it.imagePath).isFile && f.db.imageTaskDao().get(it.taskId)!!.status==ImageTaskStatus.AWAITING_CONFIRMATION})
    }}
    @Test fun zipKeepsBatchGroupingAndInterruptsQueueWithoutResendingOnRestore()=runBlocking{fixture{f->
        val batch=identity();val ids=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(f.batchItems()),ImageGenerationProvider.SEEDREAM)
        val before=ids.map{f.db.imageTaskDao().get(it)!!}
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try{manager.importBackup(zip).getOrThrow();val after=ids.map{f.db.imageTaskDao().get(it)!!}
            assertEquals(before.map{it.batchId to it.batchPosition},after.map{it.batchId to it.batchPosition})
            assertTrue(after.all{it.batchName==batch.name && it.status==ImageTaskStatus.INTERRUPTED})
            after.forEach{ImageTaskRequest.decode(it.requestJson).validateInputImages(it.sourceImagePaths)}
            f.runner.runQueue();assertEquals(0,f.frontCalls)
        }finally{f.context.contentResolver.delete(zip,null,null)}
    }}
    @Test fun cancellingOneBatchCannotStopAnotherBatchAndAcceptedInputsRemainIndependent()=runBlocking{fixture{f->
        val items=f.batchItems(2);val a=identity();val b=identity()
        val first=f.coordinator.submitMaterialBatch(a,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        val other=f.coordinator.submitMaterialBatch(b,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        f.coordinator.cancelMaterialBatch(a.id,first+other)
        assertTrue(first.all{f.db.imageTaskDao().get(it)!!.status==ImageTaskStatus.CANCELLED})
        f.runner.runQueue();assertEquals(2,f.frontCalls)
        assertTrue(other.all{f.db.imageTaskDao().get(it)!!.status==ImageTaskStatus.AWAITING_CONFIRMATION})
        assertEquals(0,f.layerCalls)
    }}
    @Test fun acceptedFrontStaysReviewableAfterLayerAdmissionAndZipRestore()=runBlocking{fixture{f->
        val batch=identity();val ids=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(f.batchItems(2)),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        val old=f.db.imageTaskDao().get(ids.first())!!;val preview=old.resultImagePath!!;val hash=imageSha256(preview)
        f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(ids.take(1)))
        assertTrue(f.db.imageTaskDao().get(ids.first())!!.protectedImagePaths().contains(preview))
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try{manager.importBackup(zip).getOrThrow();val restored=f.db.imageTaskDao().get(ids.first())!!
            assertEquals(ImageTaskStatus.COMPLETED,restored.status);assertNotNull(restored.resultImagePath)
            assertEquals(hash,imageSha256(restored.resultImagePath!!));assertNotEquals(preview,restored.resultImagePath)
            f.runner.runQueue();assertEquals(2,f.frontCalls);assertEquals(0,f.layerCalls)
        }finally{f.context.contentResolver.delete(zip,null,null)}
    }}
    @Test fun staleFailureCannotCreateAnotherRetryAfterANewerTaskWasAccepted()=runBlocking{fixture{f->
        val batch=identity();val front=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(f.batchItems(1)),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        f.layerOutput={throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)}
        val failed=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(front)).single();f.runner.runQueue()
        val old=f.db.imageTaskDao().get(failed)!!;val input=f.coordinator.prepareMaterialInput(old.sourceImagePaths.single(),false)
        val rows=listOf(BatchLayerInput(failed,input));val next=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),rows)
        val starts=f.serviceStarts
        assertTrue(runCatching{f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),rows)}.isFailure)
        assertEquals(starts,f.serviceStarts);assertEquals(1,next.size);assertEquals(1,f.layerCalls)
    }}
}
