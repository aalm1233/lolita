package com.lolita.app.ui.screen.square

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.repository.*
import com.lolita.app.data.square.*
import com.lolita.app.ui.theme.*
import com.lolita.app.ui.theme.skin.component.SkinArtBackground
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.net.ServerSocket
import java.util.UUID

/** Real host SQLite API; each test owns its Room, cache, preferences and image loader. */
class SquareNetworkTest {
    @get:Rule val compose=createComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val target=inst.targetContext
    private val root=File(target.cacheDir,"square-net-${UUID.randomUUID()}")
    private val context=object:ContextWrapper(target){
        override fun getFilesDir()=File(root,"files").apply{mkdirs()}
        override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
        override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
    }
    private lateinit var db:LolitaDatabase
    private lateinit var repo:SquareRepository
    private lateinit var drafts:ItemDraftRepository
    private var vm:SquareViewModel?=null
    private val models=ViewModelStore()
    private val skin=mutableStateOf(SkinType.DEFAULT)
    private val dark=mutableStateOf(false)
    private lateinit var host:android.view.View
    private val base get()=InstrumentationRegistry.getArguments().getString("squareServiceUrl")?:"http://10.0.2.2:18765"
    private fun <T> io(block:suspend()->T):T=runBlocking(Dispatchers.IO){block()}
    @Before fun setup(){
        db=Room.inMemoryDatabaseBuilder(context,LolitaDatabase::class.java).build()
        drafts=ItemDraftRepository(db,ItemRepository(db.itemDao(),database=db,context=context))
        repo=SquareRepository(context,db,drafts)
        ImageFileHelper.configure(context,db)
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).components{add(SquareImageFetcher.Factory{repo})}.build())
        io{repo.load()}
    }
    @After fun cleanup(){
        inst.runOnMainSync{models.clear()};io{db.close()};root.deleteRecursively()
        coil.Coil.setImageLoader((target.applicationContext as coil.ImageLoaderFactory).newImageLoader())
        ImageFileHelper.configure(target,com.lolita.app.di.AppModule.database())
    }
    private fun online(){io{repo.configureService(base);repo.refreshSilently()};assertTrue(repo.catalog.value!!.products.size>=28)}
    private fun show(){
        inst.runOnMainSync{vm=SquareViewModel(repo,SavedStateHandle(),MutableStateFlow(true));models.put("vm",vm!!)}
        compose.setContent{
            host=LocalView.current
            val cfg=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides cfg){
                LolitaTheme(skin.value,dark.value){Surface(color=MaterialTheme.colorScheme.background){
                    Box(Modifier.fillMaxSize()){SkinArtBackground();SquareScreen({}, {},vm!!)}
                }}
            }
        }
        compose.waitUntil(15000){vm!!.busy.value==null}
    }
    private fun shot(name:String){
        compose.waitForIdle();inst.waitForIdleSync()
        if(InstrumentationRegistry.getArguments().getString("externalScreenshots")=="true"){
            val marker=File(target.filesDir,"square-network-capture.txt");marker.writeText(name)
            val deadline=System.currentTimeMillis()+15000
            while(marker.exists()&&System.currentTimeMillis()<deadline)Thread.sleep(50)
            assertFalse("External native screenshot must acknowledge $name",marker.exists());return
        }
        val dir=File(target.filesDir,"square-network-native").apply{mkdirs()}
        val bitmap=compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun realServiceMetadataOnlyConcurrentRefreshAndRestart(){
        online();val before=repo.catalog.value!!
        assertTrue(File(context.filesDir,"square_catalog/images").listFiles().orEmpty().isEmpty())
        io{coroutineScope{List(4){async{repo.refreshSilently()}}.awaitAll()}}
        assertEquals(before,repo.catalog.value)
        val restart=SquareRepository(context,db,drafts)
        io{restart.load()}
        val archive=File(target.filesDir,"square-network-native").apply{mkdirs()}
        File(archive,"expected.json").writeText(SquarePackage.gson.toJson(before))
        File(archive,"actual.json").writeText(SquarePackage.gson.toJson(restart.catalog.value))
        assertTrue("Restart must preserve all catalog metadata",before==restart.catalog.value);assertEquals(base,restart.serviceUrl.value)
        io{restart.configureService("http://127.0.0.1:9");restart.refreshSilently()}
        assertEquals(before,restart.catalog.value)
        assertEquals(0,io{db.itemDao().getItemCount()});assertEquals(0,io{db.priceDao().getAllPricesList().size})
    }
    @Test fun thumbnailOriginalAndWishHaveIndependentOfflineImages(){
        online();val c=repo.catalog.value!!;val e=c.events.first{it.id=="dorothy-cotton"}
        val part=SquareBrowse.parts(c,e).first{it.partKey=="JSK"};val choice=SquareBrowseChoice(part.key,"蓝色")
        val selection=SquareBrowse.select(c,part,choice);val path=selection.photos.first()
        val thumb=io{repo.fetchImage(path,true)}
        assertTrue(thumb.path.contains("thumbnails"));assertFalse(File(context.filesDir,"square_catalog/$path").exists())
        val original=io{repo.fetchImage(path,false)};assertTrue(original.isFile);assertNotEquals(thumb,original)
        val result=io{repo.addWishes(SquareBrowse.request(c,e,listOf(choice),false))}
        assertEquals(1,result.added)
        val item=io{db.itemDao().getItemById(result.itemIds.single())!!}
        assertEquals(ItemStatus.WISHED,item.status);assertNull(item.size)
        assertTrue(item.imageUrls.all{File(it).isFile&&!it.contains("square_catalog")})
        io{repo.configureService("http://127.0.0.1:9");repo.refreshSilently()}
        assertEquals(original,io{repo.fetchImage(path,false)})
        assertTrue(item.imageUrls.all{File(it).isFile});assertEquals(0,io{db.priceDao().getAllPricesList().size})
        assertEquals(0,io{repo.addWishes(SquareBrowse.request(c,e,listOf(choice),false))}.added)
    }
    @Test fun incrementalUpdateBadCursorAndCorruptImagePreserveCache(){
        online();val initial=repo.catalog.value!!
        val product=initial.products.first()
        val revised=initial.copy(products=initial.products.map{if(it.id==product.id)it.copy(revision=it.revision+1,title=it.title+"（测试修订）")else it})
        val server=ServerSocket(0)
        val mode=java.util.concurrent.atomic.AtomicInteger(1)
        val thread=Thread{
            try{while(!server.isClosed)server.accept().use{s->
                val line=s.getInputStream().bufferedReader().readLine().orEmpty()
                val after=Regex("after=([0-9]+)").find(line)?.groupValues?.get(1)?.toLong()?:0L
                val body=if(line.contains("/v1/health"))"""{"format":"lolita.square.api","schemaVersion":1,"serverId":"controlled"}"""
                else if(line.contains("/v1/images/"))"BAD"
                else if(mode.get()==3)"""{"serverId":"controlled","cursor":0,"hasMore":false,"changes":[]}"""
                else {
                    val seq=mode.get().toLong()
                    val rows=if(after<seq)listOf(mapOf("seq" to seq,"catalog" to if(seq==1L)initial else revised))else emptyList()
                    com.google.gson.GsonBuilder().serializeNulls().create().toJson(mapOf("serverId" to "controlled","cursor" to seq,"hasMore" to false,"changes" to rows))
                }
                val bytes=body.toByteArray(Charsets.UTF_8)
                s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()+bytes)
            }}catch(_:Exception){}
        }.apply{isDaemon=true;start()}
        try{
            io{repo.configureService("http://127.0.0.1:${server.localPort}");repo.refreshSilently()}
            assertEquals(initial,repo.catalog.value)
            mode.set(2);io{repo.refreshSilently()}
            assertTrue(repo.catalog.value!!.products.single{it.id==product.id}.title.endsWith("（测试修订）"))
            val before=repo.catalog.value
            mode.set(3);io{repo.refreshSilently()};assertEquals(before,repo.catalog.value)
            val path=product.images.first().path
            try{io{repo.fetchImage(path,false)};fail("Corrupt image must be rejected")}catch(_:SquareNetworkException){}
            assertFalse(File(context.filesDir,"square_catalog/$path").exists())
            val restart=SquareRepository(context,db,drafts);io{restart.load()};assertEquals(before,restart.catalog.value)
        }finally{server.close();thread.join(1000)}
    }
    @Test fun invalidResponsesNeverReplaceCatalogOrProduceMessage(){
        online();val before=repo.catalog.value
        val server=ServerSocket(0)
        val thread=Thread{
            try{repeat(2){server.accept().use{s->s.getInputStream().bufferedReader().readLine();s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nBAD".toByteArray())}}}catch(_:Exception){}
        }.apply{isDaemon=true;start()}
        try{
            io{repo.configureService("http://127.0.0.1:${server.localPort}");repo.refreshSilently()}
            assertEquals(before,repo.catalog.value)
            show();compose.runOnIdle{vm!!.refresh()}
            compose.waitUntil(10000){!vm!!.refreshing.value}
            assertNull(vm!!.message.value);compose.onNodeWithTag("square-feed").assertExists()
        }finally{server.close();thread.join(1000)}
    }
    @Test fun disconnectedEmptyStateRefreshIsSilentAndImportStaysAvailable(){
        io{repo.configureService("http://127.0.0.1:9")};show()
        compose.onNodeWithTag("square-refresh").performTouchInput{swipeDown()}
        compose.waitUntil(10000){!vm!!.refreshing.value}
        compose.onNodeWithTag("square-import").assertIsDisplayed()
        compose.onNodeWithText("暂无店铺资料").assertIsDisplayed()
        assertNull(vm!!.message.value);assertNull(vm!!.catalog.value)
        shot("empty-offline")
    }
    @Test fun fourteenSkinsCachedOfflineFeedAndServiceDialog(){
        online()
        val c=repo.catalog.value!!
        io{SquareVisibility.events(c).take(4).forEach{e->
            val p=SquareVisibility.products(c,e).let{ps->ps.firstOrNull{it.pricePhase=="FULL"}?:ps.first()}
            repo.fetchImage(p.images.filter{it.role=="main"}.minBy{it.order}.path,true)
        }}
        show()
        // Warm visible images, then check the same content with an unreachable service.
        io{repo.configureService("http://127.0.0.1:9")}
        for(type in SkinType.entries)for(night in listOf(false,true)){
            compose.runOnIdle{skin.value=type;dark.value=night}
            compose.onNodeWithTag("square-feed").assertExists()
            val suffix="${type.name.lowercase()}-${if(night)"dark" else "light"}"
            shot("feed-$suffix")
            compose.onNodeWithTag("square-service").performClick()
            compose.onNodeWithTag("square-service-address").assertIsDisplayed()
            compose.onNodeWithTag("square-service-save").assertIsDisplayed();shot("service-$suffix")
            compose.onNodeWithText("取消").performClick()
        }
        compose.onNodeWithTag("square-refresh").performTouchInput{swipeDown()}
        compose.waitUntil(10000){!vm!!.refreshing.value};assertNull(vm!!.message.value)
    }
    @Test fun serviceAddressValidationCancelAndRealKeyboard(){
        show();compose.onNodeWithTag("square-service").performClick()
        compose.onNodeWithTag("square-service-address").performTextInput("http://public.example.com")
        compose.onNodeWithTag("square-service-save").performClick()
        compose.onNodeWithText("内网可使用HTTP，其他服务请使用HTTPS").assertExists()
        compose.onNodeWithTag("square-service-address").performTextReplacement(base)
        compose.waitUntil(10000){ViewCompat.getRootWindowInsets(host)?.isVisible(WindowInsetsCompat.Type.ime())==true}
        compose.onNodeWithTag("square-service-save").assertIsDisplayed();shot("service-real-ime")
        compose.onNodeWithTag("square-service-save").performClick()
        compose.waitUntil(15000){vm!!.catalog.value!=null}
        compose.onNodeWithTag("square-feed").assertExists();assertEquals(base,repo.serviceUrl.value)
        compose.onNodeWithTag("square-service").performClick()
        compose.onNodeWithTag("square-service-address").performTextClearance()
        compose.onNodeWithText("取消").performClick();assertEquals(base,repo.serviceUrl.value)
    }
}
