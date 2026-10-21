package com.lolita.app.data

import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.entity.ImageSourceType
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class MaterialCoverWorkflowTest {
    private suspend fun fixture(block: suspend (MaterialTestFixture) -> Unit) {
        val f = MaterialTestFixture()
        try { f.init(); withTimeout(60_000) { block(f) } } finally { f.close() }
    }
    @Test fun explicitCoverSharesConfirmedBytesAndKeepsLatestAlbumAndBusinessFields() = runBlocking { fixture { f ->
        val original = f.db.itemDao().getItemById(f.itemId)!!
        val material = f.confirmed()
        assertEquals(original, f.db.itemDao().getItemById(f.itemId))
        val extra = ImageFileHelper.saveGeneratedImage(f.context, f.opaque)
        f.db.itemDao().updateItem(original.copy(imageUrls = original.imageUrls + extra, description = "提交前刚更新的备注"))
        val starts = f.serviceStarts
        coroutineScope { List(3) { async { f.coordinator.setMaterialCover(f.itemId, material.id) } }.awaitAll() }
        val result = f.db.itemDao().getItemById(f.itemId)!!
        assertEquals(listOf(material.imagePath, f.source, extra), result.imageUrls)
        assertEquals(original.copy(description = "提交前刚更新的备注"), result.copy(imageUrls = original.imageUrls, updatedAt = original.updatedAt))
        val provenance = f.db.imageProvenanceDao().get(f.itemId, material.imagePath)!!
        assertEquals(ImageSourceType.AI_GENERATED, provenance.sourceType)
        assertEquals(material.sourceTaskId, provenance.taskId)
        f.coordinator.setMaterialCover(f.itemId, material.id)
        assertEquals(result, f.db.itemDao().getItemById(f.itemId))
        assertEquals(starts, f.serviceStarts)
    } }
    @Test fun replacingRemovingAndDeletingTasksKeepAlbumCoverAndZipProvenance() = runBlocking { fixture { f ->
        val first = f.confirmed(); f.coordinator.setMaterialCover(f.itemId, first.id)
        val hash = imageSha256(first.imagePath)
        val next = f.confirmed()
        assertNotEquals(first.id, next.id)
        assertEquals(first.imagePath, f.db.itemDao().getItemById(f.itemId)!!.imageUrls.first())
        f.coordinator.removeMaterial(f.itemId, next.id)
        f.db.imageTaskDao().getAll().forEach { f.coordinator.delete(it.id) }
        ImageFileHelper.cleanupUnreferencedImages(listOf(first.imagePath))
        assertEquals(hash, imageSha256(first.imagePath))
        assertNull(f.db.itemTryOnMaterialDao().getForItem(f.itemId))
        val manager = BackupManager(f.context, f.db, withImageTasksPaused = { action -> f.coordinator.withRestorePaused(action) })
        val zip = manager.exportToZip().getOrThrow()
        try {
            manager.importBackup(zip).getOrThrow()
            val item = f.db.itemDao().getItemById(f.itemId)!!
            assertEquals(2, item.imageUrls.size)
            assertEquals(hash, imageSha256(item.imageUrls.first()))
            assertEquals(ImageSourceType.AI_GENERATED, f.db.imageProvenanceDao().get(f.itemId, item.imageUrls.first())!!.sourceType)
            assertTrue(f.db.imageTaskDao().getAll().isEmpty())
        } finally { f.context.contentResolver.delete(zip, null, null) }
    } }
    @Test fun sharedCoverAndMaterialRestoreToSameImageAndStaleIdOrTamperedBytesCannotPromote() = runBlocking { fixture { f ->
        val material = f.confirmed()
        val before = f.db.itemDao().getItemById(f.itemId)!!
        assertTrue(runCatching { f.coordinator.setMaterialCover(f.itemId, UUID.randomUUID().toString()) }.isFailure)
        assertEquals(before, f.db.itemDao().getItemById(f.itemId))
        f.coordinator.setMaterialCover(f.itemId, material.id)
        val manager = BackupManager(f.context, f.db, withImageTasksPaused = { action -> f.coordinator.withRestorePaused(action) })
        val zip = manager.exportToZip().getOrThrow()
        try { manager.importBackup(zip).getOrThrow() } finally { f.context.contentResolver.delete(zip, null, null) }
        val restored = f.db.itemTryOnMaterialDao().getForItem(f.itemId)!!
        assertEquals(restored.imagePath, f.db.itemDao().getItemById(f.itemId)!!.imageUrls.first())
        val current = f.db.itemDao().getItemById(f.itemId)!!
        File(restored.imagePath).writeBytes(f.opaque)
        assertTrue(runCatching { f.coordinator.setMaterialCover(f.itemId, restored.id) }.isFailure)
        assertEquals(current, f.db.itemDao().getItemById(f.itemId))
    } }
    @Test fun changedTypeOrCompositionRejectsV6CoverWithoutAlbumOrProvenanceWrites() = runBlocking { fixture { f ->
        val item = f.categoryItems().first(); f.echoCategoryImages()
        val batch = MaterialBatchIdentity(UUID.randomUUID().toString(), "封面类型核对")
        val fronts = f.coordinator.submitMaterialBatch(batch, UUID.randomUUID().toString(), f.batchInputs(listOf(item)), ImageGenerationProvider.SEEDREAM); f.runner.runQueue()
        val layers = f.coordinator.submitLayerBatch(batch, UUID.randomUUID().toString(), f.batchLayers(fronts)); f.runner.runQueue()
        val task = f.db.imageTaskDao().get(layers.single())!!
        val id = f.coordinator.confirmMaterial(task.id, task.resultImagePath!!, null, true)
        f.db.itemDao().updateItem(item.copy(composition = ItemComposition.PAIR))
        assertTrue(runCatching { f.coordinator.setMaterialCover(item.id, id) }.isFailure)
        f.db.itemDao().updateItem(item.copy(categoryId = f.categoryId))
        assertTrue(runCatching { f.coordinator.setMaterialCover(item.id, id) }.isFailure)
        assertEquals(item.imageUrls, f.db.itemDao().getItemById(item.id)!!.imageUrls)
        assertTrue(f.db.imageProvenanceDao().getForItem(item.id).isEmpty())
    } }
}
