package com.lolita.app.data.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareImageIntents {
    fun create(context: Context, files: List<File>): Intent {
        require(files.isNotEmpty() && files.all { it.isFile }) { "分享图片尚未生成" }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(context.contentResolver, "穿搭分享", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
