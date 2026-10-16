package com.lolita.app.data.file

import java.util.zip.CRC32
import java.util.zip.Inflater

/** Checks every chunk CRC and the complete zlib stream, including Adam7's exact scanline sizes. */
internal object PersonalPngIntegrity {
    fun check(bytes: ByteArray): Boolean {
        require(bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
        val inflater = Inflater()
        try {
            var at = 8
            var expected = -1L
            var actual = 0L
            var sawData = false
            var endedData = false
            val buffer = ByteArray(8192)
            while (at < bytes.size) {
                require(at <= bytes.size - 12)
                val length = int(bytes, at)
                require(length >= 0 && at.toLong() + length + 12 <= bytes.size)
                val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
                require((at == 8) == (type == "IHDR"))
                val crc = CRC32().apply { update(bytes, at + 4, length + 4) }
                require(crc.value == (int(bytes, at + 8 + length).toLong() and 0xffffffffL))
                when (type) {
                    "IHDR" -> {
                        require(length == 13)
                        expected = scanlineBytes(bytes, at + 8)
                        require(expected <= 256L * 1024 * 1024)
                    }
                    "IDAT" -> {
                        require(expected >= 0 && !endedData)
                        sawData = true
                        if (inflater.finished()) require(length == 0)
                        else {
                            require(inflater.needsInput())
                            inflater.setInput(bytes, at + 8, length)
                            while (!inflater.needsInput() && !inflater.finished()) {
                                val count = inflater.inflate(buffer)
                                require(count > 0 || inflater.finished() || inflater.needsInput())
                                actual += count
                                require(actual <= expected)
                            }
                            require(inflater.remaining == 0)
                        }
                    }
                    "IEND" -> {
                        require(length == 0 && sawData && inflater.finished() && actual == expected)
                        require(at + 12 == bytes.size)
                        return true
                    }
                    else -> {
                        // PLTE is the only other defined critical chunk. Unknown critical chunks fail closed.
                        require(type.length == 4 && type.all { it in 'A'..'Z' || it in 'a'..'z' })
                        require(type == "PLTE" || type[0] in 'a'..'z')
                        if (sawData) endedData = true
                    }
                }
                at += length + 12
            }
            return false
        } finally {
            inflater.end()
        }
    }

    private fun scanlineBytes(bytes: ByteArray, at: Int): Long {
        val width = int(bytes, at)
        val height = int(bytes, at + 4)
        require(PersonalImageIntegrity.supportsDimensions(width, height))
        val depth = bytes[at + 8].toInt() and 255
        val channels = when (bytes[at + 9].toInt() and 255) {
            0 -> { require(depth in listOf(1,2,4,8,16)); 1 }
            2 -> { require(depth in listOf(8,16)); 3 }
            3 -> { require(depth in listOf(1,2,4,8)); 1 }
            4 -> { require(depth in listOf(8,16)); 2 }
            6 -> { require(depth in listOf(8,16)); 4 }
            else -> throw IllegalArgumentException("PNG color type")
        }
        require(bytes[at + 10] == 0.toByte() && bytes[at + 11] == 0.toByte())
        val interlace = bytes[at + 12].toInt()
        require(interlace in 0..1)
        fun pass(x: Int, y: Int, dx: Int, dy: Int): Long {
            val columns = if (width <= x) 0 else (width - x + dx - 1) / dx
            val rows = if (height <= y) 0 else (height - y + dy - 1) / dy
            return if (columns == 0 || rows == 0) 0 else rows * (1L + (columns.toLong() * channels * depth + 7) / 8)
        }
        return if (interlace == 0) pass(0,0,1,1) else
            pass(0,0,8,8) + pass(4,0,8,8) + pass(0,4,4,8) + pass(2,0,4,4) +
                pass(0,2,2,4) + pass(1,0,2,2) + pass(0,1,1,2)
    }

    private fun int(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 255) shl 24) or ((bytes[at + 1].toInt() and 255) shl 16) or
            ((bytes[at + 2].toInt() and 255) shl 8) or (bytes[at + 3].toInt() and 255)
}
