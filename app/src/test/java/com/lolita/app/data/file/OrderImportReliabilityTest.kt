package com.lolita.app.data.file

import com.lolita.app.data.model.*
import com.lolita.app.domain.model.*
import java.io.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Test

class OrderImportReliabilityTest {
    @Test
    fun unknownZeroAndInvalidAmountsStayDistinct() {
        assertEquals(ImportMoney.Missing, OrderImportSource.money(" "))
        assertEquals(ImportMoney.Valid(0), OrderImportSource.money("¥0.00"))
        assertEquals(ImportMoney.Valid(123450), OrderImportSource.money("￥1,234.50"))
        listOf("待确认", "NaN", "Infinity", "-1", "1.001", "1,2", "1¥2").forEach {
            assertTrue(it, OrderImportSource.money(it) is ImportMoney.Invalid)
        }
    }

    @Test
    fun sourceIdentitySurvivesRowReorderingAndSeparatesSizesAndUnits() {
        val a =
            TaobaoOrderItem(
                "测试裙",
                "https://item.taobao.com/item.htm?id=12345&spm=private",
                "蓝色;M",
                2,
                100.0,
                "JSK",
                "M",
                "蓝色",
                "order-1",
                "2026-10-01",
                "店铺",
            )
        val b = a.copy(styleSpec = "蓝色;L", quantity = 1, parsedSize = "L")
        val order = TaobaoOrder("order-1", "2026-10-01", "交易成功", "店铺", 300.0, 0.0, listOf(a, b))
        assertEquals(
            OrderImportSource.snapshot(order),
            OrderImportSource.snapshot(order.copy(items = listOf(b, a))),
        )
        assertNotEquals(OrderImportSource.key(a, 0), OrderImportSource.key(a, 1))
        assertNotEquals(OrderImportSource.key(a, 0), OrderImportSource.key(b, 0))
        assertEquals(
            "https://item.taobao.com/item.htm?id=12345",
            OrderImportSource.publicUrl(a.productUrl),
        )
        assertNotEquals(
            OrderImportSource.snapshot(order),
            OrderImportSource.snapshot(order.copy(totalPaid = 299.0)),
        )
        assertNotEquals(
            OrderImportSource.snapshot(order),
            OrderImportSource.snapshot(
                order.copy(items = listOf(a.copy(actualPaidDate = "2026-10-02"), b))
            ),
        )
        assertEquals(
            OrderImportSource.snapshot(order),
            OrderImportSource.snapshot(order.copy(rawPaid = "300.00", rawShipping = "0")),
        )
        val equalGroup =
            order.copy(items = listOf(a.copy(quantity = 1), a.copy(quantity = 1, price = 99.0)))
        val reversed = equalGroup.copy(items = equalGroup.items.reversed())
        assertEquals(
            OrderImportSource.ordinals(equalGroup)[0 to 0],
            OrderImportSource.ordinals(reversed)[1 to 0],
        )
        assertNull(
            OrderImportSource.publicUrl("https://item.taobao.com.evil.example/item.htm?id=12")
        )
    }

    @Test
    fun parserUsesValidatedHeadersAndPreservesUnknownRawFields() {
        val values =
            linkedMapOf(
                "商品名称" to "样例裙",
                "商品数量" to "1",
                "实付金额" to "待确认",
                "订单号" to "12345678901234567890",
                "店铺名称" to "样例店",
                "商品链接" to "https://item.taobao.com/item.htm?id=1&token=private",
                "订单提交时间" to "2026-10-01",
                "订单状态" to "交易成功",
                "型号款式" to "蓝色;M;JSK",
                "商品金额" to "待确认",
                "运费" to "0",
            )
        val order = TaobaoOrderParser.parse(workbook(values)).single()
        assertEquals("12345678901234567890", order.orderId)
        assertEquals("待确认", order.rawPaid)
        assertNull(OrderImportSource.amount(order.rawPaid, order.totalPaid))
        assertEquals("待确认", order.items.single().rawPrice)
        assertEquals("https://item.taobao.com/item.htm?id=1", order.items.single().productUrl)
        assertTrue(runCatching { TaobaoOrderParser.parse(workbook(values - "订单号")) }.isFailure)
    }

