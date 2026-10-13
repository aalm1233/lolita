package com.lolita.app.data.file

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TaobaoProductPriceTest {
    private fun raw() = javaClass.getResource("/taobao-shouwang-price.json")!!.readText()
    private fun quote(draft: TaobaoProductDraft) = Gson().toJsonTree(draft).asJsonObject.getAsJsonObject("referenceQuote")
    @Test fun selectedMaleSetUses658InsteadOfListing449() {
        val draft = TaobaoProductParser.parse(raw())
        val parts = Gson().toJsonTree(draft).asJsonObject.getAsJsonArray("variants").map { it.asJsonObject }
        assertEquals("658", parts.single { it["name"].asString == "男款SET" }.getAsJsonObject("referenceQuote")?.get("amount")?.asString)
        assertNull(parts.single { it["name"].asString == "OP（长款）" }.get("referenceQuote"))
        assertTrue(draft.description.contains("页面起价"))
    }
    @Test fun livePanelReplacesInitialSkuAssociation() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","Lady70","OP（长款）"],"priceText":"449","priceTitle":""}"""))
        val draft = TaobaoProductParser.parse(root.toString())
        val parts = Gson().toJsonTree(draft).asJsonObject.getAsJsonArray("variants").map { it.asJsonObject }
        assertEquals("449", parts.single { it["name"].asString == "OP（长款）" }.getAsJsonObject("referenceQuote")?.get("amount")?.asString)
        assertNull(parts.single { it["name"].asString == "男款SET" }.get("referenceQuote"))
    }
    @Test fun priceAssociationUsesStyleDimensionEvenWhenAColorSharesAnotherStyleName() {
        val root = JsonParser.parseString(raw()).asJsonObject
        val data = root.getAsJsonObject("entry").getAsJsonArray("apiStack")[0].asJsonObject.getAsJsonObject("data")
        val sku = JsonParser.parseString(data.getAsJsonObject("skuData")["skuV3Data"].asString).asJsonObject
        val base = sku.getAsJsonObject("global").getAsJsonObject("skuGlobal").getAsJsonObject("context").getAsJsonObject("skuBase")
        base.getAsJsonArray("props")[0].asJsonObject.getAsJsonArray("values").single { it.asJsonObject["vid"].asString == "6460464" }.asJsonObject.addProperty("name", "OP（长款）")
        data.getAsJsonObject("skuData").addProperty("skuV3Data",sku.toString())
        val parts = Gson().toJsonTree(TaobaoProductParser.parse(root.toString())).asJsonObject.getAsJsonArray("variants").map { it.asJsonObject }
        assertNull(parts.single { it["name"].asString == "OP（长款）" }.get("referenceQuote"))
        assertEquals("658", parts.single { it["name"].asString == "男款SET" }.getAsJsonObject("referenceQuote")?.get("amount")?.asString)
    }
    @Test fun livePriceTailCannotBeDropped() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","ManS","男款SET"],"priceText":"658","priceTail":"起"}"""))
        assertNull(quote(TaobaoProductParser.parse(root.toString())))
    }
    @Test fun selectedControlsCannotBorrowAStaleHeaderPrice() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","Lady70","OP（长款）"],"selectedText":"已选“白+黑；ManS；男款SET”","priceText":"658"}"""))
        assertNull(quote(TaobaoProductParser.parse(root.toString())))
    }
    @Test fun detectsDepositInCompletePriceSummaryAfterCouponLabel() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","ManS","男款SET"],"priceText":"90","priceTitle":"券后","priceSummary":"券后90 定金"}"""))
        assertNull(quote(TaobaoProductParser.parse(root.toString())))
    }
    @Test fun completePriceSummaryCannotHideRangeAfterFirstNumber() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","ManS","男款SET"],"priceText":"449","priceTitle":"","priceSummary":"￥449-658"}"""))
        assertNull(quote(TaobaoProductParser.parse(root.toString())))
    }
    @Test fun completeLiveMaleQuoteRemainsUsable() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["白+黑","ManS","男款SET"],"selectedText":"已选“白+黑；ManS；男款SET”","priceText":"658","priceSummary":"价格：658"}"""))
        assertEquals("658",quote(TaobaoProductParser.parse(root.toString()))?.get("amount")?.asString)
    }
    @Test fun partialLiveSelectionMustNotReuseInitialQuote() {
        val root = JsonParser.parseString(raw()).asJsonObject
        root.add("liveSku", JsonParser.parseString("""{"present":true,"selectedNames":["男款SET"],"priceText":"658"}"""))
        assertNull(quote(TaobaoProductParser.parse(root.toString())))
    }
    @Test fun rejectsRangeStartingAndDepositQuotes() {
        for ((amount, label) in listOf("449-658" to "", "658起" to "", "90" to "定金", "359" to "尾款")) {
            val root = JsonParser.parseString(raw()).asJsonObject
            root.add("liveSku", Gson().toJsonTree(mapOf("present" to true,"selectedNames" to listOf("白+黑","ManS","男款SET"),"priceText" to amount,"priceTitle" to label)))
            assertNull("$amount $label",quote(TaobaoProductParser.parse(root.toString())))
        }
    }
}
