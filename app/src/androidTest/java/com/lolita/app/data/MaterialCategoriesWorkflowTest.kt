package com.lolita.app.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.lolita.app.data.file.BackupManager
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.material.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class MaterialCategoriesWorkflowTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture)->Unit){val f=MaterialTestFixture();try{f.init();withTimeout(60000){block(f)}}finally{f.close()}}
    @Test fun mixedFamiliesKeepOwnRolesAndInputsThroughQueueSaveAndZip()=runBlocking{fixture{f->
        val items=f.categoryItems();f.echoCategoryImages();val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"服饰与小物混合批次")
        val fronts=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM)
        assertEquals(0,f.frontCalls);f.runner.runQueue();assertEquals(10,f.frontCalls)
        for(id in fronts){val task=f.db.imageTaskDao().get(id)!!;val r=ImageTaskRequest.decode(task.requestJson);assertEquals(6,r.version);assertEquals(r.garments.single().semantic!!.defaultWearRole,r.garments.single().role);assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,task.status)}
        val layers=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(fronts));f.runner.runQueue()
        val save=layers.map{val t=f.db.imageTaskDao().get(it)!!;BatchMaterialConfirmation(it,t.resultImagePath!!,null)}
        f.coordinator.confirmMaterialBatch(batch.id,save,true);val materials=f.db.itemTryOnMaterialDao().getAll();assertEquals(10,materials.size);materials.forEach{it.validate()}
        assertEquals(items,f.db.itemDao().getAllItemsList().sortedBy{it.id});assertEquals(0,f.fusionCalls)
        val hashes=materials.associate{it.itemId to it.resultSha256};val manager=BackupManager(f.context,f.db,withImageTasksPaused={action->f.coordinator.withRestorePaused(action)})
        val zip=manager.exportToZip().getOrThrow();try{manager.importBackup(zip).getOrThrow();assertEquals(hashes,f.db.itemTryOnMaterialDao().getAll().associate{it.itemId to imageSha256(it.imagePath)});assertEquals(20,f.db.imageTaskDao().getAll().count{it.batchId==batch.id})}finally{f.context.contentResolver.delete(zip,null,null)}
    }}
    @Test fun compositionOrCategoryChangeRejectsWholeBatchBeforeNextPaidStageOrSave()=runBlocking{fixture{f->
        val items=f.categoryItems().take(2);f.echoCategoryImages();val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"变更保护")
        val fronts=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM);f.runner.runQueue();val prepared=f.batchLayers(fronts)
        f.db.itemDao().updateItem(items[1].copy(composition=ItemComposition.PAIR));val starts=f.serviceStarts
        assertTrue(runCatching{f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),prepared)}.isFailure);assertEquals(starts,f.serviceStarts);assertEquals(0,f.layerCalls)
        f.db.itemDao().updateItem(items[1]);val layers=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),prepared);f.runner.runQueue()
        val save=layers.map{val t=f.db.imageTaskDao().get(it)!!;BatchMaterialConfirmation(it,t.resultImagePath!!,null)}
        f.db.itemDao().updateItem(items[1].copy(composition=ItemComposition.PAIR))
        assertTrue(runCatching{f.coordinator.confirmMaterialBatch(batch.id,save,true)}.isFailure);assertTrue(f.db.itemTryOnMaterialDao().getAll().isEmpty())
        f.db.itemDao().updateItem(items[1].copy(categoryId=items[0].categoryId))
        assertTrue(runCatching{f.coordinator.confirmMaterialBatch(batch.id,save,true)}.isFailure);assertTrue(f.db.itemTryOnMaterialDao().getAll().isEmpty())
    }}
    @Test fun familyAndTypeSearchKeepHiddenSelectionAndReopenLayerQueueAtItsActualStage()=runBlocking{fixture{f->
        val items=f.categoryItems();val store=ViewModelStore()
        try{
            val vm=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle()).also{store.put("draft",it)}}
            withTimeout(10000){vm.uiState.first{!it.loading && it.keyLoaded && it.rows.size==10}}
            withContext(Dispatchers.Main){vm.uiState.value.rows.forEach{vm.toggle(it.key,true)};vm.family(WardrobeFamily.WIG);vm.query("发片")}
            assertEquals(1,vm.uiState.value.visibleRows.size);assertEquals(10,vm.uiState.value.selectedRows.size)
            withContext(Dispatchers.Main){vm.prepareFee()};val fee=withTimeout(10000){vm.uiState.first{it.fee!=null && !it.busy}}.fee!!
            assertEquals(10,fee.rows.size);assertEquals(0,f.frontCalls)
            withContext(Dispatchers.Main){vm.submitFee(fee)};withTimeout(10000){vm.uiState.first{it.jobs.size==10 && !it.busy}}
            f.runner.runQueue();val jobs=f.db.imageTaskDao().getAll();val batch=vm.uiState.value.identity
            f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(jobs.map{it.id}))
            val reopened=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle(mapOf("batchId" to batch.id))).also{store.put("reopened",it)}}
            withTimeout(10000){reopened.uiState.first{!it.loading && it.rows.size==10}}
            assertEquals(MaterialBatchPhase.LAYERS,reopened.uiState.value.phase);assertEquals(0,f.layerCalls)
        }finally{withContext(Dispatchers.Main){store.clear()}}
    }}
    @Test fun savedClothingAndFootwearMaterialsReuseExistingFullTryOnAndRejectChangedComposition()=runBlocking{fixture{f->
        val all=f.categoryItems();val items=listOf(all[0],all[5]);f.echoCategoryImages();val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"已确认素材复用")
        val front=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(items),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        val layers=f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(front));f.runner.runQueue()
        f.coordinator.confirmMaterialBatch(batch.id,layers.map{val t=f.db.imageTaskDao().get(it)!!;BatchMaterialConfirmation(it,t.resultImagePath!!,null)},true)
        val assets=f.db.itemTryOnMaterialDao().getAll().associateBy{it.itemId}
        val selected=items.map{item->val asset=assets.getValue(item.id);val semantic=f.db.categoryDao().getCategoryById(item.categoryId)!!.categorySemantic;TryOnSelection(item.id,item.name,asset.imagePath,semantic.defaultWearRole,semantic,item.composition,asset.id)}
        val base=f.base();val profile=PersonalModelRequest("已确认人物",PersonalModelMode.PARAMETERS,BodyMeasurements(165.0,55.0))
        val person=com.lolita.app.data.local.entity.PersonalModel(name=profile.name,requestJson=profile.encode(),sourceImagePaths=emptyList(),imageUrl=base.imageUrl,provider=ImageGenerationProvider.SEEDREAM,model="controlled")
        val personId=f.db.personalModelDao().insert(person)
        val id=f.coordinator.submitTryOn(selected,ImageGenerationProvider.SEEDREAM,personId,base.imageUrl,"");f.runner.runQueue()
        assertEquals(ImageTaskStatus.COMPLETED,f.db.imageTaskDao().get(id)!!.status);assertEquals(1,f.fusionCalls)
        f.db.itemDao().updateItem(items[1].copy(composition=ItemComposition.PAIR));val starts=f.serviceStarts
        assertTrue(runCatching{f.coordinator.submitTryOn(selected,ImageGenerationProvider.SEEDREAM,personId,base.imageUrl,"")}.isFailure);assertEquals(starts,f.serviceStarts)
    }}
}
