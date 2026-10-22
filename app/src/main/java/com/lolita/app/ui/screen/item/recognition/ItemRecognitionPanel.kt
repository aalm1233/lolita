package com.lolita.app.ui.screen.item.recognition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ItemEvidence
import com.lolita.app.data.local.entity.RecognitionTask
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.ItemEditViewModel
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect

@Composable
fun ItemRecognitionPanel(viewModel: ItemEditViewModel, modifier: Modifier=Modifier,
    recognitionViewModel: ItemRecognitionViewModel=viewModel(), onConfigureApi: () -> Unit = {}) {
    val editor by viewModel.uiState.collectAsState()
    val state by recognitionViewModel.uiState.collectAsState()
    val skin=LolitaSkin.current
    val context=LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var returnToWorkbench by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && returnToWorkbench) {
                returnToWorkbench = false
                open = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun configureApi() { returnToWorkbench = true; open = false; onConfigureApi() }
    var history by rememberSaveable { mutableStateOf(false) }
    var savedEvidence by remember { mutableStateOf(false) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cropIndex by remember { mutableStateOf<Int?>(null) }
    var pendingSource by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingOwned by rememberSaveable {mutableStateOf(false)}
    var pendingAppearanceOnly by rememberSaveable {mutableStateOf(false)}
    var pendingDraftId by rememberSaveable {mutableStateOf<String?>(null)}
    var chooseMore by rememberSaveable {mutableStateOf(false)}
    var appearanceOnly by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val family=editor.categories.firstOrNull { it.id==editor.categoryId }?.categorySemantic?.family ?: WardrobeFamily.UNCONFIRMED
    LaunchedEffect(editor.draftId,editor.item?.id) { recognitionViewModel.bind(editor.draftId,editor.item?.id) }
    val task=state.tasks.firstOrNull { it.id==activeId } ?: state.tasks.firstOrNull()
    val busy=state.busy || editor.isSaving
    suspend fun prepareSource(path:String,owned:Boolean) {
        var retained=false
        try {
            viewModel.keepDraft()
            val identity=requireNotNull(viewModel.uiState.value.draftId) { "请重新进入物品编辑" }
            val restricted=appearanceOnly || recognitionViewModel.isAppearanceOnly(path)
            val crop=ImageRegionCropper.crop(context,path,NormalizedCropRect.FULL)
            if(File(crop.path).length()>3L*1024*1024) {
                ImageFileHelper.cleanupUnreferencedImages(listOf(crop.path))
                pendingSource=path;pendingOwned=owned;pendingAppearanceOnly=restricted;pendingDraftId=identity
                retained=true
                notice="图片细节较多，请框选要识别的部分"
            } else {
                activeId=recognitionViewModel.stageRegion(identity,crop.path,viewModel.currentRecognitionValues(),family,restricted)
                chooseMore=false;notice=null
            }
        } finally {if(owned && !retained) ImageFileHelper.cleanupUnreferencedImages(listOf(path))}
    }
    fun stage(path:String,owned:Boolean) {recognitionViewModel.action {prepareSource(path,owned)}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) recognitionViewModel.action {prepareSource(ImageFileHelper.copyToInternalStorage(context,uri),true)}
    }
    fun closePendingCrop() {
        if(pendingOwned) pendingSource?.let {ImageFileHelper.cleanupUnreferencedImages(listOf(it))}
        pendingSource=null;pendingOwned=false;pendingDraftId=null
    }
    fun retain(path:String,asChart:Boolean) {
        recognitionViewModel.action {
            require(!viewModel.uiState.value.isSaving) { "正在保存物品，请稍后操作" }
            require(ImageFileHelper.isOwnedReadableImage(context,path)) { "来源图片已不可读" }
            if(asChart) viewModel.addSizeChartImage(path) else viewModel.addImage(path)
            viewModel.keepDraft()
            notice=if(asChart) "已加入资料图，保存物品后生效" else "已加入商品图，保存物品后生效"
        }
    }
    Column(modifier) {
        OutlinedButton(onClick={recognitionViewModel.action {
            returnToWorkbench=false;viewModel.keepDraft();recognitionViewModel.bind(viewModel.uiState.value.draftId,editor.item?.id);open=true
        }},enabled=!editor.isLoading && !busy && editor.loadError==null,modifier=Modifier.fillMaxWidth()) {
            Text(if(state.tasks.any { it.status==RecognitionStatus.AWAITING_REVIEW }) "识图填入 · 有待核对结果" else "识图填入")
        }
        notice?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) }
        if(state.evidence.isNotEmpty()) TextButton(onClick={savedEvidence=true}) { Text("已采纳的识图来源（${state.evidence.size}）") }
    }
    if(open) Dialog(onDismissRequest={if(!state.busy) open=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val listState=rememberLazyListState();ObserveSkinScroll(listState)
        var previewed by remember(task?.id,task?.regionImagePaths) {mutableStateOf(emptySet<Int>())}
        val density=LocalDensity.current.density
        val tracker=remember(task?.id,task?.regionImagePaths,density) { RecognitionPreviewTracker(64f*density) { index ->
            if(index !in previewed) previewed=previewed+index
        } }
        LaunchedEffect(task?.id,task?.requestJson,chooseMore) {
            if(task!=null && !chooseMore) listState.scrollToItem(1+if(state.error!=null) 1 else 0)
        }
        Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),topBar={
            GradientTopAppBar(title={Text("识图填入")},hazeState=null,
                navigationIcon={TextButton(onClick={open=false},enabled=!state.busy,colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)) {Text("返回")}},
                actions={TextButton(onClick={configureApi()},enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)) {Text("API 配置")}})
        },bottomBar={
            Surface { Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(skin.spacingLarge)) {
                if(task!=null) {
                    RecognitionPrimaryAction(task,state,busy,previewed.size==task.regionImagePaths.size,viewModel.currentRecognitionValues(),
                        onSubmit={state.provider?.let { provider -> recognitionViewModel.submitVisible(task,state.keyRevision,provider,viewModel,family) {activeId=it} }},
                        onPrepare={recognitionViewModel.prepareAgain(task,{path->ImageRegionCropper.crop(context,path,NormalizedCropRect.FULL).path}) {activeId=it;chooseMore=false;notice=null}},
                        onApply={review -> recognitionViewModel.apply(task.id,review,viewModel) { merge ->
                            notice="已填入 ${merge.acceptedIds.size} 项"+if(merge.conflicts.isNotEmpty()) "，${merge.conflicts.size} 项请继续核对" else "，保存后生效"
                            if(merge.acceptedIds.isNotEmpty()) open=false
                        }})
                } else Text("选图后核对，再确认发送",style=MaterialTheme.typography.bodySmall)
            } }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).onGloballyPositioned {tracker.updateViewport(it.boundsInWindow())},state=listState,
                contentPadding=PaddingValues(skin.spacingLarge),verticalArrangement=Arrangement.spacedBy(skin.spacingMedium)) {
                item {
                    Text("当前识图服务：${state.provider?.label ?: "未选择"}",style=MaterialTheme.typography.bodySmall)
                    if(task==null || chooseMore) {
                        SectionHeader(if(task?.status in setOf(RecognitionStatus.AWAITING_REVIEW,RecognitionStatus.APPLIED)) "核对识别结果" else "选择商品图或参数图")
                        Text("核对下方图片后发送，结果由你确认填入。",style=MaterialTheme.typography.bodySmall)
                        if(!state.hasKey) TextButton(onClick={configureApi()}) {Text("配置识图 API Key")}
                        Button(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("从相册选图")}
                        val paths=(editor.imageUrls+listOfNotNull(editor.sizeChartImageUrl)+editor.sizeChartImageUrls).distinct()
                        if(paths.isNotEmpty()) {
                            Text("或使用已有图片",style=MaterialTheme.typography.bodySmall)
                            val pictures=rememberLazyListState();ObserveSkinScroll(pictures)
                            LazyRow(state=pictures,horizontalArrangement=Arrangement.spacedBy(skin.spacingSmall)) {
                                itemsIndexed(paths) { index,path -> LolitaShimmerImage(path,"选择已有图片 ${index+1}",
                                    Modifier.size(88.dp).clickable(enabled=!busy) {stage(path,false)},ContentScale.Fit,circularRevealEnabled=false) }
                            }
                        }
                        val prepared=task?.takeIf {it.status==RecognitionStatus.PREPARED}
                        val marked=prepared?.let {runCatching {it.request().regions.all {row->row.appearanceOnly}}.getOrDefault(true)} ?: false
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(if(prepared!=null) marked else appearanceOnly,{checked ->
                                if(prepared!=null && checked) recognitionViewModel.markAppearanceOnly(prepared.id)
                                else appearanceOnly=checked
                            },enabled=!busy && !marked)
                            Text(if(prepared!=null) "这些图片由 AI 生成，仅参考外观" else "接下来选择 AI 图片，仅参考外观",style=MaterialTheme.typography.bodySmall)
                        }
                        if(marked) Text("已标记的图片保持外观参考，不作为真实规格。",style=MaterialTheme.typography.bodySmall)
                    } else TextButton(onClick={chooseMore=true},enabled=!busy) {Text("添加其他图片 / 来源设置")}
                }
                state.error?.let { error -> item {Text(error,color=MaterialTheme.colorScheme.error)} }
                run {
                    task?.let { selected -> item(key=selected.id) {
                        RecognitionTaskContent(selected,busy,viewModel.currentRecognitionValues(),state.reviews[selected.id],tracker,
                            onRemove={recognitionViewModel.removeRegion(selected.id,it)},onCrop={cropIndex=it},
                            onReview={recognitionViewModel.saveReview(selected.id,it)},onCancel={recognitionViewModel.cancel(selected.id)},
                            onDiscard={recognitionViewModel.discard(selected.id)},onRetain=::retain,
                            categories=editor.categories.map {it.id to it.name},brands=editor.brands.filterNot {it.isUnsetBrand}.map {it.id to it.name})
                    } }
                    if(state.tasks.size>1) item {
                        TextButton(onClick={history=!history}) {Text("${if(history) "收起" else "查看"}其他识图记录（${state.tasks.size-1}）")}
                        if(history) state.tasks.filterNot {it.id==task?.id}.forEach { previous ->
                            TextButton(onClick={activeId=previous.id;history=false}) {Text("${recognitionStatus(previous.status)} · ${previous.regionImagePaths.size} 张图")}
                        }
                    }
                }
            }
        }
    }
    pendingSource?.let { path -> ImageRegionCropDialog(path,onDismiss=::closePendingCrop,onCropped={crop ->
        val identity=pendingDraftId
        val restricted=pendingAppearanceOnly
        recognitionViewModel.action {
            try {
                require(identity!=null && viewModel.uiState.value.draftId==identity && !viewModel.uiState.value.isSaving) {"草稿已变化，请重新选择图片"}
                activeId=recognitionViewModel.stageRegion(identity,crop,viewModel.currentRecognitionValues(),family,restricted)
                chooseMore=false;notice=null
            } catch(failure:Throwable) {ImageFileHelper.cleanupUnreferencedImages(listOf(crop));throw failure}
            finally {closePendingCrop()}
        }
    },previewInParent=true) }
    cropIndex?.let { index -> task?.takeIf {it.status==RecognitionStatus.PREPARED}?.regionImagePaths?.getOrNull(index)?.let { path ->
        ImageRegionCropDialog(path,onDismiss={cropIndex=null},onCropped={crop ->
            recognitionViewModel.replaceRegion(task.id,index,crop);cropIndex=null
        },previewInParent=true)
    } }
    if(savedEvidence) SavedEvidenceDialog(state.evidence,onDismiss={savedEvidence=false},onRetain=::retain)
}

