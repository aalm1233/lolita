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
