package com.lolita.app.ui.screen.personalmodel

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.entity.PersonalModel
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalModelViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    @After fun cleanup() { instrumentation.runOnMainSync { store.clear() } }
    @Test fun deletedDefaultFallsBackToBuiltinAndRenameTrimsBeforeSaving() = runBlocking {
        val request = PersonalModelRequest("我的模特", PersonalModelMode.PARAMETERS, BodyMeasurements(163.0, 52.0))
        val rows = MutableStateFlow(listOf(PersonalModel(id = 1, name = request.name, requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = "/accepted", provider = ImageGenerationProvider.SEEDREAM, model = "model")))
        val default = MutableStateFlow<Long?>(1)
        var renamed: String? = null
        val vm = withContext(Dispatchers.Main) {
            PersonalModelViewModel(rows, default, setDefault = { default.value = it }, renameModel = { _, name -> renamed = name },
                deleteModel = { id -> rows.value = rows.value.filterNot { it.id == id } }).also { store.put("library", it) }
        }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        withContext(Dispatchers.Main) { vm.rename(1, " 新名称 ") }
        withTimeout(10_000) { while (renamed == null) yield() }
        assertEquals("新名称", renamed)
        withContext(Dispatchers.Main) { vm.delete(1) }
        withTimeout(10_000) { vm.uiState.first { it.models.isEmpty() && !it.mutating } }
        assertNull(vm.uiState.value.defaultId)
        assertNull(default.value)
        default.value = 999
        withTimeout(10_000) { vm.uiState.first { it.defaultId == null } }
        assertNull(vm.uiState.value.defaultId)
    }
}