/** Layout measurements must not themselves invalidate Compose and schedule another layout. */
private class RecognitionPreviewTracker(private val minimum:Float,private val onVisible:(Int)->Unit) {
    private var viewport=Rect.Zero
    private val images=mutableMapOf<Int,Rect>()
    fun updateViewport(bounds:Rect) {viewport=bounds;images.forEach { (index,image)->check(index,image) }}
    fun updateImage(index:Int,bounds:Rect) {images[index]=bounds;check(index,bounds)}
    private fun check(index:Int,image:Rect) {
        val visible=image.intersect(viewport)
        if(visible.width>=minimum && visible.height>=minimum) onVisible(index)
    }
}

private fun recognitionStatus(status:RecognitionStatus)=when(status) {
    RecognitionStatus.PREPARED->"确认发送图片";RecognitionStatus.RUNNING->"正在识别";
    RecognitionStatus.AWAITING_REVIEW->"逐项核对建议";RecognitionStatus.APPLIED->"已填入，可继续核对";
    RecognitionStatus.INTERRUPTED->"识别已中断";RecognitionStatus.FAILED->"识别未完成";RecognitionStatus.DISCARDED->"已移除"
}

private fun recognitionReview(task:RecognitionTask,current:Map<RecognitionField,String>,stored:RecognitionReview?):RecognitionReview {
    val result=runCatching {task.result()}.getOrNull()
    return stored ?: runCatching {task.reviewJson?.let {RecognitionReview.decode(it,result?.candidates.orEmpty().map {row->row.id}.toSet())}}.getOrNull()
        ?: RecognitionReview(selectedIds=result?.candidates.orEmpty().filter {it.field in setOf(RecognitionField.NAME,RecognitionField.CATEGORY,RecognitionField.BRAND,RecognitionField.COLOR,RecognitionField.SIZE) && it.preselect(current[it.field].orEmpty())}.map {it.id}.toSet())
}

