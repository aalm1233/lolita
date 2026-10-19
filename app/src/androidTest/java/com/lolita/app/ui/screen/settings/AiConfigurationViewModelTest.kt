package com.lolita.app.ui.screen.settings

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.repository.*
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.RecognitionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiConfigurationViewModelTest {
    private class ControlledStore : AiConfigurationStore {
        override val changes = MutableStateFlow(Unit)
        var calls = 0
        var fail = false
        var cancel = false
        var configured = false
        override fun read() = AiConfigurationSnapshot(ImageGenerationProvider.SEEDREAM, RecognitionProvider.VOLCENGINE_ARK,
            AiKeySlot.entries.associateWith { if (configured && it == AiKeySlot.DOUBAO) AiKeyStatus.CONFIGURED else AiKeyStatus.EMPTY })
        override fun save(slot: AiKeySlot, value: String) {
            calls++
            if (cancel) throw CancellationException()
            if (fail) error("controlled-sensitive-error")
            configured = true
        }
        override fun clear(slot: AiKeySlot) { configured = false }
        override fun select(slot: AiKeySlot) { }
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun invalidKeysNeverWriteAndUnexpectedErrorsNeverLeakTheirContents() = runBlocking {
        val backend = ControlledStore()
        val store = ViewModelStore()
        lateinit var vm: AiConfigurationViewModel
        main { vm = AiConfigurationViewModel(backend); store.put("vm", vm) }
        try {
            withTimeout(5000) { vm.uiState.first { !it.isLoading } }
            main {
                vm.edit(AiKeySlot.DOUBAO)
                vm.saveKey(AiKeySlot.DOUBAO, "has internal space")
                vm.saveKey(AiKeySlot.DOUBAO, "a".repeat(513))
            }
            assertEquals(0, backend.calls)
            backend.fail = true
            main { vm.saveKey(AiKeySlot.DOUBAO, "controlled-private-key") }
            val failed = withTimeout(5000) { vm.uiState.first { !it.busy && it.error == "配置修改未完成，请重试" } }
            assertFalse(failed.toString().contains("controlled-"))
            assertEquals(AiKeySlot.DOUBAO, failed.editing)
            backend.fail = false
            main { vm.saveKey(AiKeySlot.DOUBAO, "controlled-private-key"); vm.saveKey(AiKeySlot.DOUBAO, "duplicate-key") }
            withTimeout(5000) { vm.uiState.first { !it.busy && it.editing == null } }
            assertEquals(2, backend.calls)
            assertEquals(AiKeyStatus.CONFIGURED, vm.uiState.value.configuration.keys[AiKeySlot.DOUBAO])
        } finally { main { store.clear() } }
    }

    @Test fun cancellationDoesNotBecomeAnErrorOrReportASuccessfulSave() = runBlocking {
        val backend = ControlledStore().apply { cancel = true }
        val store = ViewModelStore()
        lateinit var vm: AiConfigurationViewModel
        main { vm = AiConfigurationViewModel(backend); store.put("vm", vm) }
        try {
            withTimeout(5000) { vm.uiState.first { !it.isLoading } }
            main { vm.edit(AiKeySlot.DOUBAO); vm.saveKey(AiKeySlot.DOUBAO, "controlled-cancelled") }
            withTimeout(5000) { vm.uiState.first { !it.busy } }
            assertNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.message)
            assertEquals(AiKeyStatus.EMPTY, vm.uiState.value.configuration.keys[AiKeySlot.DOUBAO])
        } finally { main { store.clear() } }
    }
}
