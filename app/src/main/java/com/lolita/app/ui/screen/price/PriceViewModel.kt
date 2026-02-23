package com.lolita.app.ui.screen.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolita.app.data.local.dao.PriceWithPayments as DaoPriceWithPayments
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PaymentRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.data.local.LolitaDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

data class PriceManageUiState(
    val prices: List<DaoPriceWithPayments> = emptyList(),
    val isLoading: Boolean = true
)

data class PriceEditUiState(
    val priceType: PriceType = PriceType.FULL,
    val totalPrice: String = "",
    val deposit: String = "",
    val balance: String = "",
    val paymentDate: Long? = null,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class PaymentManageUiState(
    val payments: List<Payment> = emptyList(),
    val totalPrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val unpaidAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class PaymentEditUiState(
    val amount: String = "",
    val dueDate: Long? = null,
    val reminderSet: Boolean = true,
    val customReminderDays: String = "1",
    val isSaving: Boolean = false,
    val error: String? = null
)

class PriceManageViewModel(
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val itemId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceManageUiState())
    val uiState: StateFlow<PriceManageUiState> = _uiState.asStateFlow()

    init {
        loadPrices()
    }

    private fun loadPrices() {
        viewModelScope.launch {
            priceRepository.getPricesWithPaymentsByItem(itemId).collect { prices ->
                _uiState.value = _uiState.value.copy(
                    prices = prices,
                    isLoading = false
                )
            }
        }
    }

    fun deletePrice(price: Price) {
        viewModelScope.launch {
            try {
                priceRepository.deletePrice(price)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    prices = _uiState.value.prices,
                    isLoading = false
                )
            }
        }
    }
}

