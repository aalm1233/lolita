package com.lolita.app.ui.screen.personalmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.PersonalModelRepository
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersonalModelEditViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: LolitaDatabase
    private lateinit var repository: PersonalModelRepository
    private val store = ViewModelStore()
    private var submissions = 0
    private var sent: PersonalModelRequest? = null
    private var sentPaths = emptyList<String>()
    private var submit: suspend () -> String = { "task-created" }
    private var retry: suspend (String) -> ImageTask? = { null }
    private var readable: suspend (String) -> Boolean = { true }
    private var draftWriter: (suspend (PersonalModelDraft) -> Unit)? = null
    private var deleteCopied: suspend (String) -> Unit = {}
    private var draftReader: (suspend (String) -> PersonalModelDraft?)? = null
    private var copy: suspend (Context, Uri) -> String = { _, _ -> error("No copy expected") }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LolitaDatabase::class.java).build()
        repository = PersonalModelRepository(db, deleteImage = {})
    }
    @After fun close() { instrumentation.runOnMainSync { store.clear() }; instrumentation.waitForIdleSync(); db.close() }
    private suspend fun model(handle: SavedStateHandle = SavedStateHandle(), wait: Boolean = true): PersonalModelEditViewModel {
        val vm = withContext(Dispatchers.Main) {
            PersonalModelEditViewModel(handle, readDraft = { draftReader?.invoke(it) ?: repository.getDraft(it) }, saveDraft = { value -> draftWriter?.invoke(value) ?: repository.saveDraft(value) },
                deleteDraft = repository::deleteDraft, readModel = repository::get, readRetryTask = { retry(it) },
                readService = { ImageServiceStatus(ImageGenerationProvider.SEEDREAM, true) }, serviceChanges = flowOf(0),
                selectService = {}, saveApiKey = { _, _ -> }, clearApiKey = {},
                submitTask = { request, paths, _ -> submissions++; sent = request; sentPaths = paths; submit() },
                imageReadable = { readable(it) }, copyPhoto = { context, uri -> copy(context, uri) }, deleteImage = { deleteCopied(it) }).also { store.put(UUID.randomUUID().toString(), it) }
        }
        if (wait) withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded } }
        return vm
    }
    private suspend fun main(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun settled(vm: PersonalModelEditViewModel) { withTimeout(10_000) { vm.uiState.first { !it.isSavingDraft } } }
    private suspend fun valid(vm: PersonalModelEditViewModel) {
        main { vm.update { it.copy(name = " 我的模特 ", heightCm = "163", weightKg = "52", mode = PersonalModelMode.PARAMETERS) } }
        settled(vm)
    }

    @Test fun invalidRawInputDoesNotSubmitAndSurvivesDraftRestoration() = runBlocking {
        val handle = SavedStateHandle()
        val vm = model(handle)
        main { vm.update { it.copy(name = "我的模特", heightCm = "1.", weightKg = "-", mode = PersonalModelMode.PARAMETERS) }; vm.generate() }
        settled(vm)
        assertEquals(0, submissions)
        val recreated = model(handle)
        assertEquals("1.", recreated.uiState.value.draft.heightCm)
        assertEquals("-", recreated.uiState.value.draft.weightKg)
        assertNotNull(recreated.uiState.value.validationIssue)
    }

    @Test fun editingBeforeDraftReadCompletesCannotOverwriteStoredPhotos() = runBlocking {
        val id = UUID.randomUUID().toString()
        val original = PersonalModelDraft(id, PersonalModelDraftData(name = "原草稿", heightCm = "163", weightKg = "52",
            photoRoles = listOf(PersonalModelPhotoRole.FACE)).encode(), listOf("/protected-face"))
        repository.saveDraft(original)
        val gate = CompletableDeferred<PersonalModelDraft?>()
        draftReader = { gate.await() }
        val vm = model(SavedStateHandle(mapOf("personalModelDraftId" to id)), wait = false)
        main { vm.update { it.copy(name = "空白状态不应写入") }; vm.removePhoto(PersonalModelPhotoRole.FACE); vm.generate() }
        assertEquals(original, repository.getDraft(id))
        gate.complete(original)
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        assertEquals("原草稿", vm.uiState.value.draft.name)
        assertEquals(listOf("/protected-face"), vm.uiState.value.paths)
        assertEquals(0, submissions)
    }

    @Test fun failedRawDraftSaveBlocksSubmissionAndCanBeRetriedWithoutLosingFields() = runBlocking {
        val vm = model()
        draftWriter = { throw java.io.IOException("disk full") }
        main { vm.update { it.copy(name = "未保存的参数", mode = PersonalModelMode.PARAMETERS, heightCm = "163", weightKg = "52") } }
        settled(vm)
        assertTrue(vm.uiState.value.draftSaveFailed)
        assertFalse(vm.uiState.value.canGenerate)
        main { vm.generate() }
        assertEquals(0, submissions)
        draftWriter = null
        main { vm.retrySaveDraft() }
        settled(vm)
        assertFalse(vm.uiState.value.draftSaveFailed)
        assertTrue(vm.uiState.value.canGenerate)
        assertEquals("未保存的参数", vm.uiState.value.draft.name)
    }

    @Test fun switchingToParametersKeepsDraftPhotosButExcludesThemFromPaidRequest() = runBlocking {
        val id = UUID.randomUUID().toString()
        repository.saveDraft(PersonalModelDraft(id, PersonalModelDraftData(name = "本人", heightCm = "163", weightKg = "52",
            photoRoles = listOf(PersonalModelPhotoRole.FACE, PersonalModelPhotoRole.FRONT)).encode(), listOf("/face", "/front")))
        val vm = model(SavedStateHandle(mapOf("personalModelDraftId" to id)))
        main { vm.update { it.copy(mode = PersonalModelMode.PARAMETERS) } }
        settled(vm)
        assertEquals(listOf("/face", "/front"), vm.uiState.value.paths)
        main { vm.generate() }
        withTimeout(10_000) { vm.uiState.first { it.taskId != null } }
        assertTrue(sent!!.photoRoles.isEmpty())
        assertTrue(sentPaths.isEmpty())
    }

    @Test fun mixedHiddenRolesSubmitOnlyTheReferenceForTheSelectedMode() = runBlocking {
        val id = UUID.randomUUID().toString()
        repository.saveDraft(PersonalModelDraft(id, PersonalModelDraftData(name = "混合草稿", mode = PersonalModelMode.PARAMETERS,
            heightCm = "163", weightKg = "52", photoRoles = listOf(PersonalModelPhotoRole.FACE, PersonalModelPhotoRole.VIRTUAL_FACE,
                PersonalModelPhotoRole.FRONT)).encode(), listOf("/personal-face", "/virtual-approved", "/personal-front")))
        val vm = model(SavedStateHandle(mapOf("personalModelDraftId" to id)))
        assertEquals(listOf(PersonalModelPhotoRole.VIRTUAL_FACE to "/virtual-approved"), vm.uiState.value.effectivePhotos())
        main { vm.update { it.copy(mode = PersonalModelMode.PHOTO) } }
        settled(vm)
        assertEquals(listOf(PersonalModelPhotoRole.FACE to "/personal-face", PersonalModelPhotoRole.FRONT to "/personal-front"), vm.uiState.value.effectivePhotos())
        main { vm.update { it.copy(mode = PersonalModelMode.PARAMETERS) } }
        settled(vm)
        main { vm.generate() }
        withTimeout(10_000) { vm.uiState.first { it.taskId != null } }
        assertEquals(listOf(PersonalModelPhotoRole.VIRTUAL_FACE), sent!!.photoRoles)
        assertEquals(listOf("/virtual-approved"), sentPaths)
    }

    @Test fun submissionWaitsForRetryPrefillAndIsAcceptedOnlyOnce() = runBlocking {
        val gate = CompletableDeferred<ImageTask?>()
        retry = { gate.await() }
        val vm = model(SavedStateHandle(mapOf("retryTaskId" to "old-task")), wait = false)
        main { vm.update { it.copy(name = "不得写入") }; vm.generate() }
        assertEquals(0, submissions)
        assertTrue(vm.uiState.value.isLoading)
        val request = PersonalModelRequest("重试模特", PersonalModelMode.PARAMETERS, BodyMeasurements(163.0, 52.0))
        gate.complete(ImageTask("old-task", ImageTaskKind.PERSONAL_MODEL, ImageGenerationProvider.SEEDREAM, "重试模特",
            ImageTaskRequest(garments = emptyList(), version = 2, personalModel = request).encode(), emptyList(), status = ImageTaskStatus.FAILED))
        withTimeout(10_000) { vm.uiState.first { !it.isLoading && it.keyLoaded } }
        assertEquals("重试模特", vm.uiState.value.draft.name)
        val pending = CompletableDeferred<String>()
        submit = { pending.await() }
        main { vm.generate(); vm.generate() }
        withTimeout(10_000) { while (submissions < 1) yield() }
        assertEquals(1, submissions)
        pending.complete("accepted")
        withTimeout(10_000) { vm.uiState.first { it.taskId != null } }
        main { vm.generate() }
        assertEquals(1, submissions)
    }

    @Test fun retryWithMissingPhotoLoadsParametersAndRequiresReselection() = runBlocking {
        val request = PersonalModelRequest("待重试", PersonalModelMode.PHOTO, BodyMeasurements(163.0, 52.0), listOf(PersonalModelPhotoRole.FACE))
        retry = { ImageTask("missing-photo", ImageTaskKind.PERSONAL_MODEL, ImageGenerationProvider.SEEDREAM, "待重试",
            ImageTaskRequest(emptyList(), version = 2, personalModel = request).encode(), listOf("/deleted"), status = ImageTaskStatus.FAILED) }
        readable = { false }
        val vm = model(SavedStateHandle(mapOf("retryTaskId" to "missing-photo")))
        assertEquals("163", vm.uiState.value.draft.heightCm)
        assertEquals("待重试", vm.uiState.value.draft.name)
        assertFalse(vm.uiState.value.loadFailed)
        assertTrue(vm.uiState.value.paths.isEmpty())
        assertFalse(vm.uiState.value.canGenerate)
        main { vm.generate() }
        assertEquals(0, submissions)
        assertNotNull(vm.uiState.value.validationIssue)
    }

    @Test fun cancelledPhotoCopyPreservesExistingDraftAndLeavesEditorUsable() = runBlocking {
        val id = UUID.randomUUID().toString()
        repository.saveDraft(PersonalModelDraft(id, PersonalModelDraftData(name = "本人", heightCm = "163", weightKg = "52",
            photoRoles = listOf(PersonalModelPhotoRole.FACE)).encode(), listOf("/existing-face")))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        copy = { _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val vm = model(SavedStateHandle(mapOf("personalModelDraftId" to id)))
        main { vm.addPhoto(instrumentation.targetContext, Uri.parse("content://test/photo"), PersonalModelPhotoRole.FACE) }
        started.await()
        main { vm.cancelPhotoCopy() }
        cancelled.await()
        withTimeout(10_000) { vm.uiState.first { !it.isCopying } }
        assertEquals(listOf("/existing-face"), repository.getDraft(id)!!.sourceImagePaths)
        assertEquals(listOf("/existing-face"), vm.uiState.value.paths)
        assertTrue(vm.uiState.value.canGenerate)
    }

    @Test fun failedPhotoAdoptionCleansOnlyNewOwnedCopyAndPreservesOriginalDraft() = runBlocking {
        val directory = File(instrumentation.targetContext.cacheDir, "photo-adoption-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val originalFile = File(directory, "original.png").apply { writeText("existing protected file") }
            val copiedFile = File(directory, "copy.png").apply { writeText("new owned copy") }
            val id = UUID.randomUUID().toString()
            val original = PersonalModelDraft(id, PersonalModelDraftData(name = "本人", heightCm = "163", weightKg = "52",
                photoRoles = listOf(PersonalModelPhotoRole.FACE)).encode(), listOf(originalFile.absolutePath))
            repository.saveDraft(original)
            val vm = model(SavedStateHandle(mapOf("personalModelDraftId" to id)))
            copy = { _, _ -> copiedFile.absolutePath }
            draftWriter = { throw java.io.IOException("disk full") }
            deleteCopied = { File(it).delete(); Unit }
            main { vm.addPhoto(instrumentation.targetContext, Uri.parse("content://test/new"), PersonalModelPhotoRole.FACE) }
            withTimeout(10_000) { vm.uiState.first { !it.isCopying && it.error != null } }
            assertFalse(copiedFile.exists())
            assertTrue(originalFile.exists())
            assertEquals(original, repository.getDraft(id))
            assertEquals(listOf(originalFile.absolutePath), vm.uiState.value.paths)
        } finally { directory.deleteRecursively() }
    }

    @Test fun adjustingParametersPreservesApprovedVirtualFaceAndNeverOverwritesOriginal() = runBlocking {
        val request = PersonalModelRequest("原模特", PersonalModelMode.PARAMETERS, BodyMeasurements(160.0, 50.0))
        val id = db.personalModelDao().insert(PersonalModel(name = "原模特", requestJson = request.encode(), sourceImagePaths = emptyList(),
            imageUrl = "/approved", provider = ImageGenerationProvider.SEEDREAM, model = "model"))
        val vm = model(SavedStateHandle(mapOf("modelId" to id)))
        main { vm.update { it.copy(weightKg = "55") } }
        settled(vm)
        main { vm.generate() }
        withTimeout(10_000) { vm.uiState.first { it.taskId != null } }
        assertEquals(listOf(PersonalModelPhotoRole.VIRTUAL_FACE), sent!!.photoRoles)
        assertEquals(listOf("/approved"), sentPaths)
        assertEquals("原模特", repository.get(id)!!.name)
        assertEquals(50.0, repository.get(id)!!.request().measurements.weightKg, 0.0)
    }
}
