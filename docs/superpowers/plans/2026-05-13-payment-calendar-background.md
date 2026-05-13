# Payment Calendar Custom Background — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to set a custom background image on the payment calendar tab, with light frosted-glass effect on all foreground cards.

**Architecture:** Store the image path in AppPreferences (DataStore). Render the image as a background layer with `hazeSource`, and apply `hazeEffect` to existing cards. Long-press on empty area triggers a bottom sheet with gallery/camera/view/reset options.

**Tech Stack:** Kotlin, Jetpack Compose, Haze 1.6.9, Coil (via LolitaShimmerImage), DataStore Preferences, ImageFileHelper

---

### Task 1: Add background path to AppPreferences

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Add key and Flow**

In `AppPreferences`, add after the last preference property (after `sharedLibraryBaseUrl`):

```kotlin
val paymentCalendarBackgroundPath: Flow<String?> = context.dataStore.data
    .map { it[PAYMENT_CALENDAR_BACKGROUND_PATH] }

suspend fun setPaymentCalendarBackgroundPath(path: String?) {
    context.dataStore.edit { it[PAYMENT_CALENDAR_BACKGROUND_PATH] = path }
}
```

- [ ] **Step 2: Add key to companion object**

In the `companion object` block, add after `SHARED_LIBRARY_BASE_URL`:

```kotlin
private val PAYMENT_CALENDAR_BACKGROUND_PATH = stringPreferencesKey("payment_calendar_background_path")
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/preferences/AppPreferences.kt
git commit -m "feat: add paymentCalendarBackgroundPath to AppPreferences"
```

---

### Task 2: Add background layer, Haze, long-press, and bottom sheet to PaymentCalendarContent

**Files:**
- Modify: `app/src/main/java/com/lolita/app/ui/screen/calendar/PaymentCalendarScreen.kt`

- [ ] **Step 1: Add new imports**

Add these new imports (keep all existing imports):

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.preferences.AppPreferences
import com.lolita.app.ui.component.FullScreenImageViewer
import com.lolita.app.ui.screen.common.LolitaShimmerImage
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
```

- [ ] **Step 2: Rewrite PaymentCalendarContent**

Replace the `PaymentCalendarContent` composable completely:

```kotlin
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

    // Haze style applied to all cards
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
                imageUrl = backgroundPath,
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
            imageUrls = listOf(backgroundPath),
            onDismiss = { showFullScreen = false }
        )
    }
}
```

- [ ] **Step 3: Add BottomSheetOption composable**

Add this helper at the file level (near the bottom, before `formatCompactAmount`):

```kotlin
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
```

- [ ] **Step 4: Add Modifier parameter to YearHeader**

Change `YearHeader` signature to accept a modifier. Find the existing signature (around line 265) and add `modifier` parameter:

```kotlin
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
)
```

Then on the outer `Card`, change `modifier = Modifier.fillMaxWidth()` to:

```kotlin
Card(
    modifier = modifier.fillMaxWidth(),
```

- [ ] **Step 5: Add cardModifier parameter to MonthCardGrid**

Change `MonthCardGrid` signature to accept a per-card modifier and pass it to each `MonthCard`:

```kotlin
@Composable
private fun MonthCardGrid(
    monthStatsMap: Map<Int, MonthStats>,
    selectedMonth: Int?,
    currentYear: Int,
    onMonthClick: (Int) -> Unit,
    cardModifier: Modifier = Modifier
)
```

Then in the `MonthCard` call site (inside `repeat`), change:

```kotlin
MonthCard(
    month = month,
    stats = monthStatsMap[month],
    isCurrentMonth = month == currentMonth,
    isSelected = month == selectedMonth,
    modifier = Modifier.weight(1f).then(cardModifier),
    onClick = { onMonthClick(month) }
)
```

- [ ] **Step 6: Add Modifier parameter to PaymentInfoCard**

Change `PaymentInfoCard` signature to accept a `modifier` parameter:

```kotlin
@Composable
private fun PaymentInfoCard(
    payment: PaymentWithItemInfo,
    onMarkPaid: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

Then change `LolitaCard(modifier = Modifier.fillMaxWidth())` to:

```kotlin
LolitaCard(
    modifier = modifier.fillMaxWidth()
)
```

---

### Task 3: Bump version

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Update version**

```kotlin
versionCode = 62
versionName = "2.36.0"
```

- [ ] **Step 2: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 2.36.0"
```

---

### Task 4: Build and verify

- [ ] **Step 1: Clean release build**

```bash
./gradlew.bat clean assembleRelease
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify APK exists**

```bash
if (Test-Path "app/build/outputs/apk/release/app-release.apk") { Write-Output "APK generated successfully" } else { Write-Error "APK not found" }
```
