package com.lolita.app.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.entity.Payment
import com.lolita.app.data.local.entity.RestoreJournal
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

import com.lolita.app.data.local.dao.ItemDao
import com.lolita.app.data.local.dao.ItemWithFullDetails
import com.lolita.app.data.local.dao.PaymentDao
import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemPriority
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.data.local.entity.ItemPurchaseReview
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.file.ImageFileHelper
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val paymentRepository: PaymentRepository? = null,
    private val priceRepository: PriceRepository? = null,
    private val database: LolitaDatabase? = null,
    private val paymentDao: PaymentDao? = null,
    private val context: Context? = null
) {
    fun getAllItems(): Flow<List<Item>> = itemDao.getAllItems()

    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>> =
        itemDao.getItemsByStatus(status)

    fun getPurchaseReviews(): Flow<List<ItemPurchaseReview>> = itemDao.getPurchaseReviews()

    fun getWishlistByPriority(): Flow<List<Item>> =
        itemDao.getWishlistByPriority()

    fun searchItemsByName(query: String): Flow<List<Item>> =
        itemDao.searchItemsByName(query)

    suspend fun getItemById(id: Long): Item? = itemDao.getItemById(id)

    suspend fun getItemByImportToken(token: String): Item? = itemDao.getItemByImportToken(token)

    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails?> =
        itemDao.getItemWithFullDetails(id)

    fun observeItem(id: Long): Flow<Item?> = itemDao.observeItem(id)

    fun getChildren(parentId: Long): Flow<List<Item>> = itemDao.getChildren(parentId)

    suspend fun getChildrenList(parentId: Long): List<Item> = itemDao.getChildrenList(parentId)

    suspend fun insertItem(item: Item): Long = inTransaction {
        item.importToken?.let { token ->
            itemDao.getItemByImportToken(token)?.let { return@inTransaction it.id }
        }
        itemDao.insertItem(validateHierarchy(item))
    }

    suspend fun updateItem(item: Item) = inTransaction {
        require(itemDao.getItemById(item.id) != null) { "服饰已删除，无法保存修改" }
        val normalized = validateHierarchy(item).copy(updatedAt = System.currentTimeMillis())
        itemDao.updateItem(normalized)
        itemDao.updateSharedChildrenStatus(normalized.id, normalized.status.name)
    }

    suspend fun attachChild(childId: Long, parentId: Long, includedInPrice: Boolean) = inTransaction {
        val child = itemDao.getItemById(childId) ?: error("小物不存在")
        updateItem(child.copy(parentItemId = parentId, includedInParentPrice = includedInPrice))
    }

    suspend fun detachChild(childId: Long) = inTransaction {
        val child = itemDao.getItemById(childId) ?: error("小物不存在")
        updateItem(child.copy(parentItemId = null, includedInParentPrice = false))
    }

    private suspend fun validateHierarchy(item: Item): Item {
        require(!item.includedInParentPrice || item.parentItemId != null) { "共用价格的小物必须选择主件" }
        val parent = item.parentItemId?.let { parentId ->
            require(parentId != item.id) { "不能把服饰挂在自己下面" }
            val parent = itemDao.getItemById(parentId) ?: error("主件不存在，请重新选择")
            require(parent.parentItemId == null) { "小物不能作为主件，请选择独立服饰" }
            require(item.id == 0L || itemDao.getChildrenList(item.id).isEmpty()) {
                "此服饰已有所属小物，请先解除挂载后再选择主件"
            }
            parent
        }
        if (item.includedInParentPrice) {
            require(database?.orderDao()?.getBinding(item.id) == null) {
                "此小物已有独立价格，请先在价格与付款中核对并解除独立订单"
            }
            return item.copy(status = parent!!.status)
        }
        // A priced item's state belongs to the payment ledger, never to the edit form.
        if (database?.orderDao()?.getBinding(item.id) != null) {
            itemDao.getItemById(item.id)?.let { return item.copy(status = it.status) }
        }
        return item
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        database?.withTransaction { block() } ?: block()

    fun getTopBrandsByCount() = itemDao.getTopBrandsByCount()
    fun getOwnedCount() = itemDao.getOwnedCount()
    fun getWishedCount() = itemDao.getWishedCount()

    fun getItemsByBrandName(brandName: String) = itemDao.getItemsByBrandName(brandName)
    fun getItemsByCategoryName(categoryName: String) = itemDao.getItemsByCategoryName(categoryName)
    fun getItemsByStyle(style: String) = itemDao.getItemsByStyle(style)
    fun getItemsBySeason(season: String) = itemDao.getItemsBySeason(season)
    fun getWishlistByPriorityFilter(priority: ItemPriority) = itemDao.getWishlistByPriorityFilter(priority)
    fun getItemsByLocationId(locationId: Long) = itemDao.getItemsByLocationId(locationId)
    fun getItemsWithNoLocation() = itemDao.getItemsWithNoLocation()

    suspend fun deleteItem(item: Item) {
        val doDelete: suspend () -> Unit = {
            require(itemDao.getChildrenList(item.id).isEmpty()) {
                "此服饰还有所属小物，请先解除挂载或更换主件后再删除"
            }
            if (database != null) {
                val gson = Gson()
                val affected = database.priceDao().getPricesByItemList(item.id).map { it.id }.toSet()
                val previous = affected.flatMap { priceId ->
                    database.paymentDao().getPaymentsByPriceList(priceId) + database.orderDao().getStages(priceId).map { stage ->
                        Payment(id = -stage.id, priceId = stage.priceId, amount = stage.amount,
                            dueDate = stage.dueDate ?: 0L, calendarEventId = stage.calendarEventId)
                    }
                }
                val pending = database.restoreJournalDao().get()
                val retry = pending?.let { gson.fromJson(it.previousPaymentsJson, Array<Payment>::class.java).toList() }.orEmpty()
                val scope = if (pending != null && pending.affectedPriceIdsJson == null) null else {
                    val previousScope = pending?.affectedPriceIdsJson?.let { gson.fromJson(it, Array<Long>::class.java).toSet() }.orEmpty()
                    gson.toJson((affected + previousScope).sorted())
                }
                database.restoreJournalDao().save(RestoreJournal(
                    previousPaymentsJson = gson.toJson(com.lolita.app.domain.usecase.ReminderCleanup.deduplicate(previous + retry)),
                    affectedPriceIdsJson = scope
                ))
            }
            if (paymentRepository != null && priceRepository != null) {
                val prices = priceRepository.getPricesByItemList(item.id)
                for (price in prices) {
                    val payments = paymentRepository.getPaymentsByPriceList(price.id)
                    for (payment in payments) {
                        paymentRepository.deletePayment(payment)
                    }
                }
            }
            // Clear the RESTRICT price binding before Item -> Price cascades run.
            database?.orderDao()?.deleteBinding(item.id)
            itemDao.deleteItem(item)
        }
        if (database != null) {
            database.withTransaction { doDelete() }
        } else {
            doDelete()
        }
        if (database != null && context != null) {
            withContext(NonCancellable) {
                runCatching { BackupManager(context, database).recoverPendingReminders() }
                    .onFailure { Log.w("ItemRepository", "Deleted item reminder cleanup will retry", it) }
            }
        }
        item.imageUrls.forEach { try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {} }
        (listOfNotNull(item.sizeChartImageUrl) + item.sizeChartImageUrls).distinct().forEach {
            try { ImageFileHelper.deleteImage(it) } catch (_: Exception) {}
        }
    }

    suspend fun updateCoordinateOrder(itemId: Long, order: Int) {
        itemDao.updateCoordinateOrder(itemId, order)
    }

    suspend fun checkAndUpdatePendingBalanceStatus(itemId: Long) {
        val db = database ?: return
        if (itemDao.getItemById(itemId) == null) return
        db.withTransaction { db.orderDao().refreshStatuses() }
    }
}
