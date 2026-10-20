package com.lolita.app.data

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.repository.TryOnRepository
import com.lolita.app.data.remote.*
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MaterialWorkflowTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture)->Unit) {
        val f=MaterialTestFixture();try { f.init();withTimeout(45000){block(f)} }finally{f.close()}
    }
    @Test fun frontAndLayersAreSeparatelySubmittedAndOnlyExplicitConfirmationCreatesMaterial()=runBlocking{fixture{f->
        val original=f.db.itemDao().getItemById(f.itemId)!!;val originalHash=imageSha256(f.source)
        val front=f.front();val first=f.db.imageTaskDao().get(front)!!
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,first.status);assertEquals(1,f.frontCalls);assertEquals(0,f.layerCalls)
        assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        assertArrayEquals(File(first.sourceImagePaths.single()).readBytes(),f.receivedImages.single())
        assertTrue(runCatching{f.coordinator.confirmMaterial(front,first.resultImagePath!!,null,true)}.isFailure)
        val layer=f.layers(front);val task=f.db.imageTaskDao().get(layer)!!
        assertEquals(ImageTaskStatus.COMPLETED,f.db.imageTaskDao().get(front)!!.status)
        assertEquals(2,task.generatedImageCount);assertEquals(1,f.layerCalls);assertEquals(0,f.fusionCalls)
        assertTrue(runCatching{f.coordinator.confirmMaterial(layer,task.resultImagePath!!,null,false)}.isFailure)
        assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        val id=f.coordinator.confirmMaterial(layer,task.resultImagePath!!,null,true)
        assertEquals(id,f.coordinator.confirmMaterial(layer,task.resultImagePath!!,null,true))
        assertEquals(original,f.db.itemDao().getItemById(f.itemId));assertEquals(originalHash,imageSha256(f.source))
        assertEquals(1,f.layerCalls);assertEquals(1,f.frontCalls)
    }}
    @Test fun newCandidateKeepsCurrentMaterialAndStaleConfirmationCannotOverwriteIt()=runBlocking{fixture{f->
        val current=f.confirmed();val next=f.layers(f.front());val pending=f.db.imageTaskDao().get(next)!!
        assertEquals(current,f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        assertTrue(runCatching{f.coordinator.confirmMaterial(next,pending.resultImagePath!!,null,true)}.isFailure)
        assertEquals(current,f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        val replacement=f.coordinator.confirmMaterial(next,pending.resultImagePath!!,current.id,true)
        assertNotEquals(current.id,replacement)
        assertTrue(File(f.db.itemTryOnMaterialDao().getForItem(f.itemId)!!.imagePath).isFile)
    }}
    @Test fun multipleLayersRequireExplicitSelectionAndDiscardedLayersAreReleased()=runBlocking{fixture{f->
        f.layerOutput={GeneratedImageLayers(listOf(GeneratedImageLayer(f.transparent,1,"候选一","含附属",listOf(0,0,1000,1000)),GeneratedImageLayer(f.transparent,2,"候选二","含附属",listOf(0,0,1000,1000))),3)}
        val id=f.layers(f.front());val task=f.db.imageTaskDao().get(id)!!;val layers=MaterialLayers.decode(task.layersJson)
        assertEquals(2,layers.size);val chosen=layers[1]
        f.coordinator.confirmMaterial(id,chosen.imagePath,null,true)
        assertEquals(chosen.imagePath,f.db.itemTryOnMaterialDao().getForItem(f.itemId)!!.imagePath)
        assertFalse(File(layers[0].imagePath).exists());assertTrue(File(chosen.imagePath).exists())
    }}
    @Test fun opaqueLayerFailsWithoutAutomaticRetryAndOriginalImagesStay()=runBlocking{fixture{f->
        f.layerOutput={GeneratedImageLayers(listOf(GeneratedImageLayer(f.opaque,1,"错误返回","非透明",listOf(0,0,1000,1000))),2)}
        val id=f.layers(f.front());assertEquals(ImageTaskStatus.FAILED,f.db.imageTaskDao().get(id)!!.status)
        f.runner.runQueue();assertEquals(1,f.layerCalls);assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId));assertTrue(File(f.source).exists())
    }}
    @Test fun acceptedAccessoryUsesFrozenInputsEvenAfterItemAndBaseDeletionAndRetainsComparisonCopy()=runBlocking{fixture{f->
        val material=f.confirmed();val base=f.base()
        val before=f.coordinator.prepareMaterialInput(base.imageUrl,false)
        val reference=f.coordinator.prepareMaterialInput(material.imagePath,false)
        val bitmap=BitmapFactory.decodeFile(reference.imagePath);assertEquals(255,bitmap.getPixel(0,0) ushr 24);bitmap.recycle()
        assertTrue(com.lolita.app.data.tryon.MaterialImageValidation.hasTransparentForeground(material.imagePath))
        val id=f.coordinator.submitAddAccessory(f.itemId,material.id,AccessoryBaseSnapshot(AccessoryBaseType.TRY_ON_RESULT,base.id,base.name,true),before,reference,"贴合腰部",ImageGenerationProvider.SEEDREAM)
        f.db.itemDao().deleteItem(f.db.itemDao().getItemById(f.itemId)!!)
        f.db.tryOnDao().delete(base.id)
        f.coordinator.delete(material.sourceTaskId)
        f.runner.runQueue()
        val task=f.db.imageTaskDao().get(id)!!;assertEquals(ImageTaskStatus.COMPLETED,task.status);assertEquals(1,f.fusionCalls)
        assertArrayEquals(File(before.imagePath).readBytes(),f.receivedImages[0]);assertArrayEquals(File(reference.imagePath).readBytes(),f.receivedImages[1])
        val result=f.db.tryOnDao().get(task.tryOnResultId!!)!!
        assertNotNull(result.baseSnapshotImagePath);assertNotEquals(before.imagePath,result.baseSnapshotImagePath)
        assertEquals(before.sha256,imageSha256(result.baseSnapshotImagePath!!))
        f.coordinator.delete(id);assertTrue(File(result.imageUrl).exists());assertTrue(File(result.baseSnapshotImagePath!!).exists())
        TryOnRepository(f.context,f.db).delete(result.id)
        assertFalse(File(result.baseSnapshotImagePath!!).exists());assertFalse(File(result.imageUrl).exists())
    }}
    @Test fun removedItemCannotReceivePreviewOrCreateReplacementItem()=runBlocking{fixture{f->
        val id=f.layers(f.front());val task=f.db.imageTaskDao().get(id)!!
        f.db.itemDao().deleteItem(f.db.itemDao().getItemById(f.itemId)!!)
        assertTrue(runCatching{f.coordinator.confirmMaterial(id,task.resultImagePath!!,null,true)}.isFailure)
        assertTrue(f.db.itemTryOnMaterialDao().getAll().isEmpty());assertTrue(f.db.itemDao().getAllItemsList().isEmpty())
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,f.db.imageTaskDao().get(id)!!.status)
        f.coordinator.discard(id);assertFalse(File(task.resultImagePath!!).exists())
    }}
    @Test fun categoryChangedDuringReviewCannotSaveOldMaterialOrReuseItsSemantic()=runBlocking{fixture{f->
        val id=f.layers(f.front());val task=f.db.imageTaskDao().get(id)!!
        val category=f.db.categoryDao().getCategoryById(f.categoryId)!!
        f.db.categoryDao().updateCategory(category.copy(categorySemantic=CategorySemantic.UNCONFIRMED))
        assertTrue(runCatching{f.coordinator.confirmMaterial(id,task.resultImagePath!!,null,true)}.isFailure)
        assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId));assertEquals(0,f.fusionCalls)
    }}
    @Test fun sourceHashChangesAfterFeePreparationCannotBeSubmitted()=runBlocking{fixture{f->
        val prepared=f.coordinator.prepareMaterialInput(f.source,false)
        File(prepared.imagePath).appendBytes(byteArrayOf(1))
        assertTrue(runCatching{f.coordinator.submitMaterial(f.itemId,prepared,f.source,"",ImageGenerationProvider.SEEDREAM,null)}.isFailure)
        assertTrue(f.db.imageTaskDao().getAll().isEmpty());assertEquals(0,f.frontCalls)
    }}
    @Test fun accessoryHistoryAndIndependentComparisonSurviveZipAfterTaskDeletion()=runBlocking{fixture{f->
        val asset=f.confirmed();val base=f.base()
        val id=f.coordinator.submitAddAccessory(f.itemId,asset.id,AccessoryBaseSnapshot(AccessoryBaseType.TRY_ON_RESULT,base.id,base.name,true),
            f.coordinator.prepareMaterialInput(base.imageUrl,false),f.coordinator.prepareMaterialInput(asset.imagePath,false),"保留原服装",ImageGenerationProvider.SEEDREAM)
        f.runner.runQueue();val resultId=f.db.imageTaskDao().get(id)!!.tryOnResultId!!
        f.coordinator.delete(id)
        val before=f.db.tryOnDao().get(resultId)!!;val imageHash=imageSha256(before.imageUrl);val baseHash=imageSha256(before.baseSnapshotImagePath!!)
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try {
            manager.importBackup(zip).getOrThrow();val after=f.db.tryOnDao().get(resultId)!!
            assertEquals(imageHash,imageSha256(after.imageUrl));assertEquals(baseHash,imageSha256(after.baseSnapshotImagePath!!))
            assertNotEquals(before.baseSnapshotImagePath,after.baseSnapshotImagePath);assertEquals(before.requestSnapshotJson,after.requestSnapshotJson)
            assertNull(f.db.imageTaskDao().get(id));assertEquals(1,f.fusionCalls)
        } finally {f.context.contentResolver.delete(zip,null,null)}
    }}
    @Test fun personalModelAccessoryKeepsConsistentHistoricalIdentityThroughBackup()=runBlocking{fixture{f->
        val asset=f.confirmed();val base=f.base()
        val request=PersonalModelRequest("原个人模特",PersonalModelMode.PARAMETERS,BodyMeasurements(165.0,55.0))
        val person=com.lolita.app.data.local.entity.PersonalModel(name=request.name,requestJson=request.encode(),sourceImagePaths=emptyList(),imageUrl=base.imageUrl,provider=ImageGenerationProvider.SEEDREAM,model="controlled")
        val personId=f.db.personalModelDao().insert(person)
        val id=f.coordinator.submitAddAccessory(f.itemId,asset.id,AccessoryBaseSnapshot(AccessoryBaseType.PERSONAL_MODEL,personId,person.name,true),
            f.coordinator.prepareMaterialInput(person.imageUrl,false),f.coordinator.prepareMaterialInput(asset.imagePath,false),"",ImageGenerationProvider.SEEDREAM)
        f.runner.runQueue()
        val resultId=f.db.imageTaskDao().get(id)!!.tryOnResultId!!
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow()
        try {manager.importBackup(zip).getOrThrow();val restored=f.db.tryOnDao().get(resultId)!!;assertEquals(personId,restored.personalModelId);assertEquals(person.name,restored.personalModelName)}
        finally{f.context.contentResolver.delete(zip,null,null)}
    }}
    @Test fun failedLayerCanBeExplicitlyRetriedFromItsFrozenFrontWithoutGeneratingAnotherFront()=runBlocking{fixture{f->
        f.layerOutput={throw ImageGenerationFailure(ImageGenerationFailureKind.MODEL)}
        val id=f.layers(f.front());val failed=f.db.imageTaskDao().get(id)!!
        assertEquals(ImageTaskStatus.FAILED,failed.status)
        val prepared=f.coordinator.prepareMaterialInput(failed.sourceImagePaths.single(),false)
        f.layerOutput={GeneratedImageLayers(listOf(GeneratedImageLayer(f.transparent,1,"腰饰","完整单品",listOf(0,0,1000,1000))),2)}
        val retry=f.coordinator.submitMaterialLayers(id,prepared)
        assertEquals(1,f.layerCalls);f.runner.runQueue()
        assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,f.db.imageTaskDao().get(retry)!!.status)
        assertEquals(ImageTaskStatus.FAILED,f.db.imageTaskDao().get(id)!!.status)
        assertEquals(1,f.frontCalls);assertEquals(2,f.layerCalls)
    }}
    @Test fun pendingLayersAndConfirmedMaterialSurviveZipWithExactBytesAndNoResubmission()=runBlocking{fixture{f->
        val pendingId=f.layers(f.front());val task=f.db.imageTaskDao().get(pendingId)!!
        val hash=imageSha256(task.resultImagePath!!)
        val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val exports=mutableListOf<Uri>()
        try {
            val first=manager.exportToZip().getOrThrow().also(exports::add)
            manager.importBackup(first).getOrThrow()
            val restored=f.db.imageTaskDao().get(pendingId)!!
            assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,restored.status)
            assertEquals(hash,imageSha256(restored.resultImagePath!!))
            f.coordinator.confirmMaterial(pendingId,restored.resultImagePath!!,null,true)
            f.coordinator.delete(pendingId)
            val before=f.db.itemTryOnMaterialDao().getForItem(f.itemId)!!
            val second=manager.exportToZip().getOrThrow().also(exports::add)
            val preview=manager.previewBackup(second).getOrThrow()
            assertEquals(1,preview.itemTryOnMaterialCount)
            manager.importBackup(second).getOrThrow()
            val after=f.db.itemTryOnMaterialDao().getForItem(f.itemId)!!
            assertEquals(before.id,after.id);assertEquals(hash,imageSha256(after.imagePath))
            assertFalse(after.imagePath==before.imagePath)
            assertEquals(1,f.frontCalls);assertEquals(1,f.layerCalls)
        } finally { exports.forEach { f.context.contentResolver.delete(it,null,null) } }
    }}
}
