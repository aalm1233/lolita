package com.lolita.app.data.file
import org.junit.Assert.*
import org.junit.Test
class TaobaoProductComponentsTest {
 @Test fun farmCombosYieldEightDistinctPiecesAndKeepAllDetailImages() {
  val draft=TaobaoProductParser.parse(javaClass.getResource("/taobao-farm-components.json")!!.readText())
  assertEquals(listOf("OP","KC","颈饰","手袖","王子款 衬衫","王子款 背带裤","皇冠","腿环"),draft.variants.map { it.name })
  assertEquals(38,draft.remoteDetailImages.size)
  assertEquals(38,draft.remoteDetailImages.toSet().size)
  assertTrue(draft.variants.all { it.referenceQuote == null })
  val parts=com.lolita.app.ui.screen.`import`.taobaoProductBatchItems(draft,draft.variants.map { it.id }.toSet())
  assertEquals(8,parts.size)
  assertTrue(parts.all { it.remoteDetailImages.size==38 && TaobaoProductQuotes.fromDescription(it.description)==null })
 }
 @Test fun unfinishedDetailsCannotBeImportedAsAnEmptyList() {
  val root=com.google.gson.JsonParser.parseString(javaClass.getResource("/taobao-farm-components.json")!!.readText()).asJsonObject
  root.addProperty("detailStatus","pending");root.add("detailImages",com.google.gson.JsonArray())
  try { TaobaoProductParser.parse(root.toString());fail("尚未就绪的详情不应通过") } catch(e:IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("详情")) }
 }
 @Test fun colorQualifiedCombinationsRemainUnsplit() {
  assertTrue(TaobaoProductPieces.colorComponents(listOf("黑OP+黑KC","白OP+白KC")).isEmpty())
 }
 @Test fun explicitlyDifferentPrefixesAreNotMergedByCategory() {
  assertEquals(listOf("王子款 衬衫","公主款 衬衫","KC"),TaobaoProductPieces.colorComponents(listOf("王子款 衬衫","公主款 衬衫+KC")))
 }
 @Test fun normalColorNamesWithGarmentWordsDoNotBecomePieces() {
  val raw=javaClass.getResource("/taobao-farm-components.json")!!.readText()
  val root=com.google.gson.JsonParser.parseString(raw).asJsonObject
  val data=root.getAsJsonObject("entry").getAsJsonArray("apiStack")[0].asJsonObject.getAsJsonObject("data")
  val sku=com.google.gson.JsonParser.parseString(data.getAsJsonObject("skuData")["skuV3Data"].asString).asJsonObject
  val prop=sku.getAsJsonObject("global").getAsJsonObject("skuGlobal").getAsJsonObject("context").getAsJsonObject("skuBase").getAsJsonArray("props")[0].asJsonObject
  prop.add("values",com.google.gson.Gson().toJsonTree(listOf(mapOf("vid" to "1","name" to "黑色OP"),mapOf("vid" to "2","name" to "白色OP"))))
  data.getAsJsonObject("skuData").addProperty("skuV3Data",sku.toString())
  assertTrue(TaobaoProductParser.parse(root.toString()).variants.isEmpty())
 }
}
