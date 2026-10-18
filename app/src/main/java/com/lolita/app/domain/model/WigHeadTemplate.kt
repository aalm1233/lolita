package com.lolita.app.domain.model

import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/** A selected candidate is frozen per request; a catalog upgrade cannot replace it. */
data class WigHeadTemplateSnapshot(
    val id: String, val name: String, val version: Int, val sha256: String,
    val imagePath: String, val width: Int, val height: Int, val view: String = "FRONT",
    val headScale: String = "候选构图，尚未统一定版"
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{1,64}")) && name.isNotBlank() && name.length <= 100 && version > 0)
        require(sha256.matches(Regex("[a-f0-9]{64}")) && imagePath.isNotBlank()) { "头模图片身份无效" }
        require(width in 1..30000 && height in 1..30000 && view == "FRONT" && headScale.isNotBlank()) { "头模构图资料无效" }
    }
    fun validateImage() {
        require(File(imagePath).let { it.isFile && it.canRead() && it.length() > 0 } && imageSha256(imagePath) == sha256) {
            "所选头模资源缺失或校验失败，请重新选择；原任务不会替换模板"
        }
    }
    companion object {
        fun decode(json: String): WigHeadTemplateSnapshot {
            val row = JsonParser.parseString(json).asJsonObject
            row.requireFields("id", "name", "version", "sha256", "imagePath", "width", "height", "view", "headScale")
            fun integer(key: String): Int = row[key].asJsonPrimitive.let { require(it.isNumber); it.asBigDecimal.intValueExact() }
            return WigHeadTemplateSnapshot(row.strictString("id"), row.strictString("name"), integer("version"), row.strictString("sha256"),
                row.strictString("imagePath"), integer("width"), integer("height"), row.strictString("view"), row.strictString("headScale"))
        }
    }
}

fun imageSha256(path: String): String = File(path).inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    digest.digest().joinToString("") { "%02x".format(it) }
}