@Composable private fun RecognitionPrimaryAction(task:RecognitionTask,state:ItemRecognitionUiState,busy:Boolean,previewed:Boolean,
    current:Map<RecognitionField,String>,onSubmit:()->Unit,onPrepare:()->Unit,onApply:(RecognitionReview)->Unit) {
    val valid=remember(task) {runCatching {task.validate()}.isSuccess}
    when(task.status) {
        RecognitionStatus.PREPARED -> {
            Text("${state.provider?.label ?: "未选服务"} · ${task.regionImagePaths.size} 张图 · 按账户计费，不自动重试",style=MaterialTheme.typography.bodySmall)
            if(!previewed) Text("请先查看每张待发送图片",style=MaterialTheme.typography.bodySmall)
            Button(onClick=onSubmit,enabled=valid && !busy && previewed && state.hasKey && state.provider!=null,modifier=Modifier.fillMaxWidth()) {
                Text("确认发送并识别")
            }
        }
        RecognitionStatus.FAILED,RecognitionStatus.INTERRUPTED -> {
            Text("先重新准备并核对图片，此步不发送请求。",style=MaterialTheme.typography.bodySmall)
            Button(onClick=onPrepare,enabled=valid && !busy,modifier=Modifier.fillMaxWidth()) {Text("重新准备这些图片")}
        }
        RecognitionStatus.AWAITING_REVIEW,RecognitionStatus.APPLIED -> {
            val review=recognitionReview(task,current,state.reviews[task.id])
            Button(onClick={onApply(review)},enabled=valid && !busy && review.selectedIds.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                Text("填入 ${review.selectedIds.size} 项并返回")
            }
        }
        RecognitionStatus.RUNNING -> Text("可返回编辑，结果会保留在这里",style=MaterialTheme.typography.bodySmall)
        else -> Unit
    }
}

