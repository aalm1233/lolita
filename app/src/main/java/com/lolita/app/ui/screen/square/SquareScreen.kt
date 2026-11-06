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

@Composable
fun SquareScreen(onEditDraft:(String)->Unit,onOpenItem:(Long)->Unit,viewModel:SquareViewModel=viewModel()) {
    val catalog by viewModel.catalog.collectAsState()
    val eventId by viewModel.eventId.collectAsState();val choosing by viewModel.choosing.collectAsState()
    val busy by viewModel.busy.collectAsState();val preview by viewModel.preview.collectAsState()
    val message by viewModel.message.collectAsState();val registration by viewModel.registration.collectAsState()
    val snack=remember { SnackbarHostState() }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(viewModel::import)}
    fun pick(){picker.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream"))}
    LaunchedEffect(message){message?.let { snack.showSnackbar(it);viewModel.clearMessage() }}
    LaunchedEffect(registration){registration?.let { r->viewModel.consumedRegistration();r.itemId?.let(onOpenItem)?:r.draftId?.let(onEditDraft) }}
    val event=catalog?.events?.find { it.id==eventId }
    val selectedSku by viewModel.skuKey.collectAsState()
    val step by viewModel.step.collectAsState();val parts by viewModel.selectedParts.collectAsState()
    val unitJson by viewModel.unitsJson.collectAsState();val unitIndex by viewModel.unitIndex.collectAsState()
    val result by viewModel.editorResult.collectAsState()
    LaunchedEffect(result){viewModel.consumeEditorResult()}
    BackHandler(event!=null && busy==null){viewModel.back()}
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),containerColor=androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost={SnackbarHost(snack)},bottomBar={
            if(event?.canRegister==true) {
                if(choosing) {
                    val units=remember(unitJson){viewModel.units()}
                    val label=when(step){0->"选择部件";1->"为物品选图";2->"查看登记预览";else->if(unitIndex>=units.size)"本次登记已完成" else "核对第 ${unitIndex+1} / ${units.size} 件"}
                    EditorSaveBar(label,busy!=null,busy==null && when(step){0->selectedSku!=null;1->parts.isNotEmpty();else->unitIndex<units.size},
                        {if(step<3)viewModel.next() else viewModel.prepare()})
                }
                else EditorSaveBar("选择规格，加入衣橱",false,busy==null,viewModel::choose)
            }
        },topBar={GradientTopAppBar(title={Text(if(event==null)"广场" else if(choosing)listOf("选择完整规格","拆出我的物品","挑选对应资料","登记预览")[step.coerceIn(0,3)] else "商品动态")},
            navigationIcon={if(event!=null)IconButton(onClick=viewModel::back,enabled=busy==null){SkinIcon(IconKey.ArrowBack)}},
            actions={if(event==null)TextButton(onClick=::pick,enabled=busy==null && preview==null,colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)){Text("导入资料包")}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                catalog==null && busy==null->SquareEmpty(::pick)
                catalog!=null && event==null->SquareFeed(catalog!!,viewModel)
                event!=null && choosing && step>0->SquareRegistrationScreen(catalog!!,event,viewModel)
                event!=null && choosing->SquareSelection(catalog!!,event,viewModel,busy!=null)
                event!=null->SquareDetail(catalog!!,event,viewModel)
            }
            if(busy!=null)Surface(Modifier.fillMaxWidth().align(Alignment.TopCenter),tonalElevation=6.dp){Column(Modifier.padding(20.dp)){
                Text(busy!!,style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth())
            }}
        }
    }
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
    Column(Modifier.fillMaxSize().padding(28.dp).verticalScrollCompat(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        SkinIcon(IconKey.Square,Modifier.size(100.dp),MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp));Text("喜欢的店铺，慢慢看",style=MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp));Text("导入一份广场资料包，查看上新、再贩、现货与尾款。",style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp));Button(onClick=onImport,modifier=Modifier.fillMaxWidth().testTag("square-import")){Text("导入本地资料包")}
        Spacer(Modifier.height(12.dp));Text("仅添加店铺资料。选择商品后，再登记到心愿。",style=MaterialTheme.typography.bodySmall)
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

