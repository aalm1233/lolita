package com.lolita.app.data.tryon

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.TryOnRequest
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class TryOnGenerationPhase { PREPARING, GENERATING, SAVING }

/** Coordinates one explicit paid request. All entry points share the process-wide gate. */
class TryOnGenerator(
    private val findItem: suspend (Long) -> Item?,
    private val readCredentials: () -> ImageGenerationCredentials,
    private val prepareImages: suspend (List<String>) -> List<ByteArray>,
    private val generateImage: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray,
    private val saveResult: suspend (ByteArray, List<TryOnSelection>, String) -> Long,
    private val gate: ImageGenerationGate = ImageGenerationGate.process,
    private val preparePersonalImages: (suspend (String, List<String>) -> List<ByteArray>)? = null
) {
    val isGenerating: StateFlow<Boolean> = gate.isGenerating

    suspend fun generate(
        selected: List<TryOnSelection>,
        onPhase: (TryOnGenerationPhase) -> Unit = {},
        expectedProvider: com.lolita.app.domain.model.ImageGenerationProvider? = null,
        beforeRequest: suspend () -> Unit = {},
        personalModelImagePath: String? = null,
        immutableRequest: com.lolita.app.domain.model.ImageTaskRequest? = null,
        immutablePaths: List<String>? = null,
        prepareSnapshotImages: (suspend (List<String>) -> List<ByteArray>)? = null
    ): Long = gate.runExclusive {
        currentCoroutineContext().ensureActive()
        val request = TryOnRequest(selected.toList(), protocolVersion = immutableRequest?.version ?: if (selected.any { it.semantic != null || it.role.ordinal >= 8 }) 4 else 1)
        onPhase(TryOnGenerationPhase.PREPARING)
        val credentials = withContext(Dispatchers.IO) { readCredentials() }
        require(expectedProvider == null || credentials.provider == expectedProvider) { "图像服务已更换，请核对服务配置后重新生成" }
        val key = credentials.apiKey
        require(key.isNotBlank() && key.length <= 512 && key.all { it.code in 33..126 }) {
            "请先在服务配置中填写有效的 API Key"
        }
        if (immutableRequest == null) withContext(Dispatchers.IO) {
            for (row in request.selections) {
                currentCoroutineContext().ensureActive()
                val item = findItem(row.itemId)
                require(item != null && item.id == row.itemId && row.imagePath in item.imageUrls) {
                    "所选服饰或参考图片已变更，请重新选择后生成"
                }
                require(item.status == ItemStatus.OWNED) {
                    "所选服饰已不在拥有的衣橱中，请重新选择后生成"
                }
                val source = File(row.imagePath)
                require(source.isFile && source.canRead() && source.length() > 0) {
                    "参考图片已丢失或无法读取，请重新选择图片"
                }
            }
        }
        if (personalModelImagePath != null) require(File(personalModelImagePath).let { it.isFile && it.canRead() && it.length() > 0 }) {
            "模特图片已丢失，请重新选择"
        }
        val garmentPaths = request.selections.map { it.imagePath }
        val images = if (immutableRequest != null) {
            val paths = requireNotNull(immutablePaths)
            withContext(Dispatchers.IO) { immutableRequest.validateInputImages(paths) }
            requireNotNull(prepareSnapshotImages) { "任务快照图片准备尚未接入" }(paths).also {
                withContext(Dispatchers.IO) { immutableRequest.validateInputImages(paths) }
            }
        } else if (personalModelImagePath == null) prepareImages(garmentPaths)
            else requireNotNull(preparePersonalImages) { "专属模特图片准备尚未接入" }(personalModelImagePath, garmentPaths)
        require(images.size == request.selections.size + 1 && images.all { it.isNotEmpty() }) {
            "参考图片未全部准备完成，请重新选择图片"
        }
        currentCoroutineContext().ensureActive()
        onPhase(TryOnGenerationPhase.GENERATING)
        currentCoroutineContext().ensureActive()
        beforeRequest()
        currentCoroutineContext().ensureActive()
        val result = generateImage(credentials, images, immutableRequest?.promptSnapshot ?: request.prompt(personalModel = personalModelImagePath != null))
        currentCoroutineContext().ensureActive()
        onPhase(TryOnGenerationPhase.SAVING)
        currentCoroutineContext().ensureActive()
        saveResult(result, request.selections, credentials.provider.model)
    }
}
