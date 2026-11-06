package com.lolita.app.ui.screen.square

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.repository.SquareRepository
import com.lolita.app.di.AppModule
import com.lolita.app.data.square.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class SquareViewModel(private val repository:SquareRepository=AppModule.squareRepository(),
    private val saved:SavedStateHandle=SavedStateHandle(),
    val showAmounts:kotlinx.coroutines.flow.Flow<Boolean> = AppModule.appPreferences().showTotalPrice):ViewModel() {
    val wardrobe=repository.wardrobe.stateIn(viewModelScope,SharingStarted.Eagerly,SquareWishWardrobe())
    val browseKey=saved.getStateFlow<String?>("square.browseKey",null)
    val browseChoices=saved.getStateFlow("square.browseChoices","[]")
    val multiOpen=saved.getStateFlow("square.multiOpen",false)
    val multiChoices=saved.getStateFlow("square.multiChoices","[]")
    val multiGrouped=saved.getStateFlow("square.multiGrouped",false)
    private val _wishResult=MutableStateFlow<SquareWishResult?>(null)
    val wishResult=_wishResult.asStateFlow()
    fun browseParts():List<SquareBrowsePart> {
        val c=catalog.value?:return emptyList();val e=c.events.firstOrNull{it.id==eventId.value}?:return emptyList()
        return SquareBrowse.parts(c,e)
    }
    private fun choices(json:String):List<SquareBrowseChoice> = runCatching { SquarePackage.gson.fromJson(json,Array<SquareBrowseChoice>::class.java).toList() }.getOrDefault(emptyList())
    fun currentBrowsePart():SquareBrowsePart?=browseParts().firstOrNull{it.key==browseKey.value}?:browseParts().firstOrNull()
    fun currentBrowseChoice():SquareBrowseChoice? {
        val c=catalog.value?:return null;val part=currentBrowsePart()?:return null
        return choices(browseChoices.value).firstOrNull{it.key==part.key}?:SquareBrowseChoice(part.key,SquareBrowse.colors(c,part).firstOrNull().orEmpty())
    }
    fun currentBrowseSelection():SquareBrowseSelection? {
        val c=catalog.value?:return null;val part=currentBrowsePart()?:return null;val choice=currentBrowseChoice()?:return null
        return runCatching{SquareBrowse.select(c,part,choice)}.getOrNull()
    }
    fun selectBrowse(key:String){if(_busy.value!=null)return;saved["square.browseKey"]=key}
    fun chooseBrowse(color:String?=null,size:String?=null){
        if(_busy.value!=null)return
        val c=catalog.value?:return;val part=currentBrowsePart()?:return;val old=currentBrowseChoice()?:return
        var next=old.copy(color=color?:old.color,size=size?:old.size)
        if(next.size.isNotEmpty() && next.size !in SquareBrowse.sizes(c,part,next.color))next=next.copy(size="")
        saved["square.browseChoices"]=SquarePackage.gson.toJson(choices(browseChoices.value).filterNot{it.key==next.key}+next)
    }
    fun openMulti(){if(_busy.value!=null)return;val choice=currentBrowseChoice()?:return
        saved["square.multiChoices"]=SquarePackage.gson.toJson(listOf(choice));saved["square.multiGrouped"]=false;saved["square.multiOpen"]=true}
    fun closeMulti(){if(_busy.value==null)saved["square.multiOpen"]=false}
    fun selectedWishes()=choices(multiChoices.value)
    fun toggleWish(key:String){if(_busy.value!=null)return;val current=selectedWishes();val next=if(current.any{it.key==key})current.filterNot{it.key==key}else current+SquareBrowseChoice(key)
        saved["square.multiChoices"]=SquarePackage.gson.toJson(next);if(next.size<2)saved["square.multiGrouped"]=false}
    fun changeWish(key:String,color:String?=null,size:String?=null){if(_busy.value!=null)return
        val part=browseParts().firstOrNull{it.key==key}?:return;val c=catalog.value?:return
        saved["square.multiChoices"]=SquarePackage.gson.toJson(selectedWishes().map { choice->if(choice.key!=key)choice else {
            val next=choice.copy(color=color?:choice.color,size=size?:choice.size)
            if(next.size.isNotEmpty() && next.size !in SquareBrowse.sizes(c,part,next.color))next.copy(size="")else next
        } })}
    fun groupWishes(value:Boolean){if(_busy.value==null)saved["square.multiGrouped"]=value && selectedWishes().size>1}
    private fun wishRequest(multiple:Boolean):SquareWishRequest {
        val c=catalog.value?:error("请先导入广场资料包");val e=c.events.single{it.id==eventId.value}
        return SquareBrowse.request(c,e,if(multiple)selectedWishes()else listOfNotNull(currentBrowseChoice()),multiple && multiGrouped.value)
    }
    fun currentWishMatch():SquareWishMatch = runCatching { SquareWishPlans.match(SquareWishPlans.build(catalog.value!!,wishRequest(false)).single(),wardrobe.value) }.getOrDefault(SquareWishMatch())
    fun saveWishes(multiple:Boolean=false,confirmRelated:Boolean=false){
        if(_busy.value!=null)return
        val request=runCatching {
            if(confirmRelated)SquarePackage.gson.fromJson(saved.get<String>("square.lastWishRequest"),SquareWishRequest::class.java).copy(allowRelated=true)
            else wishRequest(multiple)
        }.getOrElse{_message.value="所选资料已改变，请重新选择";return}
        if(!confirmRelated)saved["square.resultSheet"]=multiple
        saved["square.lastWishRequest"]=SquarePackage.gson.toJson(request)
        work("加入心愿"){val result=repository.addWishes(request);if(!result.related && result.draftIds.isEmpty())saved["square.multiOpen"]=false;_wishResult.value=result}
    }
    fun wishWasMultiple()=runCatching{val r=SquarePackage.gson.fromJson(saved.get<String>("square.lastWishRequest"),SquareWishRequest::class.java);saved.get<Boolean>("square.resultSheet")==true||r.grouped||r.choices.size>1}.getOrDefault(false)
    fun clearWishResult(){_wishResult.value=null}
    val catalog=repository.catalog
    val eventId=saved.getStateFlow<String?>("square.event",null)
    val choosing=saved.getStateFlow("square.choosing",false)
    val query=saved.getStateFlow("square.query","")
    val kind=saved.getStateFlow("square.kind","")
    val shop=saved.getStateFlow("square.shop","")
    val color=saved.getStateFlow("square.color","")
    val style=saved.getStateFlow("square.style","")
    val form=saved.getStateFlow("square.form","")
    val productId=saved.getStateFlow<String?>("square.product",null)
    val skuKey=saved.getStateFlow<String?>("square.sku",null)
    val partKey=saved.getStateFlow<String?>("square.part",null)
    val step=saved.getStateFlow("square.step",0)
    val selectedParts=saved.getStateFlow("square.parts",arrayListOf<String>())
    val grouped=saved.getStateFlow("square.grouped",false)
    val unitsJson=saved.getStateFlow("square.units", "[]")
    val unitIndex=saved.getStateFlow("square.unitIndex",0)
    val photoUnit=saved.getStateFlow("square.photoUnit",0)
    val editorResult=saved.getStateFlow<String?>("square_editor_result",null)
    fun units():List<SquareUnit> = SquarePackage.gson.fromJson(unitsJson.value,Array<SquareUnit>::class.java).toList()
    private fun saveUnits(units:List<SquareUnit>){saved["square.units"]=SquarePackage.gson.toJson(units)}
    private fun selection():Pair<SquareProduct,SquareSku>? {
        val p=catalog.value?.products?.firstOrNull { it.skus.any { s -> s.key==skuKey.value } } ?: return null
        return p to p.skus.first { it.key==skuKey.value }
    }
    fun togglePart(key:String){val list=selectedParts.value.toMutableList();if(key in list)list.remove(key) else list.add(key);saved["square.parts"]=ArrayList(list)}
    fun group(value:Boolean){saved["square.grouped"]=value}
    fun next(){when(step.value){
        0 -> { val (_,sku)=selection()?:return;saved["square.parts"]=ArrayList(sku.parts.map{it.key});saved["square.step"]=1 }
        1 -> { val (p,sku)=selection()?:return;val parts=sku.parts.filter{it.key in selectedParts.value};if(parts.isEmpty())return
            saveUnits(if(grouped.value && parts.size>1)listOf(p.suggestUnit(sku,parts,if(parts.all{it.isAccessory})"小物组合" else "服饰组合")) else parts.map{p.suggestUnit(sku,listOf(it),"单品")})
            saved["square.planEvent"]=eventId.value;saved["square.planSku"]=sku.key
            saved["square.unitIndex"]=0;saved["square.photoUnit"]=0;saved["square.step"]=2 }
        2 -> saved["square.step"]=3
    }}
    fun editUnit(index:Int){if(index<unitIndex.value)return;saved["square.photoUnit"]=index;saved["square.step"]=2}
    fun setPhotoUnit(index:Int){if(index>=unitIndex.value)saved["square.photoUnit"]=index}
    fun changeUnit(index:Int,unit:SquareUnit){if(index<unitIndex.value || _busy.value!=null)return;val list=units().toMutableList();if(index in list.indices){list[index]=unit;saveUnits(list)}}
    fun toggleImage(index:Int,path:String,document:Boolean){val unit=units().getOrNull(index)?:return
        val current=(if(document)unit.documents else unit.photos).toMutableList()
        if(path in current)current.remove(path) else if(current.size<50)current.add(path) else {_message.value="每件每类最多选择50张图片";return}
        changeUnit(index,if(document)unit.copy(documents=current,photos=unit.photos-path) else unit.copy(photos=current,documents=unit.documents-path))
    }
    fun cover(index:Int,path:String){val unit=units().getOrNull(index)?:return;if(path in unit.photos)changeUnit(index,unit.copy(photos=listOf(path)+unit.photos.filter{it!=path}))}
    fun editorFinished(result:String){saved["square_editor_result"]=result;consumeEditorResult()}
    fun consumeEditorResult(){val result=editorResult.value?:return;saved["square_editor_result"]=null
        val active=saved.get<String>("square.activeDraft")?:return
        if(result!="$active:saved" && result!="$active:paused")return
        if(result=="$active:saved"){saved["square.unitIndex"]=unitIndex.value+1;_message.value="本件已保存；其余物品需逐件核对"}
        else _message.value="登记已暂停；可继续核对，保留的草稿仍可编辑"
        saved["square.activeDraft"]=null
    }
    private val _busy=MutableStateFlow<String?>("读取广场资料")
    val busy=_busy.asStateFlow()
    private val _preview=MutableStateFlow<SquareRepository.Preview?>(null)
    val preview=_preview.asStateFlow()
    private val _message=MutableStateFlow<String?>(null)
    val message=_message.asStateFlow()
    private val _registration=MutableStateFlow<SquareRepository.Registration?>(null)
    val registration=_registration.asStateFlow()
    init { work { repository.load() } }
    fun image(path:String)=repository.image(path)
    fun set(key:String,value:String) { saved["square.$key"]=value }
    fun open(id:String) { val c=catalog.value?:return;if(c.events.none{it.id==id && SquareVisibility.event(c,it)})return; if(id!=eventId.value){saved["square.browseKey"]=null;saved["square.browseChoices"]="[]";saved["square.multiOpen"]=false};saved["square.event"]=id;saved["square.choosing"]=false;saved["square.product"]=null;saved["square.sku"]=null;saved["square.part"]=null }
    fun back() { if(multiOpen.value){closeMulti();return};if(choosing.value && step.value>0)saved["square.step"]=step.value-1 else if(choosing.value)saved["square.choosing"]=false else saved["square.event"]=null }
    fun choose() {
        saved["square.choosing"]=true
        val planSku=saved.get<String>("square.planSku")
        if(saved.get<String>("square.planEvent")==eventId.value && units().isNotEmpty() && unitIndex.value<units().size && planSku!=null){
            saved["square.sku"]=planSku;saved["square.product"]=catalog.value?.products?.firstOrNull{it.skus.any{s->s.key==planSku}}?.id;saved["square.step"]=3
        }else saved["square.step"]=0
    }
    fun selectProduct(id:String){saved["square.product"]=id;saved["square.sku"]=null;saved["square.part"]=null}
    fun selectSku(key:String){saved["square.sku"]=key;saved["square.part"]=null;saved["square.step"]=0}
    fun selectPart(key:String?){saved["square.part"]=key}
    fun import(uri:Uri) { if(_preview.value!=null || _busy.value!=null)return
        work("正在读取资料包") { _preview.value=repository.preview(uri){_busy.value=it} }
    }
    fun cancelPreview(){val p=_preview.value?:return;work("关闭预览"){repository.discard(p);_preview.value=null}}
    fun apply(){val p=_preview.value?:return;work("保存到广场") {
        try {repository.apply(p);_message.value="资料已导入广场 · ${p.catalog.shops.size} 家店 · ${p.catalog.events.size} 条动态"}
        finally {repository.discard(p);_preview.value=null}
    }}
    fun prepare(){val event=eventId.value?:return;val sku=skuKey.value?:return
        val unit=units().getOrNull(unitIndex.value)?:return
        work("准备所选物品与图片") { val r=repository.prepare(event,sku,unit)
            if(r.itemId!=null){saved["square.unitIndex"]=unitIndex.value+1;_message.value="这件已在衣橱，打开原记录"}
            saved["square.activeDraft"]=r.draftId;_registration.value=r }
    }
    fun consumedRegistration(){_registration.value=null}
    fun clearMessage(){_message.value=null}
    private fun work(label:String="读取广场资料",block:suspend()->Unit){
        if(_busy.value!=null && label!="读取广场资料")return
        _busy.value=label
        viewModelScope.launch(Dispatchers.IO){try{block()}catch(e:Exception){
            if(e is CancellationException)throw e
            // Package validation messages are authored Chinese; I/O exceptions never expose paths.
            _message.value=if(e is IllegalArgumentException || e is IllegalStateException)
                e.message?.takeIf { it.any { c-> c in '\u4e00'..'\u9fff' } && it.length<=200 } ?: "资料无法读取，请重新选择完整的广场资料包"
                else "操作未完成，请检查文件权限与存储空间后重新选择资料包"
        }finally{_busy.value=null}}
    }
}
