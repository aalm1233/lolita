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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ItemEvidence
import com.lolita.app.data.local.entity.RecognitionTask
import com.lolita.app.domain.model.*
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.ItemEditViewModel
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import kotlinx.coroutines.launch

@Composable
fun ItemRecognitionPanel(viewModel: ItemEditViewModel, modifier: Modifier=Modifier,
    recognitionViewModel: ItemRecognitionViewModel=viewModel()) {
    val editor by viewModel.uiState.collectAsState()
    val state by recognitionViewModel.uiState.collectAsState()
    val skin=LolitaSkin.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf(false) }
    var savedEvidence by remember { mutableStateOf(false) }
    var cropSource by rememberSaveable { mutableStateOf<String?>(null) }
    var ownedSource by rememberSaveable { mutableStateOf(false) }
    var appearanceOnly by rememberSaveable { mutableStateOf(false) }
    var cropAppearanceOnly by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<RecognitionTask?>(null) }
    var confirmRevision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val family=editor.categories.firstOrNull { it.id==editor.categoryId }?.categorySemantic?.family ?: WardrobeFamily.UNCONFIRMED
    LaunchedEffect(editor.draftId,editor.item?.id) { recognitionViewModel.bind(editor.draftId,editor.item?.id) }
    LaunchedEffect(state.keyRevision) {
        if(confirm!=null && confirmRevision!=state.keyRevision) {confirm=null;notice="服务或密钥已更改，请重新核对区域与费用"}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) recognitionViewModel.action {
            cropSource=ImageFileHelper.copyToInternalStorage(context,uri)
            ownedSource=true;cropAppearanceOnly=appearanceOnly
        }
    }
    fun closeCrop() {
        if(ownedSource) cropSource?.let { ImageFileHelper.cleanupUnreferencedImages(listOf(it)) }
        cropSource=null;ownedSource=false
    }
    fun retain(path:String,asChart:Boolean) {
        recognitionViewModel.action {
            val identity=viewModel.uiState.value.draftId
            require(!viewModel.uiState.value.isSaving) { "正在保存物品，请稍后操作" }
            require(ImageFileHelper.isOwnedReadableImage(context,path)) { "来源图片已不可读" }
            require(viewModel.uiState.value.draftId==identity && !viewModel.uiState.value.isSaving) { "草稿已切换或正在保存" }
            if(asChart) viewModel.addSizeChartImage(path) else viewModel.addImage(path)
            viewModel.keepDraft()
            notice=if(asChart) "已保留为资料图，保存物品后生效" else "已保留为商品图，保存物品后生效"
        }
    }
    LolitaSection("AI 识图补全",modifier) {
        row {
            Text("从商品图或参数图框选区域，逐项核对后填入草稿。",style=MaterialTheme.typography.bodySmall)
            Button(onClick={ recognitionViewModel.action { viewModel.keepDraft();recognitionViewModel.bind(viewModel.uiState.value.draftId,editor.item?.id);open=true } },
                enabled=!editor.isLoading && !editor.isSaving && editor.loadError==null && !state.busy) { Text("选择图片并补充资料") }
            if(state.tasks.any { it.status in setOf(RecognitionStatus.AWAITING_REVIEW,RecognitionStatus.APPLIED) }) Text("有可核对的识图建议",color=MaterialTheme.colorScheme.primary)
            if(state.evidence.isNotEmpty()) TextButton(onClick={savedEvidence=true}) { Text("查看已采纳的识图来源（${state.evidence.size}）") }
        }
    }
    if(settings) RecognitionKeyDialog(state.provider,state.hasKey,onProvider=recognitionViewModel::selectProvider,
        onDismiss={settings=false},onSave={recognitionViewModel.saveKey(it);settings=false},onClear={recognitionViewModel.clearKey();settings=false})
    if(open) {
        val scroll=rememberScrollState();ObserveSkinScroll(scroll)
        AlertDialog(onDismissRequest={if(!state.busy) open=false},title={Text("识图补充物品资料")},text={
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(skin.spacingMedium)) {
                Text(state.provider?.let { "${it.label} · ${it.model}" } ?: "请明确选择识图服务",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={settings=true},enabled=!state.busy) { Text(if(state.hasKey) "管理独立识图密钥与服务" else "配置独立识图密钥与服务") }
                Text("仅发送最终确认的裁切区域。不要选择支付截图；AI 图片只能提供外观建议。",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!state.busy && !editor.isSaving) { Text("从相册框选") }
                TextButton(onClick={existing=true},enabled=!state.busy && !editor.isSaving && (editor.imageUrls.isNotEmpty() || editor.sizeChartImageUrl!=null || editor.sizeChartImageUrls.isNotEmpty())) { Text("从现有商品图或资料图框选") }
                Row { Checkbox(appearanceOnly,{appearanceOnly=it},enabled=!state.busy);Text("相册来源为 AI 图片，仅作外观建议") }
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                notice?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
                state.tasks.forEach { task -> key(task.id) {
                    RecognitionTaskContent(task,state.busy || editor.isSaving,viewModel.currentRecognitionValues(),state.reviews[task.id],
                        onRemove={recognitionViewModel.removeRegion(task.id,it)},onReview={recognitionViewModel.saveReview(task.id,it)},
                        onSubmit={ recognitionViewModel.action {
                            viewModel.keepDraft()
                            confirm=recognitionViewModel.confirmation(task.id,viewModel.currentRecognitionValues(),family)
                            confirmRevision=recognitionViewModel.uiState.value.keyRevision
                        } },onCancel={recognitionViewModel.cancel(task.id)},onDiscard={recognitionViewModel.discard(task.id)},
                        onReprepare={recognitionViewModel.reprepare(task.id)},
                        onApply={review -> recognitionViewModel.apply(task.id,review,viewModel) { merge ->
                            notice="已填入 ${merge.acceptedIds.size} 项草稿"+(if(merge.conflicts.isNotEmpty()) "，${merge.conflicts.size} 项已变化或冲突，请核对" else "；保存物品后正式生效")
                        } },onRetain=::retain,
                        categories=editor.categories.map { it.id to it.name },brands=editor.brands.filterNot { it.isUnsetBrand }.map { it.id to it.name })
                } }
            }
        },confirmButton={TextButton(onClick={open=false},enabled=!state.busy){Text("回到编辑")}})
    }
    if(existing) {
        val listState=rememberLazyListState();ObserveSkinScroll(listState)
        val paths=(editor.imageUrls+listOfNotNull(editor.sizeChartImageUrl)+editor.sizeChartImageUrls).distinct()
        AlertDialog(onDismissRequest={existing=false},title={Text("选择本机图片")},text={
            LazyColumn(Modifier.heightIn(max=420.dp),state=listState) { itemsIndexed(paths) { index,path ->
                Row(Modifier.fillMaxWidth().clickable {
                    recognitionViewModel.action {
                        cropAppearanceOnly=appearanceOnly || recognitionViewModel.isAppearanceOnly(path)
                        cropSource=path;ownedSource=false;existing=false
                    }
                }.padding(skin.spacingMedium)) {
                    LolitaShimmerImage(path,"图片 ${index+1}",Modifier.size(72.dp),circularRevealEnabled=false)
                    Text("图片 ${index+1}",Modifier.padding(skin.spacingMedium))
                }
            } }
        },confirmButton={TextButton(onClick={existing=false}){Text("取消")}})
    }
    cropSource?.let { path -> ImageRegionCropDialog(path,onDismiss={closeCrop()},onCropped={crop ->
        val draftId=editor.draftId
        if(draftId==null || editor.isSaving) ImageFileHelper.cleanupUnreferencedImages(listOf(crop))
        else recognitionViewModel.addRegion(draftId,crop,viewModel.currentRecognitionValues(),family,cropAppearanceOnly)
        closeCrop()
    }) }
    confirm?.let { task ->
        val listState=rememberLazyListState();ObserveSkinScroll(listState)
        AlertDialog(onDismissRequest={confirm=null},title={Text("确认付费识图")},text={
        Column { Text("将向 ${task.request().provider.label}（${task.request().model}）发送这 ${task.regionImagePaths.size} 个实际区域。服务按账户计费，不会自动重试；结果不会自动保存物品。")
            LazyRow(state=listState) { items(task.regionImagePaths) { path -> LolitaShimmerImage(path,"将发送的裁切区域",Modifier.size(100.dp),ContentScale.Fit,circularRevealEnabled=false) } }
        }
    },confirmButton={Button(onClick={recognitionViewModel.submit(task,confirmRevision);confirm=null},enabled=state.hasKey && !state.busy){Text("确认发送并识别")}},
        dismissButton={TextButton(onClick={confirm=null}){Text("返回核对")}}) }
    if(savedEvidence) SavedEvidenceDialog(state.evidence,onDismiss={savedEvidence=false},onRetain=::retain)
}

