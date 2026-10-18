package com.lolita.app.data.file

import java.io.InputStream
import java.io.OutputStream

/** Fresh sRGB crop encoder output only. Keep compressed scan bytes; drop profile/private metadata. */
internal object CropJpegMetadata {
    fun copyWithoutMetadata(input: InputStream, output: OutputStream) {
        fun read() = input.read().also { require(it >= 0) { "裁切图片编码不完整" } }
        fun markerPrefix(count: Int) { repeat(count) { output.write(255) } }
        require(read() == 255 && read() == 216) { "裁切图片编码无效" }
        output.write(255); output.write(216)
        var pendingMarker: Pair<Int, Int>? = null
        while (true) {
            val pending = pendingMarker
            pendingMarker = null
            var prefixCount = pending?.second ?: 1
            val marker = pending?.first ?: run {
                require(read() == 255) { "裁切图片数据段无效" }
                var value = read()
                while (value == 255) { prefixCount++; value = read() }
                value
            }
            if (marker == 217) {
                markerPrefix(prefixCount); output.write(marker)
                require(input.read() == -1) { "裁切图片包含额外数据" }
                return
            }
            require(marker !in setOf(0, 1, 216) && marker !in 208..215) { "裁切图片数据段无效" }
            val length = read() * 256 + read()
            require(length >= 2) { "裁切图片数据段无效" }
            val segment = ByteArray(length - 2)
            var offset = 0
            while (offset < segment.size) {
                val count = input.read(segment, offset, segment.size - offset)
                require(count > 0) { "裁切图片编码不完整" }
                offset += count
            }
            val jfif = marker == 224 && segment.size >= 5 &&
                (segment.copyOfRange(0, 5).contentEquals(byteArrayOf(74, 70, 73, 70, 0)) ||
                    segment.copyOfRange(0, 5).contentEquals(byteArrayOf(74, 70, 88, 88, 0)))
            val metadata = marker in 225..239 || marker == 254 || marker == 224 && !jfif
            if (!metadata) {
                markerPrefix(prefixCount); output.write(marker)
                output.write(length ushr 8); output.write(length and 255); output.write(segment)
            }
            if (marker == 218) {
                // Entropy-coded pixels are copied byte-for-byte, including stuffing and restart markers.
                while (true) {
                    val value = read()
                    if (value != 255) { output.write(value); continue }
                    var count = 1
                    var next = read()
                    while (next == 255) { count++; next = read() }
                    if (next == 0 || next in 208..215) {
                        markerPrefix(count); output.write(next)
                    } else {
                        pendingMarker = next to count
                        break
                    }
                }
            }
        }
    }
}