class PriceEditViewModel(
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val paymentRepository: PaymentRepository = com.lolita.app.di.AppModule.paymentRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val database: LolitaDatabase = com.lolita.app.di.AppModule.database()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceEditUiState())
    val uiState: StateFlow<PriceEditUiState> = _uiState.asStateFlow()
    var hasUnsavedChanges: Boolean = false
        private set

    fun loadPrice(priceId: Long?) {
        if (priceId == null) return

        viewModelScope.launch {
            val price = priceRepository.getPriceById(priceId)
            price?.let { p ->
                // Load paymentDate from first payment's paidDate
                val payments = paymentRepository.getPaymentsByPriceList(p.id)
                val firstPaidDate = payments.minByOrNull { it.createdAt }?.paidDate
                _uiState.update {
                    it.copy(
                        priceType = p.type,
                        totalPrice = p.totalPrice.toString(),
                        deposit = p.deposit?.toString() ?: "",
                        balance = p.balance?.toString() ?: "",
                        paymentDate = firstPaidDate
                    )
                }
            }
        }
    }

    fun updatePriceType(type: PriceType) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(priceType = type)
    }

    fun updateTotalPrice(value: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(totalPrice = value)
    }

    fun updateDeposit(value: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(deposit = value)
    }

    fun updateBalance(value: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(balance = value)
    }

    fun updatePaymentDate(date: Long?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(paymentDate = date)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    suspend fun save(itemId: Long): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val totalPrice = _uiState.value.totalPrice.toDoubleOrNull()
                ?: throw IllegalArgumentException("总价无效")

            val price = Price(
                itemId = itemId,
                type = _uiState.value.priceType,
                totalPrice = totalPrice,
                deposit = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
                    _uiState.value.deposit.toDoubleOrNull()
                } else null,
                balance = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
                    _uiState.value.balance.toDoubleOrNull()
                } else null
            )

            val priceId = priceRepository.insertPrice(price)

            // Auto-create payment records
            val item = itemRepository.getItemById(itemId)
            val itemName = item?.name ?: "服饰"
            val now = System.currentTimeMillis()
            val paymentDate = _uiState.value.paymentDate
            val isAlreadyPaid = paymentDate != null

            when (_uiState.value.priceType) {
                PriceType.FULL -> {
                    paymentRepository.insertPayment(
                        Payment(
                            priceId = priceId,
                            amount = totalPrice,
                            dueDate = paymentDate ?: now,
                            isPaid = isAlreadyPaid,
                            paidDate = paymentDate,
                            reminderSet = !isAlreadyPaid,
                            customReminderDays = if (!isAlreadyPaid) 1 else null
                        ),
                        itemName
                    )
                }
                PriceType.DEPOSIT_BALANCE -> {
                    val depositAmount = _uiState.value.deposit.toDoubleOrNull() ?: 0.0
                    val balanceAmount = _uiState.value.balance.toDoubleOrNull() ?: 0.0
                    if (depositAmount > 0) {
                        paymentRepository.insertPayment(
                            Payment(
                                priceId = priceId,
                                amount = depositAmount,
                                dueDate = paymentDate ?: now,
                                isPaid = isAlreadyPaid,
                                paidDate = paymentDate,
                                reminderSet = !isAlreadyPaid,
                                customReminderDays = if (!isAlreadyPaid) 1 else null
                            ),
                            itemName
                        )
                    }
                    if (balanceAmount > 0) {
                        paymentRepository.insertPayment(
                            Payment(
                                priceId = priceId,
                                amount = balanceAmount,
                                dueDate = now,
                                isPaid = false,
                                reminderSet = true,
                                customReminderDays = 1
                            ),
                            itemName
                        )
                    }
                }
            }

            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(priceId)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "保存失败")
            Result.failure(e)
        }
    }

    suspend fun update(priceId: Long): Result<Unit> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val existing = priceRepository.getPriceById(priceId)
                ?: throw IllegalStateException("价格记录不存在")
            val totalPrice = _uiState.value.totalPrice.toDoubleOrNull()
                ?: throw IllegalArgumentException("总价无效")

            val price = existing.copy(
                type = _uiState.value.priceType,
                totalPrice = totalPrice,
                deposit = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
                    _uiState.value.deposit.toDoubleOrNull()
                } else null,
                balance = if (_uiState.value.priceType == PriceType.DEPOSIT_BALANCE) {
                    _uiState.value.balance.toDoubleOrNull()
                } else null
            )

            priceRepository.updatePrice(price)

            // Sync payments if type or amounts changed — wrapped in transaction
            val typeChanged = existing.type != price.type
            val amountChanged = existing.totalPrice != price.totalPrice ||
                existing.deposit != price.deposit || existing.balance != price.balance
            if (typeChanged || amountChanged) {
                val item = itemRepository.getItemById(existing.itemId)
                val itemName = item?.name ?: "服饰"
                val paymentDate = _uiState.value.paymentDate
                val isAlreadyPaid = paymentDate != null
                database.withTransaction {
                    val oldPayments = paymentRepository.getPaymentsByPriceList(priceId)
                    // Delete old unpaid payments and recreate
                    oldPayments.filter { !it.isPaid }.forEach { paymentRepository.deletePayment(it) }
                    val now = System.currentTimeMillis()
                    when (_uiState.value.priceType) {
                        PriceType.FULL -> {
                            paymentRepository.insertPayment(
                                Payment(priceId = priceId, amount = totalPrice,
                                    dueDate = paymentDate ?: now,
                                    isPaid = isAlreadyPaid, paidDate = paymentDate,
                                    reminderSet = !isAlreadyPaid,
                                    customReminderDays = if (!isAlreadyPaid) 1 else null),
                                itemName
                            )
                        }
                        PriceType.DEPOSIT_BALANCE -> {
                            val depositAmount = _uiState.value.deposit.toDoubleOrNull() ?: 0.0
                            val balanceAmount = _uiState.value.balance.toDoubleOrNull() ?: 0.0
                            if (depositAmount > 0) {
                                paymentRepository.insertPayment(
                                    Payment(priceId = priceId, amount = depositAmount,
                                        dueDate = paymentDate ?: now,
                                        isPaid = isAlreadyPaid, paidDate = paymentDate,
                                        reminderSet = !isAlreadyPaid,
                                        customReminderDays = if (!isAlreadyPaid) 1 else null),
                                    itemName
                                )
                            }
                            if (balanceAmount > 0) {
                                paymentRepository.insertPayment(
                                    Payment(priceId = priceId, amount = balanceAmount,
                                        dueDate = now,
                                        isPaid = false, reminderSet = true,
                                        customReminderDays = 1),
                                    itemName
                                )
                            }
                        }
                    }
                }
            }

            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "保存失败")
            Result.failure(e)
        }
    }

    fun isValid(): Boolean {
        return when (_uiState.value.priceType) {
            PriceType.FULL -> {
                val total = _uiState.value.totalPrice.toDoubleOrNull()
                total != null && total > 0
            }
            PriceType.DEPOSIT_BALANCE -> {
                val total = _uiState.value.totalPrice.toDoubleOrNull()
                val deposit = _uiState.value.deposit.toDoubleOrNull()
                val balance = _uiState.value.balance.toDoubleOrNull()
                total != null && total > 0 && deposit != null && balance != null &&
                    kotlin.math.abs((deposit + balance) - total) < 0.01
            }
        }
    }
}

