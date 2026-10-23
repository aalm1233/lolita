package com.lolita.app.data

import android.net.Uri
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lolita.app.data.file.*
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.domain.usecase.*
import com.lolita.app.ui.screen.price.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.*
import java.util.UUID

class AfterSaleBusinessTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var orders: OrderRepository
    private lateinit var after: AfterSaleRepository
    private lateinit var items: ItemRepository
    private val imagesToClean = mutableSetOf<String>()
    private val models = ViewModelStore()
    private var sequence = 0
    private var itemId = 0L; private var priceId = 0L; private var child = 0L; private var wish = 0L
    private val now = System.currentTimeMillis()
    private val lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private suspend fun snapshot() = after.snapshot(priceId)!!
    private suspend fun refund(amount: Double, kind: RefundKind = RefundKind.REFUND, at: Long = now, token: String = UUID.randomUUID().toString()): Long {
        return after.recordRefund(OrderRefund(priceId = priceId, amount = amount, kind = kind, receivedAt = at, operationId = token), snapshot().fingerprint)
    }
    private suspend fun reject(action: suspend () -> Unit) { try { action(); fail("Expected rejection") } catch (expected: IllegalArgumentException) { } catch (expected: IllegalStateException) { } }
    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }
    private fun track(vm: AfterSaleEditorViewModel) = vm.also { models.put("${++sequence}", it) }
    private suspend fun await(vm: AfterSaleEditorViewModel) = withTimeout(10_000) { vm.state.first { !it.loading } }
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        orders = OrderRepository(db, context); after = AfterSaleRepository(db, context); items = ItemRepository(db.itemDao(), database = db)
        val brand = db.brandDao().insertBrand(Brand(name = "退款验收")); val category = db.categoryDao().insertCategory(Category(name = "JSK"))
        itemId = db.itemDao().insertItem(Item(name = "原主件", brandId = brand, categoryId = category, description = "原资料", status = ItemStatus.WISHED))
        wish = db.itemDao().insertItem(Item(name = "心愿", brandId = brand, categoryId = category, description = "", status = ItemStatus.WISHED))
        child = db.itemDao().insertItem(Item(name = "共价小物", brandId = brand, categoryId = category, description = "", status = ItemStatus.WISHED, parentItemId = itemId, includedInParentPrice = true))
        priceId = orders.createOrder(Price(itemId = itemId, type = PriceType.DEPOSIT_BALANCE, totalPrice = 692.0), listOf(
            PaymentStage(priceId = 0, name = "定金", amount = 200.0, dueDate = lastMonth), PaymentStage(priceId = 0, name = "尾款", amount = 492.0, dueDate = now + 86400000)))
        orders.recordPayment(priceId, 200.0, lastMonth)
        Unit
    }
    @After fun tearDown() = runBlocking { main { models.clear() }; db.close(); imagesToClean.forEach { File(it).delete() } }

    @Test fun referenceIsIndependentCasAndWishlistUsesOnlyExplicitQuotes() = runBlocking {
        val oldItem = db.itemDao().getItemById(wish)
        after.saveReference(wish, 680.12, "店铺标价", null)
        assertEquals(oldItem, db.itemDao().getItemById(wish)); assertNull(db.orderDao().getBinding(wish))
        assertEquals(680.12, db.priceDao().getWishlistTotalBudget().first(), 0.0)
        assertEquals(680.12, db.priceDao().getItemPriceSums().first().single { it.itemId == wish }.totalPrice, 0.0)
        assertEquals(692.0, db.priceDao().getItemPriceSums().first().single { it.itemId == itemId }.totalPrice, 0.0)
        assertTrue(db.priceDao().getPricesByItemList(wish).isEmpty())
        val old = after.getReference(wish)!!
        after.saveReference(wish, 0.0, "合法零元参考", old)
        assertEquals(0.0, db.priceDao().getItemPriceSums().first().single { it.itemId == wish }.totalPrice, 0.0)
        reject { after.saveReference(wish, 5.0, "陈旧覆盖", old) }
        reject { after.removeReference(wish, old) }
        reject { after.saveReference(child, 9.0, "共价不能重复", null) }
        val latest = after.getReference(wish)!!; after.removeReference(wish, latest)
        assertNull(after.getReference(wish)); assertEquals(oldItem, db.itemDao().getItemById(wish))
    }
    @Test fun referenceAdoptionOnlyChangesPriceDraft() = runBlocking {
        after.saveReference(wish, 680.0, "含邮费参考", null)
        val vm = main { PriceEditViewModel(orders, items).also { models.put("price", it); it.loadPrice(null, wish) } }
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        assertEquals("", vm.uiState.value.totalPrice)
        main { vm.useIndependentReference() }
        assertEquals("680", vm.uiState.value.totalPrice); assertEquals("0", vm.uiState.value.shipping)
        assertNull(db.orderDao().getBinding(wish)); assertEquals(ItemStatus.WISHED, db.itemDao().getItemById(wish)!!.status)
    }
    @Test fun multipleRefundsCrossMonthGrossPaymentsPreservedAndNoNewDebt() = runBlocking {
        val before = snapshot(); val id = refund(50.25, RefundKind.PRICE_DIFFERENCE)
        refund(49.75)
        val latest = snapshot()
        assertEquals(before.payments, latest.payments); assertEquals(before.price, latest.price); assertEquals(before.stages, latest.stages)
        assertEquals(100.0, latest.refunded, 0.0); assertEquals(100.0, latest.net, 0.0)
        assertEquals(492.0, orders.getOrder(priceId).first()!!.remainingAmount, 0.0)
        assertEquals(200.0, db.priceDao().getAllRecordedSpending().first(), 0.0)
        assertEquals(100.0, db.priceDao().getAllRefunds().first(), 0.0)
        val budget = BudgetRepository(db.budgetDao()).observe(YearMonth.now()).first().second
        assertEquals(-10000L, budget.paidCents)
        assertEquals(2, budget.entries.count { it.isPaid && it.amount < 0 })
        after.voidRefund(id, latest.fingerprint)
        assertNotNull(db.afterSaleDao().getRefund(id)!!.voidedAt)
        assertEquals(150.25, snapshot().net, 0.0)
    }
    @Test fun boundsStaleConcurrentAndDuplicateOperationAreAtomic() = runBlocking {
        val snap = snapshot(); val token = UUID.randomUUID().toString()
        val value = OrderRefund(priceId = priceId, amount = 80.0, kind = RefundKind.REFUND, receivedAt = now, operationId = token)
        val results = coroutineScope { listOf(async { after.recordRefund(value, snap.fingerprint) }, async { after.recordRefund(value, snap.fingerprint) }).awaitAll() }
        assertEquals(results[0], results[1]); assertEquals(1, snapshot().refunds.size)
        reject { after.recordRefund(value.copy(amount = 1.0), snap.fingerprint) }
        reject { refund(120.01) }; reject { refund(-1.0) }; reject { refund(0.0) }; reject { refund(0.001) }
        reject { refund(1.0, at = now + 86400000) }
        reject { after.recordRefund(value.copy(operationId = UUID.randomUUID().toString()), snap.fingerprint) }
        assertEquals(80.0, snapshot().refunded, 0.0)
        val payment = snapshot().payments.single()
        reject { orders.deletePayment(payment.id) }
        reject { orders.editPayment(payment.copy(amount = 79.0)) }
        assertEquals(200.0, snapshot().paid, 0.0)
    }
    @Test fun cancellationStopsCommitmentsKeepsEvidenceAndSupportsLaterRefundAndRepurchase() = runBlocking {
        val before = snapshot()
        after.cancelOrder(priceId, 200.0, "商家同意", before.fingerprint, true)
        assertNull(db.orderDao().getBinding(itemId))
        assertEquals(ItemStatus.WISHED, db.itemDao().getItemById(itemId)!!.status)
        assertEquals(ItemStatus.WISHED, db.itemDao().getItemById(child)!!.status)
        assertEquals(before.payments, snapshot().payments); assertEquals(before.price, snapshot().price)
        assertTrue(db.budgetDao().commitments().first().isEmpty()); assertEquals(0.0, db.paymentDao().getTotalUnpaidAmount().first(), 0.0)
        assertEquals(200.0, snapshot().pendingRefund!!, 0.0)
        reject { orders.recordPayment(priceId, 20.0, now) }; reject { orders.bindHistoricalOrder(itemId, priceId) }
        refund(50.0); assertEquals(150.0, snapshot().pendingRefund!!, 0.0)
        after.cancelOrder(priceId, 150.0, "协商留50定金", snapshot().fingerprint, true)
        assertEquals(100.0, snapshot().pendingRefund!!, 0.0)
        refund(100.0); assertEquals(0.0, snapshot().pendingRefund!!, 0.0); assertEquals(50.0, snapshot().net, 0.0)
        val newId = orders.createOrder(Price(itemId = itemId, type = PriceType.FULL, totalPrice = 400.0), listOf(PaymentStage(priceId = 0, name = "全款", amount = 400.0)))
        assertTrue(newId != priceId); assertEquals(before.payments, snapshot().payments)
        assertEquals(newId, db.orderDao().getBinding(itemId)!!.priceId)
    }
    @Test fun noPaymentAndNonRefundableCancellationNeverInventRefund() = runBlocking {
        reject { after.cancelOrder(priceId, 200.0, "未确认", snapshot().fingerprint, false) }
        after.cancelOrder(priceId, 0.0, "定金不退", snapshot().fingerprint, true)
        assertEquals(200.0, snapshot().net, 0.0); assertEquals(0.0, snapshot().pendingRefund!!, 0.0); assertTrue(snapshot().refunds.isEmpty())
        val unpaid = orders.createOrder(Price(itemId = wish, type = PriceType.FULL, totalPrice = 100.0), listOf(PaymentStage(priceId = 0, name = "全款", amount = 100.0)))
        after.cancelOrder(unpaid, 0.0, "未付款取消", after.snapshot(unpaid)!!.fingerprint, true)
        assertTrue(after.snapshot(unpaid)!!.refunds.isEmpty())
        assertEquals(ItemStatus.WISHED, db.itemDao().getItemById(wish)!!.status)
        after.cancelOrder(priceId, null, "约定仍待确认", snapshot().fingerprint, true)
        assertNull(snapshot().pendingRefund)
        refund(30.0)
        assertNull(snapshot().pendingRefund); assertEquals(170.0, snapshot().net, 0.0)
    }
    @Test fun savedStateRestoresInputsAndRejectsStaleSnapshotAndDuplicateSave() = runBlocking {
        val handle = SavedStateHandle()
        val vm = main { track(AfterSaleEditorViewModel(after, items, handle)).also { it.load(AfterSaleEditorMode.REFUND, priceId) } }; await(vm)
        main { vm.amount("50.00"); vm.note("差价"); vm.kind(RefundKind.PRICE_DIFFERENCE); vm.date(lastMonth); vm.load(AfterSaleEditorMode.REFUND, priceId) }
        val copy = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = main { track(AfterSaleEditorViewModel(after, items, copy)).also { it.load(AfterSaleEditorMode.REFUND, priceId) } }; await(restored)
        assertEquals("50.00", restored.state.value.amount); assertEquals(lastMonth, restored.state.value.date); assertEquals(RefundKind.PRICE_DIFFERENCE, restored.state.value.kind)
        main { restored.save(); restored.save() }
        withTimeout(10_000) { restored.state.first { it.saved } }
        assertEquals(1, snapshot().refunds.size)
        val stale = main { track(AfterSaleEditorViewModel(after, items, handle)).also { it.load(AfterSaleEditorMode.REFUND, priceId) } }; await(stale)
        assertTrue(stale.state.value.failed); assertFalse(stale.state.value.valid)
    }
    @Test fun referenceAndUnknownCancellationDraftsRestoreWithoutWriting() = runBlocking {
        for (mode in listOf(AfterSaleEditorMode.REFERENCE, AfterSaleEditorMode.CANCEL)) {
            val handle = SavedStateHandle(); val target = if (mode == AfterSaleEditorMode.REFERENCE) wish else priceId
            val original = main { track(AfterSaleEditorViewModel(after, items, handle)).also { it.load(mode, target) } }; await(original)
            main { original.amount("12.34"); original.note("保留核对内容"); original.unknown(true); original.confirm(true) }
            val copy = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val restored = main { track(AfterSaleEditorViewModel(after, items, copy)).also { it.load(mode, target) } }; await(restored)
            assertEquals("12.34", restored.state.value.amount); assertEquals("保留核对内容", restored.state.value.note)
            assertTrue(restored.state.value.confirmed); assertTrue(restored.state.value.expectedUnknown)
            assertTrue(restored.state.value.valid)
        }
        assertNull(after.getReference(wish)); assertNull(snapshot().cancellation); assertTrue(snapshot().refunds.isEmpty())
    }
    @Test fun jsonZipCsvPreviewOldFormatAndInvalidBackupProtection() = runBlocking {
        after.saveReference(wish, 680.0, "含邮费", null); refund(50.0)
        after.cancelOrder(priceId, 200.0, "取消", snapshot().fingerprint, true)
        val image = File(File(context.filesDir, "images").apply { mkdirs() }, "${UUID.randomUUID()}.png")
        imagesToClean += image.absolutePath
        android.graphics.Bitmap.createBitmap(16, 24, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(android.graphics.Color.MAGENTA)
            image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val originalItem = db.itemDao().getItemById(wish)!!
        db.itemDao().updateItem(originalItem.copy(imageUrls = listOf(image.absolutePath)))
        val imageBytes = image.readBytes()
        val manager = BackupManager(context, db)
        val jsonUri = manager.exportToJson().getOrThrow()
        val text = context.contentResolver.openInputStream(jsonUri)!!.bufferedReader().use { it.readText() }
        val parsed = BackupFormat.parse(text)
        assertEquals(15, parsed.formatVersion); assertEquals(34, parsed.schemaVersion)
        assertEquals(1, parsed.referencePrices.size); assertEquals(1, parsed.orderRefunds.size); assertEquals(1, parsed.orderCancellations.size)
        val zip = manager.exportToZip().getOrThrow(); val preview = manager.previewBackup(zip).getOrThrow()
        assertEquals(1, preview.referencePriceCount); assertEquals(1, preview.refundCount); assertEquals(1, preview.cancelledOrderCount)
        val before = snapshot()
        manager.importBackup(zip).getOrThrow(); assertEquals(before, snapshot())
        val restoredImage = db.itemDao().getItemById(wish)!!.imageUrls.single()
        imagesToClean += restoredImage
        assertArrayEquals(imageBytes, File(restoredImage).readBytes())
        assertArrayEquals(imageBytes, image.readBytes())
        assertEquals(parsed.referencePrices, db.afterSaleDao().references())
        val csv = context.contentResolver.openInputStream(manager.exportToCsv().getOrThrow())!!.bufferedReader().use { it.readText() }
        assertTrue(csv.contains("[退款与退差价]")); assertTrue(csv.contains("[参考价]")); assertTrue(csv.contains("[取消订单]"))
        assertFalse(csv.contains(parsed.orderRefunds.single().operationId))
        val old = JsonParser.parseString(text).asJsonObject
        old.addProperty("formatVersion", 14); old.addProperty("schemaVersion", 33)
        listOf("referencePrices", "orderRefunds", "orderCancellations").forEach { old.remove(it) }
        val legacy = BackupFormat.parse(old.toString()); assertTrue(legacy.referencePrices.isEmpty()); assertEquals(parsed.prices, legacy.prices)
        val bad = JsonParser.parseString(text).asJsonObject
        bad["orderRefunds"].asJsonArray[0].asJsonObject.addProperty("amount", 999.0)
        reject { BackupFormat.parse(bad.toString()) }
        val f = File(context.cacheDir, "invalid-after-sale-${UUID.randomUUID()}.json").apply { writeText(bad.toString()) }
        try { assertTrue(manager.importBackup(Uri.fromFile(f)).isFailure); assertEquals(before, snapshot()) } finally { f.delete() }
    }
}
