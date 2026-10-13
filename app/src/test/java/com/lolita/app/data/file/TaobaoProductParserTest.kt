package com.lolita.app.data.file
import org.junit.Assert.*
import org.junit.Test
class TaobaoProductParserTest {
 @Test fun acceptsShareAndRejectsUntrustedHosts() {
  assertEquals("https://e.tb.cn/h.demo?tk=abc", TaobaoProductParser.shareUrl("【淘宝】https://e.tb.cn/h.demo?tk=abc 「斗篷」"))
  assertNull(TaobaoProductParser.shareUrl("https://e.tb.cn.evil.test/h.demo"))
  assertNull(TaobaoProductParser.shareUrl("https://example.com/item.htm?id=1"))
  assertNull(TaobaoProductParser.shareUrl("http://item.taobao.com/item.htm?id=1"))
 }
 @Test fun extractsProductWithoutInventingSelectedColorOrPayment() {
  val json="""{"url":"https://pages-g.m.taobao.com/wow/z/app/detail-next/item/index?id=123&x-ssr=true","text":"全款：1280元\n定金256元，尾款1024元","entry":{"apiStack":[{"data":{"titleVO":{"title":"预约斗篷"},"featureVO":{"seller":{"shopName":"店铺"}},"paramsVO":{"groupProps":[{"基本信息":[{"品牌":"品牌"},{"尺码":"均码"},{"颜色分类":"黑色,灰色"}]}]},"priceVO":{"price":{"priceTitle":"券后","priceText":"253.44"}},"headPicVO":{"images":[{"url":"https://img.alicdn.com/a.jpg"},{"url":"https://evil.test/a.jpg"}]}}}]}}"""
  val draft=TaobaoProductParser.parse(json)
  assertEquals("预约斗篷",draft.name)
  assertEquals("均码",draft.size)
  assertEquals(1,draft.remoteImages.size)
  assertTrue(draft.description.contains("尾款1024元"))
  assertTrue(draft.description.contains("253.44"))
  assertEquals("https://item.taobao.com/item.htm?id=123",draft.productUrl)
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsLoginPage() { TaobaoProductParser.parse("""{"url":"https://login.m.taobao.com/","text":"登录"}""") }
 @Test fun readsMixedCategoryAndDetailImagesForGenericBrand() {
  val raw="""{"url": "https://item.taobao.com/item.htm?id=927238692735", "detailImages": [{"url": "//img.alicdn.com/detail1.jpg"}, {"url": "https://img.alicdn.com/detail1.jpg"}, {"url": "https://img.alicdn.com/detail2.jpg"}, {"url": "https://evil.test/a.jpg"}, {"url": "https://img.alicdn.com/O1CN01x-2-tps-790-300.png"}], "entry": {"apiStack": [{"data": {"titleVO": {"title": "【现货掉落不包邮】守望者1.0"}, "featureVO": {"seller": {"shopName": "新路德维希Neo Ludwig洋装"}}, "paramsVO": {"groupProps": [{"基本信息": [{"品牌": "other/其他"}, {"套装": "OP（长款）,OP（短款）,男款SET,头饰,头巾,假领"}]}]}}}]}}"""
  val draft=TaobaoProductParser.parse(raw)
  assertEquals("",draft.brand)
  assertEquals("新路德维希Neo Ludwig洋装",draft.shopName)
  assertEquals("OP",draft.category)
  assertTrue(draft.categoryCandidates.size>1)
  assertEquals(listOf("https://img.alicdn.com/detail1.jpg","https://img.alicdn.com/detail2.jpg"),draft.remoteDetailImages)
 }
 @Test fun matchesOnlyVerifiedBrandAliasesAndRefusesAmbiguousNames() {
  assertEquals("新路德维希（Neo Ludwig）", TaobaoProductParser.matchBrand(listOf("新路德维希（Neo Ludwig）"),listOf("新路德维希（Neo Ludwig）","新路德维希Neo Ludwig洋装")))
  assertEquals("OzzonJapan",TaobaoProductParser.matchBrand(listOf("OzzonJapan"),listOf("OZZON JAPAN")))
  assertNull(TaobaoProductParser.matchBrand(listOf("新路德维希其他店"),listOf("新路德维希")))
  assertNull(TaobaoProductParser.matchBrand(listOf("OzzonJapan","Ozzon Japan"),listOf("OzzonJapan")))
 }


 @Test fun splitsExplicitSetIntoSixIndependentlyCategorizedItems() {
  val variants = parsedVariants(listOf("套装" to "OP（长款）,OP（短款）,男款SET,头饰,头巾,假领"))
  assertEquals(listOf("OP（长款）", "OP（短款）", "男款SET", "头饰", "头巾", "假领"), variants.map { it["name"].asString })
  assertEquals(listOf("OP", "OP", "", "其他头饰", "其他头饰", "其他配饰"), variants.map { it["category"].asString })
  assertEquals(6, variants.map { it["id"].asString }.toSet().size)
  assertTrue(variants.all { it["id"].asString.isNotBlank() })
 }

 @Test fun readsModernSkuStylesBeforeParameterFallback() {
  val sku = modernSku(listOf(
   skuProperty("1627207", "颜色分类", listOf("1" to "黑色", "2" to "白色")),
   skuProperty("20509", "尺码", listOf("10" to "Lady65", "11" to "Lady70")),
   skuProperty("30000", "款式", listOf("20" to "OP（长款）", "21" to "头饰"))
  ))
  val variants = parsedVariants(listOf("套装" to "错误备用项目,另一项目"), sku)
  assertEquals(listOf("OP（长款）", "头饰"), variants.map { it["name"].asString })
  assertEquals(listOf("OP", "其他头饰"), variants.map { it["category"].asString })
  assertEquals(variants, parsedVariants(listOf("套装" to "错误备用项目,另一项目"), sku))
 }

 @Test fun doesNotSplitColorOrSizeOptionsIntoItems() {
  val sku = modernSku(listOf(
   skuProperty("1627207", "颜色分类", listOf("1" to "OP黑色", "2" to "OP白色")),
   skuProperty("20509", "尺码", listOf("10" to "Lady65", "11" to "Lady70"))
  ))
  assertTrue(parsedVariants(listOf("颜色分类" to "黑色,白色", "尺码" to "S,M,L"), sku).isEmpty())
 }

 @Test fun removesBlankAndDuplicateVariantNamesWithoutChangingTheirStableIds() {
  val sku = modernSku(listOf(skuProperty("30000", "套装", listOf(
   "20" to " OP（长款） ", "21" to "OP（长款）", "22" to "头饰", "23" to "头饰", "24" to " "
  ))))
  val variants = parsedVariants(emptyList(), sku)
  assertEquals(listOf("OP（长款）", "头饰"), variants.map { it["name"].asString })
  assertEquals(2, variants.map { it["id"].asString }.toSet().size)
  val expandedSku = modernSku(listOf(skuProperty("30000", "套装", listOf(
   "10" to "假领", "20" to "OP（长款）", "22" to "头饰"
  ))))
  val expanded = parsedVariants(emptyList(), expandedSku).associate { it["name"].asString to it["id"].asString }
  variants.forEach { assertEquals(it["id"].asString, expanded[it["name"].asString]) }
 }

 @Test fun malformedOrIncompleteSkuKeepsExplicitSetFallback() {
  val properties = listOf("套装" to "OP（长款）,头饰")
  for (sku in listOf("{invalid", "{}", "{\"global\":{\"skuGlobal\":{\"context\":{\"skuBase\":{\"props\":\"broken\"}}}}}")) {
   assertEquals(listOf("OP（长款）", "头饰"), parsedVariants(properties, sku).map { it["name"].asString })
  }
 }

 @Test fun capsExplicitCandidatesAtThirty() {
  val names = (1..35).map { "物品$it" }
  val variants = parsedVariants(listOf("套装" to names.joinToString(",")))
  assertEquals(names.take(30), variants.map { it["name"].asString })
  assertEquals(30, variants.map { it["id"].asString }.toSet().size)
 }

 @Test fun doesNotOfferSplittingForOneExplicitStyle() {
  assertTrue(parsedVariants(listOf("套装" to "OP（长款）")).isEmpty())
 }

 private fun parsedVariants(properties: List<Pair<String, String>>, sku: String? = null): List<com.google.gson.JsonObject> {
  val data = linkedMapOf<String, Any>(
   "titleVO" to mapOf("title" to "守望者1.0 OP"),
   "paramsVO" to mapOf("groupProps" to listOf(mapOf("基本信息" to properties.map { mapOf(it.first to it.second) })))
  )
  if (sku != null) data["skuData"] = mapOf("skuV3Data" to sku)
  val raw = com.google.gson.Gson().toJson(mapOf(
   "url" to "https://item.taobao.com/item.htm?id=927238692735", "text" to "宝贝详情",
   "entry" to mapOf("apiStack" to listOf(mapOf("data" to data)))
  ))
  val draft = TaobaoProductParser.parse(raw)
  // Serializing the real result keeps the red phase compilable before the additive draft field exists.
  return com.google.gson.Gson().toJsonTree(draft).asJsonObject.getAsJsonArray("variants")?.map { it.asJsonObject }.orEmpty()
 }

 private fun skuProperty(pid: String, name: String, values: List<Pair<String, String>>) = mapOf(
  "pid" to pid, "name" to name, "values" to values.map { mapOf("vid" to it.first, "name" to it.second) }
 )

 private fun modernSku(properties: List<Map<String, Any>>) = com.google.gson.Gson().toJson(mapOf(
  "global" to mapOf("skuGlobal" to mapOf("context" to mapOf("skuBase" to mapOf("props" to properties)))))
 )

}
