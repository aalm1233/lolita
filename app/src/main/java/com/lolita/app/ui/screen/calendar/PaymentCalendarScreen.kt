package com.lolita.app.ui.screen.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolita.app.data.local.entity.PaymentWithItemInfo
import com.lolita.app.data.local.entity.PriceType
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.PaymentRepository
import com.lolita.app.data.repository.PriceRepository
import com.lolita.app.di.AppModule

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.LolitaSkin
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.preferences.AppPreferences

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// --- ViewModel ---

data class MonthStats(
    val month: Int, // 0-based
    val paidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidTotal: Double = 0.0,
    val unpaidCount: Int = 0,
    val overdueAmount: Double = 0.0
)

data class PaymentCalendarUiState(
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int? = null, // 0-based, null = no month selected
    val yearPayments: List<PaymentWithItemInfo> = emptyList(),
    val monthStatsMap: Map<Int, MonthStats> = emptyMap(),
    val yearPaidTotal: Double = 0.0,
    val yearPaidCount: Int = 0,
    val yearUnpaidTotal: Double = 0.0,
    val yearUnpaidCount: Int = 0,
    val yearOverdueAmount: Double = 0.0,
    val isLoading: Boolean = true
)

class PaymentCalendarViewModel(
    private val priceRepository: PriceRepository = AppModule.priceRepository(),
    private val paymentRepository: PaymentRepository = AppModule.paymentRepository(),
    private val itemRepository: ItemRepository = AppModule.itemRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentCalendarUiState())
    val uiState: StateFlow<PaymentCalendarUiState> = _uiState.asStateFlow()

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadDataJob?.cancel()
        val state = _uiState.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, state.currentYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yearStart = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        val yearEnd = cal.timeInMillis - 1
        val now = System.currentTimeMillis()

        loadDataJob = viewModelScope.launch {
            priceRepository.getPaymentsWithItemInfoByDateRange(yearStart, yearEnd)
                .collect { payments ->
                    val monthStatsMap = buildMonthStatsMap(payments, state.currentYear, now)
                    val paid = payments.filter { it.isPaid }
                    val unpaid = payments.filter { !it.isPaid }
                    _uiState.value = _uiState.value.copy(
                        yearPayments = payments,
                        monthStatsMap = monthStatsMap,
                        yearPaidTotal = paid.sumOf { it.amount },
                        yearPaidCount = paid.size,
                        yearUnpaidTotal = unpaid.sumOf { it.amount },
                        yearUnpaidCount = unpaid.size,
                        yearOverdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount },
                        isLoading = false
                    )
                }
        }
    }

    fun previousYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear - 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun nextYear() {
        _uiState.value = _uiState.value.copy(
            currentYear = _uiState.value.currentYear + 1,
            selectedMonth = null,
            isLoading = true
        )
        loadData()
    }

    fun selectMonth(month: Int) {
        val current = _uiState.value.selectedMonth
        _uiState.value = _uiState.value.copy(
            selectedMonth = if (current == month) null else month
        )
    }

    fun markAsPaid(payment: PaymentWithItemInfo) {
        viewModelScope.launch {
            val price = priceRepository.getPriceById(payment.priceId)
            val item = price?.let { itemRepository.getItemById(it.itemId) }
            val itemName = item?.name ?: "服饰"

            val fullPayment = paymentRepository.getPaymentById(payment.paymentId)
            if (fullPayment != null && !fullPayment.isPaid) {
                paymentRepository.updatePayment(
                    fullPayment.copy(isPaid = true, paidDate = System.currentTimeMillis()),
                    itemName
                )
            }
        }
    }

    private fun buildMonthStatsMap(
        payments: List<PaymentWithItemInfo>,
        year: Int,
        now: Long
    ): Map<Int, MonthStats> {
        val cal = Calendar.getInstance()
        val monthPayments = mutableMapOf<Int, MutableList<PaymentWithItemInfo>>()
        payments.forEach { p ->
            cal.timeInMillis = p.dueDate
            if (cal.get(Calendar.YEAR) == year) {
                val month = cal.get(Calendar.MONTH)
                monthPayments.getOrPut(month) { mutableListOf() }.add(p)
            }
        }
        return monthPayments.mapValues { (month, list) ->
            val paid = list.filter { it.isPaid }
            val unpaid = list.filter { !it.isPaid }
            MonthStats(
                month = month,
                paidTotal = paid.sumOf { it.amount },
                paidCount = paid.size,
                unpaidTotal = unpaid.sumOf { it.amount },
                unpaidCount = unpaid.size,
                overdueAmount = unpaid.filter { it.dueDate < now }.sumOf { it.amount }
            )
        }
    }
}

