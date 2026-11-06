package com.lolita.app.data.square

/** A selected output record, independently editable before entering the wardrobe editor. */
data class SquareUnit(val partKeys:List<String>, val name:String, val groupLabel:String="单品",
    val photos:List<String> = emptyList(), val documents:List<String> = emptyList())

fun SquareProduct.suggestUnit(sku:SquareSku, parts:List<SquarePart>, groupLabel:String):SquareUnit {
    val whole=parts.map { it.key }.toSet()==sku.parts.map { it.key }.toSet()
    val photos=if(whole) sku.photoPaths.orEmpty() else parts.flatMap { part ->
        // Only an explicit independent SKU with the same stated colour supplies a suggestion.
        skus.firstOrNull { it.parts.size==1 && it.parts[0].key==part.key && it.color==sku.color }?.photoPaths.orEmpty()
    }.distinct()
    val documents=materials.orEmpty().filter { m -> parts.any { it.key==m.partKey } }.flatMap { it.detailPaths }.distinct()
    return SquareUnit(parts.map { it.key },parts.joinToString(" + ") { it.name },groupLabel,photos,documents)
}

fun SquareUnit.validate(product:SquareProduct,sku:SquareSku) {
    require(partKeys.isNotEmpty() && partKeys.distinct().size==partKeys.size && partKeys.all { key -> sku.parts.any { it.key==key } }) { "请重新选择实际规格中包含的部件" }
    require(name.isNotBlank() && name.length<=200 && groupLabel in setOf("单品","服饰组合","小物组合","套装")) { "请填写物品名称并确认组合类型" }
    require(partKeys.size>1 || groupLabel=="单品") { "单个部件请按单品登记" }
    val available=product.images.map { it.path }.toSet()
    require(photos.size<=50 && documents.size<=50 && (photos+documents).distinct().size==photos.size+documents.size && (photos+documents).all { it in available }) { "图片归属已改变，请重新挑选本商品的图片" }
}

fun SquareUnit.identity(event:SquareEvent,sku:SquareSku):String {
    val all=partKeys.toSet()==sku.parts.map { it.key }.toSet()
    val suffix=if(all) "@whole" else if(partKeys.size==1) partKeys.single() else "@group:"+java.util.UUID.nameUUIDFromBytes(SquarePackage.gson.toJson(partKeys.sorted()).toByteArray(Charsets.UTF_8))
    return "square:v1:${event.shopId}:${event.batchId}:${sku.variantId}:$suffix"
}
