package com.lolita.app.data.file

import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import com.lolita.app.domain.model.OrderImportSource
import com.lolita.app.domain.model.ImportMoney

/**
 * 解析淘宝订单导出的 Excel 文件 (.xlsx)
 *
 * Excel 结构 (11列):
 * 订单号 | 订单提交时间 | 订单状态 | 店铺名称 | 商品名称 | 商品链接 |
 * 型号款式 | 商品数量 | 商品金额 | 实付金额 | 运费
 *
 * 特点: 同一订单号下多个商品各占一行，订单级字段(订单号/时间/状态/店铺/实付/运费)仅在首行有值
 */
object TaobaoOrderParser {

    fun parseMultipleFiles(inputStreams: List<InputStream>): Triple<List<TaobaoOrder>, Int, Int> {
        val allOrders = mutableListOf<TaobaoOrder>()
        inputStreams.forEach { stream ->
            allOrders.addAll(parse(stream))
        }
        val totalBeforeDedup = allOrders.size
        require(allOrders.groupBy { it.orderId }.values.all { versions -> versions.map(OrderImportSource::snapshot).distinct().size == 1 }) { "同订单存在不同来源，请分别读取并比较资料" }
        val deduped = allOrders.distinctBy { it.orderId }
        val dupCount = totalBeforeDedup - deduped.size
        return Triple(deduped, inputStreams.size, dupCount)
    }

