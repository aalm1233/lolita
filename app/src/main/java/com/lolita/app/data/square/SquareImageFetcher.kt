package com.lolita.app.data.square

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.lolita.app.data.repository.SquareRepository
import okio.buffer
import okio.source

/** All Square views, including full-screen zoom, use verified persistent files. */
class SquareImageFetcher(private val uri: Uri, private val options: Options,
    private val repository: SquareRepository) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val file = repository.fetchImage(uri.path.orEmpty().removePrefix("/"), uri.getQueryParameter("size") == "thumbnail")
        return SourceResult(ImageSource(file.source().buffer(), options.context), null, DataSource.DISK)
    }
    class Factory(private val repository: () -> SquareRepository) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "lolita-square" && data.host == "asset") SquareImageFetcher(data, options, repository()) else null
    }
}
