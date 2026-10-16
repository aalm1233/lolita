package com.lolita.app.data.generation

import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.mannequin.MannequinGenerator
import com.lolita.app.data.tryon.TryOnGenerator
import com.lolita.app.data.personalmodel.PersonalModelGenerator
import com.lolita.app.domain.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Service-owned execution with a process-wide serial drain and no automatic retries. */
class ImageTaskRunner(
    private val coordinator: ImageTaskCoordinator,
    private val execute: suspend (ImageTask) -> Unit,
    private val timeoutMillis: Long = 12 * 60 * 1000L
) {
    private val drain = Mutex()
    suspend fun runQueue(
        session: ImageTaskCoordinator.ServiceSession = coordinator.openServiceSession(),
        onChanged: suspend (ImageTask?) -> Unit = {}
    ) = drain.withLock {
        coordinator.awaitStartup()
        while (coordinator.runNext({ task -> withTimeout(timeoutMillis) { execute(task) } }, onChanged, session)) { }
        onChanged(null)
    }
}

/** Normal dependency boundaries keep real validation and provider routing in controlled tests. */
class ImageTaskExecutor(
    private val coordinator: ImageTaskCoordinator,
    private val db: LolitaDatabase,
    private val readCredentials: (ImageGenerationProvider) -> ImageGenerationCredentials,
    private val prepareTryOn: suspend (List<String>) -> List<ByteArray>,
    private val prepareMannequin: suspend (List<String>) -> List<ByteArray>,
    private val generateImage: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray,
    private val preparePersonalModel: suspend (List<String>) -> List<ByteArray> = { error("专属模特图片准备尚未接入") },
    private val preparePersonalTryOn: (suspend (String, List<String>) -> List<ByteArray>)? = null,
    private val generatePersonalModel: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray = generateImage,
    private val validateSourceImage: suspend (String) -> Boolean = { java.io.File(it).let { f -> f.isFile && f.canRead() && f.length() > 0 } }
) {
    suspend fun execute(task: ImageTask) {
        val credentials = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readCredentials(task.provider) }
        val request = ImageTaskRequest.decode(task.requestJson)
        when (task.kind) {
            ImageTaskKind.PERSONAL_MODEL -> PersonalModelGenerator(
                readCredentials = { credentials }, validateSourceImage = validateSourceImage,
                prepareImages = preparePersonalModel, generateImage = generatePersonalModel,
                savePreview = { coordinator.publishPersonalModel(task.id, it) },
                discardPreview = coordinator::discardUnownedImage
            ).generate(requireNotNull(request.personalModel), task.sourceImagePaths,
                expectedProvider = task.provider, beforeRequest = { coordinator.beforeRequest(task.id) })
            ImageTaskKind.TRY_ON -> TryOnGenerator(
                findItem = db.itemDao()::getItemById, readCredentials = { credentials },
                prepareImages = prepareTryOn, generateImage = generateImage, preparePersonalImages = preparePersonalTryOn,
                saveResult = { bytes, rows, model -> coordinator.publishTryOn(task.id, bytes, rows, model) }
            ).generate(request.selections(task.sourceImagePaths), expectedProvider = task.provider,
                beforeRequest = { coordinator.beforeRequest(task.id) },
                personalModelImagePath = request.modelImagePath(task.sourceImagePaths))
            ImageTaskKind.MANNEQUIN -> MannequinGenerator(
                findItem = db.itemDao()::getItemById, findCategory = db.categoryDao()::getCategoryById,
                readCredentials = { credentials }, prepareImages = prepareMannequin, generateImage = generateImage,
                savePreview = { coordinator.publishMannequin(task.id, it) }, discardPreview = coordinator::discardUnownedImage
            ).generate(MannequinRequest(request.garments.single().itemId, task.sourceImagePaths.single(), request.instructions, request.mannequinSubject),
                expectedProvider = task.provider, beforeRequest = { coordinator.beforeRequest(task.id) })
        }
    }
}
