package com.lolita.app.data

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.ImageRegionCropper
import com.lolita.app.data.file.NormalizedCropRect
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.generation.ImageTaskExecutor
import com.lolita.app.data.generation.ImageTaskRunner
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.mannequin.WigHeadTemplateCatalog
import com.lolita.app.data.remote.ImageGenerationApi
import com.lolita.app.data.remote.SeedreamImageApi
import com.lolita.app.data.remote.TryOnImageApi
import com.lolita.app.data.tryon.TryOnImages
import com.lolita.app.di.AppModule
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

/** Real ownership/integrity/assets/admission/preparation; only startup, credentials and HTTP are fake. */
@RunWith(AndroidJUnit4::class)
class MannequinNativeAdmissionTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val semantics = listOf(CategorySemantic.JSK,CategorySemantic.KC,CategorySemantic.MARY_JANE,
        CategorySemantic.KNEE_SOCKS,CategorySemantic.FULL_WIG,CategorySemantic.BLOUSE)
    private enum class Format { PNG,JPEG,WEBP_WITH_JPG_EXTENSION }

    private inner class Fixture {
        val root=File(target.cacheDir,"mannequin-native-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(target) {
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val images=TryOnImages(context)
        val catalog=WigHeadTemplateCatalog(context)
        val uploads=mutableListOf<Int>()
        val staged=mutableListOf<String>()
        var credentialReads=0
        var validateSource:suspend (String)->Boolean={ImageFileHelper.isOwnedReadableImage(context,it)}
        var validateProduct:suspend (String)->Boolean={ImageFileHelper.isOwnedReadableProductImage(context,it)}
        var snapshot:suspend (String)->String={ImageFileHelper.copyAssetToInternalStorage(context,it).also {path ->staged+=path}}
        var start:(String)->Unit={}
        var beforeSubmit:suspend (Item)->Unit={}
        val responseBytes=bitmapBytes(Bitmap.CompressFormat.PNG)
        val coordinator=ImageTaskCoordinator(db,scope,{start(it)},
            {ImageFileHelper.saveGeneratedImage(context,it)},ImageFileHelper::deleteImage,
            {ImageFileHelper.isOwnedGeneratedImage(context,it)},
            validateSourceImage={validateSource(it)},snapshotBuiltInImage={snapshot(it)},validateProductImage={validateProduct(it)})
        val transportApi=ImageGenerationApi(TryOnImageApi {error("This fixture uses Seedream only")},
            SeedreamImageApi {url ->
                assertEquals(SeedreamImageApi.ENDPOINT,url.toString())
                RecordingTransport(responseBytes) { count ->uploads+=count }
            })
        val executor=ImageTaskExecutor(coordinator,db,
            readCredentials={provider ->credentialReads++;ImageGenerationCredentials(provider,"controlled-no-network-key")},
            prepareTryOn=images::prepare,prepareMannequin=images::prepareMannequin,
            generateImage={_,_,_ ->error("Display tasks must use their dedicated image API")},
            preparePersonalModel=images::preparePersonalModel,preparePersonalTryOn=images::prepareWithPersonalModel,
            validateSourceImage={ImageFileHelper.isOwnedReadableImage(context,it)},prepareAccessory=images::prepareAccessory,
            generateMannequin=transportApi::generateMannequin,prepareSnapshotImages=images::prepareSnapshots)
        val runner=ImageTaskRunner(coordinator,executor::execute)
        suspend fun setup() {
            ImageFileHelper.configure(context,db)
            coordinator.awaitStartup()
            db.brandDao().insertBrand(Brand(id=1,name="受控品牌"))
        }
        fun source(bytes:ByteArray,extension:String="jpg",baseName:String=UUID.randomUUID().toString()):String =
            File(File(context.filesDir,"images").apply {mkdirs()},"$baseName.$extension").apply {writeBytes(bytes)}.path
        suspend fun submit(semantic:CategorySemantic,source:String,original:String?=null):String {
            val categoryId=db.categoryDao().insertCategory(Category(name="${semantic.label}-${UUID.randomUUID()}",
                group=if(semantic.family==WardrobeFamily.CLOTHING)CategoryGroup.CLOTHING else CategoryGroup.ACCESSORY,
                categorySemantic=semantic))
            val itemId=db.itemDao().insertItem(Item(brandId=1,categoryId=categoryId,name="受控商品",description="",
                imageUrls=listOf(original ?: source),status=ItemStatus.OWNED))
            val subject=requireNotNull(mannequinSubject(db.categoryDao().getCategoryById(categoryId),confirmedSemantics=true))
            val head=if(semantic==CategorySemantic.FULL_WIG)catalog.snapshot(catalog.templates.first().id)else null
            beforeSubmit(requireNotNull(db.itemDao().getItemById(itemId)))
            return coordinator.submitMannequin(MannequinRequest(itemId,source,subject=subject,
                wigHeadTemplate=head,referenceViewConfirmed=head!=null,originalImagePath=original),ImageGenerationProvider.SEEDREAM)
        }
        suspend fun close() {
            scope.coroutineContext.job.cancelAndJoin()
            ImageFileHelper.configure(AppModule.context(),AppModule.database())
            db.close();root.deleteRecursively()
        }
    }

    private fun bitmapBytes(format:Bitmap.CompressFormat):ByteArray {
        val bitmap=Bitmap.createBitmap(48,72,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(80,150,200))
            return ByteArrayOutputStream().use {out ->assertTrue(bitmap.compress(format,95,out));out.toByteArray()}
        } finally {bitmap.recycle()}
    }
    @Suppress("DEPRECATION")
    private suspend fun exercise(format:Format) {
        assumeTrue("Legacy platforms keep the existing WebP integrity restriction",format!=Format.WEBP_WITH_JPG_EXTENSION || android.os.Build.VERSION.SDK_INT>=28)
        val f=Fixture()
        try {
            f.setup()
            val bytes=bitmapBytes(when(format) {
                Format.PNG ->Bitmap.CompressFormat.PNG
                Format.JPEG ->Bitmap.CompressFormat.JPEG
                Format.WEBP_WITH_JPG_EXTENSION ->Bitmap.CompressFormat.WEBP
            })
            val source=f.source(bytes,if(format==Format.PNG)"png" else "jpg")
            assertTrue("$format must pass production source ownership/integrity",ImageFileHelper.isOwnedReadableImage(f.context,source))
            val ids=semantics.map {semantic ->f.submit(semantic,source)}
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
            f.runner.runQueue()
            ids.forEachIndexed {index,id ->
                val task=requireNotNull(f.db.imageTaskDao().get(id))
                assertEquals("$format/${semantics[index]}: ${task.error.orEmpty()}",ImageTaskStatus.AWAITING_CONFIRMATION,task.status)
                assertTrue(task.requestSent)
                assertEquals(4,ImageTaskRequest.decode(task.requestJson).version)
                assertTrue(ImageFileHelper.isOwnedGeneratedImage(f.context,requireNotNull(task.resultImagePath)))
                assertEquals(listOf(source),f.db.itemDao().getItemById(ImageTaskRequest.decode(task.requestJson).garments.single().itemId)!!.imageUrls)
            }
            assertEquals(listOf(2,1,1,1,2,2),f.uploads)
            assertEquals(6,f.credentialReads)
            assertArrayEquals(bytes,File(source).readBytes())
        } finally {f.close()}
    }

    @Test fun pngProductsAcrossSixModesReachTheControlledProductionExecutor()=runBlocking {exercise(Format.PNG)}
    @Test fun jpegProductsAcrossSixModesReachTheControlledProductionExecutor()=runBlocking {exercise(Format.JPEG)}
    @Test fun oldWebpBytesWithJpgExtensionAcrossSixModesReachTheControlledProductionExecutor()=runBlocking {exercise(Format.WEBP_WITH_JPG_EXTENSION)}

    @Suppress("DEPRECATION")
    @Test fun existingItemImagesWithLegacyNamesCanBeAdmittedAcrossPngJpegAndWebp()=runBlocking {
        val rejected=mutableListOf<String>()
        var totalControlledRequests=0
        for(format in Format.entries) {
            if(format==Format.WEBP_WITH_JPG_EXTENSION && android.os.Build.VERSION.SDK_INT<28)continue
            val f=Fixture()
            try {
                f.setup()
                val bytes=bitmapBytes(when(format) {
                    Format.PNG ->Bitmap.CompressFormat.PNG
                    Format.JPEG ->Bitmap.CompressFormat.JPEG
                    Format.WEBP_WITH_JPG_EXTENSION ->Bitmap.CompressFormat.WEBP
                })
                val source=f.source(bytes,if(format==Format.PNG)"png" else "jpg","legacy-product-${format.name.lowercase()}")
                // The template/personal-image policy remains strict even after product compatibility.
                assertFalse(ImageFileHelper.isOwnedReadableImage(f.context,source))
                val ids=mutableListOf<String>()
                for(semantic in semantics) {
                    val attempt=runCatching {f.submit(semantic,source)}
                    attempt.getOrNull()?.let {ids+=it}
                    attempt.exceptionOrNull()?.let {failure ->
                        rejected+="$format/$semantic:${(failure as? MannequinAdmissionFailure)?.kind?.name ?: "OTHER"}"
                    }
                }
                assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
                if(ids.isNotEmpty())f.runner.runQueue()
                ids.forEach {id ->
                    val task=requireNotNull(f.db.imageTaskDao().get(id))
                    assertEquals("Legacy filenames must also survive actual preparation and publication",ImageTaskStatus.AWAITING_CONFIRMATION,task.status)
                    assertTrue(task.requestSent)
                    assertTrue(ImageFileHelper.isOwnedGeneratedImage(f.context,requireNotNull(task.resultImagePath)))
                }
                assertEquals(listOf(2,1,1,1,2,2),f.uploads)
                assertEquals(6,f.credentialReads)
                totalControlledRequests+=f.uploads.size
                assertArrayEquals(bytes,File(source).readBytes())
            } finally {f.close()}
        }
        assertTrue("Existing Item references must not fail solely because of old filenames: $rejected",rejected.isEmpty())
        assertEquals(if(android.os.Build.VERSION.SDK_INT>=28)18 else 12,totalControlledRequests)
    }

    @Test fun eachPreQueueFailureHasAFixedActionableKindWithoutPrivateExceptionDetails()=runBlocking {
        for(kind in MannequinAdmissionFailureKind.entries) {
            val f=Fixture()
            try {
                f.setup()
                val source=f.source(bitmapBytes(Bitmap.CompressFormat.PNG),"png")
                when(kind) {
                    MannequinAdmissionFailureKind.SUBJECT ->f.beforeSubmit={item ->
                        val old=requireNotNull(f.db.categoryDao().getCategoryById(item.categoryId))
                        f.db.categoryDao().updateCategory(old.copy(categorySemantic=CategorySemantic.KC))
                    }
                    MannequinAdmissionFailureKind.REFERENCE ->f.beforeSubmit={item ->
                        f.db.itemDao().updateItem(item.copy(imageUrls=emptyList()))
                    }
                    MannequinAdmissionFailureKind.TEMPLATE ->f.snapshot={throw java.io.IOException("PRIVATE_TEMPLATE_PATH PRIVATE_KEY")}
                    MannequinAdmissionFailureKind.SOURCE ->File(source).delete()
                    MannequinAdmissionFailureKind.INPUT_CHANGED ->f.validateProduct={path ->
                        ImageFileHelper.isOwnedReadableProductImage(f.context,path).also {valid ->
                            if(valid && path==source)File(path).appendText("changed-after-validation")
                        }
                    }
                    MannequinAdmissionFailureKind.STORAGE ->f.db.openHelper.writableDatabase.execSQL(
                        "CREATE TRIGGER reject_mannequin_task BEFORE INSERT ON image_tasks BEGIN SELECT RAISE(ABORT, 'PRIVATE_STORAGE_PATH PRIVATE_KEY'); END")
                }
                val failure=runCatching {f.submit(CategorySemantic.JSK,source)}.exceptionOrNull()
                assertTrue("$kind must remain a typed local failure",failure is MannequinAdmissionFailure)
                failure as MannequinAdmissionFailure
                assertEquals(kind,failure.kind)
                assertEquals(kind.message,failure.message)
                assertTrue(failure.message.orEmpty().contains("本次未发送"))
                assertFalse(failure.toString().contains("PRIVATE_"))
                assertNull(failure.cause)
                assertTrue(f.db.imageTaskDao().getAll().isEmpty())
                assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
                f.staged.forEach {assertFalse("Failed admission releases its new template",File(it).exists())}
            } finally {f.close()}
        }
    }

    @Test fun strictSourceRejectionIsTypedAndTemplateCancellationIsNeverConvertedToFailure()=runBlocking {
        val f=Fixture()
        try {
            f.setup()
            val source=f.source(bitmapBytes(Bitmap.CompressFormat.PNG),"png")
            f.validateProduct={path ->path!=source && ImageFileHelper.isOwnedReadableProductImage(f.context,path)}
            val rejected=runCatching {f.submit(CategorySemantic.KC,source)}.exceptionOrNull()
            assertEquals(MannequinAdmissionFailureKind.SOURCE,(rejected as MannequinAdmissionFailure).kind)
            assertTrue(File(source).isFile)
            f.snapshot={throw CancellationException("controlled local cancellation")}
            val cancelled=runCatching {f.submit(CategorySemantic.JSK,source)}.exceptionOrNull()
            assertTrue(cancelled is CancellationException)
            assertFalse(cancelled is MannequinAdmissionFailure)
            assertTrue(f.db.imageTaskDao().getAll().isEmpty())
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
        } finally {f.close()}
    }

    @Test fun serviceStartFailureKeepsAnAlreadyQueuedTaskInterruptedInsteadOfReportingAnUnsentAdmissionFailure()=runBlocking {
        val f=Fixture()
        try {
            f.setup()
            val source=f.source(bitmapBytes(Bitmap.CompressFormat.PNG),"png")
            f.start={throw IllegalStateException("PRIVATE_SERVICE_MESSAGE")}
            val id=f.submit(CategorySemantic.JSK,source)
            val task=requireNotNull(f.db.imageTaskDao().get(id))
            assertEquals(ImageTaskStatus.INTERRUPTED,task.status)
            assertFalse(task.error.orEmpty().contains("PRIVATE_"))
            assertFalse(task.error.orEmpty().contains("本次未发送"))
            assertFalse(task.requestSent)
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
        } finally {f.close()}
    }

    @Suppress("DEPRECATION")
    @Test fun productFilenameCompatibilityStillRejectsExternalAliasedSymlinkedUriAndDamagedInputs()=runBlocking {
        val f=Fixture()
        try {
            f.setup()
            val bytes=bitmapBytes(Bitmap.CompressFormat.PNG)
            val source=f.source(bytes,"png","legacy-valid-product")
            assertTrue(ImageFileHelper.isOwnedReadableProductImage(f.context,source))
            assertFalse("Personal/template filename policy must stay unchanged",ImageFileHelper.isOwnedReadableImage(f.context,source))
            val outside=File(f.root,"outside.png").apply {writeBytes(bytes)}
            val imageRoot=requireNotNull(File(source).parentFile)
            val insideAlias=File(imageRoot,"../images/${File(source).name}").path
            val link=File(imageRoot,"legacy-link.png")
            android.system.Os.symlink(source,link.path)
            val outsideLink=File(imageRoot,"outside-link.png")
            android.system.Os.symlink(outside.path,outsideLink.path)
            for(path in listOf(outside.path,insideAlias,link.path,outsideLink.path,android.net.Uri.fromFile(File(source)).toString())) {
                assertFalse("Product ownership must reject external or aliased input",ImageFileHelper.isOwnedReadableProductImage(f.context,path))
            }
            val aliasFiles=File(f.root,"aliased-files").apply {mkdirs()}
            android.system.Os.symlink(imageRoot.path,File(aliasFiles,"images").path)
            val aliasContext=object:ContextWrapper(f.context) {override fun getFilesDir()=aliasFiles}
            assertFalse("The image directory itself must not escape through a symlink",ImageFileHelper.isOwnedReadableProductImage(aliasContext,
                File(File(aliasFiles,"images"),File(source).name).path))
            for(format in listOf(Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG,Bitmap.CompressFormat.WEBP)) {
                val complete=bitmapBytes(format)
                val damaged=f.source(complete.copyOf(complete.size/2),"jpg","legacy-damaged-${format.name.lowercase()}")
                assertFalse("Old naming never bypasses complete-image validation",ImageFileHelper.isOwnedReadableProductImage(f.context,damaged))
            }
            if(android.os.Build.VERSION.SDK_INT<28) {
                val webp=f.source(bitmapBytes(Bitmap.CompressFormat.WEBP),"jpg","legacy-complete-webp")
                assertFalse("API 26/27 format restriction remains intact",ImageFileHelper.isOwnedReadableProductImage(f.context,webp))
            }
            assertArrayEquals(bytes,File(source).readBytes())
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
        } finally {f.close()}
    }

    @Test fun productFilenamePermissionNeverAppliesToTemplatesOrTryOnInputs()=runBlocking {
        val f=Fixture()
        try {
            f.setup()
            val source=f.source(bitmapBytes(Bitmap.CompressFormat.PNG),"png","legacy-product-only")
            f.snapshot={source}
            val templateFailure=runCatching {f.submit(CategorySemantic.JSK,source)}.exceptionOrNull()
            assertEquals(MannequinAdmissionFailureKind.TEMPLATE,(templateFailure as MannequinAdmissionFailure).kind)
            assertTrue("The existing Item still protects the product",File(source).isFile)
            f.snapshot={ImageFileHelper.copyAssetToInternalStorage(f.context,it).also {path ->f.staged+=path}}
            val item=f.db.itemDao().getAllItemsList().single()
            val tryOnFailure=runCatching {f.coordinator.submitTryOn(listOf(TryOnSelection(item.id,item.name,source,TryOnRole.MAIN,
                CategorySemantic.JSK,ItemComposition.SINGLE)),ImageGenerationProvider.SEEDREAM)}.exceptionOrNull()
            assertNotNull(tryOnFailure)
            assertFalse(tryOnFailure is MannequinAdmissionFailure)
            assertFalse(ImageFileHelper.isOwnedReadableImage(f.context,source))
            assertTrue(ImageFileHelper.isOwnedReadableProductImage(f.context,source))
            assertTrue(f.db.imageTaskDao().getAll().isEmpty())
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
        } finally {f.close()}
    }

    @Test fun unreferencedDerivedInputsKeepStrictNamesWhileLegacyReferencedOriginalsRemainUsable()=runBlocking {
        val f=Fixture()
        try {
            f.setup()
            val originalBytes=bitmapBytes(Bitmap.CompressFormat.PNG)
            val original=f.source(originalBytes,"png","legacy-original-product")
            val unreferenced=f.source(originalBytes,"png","legacy-unreferenced-derived")
            assertTrue(ImageFileHelper.isOwnedReadableProductImage(f.context,original))
            assertTrue(ImageFileHelper.isOwnedReadableProductImage(f.context,unreferenced))
            val rejected=runCatching {f.submit(CategorySemantic.JSK,unreferenced,original)}.exceptionOrNull()
            assertEquals(MannequinAdmissionFailureKind.SOURCE,(rejected as MannequinAdmissionFailure).kind)
            assertTrue(f.db.imageTaskDao().getAll().isEmpty())
            assertTrue(File(unreferenced).isFile)
            assertArrayEquals(originalBytes,File(original).readBytes())
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())

            val crop=ImageRegionCropper.crop(f.context,original,NormalizedCropRect.FULL).path
            assertTrue(ImageFileHelper.isOwnedReadableImage(f.context,crop))
            val id=f.submit(CategorySemantic.JSK,crop,original)
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
            f.runner.runQueue()
            val task=requireNotNull(f.db.imageTaskDao().get(id))
            assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION,task.status)
            assertEquals(original,ImageTaskRequest.decode(task.requestJson).productOriginalImagePath)
            assertEquals(crop,task.sourceImagePaths.last())
            assertEquals(listOf(2),f.uploads)
            assertEquals(1,f.credentialReads)
            assertArrayEquals(originalBytes,File(original).readBytes())
        } finally {f.close()}
    }

    /** Root may stage authorized local snapshots here; never package originals into APK resources. */
    @Test fun stagedPrivateHistoricalImagesPassProductionAdmissionWithoutPublishingTheirDetails()=runBlocking {
        assumeTrue("Opt-in private local fixture inventory only",InstrumentationRegistry.getArguments()
            .getString("mannequinPrivateFixtures")=="legacy-20261018")
        val sources=File(target.filesDir,"mannequin-native-legacy-inputs").listFiles().orEmpty().filter {it.isFile}
        require(sources.isNotEmpty()) {"No private fixture images were staged"}
        val f=Fixture()
        val counts=linkedMapOf<String,Int>()
        fun count(key:String) {counts[key]=(counts[key]?:0)+1}
        try {
            f.setup()
            for(original in sources) {
                val path=f.source(original.readBytes(),original.extension,original.nameWithoutExtension)
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,bounds)
                val format=when(bounds.outMimeType) {
                    "image/jpeg" ->"JPEG"
                    "image/png" ->"PNG"
                    "image/webp" ->"WEBP"
                    "image/gif" ->"GIF"
                    else ->"OTHER"
                }
                count("input_$format")
                val accepted=ImageFileHelper.isOwnedReadableProductImage(f.context,path)
                count(if(accepted)"readable" else "rejected")
                if(!accepted)count("rejected_$format")
                if(!accepted)continue
                // Exercise admission on every legacy image with the stable JSK subject.
                val submitted=runCatching {f.submit(CategorySemantic.JSK,path)}
                count(if(submitted.isSuccess)"admitted" else "admissionFailed")
            }
            // No network or image generation is needed to find the reported pre-queue failure.
            assertEquals(0,f.credentialReads);assertTrue(f.uploads.isEmpty())
            File(target.filesDir,"mannequin-native-legacy-summary.json").writeText(JsonObject().apply {
                addProperty("total",sources.size)
                counts.forEach {(key,value) ->addProperty(key,value)}
            }.toString())
            assertEquals("See private aggregate summary; no filenames or business contents are exported",0,counts["rejected"]?:0)
            assertEquals(0,counts["admissionFailed"]?:0)
        } finally {f.close()}
    }

    private class RecordingTransport(png:ByteArray,private val onUploaded:(Int)->Unit):HttpURLConnection(URL(SeedreamImageApi.ENDPOINT)) {
        private val sent=ByteArrayOutputStream()
        private val response="""{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(png)}"}]}""".toByteArray()
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun disconnect()=Unit
        override fun getOutputStream()=sent
        override fun getResponseCode():Int {
            val payload=JsonParser.parseString(sent.toString("UTF-8")).asJsonObject
            onUploaded(payload["image"].asJsonArray.size())
            return 200
        }
        override fun getInputStream()=ByteArrayInputStream(response)
    }
}
