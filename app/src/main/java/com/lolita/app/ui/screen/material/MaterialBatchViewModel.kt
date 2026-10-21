package com.lolita.app.ui.screen.material

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.tryon.ImageServiceStatus
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MaterialBatchPhase(val label: String) { INPUTS("选物品"), FRONT("主视图"), LAYERS("透明层"), SAVED("已确认") }
data class MaterialBatchRow(val key: String, val itemId: Long, val name: String, val brand: String, val position: Int,
    val item: Item?, val semantic: CategorySemantic?, val material: ItemTryOnMaterial?, val task: ImageTask?,
    val source: String, val original: String, val selectedLayer: String? = null, val categoryName: String = "") {
    val eligible get() = item?.status?.supportsVirtualTryOn == true && semantic?.supportsMaterialPreparation == true
    val layers get() = task?.let { runCatching { MaterialLayers.decode(it.layersJson) }.getOrDefault(emptyList()) }.orEmpty()
    val matchesTask get() = task?.let{ImageTaskRequest.decode(it.requestJson)}?.let{request->
        request.garments.single().semantic == semantic && (request.version<6 || request.garments.single().composition == item?.composition)
    } ?: true
    val image get() = selectedLayer ?: task?.resultImagePath ?: source
    val canRetry get() = task?.let { !it.sourceResourcesMissing && it.status in setOf(ImageTaskStatus.FAILED, ImageTaskStatus.INTERRUPTED, ImageTaskStatus.CANCELLED) } == true
}
data class MaterialBatchFeeRow(val itemId: Long, val name: String, val position: Int, val input: PreparedMaterialInput,
    val originalPath: String, val expectedMaterialId: String?, val parentTaskId: String?)
data class MaterialBatchFee(val batch: MaterialBatchIdentity, val operationId: String, val phase: MaterialBatchPhase,
    val rows: List<MaterialBatchFeeRow>, val provider: ImageGenerationProvider, val revision: Int, val fingerprint: String, val instructions: String)
data class MaterialBatchState(val id: String, val name: String, val phase: MaterialBatchPhase = MaterialBatchPhase.INPUTS,
    val loading: Boolean = true, val loadFailed: Boolean = false, val busy: Boolean = false, val prepared: Int = 0,
    val rows: List<MaterialBatchRow> = emptyList(), val jobs: List<ImageTask> = emptyList(), val selected: Set<String> = emptySet(),
    val query: String = "", val onlyMissing: Boolean = false, val instructions: String = "", val checks: Set<Int> = emptySet(),
    val provider: ImageGenerationProvider = ImageGenerationProvider.SEEDREAM, val hasKey: Boolean = false, val keyLoaded: Boolean = false,
    val revision: Int = 0, val fee: MaterialBatchFee? = null, val error: String? = null, val message: String? = null, val family: WardrobeFamily? = null) {
    val identity get() = MaterialBatchIdentity(id,name)
    val draft get() = jobs.isEmpty()
    val selectedRows get() = rows.filter { it.key in selected }
    val visibleRows get() = rows.filter { (query.isBlank() || "${it.name} ${it.brand} ${it.categoryName} ${it.semantic?.label.orEmpty()}".contains(query.trim(),true)) && (family == null || it.semantic?.family == family) && (!onlyMissing || it.material == null) }
    val active get() = jobs.filter { it.status.retainsSourceImages }
    val latestJobs get() = jobs.groupBy{it.batchPosition}.values.map{rows->rows.maxWith(compareBy<ImageTask>{it.createdAt}.thenBy{it.id})}
    fun selectable(row: MaterialBatchRow): Boolean = when(phase) {
        MaterialBatchPhase.INPUTS -> row.eligible && row.source.isNotBlank() && (draft || row.canRetry)
        MaterialBatchPhase.FRONT -> row.eligible && row.matchesTask && row.task?.status == ImageTaskStatus.AWAITING_CONFIRMATION
        MaterialBatchPhase.LAYERS -> row.eligible && row.matchesTask && (row.task?.status == ImageTaskStatus.AWAITING_CONFIRMATION || row.canRetry)
        MaterialBatchPhase.SAVED -> false
    }
    fun fingerprint(): String = "$id|$name|$phase|$instructions|$revision|$provider|" + selectedRows.joinToString { row ->
        "${row.key}:${row.item}:${row.semantic}:${row.material}:${row.task}:${row.source}:${row.original}:${row.selectedLayer}"
    }
}

