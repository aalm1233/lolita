package com.lolita.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.*
import com.lolita.app.data.share.*
import com.lolita.app.domain.usecase.*
import com.lolita.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OutfitBudgetFeatureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)
    private lateinit var db: LolitaDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build() }
    @After fun cleanup() { db.close() }
    private suspend fun seed(): Pair<Item, Item> {
        db.brandDao().insertBrand(Brand(id = 1, name = "品牌"))
        db.categoryDao().insertCategory(Category(id = 1, name = "JSK"))
        db.categoryDao().insertCategory(Category(id = 2, name = "衬衫"))
        val main = Item(id = 1, brandId = 1, categoryId = 1, name = "主裙", description = "", status = ItemStatus.OWNED)
        val top = main.copy(id = 2, categoryId = 2, name = "上衣")
        db.itemDao().insertItem(main); db.itemDao().insertItem(top)
        return main to top
    }
    @Test fun savingLookRejectsChangedOwnershipWithoutCreatingPartialCoordinate() = runBlocking {
        val (main, top) = seed()
        val old = db.coordinateDao().insertCoordinate(Coordinate(name = "原套装", description = ""))
        db.itemDao().updateItem(top.copy(coordinateId = old))
        val repository = LookRepository(db)
        try { repository.save("新套装", listOf(main, top)); fail("stale save accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.coordinateDao().getAllCoordinatesList().size)
        assertNull(db.itemDao().getItemById(main.id)!!.coordinateId)
        val reviewed = db.itemDao().getItemById(top.id)!!
        val id = repository.save("新套装", listOf(main, reviewed))
        assertEquals(listOf(main.id, top.id), db.coordinateDao().getCoordinateWithItemsList(id)!!.items.sortedBy { it.coordinateOrder }.map { it.id })
        assertNotNull(db.coordinateDao().getCoordinateById(old))
    }
    @Test fun budgetUsesPartialStageBalanceAndIncludesArchivedCashPayments() = runBlocking {
        val (main, _) = seed()
        val month = YearMonth.now()
        fun date(offset: Int) = month.plusMonths(offset.toLong()).atDay(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val price = db.priceDao().insertPrice(Price(itemId = main.id, type = PriceType.DEPOSIT_BALANCE, totalPrice = 200.0))
        db.orderDao().insertBinding(OrderBinding(main.id, price))
        val stage = db.orderDao().insertStage(PaymentStage(priceId = price, name = "定金", amount = 100.0, dueDate = date(0)))
        db.orderDao().insertStage(PaymentStage(priceId = price, name = "尾款", amount = 100.0, dueDate = date(1)))
        db.paymentDao().insertPayment(Payment(priceId = price, stageId = stage, amount = 60.0, dueDate = date(0), paidDate = date(0), isPaid = true))
        val archived = db.priceDao().insertPrice(Price(itemId = main.id, type = PriceType.FULL, totalPrice = 10.0))
        db.paymentDao().insertPayment(Payment(priceId = archived, amount = 10.0, dueDate = date(0), paidDate = date(0), isPaid = true))
        val repository = BudgetRepository(db.budgetDao())
        repository.save(month, "150.00")
        val (_, summary) = repository.observe(month).first()
        assertEquals(7000L, summary.paidCents)
        assertEquals(4000L, summary.pendingCents)
        assertEquals(4000L, summary.projectedRemainingCents)
        assertEquals(10000L, repository.observe(month.plusMonths(1)).first().second.pendingCents)
    }
    @Test fun migrationAddsBudgetWithoutChangingExistingRecords() {
        val name = "budget-migration-${UUID.randomUUID()}"
        try {
            migrations.createDatabase(name, 23).apply {
                execSQL("INSERT INTO brands(id,name,is_preset,created_at) VALUES(345,'保留品牌',0,123)")
                close()
            }
            migrations.runMigrationsAndValidate(name, 24, true, LolitaDatabase.MIGRATION_23_24).use {
                it.query("SELECT name,created_at FROM brands WHERE id=345").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals("保留品牌", cursor.getString(0)); assertEquals(123L, cursor.getLong(1))
                }
                it.execSQL("INSERT INTO monthly_budgets(month,amountCents) VALUES('2026-10',12345)")
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun budgetsSurviveJsonAndZipRestorePreviewAndCsv() = runBlocking {
        db.budgetDao().save(MonthlyBudget("2026-10", 12345))
        val manager = BackupManager(context, db)
        val published = mutableListOf<android.net.Uri>()
        try {
            val json = manager.exportToJson().getOrThrow().also { published += it }
            val zip = manager.exportToZip().getOrThrow().also { published += it }
            val csv = manager.exportToCsv().getOrThrow().also { published += it }
            val csvText = context.contentResolver.openInputStream(csv)!!.bufferedReader().use { it.readText() }
            assertTrue(csvText.contains("\"2026-10\",12345"))
            for (uri in listOf(json, zip)) {
                db.budgetDao().deleteAll()
                assertEquals(1, manager.previewBackup(uri).getOrThrow().monthlyBudgetCount)
                manager.importBackup(uri).getOrThrow()
                assertEquals(listOf(MonthlyBudget("2026-10", 12345)), db.budgetDao().getAll())
            }
        } finally { published.forEach { context.contentResolver.delete(it, null, null) } }
    }
    @Test fun shareIntentGrantsReadablePngUrisForSingleAndMultiplePages() = runBlocking {
        val colors = getSkinConfig(SkinType.DEFAULT).lightColorScheme
        val palette = SharePalette(colors.background.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb(), colors.primary.toArgb(), 24f, "甜美粉")
        val files = ShareCardRenderer.render(context, ShareCardContent("搭配", emptyList(), (1..7).map { ShareCardItem("服饰$it", null, null) }), palette)
        try {
            for (pages in listOf(files.take(1), files)) {
                val intent = ShareImageIntents.create(context, pages)
                assertEquals(if (pages.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE, intent.action)
                assertEquals("image/png", intent.type)
                assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(0, intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertEquals(pages.size, intent.clipData!!.itemCount)
                for (index in pages.indices) {
                    val uri = intent.clipData!!.getItemAt(index).uri
                    assertEquals("content", uri.scheme)
                    context.contentResolver.openInputStream(uri)!!.use { assertNotNull(BitmapFactory.decodeStream(it)) }
                }
            }
        } finally { files.forEach { it.delete() } }
    }
    @Test fun shareCardsRenderAllSkinsLightDarkAndPaginateWithoutLosingItems() = runBlocking {
        val folder = File(context.cacheDir, "share-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val image = File(folder, "photo.png")
        val bitmap = Bitmap.createBitmap(120, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val outputs = mutableListOf<File>()
        try {
            for (type in SkinType.entries) for (dark in listOf(false, true)) {
                val skin = getSkinConfig(type)
                val colors = if (dark) skin.darkColorScheme else skin.lightColorScheme
                val palette = SharePalette(colors.background.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb(), colors.primary.toArgb(), 24f, skin.name)
                val content = ShareCardContent("长标题：我的茶会搭配与古典洋装收藏", listOf(image.path), (1..7).map {
                    ShareCardItem("服饰$it · 长名称换行测试蕾丝花园收藏系列", "品牌", if (it == 7) "/missing/photo.png" else image.path)
                })
                val files = ShareCardRenderer.render(context, content, palette).also { outputs += it }
                assertEquals(2, files.size)
                files.forEach { file ->
                    val decoded = BitmapFactory.decodeFile(file.path)
                    assertNotNull(decoded); assertEquals(1080, decoded.width); assertTrue(decoded.height in 100..6000); decoded.recycle()
                }
                if (type == SkinType.DEFAULT && !dark || type == SkinType.VICTORIAN && dark) {
                    val archive = File(context.filesDir, "feature-previews").apply { mkdirs() }
                    files.first().copyTo(File(archive, "share-${type.name.lowercase()}-${if (dark) "dark" else "light"}.png"), overwrite = true)
                }
            }
        } finally { outputs.forEach { it.delete() }; folder.deleteRecursively() }
    }
}
