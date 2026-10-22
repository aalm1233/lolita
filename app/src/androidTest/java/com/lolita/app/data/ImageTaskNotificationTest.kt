package com.lolita.app.data

import android.app.Notification
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.generation.ImageTaskNotifications
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.ImageTask
import com.lolita.app.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTaskNotificationTest {
    @Test fun durableStagesAndQueueChangesReachNotificationWithoutInventedProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            val dao = db.imageTaskDao()
            val active = ImageTask("active", ImageTaskKind.TRY_ON, ImageGenerationProvider.SEEDREAM, "正在处理的裙子",
                ImageTaskRequest(listOf(ImageTaskGarment(1, "裙子", TryOnRole.MAIN))).encode(), listOf("source"),
                status = ImageTaskStatus.PREPARING)
            dao.insert(active)
            dao.insert(active.copy(id = "queued", title = "下一件", status = ImageTaskStatus.QUEUED))
            for ((status, stage) in listOf(ImageTaskStatus.PREPARING to "正在准备",
                ImageTaskStatus.GENERATING to "正在生成", ImageTaskStatus.SAVING to "正在保存")) {
                dao.update(active.copy(status = status, requestSent = status != ImageTaskStatus.PREPARING))
                val summary = withTimeout(5_000) { dao.observeExecutionSummary().first { it.status == status } }
                val notification = ImageTaskNotifications.notification(context, summary)
                assertEquals("active", summary.taskId)
                assertEquals("正在处理的裙子", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                assertTrue("Stage must follow the committed Room status", text.contains(stage))
                assertTrue("Additional queued task must remain visible", text.contains("排队 1 项"))
                assertFalse(text.contains("%"))
            }
            dao.insert(active.copy(id = "queued-2", status = ImageTaskStatus.QUEUED))
            val twoQueued = dao.observeExecutionSummary().first { it.queuedCount == 2 }
            assertTrue(ImageTaskNotifications.notification(context, twoQueued).extras
                .getCharSequence(Notification.EXTRA_TEXT).toString().contains("排队 2 项"))
            dao.update(active.copy(status = ImageTaskStatus.COMPLETED, sourceImagePaths = emptyList()))
            val waiting = dao.observeExecutionSummary().first { it.taskId == null }
            val waitingText = ImageTaskNotifications.notification(context, waiting).extras
                .getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue(waitingText.contains("等待确认")); assertTrue(waitingText.contains("排队 2 项"))
            dao.deleteAll()
            val empty = dao.observeExecutionSummary().first { it.queuedCount == 0 }
            assertNull(empty.status)
            assertTrue(ImageTaskNotifications.notification(context, empty).extras
                .getCharSequence(Notification.EXTRA_TEXT).toString().contains("处理完毕"))
        } finally { db.close() }
    }
}
