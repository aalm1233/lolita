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
}