@Composable private fun RecognitionTaskContent(task:RecognitionTask,busy:Boolean,current:Map<RecognitionField,String>,storedReview:RecognitionReview?,tracker:RecognitionPreviewTracker,
    onRemove:(Int)->Unit,onCrop:(Int)->Unit,onReview:(RecognitionReview)->Unit,onCancel:()->Unit,onDiscard:()->Unit,onRetain:(String,Boolean)->Unit,
    categories:List<Pair<Long,String>>,brands:List<Pair<Long,String>>) {
    val skin=LolitaSkin.current
    val validation=remember(task) {runCatching {task.validate();task.result()}}
    val result=validation.getOrNull()
    val request=remember(task.requestJson) {runCatching {task.request()}.getOrNull()}
    val review=recognitionReview(task,current,storedReview)
    var details by rememberSaveable(task.id) {mutableStateOf(false)}
    var preview by remember(task.id) {mutableStateOf<Int?>(null)}
    val common=setOf(RecognitionField.NAME,RecognitionField.CATEGORY,RecognitionField.BRAND,RecognitionField.COLOR,RecognitionField.SIZE)
    LolitaCard(Modifier.fillMaxWidth()) { Column(verticalArrangement=Arrangement.spacedBy(skin.spacingSmall)) {
        SectionHeader(recognitionStatus(task.status))
        task.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(validation.isFailure) Text("这条识图记录无法读取，请移除后重新选图。",color=MaterialTheme.colorScheme.error)
        if(result==null || task.status in setOf(RecognitionStatus.PREPARED,RecognitionStatus.RUNNING,RecognitionStatus.FAILED,RecognitionStatus.INTERRUPTED)) {
            task.regionImagePaths.forEachIndexed { index,path ->
                LolitaShimmerImage(path,"实际识别区域 ${index+1}",Modifier.fillMaxWidth().height(220.dp).clickable {preview=index}.onGloballyPositioned { coordinates ->
                    tracker.updateImage(index,coordinates.boundsInWindow())
                },ContentScale.Fit,circularRevealEnabled=false)
                if(task.status==RecognitionStatus.PREPARED) {
                    TextButton(onClick={onCrop(index)},enabled=!busy) {Text("框选 / 旋转图片 ${index+1}")}
                    TextButton(onClick={onRemove(index)},enabled=!busy) {Text("移除图片 ${index+1}")}
                }
            }
        }
        if(task.status==RecognitionStatus.RUNNING) {
            Row(horizontalArrangement=Arrangement.spacedBy(skin.spacingSmall)) {CircularProgressIndicator(Modifier.size(20.dp));Text("正在等待服务返回")}
            TextButton(onClick=onCancel,enabled=!busy) {Text("取消本地等待")}
        }
        if(task.status in setOf(RecognitionStatus.AWAITING_REVIEW,RecognitionStatus.APPLIED) && result!=null && request!=null) {
            val variants=result.candidates.mapNotNull {it.variant}.distinct()
            val sizes=result.candidates.mapNotNull {it.sizeRow}.distinct()
            if(variants.isNotEmpty()) ReviewChoice("正在登记的款式",review.variant,variants,!busy) {onReview(review.copy(variant=it).forSelection(result.candidates))}
            if(sizes.isNotEmpty()) ReviewChoice("本件尺码",review.sizeRow,sizes,!busy) {onReview(review.copy(sizeRow=it).forSelection(result.candidates))}
            if(result.candidates.isEmpty()) Text("没有读到可填写的信息。可换一张清晰图片，或返回手工填写。")
            result.candidates.filter {it.field in common}.forEach { candidate ->
                RecognitionCandidateRow(candidate,task,review,current,busy,onReview,onRetain,categories,brands)
            }
            val extra=result.candidates.filterNot {it.field in common}
            if(extra.isNotEmpty()) {
                TextButton(onClick={details=!details}) {Text("${if(details) "收起" else "展开"}详细规格（${extra.size}）")}
                if(details) extra.forEach {candidate -> RecognitionCandidateRow(candidate,task,review,current,busy,onReview,onRetain,categories,brands)}
            }
            Text("已有资料不会被自动覆盖；填入后仍可修改。",style=MaterialTheme.typography.bodySmall)
        }
        if(task.status!=RecognitionStatus.RUNNING) TextButton(onClick=onDiscard,enabled=!busy) {Text("移除此识图记录")}
    } }
    preview?.let { page -> com.lolita.app.ui.component.FullScreenImageViewer(task.regionImagePaths,page) {preview=null} }
}

