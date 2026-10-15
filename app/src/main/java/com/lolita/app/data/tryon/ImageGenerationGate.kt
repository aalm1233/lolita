package com.lolita.app.data.tryon

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** One non-queuing gate for all explicit paid image generation in this process. */
class ImageGenerationGate {
    private val mutex = Mutex()
    private val generating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = generating.asStateFlow()

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        check(mutex.tryLock()) { "已有图片生成任务正在进行，请等待完成或取消后重试" }
        generating.value = true
        try {
            currentCoroutineContext().ensureActive()
            return block()
        } finally {
            generating.value = false
            mutex.unlock()
        }
    }

    companion object { val process = ImageGenerationGate() }
}
