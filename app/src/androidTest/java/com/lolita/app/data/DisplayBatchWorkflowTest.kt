package com.lolita.app.data

import com.lolita.app.data.file.BackupManager
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DisplayBatchWorkflowTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture)->Unit) {
        val f=MaterialTestFixture()
        try { f.init();withTimeout(90000){block(f)} } finally { f.close() }
    }
    private fun batch()=MaterialBatchIdentity(UUID.randomUUID().toString(),"批量主视与人台")
    private suspend fun inputs(f:MaterialTestFixture)=f.db.itemDao().getAllItemsList().sortedBy{it.id}.mapIndexed{index,item->
        BatchDisplayInput(item.id,f.coordinator.prepareDisplayInput(item.id,item.imageUrls.first()),item.imageUrls.first(),"保留原细节",index)
    }
    private suspend fun ready(f:MaterialTestFixture, b:MaterialBatchIdentity):List<BatchMaterialConfirmation> {
        val ids=f.coordinator.submitDisplayBatch(b,UUID.randomUUID().toString(),inputs(f),ImageGenerationProvider.SEEDREAM)
        f.runner.runQueue()
        return ids.map { val task=f.db.imageTaskDao().get(it)!!;assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,task.status);BatchMaterialConfirmation(it,task.resultImagePath!!,null) }
    }
    @Test fun mixedTypesFreezeActualTemplatesAndSaveOneImageEachWithoutLayers()=runBlocking{fixture{f->
        f.categoryItems();val original=f.db.itemDao().getAllItemsList().associateBy{it.id}
        val rows=inputs(f);val b=batch();val op=UUID.randomUUID().toString()
        rows.forEach{row->val p=row.prepared
            assertEquals(if(p.subject.presentation==MannequinPresentation.DRESS_FORM)2 else 1,p.paths.size)
            if(p.templatePath!=null) assertEquals(imageSha256(p.templatePath),p.templateSha256)
        }
        assertTrue(rows.any{it.prepared.subject.semantic==CategorySemantic.BLOUSE && it.prepared.paths.size==2})
        val ids=f.coordinator.submitDisplayBatch(b,op,rows,ImageGenerationProvider.SEEDREAM)
        assertEquals(ids,f.coordinator.submitDisplayBatch(b,op,rows,ImageGenerationProvider.SEEDREAM))
        assertEquals(rows.size,f.serviceStarts);f.runner.runQueue()
        val save=ids.map{val t=f.db.imageTaskDao().get(it)!!;t.validate();val r=ImageTaskRequest.decode(t.requestJson)
            assertEquals(ImageTaskKind.MANNEQUIN,t.kind);assertNull(r.materialSpec);r.validateInputImages(t.sourceImagePaths)
            BatchMaterialConfirmation(it,t.resultImagePath!!,null)
        }
        f.coordinator.confirmDisplayBatch(b.id,save,true);f.coordinator.confirmDisplayBatch(b.id,save,true)
        f.db.itemDao().getAllItemsList().forEach{item->val before=original.getValue(item.id)
            assertEquals(before.imageUrls,item.imageUrls.dropLast(1));assertEquals(before.copy(imageUrls=item.imageUrls,updatedAt=item.updatedAt),item)
        }
        assertEquals(rows.size,f.db.imageProvenanceDao().getAll().size)
        val first=rows.first();val saved=save.first();f.coordinator.setDisplayCover(first.itemId,saved.taskId)
        assertEquals(listOf(saved.imagePath)+original.getValue(first.itemId).imageUrls,f.db.itemDao().getItemById(first.itemId)!!.imageUrls)
        assertEquals(0,f.layerCalls);assertEquals(0,f.frontCalls);assertEquals(rows.size,f.displayCalls)
        assertTrue(f.db.itemTryOnMaterialDao().getAll().isEmpty())
    }}
    @Test fun staleCompositionRollsBackWholeSaveAndLeavesAllPreviews()=runBlocking{fixture{f->
        val items=f.batchItems(2);val b=batch();val save=ready(f,b)
        val last=items.last();f.db.itemDao().updateItem(last.copy(composition=ItemComposition.PAIR))
        assertTrue(runCatching{f.coordinator.confirmDisplayBatch(b.id,save,true)}.isFailure)
        assertTrue(f.db.imageProvenanceDao().getAll().isEmpty())
        save.forEach{assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,f.db.imageTaskDao().get(it.taskId)!!.status);assertTrue(File(it.imagePath).isFile)}
        assertEquals(items.first().imageUrls,f.db.itemDao().getItemById(items.first().id)!!.imageUrls)
    }}
    @Test fun changedInputAndInsertionFailureCannotStartPartialBatch()=runBlocking{fixture{f->
        f.batchItems(2);val b=batch();val rows=inputs(f)
        File(rows.last().prepared.product.imagePath).appendBytes(byteArrayOf(9))
        assertTrue(runCatching{f.coordinator.submitDisplayBatch(b,UUID.randomUUID().toString(),rows,ImageGenerationProvider.SEEDREAM)}.isFailure)
        assertEquals(0,f.serviceStarts);assertTrue(f.db.imageTaskDao().getAll().isEmpty())
        val valid=inputs(f)
        val item=f.db.itemDao().getItemById(valid.last().itemId)!!
        f.db.itemDao().updateItem(item.copy(composition=ItemComposition.PAIR))
        assertTrue(runCatching{f.coordinator.submitDisplayBatch(b,UUID.randomUUID().toString(),valid,ImageGenerationProvider.SEEDREAM)}.isFailure)
        assertEquals(0,f.serviceStarts);f.db.itemDao().updateItem(item)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_display_insert BEFORE INSERT ON image_tasks WHEN NEW.batch_position=1 BEGIN SELECT RAISE(ABORT,'controlled rollback'); END")
        assertTrue(runCatching{f.coordinator.submitDisplayBatch(b,UUID.randomUUID().toString(),valid,ImageGenerationProvider.SEEDREAM)}.isFailure)
        assertEquals(0,f.serviceStarts);assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    }}
    @Test fun zipRoundTripPreservesSavedAndPendingDisplayImagesAndFrozenInputs()=runBlocking{fixture{f->
        f.categoryItems();val b=batch();val save=ready(f,b)
        f.coordinator.confirmDisplayBatch(b.id,save.take(2),true)
        val before=f.db.imageTaskDao().getAll().associateBy{it.id}
        val hashes=before.mapValues{(_,t)->t.sourceImagePaths.map(::imageSha256) to imageSha256(t.resultImagePath!!)}
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try {
            manager.importBackup(zip).getOrThrow()
            f.db.imageTaskDao().getAll().forEach{t->t.validate();val old=before.getValue(t.id)
                assertEquals(old.status,t.status);assertEquals(old.batchId,t.batchId);assertEquals(old.batchPosition,t.batchPosition)
                assertEquals(hashes.getValue(t.id),t.sourceImagePaths.map(::imageSha256) to imageSha256(t.resultImagePath!!))
                ImageTaskRequest.decode(t.requestJson).validateInputImages(t.sourceImagePaths)
            }
            assertEquals(2,f.db.imageProvenanceDao().getAll().size)
            f.runner.runQueue();assertEquals(before.size,f.displayCalls);assertEquals(0,f.layerCalls)
        } finally { f.context.contentResolver.delete(zip,null,null) }
    }}
}
