package com.lolita.app.ui.screen.`import`

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.model.TaobaoOrder
import com.lolita.app.data.model.TaobaoOrderItem
import com.lolita.app.data.repository.*
import com.lolita.app.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class TaobaoOrderImportEvidenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var vm: TaobaoImportViewModel
    private val store = ViewModelStore()
    private val originals = mutableListOf<File>()
    private val copiedPaths = mutableListOf<String>()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        ImageFileHelper.configure(context, db)
        db.brandDao().insertBrand(Brand(id = 1, name = "测试品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "SK"))
        db.categoryDao().insertCategory(Category(id = 2, name = "JSK"))
        instrumentation.runOnMainSync {
            vm = TaobaoImportViewModel(context.applicationContext as Application,
                BrandRepository(db.brandDao(), db.itemDao(), db),
                CategoryRepository(db.categoryDao(), db.itemDao(), db),
                ItemRepository(db.itemDao(), database = db),
                PriceRepository(db.priceDao(), db.paymentDao(), context, db),
                OrderRepository(db, context), db)
            store.put("import", vm)
        }
        await { vm.uiState.value.categories.map { it.id }.containsAll(listOf(1L,2L)) }
    }

    @After fun close() = runBlocking {
        instrumentation.runOnMainSync { store.clear() }
        ImageFileHelper.cleanupUnreferencedImages(copiedPaths).join()
        ImageFileHelper.configure(context, AppModule.database())
        copiedPaths.forEach { File(it).delete() }
        originals.forEach { it.delete() }
        db.close()
    }

    @Test fun shippedPaidOrderCommitsActualPaymentAndHasNoBalance() = runBlocking {
        prepare("卖家已发货")
        val row = vm.uiState.value.importItems.single()
        assertEquals(110.0, row.paidAmount, 0.0)
        assertEquals(100.0, row.price, 0.0)
        assertFalse(row.financialConfirmed)
        instrumentation.runOnMainSync {
            vm.updateImportItem(0) { it.copy(financialConfirmed = true) }
            vm.executeImport()
        }
        await { vm.uiState.value.currentStep == ImportStep.RESULT || vm.uiState.value.errorMessage != null }
        assertNull(vm.uiState.value.errorMessage)
        val price = db.priceDao().getAllPricesList().single()
        val payment = db.paymentDao().getAllPaymentsList().single()
        assertEquals(110.0, price.totalPrice, 0.0)
        assertEquals(110.0, payment.amount, 0.0)
        assertEquals(0.0, price.totalPrice - payment.amount, 0.0)
        assertEquals(ItemStatus.OWNED, db.itemDao().getAllItemsList().single().status)
    }

    @Test fun closedAndRefundStatusesPreserveSourceEvidenceButRequireReview() {
        listOf("交易关闭", "退款成功", "等待买家确认收货").forEach { status ->
            prepare(status)
            val row = vm.uiState.value.importItems.single()
            assertEquals(status, 110.0, row.paidAmount, 0.0)
            assertFalse(row.financialConfirmed)
            assertFalse(isImportItemReady(0, vm.uiState.value.importItems))
        }
    }

    @Test fun fullyPaidImportAppliesItsRequestedStorageAfterLedgerStatusIsKnown() = runBlocking {
        db.locationDao().insertLocation(Location(id=1,name="导入房间"))
        val node=StorageRepository(db).createNode(1,null,"导入衣柜")
        prepare()
        assertFalse(isImportStorageAvailable(0,vm.uiState.value.importItems))
        instrumentation.runOnMainSync {
            vm.updateImportItem(0){it.copy(financialConfirmed=true,locationId=1,storageNodeId=node)}
        }
        assertTrue(isImportStorageAvailable(0,vm.uiState.value.importItems))
        instrumentation.runOnMainSync {vm.executeImport()}
        await {vm.uiState.value.currentStep==ImportStep.RESULT||vm.uiState.value.errorMessage!=null}
        assertNull(vm.uiState.value.errorMessage)
        val item=db.itemDao().getAllItemsList().single()
        assertEquals(ItemStatus.OWNED,item.status)
        assertEquals(1L,item.locationId);assertEquals(node,item.storageNodeId)
        assertEquals(110.0,db.paymentDao().getAllPaymentsList().single().amount,0.0)
    }

    @Test fun depositOnlyImportCommitsWithoutAssigningItsRequestedStorage() = runBlocking {
        db.locationDao().insertLocation(Location(id=1,name="导入房间"))
        val node=StorageRepository(db).createNode(1,null,"导入衣柜")
        prepare()
        instrumentation.runOnMainSync {
            vm.updateImportItem(0){it.copy(financialConfirmed=true,locationId=1,storageNodeId=node,
                paymentRole=PaymentRole.DEPOSIT,price=20.0,shipping=0.0,paidAmount=20.0,manualBalance=80.0)}
            vm.updateImportItem(0){it.copy(financialConfirmed=true)}
        }
        assertFalse(isImportStorageAvailable(0,vm.uiState.value.importItems))
        instrumentation.runOnMainSync {vm.executeImport()}
        await {vm.uiState.value.currentStep==ImportStep.RESULT||vm.uiState.value.errorMessage!=null}
        assertNull(vm.uiState.value.errorMessage)
        val item=db.itemDao().getAllItemsList().single()
        assertEquals(ItemStatus.PENDING_BALANCE,item.status)
        assertNull(item.locationId);assertNull(item.storageNodeId)
        assertEquals(100.0,db.priceDao().getAllPricesList().single().totalPrice,0.0)
        assertEquals(20.0,db.paymentDao().getAllPaymentsList().single().amount,0.0)
    }

    @Test fun pairedImportStorageNeedsBothPaymentsAndConfirmationAndHandlesInvalidTotals() {
        prepare()
        val row=vm.uiState.value.importItems.single()
        val rows=listOf(row.copy(paymentRole=PaymentRole.DEPOSIT,pairedWith=1,price=20.0,shipping=0.0,paidAmount=20.0,financialConfirmed=true),
            row.copy(paymentRole=PaymentRole.BALANCE,pairedWith=0,price=80.0,shipping=0.0,paidAmount=80.0,financialConfirmed=true))
        assertTrue(isImportStorageAvailable(0,rows));assertTrue(isImportStorageAvailable(1,rows))
        assertFalse(isImportStorageAvailable(0,listOf(rows[0],rows[1].copy(paidAmount=0.0))))
        assertFalse(isImportStorageAvailable(0,listOf(rows[0],rows[1].copy(financialConfirmed=false))))
        assertFalse(isImportStorageAvailable(0,rows.map{it.copy(price=999999999.99,paidAmount=999999999.99)}))
    }

    @Test fun joinedTypeMatchesJskBeforeSkCategory() {
        prepare(parsedType = "白色JSK")
        assertEquals(2L, vm.uiState.value.importItems.single().categoryId)
    }

    @Test fun missingJskRequiresCreatingJskInsteadOfFallingBackToSk() = runBlocking {
        prepare()
        db.categoryDao().deleteCategory(db.categoryDao().getCategoryById(2)!!)
        await { vm.uiState.value.categories.size == 1 }
        instrumentation.runOnMainSync {
            vm.goBackToSelect()
            vm.proceedToPrepare()
        }
        assertEquals(ImportStep.PREPARE, vm.uiState.value.currentStep)
        assertEquals(listOf("JSK"), vm.uiState.value.missingItems.map { it.name })
        assertEquals(1, db.categoryDao().getAllCategoriesList().size)
    }

    @Test fun replacingAndClearingPickedImageDeletesOnlyDraftCopies() {
        prepare()
        val first = pickImage()
        val second = pickImage()
        await { !File(first).exists() }
        assertTrue(File(second).exists())
        instrumentation.runOnMainSync { vm.updateImportItem(0) { it.copy(imageUrl = null) } }
        await { !File(second).exists() }
        assertTrue(originals.all { it.exists() })
    }

    @Test fun returningAndRebuildingSelectionCleansDraftImage() {
        prepare()
        val image = pickImage()
        instrumentation.runOnMainSync { vm.goBackToSelect(); vm.proceedToPrepare() }
        await { vm.uiState.value.currentStep == ImportStep.DETAIL }
        await { !File(image).exists() }
    }

    @Test fun clearingViewModelCleansDraftButPreservesPersistedReference() = runBlocking {
        prepare()
        val image = pickImage()
        db.itemDao().insertItem(Item(name = "已保存的共享图片", brandId = 1, categoryId = 2, imageUrls = listOf(image), description = "", status = ItemStatus.OWNED))
        instrumentation.runOnMainSync { store.clear() }
        ImageFileHelper.cleanupUnreferencedImages(listOf(image)).join()
        assertTrue(File(image).exists())
    }

    @Test fun clearingViewModelDeletesUnsavedImage() {
        prepare()
        val image = pickImage()
        instrumentation.runOnMainSync { store.clear() }
        await { !File(image).exists() }
    }

    @Test fun successfulImportCleansSkippedImageAndRetainsSavedImage() = runBlocking {
        prepare(quantity = 2)
        val saved = pickImage(0)
        val skipped = pickImage(1)
        instrumentation.runOnMainSync {
            vm.updateImportItem(0) { it.copy(financialConfirmed = true) }
            vm.executeImport()
        }
        await { vm.uiState.value.currentStep == ImportStep.RESULT || vm.uiState.value.errorMessage != null }
        assertNull(vm.uiState.value.errorMessage)
        await { !File(skipped).exists() }
        assertTrue(File(saved).exists())
        assertEquals(listOf(saved), db.itemDao().getAllItemsList().single().imageUrls)
    }

    private fun prepare(status: String = "交易成功", parsedType: String = "JSK", quantity: Int = 1) {
        val original = TaobaoOrderItem("裙子", "", "白色JSK;M", quantity, 100.0, parsedType, "M", "白色", "source-order", "2026-10-14", "测试品牌")
        val order = TaobaoOrder("source-order", "2026-10-14", status, "测试品牌", 110.0, 10.0, listOf(original))
        instrumentation.runOnMainSync {
            @Suppress("UNCHECKED_CAST")
            val state = TaobaoImportViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }.get(vm) as MutableStateFlow<TaobaoImportUiState>
            state.value = state.value.copy(orders = listOf(order), selectedItems = setOf("source-order:0"), currentStep = ImportStep.SELECT)
            vm.proceedToPrepare()
        }
        await { vm.uiState.value.currentStep == ImportStep.DETAIL }
    }

    private fun pickImage(index: Int = 0): String {
        val previous = vm.uiState.value.importItems[index].imageUrl
        val source = File(context.cacheDir, "order-photo-${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        originals += source
        instrumentation.runOnMainSync { vm.onLocalImageSelected(index, Uri.fromFile(source)) }
        await { vm.uiState.value.importItems[index].imageUrl?.let { it != previous } == true }
        return vm.uiState.value.importItems[index].imageUrl!!.also { copiedPaths += it }
    }

    private fun await(predicate: () -> Boolean) {
        val until = System.nanoTime() + 5_000_000_000
        while (!predicate() && System.nanoTime() < until) Thread.sleep(20)
        assertTrue("等待异步导入状态超时：${vm.uiState.value}", predicate())
    }
}