@Composable
private fun RecognitionKeyDialog(provider:RecognitionProvider?,hasKey:Boolean,onProvider:(RecognitionProvider)->Unit,
    onDismiss:()->Unit,onSave:(String)->Unit,onClear:()->Unit) {
    var key by remember(provider) { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("识图服务设置")},text={Column {
        RecognitionProvider.entries.forEach { option -> Row(Modifier.fillMaxWidth().clickable {onProvider(option)}) {
            RadioButton(option==provider,{onProvider(option)});Text(option.label,Modifier.padding(top=12.dp))
        } }
        Text(provider?.let { "${it.model}。本机加密保存独立密钥，不进入备份。切换服务不会共用密钥。" } ?: "请选择识图服务，再填写独立密钥。")
        OutlinedTextField(key,{key=it},label={Text("${provider?.label ?: "识图服务"} API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=provider!=null)
        if(hasKey) TextButton(onClick=onClear){Text("移除此服务的本机密钥")}
    }},confirmButton={TextButton(onClick={onSave(key);key=""},enabled=provider!=null && key.isNotBlank()){Text("保存密钥")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable
private fun RecognitionTaskContent(task:RecognitionTask,busy:Boolean,current:Map<RecognitionField,String>,storedReview:RecognitionReview?,
    onRemove:(Int)->Unit,onReview:(RecognitionReview)->Unit,onSubmit:()->Unit,onCancel:()->Unit,onDiscard:()->Unit,
    onReprepare:()->Unit,onApply:(RecognitionReview)->Unit,onRetain:(String,Boolean)->Unit,
    categories:List<Pair<Long,String>>,brands:List<Pair<Long,String>>) {
    val skin=LolitaSkin.current
    val regionsState=rememberLazyListState();ObserveSkinScroll(regionsState)
    val validation=remember(task.requestJson,task.resultJson,task.reviewJson) { runCatching { task.validate();task.result() } }
    val result=validation.getOrNull()
    val request=remember(task.requestJson) { runCatching { task.request() }.getOrNull() }
    val initial=remember(task.id,task.resultJson) { runCatching { task.reviewJson?.let { RecognitionReview.decode(it,result?.candidates.orEmpty().map { row -> row.id }.toSet()) } }.getOrNull()
        ?: RecognitionReview(selectedIds=result?.candidates.orEmpty().filter { it.preselect(current[it.field].orEmpty()) }.map { it.id }.toSet()) }
    val review=storedReview ?: initial
    var evidence by remember { mutableStateOf<RecognitionCandidate?>(null) }
    val status=when(task.status) { RecognitionStatus.PREPARED->"待确认区域";RecognitionStatus.RUNNING->"正在识图";
        RecognitionStatus.AWAITING_REVIEW->"逐项核对建议";RecognitionStatus.APPLIED->"已采纳，可继续核对";
        RecognitionStatus.INTERRUPTED->"识图已中断";RecognitionStatus.FAILED->"识图未完成";RecognitionStatus.DISCARDED->"已舍弃" }
    LolitaCard(Modifier.fillMaxWidth()) { Column(verticalArrangement=Arrangement.spacedBy(skin.spacingSmall)) {
        SectionHeader(status)
        request?.let { Text("${it.provider.label} · ${it.model}",style=MaterialTheme.typography.bodySmall) }
        task.actualModel?.let { Text("返回模型：$it",style=MaterialTheme.typography.bodySmall) }
        LazyRow(state=regionsState) { itemsIndexed(task.regionImagePaths) { index,path -> Column(Modifier.width(104.dp)) {
            LolitaShimmerImage(path,"实际识别区域 ${index+1}",Modifier.size(100.dp),ContentScale.Fit,circularRevealEnabled=false)
            if(task.status==RecognitionStatus.PREPARED) TextButton(onClick={onRemove(index)},enabled=!busy){Text("移除此区域")}
        } } }
        task.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(validation.isFailure) Text("识图记录无法核验，请移除后重新框选。",color=MaterialTheme.colorScheme.error)
        if(task.status==RecognitionStatus.PREPARED && validation.isSuccess) Button(onClick=onSubmit,enabled=!busy){Text("核对区域与识图费用")}
        if(task.status in setOf(RecognitionStatus.INTERRUPTED,RecognitionStatus.FAILED) && validation.isSuccess) {
            Text("不会自动重发。保留的区域可重新核对，确认费用后才再次发送。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onReprepare,enabled=!busy){Text("重新核对这些区域")}
        }
        if(task.status==RecognitionStatus.RUNNING) { Row { CircularProgressIndicator(Modifier.size(20.dp));Text("等待服务返回，不会自动重试") };TextButton(onClick=onCancel,enabled=!busy){Text("取消本地等待") } }
        if(task.status in setOf(RecognitionStatus.AWAITING_REVIEW,RecognitionStatus.APPLIED) && result!=null && request!=null) {
            val variants=result.candidates.mapNotNull { it.variant }.distinct()
            val sizes=result.candidates.mapNotNull { it.sizeRow }.distinct()
            if(variants.isNotEmpty()) ReviewChoice("正在登记的款式",review.variant,variants,!busy) { onReview(review.copy(variant=it).forSelection(result.candidates)) }
            if(sizes.isNotEmpty()) ReviewChoice("实际购入尺码行",review.sizeRow,sizes,!busy) { onReview(review.copy(sizeRow=it).forSelection(result.candidates)) }
            result.candidates.forEach { candidate ->
                val value=review.edits[candidate.id] ?: candidate.value
                val sourceRestricted=candidate.regionIds.any { id -> request.regions.first { it.id==id }.appearanceOnly }
                val edited=candidate.copy(value=value)
                val eligible=edited.eligible(review.variant,review.sizeRow) && (!sourceRestricted || candidate.source==RecognitionSource.VISUAL && !candidate.field.textOnly)
                Column(Modifier.fillMaxWidth().padding(vertical=skin.spacingSmall)) {
                    Row {
                        Checkbox(candidate.id in review.selectedIds && eligible,onCheckedChange={checked -> onReview(review.copy(selectedIds=if(checked) review.selectedIds+candidate.id else review.selectedIds-candidate.id))},enabled=eligible && !busy)
                        Column { Text(candidate.field.label,style=MaterialTheme.typography.titleSmall);Text("当前：${current[candidate.field].orEmpty().ifBlank { "未设置" }}",style=MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedTextField(value,{onReview(review.copy(edits=review.edits+(candidate.id to it)))},Modifier.fillMaxWidth(),label={Text("识别建议，可修改")},enabled=!busy)
                    Text(candidate.source.label+(candidate.variant?.let { " · 适用 $it" } ?: "")+(candidate.sizeRow?.let { " · 尺码 $it" } ?: ""),style=MaterialTheme.typography.bodySmall)
                    if(!eligible) Text(when { sourceRestricted -> "AI 来源只可采纳外观建议";candidate.field==RecognitionField.PRICE_NOTE -> "仅供参考，不进入价格或实付";else -> "需对应款式、尺码及可核对的原文；数值还需明确对象、口径和单位" },style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={evidence=candidate}) { Text("查看原文与区域") }
                    if(candidate.field==RecognitionField.CATEGORY) ReferenceChoice("选择现有类型",review.resolvedCategoryId,categories,!busy) {onReview(review.copy(resolvedCategoryId=it))}
                    if(candidate.field==RecognitionField.BRAND) ReferenceChoice("选择现有品牌",review.resolvedBrandId,brands,!busy) {onReview(review.copy(resolvedBrandId=it))}
                }
            }
            Text("已有值与冲突项默认不选；应用时再次核对当前草稿。",style=MaterialTheme.typography.bodySmall)
            Button(onClick={onApply(review)},enabled=!busy && review.selectedIds.isNotEmpty()) { Text("应用所选资料到草稿") }
        }
        if(task.status!=RecognitionStatus.RUNNING) TextButton(onClick=onDiscard,enabled=!busy){Text("移除此识图记录")}
    } }
    evidence?.let { candidate -> EvidencePreview(candidate,candidate.regionIds.mapNotNull { region ->
        task.regionImagePaths.getOrNull(request?.regions?.indexOfFirst { it.id==region } ?: -1)
    },onDismiss={evidence=null},onRetain=onRetain) }
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
            Text("来源默认只保留为识图证据，以上操作会另加入编辑草稿。",style=MaterialTheme.typography.bodySmall)
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
