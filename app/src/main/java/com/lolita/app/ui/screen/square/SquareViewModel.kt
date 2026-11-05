package com.lolita.app.ui.screen.square

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.repository.SquareRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SquareViewModel(private val repository:SquareRepository=AppModule.squareRepository(),
    private val saved:SavedStateHandle=SavedStateHandle()):ViewModel() {
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
    fun open(id:String) { saved["square.event"]=id;saved["square.choosing"]=false;saved["square.product"]=null;saved["square.sku"]=null;saved["square.part"]=null }
    fun back() { if(choosing.value)saved["square.choosing"]=false else saved["square.event"]=null }
    fun choose() { saved["square.choosing"]=true }
    fun selectProduct(id:String){saved["square.product"]=id;saved["square.sku"]=null;saved["square.part"]=null}
    fun selectSku(key:String){saved["square.sku"]=key;saved["square.part"]=null}
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
        work("准备完整商品图片") { _registration.value=repository.prepare(event,sku,partKey.value) }
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