    @Test
    fun differingExportsCannotBeSilentlyDeduplicated() {
        fun row(paid: String) =
            linkedMapOf(
                "订单号" to "o1",
                "订单提交时间" to "2026-10-01",
                "订单状态" to "交易成功",
                "店铺名称" to "店",
                "商品名称" to "裙",
                "商品链接" to "",
                "型号款式" to "M;JSK",
                "商品数量" to "1",
                "商品金额" to "100",
                "实付金额" to paid,
                "运费" to "0",
            )
        assertTrue(
            runCatching {
                    TaobaoOrderParser.parseMultipleFiles(
                        listOf(workbook(row("100")), workbook(row("99")))
                    )
                }
                .isFailure
        )
        assertEquals(
            1,
            TaobaoOrderParser.parseMultipleFiles(listOf(workbook(row("100")), workbook(row("100"))))
                .third,
        )
    }

    @Test
    fun decompressionIsBoundedBeforeWorkbookAllocation() {
        val bytes =
            ByteArrayOutputStream()
                .also { output ->
                    java.util.zip.ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(java.util.zip.ZipEntry("xl/sharedStrings.xml"))
                        zip.write(ByteArray(2 * 1024 * 1024))
                        zip.closeEntry()
                    }
                }
                .toByteArray()
        assertTrue(runCatching { OrderFilePreflight.read(ByteArrayInputStream(bytes)) }.isFailure)
    }

    private fun workbook(values: Map<String, String>): InputStream {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { book ->
            val sheet = book.createSheet()
            val head = sheet.createRow(0)
            val row = sheet.createRow(1)
            values.entries.forEachIndexed { i, v ->
                head.createCell(i).setCellValue(v.key)
                row.createCell(i).setCellValue(v.value)
            }
            book.write(out)
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun missingOrderDatesFilterWithoutClearingHiddenSelectionAndRejectInvalidSupplementEvidence() {
        val row =
            TaobaoOrderItem(
                "测试裙",
                "https://item.taobao.com/item.htm?id=1",
                "M",
                1,
                10.0,
                "JSK",
                "M",
                "",
                "a",
                "未知",
                "店铺",
            )
        val missing = TaobaoOrder("a", "未知", "交易成功", "店铺", 10.0, 0.0, listOf(row))
        val dated =
            missing.copy(
                orderId = "b",
                orderTime = "2026-10-01",
                items = listOf(row.copy(orderId = "b")),
            )
        val state =
            com.lolita.app.ui.screen.`import`.TaobaoImportUiState(
                orders = listOf(missing, dated),
                selectedItems = setOf("a:0", "b:0"),
                selectionFilter = "日期未知",
            )
        assertEquals(
            listOf("a"),
            com.lolita.app.ui.screen.`import`.visibleOrderLines(state).map { it.first.orderId },
        )
        assertEquals(2, state.selectedItems.size)
        val payload =
            ProductSupplementPayload(
                candidate =
                    TaobaoProductDraft(
                        "测试裙",
                        "",
                        "",
                        "",
                        "",
                        "https://item.taobao.com/item.htm?id=1",
                        emptyList(),
                    ),
                photoIndices = emptyList(),
                detailIndices = emptyList(),
                existingEditor = false,
            )
        val json = com.google.gson.JsonParser.parseString(payload.encode()).asJsonObject
        json["candidate"].asJsonObject.addProperty("untrackedImagePath", "/private/file.png")
        assertTrue(runCatching { ProductSupplementPayload.decode(json.toString()) }.isFailure)
        assertTrue(
            runCatching {
                    payload
                        .copy(
                            candidate =
                                payload.candidate.copy(
                                    productUrl = payload.candidate.productUrl + "&token=private"
                                )
                        )
                        .validate()
                }
                .isFailure
        )
    }
}