@Composable private fun RecognitionCandidateRow(candidate:RecognitionCandidate,task:RecognitionTask,review:RecognitionReview,
    current:Map<RecognitionField,String>,busy:Boolean,onReview:(RecognitionReview)->Unit,onRetain:(String,Boolean)->Unit,
    categories:List<Pair<Long,String>>,brands:List<Pair<Long,String>>) {
    val skin=LolitaSkin.current
    var edit by rememberSaveable(task.id,candidate.id) {mutableStateOf(false)}
    var source by rememberSaveable(task.id,candidate.id) {mutableStateOf(false)}
    val value=review.edits[candidate.id] ?: candidate.value
    val request=task.request()
    val restricted=candidate.regionIds.any { id->request.regions.first {it.id==id}.appearanceOnly }
    val eligible=candidate.copy(value=value).eligible(review.variant,review.sizeRow) && (!restricted || candidate.source==RecognitionSource.VISUAL && !candidate.field.textOnly)
    Column(Modifier.fillMaxWidth().padding(vertical=skin.spacingSmall)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(candidate.id in review.selectedIds && eligible,{checked ->onReview(review.copy(selectedIds=if(checked) review.selectedIds+candidate.id else review.selectedIds-candidate.id))},modifier=Modifier.semantics {contentDescription="采纳${candidate.field.label}：$value"},enabled=eligible && !busy)
            Column(Modifier.weight(1f)) {
                Text(candidate.field.label,style=MaterialTheme.typography.labelMedium)
                if(edit) OutlinedTextField(value,{onReview(review.copy(edits=review.edits+(candidate.id to it)))},Modifier.fillMaxWidth(),label={Text("识别建议，可修改")},enabled=!busy)
                else Text(value,Modifier.fillMaxWidth().clickable(enabled=!busy) {edit=true},style=MaterialTheme.typography.bodyLarge)
                val previous=current[candidate.field].orEmpty()
                if(previous.isNotBlank()) Text("当前：$previous",style=MaterialTheme.typography.bodySmall)
            }
        }
        if(!eligible) Text(when {
            restricted->"AI 图片只可参考外观"
            candidate.field==RecognitionField.PRICE_NOTE->"标价仅供参考"
            candidate.variant!=null && review.variant!=candidate.variant->"请先选择对应款式"
            candidate.sizeRow!=null && review.sizeRow!=candidate.sizeRow->"请先选择对应尺码行"
            else->"此项缺少可靠原文或明确单位，可手工填写"
        },style=MaterialTheme.typography.bodySmall)
        if(candidate.field==RecognitionField.CATEGORY) ReferenceChoice("选择现有类型",review.resolvedCategoryId,categories,!busy) {onReview(review.copy(resolvedCategoryId=it))}
        if(candidate.field==RecognitionField.BRAND) ReferenceChoice("选择现有品牌",review.resolvedBrandId,brands,!busy) {onReview(review.copy(resolvedBrandId=it))}
        TextButton(onClick={source=!source}) {Text(if(source) "收起来源" else "查看来源")}
        if(source) {
            Text(candidate.source.label,style=MaterialTheme.typography.labelMedium)
            Text(candidate.quote.ifBlank {"没有明文，仅为外观建议"},style=MaterialTheme.typography.bodySmall)
            candidate.regionIds.forEach { id -> task.regionImagePaths.getOrNull(request.regions.indexOfFirst {it.id==id})?.let { path ->
                LolitaShimmerImage(path,"证据裁切区域",Modifier.fillMaxWidth().height(180.dp),ContentScale.Fit,circularRevealEnabled=false)
                TextButton(onClick={onRetain(path,true)}) {Text("保留为资料图")}
            } }
        }
    }
}

