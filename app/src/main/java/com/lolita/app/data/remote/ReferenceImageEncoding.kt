package com.lolita.app.data.remote

/** Inputs come from TryOnImages: lossless PNG where bounded, otherwise JPEG. */
internal enum class ReferenceImageEncoding(val mimeType: String, val extension: String) {
    PNG("image/png", "png"), JPEG("image/jpeg", "jpg");

    companion object {
        private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

        fun of(bytes: ByteArray): ReferenceImageEncoding =
            if (bytes.size >= pngSignature.size && pngSignature.indices.all { bytes[it] == pngSignature[it] }) PNG else JPEG
    }
}
