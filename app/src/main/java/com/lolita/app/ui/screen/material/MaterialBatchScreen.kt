package com.lolita.app.ui.screen.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolita.app.domain.model.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.tasks.label
import com.lolita.app.ui.screen.tasks.ImageTaskQueueActions
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialBatchScreen(viewModel: MaterialBatchViewModel,onBack:()->Unit,onTasks:()->Unit,onConfigure:()->Unit,
    onNewBatch:()->Unit,onOpenTask:(String)->Unit) {
    val s by viewModel.uiState.collectAsState();val skin=LolitaSkin.current
    val scroll=rememberLazyListState();ObserveSkinScroll(scroll)
    var familyPicker by remember{mutableStateOf(false)}
    var zoom by remember{mutableStateOf<String?>(null)}
    var sourcePicker by remember{mutableStateOf<MaterialBatchRow?>(null)}
    var referenceRow by remember{mutableStateOf<MaterialBatchRow?>(null)}
    var cropRow by remember{mutableStateOf<MaterialBatchRow?>(null)}
    var layerRow by remember{mutableStateOf<MaterialBatchRow?>(null)}
    var saveRows by remember{mutableStateOf<List<BatchMaterialConfirmation>?>(null)}
    var cancelIds by remember{mutableStateOf<List<String>?>(null)}
    var darkChecker by rememberSaveable{mutableStateOf(false)}
    val selected=s.selectedRows
    val retryLayers=s.phase==MaterialBatchPhase.LAYERS && selected.isNotEmpty() && selected.all{it.canRetry}
    val saving=s.phase==MaterialBatchPhase.LAYERS && !retryLayers
    val needsService=s.phase!=MaterialBatchPhase.SAVED && !saving
    val configured=s.keyLoaded && s.hasKey && s.provider==ImageGenerationProvider.SEEDREAM
    val canSubmit=!s.busy && !s.loading && !s.loadFailed && when {
        s.phase==MaterialBatchPhase.SAVED->true
        selected.isEmpty() || selected.any{!s.selectable(it)}->false
        saving->viewModel.saveSnapshot()!=null
        else->s.keyLoaded
    }
    val primary=when {
        s.phase==MaterialBatchPhase.SAVED->"继续整理其他物品"
        needsService && !configured->if(s.provider==ImageGenerationProvider.SEEDREAM)"配置即梦服务" else "切换到即梦服务"
        saving->"确认并保存${selected.size}份素材"
        s.phase==MaterialBatchPhase.INPUTS->"核对${selected.size}件主视输入与费用"
        else->"核对${selected.size}件分层输入与费用"
    }
    Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        GradientTopAppBar(title={Text("批量生成展示图")},navigationIcon={IconButton(onClick=onBack){SkinIcon(IconKey.ArrowBack,Modifier.semantics{contentDescription="返回"})}},
            actions={IconButton(onClick=onConfigure){SkinIcon(IconKey.Settings,Modifier.semantics{contentDescription="图像服务配置"})};IconButton(onClick=onTasks){SkinIcon(IconKey.ViewAgenda,Modifier.semantics{contentDescription="任务中心"})}})
    },bottomBar={Surface(color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=skin.spacingLarge,vertical=skin.spacingMedium)){
        Text(if(s.busy)"正在处理本批 · 已准备${s.prepared}件实际输入" else if(saving)"勾选后核对三项；本地保存不计费。" else "本次已选${selected.size}件；按阶段整批确认，可离页处理。",style=MaterialTheme.typography.bodySmall)
        Button(onClick={when{ s.phase==MaterialBatchPhase.SAVED->onNewBatch();needsService && !configured->onConfigure();saving->{viewModel.saveSnapshot()?.let{if(it.any{r->r.expectedMaterialId!=null})saveRows=it else viewModel.save(it)}};else->viewModel.prepareFee()}},enabled=canSubmit,modifier=Modifier.fillMaxWidth().testTag("batchPrimary")){
            if(s.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(skin.spacingMedium))};Text(if(s.busy)"正在处理…" else primary)
        }
    }}}){padding->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)){
            val columns=if(maxWidth<360.dp || LocalDensity.current.fontScale>1.3f)1 else 2
            LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().testTag("materialBatch"),contentPadding=PaddingValues(skin.spacingLarge),verticalArrangement=Arrangement.spacedBy(skin.cardGap)){
                item("phases"){FlowRow(horizontalArrangement=Arrangement.spacedBy(skin.spacingSmall)){
                    MaterialBatchPhase.entries.forEach{phase->FilterChip(selected=s.phase==phase,onClick={viewModel.phase(phase)},enabled=!s.busy,label={Text(phase.label)})}
                }}
                if(s.loading)item("loading"){LolitaCard{Column{ShimmerRect(160.dp,180.dp,modifier=Modifier.fillMaxWidth());ShimmerLine(0.8f,18.dp)}}}
                s.error?.let{item("error"){LolitaSection(title="操作提示"){row{Text(it,color=MaterialTheme.colorScheme.error)}}}}
                s.message?.let{item("message"){Text(it,style=MaterialTheme.typography.bodyMedium)}}
                if(!s.draft)item("queue-actions"){
                    ImageTaskQueueActions(s.jobs,s.busy || s.loading || s.loadFailed,viewModel::stopQueued,viewModel::discardPending)
                }
                if(!s.draft)item("summary"){LolitaSection(title=s.name){row{
                    val count=s.jobs.map{it.batchPosition}.distinct().size
                    val pending=s.jobs.count{it.status==ImageTaskStatus.AWAITING_CONFIRMATION}
                    Text("本批${count}件 · ${s.active.size}个排队或执行 · ${pending}个待核对",style=MaterialTheme.typography.bodyMedium)
                    val latest=s.latestJobs
                    Text("${latest.count{it.materialId!=null}}份已确认 · ${latest.count{it.status in setOf(ImageTaskStatus.FAILED,ImageTaskStatus.INTERRUPTED,ImageTaskStatus.CANCELLED)}}件失败或中断",style=MaterialTheme.typography.bodySmall)
                    if(s.active.isNotEmpty()){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={cancelIds=s.active.map{it.id}},enabled=!s.busy){Text("停止本批未完成任务")}}
                    Text("单件失败不阻塞其他结果；不会自动重试或连续付费。",style=MaterialTheme.typography.bodySmall)
                }}}
                item("instructions"){LolitaSection(title=when(s.phase){MaterialBatchPhase.INPUTS->if(s.draft)"一次选择，多件一起整理" else "原始输入与失败主视图";MaterialBatchPhase.FRONT->"集中核对主视图";MaterialBatchPhase.LAYERS->"集中核对透明单品";else->"本批当前确认素材"}){row{
                    Text(when(s.phase){MaterialBatchPhase.INPUTS->if(s.draft)"每批主动选择1至30件已拥有或心愿服饰和小物，逐卡可换图和框选。" else "只选择失败、中断或取消的主视图重新处理。成功结果继续保留。";MaterialBatchPhase.FRONT->"勾选已查看且主视结构正确的结果，一次确认进入分层。原透视摆正后对称可以合理。";MaterialBatchPhase.LAYERS->"每件选择包含完整附属部件的一层，勾选并完成三项核对后整批保存。失败项可单独重新确认分层。";else->"已保存素材可继续从物品详情用于试衣；本轮工作流到素材保存结束。"},style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value=s.query,onValueChange=viewModel::query,label={Text("搜索物品")},placeholder={Text("名称／品牌／类型")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("batchSearch"))
                    OutlinedButton(onClick={familyPicker=true},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("batchFamily")){Text(s.family?.label ?: "全部品类")}
                    if(s.phase==MaterialBatchPhase.INPUTS && s.draft)BatchCheck("只看尚无确认素材",s.onlyMissing,!s.busy,viewModel::onlyMissing)
                    Text("已选${selected.size}件，其中${selected.count{it !in s.visibleRows}}件在当前筛选外。筛选保留选择。",style=MaterialTheme.typography.bodySmall)
                    if(!s.draft)FlowRow(horizontalArrangement=Arrangement.spacedBy(skin.spacingSmall)){
                        if(s.phase in setOf(MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS))TextButton(onClick=viewModel::selectReady,enabled=!s.busy){Text("仅选择待核对项")}
                        if(s.phase in setOf(MaterialBatchPhase.INPUTS,MaterialBatchPhase.LAYERS))TextButton(onClick=viewModel::selectFailed,enabled=!s.busy){Text("仅选择失败项")}
                    }
                    if(s.phase in setOf(MaterialBatchPhase.LAYERS,MaterialBatchPhase.SAVED))FlowRow(horizontalArrangement=Arrangement.spacedBy(skin.spacingSmall)){
                        FilterChip(selected=!darkChecker,onClick={darkChecker=false},label={Text("浅底检查")});FilterChip(selected=darkChecker,onClick={darkChecker=true},label={Text("深底检查")})
                    }
                }}}
                if(s.visibleRows.isEmpty() && !s.loading)item("empty"){LolitaSection(title="暂无可显示物品"){row{Text(if(s.draft)"请先确认物品为已拥有／心愿服饰或小物、核对类型并添加参考图片；也可清除搜索或品类筛选。" else "本阶段尚无结果。其他任务会继续排队，完成后在这里集中处理。")}}}
                items(s.visibleRows.chunked(columns),key={rows->s.phase.name+rows.joinToString{it.key}}){rows->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(skin.spacingMedium)){
                    rows.forEach{row->Column(Modifier.weight(1f).testTag("batchRow${row.itemId}")){LolitaCard{Column(verticalArrangement=Arrangement.spacedBy(skin.spacingSmall)){
                        Text(row.name,style=MaterialTheme.typography.titleSmall)
                        val path=if(s.phase==MaterialBatchPhase.SAVED)row.material?.imagePath else row.image
                        path?.takeIf(String::isNotBlank)?.let{MaterialPhoto(it,"放大第${row.position+1}件：${row.name}",{zoom=it},if(columns==1)210 else 135,checker=s.phase in setOf(MaterialBatchPhase.LAYERS,MaterialBatchPhase.SAVED),darkChecker=darkChecker)}
                        Text(listOf(row.categoryName, row.item?.composition?.label.orEmpty()).filter(String::isNotBlank).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        if(row.brand.isNotBlank())Text(row.brand,style=MaterialTheme.typography.bodySmall)
                        row.task?.let{task->Text(task.status.label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary);task.error?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}}
                        if((!row.eligible || !row.matchesTask && s.phase in setOf(MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS)) && s.phase!=MaterialBatchPhase.SAVED)Text("原物品已删除或类型／组合／状态已改变",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        if(s.phase==MaterialBatchPhase.INPUTS){
                            TextButton(onClick={referenceRow=row},enabled=!s.busy && row.item!=null && (s.draft || row.canRetry),modifier=Modifier.fillMaxWidth()){Text("调整参考图")}
                        }
                        if(s.phase==MaterialBatchPhase.LAYERS && row.layers.size>1)OutlinedButton(onClick={layerRow=row},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("选择单品层（${row.layers.size}层）")}
                        if(s.phase in setOf(MaterialBatchPhase.FRONT,MaterialBatchPhase.LAYERS)){
                            TextButton(onClick={row.task?.let{ImageTaskRequest.decode(it.requestJson).materialSpec?.originalInputPath}?.let{zoom=it}},modifier=Modifier.fillMaxWidth()){Text("对照原输入")}
                        }
                        if(s.phase==MaterialBatchPhase.SAVED) {
                            Text(if(row.material?.sourceTaskId in s.jobs.map{it.id})"本批已确认 · AI整理" else "当前原素材 · 新候选尚未保存",style=MaterialTheme.typography.bodySmall)
                            row.material?.let { material ->
                                val isCover = row.item?.imageUrls?.firstOrNull() == material.imagePath
                                OutlinedButton(onClick={viewModel.setCover(row.itemId,material.id)},enabled=!s.busy && !isCover && material.matchesSource(row.semantic,row.item?.composition ?: ItemComposition.UNCONFIRMED),modifier=Modifier.fillMaxWidth().testTag("batchSetCover${row.itemId}")){Text(if(isCover)"当前封面" else "设为封面")}
                            }
                        } else BatchCheck(if(s.phase==MaterialBatchPhase.FRONT)"主视结构正确，继续分层" else if(row.canRetry)"重新处理此项" else "本次处理",row.key in s.selected,!s.busy && s.selectable(row)){viewModel.toggle(row.key,it)}
                        row.task?.let{task->TextButton(onClick={onOpenTask(task.id)},modifier=Modifier.fillMaxWidth()){Text("任务详情")}}
                    }}}}
                    if(rows.size<columns)Spacer(Modifier.weight(1f))
                }}
                if(s.phase==MaterialBatchPhase.INPUTS)item("requirements"){LolitaSection(title="本批整理要求"){row{MaterialInstructions(s.instructions,!s.busy,viewModel::instructions);Text("只影响这次勾选的新请求，已提交任务继续使用原快照。",style=MaterialTheme.typography.bodySmall)}}}
                if(s.phase==MaterialBatchPhase.LAYERS && !retryLayers)item("checks"){LolitaSection(title="核对本次勾选的${selected.size}件"){row{
                    listOf("各自只包含该物品及附属部件","造型、颜色和部件数量正确","没有残留人台、文字或其他服饰").forEachIndexed{index,label->BatchCheck(label,index in s.checks,!s.busy){viewModel.check(index,it)}}
                    if(selected.any{it.canRetry})Text("待核对结果与失败项请分开选择；可用上方按钮明确选择一组。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Text("一条物品可为单件、成双或成组，连同附属部件整体核对；不补造成对，透明性通过不等于结构正确。",style=MaterialTheme.typography.bodySmall)
                }}}
            }
        }
    }
    if(familyPicker)MaterialFamilyPicker(s.family,{viewModel.family(it);familyPicker=false},{familyPicker=false})
    s.fee?.let{fee->MaterialBatchFeeDialog(fee,s.busy,viewModel::dismissFee,{viewModel.submitFee(fee)},{zoom=it})}
    sourcePicker?.let{row->row.item?.let{item->MaterialSourcePicker(item,row.original,{viewModel.selectSource(row.itemId,it);sourcePicker=null},{sourcePicker=null})}}
    referenceRow?.let{row->AlertDialog(onDismissRequest={referenceRow=null},title={Text("${row.name} · 参考图")},text={Column{
        Text("可更换商品／详情图，或框选这件物品。只发送最终选中的一张或选区，原图保留。")
        OutlinedButton(onClick={referenceRow=null;sourcePicker=row},modifier=Modifier.fillMaxWidth()){Text("更换参考图")}
        OutlinedButton(onClick={referenceRow=null;cropRow=row},enabled=row.original in row.item?.productReferenceImages.orEmpty(),modifier=Modifier.fillMaxWidth()){Text("框选当前参考图")}
    }},confirmButton={TextButton(onClick={referenceRow=null}){Text("返回")}})}
    cropRow?.let{row->ImageRegionCropDialog(row.original,onDismiss={cropRow=null},onCropped={viewModel.crop(row.itemId,row.original,it);cropRow=null})}
    layerRow?.let{row->val latest=s.rows.firstOrNull{it.key==row.key} ?: row;BatchLayerPicker(latest,{viewModel.layer(row.key,it);layerRow=null},{layerRow=null})}
    saveRows?.let{rows->AlertDialog(onDismissRequest={saveRows=null},title={Text("保存并替换本次素材？")},text={Text("本次保存${rows.size}份，其中${rows.count{it.expectedMaterialId!=null}}份会替换当前确认素材。请在卡片中对照新结果；原商品照片与已提交任务仍保留。")},confirmButton={TextButton(onClick={saveRows=null;viewModel.save(rows)}){Text("确认批量保存")}},dismissButton={TextButton(onClick={saveRows=null}){Text("返回核对")}})}
    cancelIds?.let{ids->AlertDialog(onDismissRequest={cancelIds=null},title={Text("停止本批${ids.size}个未完成任务？")},text={Text("已完成预览和已确认素材保留。已发送的请求可能仍处理或计费，停止后不会自动重发。")},confirmButton={TextButton(onClick={cancelIds=null;viewModel.cancel(ids)}){Text("停止本批")}},dismissButton={TextButton(onClick={cancelIds=null}){Text("继续等待")}})}
    zoom?.let{FullScreenImageViewer(listOf(it),0,{zoom=null})}
}

