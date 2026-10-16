package com.lolita.app.data.file

/**
 * Consumes JPEG Huffman entropy for every expected block. No pixel decode, IDCT, or color conversion.
 * Tracking nonzero coefficients is necessary to consume legal progressive refinement scans exactly.
 * The platform decoder remains responsible for rendering after this integrity check.
 */
internal object PersonalJpegIntegrity {
    fun check(bytes: ByteArray): Boolean = Parser(bytes).check()

    private class Component(val id: Int, val h: Int, val v: Int) {
        var width = 0
        var height = 0
        var paddedWidth = 0
        var masks = LongArray(0)
        val approximation = IntArray(64) { -1 }
    }

    private data class ScanComponent(val component: Component, val dc: Int, val ac: Int)

    private class Parser(private val bytes: ByteArray) {
        private var at = 0
        private var progressive = false
        private var frame = false
        private var columns = 0
        private var rows = 0
        private var restartInterval = 0
        private var scans = 0
        private var coefficientVisits = 0L
        private val components = mutableListOf<Component>()
        private val huffman = arrayOfNulls<Huffman>(8)
        private fun byte(): Int { require(at < bytes.size); return bytes[at++].toInt() and 255 }
        private fun word(): Int = (byte() shl 8) or byte()
        private fun marker(): Int {
            require(byte() == 255)
            var value = byte()
            while (value == 255) value = byte()
            require(value != 0)
            return value
        }

        fun check(): Boolean {
            require(word() == 0xffd8)
            while (at < bytes.size) {
                val marker = marker()
                if (marker == 0xd9) {
                    require(frame && scans > 0 && at == bytes.size && components.all { it.approximation[0] >= 0 })
                    return true
                }
                require(marker !in 0xd0..0xd8 && marker != 0x01)
                val length = word()
                require(length >= 2 && at.toLong() + length - 2 <= bytes.size)
                val end = at + length - 2
                when (marker) {
                    0xc0, 0xc1, 0xc2 -> readFrame(marker, end)
                    0xc4 -> readHuffman(end)
                    0xdd -> { require(length == 4); restartInterval = word() }
                    0xda -> {
                        readScan(end)
                        scans++
                        continue
                    }
                    in 0xc0..0xcf -> throw IllegalArgumentException("Unsupported JPEG coding process")
                    else -> Unit // quantization and metadata are checked by the platform decoder.
                }
                at = end
            }
            return false
        }

        private fun readFrame(marker: Int, end: Int) {
            require(!frame && byte() == 8)
            val height = word()
            val width = word()
            require(PersonalImageIntegrity.supportsDimensions(width, height))
            val count = byte()
            require(count in 1..4 && end - at == count * 3)
            repeat(count) {
                val id = byte()
                val sampling = byte()
                val h = sampling shr 4
                val v = sampling and 15
                require(h in 1..4 && v in 1..4 && components.none { it.id == id } && byte() in 0..3)
                components += Component(id,h,v)
            }
            val maxH = components.maxOf { it.h }
            val maxV = components.maxOf { it.v }
            columns = (width + 8 * maxH - 1) / (8 * maxH)
            rows = (height + 8 * maxV - 1) / (8 * maxV)
            progressive = marker == 0xc2
            components.forEach {
                it.width = (width * it.h + 8 * maxH - 1) / (8 * maxH)
                it.height = (height * it.v + 8 * maxV - 1) / (8 * maxV)
                it.paddedWidth = columns * it.h
                // Bound progressive bookkeeping independently of the encoded file size.
                if (progressive) {
                    val blocks = it.paddedWidth * rows * it.v
                    require(blocks.toLong() + components.sumOf { part -> part.masks.size.toLong() } <= 4_194_304)
                    it.masks = LongArray(blocks)
                }
            }
            frame = true
        }

        private fun readHuffman(end: Int) {
            while (at < end) {
                val selector = byte()
                val kind = selector shr 4
                val id = selector and 15
                require(kind in 0..1 && id in 0..3 && end - at >= 16)
                val counts = IntArray(16) { byte() }
                val total = counts.sum()
                require(total in 1..256 && end - at >= total)
                huffman[kind * 4 + id] = Huffman(counts, IntArray(total) { byte() })
            }
            require(at == end)
        }

