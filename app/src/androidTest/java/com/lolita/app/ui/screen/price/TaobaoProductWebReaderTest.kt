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

/** Executes the actual extraction script in Android WebView without network or real account data. */
class TaobaoProductWebReaderTest {
    private fun read(price: String, suffix: String, label: String = ""): JsonObject {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        val extracted = CountDownLatch(1)
        lateinit var view: WebView
        var result: JsonObject? = null
        instrument.runOnMainSync {
            view = WebView(instrument.targetContext)
            view.settings.javaScriptEnabled = true
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) { loaded.countDown() }
            }
            view.loadDataWithBaseURL("https://item.taobao.com/item.htm?id=927238692735", """
                <div aria-label='已选“白+黑；ManS；男款SET”'>已选“白+黑；ManS；男款SET”</div>
                <div aria-label='价格：$label $price$suffix'>$label <span class='priceText--test'>$price</span><span>$suffix</span></div>
                <div role='button' aria-label='已选中 白+黑， '><span class='skuValueName--test'>白+黑</span></div>
                <div role='button' aria-label='已选中 ManS， '><span class='skuValueName--test'>ManS</span></div>
                <div role='button' aria-label='已选中 男款SET， '><span class='skuValueName--test'>男款SET</span></div>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        assertTrue(loaded.await(5, TimeUnit.SECONDS))
        instrument.runOnMainSync { view.evaluateJavascript(TaobaoProductWebReader.script) {
            result = JsonParser.parseString(JsonParser.parseString(it).asString).asJsonObject.getAsJsonObject("liveSku")
            extracted.countDown()
        } }
        assertTrue(extracted.await(5, TimeUnit.SECONDS))
        instrument.runOnMainSync { view.destroy() }
        return result!!
    }
    @Test fun readsStartingSuffixOutsideNumberSpan() { assertEquals("起",read("449", "起")["priceTail"]?.asString) }
    @Test fun preservesDepositMeaningEvenWhenCouponLabelComesFirst() { assertTrue(read("90", " 定金", "券后")["priceSummary"]?.asString?.contains("定金") == true) }
    @Test fun quotationCarriesItsOwnHeaderSpecification() { assertEquals("已选“白+黑；ManS；男款SET”",read("658", "")["selectedText"]?.asString) }
}
