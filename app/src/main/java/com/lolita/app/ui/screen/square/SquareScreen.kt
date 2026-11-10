package com.lolita.app.ui.screen.square

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonElement
import com.lolita.app.data.square.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.metadata
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquareScreen(onEditDraft:(String)->Unit,onOpenItem:(Long)->Unit,viewModel:SquareViewModel=viewModel(),onOpenWishlist:()->Unit={}) {
    val catalog by viewModel.catalog.collectAsState()
    val showAmounts by viewModel.showAmounts.collectAsState(initial=false)
    val multi by viewModel.multiOpen.collectAsState()
    val wishResult by viewModel.wishResult.collectAsState()
    val eventId by viewModel.eventId.collectAsState();val choosing by viewModel.choosing.collectAsState()
    val busy by viewModel.busy.collectAsState();val preview by viewModel.preview.collectAsState()
    val message by viewModel.message.collectAsState();val registration by viewModel.registration.collectAsState()
    val snack=remember { SnackbarHostState() }
    val refreshing by viewModel.refreshing.collectAsState()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(viewModel::import)}
    fun pick(){picker.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream"))}
    LaunchedEffect(message){message?.let { snack.showSnackbar(it);viewModel.clearMessage() }}
    LaunchedEffect(registration){registration?.let { r->viewModel.consumedRegistration();r.itemId?.let(onOpenItem)?:r.draftId?.let(onEditDraft) }}
    val event=catalog?.let { c -> c.events.find { it.id==eventId && SquareVisibility.event(c, it) } }
    val selectedSku by viewModel.skuKey.collectAsState()
    val step by viewModel.step.collectAsState();val parts by viewModel.selectedParts.collectAsState()
    val unitJson by viewModel.unitsJson.collectAsState();val unitIndex by viewModel.unitIndex.collectAsState()
    val result by viewModel.editorResult.collectAsState()
    LaunchedEffect(result){viewModel.consumeEditorResult()}
    BackHandler(event!=null){if(busy==null)viewModel.back()}
    LaunchedEffect(wishResult){wishResult?.let { r->
        if(!r.related && r.draftIds.isEmpty() && !viewModel.wishWasMultiple() && r.itemIds.isNotEmpty()) {
            viewModel.clearWishResult()
            if(r.added==0)onOpenItem(r.itemIds.first())
        }
    }}
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),containerColor=androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost={SnackbarHost(snack) { data -> Snackbar(data, actionOnNewLine = true) }},bottomBar={
            if(event?.canRegister==true) {
                if(choosing) {
                    val units=remember(unitJson){viewModel.units()}
                    val label=when(step){0->"选择部件";1->"为物品选图";2->"查看登记预览";else->if(unitIndex>=units.size)"本次登记已完成" else "核对第 ${unitIndex+1} / ${units.size} 件"}
                    EditorSaveBar(label,busy!=null,busy==null && when(step){0->selectedSku!=null;1->parts.isNotEmpty();else->unitIndex<units.size},
                        {if(step<3)viewModel.next() else viewModel.prepare()})
                }
                else SquareWishDock(viewModel,onOpenItem,busy==null)
            }
        },topBar={GradientTopAppBar(title={Text(if(event==null)"广场" else if(choosing)listOf("选择完整规格","拆出我的物品","挑选对应资料","登记预览")[step.coerceIn(0,3)] else "商品详情")},
            navigationIcon={if(event!=null)IconButton(onClick=viewModel::back,enabled=busy==null){SkinIcon(IconKey.ArrowBack)}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                event==null->PullToRefreshBox(isRefreshing=refreshing,onRefresh=viewModel::refresh,modifier=Modifier.fillMaxSize().testTag("square-refresh")) {
                    if(catalog!=null)SquareBrowseFeed(catalog!!,viewModel,showAmounts)
                    else if(busy==null)SquareEmpty(::pick)
                }
                event!=null && choosing && step>0->SquareRegistrationScreen(catalog!!,event,viewModel)
                event!=null && choosing->SquareSelection(catalog!!,event,viewModel,busy!=null)
                event!=null && !event.canRegister->SquareTailDetail(catalog!!,event,viewModel,showAmounts,snack)
                event!=null->key(event.id){SquareBrowseDetail(catalog!!,event,viewModel,showAmounts,snack)}
            }
            if(busy!=null)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if(multi && catalog!=null && event?.canRegister==true)SquareWishSheet(catalog!!,viewModel)
    SquareWishResultSheet(viewModel,onOpenItem,onEditDraft,onOpenWishlist)
    preview?.let { p -> AlertDialog(onDismissRequest={if(busy==null)viewModel.cancelPreview()},
        title={Text("导入到广场")},text={Column(Modifier.verticalScrollCompat()){
            Text(p.catalog.title,style=MaterialTheme.typography.titleSmall);Spacer(Modifier.height(12.dp))
            Text("${p.catalog.shops.size} 家店 · ${p.catalog.events.size} 条动态\n${p.catalog.products.size} 个商品链接 · ${p.catalog.products.sumOf { it.skus.size }} 个完整规格\n${p.catalog.assets.size} 张去重原图")
            Spacer(Modifier.height(12.dp));Text(p.catalog.shops.joinToString(" · "){it.name})
            Text("采集快照：${p.catalog.capturedAt}",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp));Text("只更新广场资料，不覆盖衣橱。价格与库存为采集时快照；同一标识按版本更新，未出现的旧资料保留。",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick=viewModel::apply,enabled=busy==null){Text("导入到广场")}},
        dismissButton={TextButton(onClick=viewModel::cancelPreview,enabled=busy==null){Text("取消")}}) }
}

@Composable private fun Modifier.verticalScrollCompat()=this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable private fun SquareEmpty(onImport:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        SkinIcon(IconKey.Square,Modifier.size(80.dp),MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp));Text("暂无店铺资料",style=MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp));Button(onClick=onImport,shape=LolitaSkin.current.cardShape,modifier=Modifier.fillMaxWidth().testTag("square-import")){Text("导入资料包")}
    }
}

