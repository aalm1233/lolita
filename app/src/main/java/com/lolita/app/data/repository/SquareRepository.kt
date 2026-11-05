package com.lolita.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoProductParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.square.*
import com.lolita.app.domain.model.ItemComposition
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SquareRepository(private val context: Context, private val database: LolitaDatabase,
    private val drafts: ItemDraftRepository) {
    private val gate=Mutex()
    private var loaded=false
    private val root=File(context.filesDir,"square_catalog")
    private val index get()=AtomicFile(File(root,"catalog.json"))
    private val _catalog=MutableStateFlow<SquareCatalog?>(null)
    val catalog=_catalog.asStateFlow()
    data class Preview(val catalog:SquareCatalog,val stage:File)
    data class Registration(val itemId:Long?=null,val draftId:String?=null)

    suspend fun load()=withContext(Dispatchers.IO) { gate.withLock {
        if(loaded)return@withLock
        root.mkdirs()
        // Only interrupted temporary imports; published image blobs are never swept here.
        root.listFiles()?.filter { it.name.startsWith("pending-") || it.name.startsWith("input-") }?.forEach { it.deleteRecursively() }
        if(File(root,"catalog.json").exists()) {
            val c=index.openRead().bufferedReader().use { SquarePackage.decode(it.readText()) }
            require(c.assets.all { File(root,it.path).isFile }) { "广场图片缺失，请重新导入资料包" }
            _catalog.value=c
        }
        loaded=true
    } }

    fun image(path:String)=File(root,path).absolutePath

    suspend fun preview(uri:Uri,progress:(String)->Unit):Preview=withContext(Dispatchers.IO) { gate.withLock {
        val jobContext=currentCoroutineContext()
        root.mkdirs()
        root.listFiles()?.filter { it.name.startsWith("pending-") }?.forEach { it.deleteRecursively() }
        val input=File(root,"input-${UUID.randomUUID()}.zip")
        val stage=File(root,"pending-${UUID.randomUUID()}")
        try {
            progress("正在读取资料包")
            val available=root.usableSpace
            require(available>100L*1024*1024) { "可用存储空间不足，请释放空间后重试" }
            context.contentResolver.openInputStream(uri)?.use { source -> input.outputStream().use { dest ->
                val buf=ByteArray(64*1024);var count=0L
                while(true){val n=source.read(buf);if(n<0)break;count+=n
                    require(count<=SquarePackage.MAX_ZIP && count < available/2) { "资料包过大或可用存储空间不足" };dest.write(buf,0,n)}
            } } ?: error("无法读取所选文件，请重新选择资料包")
            val c=SquarePackage.unpack(input,stage,{ jobContext.ensureActive();progress(it) }) { file,a ->
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(file.absolutePath,options)
                require(options.outWidth==a.width && options.outHeight==a.height && options.outMimeType in setOf("image/jpeg","image/png","image/webp")) { "图片无法完整识别，请重新获取资料包" }
            }
            SquarePackage.merge(_catalog.value,c) // Conflict checks happen before the preview's commit button.
            Preview(c,stage)
        } catch(t:Throwable) { stage.deleteRecursively();throw t } finally { input.delete() }
    } }

    suspend fun discard(preview:Preview)=withContext(Dispatchers.IO) { gate.withLock { preview.stage.deleteRecursively();Unit } }

    suspend fun apply(preview:Preview)=withContext(NonCancellable + Dispatchers.IO) { gate.withLock {
        require(preview.stage.isDirectory) { "预览已失效，请重新选择资料包" }
        val merged=SquarePackage.merge(_catalog.value,preview.catalog)
        preview.catalog.assets.forEach { asset ->
            val dest=File(root,asset.path);dest.parentFile!!.mkdirs()
            // Replace only verified content-addressed blobs, so a reimport repairs missing/corrupt files.
            val source=File(preview.stage,asset.path)
            require(source.renameTo(dest)) { "保存广场图片失败，请检查存储空间后重试" }
        }
        val output=index.startWrite()
        try { output.write(SquarePackage.gson.toJson(merged).toByteArray(Charsets.UTF_8));index.finishWrite(output) }
        catch(t:Throwable){index.failWrite(output);throw t}
        _catalog.value=merged
        preview.stage.deleteRecursively()
    } }

    /** Freeze one complete tuple into an existing, backup-compatible wardrobe editor draft. */
    suspend fun prepare(eventId:String,skuKey:String,partKey:String?):Registration=withContext(Dispatchers.IO) { gate.withLock {
        val catalog=_catalog.value ?: error("请先导入广场资料包")
        val event=catalog.events.single { it.id==eventId }
        require(event.canRegister) { "尾款仅供查看，不能作为新物品登记" }
        val product=catalog.products.single { p -> p.id in event.productIds && p.skus.any { it.key==skuKey } }
        require(product.pricePhase!="BALANCE") { "尾款规格不能作为新物品登记" }
        val sku=product.skus.single { it.key==skuKey }
        val part=partKey?.let { key -> sku.parts.single { it.key==key } }
        val identity="square:v1:${event.shopId}:${event.batchId}:${sku.variantId}:${part?.key ?: "@whole"}"
        val token="square:"+UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))
        database.itemDao().getItemByImportToken(token)?.let { return@withLock Registration(itemId=it.id) }
        val draftId=UUID.nameUUIDFromBytes(("draft:"+identity).toByteArray(Charsets.UTF_8)).toString()
        drafts.get(draftId)?.let { return@withLock Registration(draftId=draftId) }
        val shop=catalog.shops.single { it.id==event.shopId }
        val aliases=when(shop.id){"puppets"->listOf(shop.name,"Puppets and Doll","Puppets & Doll","古典玩偶Puppets and Doll官方店","古典玩偶ClassicalPuppets","古典玩偶（Classical Puppets）","Classical Puppets")
            "lilizi"->listOf(shop.name,"莉莉子lolita店");"neo"->listOf(shop.name,"Neo Ludwig","Neoludwig新路德维希","新路德维希Neo Ludwig洋装","新路德维希（Neo Ludwig）");else->listOf(shop.name)}
        val brands=database.brandDao().getAllBrandsList()
        val match=TaobaoProductParser.matchBrand(brands.map { it.name },aliases)
        val brandId=brands.singleOrNull { it.name==match }?.id ?: 0L
        val categoryName=part?.category ?: sku.parts.firstOrNull()?.category
        val categories=database.categoryDao().getAllCategoriesList()
        val categoryId=categories.firstOrNull { it.name.equals(categoryName,true) }?.id ?: 0L
        val main=product.images.filter { it.role=="main" || it.role=="sku" }.map { it.path }.distinct()
        val details=product.images.filter { it.role=="detail" }.map { it.path }.distinct()
        val assets=catalog.assets.associateBy { it.path }
        require((main+details).distinct().sumOf { assets.getValue(it).bytes }<=SquarePackage.MAX_PRODUCT) { "商品图片超过80MiB，请整理资料包后重试" }
        val copies=mutableListOf<String>()
        try {
            suspend fun copy(paths:List<String>)=paths.map { path ->
                val source=File(root,path);require(source.isFile && source.length()==assets.getValue(path).bytes) { "原图不完整，请重新导入资料包" }
                ImageFileHelper.copyExistingImageToInternalStorage(context,source.absolutePath).also { copies.add(it) }
            }
            val photos=copy(main);val charts=copy(details)
            val description=buildString {
                appendLine("广场资料 · ${shop.name} · ${event.title} ${event.subtitle}")
                appendLine("${event.kind} / ${event.state}；采集：${product.capturedAt}")
                appendLine("商品：${product.url}")
                appendLine("完整规格："+sku.rawProperties.entries.joinToString("；") { "${it.key}：${it.value}" })
                appendLine("购买内容：${sku.content}（${sku.form}）")
                part?.let { appendLine("本次仅登记：${it.name}；其余部件未登记。未分别标注的颜色、尺码留空。") }
                appendLine("${product.priceType}：${squarePrice(sku.price)}；仅为采集报价，不代表实付。组合价不分摊到分件。")
                appendLine(event.note);appendLine(event.material);appendLine(event.detail)
                sku.styleFacts.forEach { appendLine("风格：${it.value}（${it.source}）") }
                appendLine("资料标识：${event.id} / ${event.batchId} / ${product.id} / ${sku.id}；版本 ${product.revision}")
                appendLine("商品资料：\n"+squareInfoText(product.info))
            }
            val data=ItemDraftData(name="${event.title} · ${part?.name ?: sku.content}",description=description,
                brandId=brandId,categoryId=categoryId,status=ItemStatus.WISHED,
                colors=listOfNotNull(if(part!=null)part.color else sku.color),
                size=if(part!=null)part.size else sku.size,style=sku.styleFacts.map { it.value }.distinct().singleOrNull(),source="淘宝",
                composition=if(part==null && sku.parts.size>1)ItemComposition.SET else ItemComposition.SINGLE,importToken=token)
            database.withTransaction {
                database.itemDao().getItemByImportToken(token)?.let { error("此规格已在衣橱，请返回重新打开") }
                require(database.itemDraftDao().get(draftId)==null) { "此规格已有登记草稿，请返回重新打开" }
                drafts.save(ItemDraft(id=draftId,dataJson=data.encode(),imagePaths=photos,sizeChartImagePaths=charts))
            }
            Registration(draftId=draftId)
        } catch(t:Throwable) { ImageFileHelper.cleanupUnreferencedImages(copies);throw t }
    } }
}