// --- UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCalendarContent(
    viewModel: PaymentCalendarViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    val preferences = remember { AppPreferences(context) }
    val backgroundPath by preferences.paymentCalendarBackgroundPath.collectAsState(initial = null)
    val hasBackground = backgroundPath != null

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showFullScreen by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val path = ImageFileHelper.copyToInternalStorage(context, it)
                backgroundPath?.let { old -> ImageFileHelper.deleteImage(old) }
                preferences.setPaymentCalendarBackgroundPath(path)
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { uri ->
                scope.launch {
                    val path = ImageFileHelper.copyToInternalStorage(context, uri)
                    backgroundPath?.let { old -> ImageFileHelper.deleteImage(old) }
                    preferences.setPaymentCalendarBackgroundPath(path)
                }
            }
        }
    }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            )
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    // Haze modifier applied to all cards when background is set
    val hazeModifier = if (hasBackground) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = Color.White.copy(alpha = 0.3f),
                tint = HazeTint(Color.White.copy(alpha = 0.3f)),
                blurRadius = 8.dp
            )
        )
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(hasBackground) {
                if (hasBackground) {
                    detectTapGestures(onLongPress = { showBottomSheet = true })
                }
            }
    ) {
        // Layer 1: Background image
        if (hasBackground && backgroundPath != null) {
            LolitaShimmerImage(
                model = backgroundPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                circularRevealEnabled = false
            )
        }

        // Layer 2: Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                YearHeader(
                    year = uiState.currentYear,
                    yearPaidTotal = uiState.yearPaidTotal,
                    yearPaidCount = uiState.yearPaidCount,
                    yearUnpaidTotal = uiState.yearUnpaidTotal,
                    yearUnpaidCount = uiState.yearUnpaidCount,
                    yearOverdueAmount = uiState.yearOverdueAmount,
                    onPrevious = viewModel::previousYear,
                    onNext = viewModel::nextYear,
                    modifier = hazeModifier
                )
            }
            item {
                MonthCardGrid(
                    monthStatsMap = uiState.monthStatsMap,
                    selectedMonth = uiState.selectedMonth,
                    currentYear = uiState.currentYear,
                    onMonthClick = viewModel::selectMonth,
                    cardModifier = hazeModifier
                )
            }

            val selectedPayments = uiState.selectedMonth?.let { month ->
                getPaymentsForMonth(uiState.yearPayments, uiState.currentYear, month)
            } ?: emptyList()

            if (uiState.selectedMonth != null) {
                item {
                    Text(
                        "${uiState.selectedMonth!! + 1}月 付款记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (selectedPayments.isEmpty()) {
                    item {
                        LolitaCard(
                            modifier = (if (hasBackground) hazeModifier else Modifier).fillMaxWidth()
                        ) {
                            Text(
                                "当月无付款记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(selectedPayments, key = { it.paymentId }) { payment ->
                        PaymentInfoCard(
                            payment = payment,
                            onMarkPaid = if (!payment.isPaid) {{ viewModel.markAsPaid(payment) }} else null,
                            modifier = if (hasBackground) hazeModifier else Modifier
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                BottomSheetOption(
                    text = "从相册选择",
                    icon = IconKey.Gallery,
                    onClick = {
                        showBottomSheet = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                BottomSheetOption(
                    text = "拍照",
                    icon = IconKey.Camera,
                    onClick = {
                        showBottomSheet = false
                        val cameraPermission = Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(
                                context, cameraPermission
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                            )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(cameraPermission)
                        }
                    }
                )
                if (hasBackground && backgroundPath != null) {
                    BottomSheetOption(
                        text = "查看大图",
                        icon = IconKey.OpenInNew,
                        onClick = {
                            showBottomSheet = false
                            showFullScreen = true
                        }
                    )
                    BottomSheetOption(
                        text = "恢复默认",
                        icon = IconKey.Delete,
                        onClick = {
                            showBottomSheet = false
                            scope.launch {
                                backgroundPath?.let { ImageFileHelper.deleteImage(it) }
                                preferences.setPaymentCalendarBackgroundPath(null)
                            }
                        }
                    )
                }
            }
        }
    }

    // Full screen viewer
    if (showFullScreen && backgroundPath != null) {
        FullScreenImageViewer(
            imageUrls = listOfNotNull(backgroundPath),
            onDismiss = { showFullScreen = false }
        )
    }
}

@Composable
private fun YearHeader(
    year: Int,
    yearPaidTotal: Double,
    yearPaidCount: Int,
    yearUnpaidTotal: Double,
    yearUnpaidCount: Int,
    yearOverdueAmount: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val skin = LolitaSkin.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = skin.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation),
        border = if (isDark) skin.cardBorderStrokeDark else skin.cardBorderStroke
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Year selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    SkinIcon(IconKey.KeyboardArrowLeft, modifier = Modifier.size(20.dp))
                }
                Text(
                    "${year}年",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    SkinIcon(IconKey.KeyboardArrowRight, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBlock(
                    label = "已付",
                    amount = yearPaidTotal,
                    count = yearPaidCount,
                    color = paidColor()
                )
                StatBlock(
                    label = "待付",
                    amount = yearUnpaidTotal,
                    count = yearUnpaidCount,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (yearOverdueAmount > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = overdueColor().copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠",
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "逾期  ¥${formatAmount(yearOverdueAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = overdueColor(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, amount: Double, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "¥${formatAmount(amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            "$label  ${count}笔",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatAmount(amount: Double): String {
    return "%,.0f".format(amount)
}

@Composable
private fun MonthCardGrid(
    monthStatsMap: Map<Int, MonthStats>,
    selectedMonth: Int?,
    currentYear: Int,
    onMonthClick: (Int) -> Unit,
    cardModifier: Modifier = Modifier
) {
    val todayCal = Calendar.getInstance()
    val isCurrentYear = todayCal.get(Calendar.YEAR) == currentYear
    val currentMonth = if (isCurrentYear) todayCal.get(Calendar.MONTH) else -1

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { col ->
                    val month = row * 4 + col
                    MonthCard(
                        month = month,
                        stats = monthStatsMap[month],
                        isCurrentMonth = month == currentMonth,
                        isSelected = month == selectedMonth,
                        modifier = Modifier.weight(1f).then(cardModifier),
                        onClick = { onMonthClick(month) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCard(
    month: Int,
    stats: MonthStats?,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val skin = LolitaSkin.current
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasPayments = stats != null
    val hasOverdue = (stats?.overdueAmount ?: 0.0) > 0
    val containerColor = if (isDark) skin.cardContainerColorDark else skin.cardContainerColor

    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(alpha = 0.15f)
        else containerColor,
        animationSpec = tween(200), label = "monthBg"
    )

    Card(
        modifier = modifier
            .heightIn(min = 80.dp)
            .then(
                if (isCurrentMonth) Modifier.border(
                    2.dp, primaryColor, skin.cardShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        shape = skin.cardShape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = skin.cardElevation),
        border = if (isDark) skin.cardBorderStrokeDark else skin.cardBorderStroke
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "${month + 1}月",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasPayments) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
            if (stats != null) {
                if (stats.paidTotal > 0) {
                    Text(
                        "已 ¥${formatCompactAmount(stats.paidTotal)}",
                        fontSize = 10.sp,
                        color = paidColor(),
                        maxLines = 1
                    )
                }
                if (stats.unpaidTotal > 0) {
                    val unpaidColor = if (hasOverdue) overdueColor() else primaryColor
                    Text(
                        "待 ¥${formatCompactAmount(stats.unpaidTotal)}",
                        fontSize = 10.sp,
                        color = unpaidColor,
                        maxLines = 1
                    )
                }
                if (hasOverdue && stats.overdueAmount > 0) {
                    Text(
                        "逾期",
                        fontSize = 9.sp,
                        color = overdueColor(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetOption(
    text: String,
    icon: IconKey,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkinIcon(icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Formats amounts over 9999 as "1.2万" for compact display in month cards.
 */
private fun formatCompactAmount(amount: Double): String {
    return if (amount >= 10000) {
        val wan = amount / 10000.0
        "%.1f万".format(wan)
    } else {
        "%.0f".format(amount)
    }
}

@Composable
private fun PaymentInfoCard(
    payment: PaymentWithItemInfo,
    onMarkPaid: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val typeLabel = when (payment.priceType) {
        PriceType.DEPOSIT_BALANCE -> "定金尾款"
        PriceType.FULL -> "全款"
    }
    val now = System.currentTimeMillis()
    val isOverdue = !payment.isPaid && payment.dueDate < now
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog && onMarkPaid != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认付款") },
            text = {
                Text("确认将 ${payment.itemName} 的 ¥${String.format("%.2f", payment.amount)} 标记为已付款？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onMarkPaid()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    LolitaCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    payment.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (payment.isPaid) paidColor().copy(alpha = 0.1f)
                    else if (isOverdue) overdueColor().copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (payment.isPaid) "已付清" else if (isOverdue) "已逾期" else "待付款",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.isPaid) paidColor()
                        else if (isOverdue) overdueColor()
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$typeLabel ¥${String.format("%.2f", payment.amount)}  应付: ${sdf.format(Date(payment.dueDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onMarkPaid != null) {
                    TextButton(
                        onClick = { showConfirmDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("标记已付", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun getPaymentsForMonth(
    payments: List<PaymentWithItemInfo>,
    year: Int,
    month: Int
): List<PaymentWithItemInfo> {
    val cal = Calendar.getInstance()
    return payments.filter { p ->
        cal.timeInMillis = p.dueDate
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
    }
}

@Composable
private fun paidColor(): Color = Color(0xFF4CAF50)

@Composable
private fun overdueColor(): Color = MaterialTheme.colorScheme.error
