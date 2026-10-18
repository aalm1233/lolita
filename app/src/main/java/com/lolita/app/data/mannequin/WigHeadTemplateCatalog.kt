package com.lolita.app.data.mannequin

import android.content.Context
import com.google.gson.JsonParser
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.domain.model.WigHeadTemplateSnapshot
import com.lolita.app.domain.model.imageSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class WigHeadTemplate(val id: String, val name: String, val version: Int, val sha256: String,
    val assetPath: String, val width: Int, val height: Int, val view: String = "FRONT", val byteSize: Long = 0) {
    val previewPath: String get() = "file:///android_asset/$assetPath"
    val statusLabel: String get() = "候选 · 待真实假发评审"
}

/** Offline only. Selecting or previewing a candidate never starts a paid operation. */
class WigHeadTemplateCatalog(private val context: Context) {
    val templates: List<WigHeadTemplate> by lazy {
        context.assets.open("wig-heads/catalog.json").bufferedReader().use { reader ->
            JsonParser.parseReader(reader).asJsonArray.map { value -> value.asJsonObject.let { row ->
                WigHeadTemplate(row["id"].asString, row["name"].asString, row["version"].asInt, row["sha256"].asString,
                    row["assetPath"].asString, row["width"].asInt, row["height"].asInt, byteSize = row["byteSize"].asLong)
            } }
        }
    }

    companion object {
        private fun preference(context: Context) = android.util.AtomicFile(File(context.noBackupFilesDir, "wig-head-template-preference-v1"))
        fun readPreference(context: Context): WigHeadTemplateSnapshot? {
            val file = preference(context)
            if (!file.baseFile.exists()) return null
            return file.openRead().bufferedReader().use { WigHeadTemplateSnapshot.decode(it.readText()) }
        }
        fun writePreference(context: Context, snapshot: WigHeadTemplateSnapshot?) {
            val file = preference(context)
            if (snapshot == null) { file.delete(); return }
            val output = file.startWrite()
            try {
                output.write(com.google.gson.Gson().toJson(snapshot.copy(imagePath = "preference-only")).toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (failure: Throwable) { file.failWrite(output); throw failure }
        }
    }

    suspend fun snapshot(id: String): WigHeadTemplateSnapshot = withContext(Dispatchers.IO) {
        val template = requireNotNull(templates.firstOrNull { it.id == id }) { "头模候选已下架，请重新选择" }
        // Reuse the verified immutable bytes already owned by this app, including older task inputs.
        val root = File(context.filesDir, "images")
        val existing = root.listFiles()?.firstOrNull { file -> file.isFile && file.extension == "png" && file.length() == template.byteSize &&
            runCatching { imageSha256(file.absolutePath) == template.sha256 }.getOrDefault(false) }
        val path = existing?.absolutePath ?: ImageFileHelper.copyAssetToInternalStorage(context, template.assetPath)
        try {
            WigHeadTemplateSnapshot(template.id, template.name, template.version, template.sha256, path,
                template.width, template.height, template.view).also { it.validateImage() }
        } catch (failure: Throwable) {
            if (existing == null) ImageFileHelper.cleanupUnreferencedImages(listOf(path))
            throw failure
        }
    }
}
