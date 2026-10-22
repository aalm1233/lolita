package com.lolita.app.ui.screen.item.recognition

import com.lolita.app.ui.screen.common.uiErrorText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.entity.ItemEvidence
import com.lolita.app.data.local.entity.RecognitionTask
import com.lolita.app.data.recognition.RecognitionCoordinator
import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.data.repository.RecognitionRepository
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.item.ItemEditViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ItemRecognitionUiState(val draftId: String?=null,val tasks: List<RecognitionTask> = emptyList(),
    val busy: Boolean=false,val error: String?=null,val hasKey: Boolean=false,val keyRevision: Int=0,
    val provider: RecognitionProvider? = null,
    val reviews: Map<String,RecognitionReview> = emptyMap(),val evidence: List<ItemEvidence> = emptyList())

class ItemRecognitionViewModel(private val repository: RecognitionRepository=AppModule.recognitionRepository(),
    private val coordinator: RecognitionCoordinator=AppModule.recognitionCoordinator(),
    private val settings: RecognitionSettings=AppModule.recognitionSettings()): ViewModel() {
    private fun settingsState(): ItemRecognitionUiState {
        val selected=runCatching { settings.readProvider() }.getOrNull()
        return ItemRecognitionUiState(hasKey=selected?.let { settings.hasKey(it) } ?: false,keyRevision=settings.changes.value,
            provider=selected,error=if(selected==null) "识图服务设置无法读取，请明确选择服务" else null)
    }
    private val _uiState=MutableStateFlow(settingsState())
    val uiState=_uiState.asStateFlow()
    private var observation: Job?=null
    private var evidenceObservation: Job?=null
    private var boundItemId: Long?=null
    private var boundEvidence=emptyList<ItemEvidence>()
    private var draftEvidence=emptyList<ItemEvidence>()
    private val reviewMutex=Mutex()
    init { viewModelScope.launch { settings.changes.collect { revision ->
        val refreshed=settingsState()
        _uiState.update { it.copy(hasKey=refreshed.hasKey,keyRevision=revision,provider=refreshed.provider,error=refreshed.error ?: it.error) }
    } } }
    fun bind(draftId: String?,itemId: Long?=null) {
        if (_uiState.value.draftId==draftId && boundItemId==itemId) return
        observation?.cancel();evidenceObservation?.cancel();boundItemId=itemId;boundEvidence=emptyList();draftEvidence=emptyList()
        _uiState.update { it.copy(draftId=draftId,tasks=emptyList(),reviews=emptyMap(),evidence=emptyList(),error=null) }
        if(draftId!=null) observation=viewModelScope.launch { repository.observe(draftId).collect { tasks ->
            if(_uiState.value.draftId==draftId) {
                val decoded=mutableMapOf<String,RecognitionReview>()
                var invalid=false
                tasks.forEach { task -> runCatching { task.validate();task.reviewJson?.let { RecognitionReview.decode(it,task.result()!!.candidates.map { row -> row.id }.toSet()) } }
                    .onSuccess { if(it!=null) decoded[task.id]=it }.onFailure { invalid=true } }
                val staged=repository.draftEvidence(draftId)
                if(_uiState.value.draftId!=draftId) return@collect
                draftEvidence=staged
                _uiState.update { current -> current.copy(tasks=tasks,evidence=(boundEvidence+draftEvidence).distinctBy { it.id },reviews=decoded+current.reviews.filterKeys { key -> tasks.any { it.id==key } },
                    error=if(invalid) "部分识图记录无法读取，可移除后重新框选" else current.error) }
            }
        } }
        if(itemId!=null) evidenceObservation=viewModelScope.launch { repository.observeEvidence(itemId).collect { evidence ->
            if(boundItemId==itemId) {boundEvidence=evidence;_uiState.update { it.copy(evidence=(boundEvidence+draftEvidence).distinctBy { row -> row.id }) }}
        } }
    }
    fun action(block: suspend ()->Unit) {
        if(_uiState.value.busy) return
        _uiState.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            try { block() } catch(failure: Exception) { report(failure) }
            finally { _uiState.update { it.copy(busy=false) } }
        }
    }
    private fun report(failure: Exception) {
        if(failure is CancellationException) throw failure
        _uiState.update { it.copy(error=uiErrorText(failure, "操作未完成，请重新核对")) }
    }
    suspend fun stageRegion(draftId: String,path: String,baseline: Map<RecognitionField,String>,family: WardrobeFamily,appearanceOnly: Boolean): String {
        try { return repository.addRegion(draftId,path,baseline,family,appearanceOnly) }
        catch(failure: Throwable) { com.lolita.app.data.file.ImageFileHelper.cleanupUnreferencedImages(listOf(path)); throw failure }
    }
    fun addRegion(draftId: String,path: String,baseline: Map<RecognitionField,String>,family: WardrobeFamily,appearanceOnly: Boolean) = action {
        stageRegion(draftId,path,baseline,family,appearanceOnly)
    }
    fun replaceRegion(id: String,index: Int,path: String)=action {
        try { repository.replaceRegion(id,index,path) }
        catch(failure: Throwable) { com.lolita.app.data.file.ImageFileHelper.cleanupUnreferencedImages(listOf(path)); throw failure }
    }
    /** Rebuild old crop files locally; the resulting PREPARED card still needs a separate paid send. */
    fun prepareAgain(task: RecognitionTask, normalize: suspend (String)->String, onPrepared: (String)->Unit)=action {
        val current=repository.get(task.id) ?: error("识图记录已移除")
        require(current==task && current.status in setOf(RecognitionStatus.FAILED,RecognitionStatus.INTERRUPTED)) {
            "识图图片或状态已变化，请重新核对"
        }
        var preparedId: String?=null
        val newImages=mutableListOf<String>()
        try {
            // Capture committed identities/files before cancellation can resume the caller.
            withContext(NonCancellable) { repository.reprepare(task.id).also { preparedId=it } }
            val id=requireNotNull(preparedId)
            task.regionImagePaths.forEachIndexed { index,path ->
                currentCoroutineContext().ensureActive()
                val normalized=withContext(NonCancellable) { normalize(path).also { newImages.add(it) } }
                require(normalized !in task.regionImagePaths) { "请为识图区域生成新的裁切副本" }
                currentCoroutineContext().ensureActive()
                repository.replaceRegion(id,index,normalized)
            }
            currentCoroutineContext().ensureActive()
            onPrepared(id)
        } catch(failure: Throwable) {
            withContext(NonCancellable) {
                try { preparedId?.let { repository.discard(it) } }
                finally { com.lolita.app.data.file.ImageFileHelper.cleanupUnreferencedImages(newImages).join() }
            }
            throw failure
        }
    }
    /** The visible image/service card is the confirmation. Recheck it at the explicit send click. */
    fun submitVisible(displayed: RecognitionTask,revision: Int,provider: RecognitionProvider,editor: ItemEditViewModel,
        family: WardrobeFamily,onPrepared:(String)->Unit)=action {
        fun checkSettings() {
            require(settings.changes.value==revision && settings.readProvider()==provider) { "服务或密钥已更改，请重新核对图片与费用" }
        }
        checkSettings()
        require(!editor.uiState.value.isSaving && editor.uiState.value.draftId==displayed.draftId) { "草稿已变化，请重新核对" }
        editor.keepDraft()
        val current=repository.get(displayed.id) ?: error("识图记录已移除")
        require(current==displayed) { "识图图片或状态已变化，请重新核对" }
        val id=if(displayed.status in setOf(RecognitionStatus.FAILED,RecognitionStatus.INTERRUPTED)) repository.reprepare(displayed.id) else displayed.id
        val confirmed=repository.confirmation(id,editor.currentRecognitionValues(),family,provider)
        require(confirmed.draftId==displayed.draftId && confirmed.targetItemId==displayed.targetItemId &&
            confirmed.regionImagePaths==displayed.regionImagePaths && confirmed.request().regions==displayed.request().regions) { "识图图片已变化，请重新核对" }
        checkSettings()
        onPrepared(id)
        coordinator.submit(confirmed,revision)
    }
    suspend fun isAppearanceOnly(path: String)=repository.isAppearanceOnly(path)
    fun markAppearanceOnly(id: String)=action { repository.markAppearanceOnly(id) }
    fun removeRegion(id: String,index: Int)=action { repository.removeRegion(id,index) }
    suspend fun confirmation(id: String,baseline: Map<RecognitionField,String>,family: WardrobeFamily)=repository.confirmation(id,baseline,family,settings.readProvider())
    fun submit(task: RecognitionTask,revision: Int)=action { coordinator.submit(task,revision) }
    fun cancel(id: String)=action { coordinator.cancel(id) }
    fun discard(id: String)=action { reviewMutex.withLock { repository.discard(id) } }
    fun reprepare(id: String)=action { repository.reprepare(id) }
    fun selectProvider(provider: RecognitionProvider)=action { withContext(Dispatchers.IO) { settings.selectProvider(provider) } }
    fun saveKey(key: String)=action { withContext(Dispatchers.IO) { settings.saveKey(key,requireNotNull(_uiState.value.provider) { "请先选择识图服务" }) } }
    fun clearKey()=action { withContext(Dispatchers.IO) { settings.clearKey(requireNotNull(_uiState.value.provider) { "请先选择识图服务" }) } }
    /** Every edit is accepted immediately; ordered writes coalesce to the latest visible review. */
    fun saveReview(id: String,review: RecognitionReview) {
        val identity=_uiState.value.draftId
        if(_uiState.value.busy || _uiState.value.tasks.none { it.id==id && it.draftId==identity }) return
        _uiState.update { it.copy(reviews=it.reviews+(id to review)) }
        viewModelScope.launch {
            try { reviewMutex.withLock {
                if(_uiState.value.draftId==identity) repository.saveReview(id,requireNotNull(_uiState.value.reviews[id]).encode())
            } } catch(failure: Exception) { report(failure) }
        }
    }
    fun apply(id: String,review: RecognitionReview,editor: ItemEditViewModel,onApplied:(RecognitionMerge)->Unit)=action {
        reviewMutex.withLock {
            val latest=_uiState.value.reviews[id] ?: review
            repository.saveReview(id,latest.encode())
            val merge=editor.applyRecognitionTask(repository,id,latest)
            repository.get(id)?.let { task -> task.reviewJson?.let { json ->
                val saved=RecognitionReview.decode(json,task.result()!!.candidates.map { it.id }.toSet())
                _uiState.update { it.copy(reviews=it.reviews+(id to saved)) }
            } }
            onApplied(merge)
        }
    }
}
