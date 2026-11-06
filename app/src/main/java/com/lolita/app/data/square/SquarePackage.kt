package com.lolita.app.data.square

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Portable public catalog. Private wardrobe fields never enter this envelope. */
data class SquareCatalog(
    val format: String = "lolita.square", val schemaVersion: Int = 1,
    val packageId: String = "", val revision: Long = 1, val title: String = "", val capturedAt: String = "",
    val shops: List<SquareShop> = emptyList(), val events: List<SquareEvent> = emptyList(),
    val products: List<SquareProduct> = emptyList(), val assets: List<SquareAsset> = emptyList()
)
data class SquareShop(val id: String, val name: String, val revision: Long)
data class SquareEvent(
    val id: String, val revision: Long, val shopId: String, val designId: String, val batchId: String,
    val title: String, val subtitle: String, val kind: String, val state: String, val date: String,
    val price: String, val priceNote: String, val note: String, val material: String, val detail: String,
    val captured: String, val productIds: List<String>
) { val canRegister get() = kind != "尾款" }
data class SquareProduct(
    val id: String, val revision: Long, val shopId: String, val title: String, val url: String,
    val capturedAt: String, val pricePhase: String, val priceType: String,
    val skus: List<SquareSku>, val info: JsonObject, val images: List<SquareImage>,
    val materials: List<SquareMaterial>? = null
)
data class SquareSku(
    val id: String, val key: String, val itemId: String, val props: Map<String, String>,
    val price: String?, val priceType: String, val stock: String, val content: String,
    val color: String?, val size: String?, val form: String, val parts: List<SquarePart>,
    val styleFacts: List<SquareStyle>, val notSoldAlone: Boolean, val rawProperties: Map<String, String>, val variantId: String, val photoPaths: List<String>? = null
)
data class SquarePart(val key: String, val name: String, val category: String?, val isAccessory: Boolean,
    val color: String?, val size: String?, val relation: String, val uncertainty: String?)
data class SquareMaterial(val partKey: String, val detailPaths: List<String>, val facts: String, val source: String, val sizeTable: SquareSizeTable? = null)
data class SquareSizeTable(val columns: List<String>, val rows: List<SquareSizeRow>, val note: String = "")
data class SquareSizeRow(val size: String, val values: List<String>)
data class SquareStyle(val value: String, val source: String, val field: String)
data class SquareImage(val path: String, val role: String, val order: Int, val url: String)
data class SquareAsset(val path: String, val sha256: String, val bytes: Long, val width: Int, val height: Int)

object SquarePackage {
    const val MAX_ZIP = 600L * 1024 * 1024
    const val MAX_IMAGE = 20L * 1024 * 1024
    const val MAX_PRODUCT = 80L * 1024 * 1024
    private const val MAX_JSON = 8L * 1024 * 1024
    private val assetPath = Regex("images/([a-f0-9]{64})\\.(jpg|jpeg|png|webp)")
    private val idPattern = Regex("[A-Za-z0-9_-]{1,100}")
    val gson = Gson()