class MaterialBatchViewModel(private val handle: SavedStateHandle,
    items: Flow<List<Item>> = AppModule.itemRepository().getAllItems(),
    categories: Flow<List<Category>> = AppModule.categoryRepository().getAllCategories(),
    materials: Flow<List<ItemTryOnMaterial>> = AppModule.itemTryOnMaterialRepository().materials,
    tasks: Flow<List<ImageTask>> = AppModule.imageTaskRepository().tasks,
    brands: Flow<List<Brand>> = AppModule.brandRepository().getAllBrands(),
    private val coordinator: ImageTaskCoordinator = AppModule.imageTaskCoordinator(),
    private val readService: suspend () -> ImageServiceStatus = { AppModule.tryOnSettings().readStatus() },
    changes: Flow<Int> = AppModule.tryOnSettings().changes,
    private val pin: (List<String>) -> (suspend () -> Unit) = { paths -> val lease=ImageFileHelper.pinImageReferences(AppModule.context(),paths);val release:suspend()->Unit={lease.release()};release },
    private val cleanup: (List<String>) -> Unit = { ImageFileHelper.cleanupUnreferencedImages(it);Unit }
) : ViewModel() {
    private data class Data(val items:List<Item>,val categories:List<Category>,val materials:List<ItemTryOnMaterial>,val tasks:List<ImageTask>,val brands:List<Brand>)
    private val id=handle.get<String>("batchId")?.takeIf(String::isNotBlank) ?: handle.get<String>("batchDraftId") ?: UUID.randomUUID().toString().also{handle["batchDraftId"]=it}
    private val initialPhase=(handle.get<String>("batchPhase") ?: handle.get<String>("phase"))?.let{runCatching{MaterialBatchPhase.valueOf(it)}.getOrNull()}
    private val state=MutableStateFlow(MaterialBatchState(id,handle.get<String>("batchName") ?: "素材批次 · ${SimpleDateFormat("MM/dd HH:mm",Locale.CHINA).format(Date())}",
        phase=initialPhase ?: MaterialBatchPhase.INPUTS, query=handle.get<String>("batchQuery").orEmpty(),instructions=handle.get<String>("batchInstructions").orEmpty(), family=handle.get<String>("batchFamily")?.let{runCatching{WardrobeFamily.valueOf(it)}.getOrNull()}))
    val uiState=state.asStateFlow()
    private var data:Data?=null
    private var phaseInitialized=initialPhase!=null
    private var generation=0
    private var leases=emptyList<suspend()->Unit>()
    private val crops=mutableMapOf<Long,Pair<String,suspend()->Unit>>()
    init {
        handle.get<Long>("itemId")?.takeIf{it>0 && handle.get<List<String>>("batchSelected${MaterialBatchPhase.INPUTS}")==null}?.let{handle["batchSelected${MaterialBatchPhase.INPUTS}"]=listOf(it.toString())}
        handle.get<List<Long>>("batchCropItems").orEmpty().forEach { itemId -> handle.get<String>("batchSource$itemId")?.let{crops[itemId]=it to pin(listOf(it))} }
        viewModelScope.launch {
            combine(items,categories,materials,tasks,brands,::Data).catch { failure ->
                if(failure is CancellationException)throw failure
                state.update{it.copy(loading=false,loadFailed=true,error="批次资料读取失败，请返回后重试")}
            }.collect { snapshot ->
                data=snapshot
                val jobs=snapshot.tasks.filter{it.batchId==id}
                if(!phaseInitialized && jobs.isNotEmpty()) {
                    val layers=jobs.filter{ImageTaskRequest.decode(it.requestJson).materialSpec?.stage==MaterialStage.EXTRACT_LAYER}
                    val next=if(layers.any{it.status!=ImageTaskStatus.COMPLETED})MaterialBatchPhase.LAYERS
                        else if(layers.isNotEmpty() && layers.all{it.status==ImageTaskStatus.COMPLETED})MaterialBatchPhase.SAVED else MaterialBatchPhase.FRONT
                    state.update{it.copy(phase=next)};phaseInitialized=true
                }
                val name=jobs.firstOrNull()?.batchName ?: state.value.name
                handle["batchName"]=name
                refresh(state.value.copy(name=name,jobs=jobs,loading=false))
            }
        }
        viewModelScope.launch { changes.collect {
            invalidateFee();state.update{it.copy(keyLoaded=false,revision=it.revision+1)}
            try {val status=withContext(Dispatchers.IO){readService()};state.update{it.copy(provider=status.provider,hasKey=status.hasKey,keyLoaded=true)}}
            catch(failure:Exception){if(failure is CancellationException)throw failure;state.update{it.copy(keyLoaded=true,hasKey=false,error="图像服务配置读取失败")}}
        } }
    }
    private fun rows(s:MaterialBatchState):List<MaterialBatchRow> {
        val d=data ?: return emptyList()
        val requests=s.jobs.associate{it.id to ImageTaskRequest.decode(it.requestJson)}
        val grouped=s.jobs.groupBy{requireNotNull(requests[it.id]).garments.single().itemId}
        val records=if(s.draft) d.items.filter{it.status.supportsVirtualTryOn && d.categories.firstOrNull{c->c.id==it.categoryId}?.categorySemantic?.supportsMaterialPreparation==true}.map{it.id to null}
            else grouped.map{it.key to it.value.minBy{j->requireNotNull(j.batchPosition)}}
        return records.mapIndexedNotNull { index,(itemId,first) ->
            val item=d.items.firstOrNull{it.id==itemId};val all=grouped[itemId].orEmpty()
            val stage=if(s.phase==MaterialBatchPhase.LAYERS)MaterialStage.EXTRACT_LAYER else MaterialStage.FRONT_VIEW
            val task=all.filter{requests[it.id]?.materialSpec?.stage==stage}.maxWithOrNull(compareBy<ImageTask>{it.createdAt}.thenBy{it.id})
            val material=d.materials.firstOrNull{it.itemId==itemId}
            if(s.phase==MaterialBatchPhase.SAVED && material==null || s.phase in setOf(MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS) && task==null)return@mapIndexedNotNull null
            val source=(if(s.phase==MaterialBatchPhase.INPUTS)handle.get<String>("batchSource$itemId") else null) ?: task?.sourceImagePaths?.singleOrNull() ?: item?.productReferenceImages?.firstOrNull().orEmpty()
            val original=handle.get<String>("batchOriginal$itemId") ?: task?.let{requests[it.id]?.productOriginalImagePath} ?: source
            val key=if(s.phase in setOf(MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS))requireNotNull(task).id else itemId.toString()
            val layer=if(s.phase==MaterialBatchPhase.LAYERS)handle.get<String>("batchLayer$key") else null
            MaterialBatchRow(key,itemId,item?.name ?: first?.let{requests[it.id]?.garments?.single()?.name}.orEmpty(),
                d.brands.firstOrNull{it.id==item?.brandId}?.name.orEmpty(),first?.batchPosition ?: index,item,
                d.categories.firstOrNull{it.id==item?.categoryId}?.categorySemantic,material,task,source,original,
                layer?.takeIf{path->task?.let{MaterialLayers.decode(it.layersJson).any{l->l.imagePath==path}}==true}, d.categories.firstOrNull{it.id==item?.categoryId}?.name.orEmpty())
        }.sortedBy{it.position}
    }
    private fun refresh(next:MaterialBatchState=state.value) {
        val old=state.value
        val selected=handle.get<List<String>>("batchSelected${next.phase}").orEmpty().toSet()
        val result=next.copy(rows=rows(next),selected=selected)
        val changed=old.fingerprint()!=result.fingerprint()
        if(changed)invalidateFee()
        state.value=result.copy(fee=if(changed)null else old.fee,checks=if(changed)emptySet() else old.checks)
    }
    fun phase(value:MaterialBatchPhase){if(state.value.busy)return;invalidateFee();handle["batchPhase"]=value.name;phaseInitialized=true;refresh(state.value.copy(phase=value,error=null,message=null))}
    fun query(value:String){handle["batchQuery"]=value;state.update{it.copy(query=value)}}
    fun family(value:WardrobeFamily?){handle["batchFamily"]=value?.name;state.update{it.copy(family=value)}}
    fun onlyMissing(value:Boolean){state.update{it.copy(onlyMissing=value)}}
    fun toggle(key:String,value:Boolean){
        val s=state.value;if(s.busy)return;val row=s.rows.firstOrNull{it.key==key} ?: return
        if(value && (!s.selectable(row) || s.selectedRows.size>=MaterialBatchIdentity.MAX_ITEMS)){state.update{it.copy(error="每批最多选择30件可用物品")};return}
        invalidateFee();val selected=if(value)s.selected+key else s.selected-key;handle["batchSelected${s.phase}"]=selected.toList()
        state.update{it.copy(selected=selected,checks=emptySet(),error=null,message=null)}
    }
    fun selectFailed(){val s=state.value;if(s.busy)return;invalidateFee();val keys=s.rows.filter{it.canRetry && s.selectable(it)}.take(MaterialBatchIdentity.MAX_ITEMS).map{it.key}.toSet();handle["batchSelected${s.phase}"]=keys.toList();state.update{it.copy(selected=keys,checks=emptySet())}}
    fun selectReady(){val s=state.value;if(s.busy)return;invalidateFee();val keys=s.rows.filter{it.task?.status==ImageTaskStatus.AWAITING_CONFIRMATION && s.selectable(it)}.take(MaterialBatchIdentity.MAX_ITEMS).map{it.key}.toSet();handle["batchSelected${s.phase}"]=keys.toList();state.update{it.copy(selected=keys,checks=emptySet())}}
    fun instructions(value:String){if(state.value.busy)return;invalidateFee();handle["batchInstructions"]=value.take(200);state.update{it.copy(instructions=value.take(200))}}
    fun selectSource(itemId:Long,path:String){val s=state.value;if(s.busy || path !in s.rows.firstOrNull{it.itemId==itemId}?.item?.productReferenceImages.orEmpty())return
        invalidateFee();releaseCrop(itemId);handle["batchSource$itemId"]=path;handle["batchOriginal$itemId"]=null;refresh()}
    fun crop(itemId:Long,original:String,path:String){val s=state.value;if(s.busy || original !in s.rows.firstOrNull{it.itemId==itemId}?.item?.productReferenceImages.orEmpty()){cleanup(listOf(path));return}
        invalidateFee();releaseCrop(itemId);crops[itemId]=path to pin(listOf(path,original));handle["batchCropItems"]=crops.keys.toList();handle["batchSource$itemId"]=path;handle["batchOriginal$itemId"]=original;refresh()}
    fun layer(key:String,path:String){if(state.value.busy || state.value.rows.firstOrNull{it.key==key}?.layers?.none{it.imagePath==path}!=false)return
        handle["batchLayer$key"]=path;refresh(state.value.copy(checks=emptySet()))}
    fun check(index:Int,value:Boolean){if(state.value.busy || index !in 0..2)return;state.update{it.copy(checks=if(value)it.checks+index else it.checks-index)}}
    fun prepareFee(){
        val s=state.value;val selected=s.selectedRows
        if(s.busy || s.loading || s.loadFailed || !s.keyLoaded || !s.hasKey || s.provider!=ImageGenerationProvider.SEEDREAM || selected.isEmpty() || selected.any{!s.selectable(it)} ||
            s.phase !in setOf(MaterialBatchPhase.INPUTS,MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS) || s.phase==MaterialBatchPhase.LAYERS && selected.any{!it.canRetry})return
        invalidateFee();val ticket=generation;state.update{it.copy(busy=true,prepared=0,error=null,message=null)}
        viewModelScope.launch {
            val prepared=mutableListOf<MaterialBatchFeeRow>();val pins=mutableListOf<suspend()->Unit>()
            try {
                selected.forEachIndexed { index,row ->
                    val source=when(s.phase){MaterialBatchPhase.INPUTS->row.source;MaterialBatchPhase.FRONT->requireNotNull(row.task?.resultImagePath);else->requireNotNull(row.task).sourceImagePaths.single()}
                    val input=withContext(Dispatchers.IO){coordinator.prepareMaterialInput(source,false)}
                    prepared+=MaterialBatchFeeRow(row.itemId,row.name,if(s.draft)index else row.position,input,row.original,row.material?.id,row.task?.id)
                    pins+=pin(listOf(input.imagePath));state.update{it.copy(prepared=prepared.size)}
                }
                if(ticket!=generation || s.fingerprint()!=state.value.fingerprint())return@launch
                leases=pins.toList()
                state.update{it.copy(fee=MaterialBatchFee(s.identity,UUID.randomUUID().toString(),s.phase,prepared.toList(),s.provider,s.revision,s.fingerprint(),s.instructions))}
            }catch(failure:Exception){if(failure is CancellationException)throw failure;state.update{it.copy(error="本批实际输入未准备完整，请核对所选图片；尚未发送请求")}}
            finally {if(state.value.fee?.rows!=prepared){pins.forEach{it()};cleanup(prepared.map{it.input.imagePath})};state.update{it.copy(busy=false)}}
        }
    }
    fun submitFee(fee:MaterialBatchFee){
        val s=state.value;if(s.busy || s.fee!=fee || s.fingerprint()!=fee.fingerprint || s.revision!=fee.revision)return
        state.update{it.copy(busy=true,error=null)}
        viewModelScope.launch {try{
            val service=withContext(Dispatchers.IO){readService()};require(service.hasKey && service.provider==fee.provider && state.value.fingerprint()==fee.fingerprint)
            if(fee.phase==MaterialBatchPhase.INPUTS)coordinator.submitMaterialBatch(fee.batch,fee.operationId,fee.rows.map{BatchMaterialInput(it.itemId,it.input,it.originalPath,it.expectedMaterialId,fee.instructions,it.position)},fee.provider)
            else coordinator.submitLayerBatch(fee.batch,fee.operationId,fee.rows.map{BatchLayerInput(requireNotNull(it.parentTaskId),it.input)})
            invalidateFee();val next=if(fee.phase==MaterialBatchPhase.INPUTS)MaterialBatchPhase.FRONT else MaterialBatchPhase.LAYERS
            handle["batchPhase"]=next.name;handle["batchSelected${fee.phase}"]=emptyList<String>();phaseInitialized=true
            refresh(state.value.copy(phase=next,message="本批已加入队列，可离开页面；返回后集中核对"))
        }catch(failure:Exception){if(failure is CancellationException)throw failure;state.update{it.copy(error="本批提交未完成，请在任务中心核对记录后再操作；不会自动重发")}}
        finally{state.update{it.copy(busy=false)}}}
    }
    fun saveSnapshot():List<BatchMaterialConfirmation>? {
        val s=state.value;if(s.busy || s.phase!=MaterialBatchPhase.LAYERS || s.checks!=setOf(0,1,2) || s.selectedRows.isEmpty() || s.selectedRows.any{it.task?.status!=ImageTaskStatus.AWAITING_CONFIRMATION || !it.eligible || !it.matchesTask})return null
        return s.selectedRows.map{BatchMaterialConfirmation(requireNotNull(it.task).id,requireNotNull(it.image),it.material?.id)}
    }
    fun save(rows:List<BatchMaterialConfirmation>){if(rows!=saveSnapshot())return;mutate{
        coordinator.confirmMaterialBatch(id,rows,true);handle["batchSelected${MaterialBatchPhase.LAYERS}"]=emptyList<String>();handle["batchPhase"]=MaterialBatchPhase.SAVED.name
        refresh(state.value.copy(phase=MaterialBatchPhase.SAVED,checks=emptySet(),message="已保存${rows.size}份确认素材"))}}
    fun cancel(ids:List<String>)=mutate{coordinator.cancelMaterialBatch(id,ids)}
    private fun mutate(action:suspend()->Unit){if(state.value.busy)return;state.update{it.copy(busy=true,error=null)};viewModelScope.launch{try{action()}catch(failure:Exception){if(failure is CancellationException)throw failure;state.update{it.copy(error="本批操作未完成，请核对最新状态；原资料仍保留")}}finally{state.update{it.copy(busy=false)}}}}
    fun dismissFee(){if(!state.value.busy)invalidateFee()}
    private fun invalidateFee(){generation++;val old=leases;leases=emptyList();val paths=state.value.fee?.rows.orEmpty().map{it.input.imagePath};state.update{it.copy(fee=null)};releaseBatchImages(old,paths,cleanup)}
    private fun releaseCrop(itemId:Long){crops.remove(itemId)?.let{releaseBatchImages(listOf(it.second),listOf(it.first),cleanup)};handle["batchCropItems"]=crops.keys.toList()}
    override fun onCleared(){invalidateFee();crops.keys.toList().forEach(::releaseCrop);super.onCleared()}
}

private val batchCleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
private fun releaseBatchImages(releases:List<suspend()->Unit>,paths:List<String>,cleanup:(List<String>)->Unit){batchCleanupScope.launch{try{releases.forEach{it()}}finally{cleanup(paths)}}}