@Composable private fun EvidencePreview(candidate:RecognitionCandidate,paths:List<String>,onDismiss:()->Unit,onRetain:(String,Boolean)->Unit) {
    val scroll=rememberScrollState();ObserveSkinScroll(scroll)
    AlertDialog(onDismissRequest=onDismiss,title={Text("${candidate.field.label}来源")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(scroll)) {
            Text(candidate.source.label+(candidate.variant?.let { " · $it" } ?: "")+(candidate.sizeRow?.let { " · 尺码 $it" } ?: ""));Text(candidate.quote.ifBlank { "没有可核对文字，仅为外观建议" })
            candidate.measurement?.let { Text("测量口径：${when(it.basis) {RecognitionMeasurementBasis.PRODUCT_MEASURED->"成品实测";RecognitionMeasurementBasis.BODY_FIT->"人体适配";RecognitionMeasurementBasis.FLAT_WIDTH->"平铺宽度";RecognitionMeasurementBasis.STRETCHED_RANGE->"拉伸范围";else->"未确认"}} · 单位 ${it.unit.name.lowercase()}") }
            paths.forEach { path ->
                LolitaShimmerImage(path,"证据裁切区域",Modifier.fillMaxWidth().height(220.dp),ContentScale.Fit,circularRevealEnabled=false)
                TextButton(onClick={onRetain(path,false)}) { Text("保留为商品图") }
                TextButton(onClick={onRetain(path,true)}) { Text("保留为资料图") }
            }
            Text("来源已保留；加入商品图或资料图后，保存物品才生效。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("返回核对")}})
}

@Composable private fun SavedEvidenceDialog(rows:List<ItemEvidence>,onDismiss:()->Unit,onRetain:(String,Boolean)->Unit) {
    var selected by remember { mutableStateOf<ItemEvidence?>(null) }
    val listState=rememberLazyListState();ObserveSkinScroll(listState)
    AlertDialog(onDismissRequest=onDismiss,title={Text("已采纳的识图来源")},text={LazyColumn(Modifier.heightIn(max=440.dp),state=listState) {
        items(rows,key={it.id}) { evidence -> val payload=runCatching { evidence.validateRecognitionEvidence() }.getOrNull()
            TextButton(onClick={if(payload!=null) selected=evidence}) { Text("${if(evidence.itemId==null) "草稿 · " else ""}${evidence.field.label}：${payload?.candidate?.value ?: "来源记录无法读取"}") }
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
    selected?.let { evidence -> runCatching { evidence.validateRecognitionEvidence() }.getOrNull()?.let { payload ->
        EvidencePreview(payload.candidate,evidence.regionImagePaths,onDismiss={selected=null},onRetain=onRetain)
    } }
}

@Composable private fun ReviewChoice(label:String,value:String?,options:List<String>,enabled:Boolean,onChange:(String?)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuScroll=rememberScrollState();ObserveSkinScroll(menuScroll)
    Column { TextButton(onClick={expanded=true},enabled=enabled){Text("$label：${value ?: "未确认"}")}
        DropdownMenu(expanded,{expanded=false},scrollState=menuScroll) { DropdownMenuItem({Text("未确认")},{onChange(null);expanded=false});options.forEach { option -> DropdownMenuItem({Text(option)},{onChange(option);expanded=false}) } }
    }
}
@Composable private fun ReferenceChoice(label:String,value:Long?,options:List<Pair<Long,String>>,enabled:Boolean,onChange:(Long?)->Unit) {
    var expanded by remember { mutableStateOf(false) };var query by remember { mutableStateOf("") }
    TextButton(onClick={expanded=true},enabled=enabled){Text("$label：${options.firstOrNull { it.first==value }?.second ?: "尚未选择"}")}
    if(expanded) {
        val listState=rememberLazyListState();ObserveSkinScroll(listState)
        AlertDialog(onDismissRequest={expanded=false},title={Text(label)},text={Column {
        OutlinedTextField(query,{query=it},label={Text("搜索名称")})
        LazyColumn(Modifier.heightIn(max=280.dp),state=listState) { items(options.filter { query.isBlank() || it.second.contains(query,true) }) { row -> TextButton(onClick={onChange(row.first);expanded=false}){Text(row.second)} } }
    }},confirmButton={TextButton(onClick={expanded=false}){Text("取消")}})
    }
}
