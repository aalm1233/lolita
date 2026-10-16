package com.lolita.app.data.file

/** Strict legacy-platform integrity gate for new personal-model source photographs only. */
internal object PersonalImageIntegrity {
    const val MAX_PIXELS = 36_000_000L
    private const val MAX_BYTES = 80 * 1024 * 1024
    fun supportsDimensions(width: Int, height: Int): Boolean =
        width in 1..16384 && height in 1..16384 && width.toLong() * height <= MAX_PIXELS

    fun isComplete(bytes: ByteArray, mimeType: String?): Boolean = try {
        require(bytes.size in 1..MAX_BYTES)
        when (mimeType) {
            "image/jpeg" -> PersonalJpegIntegrity.check(bytes)
            "image/png" -> PersonalPngIntegrity.check(bytes)
            else -> false
        }
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.util.zip.DataFormatException) {
        false
    }
}