    fun parse(inputStream: InputStream): List<TaobaoOrder> = inputStream.use { stream ->
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(80L*1024*1024)
        org.apache.poi.openxml4j.util.ZipSecureFile.setMaxTextSize(20L*1024*1024)
        val bytes=OrderFilePreflight.read(stream)
        XSSFWorkbook(java.io.ByteArrayInputStream(bytes)).use { workbook ->
        val sheet = workbook.getSheetAt(0)
        val header = sheet.getRow(0)
        require(sheet.lastRowNum <= 10_000) { "文件超过10000行，请拆分后导入" }
        val columns = (0 until (header?.lastCellNum?.toInt() ?: 0)).groupBy { getCellString(header?.getCell(it)).trim() }
        val aliases = listOf(listOf("订单号","订单编号"),listOf("订单提交时间","下单时间"),listOf("订单状态"),listOf("店铺名称","店铺"),listOf("商品名称","宝贝名称"),listOf("商品链接","宝贝链接"),listOf("型号款式","商品规格"),listOf("商品数量","数量"),listOf("商品金额","商品价格"),listOf("实付金额","订单实付"),listOf("运费","邮费"))
        val mapped = aliases.map { names ->
            val matches = names.flatMap { columns[it].orEmpty() }
            require(matches.size == 1) { "缺少或重复“${names.first()}”列，请重新导出订单文件" }
            matches.single()
        }
        val extraColumns = columns.filterValues { it.size == 1 }.mapValues { it.value.single() }

        // 按订单号分组的临时结构
        val orderMap = linkedMapOf<String, OrderBuilder>()
        var currentOrderId = ""

        // 跳过表头，从第1行开始
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue

            // 读取订单号 — 如果本行有值则更新当前订单号
            val idCell=row.getCell(mapped[0])
            require(idCell?.cellType!=org.apache.poi.ss.usermodel.CellType.NUMERIC || idCell.numericCellValue<1_000_000_000_000_000.0) { "订单号已被Excel转成长数值，请以文本格式重新导出，避免号码失真" }
            val rawOrderId = getCellString(row.getCell(mapped[0]))
            if (rawOrderId.isNotBlank()) {
                currentOrderId = rawOrderId
            }
            if (currentOrderId.isBlank()) continue

            // 首次遇到该订单号时，读取订单级字段
            if (currentOrderId !in orderMap) {
                orderMap[currentOrderId] = OrderBuilder(
                    orderId = currentOrderId,
                    orderTime = getCellString(row.getCell(mapped[1])),
                    orderStatus = getCellString(row.getCell(mapped[2])),
                    shopName = getCellString(row.getCell(mapped[3])),
                    totalPaid = parsePrice(getCellString(row.getCell(mapped[9]))),
                    rawPaid = getCellString(row.getCell(mapped[9])),
                    rawShipping = getCellString(row.getCell(mapped[10])),
                    shipping = parsePrice(getCellString(row.getCell(mapped[10])))
                )
            }
            if(rawOrderId.isNotBlank()) {
                val original=orderMap.getValue(currentOrderId)
                listOf(1 to original.orderTime,2 to original.orderStatus,3 to original.shopName,9 to original.rawPaid,10 to original.rawShipping).forEach { (column,first) ->
                    val next=getCellString(row.getCell(mapped[column])).trim()
                    val equivalent=if(column in setOf(9,10)) OrderImportSource.money(next)==OrderImportSource.money(first.orEmpty()) else next==first
                    require(next.isBlank() || equivalent) { "订单 $currentOrderId 的多行来源信息有差异，请重新导出后比较" }
                }
            }
            // 读取商品级字段
            val itemName = getCellString(row.getCell(mapped[4]))
            if (itemName.isBlank()) continue

            val productUrl = OrderImportSource.publicUrl(getCellString(row.getCell(mapped[5]))).orEmpty()
            val styleSpec = getCellString(row.getCell(mapped[6]))
            val quantityText = getCellString(row.getCell(mapped[7])).trim()
            val quantity = if (quantityText.isBlank()) 1 else runCatching {
                quantityText.toBigDecimalOrNull()?.intValueExact()
            }.getOrNull() ?: throw IllegalArgumentException("订单 $currentOrderId 的商品数量无效，请填写1至100的整数")
            require(quantity in 1..100) { "订单 $currentOrderId 的商品数量无效，请填写1至100的整数" }
            val price = parsePrice(getCellString(row.getCell(mapped[8])))

            val parsed = parseStyleSpec(styleSpec)
            fun extra(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
                extraColumns[name]?.let { getCellString(row.getCell(it)).trim().takeIf(String::isNotBlank) }
            }
            val specifications = extra("专属规格JSON", "规格JSON").orEmpty()
            com.lolita.app.domain.model.WardrobeSpecifications.decode(specifications)

            val builder = orderMap[currentOrderId]!!
            builder.items.add(
                TaobaoOrderItem(
                    name = itemName,
                    productUrl = productUrl,
                    styleSpec = styleSpec,
                    quantity = quantity,
                    price = price,
                    parsedType = extra("物品类型", "类型") ?: parsed.type,
                    parsedSize = extra("尺码", "鞋码") ?: parsed.size,
                    parsedColor = parsed.color,
                    orderId = currentOrderId,
                    orderTime = builder.orderTime,
                    shopName = builder.shopName,
                    requestedRoomName = extra("房间"), requestedStoragePath = extra("收纳路径", "收纳位置"),
                    specificationsJson = specifications,
                    rawPrice = getCellString(row.getCell(mapped[8])),
                    actualPaidDate = extra("实际付款时间", "付款时间", "实际付款日期")
                )
            )
        }

        orderMap.values.map { it.build() }
    } // workbook.use
    } // inputStream.use

    private fun parsePrice(raw: String): Double {
        return (OrderImportSource.money(raw) as? ImportMoney.Valid)?.let { java.math.BigDecimal.valueOf(it.cents, 2).toDouble() } ?: 0.0
    }

    /**
     * 解析型号款式字段（分号分隔）
     * 例: "蓝黑配色;Lady80;开襟OP" → type=开襟OP, size=Lady80, color=蓝黑配色
     */
    private fun parseStyleSpec(spec: String): ParsedStyle {
        if (spec.isBlank()) return ParsedStyle()
        // 阶段1: 增强噪声清理
        val cleaned = spec
            // 括号类噪声
            .replace(Regex("【[^】]*】"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("\\{[^\\}]*\\}"), "")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("（[^）]*(?:尾款|定金|不单售|备注|售后)[^）]*）"), "")
            .replace(Regex("\\([^)]*(?:尾款|定金|不单售|备注|售后)[^)]*\\)"), "")
            // 点缀噪声
            .replace(Regex("·(?:尾款|定金|现货)"), "")
            .replace(Regex("β款"), "")
            // 备注类（必须在支付噪声之前，避免"色码以定金为准"被部分吃掉）
            .replace(Regex("色码以定金为准"), "")
            // 支付/批次噪声
            .replace(Regex("[一二三四五六七八九十\\d]+批"), "")
            .replace(Regex("[一二三四五六七八九十\\d]+团"), "")
            .replace(Regex("(?:仅|只)?(?:定-?金|尾款|现货|预约|全款|意向金)"), "")
            // 备注类
            .replace(Regex("(?:自行)?备注尺码|尺码(?:请)?备注|单品尺码请备注"), "")
            .replace(Regex("(?:发货|收货)[^;/]*地址[^;/]*"), "")
            .replace(Regex("注意[^;/]*"), "")
            .replace(Regex("需有[^;/]*"), "")
            .replace(Regex("需要有[^;/]*"), "")
            .replace(Regex("需补[^;/]*"), "")
            .replace(Regex("还需补[^;/]*"), "")
            // 日期时间模式
            .replace(Regex("\\d+\\.\\d+(?:晚上|上午|下午)[^;/]{0,10}"), "")
            .replace(Regex("\\d+月\\d+日[^;/]*"), "")
            // 杂项
            .replace(Regex("本体"), "")
            .replace(Regex("正常长度"), "")
            .replace(Regex("不退不换不售后"), "")
            .replace(Regex("福袋[^;/]*"), "")
            .replace(Regex("按拍付顺序发"), "")
            .replace(Regex("仅[^;/]*期间[^;/]*"), "")
            .replace(Regex("物流情况"), "")
            .replace(Regex("年前年后"), "")
            .replace(Regex("单品[^;/]*请?备注"), "")
            .trim()

        // 阶段2: 多级分割
        val primaryParts = cleaned.split(Regex("[;/]")).map { it.trim() }.filter { it.isNotBlank() }
        val allParts = primaryParts.flatMap { part ->
            if (isSizeKeyword(part)) listOf(part) else part.split(Regex("\\s+|--")).map { it.trim() }.filter { it.isNotBlank() }
        }

        // 阶段3: 智能分类
        var type: String? = null
        var size: String? = null
        var color: String? = null

        for (part in allParts) {
            val (t, s, c) = classifyPart(part)
            if (t != null && type == null) type = t
            if (s != null && size == null) size = s
            if (c != null && color == null) color = c
        }
        return ParsedStyle(type, size, color)
    }

    private fun classifyPart(part: String): Triple<String?, String?, String?> {
        if (part.isBlank()) return Triple(null, null, null)

        // 1. 纯尺寸
        if (isSizeKeyword(part)) return Triple(null, part, null)

        // 2. 先提取连写的尺码，避免类型匹配吞掉 M半裙 / L白色JSK 的尺码。
        val sizePrefix = if (typeKeywords.any { part.startsWith(it, ignoreCase = true) }) null else extractLeadingSize(part)
        if (sizePrefix != null) {
            val remaining = part.substring(sizePrefix.length).trim()
            if (remaining.isNotBlank()) {
                val (t, _, c) = classifyPart(remaining)
                return Triple(t, sizePrefix, c)
            }
            return Triple(null, sizePrefix, null)
        }

        // 3. 规范拼写，优先保留完整的类型变体。
        typeKeywords.firstOrNull { part.equals(it, ignoreCase = true) }?.let {
            return Triple(it, null, null)
        }

        // 4. 最长类型优先，JSK 不能先按 SK 拆成颜色“白色J”。
        for (keyword in typeKeywords.sortedByDescending { it.length }) {
            if (part.contains(keyword, ignoreCase = true)) {
                val remaining = part.replace(
                    Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE), ""
                ).trim()
                val extractedColor = if (remaining.isNotBlank() && isColorKeyword(remaining)) {
                    remaining
                } else null
                return Triple(keyword, null, extractedColor)
            }
        }

        // 5. 颜色匹配
        if (isColorKeyword(part)) return Triple(null, null, part)

        // 6. 兜底：归为颜色
        return Triple(null, null, part)
    }

    private fun extractLeadingSize(part: String): String? {
        fun hasSizeBoundary(prefix: String): Boolean {
            val remaining = part.substring(prefix.length)
            if (remaining.isBlank()) return false
            fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
            if (!remaining.first().isAsciiLetter()) return true
            // MJSK is a size followed by a complete type; Mint/Sax/Lavender are
            // words, whose initial letter alone is not size evidence.
            return typeKeywords.any { keyword ->
                remaining.startsWith(keyword, ignoreCase = true) &&
                    (remaining.length == keyword.length || !remaining[keyword.length].isAsciiLetter())
            }
        }
        Regex("^(Lady\\d+)", RegexOption.IGNORE_CASE).find(part)?.let {
            if (hasSizeBoundary(it.value)) return it.value
        }
        Regex("^(XXXL|XXL|2XL|3XL|XS|XL|S|M|L)", RegexOption.IGNORE_CASE).find(part)?.let {
            if (hasSizeBoundary(it.value)) return it.value
        }
        return null
    }

    private val typeKeywords = listOf(
        "全头假发", "刘海片", "假马尾", "假辫", "发片", "短袜", "中筒袜", "及膝袜", "过膝袜", "连裤袜", "袜套",
        "玛丽珍鞋", "茶会鞋", "浅口鞋", "短靴", "长靴", "裙撑", "手提包", "斜挎包", "背包", "手拿包", "阳伞", "扇子", "手杖", "玩偶", "花束",
        // 基础类型
        "OP", "SK", "JSK", "SET", "FS",
        // 裙类变体
        "开襟OP", "堆褶OP", "堆褶开襟OP", "拼色SK", "罩裙", "半裙", "格裙", "长裙", "连衣裙",
        // 套装
        "Fullset", "FullSet", "fullset", "FULLSET", "华丽套装",
        // 上装
        "衬衫", "上衣", "外套", "长外套", "大衣", "罩衫", "斗篷", "罩衫斗篷", "胸衣", "蝙蝠胸衣",
        // 下装
        "短裤", "南瓜裤",
        // 配饰 — 头部
        "头饰", "蝴蝶结头饰", "帽子", "贝雷帽", "KC", "发箍", "发带", "发夹", "边夹", "头纱",
        // 配饰 — 身体
        "腰封", "拖尾腰封", "印花钢骨腰封", "手袖", "接袖", "袖子",
        // 配饰 — 其他
        "包", "包包", "吊坠", "项链", "耳环", "戒指", "胸针"
    )

    private val sizePatterns = listOf(
        Regex("^(EU|US|UK|CN|JP)\\s*\\d{1,3}(?:\\.\\d+)?$", RegexOption.IGNORE_CASE),
        Regex("^\\d{1,3}(?:\\.\\d+)?\\s*(?:至|到|[-~～])?\\s*\\d{0,3}(?:\\.\\d+)?\\s*(?:cm|厘米|mm|毫米)$", RegexOption.IGNORE_CASE),
        Regex("^Lady\\d+$", RegexOption.IGNORE_CASE),
        Regex("^\\d{2,3}$"),                        // 2-3位数字（排除年份等4位数）
        Regex("^(XS|S|M|L|XL|XXL|XXXL|2XL|3XL)$", RegexOption.IGNORE_CASE),
        Regex("^均码$"),
        Regex("^F$", RegexOption.IGNORE_CASE)
    )

    // 颜色关键词（基于实际订单数据提取）
    private val colorKeywords = listOf(
        // 基础色
        "黑色", "白色", "红色", "粉色", "蓝色", "绿色", "紫色", "黄色", "灰色", "棕色",
        "黑", "白", "红", "粉", "蓝", "绿", "紫", "黄", "灰", "棕",
        // 深浅变体
        "深蓝", "浅蓝", "天蓝", "藏蓝", "深绿", "浅绿", "墨绿", "军绿",
        "深紫", "浅紫", "暗红", "酒红", "玫红", "粉红",
        // 特殊色名
        "绀色", "苔绿", "生成色", "香槟色", "白金色", "月银色", "深海色",
        "龙骨酒红色", "金色", "银色", "玫瑰金", "肤色",
        "米白", "米黄", "米色", "卡其", "驼色", "咖啡",
        // 双色组合
        "黑白", "蓝黑", "灰黑", "绿金", "粉绿", "黑粉",
        "黑x红", "黑x青", "白×蓝", "黑x红色", "黑x青色", "白×蓝色",
        // 系列色名
        "织金", "白玫瑰", "黑玫瑰", "黑夕", "白昼", "蓝暮", "紫夜", "白金", "深海",
        // 配色/拼色表达
        "配色", "拼色", "撞色", "混色", "渐变",
        // 图色等特殊值
        "图色"
    )

    private fun isSizeKeyword(part: String): Boolean {
        return sizePatterns.any { it.matches(part) }
    }

    private fun isColorKeyword(part: String): Boolean {
        return colorKeywords.any { keyword ->
            part.contains(keyword, ignoreCase = true)
        }
    }

    private fun getCellString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        require(cell.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA) { "订单文件包含公式，请导出为普通数值后重试" }
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.trim()
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BLANK -> ""
            else -> cell.toString().trim()
        }
    }

    private data class ParsedStyle(
        val type: String? = null,
        val size: String? = null,
        val color: String? = null
    )

    private class OrderBuilder(
        val orderId: String,
        val orderTime: String,
        val orderStatus: String,
        val shopName: String,
        val totalPaid: Double,
        val rawPaid: String,
        val rawShipping: String,
        val shipping: Double,
        val items: MutableList<TaobaoOrderItem> = mutableListOf()
    ) {
        fun build() = TaobaoOrder(
            orderId = orderId,
            orderTime = orderTime,
            orderStatus = orderStatus,
            shopName = shopName,
            totalPaid = totalPaid,
            shipping = shipping,
            items = items.toList(), rawPaid = rawPaid, rawShipping = rawShipping
        )
    }
}