class PaymentManageViewModel(
    private val paymentRepository: PaymentRepository = com.lolita.app.di.AppModule.paymentRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository(),
    private val priceId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentManageUiState())
    val uiState: StateFlow<PaymentManageUiState> = _uiState.asStateFlow()

    init {
        loadPayments()
    }

    private fun loadPayments() {
        viewModelScope.launch {
            priceRepository.getPriceWithPayments(priceId).collect { result ->
                _uiState.value = result?.let { data ->
                    val totalPrice = data.price.totalPrice
                    val paidAmount = data.payments.filter { it.isPaid }.sumOf { it.amount }
                    val unpaidAmount = data.payments.filter { !it.isPaid }.sumOf { it.amount }

                    PaymentManageUiState(
                        payments = data.payments,
                        totalPrice = totalPrice,
                        paidAmount = paidAmount,
                        unpaidAmount = unpaidAmount,
                        isLoading = false
                    )
                } ?: PaymentManageUiState(isLoading = false)
            }
        }
    }

    fun markAsPaid(payment: Payment) {
        viewModelScope.launch {
            // Get item name from price for notification
            val price = priceRepository.getPriceById(priceId)
            val item = price?.let { itemRepository.getItemById(it.itemId) }
            val itemName = item?.name ?: "服饰"

            paymentRepository.updatePayment(
                payment.copy(
                    isPaid = true,
                    paidDate = System.currentTimeMillis()
                ),
                itemName
            )
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            try {
                paymentRepository.deletePayment(payment)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "删除失败")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

class PaymentEditViewModel(
    private val paymentRepository: PaymentRepository = com.lolita.app.di.AppModule.paymentRepository(),
    private val priceRepository: PriceRepository = com.lolita.app.di.AppModule.priceRepository(),
    private val itemRepository: ItemRepository = com.lolita.app.di.AppModule.itemRepository()
) : ViewModel() {

    private var currentPriceId: Long = 0

    private val _uiState = MutableStateFlow(PaymentEditUiState())
    val uiState: StateFlow<PaymentEditUiState> = _uiState.asStateFlow()
    var hasUnsavedChanges: Boolean = false
        private set

    fun loadPayment(paymentId: Long?) {
        if (paymentId == null) {
            // New payment - set today's date as default due date
            _uiState.value = _uiState.value.copy(dueDate = System.currentTimeMillis())
            return
        }

        viewModelScope.launch {
            val payment = paymentRepository.getPaymentById(paymentId)
            payment?.let {
                _uiState.value = PaymentEditUiState(
                    amount = it.amount.toString(),
                    dueDate = it.dueDate,
                    reminderSet = it.reminderSet,
                    customReminderDays = it.customReminderDays?.toString() ?: "1"
                )
            }
        }
    }

    fun updateAmount(value: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(amount = value)
    }

    fun updateDueDate(date: Long?) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(dueDate = date)
    }

    fun updateReminderSet(enabled: Boolean) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(reminderSet = enabled)
    }

    fun updateCustomReminderDays(value: String) {
        hasUnsavedChanges = true
        _uiState.value = _uiState.value.copy(customReminderDays = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    suspend fun save(priceId: Long): Result<Long> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        currentPriceId = priceId
        return try {
            val amount = _uiState.value.amount.toDoubleOrNull() ?: throw IllegalArgumentException("金额无效")

            val payment = Payment(
                priceId = priceId,
                amount = amount,
                dueDate = _uiState.value.dueDate ?: System.currentTimeMillis(),
                isPaid = false,
                reminderSet = _uiState.value.reminderSet,
                customReminderDays = _uiState.value.customReminderDays.toIntOrNull()
            )

            // Get item name for notification
            val price = priceRepository.getPriceById(priceId)
            val item = price?.let { itemRepository.getItemById(it.itemId) }
            val itemName = item?.name ?: "服饰"

            val id = paymentRepository.insertPayment(payment, itemName)
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(id)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "保存失败")
            Result.failure(e)
        }
    }

    suspend fun update(paymentId: Long): Result<Unit> {
        _uiState.value = _uiState.value.copy(isSaving = true)
        return try {
            val amount = _uiState.value.amount.toDoubleOrNull() ?: throw IllegalArgumentException("金额无效")

            val existing = paymentRepository.getPaymentById(paymentId)
                ?: throw IllegalStateException("付款记录不存在")

            val payment = existing.copy(
                amount = amount,
                dueDate = _uiState.value.dueDate ?: System.currentTimeMillis(),
                reminderSet = _uiState.value.reminderSet,
                customReminderDays = _uiState.value.customReminderDays.toIntOrNull()
            )

            // Get item name for notification
            val price = priceRepository.getPriceById(existing.priceId)
            val item = price?.let { itemRepository.getItemById(it.itemId) }
            val itemName = item?.name ?: "服饰"

            paymentRepository.updatePayment(payment, itemName)
            _uiState.value = _uiState.value.copy(isSaving = false)
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "保存失败")
            Result.failure(e)
        }
    }

    fun isValid(): Boolean {
        return _uiState.value.amount.toDoubleOrNull() != null &&
               _uiState.value.dueDate != null
    }
}
