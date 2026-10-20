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
    private val validateSourceImage: suspend (String) -> Boolean = { java.io.File(it).let { f -> f.isFile && f.canRead() && f.length() > 0 } },
    private val prepareAccessory: (suspend (List<String>) -> List<ByteArray>)? = null,
    private val generateMannequin: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray = generateImage,
    private val prepareSnapshotImages: (suspend (List<String>) -> List<ByteArray>)? = null,
    private val generateMaterial: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray = generateMannequin,
    private val decomposeMaterial: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> com.lolita.app.data.remote.GeneratedImageLayers = { _, _, _ -> error("图层拆分尚未接入") }
) {
    suspend fun execute(task: ImageTask) {
        require(!task.sourceResourcesMissing) { ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE }
        val credentials = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readCredentials(task.provider) }
        val request = ImageTaskRequest.decode(task.requestJson)
        if (request.version == 4) require(request.serviceModel == task.provider.model) { "原服务型号已不可用，请核对后为新任务重新选择；原请求未改变" }
        if (request.version == 5) {
            request.validate(task.kind)
            require(request.serviceModel == task.provider.model) { "原服务型号已改变" }
            com.lolita.app.data.tryon.ImageGenerationGate.process.runExclusive {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { request.validateInputImages(task.sourceImagePaths) }
                val images = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { task.sourceImagePaths.map { java.io.File(it).readBytes() } }
                require(images.all { it.size in 1..4 * 1024 * 1024 } && images.sumOf { it.size.toLong() } <= 40L * 1024 * 1024)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { request.validateInputImages(task.sourceImagePaths) }
                coordinator.beforeRequest(task.id)
                if (task.kind == ImageTaskKind.SINGLE_ITEM_MATERIAL) {
                    when (requireNotNull(request.materialSpec).stage) {
                        MaterialStage.EXTRACT_LAYER -> coordinator.publishMaterialLayers(task.id, decomposeMaterial(credentials, images, requireNotNull(request.promptSnapshot)))
                        MaterialStage.FRONT_VIEW -> coordinator.publishMannequin(task.id, generateMaterial(credentials, images, requireNotNull(request.promptSnapshot)))
                        MaterialStage.TRANSPARENT -> coordinator.publishTransparentMaterial(task.id, generateMaterial(credentials, images, requireNotNull(request.promptSnapshot)))
                    }
                } else {
                    val bytes = generateImage(credentials, images, requireNotNull(request.promptSnapshot))
                    coordinator.publishTryOn(task.id, bytes, request.selections(task.sourceImagePaths), task.provider.model)
                }
            }
            return
        }
        when (task.kind) {
            ImageTaskKind.SINGLE_ITEM_MATERIAL -> error("素材任务必须使用新协议")
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
                personalModelImagePath = request.modelImagePath(task.sourceImagePaths),
                immutableRequest = request.takeIf { it.version == 4 }, immutablePaths = task.sourceImagePaths,
                prepareSnapshotImages = prepareSnapshotImages)
            ImageTaskKind.MANNEQUIN -> MannequinGenerator(
                findItem = db.itemDao()::getItemById, findCategory = db.categoryDao()::getCategoryById,
                readCredentials = { credentials }, prepareImages = prepareMannequin, generateImage = generateMannequin,
                savePreview = { coordinator.publishMannequin(task.id, it) }, discardPreview = coordinator::discardUnownedImage,
                prepareAccessoryImages = prepareAccessory
            ).generate(MannequinRequest(request.garments.single().itemId, task.sourceImagePaths.last(), request.instructions, request.mannequinSubject, request.wigHeadTemplate),
                expectedProvider = task.provider, beforeRequest = { coordinator.beforeRequest(task.id) },
                immutableRequest = request.takeIf { it.version == 4 }, immutablePaths = task.sourceImagePaths,
                prepareSnapshotImages = prepareSnapshotImages)
        }
    }
}
