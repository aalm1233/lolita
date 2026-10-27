package com.lolita.app.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.material.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MaterialBatchViewModelTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture,ViewModelStore)->Unit){val f=MaterialTestFixture();val store=ViewModelStore();try{f.init();withTimeout(45000){block(f,store)}}finally{withContext(Dispatchers.Main){store.clear()};f.close()}}
    private suspend fun MaterialBatchViewModel.await(predicate:(MaterialBatchState)->Boolean)=withTimeout(10000){uiState.first(predicate)}
    @Test fun missingDisplayFilterUsesAlbumProvenanceAfterTaskDeletion()=runBlocking{fixture{f,store->
        val items=f.batchItems(2);val item=items.first();val b=MaterialBatchIdentity(UUID.randomUUID().toString(),"已保存展示图")
        val prepared=f.coordinator.prepareDisplayInput(item.id,item.imageUrls.first())
        val id=f.coordinator.submitDisplayBatch(b,UUID.randomUUID().toString(),listOf(BatchDisplayInput(item.id,prepared,item.imageUrls.first(),"",0)),ImageGenerationProvider.SEEDREAM).single()
        f.runner.runQueue();val path=f.db.imageTaskDao().get(id)!!.resultImagePath!!
        f.coordinator.confirmDisplayBatch(b.id,listOf(BatchMaterialConfirmation(id,path,null)),true)
        f.coordinator.delete(id);assertTrue(java.io.File(path).isFile)
        val vm=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle()).also{store.put("vm",it)}}
        vm.await{!it.loading && it.rows.size==2 && it.rows.first{row->row.itemId==item.id}.hasDisplay}
        withContext(Dispatchers.Main){vm.toggle(item.id.toString(),true);vm.onlyMissing(true)}
        assertEquals(listOf(items.last().id),vm.uiState.value.visibleRows.map{it.itemId});assertEquals(1,vm.uiState.value.selectedRows.size)
        f.db.itemDao().updateImages(item.id,item.imageUrls,System.currentTimeMillis())
        vm.await{it.visibleRows.size==2}
    }}
    @Test fun hiddenSelectionsAndItemsBeyondFirstThirtyUseAllChosenInputsWithoutPaidCalls()=runBlocking{fixture{f,store->
        val items=f.batchItems(35)
        val vm=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle()).also{store.put("vm",it)}}
        vm.await{!it.loading && it.keyLoaded && it.rows.size==35}
        assertTrue(vm.uiState.value.selected.isEmpty())
        withContext(Dispatchers.Main){vm.toggle(items.first().id.toString(),true);vm.toggle(items.last().id.toString(),true);vm.query(items.last().name);vm.prepareFee()}
        val state=vm.await{it.fee!=null && !it.busy};assertEquals(2,state.selectedRows.size);assertEquals(1,state.visibleRows.size)
        assertEquals(items.filterIndexed{i,_->i==0 || i==34}.map{it.id}.toSet(),state.fee!!.rows.map{it.itemId}.toSet())
        assertEquals(listOf(0,1),state.fee!!.rows.map{it.position});assertEquals(0,f.serviceStarts);assertEquals(0,f.frontCalls)
        withContext(Dispatchers.Main){vm.submitFee(state.fee!!)}
        vm.await{it.phase==MaterialBatchPhase.FRONT && it.jobs.size==2 && !it.busy}
        assertEquals(0,f.frontCalls);assertEquals(2,f.serviceStarts)
    }}
    @Test fun serviceAndSourceChangesRevokeWholeBatchFee()=runBlocking{fixture{f,store->
        f.batchItems(2);val changes=MutableStateFlow(0);var provider=ImageGenerationProvider.SEEDREAM
        val vm=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle(),changes){ImageServiceStatus(provider,true)}.also{store.put("vm",it)}}
        vm.await{!it.loading && it.keyLoaded}
        withContext(Dispatchers.Main){vm.uiState.value.rows.forEach{vm.toggle(it.key,true)};vm.prepareFee()}
        val fee=vm.await{it.fee!=null && !it.busy}.fee!!
        provider=ImageGenerationProvider.OPENAI;changes.value++
        vm.await{it.provider==provider && it.keyLoaded && it.fee==null}
        withContext(Dispatchers.Main){vm.submitFee(fee)}
        assertNull(vm.uiState.value.fee);assertEquals(0,f.serviceStarts)
        withContext(Dispatchers.Main){vm.prepareFee()}
        val refreshed=vm.await{it.fee!=null && !it.busy}.fee!!
        assertEquals(ImageGenerationProvider.OPENAI,refreshed.provider);assertEquals(0,f.serviceStarts)
    }}
    @Test fun pendingReviewSelectionRequiresAllChecksAndLayerChangeClearsThem()=runBlocking{fixture{f,store->
        val batch=MaterialBatchIdentity(UUID.randomUUID().toString(),"集中核对")
        f.layerOutput={com.lolita.app.data.remote.GeneratedImageLayers((1..2).map{com.lolita.app.data.remote.GeneratedImageLayer(f.transparent,it,"候选$it","完整",listOf(0,0,1000,1000))},3)}
        val front=f.coordinator.submitMaterialBatch(batch,UUID.randomUUID().toString(),f.batchInputs(f.batchItems(2)),ImageGenerationProvider.SEEDREAM);f.runner.runQueue()
        f.coordinator.submitLayerBatch(batch,UUID.randomUUID().toString(),f.batchLayers(front));f.runner.runQueue()
        val vm=withContext(Dispatchers.Main){f.batchViewModel(SavedStateHandle(mapOf("batchId" to batch.id))).also{store.put("vm",it)}}
        vm.await{!it.loading && it.phase==MaterialBatchPhase.LAYERS && it.rows.size==2}
        withContext(Dispatchers.Main){vm.selectReady();vm.check(0,true);vm.check(1,true)}
        assertNull(vm.saveSnapshot())
        withContext(Dispatchers.Main){vm.check(2,true)};assertEquals(2,vm.saveSnapshot()!!.size)
        val row=vm.uiState.value.rows.first()
        withContext(Dispatchers.Main){vm.layer(row.key,row.layers[1].imagePath)}
        assertTrue(vm.uiState.value.checks.isEmpty());assertNull(vm.saveSnapshot())
        withContext(Dispatchers.Main){(0..2).forEach{vm.check(it,true)};vm.save(vm.saveSnapshot()!!)}
        vm.await{it.phase==MaterialBatchPhase.SAVED && !it.busy && it.rows.size==2}
        assertEquals(2,f.db.itemTryOnMaterialDao().getAll().size);assertEquals(0,f.fusionCalls)
    }}
}
