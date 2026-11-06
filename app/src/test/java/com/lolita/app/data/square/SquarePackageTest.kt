package com.lolita.app.data.square

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SquarePackageTest {
    private val bytes="original-photo".toByteArray()
    private val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private val asset=SquareAsset("images/$hash.png",hash,bytes.size.toLong(),1,1)
    private val sku=SquareSku("sku1","p1:sku1","p1",mapOf("颜色分类" to "蓝色JSK","尺码" to "M"),"899.00","全款","未知","JSK","蓝色","M","单品",
        listOf(SquarePart("JSK","JSK","JSK",false,"蓝色","M","独立SKU",null)),listOf(SquareStyle("古典","参数","风格")),false,mapOf("颜色分类" to "蓝色JSK","尺码" to "M"),"variant1")
    private fun catalog()=SquareCatalog(packageId="first",title="测试公开目录",capturedAt="2026-11-05",
        shops=listOf(SquareShop("shop","测试店铺",1)),events=listOf(SquareEvent("e",1,"shop","design","batch","商品","第一批","上新","开售","未知","899","全款","","","","2026-11-05",listOf("p1"))),
        products=listOf(SquareProduct("p1",1,"shop","商品","https://item.taobao.com/item.htm?id=1","2026-11-05","FULL","全款",listOf(sku),JsonObject(),listOf(SquareImage(asset.path,"main",1,"")))),assets=listOf(asset))
    private inline fun rejected(block:()->Unit){try{block();fail("Must reject invalid package")}catch(expected:IllegalArgumentException){}}
    private fun zip(root:File,c:SquareCatalog=catalog(),name:String=asset.path,photo:ByteArray=bytes):File=File(root,"input.zip").also{file->
        ZipOutputStream(file.outputStream()).use{z->z.putNextEntry(ZipEntry("manifest.json"));z.write(SquarePackage.gson.toJson(c).toByteArray());z.closeEntry();z.putNextEntry(ZipEntry(name));z.write(photo);z.closeEntry()}}
    @Test fun validPackageUnpacksAllBytesAndKeepsOriginalMetadata(){val root=Files.createTempDirectory("square").toFile();try{
        val c=SquarePackage.unpack(zip(root),File(root,"stage"));assertEquals("M",c.products.single().skus.single().size);assertArrayEquals(bytes,File(root,"stage/${asset.path}").readBytes())
    }finally{root.deleteRecursively()}}
    @Test fun checksumFailureCleansStage(){val root=Files.createTempDirectory("square").toFile();try{
        val stage=File(root,"stage");rejected{SquarePackage.unpack(zip(root,photo="corrupted-byte".toByteArray()),stage)};assertFalse(stage.exists())
    }finally{root.deleteRecursively()}}
    @Test fun traversalAndUnlistedFilesAreRejected(){val root=Files.createTempDirectory("square").toFile();try{
        rejected{SquarePackage.unpack(zip(root,name="../escape.png"),File(root,"stage"))};assertFalse(File(root.parentFile,"escape.png").exists())
    }finally{root.deleteRecursively()}}
    @Test fun unknownEnvelopeAndSchemaAreRejected(){val c=SquarePackage.gson.toJsonTree(catalog()).asJsonObject;c.addProperty("schemaVersion",2);rejected{SquarePackage.decode(c.toString())};c.addProperty("schemaVersion",1);c.addProperty("privateData","no");rejected{SquarePackage.decode(c.toString())}}
    @Test fun missingReferencesAndDuplicateIdentityAreRejected(){val c=catalog();rejected{SquarePackage.validate(c.copy(shops=c.shops+c.shops))};rejected{SquarePackage.validate(c.copy(assets=listOf(asset.copy(path="images/${"a".repeat(64)}.png"))))}}
    @Test fun completeRawSkuAndComponentIdentityRequired(){val c=catalog();rejected{SquarePackage.validate(c.copy(products=c.products.map{it.copy(skus=listOf(sku.copy(rawProperties=emptyMap())))}))};rejected{SquarePackage.validate(c.copy(products=c.products.map{it.copy(skus=listOf(sku.copy(parts=sku.parts+sku.parts)))}))}}
    @Test fun tailCannotMasqueradeAsNewRelease(){val c=catalog();rejected{SquarePackage.validate(c.copy(products=c.products.map{it.copy(pricePhase="BALANCE")}))};SquarePackage.validate(c.copy(products=c.products.map{it.copy(pricePhase="BALANCE")},events=c.events.map{it.copy(kind="尾款")}));assertFalse(c.events.single().copy(kind="尾款").canRegister)}
    @Test fun mergeIsIdempotentPreservesAbsentRecordsAndRefusesSameRevisionConflict(){val old=catalog();assertEquals(old,SquarePackage.merge(old,old));val newer=old.copy(products=old.products.map{it.copy(revision=2,title="修订款名")});val merged=SquarePackage.merge(old,newer);assertEquals("修订款名",merged.products.single().title);assertEquals("修订款名",SquarePackage.merge(merged,old).products.single().title)
        rejected{SquarePackage.merge(old,old.copy(products=old.products.map{it.copy(title="同版本篡改")}))}
        val second=old.copy(events=old.events.map{it.copy(id="e2")});assertEquals(2,SquarePackage.merge(old,second).events.size)}
    @Test fun filteringMatchesOneActualSkuRatherThanCombiningDifferentOnes(){val blueKC=sku.copy(color="蓝色",content="KC",form="小物");val lightBlouse=sku.copy(color="浅蓝色",content="内搭",form="单品");assertFalse(listOf(blueKC,lightBlouse).any{it.matches("浅蓝色","","小物")});assertTrue(blueKC.matches("蓝色","古典","小物"))}
    @Test fun oldCatalogHasNoInventedImageBindingsAndRefinedBindingsAreStrict(){
        val old=SquarePackage.decode(SquarePackage.gson.toJson(catalog()));val p=old.products.single()
        assertTrue(p.suggestUnit(sku,sku.parts,"单品").photos.isEmpty())
        assertTrue(p.suggestUnit(sku,sku.parts,"单品").documents.isEmpty())
        rejected{SquarePackage.validate(old.copy(products=listOf(p.copy(skus=listOf(sku.copy(photoPaths=listOf("images/missing.png")))))))}
        rejected{SquarePackage.validate(old.copy(products=listOf(p.copy(materials=listOf(SquareMaterial("JSK",listOf("images/missing.png"),"","原图"))))))}
    }
    @Test fun selectingComponentUsesOnlyItsExplicitImagesAndDoesNotBorrowGroupCover(){
        val kc=SquarePart("KC","KC","头饰",true,null,null,"配套",null)
        val full=sku.copy(content="JSK+KC",parts=sku.parts+kc,photoPaths=listOf("group"))
        val singleKc=sku.copy(parts=listOf(kc),photoPaths=listOf("kc"))
        val p=catalog().products.single().copy(skus=listOf(full,singleKc),materials=listOf(SquareMaterial("KC",listOf("kc-chart"),"KC材质","原图")))
        val unit=p.suggestUnit(full,listOf(kc),"单品")
        assertEquals(listOf("kc"),unit.photos);assertEquals(listOf("kc-chart"),unit.documents)
        assertNull(kc.size);assertNull(kc.color)
        assertEquals(listOf("group"),p.suggestUnit(full,full.parts,"服饰组合").photos)
        val unknown=p.copy(skus=listOf(full,singleKc.copy(color="黑色")))
        assertTrue(unknown.suggestUnit(full,listOf(kc),"单品").photos.isEmpty())
    }
    @Test fun customGroupsHaveOrderIndependentIdentityAndValidateImageOwnership(){
        val e=catalog().events.single();val kc=SquarePart("KC","KC",null,true,null,null,"",null)
        val blouse=kc.copy(key="blouse",name="内搭",isAccessory=false)
        val full=sku.copy(parts=sku.parts+kc+blouse)
        val unit=SquareUnit(listOf("JSK","KC"),"裙与头饰","服饰组合")
        assertEquals(unit.identity(e,full),unit.copy(partKeys=unit.partKeys.reversed()).identity(e,full))
        assertNotEquals(unit.identity(e,full),unit.copy(partKeys=full.parts.map{it.key}).identity(e,full))
        val p=catalog().products.single().copy(skus=listOf(full));unit.validate(p,full)
        rejected{unit.copy(partKeys=listOf("not-in-sku")).validate(p,full)}
        rejected{unit.copy(photos=listOf(asset.path),documents=listOf(asset.path)).validate(p,full)}
        rejected{unit.copy(photos=listOf("other-product-image")).validate(p,full)}
        assertEquals("square:v1:shop:batch:variant1:@whole",SquareUnit(listOf("JSK"),"JSK").identity(e,sku))
    }

    @Test fun archivedMetadataMergesWithoutDeletingPhotosAndOldPackagesStayCompatible() {
        val original=catalog(); val before=SquarePackage.decode(SquarePackage.gson.toJson(original))
        assertNull(before.products.single().listing); assertTrue(SquareVisibility.event(before,before.events.single()))
        val archived=before.copy(products=before.products.map { it.copy(revision=2,listing=SquareListing("OFF_SALE","公开下架提示","2026-11-06T22:00:00+08:00")) })
        val merged=SquarePackage.merge(before,archived)
        assertEquals(before.assets,merged.assets); assertEquals(before.products.single().skus,merged.products.single().skus)
        assertTrue(SquareVisibility.events(merged).isEmpty()); assertEquals(merged,SquarePackage.merge(merged,before))
        assertEquals(merged,SquarePackage.decode(SquarePackage.gson.toJson(merged)))
        rejected { SquarePackage.validate(archived.copy(products=archived.products.map { it.copy(listing=it.listing!!.copy(status="MISSING")) })) }
        rejected { SquarePackage.validate(archived.copy(products=archived.products.map { it.copy(listing=it.listing!!.copy(source="")) })) }
        rejected { SquarePackage.merge(archived,before.copy(products=before.products.map { it.copy(revision=3) })) }
        rejected { SquarePackage.merge(archived,archived.copy(products=archived.products.map { it.copy(revision=3,listing=it.listing!!.copy(observedAt="2026-11-06T21:00:00+08:00")) })) }
        val active=archived.copy(products=archived.products.map { it.copy(revision=3,listing=it.listing!!.copy(status="ACTIVE")) })
        assertEquals(1,SquareVisibility.events(SquarePackage.merge(merged,active)).size)
    }

}
