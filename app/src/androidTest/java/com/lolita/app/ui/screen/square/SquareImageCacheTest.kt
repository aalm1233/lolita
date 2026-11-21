package com.lolita.app.ui.screen.square

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.gson.JsonObject
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.repository.ItemDraftRepository
import com.lolita.app.data.repository.ItemRepository
import com.lolita.app.data.repository.SquareRepository
import com.lolita.app.data.square.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Counts actual HTTP transfers, using synthetic images and an isolated cache/Room. */
class SquareImageCacheTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(target.cacheDir, "square-image-cache-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(target) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
    }
    private lateinit var db: LolitaDatabase
    private lateinit var drafts: ItemDraftRepository
    private lateinit var repo: SquareRepository
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private lateinit var loader: ImageLoader
    private val requests = ConcurrentHashMap<String, AtomicInteger>()
    private val photos = listOf(photo(0xff3355aa.toInt()), photo(0xff339955.toInt()))
    private val assets = photos.map { bytes ->
        val sha = sha(bytes)
        SquareAsset("images/$sha.png", sha, bytes.size.toLong(), 32, 40)
    }
    @Volatile private var revision = 1L
    @Volatile private var broken = false
    private val base get() = "http://127.0.0.1:${server.localPort}"
    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
    private fun photo(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray()
        }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun catalog(): SquareCatalog {
        val asset = assets[(revision - 1).toInt()]
        val props = mapOf("颜色分类" to "蓝色JSK", "尺码" to "M")
        val sku = SquareSku("sku", "p:sku", "p", props, "899", "全款", "未知", "JSK", "蓝色", "M", "单品",
            listOf(SquarePart("JSK", "JSK", "JSK", false, "蓝色", "M", "独立SKU", null)), emptyList(), false, props, "variant")
        return SquareCatalog(packageId = "cache-test", revision = revision, title = "缓存测试", capturedAt = "2026-11-21",
            shops = listOf(SquareShop("shop", "测试店", 1)),
            events = listOf(SquareEvent("event", revision, "shop", "design", "batch", "缓存测试", "", "上新", "开售", "未知", "899", "全款", "", "", "", "2026-11-21", listOf("p"))),
            products = listOf(SquareProduct("p", revision, "shop", "缓存测试", "https://example.com/product", "2026-11-21", "FULL", "全款", listOf(sku), JsonObject(), listOf(SquareImage(asset.path, "main", 0, "")))),
            assets = listOf(asset))
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, LolitaDatabase::class.java).build()
        drafts = ItemDraftRepository(db, ItemRepository(db.itemDao(), database = db, context = context))
        repo = SquareRepository(context, db, drafts, defaultServiceUrl = "")
        server = ServerSocket(0)
        worker = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val route = reader.readLine().split(' ')[1]
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    requests.computeIfAbsent(route) { AtomicInteger() }.incrementAndGet()
                    val body = when {
                        route == "/v1/health" -> """{"format":"lolita.square.api","schemaVersion":1,"serverId":"cache-test"}""".toByteArray()
                        route.startsWith("/v1/square/changes") -> {
                            val after = Regex("after=([0-9]+)").find(route)!!.groupValues[1].toLong()
                            val rows = if (after < revision) listOf(mapOf("seq" to revision, "catalog" to catalog())) else emptyList()
                            SquarePackage.gson.toJson(mapOf("serverId" to "cache-test", "cursor" to revision, "hasMore" to false, "changes" to rows)).toByteArray()
                        }
                        route.startsWith("/v1/images/") -> if (broken) "BAD".toByteArray() else photos[assets.indexOfFirst { route.startsWith("/v1/${it.path}?") }]
                        else -> error("Unexpected request: $route")
                    }
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nX-Content-SHA256: ${sha(body)}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            } catch (_: java.net.SocketException) { /* server closed by cleanup */ }
        }.apply { isDaemon = true; start() }
        loader = ImageLoader.Builder(context).components { add(SquareImageFetcher.Factory { repo }) }.build()
        io { repo.configureService(base); repo.refreshSilently() }
        assertNotNull(repo.catalog.value)
    }
    @After fun cleanup() {
        loader.shutdown(); server.close(); worker.join(1000); db.close(); root.deleteRecursively()
    }
    private fun count(asset: SquareAsset, variant: String) = requests["/v1/${asset.path}?variant=$variant"]?.get() ?: 0
    private fun decode(model: String): SuccessResult = io {
        val result = loader.execute(ImageRequest.Builder(context).data(model).size(32, 40).allowHardware(false).build())
        assertTrue("Image must decode: $result", result is SuccessResult)
        result as SuccessResult
    }

    @Test fun cachedThumbnailKeepsStableModelMemoryCacheAndOfflineRestart() {
        val asset = assets.first()
        decode(repo.thumbnail(asset.path))
        val model = repo.thumbnail(asset.path)
        assertTrue("Cached thumbnail should resolve directly to its persistent file", File(model).isFile)
        decode(model)
        repeat(5) {
            io { repo.refreshSilently() }
            assertEquals(model, repo.thumbnail(asset.path))
            assertEquals(DataSource.MEMORY_CACHE, decode(repo.thumbnail(asset.path)).dataSource)
        }
        assertEquals(1, count(asset, "thumbnail")); assertEquals(0, count(asset, "original"))
        io { repo.configureService("") }
        repo = SquareRepository(context, db, drafts, defaultServiceUrl = "")
        io { repo.load(); repo.refreshSilently() }
        assertEquals(model, repo.thumbnail(asset.path))
        loader.memoryCache?.clear()
        assertEquals(DataSource.DISK, decode(model).dataSource)
        assertEquals(1, count(asset, "thumbnail"))
    }

    @Test fun originalStaysIndependentAndTakesPrecedenceOffline() {
        val asset = assets.first()
        val thumb = io { repo.fetchImage(asset.path, true) }
        assertFalse(File(context.filesDir, "square_catalog/${asset.path}").exists())
        val original = io { repo.fetchImage(asset.path, false) }
        assertNotEquals(thumb, original); assertArrayEquals(photos.first(), original.readBytes())
        io { repo.configureService("") }
        repeat(5) {
            io { repo.refreshSilently() }
            assertEquals(original.absolutePath, repo.thumbnail(asset.path))
            assertEquals(original.absolutePath, repo.image(asset.path))
            assertEquals(original, io { repo.fetchImage(asset.path, false) })
            assertEquals(original, io { repo.fetchImage(asset.path, true) })
        }
        assertEquals(1, count(asset, "thumbnail")); assertEquals(1, count(asset, "original"))
    }

    @Test fun failedAndEmptyThumbnailsRetryWithoutReplacingGoodFiles() {
        val asset = assets.first()
        val before = repo.thumbnail(asset.path)
        broken = true
        try { io { repo.fetchImage(asset.path, true) }; fail("Bad image must fail") } catch (_: SquareNetworkException) { }
        val dest = File(context.filesDir, "square_catalog/thumbnails/${asset.sha256}.webp")
        assertFalse(dest.exists())
        dest.writeBytes(byteArrayOf())
        assertTrue(repo.thumbnail(asset.path).startsWith("lolita-square:"))
        io { repo.refreshSilently() }
        assertNotEquals(before, repo.thumbnail(asset.path))
        broken = false
        decode(repo.thumbnail(asset.path))
        assertEquals(dest.absolutePath, repo.thumbnail(asset.path))
        val bytes = dest.readBytes()
        broken = true
        io { repo.refreshSilently() }
        assertEquals(dest, io { repo.fetchImage(asset.path, true) })
        assertArrayEquals(bytes, dest.readBytes()); assertEquals(2, count(asset, "thumbnail"))
    }

    @Test fun concurrentDownloadsAndNewContentOnlyFetchMissingImage() {
        val old = assets.first()
        val files = io { coroutineScope { List(12) { async { repo.fetchImage(old.path, true) } }.awaitAll() } }
        assertEquals(1, files.distinct().size); assertEquals(1, count(old, "thumbnail"))
        val cached = files.first(); val bytes = cached.readBytes()
        revision = 2
        io { repo.refreshSilently() }
        val fresh = assets.last()
        assertEquals(fresh.path, repo.catalog.value!!.products.single().images.single().path)
        assertTrue(repo.thumbnail(fresh.path).startsWith("lolita-square:"))
        decode(repo.thumbnail(fresh.path))
        assertArrayEquals(bytes, cached.readBytes())
        assertEquals(1, count(old, "thumbnail")); assertEquals(1, count(fresh, "thumbnail"))
        assertEquals(0, count(old, "original") + count(fresh, "original"))
        assertEquals(0, io { db.itemDao().getItemCount() })
        assertEquals(0, io { db.priceDao().getAllPricesList().size })
    }
}
