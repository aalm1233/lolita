package com.lolita.app.domain.usecase

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.lolita.app.data.file.BackupManager
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.file.TaobaoProductParser
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.*
import com.lolita.app.data.repository.BrandRepository
import com.lolita.app.data.square.*
import com.lolita.app.domain.model.CategorySemantic
import com.lolita.app.domain.model.ItemComposition
import com.lolita.app.domain.model.WardrobeFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AddSquareWishUseCase(private val context: Context, private val db: LolitaDatabase,
    private val beforeWrite: suspend (Int) -> Unit = {}) {
    private val journal get() = AtomicFile(File(context.noBackupFilesDir, "square-wish-images.json"))
    private fun journal(paths: List<String>) {
        val out = journal.startWrite()
        try { out.write(SquarePackage.gson.toJson(paths).toByteArray(Charsets.UTF_8)); journal.finishWrite(out) }
        catch (t: Throwable) { journal.failWrite(out); throw t }
    }

    /** Survives a kill during copy, after commit, or before the UI receives success. */
    suspend fun cleanupInterrupted() = BackupManager.withCatalogWrite {
        if (journal.baseFile.exists()) {
            val paths = journal.openRead().bufferedReader().use { SquarePackage.gson.fromJson(it, Array<String>::class.java).toList() }
            paths.forEach { ImageFileHelper.deleteImage(it) }
            journal.delete()
        }
    }

    private suspend fun wardrobe() = SquareWishWardrobe(db.itemDao().getAllItemsList(), db.orderImportDao().sources(), db.itemDraftDao().getAll())

    suspend operator fun invoke(c: SquareCatalog, request: SquareWishRequest, source: (String) -> String): SquareWishResult = withContext(Dispatchers.IO) {
        BackupManager.withCatalogWrite {
            val plans = SquareWishPlans.build(c, request)
            val initial = db.withTransaction { val state = wardrobe(); plans.map { SquareWishPlans.match(it, state) } }
            val drafts = initial.flatMap { it.drafts }.distinct()
            if (drafts.isNotEmpty()) return@withCatalogWrite SquareWishResult(draftIds = drafts)
            val related = initial.filter { it.exact.isEmpty() }.flatMap { it.related }.distinct()
            if (related.isNotEmpty() && !request.allowRelated) return@withCatalogWrite SquareWishResult(itemIds = related, related = true)
            val newPlans = plans.filterIndexed { index, _ -> initial[index].exact.isEmpty() }
            val paths = newPlans.flatMap { it.paths }.distinct()
            val assets = c.assets.associateBy { it.path }
            require(paths.sumOf { assets.getValue(it).bytes } <= SquarePackage.MAX_PRODUCT) { "所选图片超过80MiB，请减少物品后重试" }
            val allocated = mutableListOf<String>(); val copies = linkedMapOf<String, String>()
            try {
                journal(emptyList())
                paths.forEach { path ->
                    val original = File(source(path)); val asset = assets.getValue(path)
                    require(original.isFile && original.length() == asset.bytes && sha256(original) == asset.sha256) { "原图不完整，请重新导入资料包" }
                    copies[path] = ImageFileHelper.copyExistingImageToInternalStorage(context, original.absolutePath) { dest ->
                        allocated.add(dest); journal(allocated)
                    }
                    require(ImageFileHelper.isOwnedReadableProductImage(context, copies.getValue(path))) { "图片无法完整读取，请重新导入资料包" }
                }
                db.withTransaction {
                    val state = wardrobe(); val matches = plans.map { SquareWishPlans.match(it, state) }
                    val draftIds = matches.flatMap { it.drafts }.distinct()
                    if (draftIds.isNotEmpty()) return@withTransaction SquareWishResult(draftIds = draftIds)
                    val overlap = matches.filter { it.exact.isEmpty() }.flatMap { it.related }.distinct()
                    if (overlap.isNotEmpty() && !request.allowRelated) return@withTransaction SquareWishResult(itemIds = overlap, related = true)
                    val ids = mutableListOf<Long>(); var added = 0
                    plans.forEachIndexed { index, plan ->
                        if (matches[index].exact.isNotEmpty()) { ids.addAll(matches[index].exact); return@forEachIndexed }
                        beforeWrite(added)
                        val snapshot = plan.snapshot; val member = snapshot.members.singleOrNull()
                        val brand = resolveBrand(snapshot.shopId, snapshot.shopName)
                        val category = resolveCategory(member?.category, snapshot.members.all { m -> plan.snapshot.members.size > 1 && m.category in setOf("KC", "胸针", "其他配饰", "发带", "Bonnet", "手袖") })
                        val imagePaths = plan.paths.map { copies.getValue(it) }
                        SquareWishSnapshot.decode(snapshot.encode(), imagePaths.size)
                        val photos = snapshot.members.flatMap { it.photos }.distinct().map { imagePaths[it] }
                        val documents = snapshot.members.flatMap { it.documents }.distinct().map { imagePaths[it] }
                        val id = db.itemDao().insertItem(Item(brandId = brand, categoryId = category,
                            name = "${snapshot.title} · " + snapshot.members.joinToString("＋") { it.name },
                            description = "", status = ItemStatus.WISHED, source = "淘宝",
                            colors = listOfNotNull(member?.color?.takeIf { it.isNotEmpty() }), size = member?.size?.takeIf { it.isNotEmpty() },
                            imageUrls = photos, sizeChartImageUrl = documents.firstOrNull(), sizeChartImageUrls = documents.drop(1),
                            composition = if (snapshot.grouped) ItemComposition.SET else ItemComposition.SINGLE, importToken = snapshot.identity()))
                        db.orderImportDao().insertSources(listOf(ItemSourceRecord(itemId = id, kind = "SQUARE_WISH", publicUrl = snapshot.members.first().origins.first().url,
                            snapshotJson = snapshot.encode(), appliedFields = listOf("name", "brandId", "categoryId", "colors", "size", "photos", "details"), imagePaths = imagePaths)))
                        ids.add(id); added++
                    }
                    SquareWishResult(ids.distinct(), added)
                }
            } finally {
                withContext(NonCancellable) {
                    allocated.forEach { ImageFileHelper.deleteImage(it) }
                    journal.delete()
                }
            }
        }
    }

    private suspend fun resolveBrand(shopId: String, name: String): Long {
        val aliases = when (shopId) {
            "puppets" -> listOf(name, "古典玩偶（Classical Puppets）", "Classical Puppets", "Puppets and Doll")
            "lilizi" -> listOf(name, "莉莉子 Lolita", "莉莉子lolita店")
            "neo" -> listOf(name, "新路德维希（Neo Ludwig）", "Neo Ludwig")
            else -> listOf(name)
        }
        val brands = db.brandDao().getAllBrandsList()
        val match = TaobaoProductParser.matchBrand(brands.map { it.name }, aliases)
        return brands.singleOrNull { it.name == match }?.id ?: BrandRepository(db.brandDao(), db.itemDao(), db).ensureUnsetBrand()
    }
    private suspend fun resolveCategory(name: String?, accessoryGroup: Boolean): Long {
        val semantic = CategorySemantic.entries.singleOrNull { it.label.equals(name, true) || it.name.equals(name, true) } ?: CategorySemantic.UNCONFIRMED
        val categories = db.categoryDao().getAllCategoriesList()
        categories.firstOrNull { it.categorySemantic == semantic }?.let { return it.id }
        var label = semantic.label; var suffix = 1
        while (categories.any { it.name == label }) label = "${semantic.label}（${suffix++}）"
        return db.categoryDao().insertCategory(Category(name = label, categorySemantic = semantic,
            group = if (semantic.family == WardrobeFamily.ACCESSORY || accessoryGroup) CategoryGroup.ACCESSORY else CategoryGroup.CLOTHING,
            defaultWearRole = semantic.defaultWearRole))
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(65536); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
