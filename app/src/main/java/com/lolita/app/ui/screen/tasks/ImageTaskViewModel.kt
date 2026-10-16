package com.lolita.app.ui.screen.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.TryOnResult
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Filtering and presentation only; every accepted mutation is owned by the coordinator. */
enum class ImageTaskFilter(val label: String) { ALL("全部"), ACTIVE("进行中"), PENDING("待处理"), FINISHED("已结束") }

data class ImageTaskUiState(
    val tasks: List<ImageTask> = emptyList(),
    val items: List<Item> = emptyList(),
    val results: List<TryOnResult> = emptyList(),
    val filter: ImageTaskFilter = ImageTaskFilter.ALL,
    val isLoading: Boolean = true,
    val mutating: Set<String> = emptySet(),
    val error: String? = null
) {
    val visibleTasks get() = tasks.filter {
        when (filter) {
            ImageTaskFilter.ALL -> true
            ImageTaskFilter.ACTIVE -> it.status.retainsSourceImages
            ImageTaskFilter.PENDING -> it.status == ImageTaskStatus.AWAITING_CONFIRMATION
            ImageTaskFilter.FINISHED -> it.status.isTerminal
        }
    }.sortedWith(compareBy<ImageTask> {
        when { it.status.retainsSourceImages -> 0; it.status == ImageTaskStatus.AWAITING_CONFIRMATION -> 1; else -> 2 }
    }.thenByDescending { it.createdAt })

    fun resultPath(task: ImageTask): String? = when {
        task.status == ImageTaskStatus.AWAITING_CONFIRMATION -> task.resultImagePath
        task.status != ImageTaskStatus.COMPLETED -> null
        task.kind == ImageTaskKind.TRY_ON -> results.firstOrNull { it.id == task.tryOnResultId }?.imageUrl
        else -> task.resultImagePath?.takeIf { path -> items.any { item -> item.id == task.garmentId && path in item.imageUrls } }
    }
    fun canAppend(task: ImageTask): Boolean = task.status == ImageTaskStatus.AWAITING_CONFIRMATION && items.any { it.id == task.garmentId }
}

val ImageTask.garmentId: Long? get() = runCatching { ImageTaskRequest.decode(requestJson).garments.singleOrNull()?.itemId }.getOrNull()
val ImageTask.canRetry: Boolean get() = status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED)
val ImageTaskStatus.label: String get() = when (this) {
    ImageTaskStatus.QUEUED -> "排队中"
    ImageTaskStatus.PREPARING -> "准备图片"
    ImageTaskStatus.GENERATING -> "生成中"
    ImageTaskStatus.SAVING -> "保存结果"
    ImageTaskStatus.AWAITING_CONFIRMATION -> "待确认"
    ImageTaskStatus.COMPLETED -> "已完成"
    ImageTaskStatus.FAILED -> "失败"
    ImageTaskStatus.INTERRUPTED -> "已中断"
    ImageTaskStatus.CANCELLED -> "已取消"
    ImageTaskStatus.DISCARDED -> "已舍弃"
}

class ImageTaskViewModel @JvmOverloads constructor(
    tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks,
    items: Flow<List<Item>> = AppModule.itemRepository().getAllItems(),
    results: Flow<List<TryOnResult>> = AppModule.tryOnRepository().results,
    private val cancelTask: suspend (String) -> Unit = { AppModule.imageTaskCoordinator().cancel(it) },
    private val confirmTask: suspend (String) -> Unit = { AppModule.imageTaskCoordinator().confirmMannequin(it) },
    private val discardTask: suspend (String) -> Unit = { AppModule.imageTaskCoordinator().discard(it) },
    private val deleteTask: suspend (String) -> Unit = { AppModule.imageTaskCoordinator().delete(it) }
) : ViewModel() {
    private val state = MutableStateFlow(ImageTaskUiState())
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            combine(tasks, items, results) { jobs, wardrobe, history -> Triple(jobs, wardrobe, history) }
                .catch { e -> if (e is CancellationException) throw e; state.update { it.copy(isLoading = false, error = "任务读取失败，请返回后重试") } }
                .collect { (jobs, wardrobe, history) -> state.update { it.copy(tasks = jobs, items = wardrobe, results = history, isLoading = false) } }
        }
    }
    fun filter(value: ImageTaskFilter) { state.update { it.copy(filter = value) } }
    fun clearError() { state.update { it.copy(error = null) } }
    fun cancel(id: String) = mutate(id, cancelTask)
    fun confirm(id: String) = mutate(id, confirmTask)
    fun discard(id: String) = mutate(id, discardTask)
    fun delete(id: String) = mutate(id, deleteTask)
    private fun mutate(id: String, operation: suspend (String) -> Unit) {
        if (id in state.value.mutating) return
        state.update { it.copy(mutating = it.mutating + id, error = null) }
        viewModelScope.launch {
            try { operation(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(error = "操作未完成，请核对任务、原服饰和图片后重试") } }
            finally { state.update { it.copy(mutating = it.mutating - id) } }
        }
    }
}
