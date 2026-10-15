package com.lolita.app.data.mannequin

import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.data.local.entity.Category
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.tryon.ImageGenerationGate
import com.lolita.app.data.tryon.TryOnGenerationPhase
import com.lolita.app.domain.model.MannequinRequest
import com.lolita.app.domain.model.supportsMannequin
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
    private val gate: ImageGenerationGate = ImageGenerationGate.process
) {
    val isGenerating: StateFlow<Boolean> = gate.isGenerating

    suspend fun generate(
        request: MannequinRequest,
        onPhase: (TryOnGenerationPhase) -> Unit = {},
        expectedProvider: com.lolita.app.domain.model.ImageGenerationProvider? = null
    ): String = gate.runExclusive {
        var preview: String? = null
        try {
            onPhase(TryOnGenerationPhase.PREPARING)
            val credentials = withContext(Dispatchers.IO) { readCredentials() }
            require(expectedProvider == null || credentials.provider == expectedProvider) { "图像服务已更换，请核对服务配置后重新生成" }
            val key = credentials.apiKey
            require(key.isNotBlank() && key.length <= 512 && key.all { it.code in 33..126 }) {
                "请先在服务配置中填写有效的 API Key"
            }
            validateSource(request)
            val images = prepareImages(listOf(request.imagePath))
            require(images.size == 2 && images.all { it.isNotEmpty() }) {
                "参考图片未全部准备完成，请重新选择图片"
            }
            // Preparation may take time; reject an edit/delete that happened while decoding.
            validateSource(request)
            currentCoroutineContext().ensureActive()
            onPhase(TryOnGenerationPhase.GENERATING)
            currentCoroutineContext().ensureActive()
            val bytes = generateImage(credentials, images, request.prompt())
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
            "裙子或参考图片已变更，请重新选择后生成"
        }
        val category = findCategory(item.categoryId)
        require(category?.id == item.categoryId && supportsMannequin(category)) {
            "此服饰类别暂不支持正面人台图，请核对裙子类别"
        }
        val source = File(request.imagePath)
        require(source.isFile && source.canRead() && source.length() > 0) {
            "参考图片已丢失或无法读取，请重新选择图片"
        }
    }
}
