package com.lolita.app.data.file

import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

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

    fun parse(inputStream: InputStream): List<TaobaoOrder> = inputStream.use { stream ->
        XSSFWorkbook(stream).use { workbook ->
        val sheet = workbook.getSheetAt(0)

        // 按订单号分组的临时结构
        val orderMap = linkedMapOf<String, OrderBuilder>()
        var currentOrderId = ""

        // 跳过表头，从第1行开始
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue

            // 读取订单号 — 如果本行有值则更新当前订单号
            val rawOrderId = getCellString(row.getCell(0))
            if (rawOrderId.isNotBlank()) {
                currentOrderId = rawOrderId
            }
            if (currentOrderId.isBlank()) continue

            // 首次遇到该订单号时，读取订单级字段
            if (currentOrderId !in orderMap) {
                orderMap[currentOrderId] = OrderBuilder(
                    orderId = currentOrderId,
                    orderTime = getCellString(row.getCell(1)),
                    orderStatus = getCellString(row.getCell(2)),
                    shopName = getCellString(row.getCell(3)),
                    totalPaid = parsePrice(getCellString(row.getCell(9))),
                    shipping = parsePrice(getCellString(row.getCell(10)))
                )
            }
            // 读取商品级字段
            val itemName = getCellString(row.getCell(4))
            if (itemName.isBlank()) continue

            val productUrl = getCellString(row.getCell(5))
            val styleSpec = getCellString(row.getCell(6))
            val quantity = getCellString(row.getCell(7)).toIntOrNull() ?: 1
            val price = parsePrice(getCellString(row.getCell(8)))

            val parsed = parseStyleSpec(styleSpec)

            val builder = orderMap[currentOrderId]!!
            builder.items.add(
                TaobaoOrderItem(
                    name = itemName,
                    productUrl = productUrl,
                    styleSpec = styleSpec,
                    quantity = quantity,
                    price = price,
                    parsedType = parsed.type,
                    parsedSize = parsed.size,
                    parsedColor = parsed.color,
                    orderId = currentOrderId,
                    orderTime = builder.orderTime,
                    shopName = builder.shopName
                )
            )
        }

        orderMap.values.map { it.build() }
    } // workbook.use
    } // inputStream.use

    private fun parsePrice(raw: String): Double {
        return raw.replace("￥", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
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
            .replace(Regex("\\[[^\\]]*]"), "")
            .replace(Regex("\\{[^}]*}"), "")
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
            .replace(Regex("\\d+\\.\\d+(?:晚上|上午|下午)?[^;/]{0,10}"), "")
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
            part.split(Regex("\\s+|--")).map { it.trim() }.filter { it.isNotBlank() }
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

        // 2. 精确类型匹配
        if (typeKeywords.any { part.equals(it, ignoreCase = true) }) {
            return Triple(part, null, null)
        }

        // 3. 后缀类型匹配 (如 "粉色柳波芙jsk", "深海色FS")
        for (suffix in typeSuffixes) {
            if (part.endsWith(suffix, ignoreCase = true) && part.length > suffix.length) {
                val prefix = part.substring(0, part.length - suffix.length)
                val extractedColor = if (isColorKeyword(prefix)) prefix else null
                return Triple(part, null, extractedColor)
            }
        }

        // 4. 包含类型匹配（长关键词优先）
        val sortedTypeKeywords = typeKeywords.sortedByDescending { it.length }
        for (keyword in sortedTypeKeywords) {
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

        // 5. 前缀尺寸提取 (如 "M半裙", "L白色JSK", "XL上衣")
        val sizePrefix = extractLeadingSize(part)
        if (sizePrefix != null) {
            val remaining = part.substring(sizePrefix.length).trim()
            if (remaining.isNotBlank()) {
                val (t, _, c) = classifyPart(remaining)
                return Triple(t, sizePrefix, c)
            }
            return Triple(null, sizePrefix, null)
        }

        // 6. 颜色匹配
        if (isColorKeyword(part)) return Triple(null, null, part)

        // 7. 兜底：归为颜色
        return Triple(null, null, part)
    }

    private fun extractLeadingSize(part: String): String? {
        Regex("^(Lady\\d+)", RegexOption.IGNORE_CASE).find(part)?.let {
            if (it.value.length < part.length) return it.value
        }
        Regex("^(XS|XXL|XXXL|2XL|3XL|XL|S|M|L)", RegexOption.IGNORE_CASE).find(part)?.let {
            if (it.value.length < part.length) return it.value
        }
        return null
    }

    private val typeKeywords = listOf(
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
        Regex("^Lady\\d+$", RegexOption.IGNORE_CASE),
        Regex("^\\d{2,3}$"),                        // 2-3位数字（排除年份等4位数）
        Regex("^(XS|S|M|L|XL|XXL|XXXL|2XL|3XL)$", RegexOption.IGNORE_CASE),
        Regex("^均码$"),
        Regex("^F$", RegexOption.IGNORE_CASE)
    )

    private val typeSuffixes = listOf("OP", "SK", "JSK", "SET", "FS")

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

    private fun isTypeKeyword(part: String): Boolean {
        if (typeKeywords.any { part.equals(it, ignoreCase = true) }) return true
        return typeSuffixes.any { suffix ->
            part.endsWith(suffix, ignoreCase = true) && part.length > suffix.length
        }
    }

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
            items = items.toList()
        )
    }
}
