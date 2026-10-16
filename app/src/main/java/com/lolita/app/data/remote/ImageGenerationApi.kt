package com.lolita.app.data.remote

import com.lolita.app.domain.model.ImageGenerationCredentials
import com.lolita.app.domain.model.ImageGenerationProvider

class ImageGenerationApi(
    private val openAi: TryOnImageApi = TryOnImageApi(),
    private val seedream: SeedreamImageApi = SeedreamImageApi()
) {
    suspend fun generateMannequin(credentials: ImageGenerationCredentials, images: List<ByteArray>, prompt: String): ByteArray =
        when (credentials.provider) {
            ImageGenerationProvider.OPENAI -> openAi.generateMannequin(credentials.apiKey, images, prompt)
            ImageGenerationProvider.SEEDREAM -> seedream.generateMannequin(credentials.apiKey, images, prompt)
        }

    suspend fun generate(credentials: ImageGenerationCredentials, images: List<ByteArray>, prompt: String): ByteArray =
        when (credentials.provider) {
            ImageGenerationProvider.OPENAI -> openAi.generate(credentials.apiKey, images, prompt)
            ImageGenerationProvider.SEEDREAM -> seedream.generate(credentials.apiKey, images, prompt)
        }

    suspend fun generatePersonalModel(credentials: ImageGenerationCredentials, images: List<ByteArray>, prompt: String): ByteArray =
        when (credentials.provider) {
            ImageGenerationProvider.OPENAI -> openAi.generatePersonalModel(credentials.apiKey, images, prompt)
            ImageGenerationProvider.SEEDREAM -> seedream.generatePersonalModel(credentials.apiKey, images, prompt)
        }
}