    fun decode(json: String): SquareCatalog {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.keySet().all { it in setOf("format","schemaVersion","packageId","revision","title","capturedAt","shops","events","products","assets") }) { "资料包有未支持的字段，请更新应用" }
        require(root["format"]?.asString == "lolita.square" && root["schemaVersion"]?.asBigDecimal?.intValueExact() == 1) { "这不是支持的广场资料包，请选择广场ZIP" }
        return gson.fromJson(root, SquareCatalog::class.java).also(::validate)
    }

    fun validate(c: SquareCatalog) {
        require(c.format == "lolita.square" && c.schemaVersion == 1 && idPattern.matches(c.packageId) && c.revision > 0) { "资料包版本或标识无效" }
        require(c.title.isNotBlank() && c.title.length <= 200 && c.capturedAt.length in 1..80) { "资料包名称或采集日期无效" }
        require(c.shops.size in 1..200 && c.events.size in 1..2000 && c.products.size in 1..2000 && c.assets.size in 1..4000) { "资料包内容为空或超过本地导入上限" }
        fun <T> unique(rows: List<T>, key: (T)->String) { require(rows.map(key).distinct().size == rows.size) { "资料包包含重复标识" } }
        unique(c.shops) { it.id }; unique(c.events) { it.id }; unique(c.products) { it.id }; unique(c.assets) { it.path }
        val shops = c.shops.associateBy { it.id }; val products = c.products.associateBy { it.id }; val assets = c.assets.associateBy { it.path }
        c.shops.forEach { require(idPattern.matches(it.id) && it.revision > 0 && it.name.isNotBlank() && it.name.length <= 200) { "店铺标识或名称无效" } }
        c.assets.forEach { a ->
            require(assetPath.matches(a.path) && assetPath.matchEntire(a.path)!!.groupValues[1] == a.sha256 && a.bytes in 1..MAX_IMAGE && a.width in 1..50000 && a.height in 1..50000 && a.width.toLong()*a.height <= 80_000_000) { "图片路径、大小或尺寸无效" }
        }
        require(c.assets.sumOf { it.bytes } <= MAX_ZIP) { "资料包图片总量超过600MiB" }
        c.products.forEach { p ->
            require(idPattern.matches(p.id) && p.revision > 0 && p.shopId in shops && p.title.isNotBlank() && p.title.length <= 500 && p.capturedAt.isNotBlank()) { "商品资料或店铺引用无效" }
            require(p.url.startsWith("https://") && p.url.length <= 2000 && p.pricePhase in setOf("FULL","DEPOSIT","BALANCE","PLACEHOLDER")) { "商品链接或价格阶段无效" }
            require(p.skus.size in 1..1000 && p.images.size in 1..300 && p.images.any { it.role == "main" }) { "商品缺少完整规格或主图" }
            unique(p.skus) { it.id }
            val imagePaths=p.images.map { it.path }.toSet()
            require(p.materials.orEmpty().size<=100 && p.materials.orEmpty().all { m -> m.partKey.length in 1..100 && p.skus.any { s -> s.parts.any { it.key==m.partKey } } && m.source.length in 1..2000 && m.facts.length<=12000 && m.detailPaths.size<=30 && m.detailPaths.distinct().size==m.detailPaths.size && m.detailPaths.all { path -> path in imagePaths } }) { "部件资料引用无效" }
            p.materials.orEmpty().forEach { it.sizeTable?.validate() }
            p.skus.forEach { s ->
                require(s.photoPaths.orEmpty().size<=30 && s.photoPaths.orEmpty().distinct().size==s.photoPaths.orEmpty().size && s.photoPaths.orEmpty().all { it in imagePaths }) { "规格图片引用无效" }
                require(idPattern.matches(s.variantId) && idPattern.matches(s.id) && s.key == "${p.id}:${s.id}" && s.itemId == p.id && s.props.isNotEmpty() && s.props == s.rawProperties && s.props.size <= 12 && s.props.all { it.key.length in 1..100 && it.value.length in 1..500 }) { "商品SKU原始规格不完整" }
                require(s.content.isNotBlank() && s.content.length <= 500 && s.form in setOf("单品","小物","套装","组合") && s.parts.size in 1..20 && s.parts.map { it.key }.distinct().size == s.parts.size) { "购买内容或部件资料无效" }
                require(s.parts.all { it.key.isNotBlank() && it.name.isNotBlank() } && s.styleFacts.all { it.value.isNotBlank() && it.source.isNotBlank() }) { "部件或风格来源无效" }
                require(s.price == null || s.price.toBigDecimalOrNull()?.let { it.signum() >= 0 && it <= 100_000_000.toBigDecimal() } == true) { "SKU报价无效" }
            }
            p.images.forEach { require(it.path in assets && it.role in setOf("main","sku","detail","promo") && it.order >= 0) { "商品图片缺失或角色不明确" } }
            require(p.images.map { it.path }.distinct().sumOf { assets.getValue(it).bytes } <= MAX_PRODUCT) { "单商品图片超过80MiB，请拆分整理资料包" }
        }
        c.events.forEach { e ->
            require(idPattern.matches(e.id) && idPattern.matches(e.designId) && idPattern.matches(e.batchId) && e.revision > 0 && e.shopId in shops && e.title.isNotBlank() && e.title.length <= 500 && e.kind in setOf("上新","再贩","现货","尾款")) { "动态类型或批次无效" }
            require(e.productIds.isNotEmpty() && e.productIds.distinct().size == e.productIds.size && e.productIds.all { products[it]?.shopId == e.shopId }) { "动态商品引用无效" }
            require(e.kind == "尾款" || e.productIds.any { products.getValue(it).pricePhase != "BALANCE" }) { "尾款商品不能登记为上新" }
        }
        require(c.products.sumOf { it.skus.size } <= 20_000) { "规格总数超过20000个" }
        require(c.products.flatMap { it.images }.map { it.path }.toSet() == assets.keys) { "资料包存在未归属的图片" }
    }

    /** All bytes and references are checked before anything is published to the catalog. */
    fun unpack(zip: File, stage: File, progress: (String)->Unit = {}, validateImage: (File,SquareAsset)->Unit = {_,_->}): SquareCatalog {
        require(zip.length() in 1..MAX_ZIP) { "文件为空或超过600MiB，请重新选择广场资料包" }
        require(stage.mkdirs()) { "无法暂存资料包，请检查存储空间后重试" }
        try {
            ZipFile(zip).use { z ->
                val entries = z.entries().asSequence().toList()
                require(entries.size in 2..4001 && entries.map { it.name }.distinct().size == entries.size) { "压缩包包含重复文件或文件过多" }
                require(entries.none { it.isDirectory || it.size < 0 || (it.name != "manifest.json" && !assetPath.matches(it.name)) }) { "压缩包路径不符合广场格式" }
                val manifest = z.getEntry("manifest.json") ?: error("缺少manifest.json，请选择完整广场资料包")
                require(manifest.size in 1..MAX_JSON) { "资料清单过大或为空" }
                val json = z.getInputStream(manifest).use { readBounded(it, MAX_JSON).toString(Charsets.UTF_8) }
                val c = decode(json)
                require(entries.map { it.name }.toSet() == c.assets.map { it.path }.toSet() + "manifest.json") { "图片清单与压缩包不一致" }
                c.assets.forEachIndexed { i,a ->
                    progress("核验原图 ${i+1} / ${c.assets.size}")
                    val entry = z.getEntry(a.path)
                    require(entry.size == a.bytes && entry.compressedSize > 0 && entry.size <= entry.compressedSize * 250L) { "图片大小或压缩比例异常" }
                    val out = File(stage,a.path); require(out.parentFile!!.mkdirs() || out.parentFile!!.isDirectory)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count=0L
                    z.getInputStream(entry).use { input -> out.outputStream().use { output ->
                        val buf=ByteArray(64*1024); while(true) { val n=input.read(buf); if(n<0)break; count+=n
                            require(count<=a.bytes && count<=MAX_IMAGE) { "图片解压超过限制" }; digest.update(buf,0,n); output.write(buf,0,n) }
                    } }
                    require(count==a.bytes && digest.digest().joinToString("") { "%02x".format(it) } == a.sha256) { "有图片校验不通过，请重新获取完整资料包" }
                    validateImage(out,a)
                }
                File(stage,"manifest.json").writeText(json)
                return c
            }
        } catch(failure: Throwable) { stage.deleteRecursively(); throw failure }
    }
    private fun readBounded(input: InputStream, max: Long): ByteArray {
        val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);var total=0L
        while(true){val n=input.read(buf);if(n<0)break;total+=n;require(total<=max){"资料清单超过限制"};out.write(buf,0,n)}
        return out.toByteArray()
    }

    fun merge(old: SquareCatalog?, incoming: SquareCatalog): SquareCatalog {
        if(old==null)return incoming
        fun <T> rows(previous:List<T>,next:List<T>,id:(T)->String,revision:(T)->Long):List<T> {
            val all=previous.associateBy(id).toMutableMap()
            next.forEach { n -> val p=all[id(n)]
                if(p==null || revision(n)>revision(p)) all[id(n)]=n
                else if(revision(n)==revision(p)) require(gson.toJsonTree(p)==gson.toJsonTree(n)) { "同一资料版本内容冲突，请使用修正后的新版本包" }
            };return all.values.toList()
        }
        val products=rows(old.products,incoming.products,{it.id},{it.revision})
        val used=products.flatMap { it.images }.map { it.path }.toSet()
        return incoming.copy(shops=rows(old.shops,incoming.shops,{it.id},{it.revision}),
            events=rows(old.events,incoming.events,{it.id},{it.revision}), products=products,
            assets=(old.assets+incoming.assets).associateBy { it.path }.values.filter { it.path in used }).also { validate(it);require(gson.toJson(it).toByteArray(Charsets.UTF_8).size <= MAX_JSON) { "本地目录清单超过8MiB，请分批整理资料" } }
    }
}

