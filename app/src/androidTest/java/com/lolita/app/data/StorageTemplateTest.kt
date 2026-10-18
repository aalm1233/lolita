package com.lolita.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.repository.StorageRepository
import com.lolita.app.di.AppModule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class StorageTemplateTest {
    @Test fun templatesOnlyCreateSelectedNodesAfterAdoptionAndNeverReappearAfterDeletion() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "storage-templates-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        try {
            ImageFileHelper.configure(context, db)
            val repository = StorageRepository(db, context)
            val room = db.locationDao().insertLocation(Location(name = "自己的房间", imageUrl = "/原照片"))
            val original = db.locationDao().getLocationByIdSync(room)
            val templates = repository.getTemplates()
            assertEquals(4, templates.size)
            assertTrue(repository.getNodes().isEmpty())
            assertEquals(original, db.locationDao().getLocationByIdSync(room))
            val chosen = templates.first()
            repository.adoptTemplates(room, setOf(chosen.id))
            val node = repository.getNodes().single()
            assertEquals(chosen.name, node.name)
            val image = File(requireNotNull(node.imageUrl))
            UUID.fromString(image.nameWithoutExtension)
            val imageDirectory = requireNotNull(image.parentFile)
            assertEquals(File(context.filesDir, "images").canonicalFile, imageDirectory.canonicalFile)
            assertEquals(chosen.sha256, MessageDigest.getInstance("SHA-256").digest(image.readBytes()).joinToString("") { "%02x".format(it) })
            try { repository.adoptTemplates(room, setOf(chosen.id)); fail("重复采纳应拒绝") } catch (_: IllegalArgumentException) { }
            withTimeout(5_000) { while (imageDirectory.listFiles().orEmpty().size != 1) delay(25) }
            assertTrue(image.exists())
            repository.deleteNode(node.id)
            withTimeout(5_000) { while (image.exists()) delay(25) }
            repository.getTemplates()
            assertTrue(repository.getNodes().isEmpty())
            assertEquals(original, db.locationDao().getLocationByIdSync(room))
        } finally {
            ImageFileHelper.configure(AppModule.context(), AppModule.database())
            db.close()
            directory.deleteRecursively()
        }
    }
}
