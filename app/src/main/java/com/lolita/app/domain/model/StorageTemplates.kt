package com.lolita.app.domain.model

import android.content.Context
import com.google.gson.Gson
import com.lolita.app.data.file.ImageFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class StorageTemplate(
    val id: String,
    val name: String,
    val assetPath: String,
    val sha256: String,
    val description: String = "收纳位置示意插图",
    val version: Int = 1
)

/** Offline illustrations are offered to users; reading this catalog never creates locations. */
object StorageTemplates {
    const val CATALOG_PATH = "builtin/storage/catalog.json"
    private data class Catalog(val formatVersion: Int, val templates: List<StorageTemplate>)

    suspend fun read(context: Context): List<StorageTemplate> = withContext(Dispatchers.IO) {
        val catalog = context.assets.open(CATALOG_PATH).bufferedReader().use {
            Gson().fromJson(it, Catalog::class.java)
        }
        require(catalog.formatVersion == 1 && catalog.templates.size == 4 &&
            catalog.templates.map { it.id }.distinct().size == 4) { "内置收纳素材清单无效" }
        catalog.templates.forEach {
            require(it.id.isNotBlank() && it.name.isNotBlank() && it.version > 0 &&
                it.sha256.matches(Regex("[a-f0-9]{64}")) &&
                it.assetPath.startsWith("builtin/storage/") && it.assetPath.split('/').none { p -> p == ".." || p == "." || p.isBlank() }) {
                "内置收纳素材信息无效"
            }
        }
        catalog.templates
    }

    suspend fun copyVerifiedImage(context: Context, template: StorageTemplate): String {
        val digest = withContext(Dispatchers.IO) { context.assets.open(template.assetPath).use { input ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
            hash.digest().joinToString("") { "%02x".format(it) }
        } }
        require(digest == template.sha256) { "收纳示意图校验失败，请更新应用" }
        return ImageFileHelper.copyAssetToInternalStorage(context, template.assetPath)
    }
}
