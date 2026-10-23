package com.lolita.app.data.file

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bound decompression before POI allocates the workbook's shared strings and worksheets. */
internal object OrderFilePreflight {
    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 20 * 1024 * 1024) { "文件超过20MiB，请拆分后导入" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        var entries = 0
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (zip.nextEntry != null) {
                require(++entries <= 1024) { "订单文件内部内容过多，请重新导出" }
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expanded += count
                    require(
                        expanded <= 80L * 1024 * 1024 && expanded <= bytes.size * 100L + 1024 * 1024
                    ) {
                        "订单文件解压内容超过限制，请拆分后重新导出"
                    }
                }
            }
        }
        require(entries > 0) { "请选择有效的xlsx订单文件" }
        return bytes
    }
}