/** A filter must match one actual SKU, never a cross product of unrelated parts. */
fun SquareSku.matches(color: String, style: String, form: String): Boolean =
    (color.isEmpty() || this.color == color) && (style.isEmpty() || styleFacts.any { it.value == style }) && (form.isEmpty() || this.form == form)

fun squarePrice(raw: String?): String = raw?.toBigDecimalOrNull()?.let { "¥"+it.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString() } ?: "未知"

fun squareInfoText(e: JsonElement): String = when {
    e.isJsonNull -> "未提供"
    e.isJsonPrimitive -> e.asString
    e.isJsonArray -> e.asJsonArray.joinToString("\n") { squareInfoText(it) }
    else -> e.asJsonObject.entrySet().filter { it.key != "field" }.joinToString("\n") {
        val label=mapOf("value" to "风格","source" to "依据","name" to "名称","color" to "颜色","size" to "尺码",
            "category" to "类型","condition" to "条件","description" to "说明","note" to "补充","notes" to "补充",
            "length" to "衣长","bust" to "胸围","waist" to "腰围","shoulder" to "肩宽","sleeve" to "袖长",
            "styles" to "风格及依据","season" to "适用季节","components" to "部件说明","included" to "包含内容",
            "excluded" to "不包含","gifts" to "赠品条件","care" to "洗护","materials" to "材质","craft" to "工艺","sizes" to "尺码资料")[it.key] ?: it.key
        "$label：${squareInfoText(it.value)}"
    }
}

fun SquareSizeTable.validate() {
    require(columns.size in 1..12 && columns.all { it.length in 1..100 } && columns.distinct().size == columns.size && rows.size in 1..50 && rows.map { it.size }.distinct().size == rows.size && note.length <= 1000) { "尺码表格式无效" }
    require(rows.all { it.size.length in 1..100 && it.values.size == columns.size && it.values.all { v -> v.length in 1..200 } }) { "尺码表行列不完整" }
}
