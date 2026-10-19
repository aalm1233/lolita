package com.lolita.app.data.repository

import com.lolita.app.data.recognition.RecognitionSettings
import com.lolita.app.data.tryon.TryOnSettings
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.RecognitionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class AiKeySlot(val label: String, val generation: ImageGenerationProvider? = null, val recognition: RecognitionProvider? = null) {
    SEEDREAM("即梦 / 火山方舟", generation = ImageGenerationProvider.SEEDREAM),
    OPENAI("GPT 图像 / OpenAI", generation = ImageGenerationProvider.OPENAI),
    DOUBAO("豆包 / 火山方舟", recognition = RecognitionProvider.VOLCENGINE_ARK),
    DEEPSEEK("DeepSeek", recognition = RecognitionProvider.DEEPSEEK);

    val purpose: String get() = if (generation != null) "生图" else "识图"
    val help: String get() = generation?.keyHelp ?: when (this) {
        DOUBAO -> "填写火山方舟 API Key，并开通豆包 Seed 2.1 Pro。识图按量计费。"
        else -> "填写 DeepSeek API Key，并确认账号有可用额度。识图按量计费。"
    }
}

enum class AiKeyStatus { EMPTY, CONFIGURED, UNREADABLE }

/** Contains status only. Secret bytes never leave the existing settings adapters when reading. */
data class AiConfigurationSnapshot(
    val generation: ImageGenerationProvider? = null,
    val recognition: RecognitionProvider? = null,
    val keys: Map<AiKeySlot, AiKeyStatus> = emptyMap(),
    val generationRevision: Int = 0,
    val recognitionRevision: Int = 0
) {
    fun isSelected(slot: AiKeySlot): Boolean = slot.generation?.let { it == generation }
        ?: (slot.recognition == recognition)
    fun revision(slot: AiKeySlot): Int = if (slot.generation != null) generationRevision else recognitionRevision
}

interface AiConfigurationStore {
    val changes: Flow<Unit>
    fun read(): AiConfigurationSnapshot
    fun save(slot: AiKeySlot, value: String)
    fun clear(slot: AiKeySlot)
    fun select(slot: AiKeySlot)
}

/** A single configuration entry point using the unchanged encrypted files and revision streams. */
class AiConfigurationRepository(private val images: TryOnSettings, private val recognition: RecognitionSettings) : AiConfigurationStore {
    override val changes = combine(images.changes, recognition.changes) { _, _ -> Unit }

    override fun read() = AiConfigurationSnapshot(
        generation = runCatching { images.readProvider() }.getOrNull(),
        recognition = runCatching { recognition.readProvider() }.getOrNull(),
        keys = AiKeySlot.entries.associateWith { slot ->
            try {
                val present = slot.generation?.let { images.readKey(it).isNotBlank() }
                    ?: recognition.readKey(requireNotNull(slot.recognition)).isNotBlank()
                if (present) AiKeyStatus.CONFIGURED else AiKeyStatus.EMPTY
            } catch (_: Exception) { AiKeyStatus.UNREADABLE }
        },
        generationRevision = images.changes.value,
        recognitionRevision = recognition.changes.value
    )

    override fun save(slot: AiKeySlot, value: String) {
        slot.generation?.let { images.saveKey(value, it) }
            ?: recognition.saveKey(value, requireNotNull(slot.recognition))
    }
    override fun clear(slot: AiKeySlot) {
        slot.generation?.let { images.clearKey(it) }
            ?: recognition.clearKey(requireNotNull(slot.recognition))
    }
    override fun select(slot: AiKeySlot) {
        slot.generation?.let { images.selectProvider(it) }
            ?: recognition.selectProvider(requireNotNull(slot.recognition))
    }
}