@Composable private fun Choices(values:List<String>,selected:String,onSelect:(String)->Unit,label:String,allowAll:Boolean=true){
    Column {
        if(label.isNotEmpty())Text(label,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(allowAll)FilterChip(selected=selected.isEmpty(),onClick={onSelect("")},label={Text("全部")})
            values.distinct().forEach { v-> FilterChip(selected=selected==v,onClick={onSelect(v)},label={Text(v)}) }
        }
    }
}

@Composable private fun SquareSelection(c:SquareCatalog,e:SquareEvent,vm:SquareViewModel,busy:Boolean){
    val pid by vm.productId.collectAsState();val key by vm.skuKey.collectAsState();val partKey by vm.partKey.collectAsState()
    val products=SquareVisibility.products(c,e).filter{it.pricePhase!="BALANCE"}
    val p=products.find{it.id==pid}?:products.first()
    val selected=p.skus.find{it.key==key}
    var search by rememberSaveable(e.id,p.id){mutableStateOf("")}
    var contentFilter by rememberSaveable(e.id,p.id){mutableStateOf("")}
    var colorFilter by rememberSaveable(e.id,p.id){mutableStateOf("")}
    var sizeFilter by rememberSaveable(e.id,p.id){mutableStateOf("")}
    val candidates=p.skus.filter{(contentFilter.isEmpty()||it.content==contentFilter)&&(colorFilter.isEmpty()||it.color==colorFilter)&&(sizeFilter.isEmpty()||it.size==sizeFilter)}
    val state=androidx.compose.foundation.lazy.rememberLazyListState();ObserveSkinScroll(state)
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("square-selection"),state=state,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { Text(e.title,style=MaterialTheme.typography.headlineSmall);Text("先选择一个实际完整规格，再进入服饰编辑核对。",style=MaterialTheme.typography.bodyMedium) }
        if(products.size>1)item { LolitaSection("商品链接与购买内容") { row { ProductChoices(products,p.id,vm::selectProduct) } } }
        item { Column {
            Choices(p.skus.map{it.content},contentFilter,{contentFilter=it;colorFilter="";sizeFilter=""},"购买内容")
            Choices(p.skus.filter{contentFilter.isEmpty()||it.content==contentFilter}.mapNotNull{it.color},colorFilter,{colorFilter=it;sizeFilter=""},"颜色")
            Choices(p.skus.filter{(contentFilter.isEmpty()||it.content==contentFilter)&&(colorFilter.isEmpty()||it.color==colorFilter)}.mapNotNull{it.size},sizeFilter,{sizeFilter=it},"尺码")
        } }
        item {OutlinedTextField(value=search,onValueChange={search=it},label={Text("搜索款式、颜色或尺码")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        if(selected!=null)item {LolitaSection("已选规格") {row{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(selected.rawProperties.entries.joinToString(" · "){"${it.key}：${it.value}"})
            Text("${p.priceType}：${squarePrice(selected.price)} · ${selected.stock}",style=MaterialTheme.typography.bodySmall)
            if(selected.styleFacts.isNotEmpty())Text(selected.styleFacts.joinToString("；"){"${it.value}（${it.source}）"})
            Text("下一步可勾选部件，分别登记或自由组合，并挑选每件的图片与资料。",style=MaterialTheme.typography.bodySmall)
            Text("默认保存为心愿；不建立订单或实付。来源报价保留在说明中，不自动录价。",style=MaterialTheme.typography.bodySmall)

        }}}}
        item {SectionHeader("符合条件的SKU · ${candidates.size} 个")}
        items(candidates.filter{search.isBlank()||it.props.values.joinToString(" ").contains(search,true)},key={it.key}){sku->
            LolitaCard(variant=CardVariant.COMPACT,modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){vm.selectSku(sku.key)}){
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected?.key==sku.key,{vm.selectSku(sku.key)},enabled=!busy)
                    Column(Modifier.weight(1f)){Text(sku.props.entries.joinToString(" · "){it.value},style=MaterialTheme.typography.titleSmall)
                        Text("${sku.form} · ${sku.stock} · ${p.priceType} ${squarePrice(sku.price)}",style=MaterialTheme.typography.bodySmall)}}
            }
        }
    }
}