        private fun readScan(end: Int) {
            require(frame)
            val count = byte()
            require(count in 1..components.size && end - at == 2 * count + 3)
            val selected = List(count) {
                val id = byte()
                val component = components.firstOrNull { it.id == id } ?: throw IllegalArgumentException("JPEG component")
                val tables = byte()
                require(tables shr 4 in 0..3 && tables and 15 in 0..3)
                ScanComponent(component, tables shr 4, tables and 15)
            }
            require(selected.map { it.component.id }.distinct().size == count)
            val first = byte()
            val last = byte()
            val successive = byte()
            val high = successive shr 4
            val low = successive and 15
            require(first in 0..63 && last in first..63 && high <= 13 && low <= 13)
            if (progressive) require((first != 0 || last == 0) && (first == 0 || count == 1) && (high == 0 || low == high - 1))
            else require(first == 0 && last == 63 && high == 0 && low == 0)
            selected.forEach { part ->
                for (coefficient in first..last) require(if (high == 0) part.component.approximation[coefficient] == -1 else part.component.approximation[coefficient] == high)
            }
            val interleaved = count > 1
            val scanColumns = if (interleaved) columns else selected.single().component.width
            val scanRows = if (interleaved) rows else selected.single().component.height
            val units = scanColumns * scanRows
            val blocksPerUnit = selected.sumOf { if (interleaved) it.component.h * it.component.v else 1 }
            coefficientVisits += units.toLong() * blocksPerUnit * (last - first + 1)
            require(coefficientVisits <= 256_000_000L) { "JPEG refinement work budget exceeded" }
            val reader = Entropy(bytes, at)
            var eobRun = 0
            fun table(index: Int) = huffman[index] ?: throw IllegalArgumentException("Missing JPEG Huffman table")
            fun block(part: ScanComponent, index: Int) {
                var mask = if (progressive) part.component.masks[index] else 0L
                if (first == 0) {
                    if (high == 0) {
                        val bits = table(part.dc).decode(reader)
                        require(bits in 0..11)
                        reader.bits(bits)
                    } else reader.bits(1)
                }
                if (last > 0 && high == 0) {
                    if (eobRun > 0) eobRun-- else {
                        var coefficient = maxOf(1,first)
                        while (coefficient <= last) {
                            val symbol = table(4 + part.ac).decode(reader)
                            val run = symbol shr 4
                            val size = symbol and 15
                            if (size == 0) {
                                if (run == 15) {
                                    coefficient += 16
                                    require(coefficient <= last + 1)
                                } else {
                                    require(progressive || run == 0)
                                    eobRun = (1 shl run) + reader.bits(run) - 1
                                    break
                                }
                            } else {
                                require(size <= 10)
                                coefficient += run
                                require(coefficient <= last)
                                reader.bits(size)
                                mask = mask or (1L shl coefficient++)
                            }
                        }
                    }
                } else if (last > 0) {
                    var coefficient = first
                    if (eobRun == 0) {
                        while (coefficient <= last) {
                            val symbol = table(4 + part.ac).decode(reader)
                            var run = symbol shr 4
                            val size = symbol and 15
                            require(size in 0..1)
                            if (size == 0 && run < 15) {
                                eobRun = (1 shl run) + reader.bits(run)
                                break
                            }
                            if (size == 1) reader.bits(1) else run = 16
                            while (coefficient <= last) {
                                if (mask and (1L shl coefficient) != 0L) reader.bits(1)
                                else if (run > 0) {
                                    run--
                                    if (size == 0 && run == 0) { coefficient++; break }
                                } else break
                                coefficient++
                            }
                            require(run == 0)
                            if (size == 1) {
                                require(coefficient <= last)
                                mask = mask or (1L shl coefficient++)
                            }
                        }
                    }
                    if (eobRun > 0) {
                        while (coefficient <= last) {
                            if (mask and (1L shl coefficient) != 0L) reader.bits(1)
                            coefficient++
                        }
                        eobRun--
                    }
                }
                if (progressive) part.component.masks[index] = mask
            }
            var restart = 0
            for (unit in 0 until units) {
                val x = unit % scanColumns
                val y = unit / scanColumns
                selected.forEach { part ->
                    val component = part.component
                    if (interleaved) repeat(component.v) { dy -> repeat(component.h) { dx ->
                        block(part,(y * component.v + dy) * component.paddedWidth + x * component.h + dx)
                    }} else block(part, y * component.paddedWidth + x)
                }
                if (restartInterval > 0 && (unit + 1) % restartInterval == 0 && unit + 1 < units) {
                    require(eobRun == 0)
                    reader.restart(restart++ and 7)
                }
            }
            require(eobRun == 0)
            reader.finish()
            at = reader.at
            selected.forEach { part -> for (coefficient in first..last) part.component.approximation[coefficient] = low }
        }
    }

    private class Huffman(counts: IntArray, private val symbols: IntArray) {
        private val counts = counts.copyOf()
        private val firstCode = IntArray(16)
        private val firstSymbol = IntArray(16)
        init {
            var code = 0
            var symbol = 0
            for (length in 0..15) {
                firstCode[length] = code
                firstSymbol[length] = symbol
                require(code + counts[length] < (1 shl (length + 1)))
                code = (code + counts[length]) shl 1
                symbol += counts[length]
            }
        }
        fun decode(reader: Entropy): Int {
            var code = 0
            for (length in 0..15) {
                code = (code shl 1) or reader.bits(1)
                val offset = code - firstCode[length]
                if (offset in 0 until counts[length]) return symbols[firstSymbol[length] + offset]
            }
            throw IllegalArgumentException("Invalid JPEG Huffman code")
        }
    }

    private class Entropy(private val bytes: ByteArray, var at: Int) {
        private var value = 0
        private var remaining = 0
        fun bits(count: Int): Int {
            var result = 0
            repeat(count) {
                if (remaining == 0) {
                    require(at < bytes.size)
                    value = bytes[at++].toInt() and 255
                    if (value == 255) { require(at < bytes.size && bytes[at++].toInt() == 0) }
                    remaining = 8
                }
                result = (result shl 1) or ((value shr --remaining) and 1)
            }
            return result
        }
        fun finish() {
            require(remaining == 0 || value and ((1 shl remaining) - 1) == (1 shl remaining) - 1)
            remaining = 0
            require(at < bytes.size && bytes[at].toInt() and 255 == 255)
        }
        fun restart(index: Int) {
            finish()
            while (at < bytes.size && bytes[at].toInt() and 255 == 255) at++
            require(at < bytes.size && bytes[at++].toInt() and 255 == 0xd0 + index)
        }
    }
}