@Composable private fun SquareFeed(c:SquareCatalog,vm:SquareViewModel){
    val query by vm.query.collectAsState();val kind by vm.kind.collectAsState();val shop by vm.shop.collectAsState()
    val color by vm.color.collectAsState();val style by vm.style.collectAsState();val form by vm.form.collectAsState()
    var filters by rememberSaveable { mutableStateOf(false) };var shops by remember { mutableStateOf(false) }
    val products=remember(c){c.products.associateBy { it.id }}
    val allSkus=remember(c){c.products.flatMap { it.skus }}
    val events=remember(c,query,kind,shop,color,style,form){c.events.filter { e->
        (kind.isEmpty()||e.kind==kind)&&(shop.isEmpty()||e.shopId==shop)&&
        (query.isBlank() || (e.title+e.subtitle+c.shops.find{it.id==e.shopId}?.name+e.productIds.joinToString { products.getValue(it).title }).contains(query,true))&&
        e.productIds.any { id -> products.getValue(id).skus.any { it.matches(color,style,form) } }
    }}
    val state=androidx.compose.foundation.lazy.rememberLazyListState();ObserveSkinScroll(state)
    LazyColumn(Modifier.fillMaxSize().testTag("square-feed"),state=state,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item { Column {
            OutlinedTextField(value=query,onValueChange={vm.set("query",it)},placeholder={Text("搜索店铺或商品")},
                leadingIcon={SkinIcon(IconKey.Search,Modifier.size(20.dp))},singleLine=true,
                shape=LolitaSkin.current.cardShape,modifier=Modifier.fillMaxWidth().testTag("square-search"))
            val kinds=listOf("全部","上新","再贩","现货","尾款")
            ScrollableTabRow(selectedTabIndex=kinds.indexOf(kind.ifEmpty{"全部"}).coerceAtLeast(0),edgePadding=0.dp,containerColor=androidx.compose.ui.graphics.Color.Transparent){
                kinds.forEach { label->Tab(selected=kind==label || (kind.isEmpty()&&label=="全部"),onClick={vm.set("kind",if(label=="全部")"" else label)},text={Text(label,style=MaterialTheme.typography.labelLarge)}) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={shops=true}){Text(c.shops.find{it.id==shop}?.name?:"全部店铺");SkinIcon(IconKey.ArrowDropDown,Modifier.size(18.dp))}
                TextButton(onClick={filters=!filters}){SkinIcon(IconKey.FilterList,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("颜色 · 风格 · 内容"+(if(color+style+form!="")" ✓" else ""),style=MaterialTheme.typography.bodySmall)}
            }
            if(filters){Choices(allSkus.mapNotNull { it.color },color,{vm.set("color",it)},"颜色")
                Choices(allSkus.flatMap { it.styleFacts }.map { it.value },style,{vm.set("style",it)},"风格（明确来源）")
                Choices(listOf("单品","小物","套装","组合"),form,{vm.set("form",it)},"购买内容")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("店铺手记",style=MaterialTheme.typography.titleMedium)
                Text("${c.shops.size} 家店 · 采集快照",style=MaterialTheme.typography.bodySmall)
            }
        } }
        if(events.isEmpty()) item { LolitaCard { Column { Text("没有符合条件的动态");TextButton(onClick={listOf("query","kind","shop","color","style","form").forEach{vm.set(it,"")}}){Text("清除搜索和筛选")} } } }
        items(events,key={it.id}){e->val p=products.getValue(e.productIds.first());val cover=p.images.first{it.role=="main"}
            LolitaCard(variant=CardVariant.GALLERY,contentPadding=PaddingValues(0.dp),modifier=Modifier.fillMaxWidth().clickable{vm.open(e.id)}){
                Column {
                    LolitaShimmerImage(model=vm.image(cover.path),contentDescription=e.title,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(180.dp),circularRevealEnabled=false)
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text("${c.shops.first{it.id==e.shopId}.name} · ${e.kind} · ${e.state}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                        Text(e.title,style=MaterialTheme.typography.headlineSmall);Text(e.subtitle,style=MaterialTheme.typography.bodySmall)
                        Text(e.date,style=MaterialTheme.typography.bodySmall)
                        Text(e.price,style=MaterialTheme.typography.titleSmall)
                        TextButton(onClick={vm.open(e.id)}){Text(if(e.canRegister)"查看商品与规格" else "查看尾款说明")}
                    }
                }
            }
        }
    }
    if(shops)AlertDialog(onDismissRequest={shops=false},title={Text("选择店铺")},text={Column{
        TextButton(onClick={vm.set("shop","");shops=false}){Text("全部店铺")}
        c.shops.forEach { item->TextButton(onClick={vm.set("shop",item.id);shops=false}){Text(item.name)} }
    }},confirmButton={TextButton(onClick={shops=false}){Text("关闭")}})

}

@Composable private fun SquareDetail(c:SquareCatalog,e:SquareEvent,vm:SquareViewModel){
    var chosenProduct by rememberSaveable(e.id){mutableStateOf(e.productIds.first())}
    val p=c.products.first{it.id==chosenProduct};var role by rememberSaveable{mutableStateOf("main")}
    var viewer by remember { mutableStateOf<Pair<List<String>,Int>?>(null) }
    val imgs=p.images.filter{it.role==role}.distinctBy{it.path}.sortedBy{it.order}
    val state=androidx.compose.foundation.lazy.rememberLazyListState();ObserveSkinScroll(state)
    LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(c.shops.first{it.id==e.shopId}.name,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
            Text(e.title,style=MaterialTheme.typography.headlineSmall)
            Text("${e.kind} · ${e.state} · ${e.date}",style=MaterialTheme.typography.bodySmall)
            Text("采集 ${e.captured} · 价格与库存非实时状态",style=MaterialTheme.typography.bodySmall)
        }}
        if(e.productIds.size>1)item {LolitaCard(variant=CardVariant.COMPACT) { ExpandableInfo("当前：${p.priceType} · 切换商品链接") { ProductChoices(c.products.filter{it.id in e.productIds},chosenProduct,{chosenProduct=it;role="main"}) } }}
        item { LolitaSection("具体单品与资料") { row { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("先看具体部件；进入规格后可自主拆分与组合。",style=MaterialTheme.typography.bodySmall)
            p.skus.flatMap { it.parts }.distinctBy { it.key }.forEach { part ->
                val exact=p.skus.firstOrNull { it.parts.size==1 && it.parts[0].key==part.key && !it.photoPaths.isNullOrEmpty() }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    exact?.photoPaths?.firstOrNull()?.let { path->LolitaShimmerImage(model=vm.image(path),contentDescription=part.name,contentScale=ContentScale.Fit,
                        modifier=Modifier.size(76.dp).clickable{viewer=listOf(vm.image(path)) to 0},circularRevealEnabled=false) }
                    Column(Modifier.weight(1f).padding(8.dp)){Text(part.name,style=MaterialTheme.typography.titleSmall)
                        Text(if(part.isAccessory)"小物 · 规格独立核对" else "服饰 · 可挑选对应资料",style=MaterialTheme.typography.bodySmall)
                        p.materials.orEmpty().filter{it.partKey==part.key}.forEach { material->
                            ExpandableInfo("查看${part.name}材质与尺码"){Text(material.facts)
                                LazyRow { items(material.detailPaths){path->LolitaShimmerImage(model=vm.image(path),contentDescription="${part.name}资料原图",contentScale=ContentScale.Fit,
                                    modifier=Modifier.size(96.dp).clickable{viewer=material.detailPaths.map{vm.image(it)} to material.detailPaths.indexOf(path)},circularRevealEnabled=false)} }
                            }
                        }
                    }
                }
            }
        } } } }
        item {LolitaSection("图片与完整资料") {row{Column{
            Choices(listOf("主图","规格图","详情图","促销图"),mapOf("main" to "主图","sku" to "规格图","detail" to "详情图","promo" to "促销图").getValue(role),{role=mapOf("主图" to "main","规格图" to "sku","详情图" to "detail","促销图" to "promo").getValue(it)},"",false)
            Text("${imgs.size} 张原图 · 点击完整放大",style=MaterialTheme.typography.bodySmall)
            if(imgs.isNotEmpty()){
                val paths=imgs.map{vm.image(it.path)}
                ImageFrame(Modifier.fillMaxWidth()) {
                    LolitaShimmerImage(model=paths.first(),contentDescription="${e.title}完整预览",contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxWidth().height(238.dp).clickable{viewer=paths to 0},circularRevealEnabled=false)
                }
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(imgs.size){i->LolitaShimmerImage(model=paths[i],contentDescription="${role}原图 ${i+1}",contentScale=ContentScale.Fit,modifier=Modifier.size(72.dp).clickable{viewer=paths to i},circularRevealEnabled=false)}}
            }else Text("本链接未提供此类图片")
        }}}}
        item {LolitaSection("价格与购买内容") {row{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(e.price,style=MaterialTheme.typography.titleSmall);Text(e.priceNote);Text(p.priceType)
            Text(e.material);Text(e.detail)
            Text("完整SKU ${p.skus.size} 个 · 套装、组合与小物分别选择。",style=MaterialTheme.typography.bodySmall)
        }}}}
        item {LolitaSection("商品信息与来源") {row{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            ExpandableInfo(title="展开完整系列资料与来源") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            p.info.entrySet().filter{it.key!="source" && !it.value.isJsonNull && it.value.toString()!="[]"}.forEach{(key,v)->
                Text(infoLabel(key),style=MaterialTheme.typography.titleSmall);Text(readable(v),style=MaterialTheme.typography.bodyMedium)
            }
            Text("公开链接：${p.url}",style=MaterialTheme.typography.bodySmall)
            Text("商品 ${p.id} · 批次 ${e.batchId} · 资料版本 ${p.revision}",style=MaterialTheme.typography.bodySmall)
            }}
        }}}}
        if(!e.canRegister)item {LolitaCard { Text("本页为尾款资料，非商品全款。仅供核对批次与时间，不建立新物品或付款。") }}
    }
    viewer?.let{(paths,i)->FullScreenImageViewer(paths,i){viewer=null}}
}

@Composable private fun SquareSelection(c:SquareCatalog,e:SquareEvent,vm:SquareViewModel,busy:Boolean){
    val pid by vm.productId.collectAsState();val key by vm.skuKey.collectAsState();val partKey by vm.partKey.collectAsState()
    val products=c.products.filter{it.id in e.productIds && it.pricePhase!="BALANCE"}
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
