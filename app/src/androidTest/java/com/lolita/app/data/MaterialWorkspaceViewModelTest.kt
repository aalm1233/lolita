package com.lolita.app.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.ui.screen.material.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialWorkspaceViewModelTest {
    private suspend fun fixture(block:suspend(MaterialTestFixture,ViewModelStore)->Unit) {
        val f=MaterialTestFixture();val store=ViewModelStore()
        try { f.init();withTimeout(45000){block(f,store)} }
        finally { withContext(Dispatchers.Main){store.clear()};f.close() }
    }
    private suspend fun MaterialWorkspaceViewModel.await(predicate:(MaterialWorkspaceState)->Boolean)=withTimeout(10000){uiState.first(predicate)}
    @Test fun feeOnlyPreparesLocallyAndChangingCredentialsInvalidatesItWithoutSending()=runBlocking{fixture{f,store->
        val changes=MutableStateFlow(0);var configured=true
        val vm=withContext(Dispatchers.Main){f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId)),changes){ImageServiceStatus(ImageGenerationProvider.SEEDREAM,configured)}.also{store.put("vm",it)}}
        vm.await{it.eligible && it.keyLoaded}
        withContext(Dispatchers.Main){vm.prepareFee(MaterialFeeAction.PREPARE)}
        val fee=vm.await{it.fee!=null && !it.busy}.fee!!
        assertTrue(f.db.imageTaskDao().getAll().isEmpty());assertEquals(0,f.frontCalls)
        configured=false;changes.value++
        vm.await{it.fee==null && it.keyLoaded && !it.hasKey}
        withContext(Dispatchers.Main){vm.submitFee(fee)}
        assertTrue(f.db.imageTaskDao().getAll().isEmpty())
    }}
    @Test fun allThreeChecksAreRequiredAndChoosingAnotherLayerResetsThem()=runBlocking{fixture{f,store->
        f.layerOutput={com.lolita.app.data.remote.GeneratedImageLayers((1..2).map{com.lolita.app.data.remote.GeneratedImageLayer(f.transparent,it,"候选$it","单品",listOf(0,0,1000,1000))},3)}
        val id=f.layers(f.front())
        val vm=withContext(Dispatchers.Main){f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId,"taskId" to id))).also{store.put("vm",it)}}
        vm.await{it.pending}
        withContext(Dispatchers.Main){vm.check(0,true);vm.check(1,true);vm.saveMaterial(null)}
        assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        withContext(Dispatchers.Main){vm.check(2,true);vm.selectLayer(vm.uiState.value.layers[1].imagePath)}
        assertTrue(vm.uiState.value.checks.isEmpty())
        withContext(Dispatchers.Main){(0..2).forEach{vm.check(it,true)};vm.saveMaterial(null)}
        vm.await{it.saved && !it.busy};assertNotNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId))
    }}
    @Test fun accessoryNeedsExplicitClothingConfirmationAndChangingBaseRevokesTheFee()=runBlocking{fixture{f,store->
        f.confirmed();val base=f.base();val second=f.base()
        val vm=withContext(Dispatchers.Main){f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId,"baseResultId" to base.id))).also{store.put("vm",it)}}
        vm.await{it.eligible && it.material!=null && it.selectedBase!=null && it.keyLoaded}
        withContext(Dispatchers.Main){vm.prepareFee(MaterialFeeAction.ADD_ACCESSORY)}
        assertNull(vm.uiState.value.fee)
        withContext(Dispatchers.Main){vm.clothingConfirmed(true);vm.prepareFee(MaterialFeeAction.ADD_ACCESSORY)}
        val fee=vm.await{it.fee!=null && !it.busy}.fee!!
        withContext(Dispatchers.Main){vm.selectBase(vm.uiState.value.bases.first{it.snapshot.id==second.id});vm.submitFee(fee)}
        assertNull(vm.uiState.value.fee);assertFalse(vm.uiState.value.clothingConfirmed);assertEquals(0,f.fusionCalls)
    }}
    @Test fun retryLoadsCurrentBaseButRequiresFreshClothingAndFeeConfirmation()=runBlocking{fixture{f,store->
        val asset=f.confirmed();val base=f.base()
        val id=f.coordinator.submitAddAccessory(f.itemId,asset.id,com.lolita.app.domain.model.AccessoryBaseSnapshot(com.lolita.app.domain.model.AccessoryBaseType.TRY_ON_RESULT,base.id,base.name,true),f.coordinator.prepareMaterialInput(base.imageUrl,false),f.coordinator.prepareMaterialInput(asset.imagePath,false),"保留搭配",ImageGenerationProvider.SEEDREAM)
        f.coordinator.cancel(id)
        val vm=withContext(Dispatchers.Main){f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId,"taskId" to id))).also{store.put("vm",it)}}
        val state=vm.await{it.selectedBase!=null && it.instructions=="保留搭配"}
        assertEquals(base.id,state.selectedBase!!.snapshot.id);assertFalse(state.clothingConfirmed);assertNull(state.fee);assertEquals(0,f.fusionCalls)
    }}
    @Test fun decompositionCannotBeConfirmedForAnotherRecipientAfterServiceSwitch()=runBlocking{fixture{f,store->
        val id=f.front();val changes=MutableStateFlow(0);var provider=ImageGenerationProvider.SEEDREAM
        val vm=withContext(Dispatchers.Main){f.viewModel(SavedStateHandle(mapOf("itemId" to f.itemId,"taskId" to id)),changes){ImageServiceStatus(provider,true)}.also{store.put("vm",it)}}
        vm.await{it.pending && it.keyLoaded}
        withContext(Dispatchers.Main){vm.frontConfirmed(true);vm.prepareFee(MaterialFeeAction.DECOMPOSE)}
        val fee=vm.await{it.fee!=null && !it.busy}.fee!!
        provider=ImageGenerationProvider.OPENAI;changes.value++
        vm.await{it.provider==provider && it.keyLoaded && it.fee==null}
        withContext(Dispatchers.Main){vm.submitFee(fee);vm.prepareFee(MaterialFeeAction.DECOMPOSE)}
        assertNull(vm.uiState.value.fee);assertEquals(1,f.frontCalls);assertEquals(0,f.layerCalls)
    }}
}
