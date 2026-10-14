package com.lolita.app.ui.screen.price
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lolita.app.ui.screen.`import`.TaobaoProductWebReader
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class TaobaoDetailsReaderTest {
 private fun read(html:String):JsonObject {
  val instrument=InstrumentationRegistry.getInstrumentation();val loaded=CountDownLatch(1);val done=CountDownLatch(1)
  lateinit var view:WebView;var result:JsonObject?=null
  instrument.runOnMainSync {
   view=WebView(instrument.targetContext);view.settings.javaScriptEnabled=true
   view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,url:String){loaded.countDown()}}
   view.loadDataWithBaseURL("https://item.taobao.com/item.htm?id=1047259846364",html,"text/html","UTF-8",null)
  }
  assertTrue(loaded.await(5,TimeUnit.SECONDS))
  instrument.runOnMainSync {view.evaluateJavascript(TaobaoProductWebReader.script){result=JsonParser.parseString(JsonParser.parseString(it).asString).asJsonObject;done.countDown()}}
  assertTrue(done.await(5,TimeUnit.SECONDS));instrument.runOnMainSync{view.destroy()};return result!!
 }
 @Test fun v7LazyOriginalsAreReadOnlyFromDescription() {
  val result=read("""<img src='https://img.alicdn.com/shop-avatar.jpg'>
   <div class='detail-desc detail-desc-v3'><div><img id='lazy' src='https://img.alicdn.com/a-2-tps-790-300.png'></div><img data-src='https://img.alicdn.com/detail2.jpg' src='https://img.alicdn.com/a-2-tps-790-300.png'></div>
   <script>document.getElementById('lazy')['__reactFiber${'$'}fixture']={memoizedProps:{src:'https://img.alicdn.com/a-2-tps-790-300.png'},return:{memoizedProps:{imageUrl:'https://img.alicdn.com/detail1.jpg'}}};</script>""")
  assertEquals(listOf("https://img.alicdn.com/detail1.jpg","https://img.alicdn.com/detail2.jpg"),result.getAsJsonArray("detailImages").map{it.asJsonObject["url"].asString})
  assertEquals("complete",result["detailStatus"]?.asString)
 }
 @Test fun unresolvedPlaceholdersRemainPending() {
  val result=read("<div class='detail-desc'><img src='https://img.alicdn.com/a-2-tps-790-300.png'></div>")
  assertEquals("pending",result["detailStatus"]?.asString)
 }
 @Test fun missingDescriptionIsNotAnEmptySuccessfulDetail() {assertEquals("pending",read("<div>商品标题已加载</div>")["detailStatus"]?.asString)}
 @Test fun legacyContainerStillLoadsAndExcludesShopPicture() {
  val result=read("<img src='https://img.alicdn.com/shop.jpg'><div id='v6_pc_content'><img data-src='https://img.alicdn.com/original.jpg' src='https://img.alicdn.com/a-2-tps-2-2.png'></div>")
  assertEquals(1,result.getAsJsonArray("detailImages").size());assertEquals("complete",result["detailStatus"]?.asString)
 }
 @Test fun loadingEllipsisIsNotACompletedTextDescription() {assertEquals("pending",read("<div class='detail-desc'>加载中...</div>")["detailStatus"]?.asString)}
 @Test fun failedLoadingParagraphIsNotCompleted() {assertEquals("pending",read("<div class='detail-desc'><p>加载失败，请重试</p></div>")["detailStatus"]?.asString)}
 @Test fun genuineDescriptionParagraphMayBeTextOnly() {assertEquals("complete",read("<div class='detail-desc'><p>商品材质：棉。请按尺寸表选择。</p></div>")["detailStatus"]?.asString)}

}