private fun infoLabel(key:String)=mapOf("styles" to "风格及依据","season" to "适用季节","components" to "部件说明","included" to "包含内容","excluded" to "不包含","gifts" to "赠品条件","care" to "洗护","materials" to "材质","craft" to "工艺","sizes" to "尺码资料")[key]?:key
private fun readable(e:JsonElement):String=squareInfoText(e)

@Composable private fun ProductChoices(products:List<SquareProduct>,selectedId:String,onSelect:(String)->Unit){
    val duplicates=products.groupingBy{it.priceType}.eachCount()
    Column { products.forEach { p ->
        Row(Modifier.fillMaxWidth().clickable{onSelect(p.id)},verticalAlignment=Alignment.CenterVertically){
            RadioButton(selectedId==p.id,{onSelect(p.id)})
            Column(Modifier.weight(1f).padding(vertical=8.dp)){
                if(duplicates.getValue(p.priceType)>1)Text(p.title,style=MaterialTheme.typography.titleSmall)
                Text(p.priceType,style=MaterialTheme.typography.bodyMedium)
            }
        }
    } }
}


/** NavBackStackEntry's result handle is distinct from the ViewModel's own saved state. */
@Composable
fun SquareEditorResult(entry:androidx.navigation.NavBackStackEntry,vm:SquareViewModel) {
    val result by entry.savedStateHandle.getStateFlow<String?>("square_editor_result",null).collectAsState()
    LaunchedEffect(result) { result?.let { vm.editorFinished(it);entry.savedStateHandle.set<String?>("square_editor_result",null) } }
}
