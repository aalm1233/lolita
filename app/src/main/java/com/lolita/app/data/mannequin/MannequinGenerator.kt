package com.lolita.app.data.mannequin

import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.tryon.ImageGenerationGate
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.MannequinPresentation
import com.lolita.app.domain.model.mannequinSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Creates an uncommitted preview; only the user's later confirmation may attach it to an Item. */
class MannequinGenerator(
    private val findItem: suspend (Long) -> Item?,
    private val findCategory: suspend (Long) -> Category?,
    private val readCredentials: () -> ImageGenerationCredentials,
    private val prepareImages: suspend (List<String>) -> List<ByteArray>,
    private val generateImage: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray,
    private val savePreview: suspend (ByteArray) -> String,
    private val discardPreview: suspend (String) -> Unit,
    private val gate: ImageGenerationGate = ImageGenerationGate.process,
    private val prepareAccessoryImages: (suspend (List<String>) -> List<ByteArray>)? = null
) {
    val isGenerating: StateFlow<Boolean> = gate.isGenerating

    suspend fun generate(
        request: MannequinRequest,
        onPhase: (TryOnGenerationPhase) -> Unit = {},
        expectedProvider: com.lolita.app.domain.model.ImageGenerationProvider? = null,
        beforeRequest: suspend () -> Unit = {},
        immutableRequest: com.lolita.app.domain.model.ImageTaskRequest? = null,
        immutablePaths: List<String>? = null,
        prepareSnapshotImages: (suspend (List<String>) -> List<ByteArray>)? = null
    ): String = gate.runExclusive {
        var preview: String? = null
        try {
            require(immutableRequest == null || immutableRequest.version == 4) { "不可变展示任务协议无效" }
            require(request.presentation != MannequinPresentation.WIG_HEAD || immutableRequest != null) { "假发头模展示需先建立不可变任务" }
            onPhase(TryOnGenerationPhase.PREPARING)
            val credentials = withContext(Dispatchers.IO) { readCredentials() }
            require(expectedProvider == null || credentials.provider == expectedProvider) { "图像服务已更换，请核对服务配置后重新生成" }
            val key = credentials.apiKey
            require(key.isNotBlank() && key.length <= 512 && key.all { it.code in 33..126 }) {
                "请先在服务配置中填写有效的 API Key"
            }
            if (immutableRequest == null) validateSource(request)
            else withContext(Dispatchers.IO) { immutableRequest.validateInputImages(requireNotNull(immutablePaths)) }
            val images = if (immutableRequest != null) requireNotNull(prepareSnapshotImages) { "任务快照图片准备尚未接入" }(requireNotNull(immutablePaths))
                else {
                    val prepare = if (request.presentation == MannequinPresentation.ACCESSORY)
                        requireNotNull(prepareAccessoryImages) { "小物图片准备尚未接入" } else prepareImages
                    prepare(listOf(request.imagePath))
                }
            require(images.size == request.presentation.inputCount && images.all { it.isNotEmpty() }) {
                "参考图片未全部准备完成，请重新选择图片"
            }
            // Preparation may take time; reject an edit/delete that happened while decoding.
            if (immutableRequest == null) validateSource(request)
            else withContext(Dispatchers.IO) { immutableRequest.validateInputImages(requireNotNull(immutablePaths)) }
            currentCoroutineContext().ensureActive()
            onPhase(TryOnGenerationPhase.GENERATING)
            currentCoroutineContext().ensureActive()
            beforeRequest()
            currentCoroutineContext().ensureActive()
            val bytes = generateImage(credentials, images, immutableRequest?.promptSnapshot ?: request.prompt())
            currentCoroutineContext().ensureActive()
            onPhase(TryOnGenerationPhase.SAVING)
            currentCoroutineContext().ensureActive()
            // Record ownership inside the non-cancellable handoff. Prompt cancellation on
            // withContext's return must still leave us the path needed for cleanup.
            val saved = withContext(NonCancellable) {
                savePreview(bytes).also { preview = it }
            }
            currentCoroutineContext().ensureActive()
            saved
        } catch (failure: Throwable) {
            preview?.let { path ->
                withContext(NonCancellable) { runCatching { discardPreview(path) } }
            }
            throw failure
        }
    }

    private suspend fun validateSource(request: MannequinRequest) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val item = findItem(request.itemId)
        require(item != null && item.id == request.itemId && request.imagePath in item.imageUrls) {
            "物品或参考图片已变更，请重新选择后生成"
        }
        val category = findCategory(item.categoryId)
        val currentSubject = mannequinSubject(category)
        require(category?.id == item.categoryId && currentSubject != null &&
            (request.subject?.let { it == currentSubject } ?: (currentSubject.presentation == MannequinPresentation.DRESS_FORM))) {
            "物品分类已改变或暂不支持展示图，请重新核对分类"
        }
        val source = File(request.imagePath)
        require(source.isFile && source.canRead() && source.length() > 0) {
            "参考图片已丢失或无法读取，请重新选择图片"
        }
    }
}
