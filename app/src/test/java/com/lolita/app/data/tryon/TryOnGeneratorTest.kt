package com.lolita.app.data.tryon

import com.lolita.app.data.local.entity.Item
import com.lolita.app.data.local.entity.ItemStatus
import com.lolita.app.domain.model.TryOnRole
import com.lolita.app.domain.model.TryOnSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TryOnGeneratorTest {
    @Test fun frozenOldAndNewPromptsAreSentVerbatimDespiteProviderPromptRefinements() = runBlocking {
        Fixture().use { f ->
            val model = File(f.folder, "model.jpg").apply { writeBytes(byteArrayOf(3)) }
            val paths = listOf(model.path) + f.selected.map { it.imagePath }
            var sent: String? = null
            f.api = { _, _, prompt -> sent = prompt; byteArrayOf(7) }
            for (revision in listOf(2, com.lolita.app.domain.model.TryOnRequest.PROMPT_VERSION)) {
                val request = com.lolita.app.domain.model.ImageTaskRequest(f.selected.map { com.lolita.app.domain.model.ImageTaskGarment(it.itemId, it.name, it.role) },
                    version = 4, promptVersion = revision, promptSnapshot = "exact-frozen-$revision", serviceModel = com.lolita.app.domain.model.ImageGenerationProvider.OPENAI.model,
                    inputSha256s = paths.map { com.lolita.app.domain.model.imageSha256(it) })
                f.generator().generate(f.selected, immutableRequest = request, immutablePaths = paths,
                    prepareSnapshotImages = { input -> input.map { File(it).readBytes() } })
                assertEquals("exact-frozen-$revision", sent)
            }
        }
    }
    @Test fun staleDisclosedProviderNeverPreparesImagesOrStartsPaidRequest() = runBlocking {
        Fixture().use { f ->
            expectFailure { f.generator().generate(f.selected, expectedProvider = com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM) }
            assertEquals(0, f.prepareCalls); assertEquals(0, f.apiCalls); assertTrue(f.saved.isEmpty())
        }
    }
    @Test fun changingServiceDuringPreparationKeepsOriginalKeyAndActualModelInHistory() = runBlocking {
        Fixture().use { f ->
            var active = com.lolita.app.domain.model.ImageGenerationCredentials(com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM, "seed-key")
            var reads = 0
            var sent: com.lolita.app.domain.model.ImageGenerationCredentials? = null
            var savedModel = ""
            val generator = TryOnGenerator(findItem = { f.items[it] }, readCredentials = { reads++; active },
                prepareImages = { active = com.lolita.app.domain.model.ImageGenerationCredentials(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, "gpt-key"); f.prepare(it) },
                generateImage = { credentials, _, _ -> sent = credentials; byteArrayOf(7) },
                saveResult = { _, _, model -> savedModel = model; 1L })
            generator.generate(f.selected)
            assertEquals(1, reads)
            assertEquals("seed-key", sent!!.apiKey)
            assertEquals(com.lolita.app.domain.model.ImageGenerationProvider.SEEDREAM, sent!!.provider)
            assertEquals("doubao-seedream-5-0-pro-260628", savedModel)
        }
    }
    @Test fun awaitedBeforeRequestCompletesBeforeTransportAndFailurePreventsSending() = runBlocking {
        Fixture().use { f ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val running = async { f.generator().generate(f.selected, beforeRequest = {
                entered.complete(Unit); release.await()
            }) }
            withTimeout(2_000) { entered.await() }
            assertEquals(0, f.apiCalls)
            release.complete(Unit)
            assertEquals(91L, running.await())
            assertEquals(1, f.apiCalls)
            expectFailure { f.generator().generate(f.selected, beforeRequest = { error("durable write rejected") }) }
            assertEquals(1, f.apiCalls)
        }
    }

    private class Fixture : AutoCloseable {
        val folder = kotlin.io.path.createTempDirectory("tryon-generator-").toFile()
        val dress = File(folder, "dress.jpg").apply { writeBytes(byteArrayOf(1)) }
        val cuffs = File(folder, "cuffs.jpg").apply { writeBytes(byteArrayOf(2)) }
        val selected = listOf(
            TryOnSelection(11, "连衣裙", dress.absolutePath, TryOnRole.MAIN),
            TryOnSelection(12, "手袖", cuffs.absolutePath, TryOnRole.HAND)
        )
        val items = selected.associate { row -> row.itemId to Item(
            brandId = 1, categoryId = 1, description = "不应上传的私有备注", id = row.itemId,
            imageUrls = listOf(row.imagePath), name = row.name, status = ItemStatus.OWNED
        ) }.toMutableMap()
        var key = "sk-personal-test-key"
        var prepareCalls = 0
        var apiCalls = 0
        val saved = mutableListOf<Triple<ByteArray, List<TryOnSelection>, String>>()
        var prepare: suspend (List<String>) -> List<ByteArray> = { paths ->
            prepareCalls++
            listOf("MODEL".toByteArray()) + paths.map { File(it).readBytes() }
        }
        var api: suspend (String, List<ByteArray>, String) -> ByteArray = { _, _, _ ->
            apiCalls++
            "GENERATED".toByteArray()
        }
        var save: suspend (ByteArray, List<TryOnSelection>, String) -> Long = { bytes, rows, model ->
            saved.add(Triple(bytes, rows, model))
            91L
        }

        fun generator() = TryOnGenerator(
            findItem = { items[it] }, readCredentials = { com.lolita.app.domain.model.ImageGenerationCredentials(com.lolita.app.domain.model.ImageGenerationProvider.OPENAI, key) },
            prepareImages = { prepare(it) }, generateImage = { key, images, prompt -> api(key.apiKey, images, prompt) },
            saveResult = { bytes, rows, model -> save(bytes, rows, model) }
        )

        override fun close() { folder.deleteRecursively() }
    }

    @Test fun invalidPersonalKeyNeverPreparesImagesOrStartsPaidRequest() = runBlocking {
        Fixture().use { fixture ->
            for (invalid in listOf("", " ", "sk key", "sk-key\nheader", "x".repeat(513))) {
                fixture.key = invalid
                val generator = fixture.generator()
                expectFailure { generator.generate(fixture.selected) }
                assertFalse(generator.isGenerating.value)
            }
            assertEquals(0, fixture.prepareCalls)
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test fun deletedItemOrUnrelatedImageRejectsTheWholeSelectionBeforeUpload() = runBlocking {
        Fixture().use { fixture ->
            val original = fixture.items.getValue(12)
            fixture.items.remove(12)
            expectFailure { fixture.generator().generate(fixture.selected) }
            fixture.items[12] = original.copy(imageUrls = listOf("/another-photo.jpg"))
            expectFailure { fixture.generator().generate(fixture.selected) }
            assertEquals(0, fixture.prepareCalls)
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test fun missingSelectedFileNeverUploadsTheRemainingImages() = runBlocking {
        Fixture().use { fixture ->
            fixture.cuffs.delete()
            expectFailure { fixture.generator().generate(fixture.selected) }
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test fun itemOutsideOwnedAndWishListsRejectsTheWholeSelectionBeforeUpload() = runBlocking {
        Fixture().use { fixture ->
            val original = fixture.items.getValue(12)
            for (status in ItemStatus.entries.filter { it !in setOf(ItemStatus.OWNED, ItemStatus.WISHED) }) {
                fixture.items[12] = original.copy(status = status)
                expectFailure { fixture.generator().generate(fixture.selected) }
            }
            assertEquals(0, fixture.prepareCalls)
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test fun wishedReferencesCanGenerateWithoutChangingTheirPurchaseState() = runBlocking {
        Fixture().use { f ->
            f.items.replaceAll { _, item -> item.copy(status = ItemStatus.WISHED) }
            val before = f.items.toMap()
            assertEquals(91L, f.generator().generate(f.selected))
            assertEquals(1, f.prepareCalls)
            assertEquals(1, f.apiCalls)
            assertEquals(before, f.items)
            assertEquals(f.selected, f.saved.single().second)
        }
    }

    @Test fun partialPreparationNeverStartsPaidRequest() = runBlocking {
        Fixture().use { fixture ->
            fixture.prepare = { listOf("MODEL".toByteArray(), "DRESS".toByteArray()) }
            expectFailure { fixture.generator().generate(fixture.selected) }
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
        }
    }

    @Test fun differentEntryPointsShareOneGenerationLockAndReleaseItAfterCancellation() = runBlocking {
        Fixture().use { fixture ->
            val entered = CompletableDeferred<Unit>()
            fixture.api = { _, _, _ -> entered.complete(Unit); awaitCancellation() }
            val first = fixture.generator()
            val second = fixture.generator()
            val running = async(start = CoroutineStart.UNDISPATCHED) { first.generate(fixture.selected) }
            withTimeout(2_000) { entered.await() }
            assertTrue(first.isGenerating.value)
            assertTrue(second.isGenerating.value)
            withTimeout(2_000) { expectFailure { second.generate(fixture.selected) } }
            assertTrue(fixture.saved.isEmpty())
            running.cancelAndJoin()
            assertFalse(first.isGenerating.value)
            assertFalse(second.isGenerating.value)
            fixture.api = { _, _, _ -> "GENERATED".toByteArray() }
            assertEquals(91L, second.generate(fixture.selected))
            assertEquals(1, fixture.saved.size)
        }
    }

    @Test fun cancellationWhilePreparingCreatesNoHistoryAndDoesNotStartRequest() = runBlocking {
        Fixture().use { fixture ->
            val entered = CompletableDeferred<Unit>()
            fixture.prepare = { entered.complete(Unit); awaitCancellation() }
            val generator = fixture.generator()
            val running = async(start = CoroutineStart.UNDISPATCHED) { generator.generate(fixture.selected) }
            withTimeout(2_000) { entered.await() }
            running.cancelAndJoin()
            assertEquals(0, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
            assertFalse(generator.isGenerating.value)
        }
    }

    @Test fun failedRequestDoesNotSaveOrRetryAndCanBeRetriedByTheUser() = runBlocking {
        Fixture().use { fixture ->
            fixture.api = { _, _, _ -> fixture.apiCalls++; error("受控服务错误") }
            val generator = fixture.generator()
            expectFailure { generator.generate(fixture.selected) }
            assertEquals(1, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
            assertFalse(generator.isGenerating.value)
            fixture.api = { _, _, _ -> "GENERATED".toByteArray() }
            assertEquals(91L, generator.generate(fixture.selected))
            assertEquals(1, fixture.saved.size)
        }
    }

    @Test fun cancellationAfterNetworkFinishesStillPreventsSaving() = runBlocking {
        Fixture().use { fixture ->
            val running = async {
                val job = currentCoroutineContext().job
                fixture.generator().generate(fixture.selected, onPhase = { phase ->
                    if (phase == TryOnGenerationPhase.SAVING) job.cancel()
                })
            }
            try {
                running.await()
                fail("cancelled generation returned a history ID")
            } catch (_: CancellationException) { }
            assertEquals(1, fixture.apiCalls)
            assertTrue(fixture.saved.isEmpty())
            assertFalse(fixture.generator().isGenerating.value)
        }
    }

    @Test fun savesOneResultWithOrderedReferencesSelectedRolesAndAccurateModel() = runBlocking {
        Fixture().use { fixture ->
            val phases = mutableListOf<TryOnGenerationPhase>()
            fixture.api = { key, images, prompt ->
                fixture.apiCalls++
                assertEquals("sk-personal-test-key", key)
                assertEquals(3, images.size)
                assertArrayEquals("MODEL".toByteArray(), images[0])
                assertArrayEquals(byteArrayOf(1), images[1])
                assertArrayEquals(byteArrayOf(2), images[2])
                assertTrue(prompt.contains("\"image\":2"))
                assertTrue(prompt.contains("\"image\":3"))
                assertTrue(prompt.contains("hand or wrist accessory"))
                assertFalse(prompt.contains("不应上传的私有备注"))
                "GENERATED".toByteArray()
            }
            val generator = fixture.generator()
            assertEquals(91L, generator.generate(fixture.selected, phases::add))
            assertEquals(listOf(TryOnGenerationPhase.PREPARING, TryOnGenerationPhase.GENERATING, TryOnGenerationPhase.SAVING), phases)
            assertEquals(1, fixture.apiCalls)
            assertEquals(1, fixture.saved.size)
            val saved = fixture.saved.single()
            assertArrayEquals("GENERATED".toByteArray(), saved.first)
            assertEquals(fixture.selected, saved.second)
            assertEquals("gpt-image-2.5-sunburst", saved.third)
            assertFalse(generator.isGenerating.value)
        }
    }

    @Test fun selectionSnapshotCannotBeChangedDuringPreparation() = runBlocking {
        Fixture().use { fixture ->
            val rows = fixture.selected.toMutableList()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val prepare = fixture.prepare
            fixture.prepare = { paths -> entered.complete(Unit); release.await(); prepare(paths) }
            val running = async(start = CoroutineStart.UNDISPATCHED) { fixture.generator().generate(rows) }
            withTimeout(2_000) { entered.await() }
            rows.clear()
            release.complete(Unit)
            assertEquals(91L, withTimeout(2_000) { running.await() })
            assertEquals(fixture.selected, fixture.saved.single().second)
        }
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("生成前置条件或服务错误必须阻止成功")
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
    }
}
