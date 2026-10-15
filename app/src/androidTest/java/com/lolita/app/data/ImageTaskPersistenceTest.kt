package com.lolita.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.data.repository.ImageTaskRepository
import com.lolita.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageTaskPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LolitaDatabase::class.java)

    private fun task(id: String, status: ImageTaskStatus = ImageTaskStatus.QUEUED, createdAt: Long = 10) = ImageTask(
        id = id, kind = ImageTaskKind.MANNEQUIN, provider = ImageGenerationProvider.OPENAI, title = "原裙子人台图",
        requestJson = ImageTaskRequest(listOf(ImageTaskGarment(5, "原裙子", TryOnRole.MAIN)), "保留花纹").encode(),
        sourceImagePaths = listOf("/images/original.jpg"), status = status, createdAt = createdAt, updatedAt = 10
    )

    // The additive migration must preserve all existing values, not merely row counts.
    @Test fun migrationAddsEmptyTaskTableAndPreservesExistingSentinels() {
        val name = "image-task-migration-${UUID.randomUUID()}"
        val before: Map<String, List<List<String?>>>
        try {
            migration.createDatabase(name, 27).use { old ->
                old.execSQL("INSERT INTO brands(id,name,logo_url,created_at,is_preset) VALUES(7,'原品牌','/original/logo.png',123,0)")
                old.execSQL("INSERT INTO categories(id,name,created_at,is_preset,category_group) VALUES(8,'JSK',124,0,'CLOTHING')")
                old.execSQL("INSERT INTO items(id,brand_id,category_id,name,description,status,created_at,updated_at,image_urls) VALUES(5,7,8,'原裙子','原备注','OWNED',125,126,'[\"/original/garment.png\"]')")
                old.execSQL("INSERT INTO try_on_results(id,name,image_url,item_ids,item_names,model,created_at) VALUES(9,'原试衣','/original/result.png','[\"5\"]','[\"原裙子\"]','original-model',127)")
                before = snapshot(old)
            }
            migration.runMigrationsAndValidate(name, 28, true, LolitaDatabase.MIGRATION_27_28).use { db ->
                assertEquals(before, snapshot(db).filterKeys { it != "image_tasks" })
                db.query("SELECT COUNT(*) FROM image_tasks").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun concurrentClaimHasOneWinnerAndNeverReclaimsStartedOrTerminalTasks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val repo = ImageTaskRepository(db.imageTaskDao())
            repo.insert(task("single"))
            val winners = coroutineScope { (1..16).map { async(Dispatchers.IO) { repo.claimQueued("single", 20) } }.awaitAll() }
            assertEquals(1, winners.count { it })
            assertEquals(ImageTaskStatus.PREPARING, repo.get("single")!!.status)
            assertFalse(repo.claimQueued("single", 30))
            repo.insert(task("done", ImageTaskStatus.COMPLETED))
            assertFalse(repo.claimQueued("done", 30))
        } finally { db.close() }
    }

    @Test fun nextClaimUsesOldestQueuedRecordAndObservationIsNewestFirst() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val repo = ImageTaskRepository(db.imageTaskDao())
            repo.insert(task("new", createdAt = 30)); repo.insert(task("old", createdAt = 10))
            repo.insert(task("middle", createdAt = 20))
            assertEquals(listOf("new", "middle", "old"), repo.tasks.first().map { it.id })
            assertEquals("old", repo.claimNextQueued(40)?.id)
            assertEquals("middle", repo.claimNextQueued(41)?.id)
            assertEquals("new", repo.claimNextQueued(42)?.id)
            assertNull(repo.claimNextQueued(43))
        } finally { db.close() }
    }

    @Test fun restartRetainsChargeEvidenceButReleasesSourcesAndPreservesPendingOutput() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val repo = ImageTaskRepository(db.imageTaskDao())
            ImageTaskStatus.entries.forEach { status ->
                repo.insert(task(status.name, status).copy(requestSent = status == ImageTaskStatus.GENERATING,
                    resultImagePath = if (status == ImageTaskStatus.AWAITING_CONFIRMATION || status == ImageTaskStatus.COMPLETED) "/images/result.png" else null))
            }
            assertEquals(4, repo.interruptUnfinished(50))
            assertEquals(0, repo.interruptUnfinished(60))
            assertEquals(ImageTaskStatus.INTERRUPTED, repo.observe("GENERATING").first()!!.status)
            assertTrue(repo.get("GENERATING")!!.requestSent)
            assertTrue(repo.get("GENERATING")!!.sourceImagePaths.isEmpty())
            assertEquals(50, repo.get("GENERATING")!!.updatedAt)
            val pending = repo.get("AWAITING_CONFIRMATION")!!
            assertEquals(ImageTaskStatus.AWAITING_CONFIRMATION, pending.status)
            assertEquals(setOf("/images/result.png"), pending.protectedImagePaths())
            assertTrue(pending.sourceImagePaths.isEmpty())
            assertTrue(repo.get("COMPLETED")!!.protectedImagePaths().isEmpty())
            assertEquals("/images/result.png", repo.get("COMPLETED")!!.resultImagePath)
            assertEquals(10, repo.getAll().size)
        } finally { db.close() }
    }

    @Test fun taskMetadataAndOrderedReferencesSurviveDatabaseReopen() = runBlocking {
        val name = "image-task-reopen-${UUID.randomUUID()}"
        try {
            val original = task("persisted").copy(kind = ImageTaskKind.TRY_ON,
                requestJson = ImageTaskRequest(listOf(ImageTaskGarment(7,"裙子",TryOnRole.MAIN), ImageTaskGarment(3,"手袖",TryOnRole.HAND))).encode(),
                sourceImagePaths = listOf("/images/seven.png", "/images/three.png"))
            val initial = Room.databaseBuilder(context, LolitaDatabase::class.java, name).build()
            try { ImageTaskRepository(initial.imageTaskDao()).insert(original) } finally { initial.close() }
            val reopened = Room.databaseBuilder(context, LolitaDatabase::class.java, name).build()
            try {
                val restored = ImageTaskRepository(reopened.imageTaskDao()).get("persisted")!!
                assertEquals(original, restored)
                assertEquals(listOf(7L, 3L), ImageTaskRequest.decode(restored.requestJson).selections(restored.sourceImagePaths).map { it.itemId })
                assertEquals(setOf("/images/seven.png", "/images/three.png"), restored.protectedImagePaths())
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    private fun snapshot(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata') ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
            }
        }
    }
}
