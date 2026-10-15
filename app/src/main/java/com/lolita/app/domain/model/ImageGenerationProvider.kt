package com.lolita.app.domain.model

enum class ImageGenerationProvider(val label: String, val recipient: String, val model: String) {
    SEEDREAM("即梦 5.0 Pro", "火山方舟", "doubao-seedream-5-0-pro-260628"),
    OPENAI("GPT 图像", "OpenAI", "gpt-image-2.5-sunburst");

    val keyHelp: String get() = when (this) {
        SEEDREAM -> "填写火山方舟 API Key，并开通 Seedream 5.0 Pro。按量计费，需有可用额度。"
        OPENAI -> "填写 OpenAI API Key。API 单独计费，ChatGPT 订阅不含此额度。"
    }
}

/** Immutable per-request snapshot. Deliberately excludes the secret from diagnostics. */
class ImageGenerationCredentials(val provider: ImageGenerationProvider, val apiKey: String) {
    override fun toString() = "ImageGenerationCredentials(provider=$provider)"
}
