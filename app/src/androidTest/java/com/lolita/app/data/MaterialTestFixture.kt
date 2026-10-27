package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.generation.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.remote.*
import com.lolita.app.data.tryon.*
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Isolated Room, preferences and files. No HTTP or credentials; safe for dedicated AVDs only. */
internal class MaterialTestFixture {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    val root = File(target.cacheDir,"material-${UUID.randomUUID()}").apply { mkdirs() }
    val context = object : ContextWrapper(target) {
        override fun getFilesDir() = File(root,"files").apply { mkdirs() }
        override fun getCacheDir() = File(root,"cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root,"noBackup").apply { mkdirs() }
    }
    val db = Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
    val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val images = TryOnImages(context)
    val opaque = png(false)
    val transparent = png(true)
    var frontCalls = 0
    var layerCalls = 0
    var fusionCalls = 0
    var displayCalls = 0
    var displayOutput: suspend () -> ByteArray = { opaque }
    var serviceStarts = 0
    var frontOutput: suspend () -> ByteArray = { opaque }
    var layerOutput: (suspend () -> GeneratedImageLayers) = { GeneratedImageLayers(listOf(GeneratedImageLayer(transparent,1,"腰饰","含细链和独立绳端",listOf(100,100,900,900))),2) }
    var receivedImages = emptyList<ByteArray>()
    lateinit var source: String
    var itemId = 0L
    var categoryId = 0L
    val coordinator = ImageTaskCoordinator(db,scope,{serviceStarts++}, { ImageFileHelper.saveGeneratedImage(context,it) },ImageFileHelper::deleteImage,
        { ImageFileHelper.isOwnedGeneratedImage(context,it) },
        validateSourceImage = { ImageFileHelper.isOwnedReadableImage(context,it) },
        validateProductImage = { ImageFileHelper.isOwnedReadableProductImage(context,it) },
        snapshotBuiltInImage = { ImageFileHelper.copyAssetToInternalStorage(context,it) },
        appendImageProvenance = { itemId,path,mode,taskId -> db.imageProvenanceDao().upsert(ImageProvenance(path,itemId,ImageSourceType.AI_GENERATED,mode,taskId)) },
        snapshotMaterialInput = { path,alpha -> ImageFileHelper.saveGeneratedImage(context,images.prepareMaterialSnapshot(path,alpha)) },
        validateMaterialImage = { ImageFileHelper.isOwnedGeneratedImage(context,it) && MaterialImageValidation.hasTransparentForeground(it) },
        pinMaterialInputs = { paths -> val lease = ImageFileHelper.pinImageReferences(context,paths); val release: suspend () -> Unit = { lease.release() }; release })
    val executor = ImageTaskExecutor(coordinator,db,{ ImageGenerationCredentials(it,"controlled-only-key") },
        images::prepare,images::prepareMannequin,
        { _,inputs,_ -> fusionCalls++; receivedImages=inputs; opaque },
        prepareSnapshotImages=images::prepareSnapshots,
        generateMannequin={ _,inputs,_ -> displayCalls++; receivedImages=inputs; displayOutput() },
        generateMaterial={ _,inputs,_ -> frontCalls++; receivedImages=inputs; frontOutput() },
        decomposeMaterial={ _,inputs,_ -> layerCalls++; receivedImages=inputs; layerOutput() })
    val runner = ImageTaskRunner(coordinator,executor::execute)
    suspend fun init() {
        ImageFileHelper.configure(context,db)
        coordinator.awaitStartup()
        source=ImageFileHelper.saveGeneratedImage(context,opaque)
        val brand=db.brandDao().insertBrand(Brand(name="测试品牌"))
        categoryId=db.categoryDao().insertCategory(Category(name="已改名类别",categorySemantic=CategorySemantic.WAIST_ACCESSORY))
        itemId=db.itemDao().insertItem(Item(brandId=brand,categoryId=categoryId,name="示意双扣腰饰",description="原资料保持",imageUrls=listOf(source),status=ItemStatus.WISHED,composition=ItemComposition.SINGLE))
    }
    suspend fun front(): String {
        val input=coordinator.prepareMaterialInput(source,false)
        return coordinator.submitMaterial(itemId,input,source,"两枚主扣，保留附属部件",ImageGenerationProvider.SEEDREAM,
            db.itemTryOnMaterialDao().getForItem(itemId)?.id).also { runner.runQueue() }
    }
    suspend fun layers(parent: String): String {
        val preview=requireNotNull(db.imageTaskDao().get(parent)?.resultImagePath)
        val input=coordinator.prepareMaterialInput(preview,false)
        return coordinator.submitMaterialLayers(parent,input).also { runner.runQueue() }
    }
    suspend fun confirmed(): ItemTryOnMaterial {
        val taskId=layers(front()); val task=requireNotNull(db.imageTaskDao().get(taskId))
        coordinator.confirmMaterial(taskId,requireNotNull(task.resultImagePath),db.itemTryOnMaterialDao().getForItem(itemId)?.id,true)
        return requireNotNull(db.itemTryOnMaterialDao().getForItem(itemId))
    }
    suspend fun base(): TryOnResult {
        val image=ImageFileHelper.saveGeneratedImage(context,target.assets.open("tryon/model.png").use { it.readBytes() })
        val row=TryOnResult(name="原穿搭底图",imageUrl=image,itemIds=listOf(itemId.toString()),itemNames=listOf("示意服饰"),model="controlled-model")
        return row.copy(id=db.tryOnDao().insert(row))
    }
    suspend fun close() {
        scope.coroutineContext.job.cancelAndJoin(); db.close(); root.deleteRecursively()
        ImageFileHelper.configure(AppModule.context(),AppModule.database())
    }
    private fun png(alpha: Boolean): ByteArray {
        val source=InstrumentationRegistry.getInstrumentation().context.assets.open("material-preview.png").use { BitmapFactory.decodeStream(it) }
        val bitmap=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if(alpha) android.graphics.Color.TRANSPARENT else android.graphics.Color.LTGRAY)
        Canvas(bitmap).drawBitmap(source,0f,0f,null); source.recycle()
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
    }
}