@Composable
private fun BatchCheck(label:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().toggleable(checked,enabled,Role.Checkbox,onChange).padding(vertical=LolitaSkin.current.spacingSmall),verticalAlignment=Alignment.CenterVertically){Checkbox(checked,null,enabled=enabled);Text(label,Modifier.weight(1f).padding(start=LolitaSkin.current.spacingSmall),style=MaterialTheme.typography.bodyMedium)}}

@Composable
private fun BatchLayerPicker(row:MaterialBatchRow,onPick:(String)->Unit,onDismiss:()->Unit){val list=rememberLazyListState();ObserveSkinScroll(list)
    AlertDialog(onDismissRequest=onDismiss,title={Text("${row.name} · 选择完整单品层")},text={LazyColumn(state=list,modifier=Modifier.heightIn(max=480.dp)){
        items(row.layers,key={it.imagePath}){layer->Column{MaterialPhoto(layer.imagePath,layer.name,{onPick(layer.imagePath)},140,checker=true);TextButton(onClick={onPick(layer.imagePath)},modifier=Modifier.fillMaxWidth()){Text("选择${layer.name.ifBlank{"图层${layer.zIndex}"}}")}}}
    }},confirmButton={TextButton(onClick=onDismiss){Text("返回")}})
}

@Composable
private fun MaterialBatchFeeDialog(fee:MaterialBatchFee,busy:Boolean,onDismiss:()->Unit,onSend:()->Unit,onZoom:(String)->Unit){
    val skin=LolitaSkin.current;val list=rememberLazyListState();ObserveSkinScroll(list);var checked by remember(fee){mutableStateOf(false)}
    Dialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Scaffold(modifier=Modifier.testTag("batchFeeRoot"),containerColor=MaterialTheme.colorScheme.surface,contentWindowInsets=WindowInsets(0,0,0,0),topBar={GradientTopAppBar(title={Text("本批发送前确认")},navigationIcon={IconButton(onClick={if(!busy)onDismiss()}){SkinIcon(IconKey.ArrowBack)}})},bottomBar={Column(Modifier.fillMaxWidth().padding(skin.spacingLarge)){
            Button(onClick=onSend,enabled=checked && !busy,modifier=Modifier.fillMaxWidth().testTag("batchFeeSend")){Text("确认本批${fee.rows.size}次请求")};TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("返回修改")}
        }}){padding->LazyColumn(state=list,modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("batchFeeInputs"),contentPadding=PaddingValues(skin.spacingLarge),verticalArrangement=Arrangement.spacedBy(skin.cardGap)){
            item{LolitaSection(title="${fee.provider.recipient} · ${fee.provider.label}"){row{
                Text("${fee.rows.size}件物品 · ${fee.rows.size}次${if(fee.phase==MaterialBatchPhase.INPUTS)"主视生成" else "分层"}请求",style=MaterialTheme.typography.titleMedium)
                Text(if(fee.phase==MaterialBatchPhase.INPUTS)"本次最多输出${fee.rows.size}张主视图。返回后集中核对，再另行确认分层费用。" else "每次含底图和最多16个透明层，本批最多${fee.rows.size*17}张成功输出。按实际输出张数计费，不能预先确认金额。")
                Text("API按服务资费计费。未选相册、订单、价格、位置与身体参数不发送。",style=MaterialTheme.typography.bodySmall)
            }}}
            itemsIndexed(fee.rows,key={_,row->row.itemId}){index,row->LolitaSection(title="实际输入${index+1} · ${row.name}"){row{MaterialPhoto(row.input.imagePath,"放大实际输入${index+1}",{onZoom(row.input.imagePath)},190)}}}
            item{LolitaSection(title="处理边界"){row{if(fee.instructions.isNotBlank())Text("本批补充要求：${fee.instructions}");Text("全部验证并入队后才开始。单件失败不阻塞其他任务，不自动重试，也不自动进入下一次付费阶段。取消或超时后服务仍可能计费。")}}}
            item{BatchCheck("已核对本批全部实际输入、接收方和费用范围",checked,!busy){checked=it}}
        }}
    }
}

@Composable
private fun MaterialFamilyPicker(selected:WardrobeFamily?,onSelect:(WardrobeFamily?)->Unit,onDismiss:()->Unit){
    val list=rememberLazyListState();ObserveSkinScroll(list)
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择品类")},text={LazyColumn(state=list,modifier=Modifier.heightIn(max=440.dp)){
        item{TextButton(onClick={onSelect(null)},modifier=Modifier.fillMaxWidth()){Text(if(selected==null)"全部品类 · 已选" else "全部品类")}}
        items(WardrobeFamily.entries.filter{it!=WardrobeFamily.UNCONFIRMED}){family->TextButton(onClick={onSelect(family)},modifier=Modifier.fillMaxWidth()){Text(family.label+if(selected==family)" · 已选" else "")}}
    }},confirmButton={TextButton(onClick=onDismiss){Text("返回")}})
}
