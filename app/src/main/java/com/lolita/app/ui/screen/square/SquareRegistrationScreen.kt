package com.lolita.app.ui.screen.square

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lolita.app.data.square.*
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll

@Composable
internal fun SquareRegistrationScreen(c:SquareCatalog,e:SquareEvent,vm:SquareViewModel){
    val key by vm.skuKey.collectAsState();val step by vm.step.collectAsState()
    val selected by vm.selectedParts.collectAsState();val grouped by vm.grouped.collectAsState()
    val json by vm.unitsJson.collectAsState();val index by vm.photoUnit.collectAsState();val done by vm.unitIndex.collectAsState()
    val p=c.products.firstOrNull{it.id in e.productIds && it.skus.any{s->s.key==key}}?:return
    val sku=p.skus.first{it.key==key};val units=remember(json){vm.units()}
    var role by rememberSaveable{mutableStateOf("主图")}
    var sourceFilter by rememberSaveable{mutableStateOf("全部原图")}
    var viewer by remember{mutableStateOf<Pair<List<String>,Int>?>(null)}
    val state=androidx.compose.foundation.lazy.rememberLazyListState();ObserveSkinScroll(state)
    LaunchedEffect(step,index){state.scrollToItem(0)}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("square-registration"),state=state,
        contentPadding=PaddingValues(LolitaSkin.current.spacingLarge),verticalArrangement=Arrangement.spacedBy(LolitaSkin.current.spacingMedium)){
        if(step!=2)item { Text("部件 → 图片 → 核对",style=MaterialTheme.typography.bodySmall) }
        if(step!=2)item { LolitaCard(variant=CardVariant.COMPACT,modifier=Modifier.fillMaxWidth()) {
            if(step==1)Column {
                Text(sku.content,style=MaterialTheme.typography.titleSmall)
                Text(sku.rawProperties.values.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                Text("原规格${p.priceType} ${squarePrice(sku.price)} · 采集快照",style=MaterialTheme.typography.bodySmall)
            } else ExpandableInfo("原购买规格 · ${sku.content}") {
                Text(sku.rawProperties.values.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                Text("原规格${p.priceType} ${squarePrice(sku.price)} · 采集快照",style=MaterialTheme.typography.bodySmall)
            }
        } }
        if(step==1){
            item { LolitaSection("这次登记哪些部件？"){
                sku.parts.forEach { part ->row {
                    val suggestion=p.suggestUnit(sku,listOf(part),"单品")
                    Row(Modifier.fillMaxWidth().clickable{vm.togglePart(part.key)},verticalAlignment=Alignment.CenterVertically){
                        Checkbox(part.key in selected,{vm.togglePart(part.key)})
                        suggestion.photos.firstOrNull()?.let{path->LolitaShimmerImage(model=vm.image(path),contentDescription=part.name,contentScale=ContentScale.Fit,
                            modifier=Modifier.size(64.dp),circularRevealEnabled=false)}
                        Column(Modifier.weight(1f).padding(8.dp)){Text(part.name,style=MaterialTheme.typography.titleSmall)
                            Text(listOf(if(part.isAccessory)"小物" else "服饰",part.color?:"颜色待核对",part.size?:"尺码待核对").joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                            part.uncertainty?.let{Text(it,style=MaterialTheme.typography.bodySmall)} }
                    }
                }}
            } }
            item { LolitaSection("怎样登记？"){row {Column{
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(!grouped,{vm.group(false)});Text("分别作为单品")}
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(grouped,{vm.group(true)});Text("所选部件合成一组")}
                Text("可只选需要的部件。组合名称与类型下一步核对；不自动关联主件、衣橱套装或价格。",style=MaterialTheme.typography.bodySmall)
            }}} }
        }else if(step==2){
            val active=units.getOrNull(index)
            if(active!=null){
                item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    units.forEachIndexed{i,u->FilterChip(i==index,{vm.setPhotoUnit(i)},enabled=i>=done,label={Text(u.name)})}
                } }
                item { ExpandableInfo("名称与组合类型") {
                    OutlinedTextField(active.name,{vm.changeUnit(index,active.copy(name=it))},label={Text("本件名称")},modifier=Modifier.fillMaxWidth())
                    if(active.partKeys.size>1)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        listOf("服饰组合","小物组合","套装").forEach{label->FilterChip(active.groupLabel==label,{vm.changeUnit(index,active.copy(groupLabel=label))},label={Text(label)})}
                    }
                    Text(sku.rawProperties.values.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                } }
                item { Text("${active.name} 的图片",style=MaterialTheme.typography.titleMedium)
                    Text("点图放大 · 勾选归属 · 首张封面",style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        listOf("主图","资料图").forEach { label->FilterChip(role==label,{role=label},label={Text(label)}) }
                    }
                    Text("本件已选 ${active.photos.size} 张主图 · ${active.documents.size} 张资料",style=MaterialTheme.typography.bodySmall)
                    if(active.photos.isEmpty())Text("没有明确对应的独立规格图，可从原图中手动选择，或保留无图。",style=MaterialTheme.typography.bodySmall)
                    if(p.materials.orEmpty().isEmpty())Text("旧包未提供部件资料映射，请手动核对；未选图片会保留在广场。",style=MaterialTheme.typography.bodySmall)
                }
                item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("全部原图","规格图","详情图","模特主图").forEach{filter->FilterChip(sourceFilter==filter,{sourceFilter=filter},label={Text(filter,style=MaterialTheme.typography.bodySmall)})}
                } }
                val paths=p.images.filter { sourceFilter=="全部原图" || it.role==mapOf("规格图" to "sku","详情图" to "detail","模特主图" to "main")[sourceFilter] }
                    .distinctBy{it.path}.sortedWith(compareBy<SquareImage>{ if(it.path in (if(role=="资料图")active.documents else active.photos))0 else 1 }
                        .thenBy{if(role=="资料图" && it.role=="detail" || role=="主图" && it.role=="sku")0 else 1}.thenBy{it.order})
                items(paths.chunked(2),key={it.first().path}){pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    pair.forEach { img->val checked=img.path in if(role=="资料图")active.documents else active.photos
                        LolitaCard(variant=CardVariant.COMPACT,modifier=Modifier.weight(1f),contentPadding=PaddingValues(6.dp)){
                            Column {
                                LolitaShimmerImage(model=vm.image(img.path),contentDescription="${img.role} 原图 ${img.order}",contentScale=ContentScale.Fit,
                                    modifier=Modifier.fillMaxWidth().height(124.dp).clickable{val images=paths.map{vm.image(it.path)};viewer=images to paths.indexOf(img)},circularRevealEnabled=false)
                                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().clickable{vm.toggleImage(index,img.path,role=="资料图")}){
                                    Checkbox(checked,{vm.toggleImage(index,img.path,role=="资料图")})
                                    Text(if(checked)"已选择" else "选择",style=MaterialTheme.typography.bodySmall)
                                }
                                if(role=="主图" && checked)TextButton(onClick={vm.cover(index,img.path)},enabled=active.photos.firstOrNull()!=img.path){Text(if(active.photos.firstOrNull()==img.path)"封面" else "设封面",style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                    };if(pair.size==1)Spacer(Modifier.weight(1f))
                }}
            }
        }else{
            item { Text("${done.coerceAtMost(units.size)} / ${units.size} 件已处理",style=MaterialTheme.typography.titleMedium)
                Text("逐件进入编辑并确认保存。已保存物品不重复新建；退出可保留当前草稿。组合报价不拆分，不建立订单或实付。",style=MaterialTheme.typography.bodySmall) }
            items(units.indices.toList()){i->val u=units[i]
                LolitaCard { Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("${i+1}. ${u.name} · ${u.groupLabel}",style=MaterialTheme.typography.titleSmall)
                    if(u.photos.isNotEmpty())LolitaShimmerImage(model=vm.image(u.photos.first()),contentDescription=u.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(160.dp),circularRevealEnabled=false)
                    Text("${u.photos.size} 张主图 · ${u.documents.size} 张资料"+(if(u.photos.isEmpty())" · 无图，编辑时可后补" else ""),style=MaterialTheme.typography.bodySmall)
                    val parts=sku.parts.filter{it.key in u.partKeys}
                    Text(parts.joinToString("\n"){"${it.name}：${it.color?:"颜色待核对"} / ${it.size?:"尺码待核对"}"},style=MaterialTheme.typography.bodyMedium)
                    if(i>=done)TextButton(onClick={vm.editUnit(i)}){Text("调整本件图片与名称")}else Text("已处理，原记录和草稿继续保留",style=MaterialTheme.typography.bodySmall)
                }}
            }
        }
    }
    viewer?.let{(paths,page)->FullScreenImageViewer(paths,page){viewer=null}}
}
