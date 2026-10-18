package com.lolita.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.ImageTaskCoordinator
import com.lolita.app.data.generation.ImageTaskExecutor
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No real key, image decoder or transport is called: counters verify the paid boundary. */
@RunWith(AndroidJUnit4::class)
class ImageTaskMissingResourceExecutionTest {
    @Test fun missingInputMarkerStopsBeforeCredentialsPreparationOrTransport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val coordinator = ImageTaskCoordinator(db, scope, {}, { error("No result may be saved") }, {}, { true })
            coordinator.awaitStartup()
            var credentialReads = 0; var preparations = 0; var requests = 0
            val executor = ImageTaskExecutor(coordinator, db,
                { credentialReads++; throw IllegalStateException("Controlled credential read must never run") },
                { preparations++; emptyList() }, { preparations++; emptyList() },
                { _, _, _ -> requests++; byteArrayOf(1) })
            val head = WigHeadTemplateSnapshot("chosen", "原选头模候选", 1, "a".repeat(64), "/missing/head.png", 1024, 1536)
            val request = ImageTaskRequest(listOf(ImageTaskGarment(1, "假发", TryOnRole.MAIN, CategorySemantic.FULL_WIG)), version = 4,
                mannequinSubject = MannequinSubject(7, "假发", MannequinPresentation.WIG_HEAD, CategorySemantic.FULL_WIG),
                promptVersion = 1, promptSnapshot = "original prompt", serviceModel = ImageGenerationProvider.SEEDREAM.model,
                inputSha256s = listOf(head.sha256, "b".repeat(64)), wigHeadTemplate = head)
            val task = ImageTask("missing", ImageTaskKind.MANNEQUIN, ImageGenerationProvider.SEEDREAM, "缺输入", request.encode(),
                listOf(head.imagePath, "/missing/wig.jpg"), keepSourceImages = true, sourceResourcesMissing = true)
            val failure = runCatching { executor.execute(task) }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(0, credentialReads); assertEquals(0, preparations); assertEquals(0, requests)
            assertEquals(ImageTask.SOURCE_RESOURCES_MISSING_MESSAGE, failure!!.message)
            assertEquals("chosen", ImageTaskRequest.decode(task.requestJson).wigHeadTemplate!!.id)
        } finally { scope.coroutineContext.job.cancelAndJoin(); db.close() }
    }
}
