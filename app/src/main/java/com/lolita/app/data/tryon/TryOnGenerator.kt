package com.lolita.app.data.tryon

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.remote.TryOnImageApi
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
    private val readKey: () -> String,
    private val prepareImages: suspend (List<String>) -> List<ByteArray>,
    private val generateImage: suspend (String, List<ByteArray>, String) -> ByteArray,
    private val saveResult: suspend (ByteArray, List<TryOnSelection>, String) -> Long,
    private val gate: ImageGenerationGate = ImageGenerationGate.process
) {
    val isGenerating: StateFlow<Boolean> = gate.isGenerating

    suspend fun generate(
        selected: List<TryOnSelection>,
        onPhase: (TryOnGenerationPhase) -> Unit = {}
    ): Long = gate.runExclusive {
        currentCoroutineContext().ensureActive()
        val request = TryOnRequest(selected.toList())
        onPhase(TryOnGenerationPhase.PREPARING)
        val key = withContext(Dispatchers.IO) { readKey() }
        require(key.isNotBlank() && key.length <= 512 && key.all { it.code in 33..126 }) {
            "请先在服务配置中填写有效的 API Key"
        }
        withContext(Dispatchers.IO) {
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
        val images = prepareImages(request.selections.map { it.imagePath })
        require(images.size == request.selections.size + 1 && images.all { it.isNotEmpty() }) {
            "参考图片未全部准备完成，请重新选择图片"
        }
        currentCoroutineContext().ensureActive()
        onPhase(TryOnGenerationPhase.GENERATING)
        currentCoroutineContext().ensureActive()
        val result = generateImage(key, images, request.prompt())
        currentCoroutineContext().ensureActive()
        onPhase(TryOnGenerationPhase.SAVING)
        currentCoroutineContext().ensureActive()
        saveResult(result, request.selections, TryOnImageApi.MODEL)
    }
}
