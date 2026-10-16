package com.lolita.app.data.personalmodel

import com.lolita.app.data.tryon.ImageGenerationGate
import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.PersonalModelRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One explicit personal-model request; publication belongs to the durable task coordinator. */
class PersonalModelGenerator(
    private val readCredentials: () -> ImageGenerationCredentials,
    private val validateSourceImage: suspend (String) -> Boolean,
    private val prepareImages: suspend (List<String>) -> List<ByteArray>,
    private val generateImage: suspend (ImageGenerationCredentials, List<ByteArray>, String) -> ByteArray,
    private val savePreview: suspend (ByteArray) -> String,
    private val discardPreview: suspend (String) -> Unit,
    private val gate: ImageGenerationGate = ImageGenerationGate.process
) {
    suspend fun generate(
        request: PersonalModelRequest,
        paths: List<String>,
        expectedProvider: ImageGenerationProvider? = null,
        beforeRequest: suspend () -> Unit = {}
    ): String = gate.runExclusive {
        request.validatePaths(paths)
        val credentials = withContext(Dispatchers.IO) { readCredentials() }
        require(expectedProvider == null || credentials.provider == expectedProvider) { "图像服务已更换，请核对后重新生成" }
        require(credentials.apiKey.isNotBlank() && credentials.apiKey.length <= 512 && credentials.apiKey.all { it.code in 33..126 }) {
            "请先在服务配置中填写有效的 API Key"
        }
        suspend fun validatePhotos() {
            paths.forEach { path ->
                currentCoroutineContext().ensureActive()
                require(validateSourceImage(path)) { "参考照片已丢失或无法完整读取，请重新选择" }
            }
        }
        validatePhotos()
        val images = prepareImages(paths)
        require(images.size == paths.size + 1 && images.all { it.isNotEmpty() }) { "参考照片未全部准备完成" }
        validatePhotos()
        currentCoroutineContext().ensureActive()
        beforeRequest()
        currentCoroutineContext().ensureActive()
        val bytes = generateImage(credentials, images, request.prompt())
        currentCoroutineContext().ensureActive()
        var published: String? = null
        try {
            val path = withContext(NonCancellable) { savePreview(bytes).also { published = it } }
            currentCoroutineContext().ensureActive()
            path
        } catch (failure: Throwable) {
            published?.let { path -> withContext(NonCancellable) { discardPreview(path) } }
            throw failure
        }
    }
}
