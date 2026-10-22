package com.lolita.app.ui.screen.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Tests the presentation's real published URI; no restore or production database access. */
class BackupExportResultTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var db: LolitaDatabase
    private lateinit var manager: BackupManager
    private val store = ViewModelStore()
    private val exports = mutableListOf<Uri>()
    @Before fun prepare() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val brand = db.brandDao().insertBrand(Brand(name = "导出独立品牌"))
        val category = db.categoryDao().insertCategory(Category(name = "OP"))
        db.itemDao().insertItem(Item(name = "导出验证物品", brandId = brand, categoryId = category, description = "保留原资料", status = ItemStatus.WISHED))
        manager = BackupManager(context, db)
    }
    @After fun close() = runBlocking {
        instrumentation.runOnMainSync { store.clear() }
        exports.forEach { context.contentResolver.delete(it, null, null) }
        db.close()
    }
    private fun fileName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    @Test fun actualZipJsonCsvUrisRemainReadableAndPreviewDoesNotReplaceRecords() = runBlocking {
        lateinit var model: BackupRestoreViewModel
        instrumentation.runOnMainSync { model = BackupRestoreViewModel(manager); store.put("export", model) }
        val before = db.itemDao().getAllItemsList()
        for (format in BackupExportFormat.entries) {
            instrumentation.runOnMainSync {
                when (format) { BackupExportFormat.ZIP -> model.exportZip(); BackupExportFormat.JSON -> model.exportJson(); BackupExportFormat.CSV -> model.exportCsv() }
            }
            val state = withTimeout(15_000) { model.uiState.first { !it.isExporting && it.lastExport?.format == format } }
            val result = requireNotNull(state.lastExport); exports += result.uri
            assertEquals(fileName(result.uri), result.fileName)
            assertTrue(result.fileName.endsWith(".${format.name.lowercase()}"))
            assertTrue(context.contentResolver.openInputStream(result.uri)!!.use { it.readBytes().isNotEmpty() })
            if (format == BackupExportFormat.ZIP) {
                instrumentation.runOnMainSync { model.onFileSelected(result.uri) }
                val preview = withTimeout(15_000) { model.uiState.first { it.showConfirmDialog } }
                assertEquals(1, preview.preview!!.itemCount)
                instrumentation.runOnMainSync { model.dismissDialog() }
                assertEquals(before, db.itemDao().getAllItemsList())
            }
        }
    }
    @Test fun exportBusyGuardPreventsSecondExportAndRestoreDuringResultResolution() = runBlocking {
        val reached = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        lateinit var model: BackupRestoreViewModel
        instrumentation.runOnMainSync {
            model = BackupRestoreViewModel(manager) { uri ->
                exports += uri; reached.complete(Unit); release.await(); fileName(uri)
            }
            store.put("export", model); model.exportZip()
        }
        try {
            withTimeout(15_000) { reached.await() }
            instrumentation.runOnMainSync { model.exportCsv(); model.onFileSelected(exports.single()) }
            assertTrue(model.uiState.value.isExporting)
            assertFalse(model.uiState.value.isPreviewing)
            assertNull(model.uiState.value.lastExport)
        } finally { release.complete(Unit) }
        val state = withTimeout(15_000) { model.uiState.first { !it.isExporting } }
        assertEquals(BackupExportFormat.ZIP, state.lastExport!!.format)
        assertEquals(1, exports.size)
        instrumentation.runOnMainSync { model.openExportFailed() }
        assertEquals(state.lastExport, model.uiState.value.lastExport)
        assertTrue(model.uiState.value.message!!.contains("文件已导出"))
    }
}
