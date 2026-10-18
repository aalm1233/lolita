package com.lolita.app.data.file

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TaobaoOrderParserTest {
    @Test fun joinedJskUsesLongestTypeAndKeepsColorSeparate() {
        val item = parseSpec("白色JSK;M")
        assertEquals("JSK", item.parsedType)
        assertEquals("白色", item.parsedColor)
        assertEquals("M", item.parsedSize)
    }

    @Test fun leadingSizesAreExtractedBeforeClothingType() {
        listOf("M半裙" to "M", "L白色JSK" to "L", "XXXL上衣" to "XXXL", "Lady80开襟OP" to "Lady80").forEach { (spec, size) ->
            val item = parseSpec(spec)
            assertEquals(spec, size, item.parsedSize)
        }
        assertEquals("半裙", parseSpec("M半裙").parsedType)
        assertEquals("白色", parseSpec("L白色JSK").parsedColor)
    }

    @Test fun englishColorNamesAreNotLeadingSizes() {
        listOf("Sax蓝", "Mint绿", "Lavender紫").forEach { color ->
            val item = parseSpec("${color}JSK")
            assertNull(color, item.parsedSize)
            assertEquals("JSK", item.parsedType)
            assertEquals(color, item.parsedColor)
        }
        assertEquals("M", parseSpec("MJSK").parsedSize)
        assertEquals("JSK", parseSpec("MJSK").parsedType)
    }

    @Test fun exactTypesKeepCanonicalSpellingAndExistingVariants() {
        assertEquals("JSK", parseSpec("白色jsk;均码").parsedType)
        assertEquals("开襟OP", parseSpec("蓝黑配色;Lady80;开襟OP").parsedType)
        assertEquals("Fullset", parseSpec("Fullset;均码").parsedType)
        assertEquals("SK", parseSpec("蓝色SK;XL").parsedType)
        assertEquals("SK", parseSpec("SK白色;M").parsedType)
        assertEquals("SET", parseSpec("SET白色;M").parsedType)
    }

    @Test fun shoesAndSockSizesKeepOriginalSystemsRangesAndDecimals() {
        val shoe = parseSpec("玛丽珍鞋;EU 37.5;黑色")
        assertEquals("玛丽珍鞋", shoe.parsedType)
        assertEquals("EU 37.5", shoe.parsedSize)
        val sock = parseSpec("过膝袜;23 至 25 cm;白色")
        assertEquals("过膝袜", sock.parsedType)
        assertEquals("23 至 25 cm", sock.parsedSize)
    }

    @Test fun optionalWardrobeColumnsKeepExplicitSpecificationsAndLocationText() {
        val bytes = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet()
            val header = sheet.createRow(0)
            listOf("物品类型", "房间", "收纳路径", "专属规格JSON").forEachIndexed { index, text -> header.createCell(11 + index).setCellValue(text) }
            val row = sheet.createRow(1)
            listOf("order-2", "2026-10-14", "交易成功", "测试品牌", "长卷", "", "EU 37.5", "1", "100", "110", "10",
                "全头假发", "卧室", "卧室 → 假发架", "{\"version\":1,\"wig\":{\"lengthCm\":\"60 cm\"}}")
                .forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
            workbook.write(bytes)
        }
        val item = TaobaoOrderParser.parse(ByteArrayInputStream(bytes.toByteArray())).single().items.single()
        assertEquals("全头假发", item.parsedType)
        assertEquals("卧室", item.requestedRoomName)
        assertEquals("卧室 → 假发架", item.requestedStoragePath)
        assertEquals("60 cm", com.lolita.app.domain.model.WardrobeSpecifications.decode(item.specificationsJson).wig.lengthCm)
    }

    private fun parseSpec(spec: String): com.lolita.app.data.model.TaobaoOrderItem {
        val bytes = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet()
            sheet.createRow(0)
            val row = sheet.createRow(1)
            listOf("order-1", "2026-10-14", "卖家已发货", "测试品牌", "裙子", "", spec, "1", "100", "110", "10")
                .forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
            workbook.write(bytes)
        }
        return TaobaoOrderParser.parse(ByteArrayInputStream(bytes.toByteArray())).single().items.single()
    }
}
