package com.lolita.app.data.recognition

import android.content.Context
import androidx.room.withTransaction
import com.lolita.app.data.file.ImageFileHelper
import com.lolita.app.data.local.LolitaDatabase
import com.lolita.app.data.local.entity.RecognitionTask
import com.lolita.app.data.remote.RecognitionApiResult
import com.lolita.app.data.remote.RecognitionApiFailure
import com.lolita.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import android.graphics.BitmapFactory

/** Requests belong to an application scope; process recreation interrupts rather than resends. */
class RecognitionCoordinator(private val database: LolitaDatabase,private val context: Context,
    private val scope: CoroutineScope,
    private val readKey: ()->String,
    private val keyRevision: ()->Int,
    private val recognize: suspend (String,RecognitionRequest,List<ByteArray>)->RecognitionResult,
    private val providerReady: ()->Boolean = { false },
    private val selectedProvider: ()->RecognitionProvider = { RecognitionProvider.VOLCENGINE_ARK },
    private val readProviderKey: ((RecognitionProvider)->String)? = null,
    private val recognizeWithMetadata: (suspend (String,RecognitionRequest,List<ByteArray>)->RecognitionApiResult)? = null) {
    private val mutex=Mutex()
    private val jobs=mutableMapOf<String,Job>()
    private val recovery=scope.async { database.recognitionDao().interruptUnfinished(System.currentTimeMillis()) }

    suspend fun submit(confirmation: RecognitionTask,expectedKeyRevision: Int) {
        require(providerReady()) { "识图服务正在重新选型，暂未开放付费提交" }
        recovery.await()
        mutex.withLock {
            require(jobs.values.none { it.isActive }) { "已有识图正在进行，请等待结果" }
            require(keyRevision()==expectedKeyRevision) { "识图密钥已变更，请重新确认" }
            val provider=confirmation.request().provider
            require(selectedProvider()==provider) { "识图服务已变更，请重新确认" }
            val key=readProviderKey?.invoke(provider) ?: readKey()
            require(keyRevision()==expectedKeyRevision && selectedProvider()==provider) { "识图配置已变更，请重新确认" }
            require(key.isNotBlank()) { "请先配置独立的识图 API Key" }
            val task=database.withTransaction {
                val task=database.recognitionDao().get(confirmation.id) ?: error("识图草稿已关闭")
                require(task==confirmation && task.status==RecognitionStatus.PREPARED && !task.requestSent) { "识图内容已变更，请重新确认" }
                val draft=database.itemDraftDao().get(task.draftId) ?: error("编辑草稿已关闭")
                require(draft.targetItemId==task.targetItemId)
                task.targetItemId?.let { require(database.itemDao().getItemById(it)!=null) { "物品已删除" } }
                task.copy(status=RecognitionStatus.RUNNING,requestSent=true,updatedAt=System.currentTimeMillis()).also { database.recognitionDao().update(it) }
            }
            jobs[task.id]=scope.launch(start=CoroutineStart.LAZY) { execute(task,key) }.also { it.start() }
        }
    }

    private suspend fun execute(task: RecognitionTask,key: String) {
        try {
            val request=task.request()
            val bytes=withContext(Dispatchers.IO) {
                task.regionImagePaths.mapIndexed { index,path ->
                    require(ImageFileHelper.isOwnedReadableImage(context,path)) { "识图区域资源缺失，请重新框选" }
                    val file=File(path)
                    require(file.length() in 1..(3L*1024*1024))
                    file.readBytes().also { bytes ->
                        val region=request.regions[index]
                        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                        require(bytes.size in 1..(3*1024*1024) && hash==region.sha256) { "识图区域内容已变化，请重新框选" }
                        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                        require(bounds.outWidth==region.width && bounds.outHeight==region.height) { "识图区域尺寸已变化，请重新框选" }
                    }
                }
            }
            val response=recognizeWithMetadata?.invoke(key,request,bytes)
                ?: RecognitionApiResult(recognize(key,request,bytes),request.provider,request.model)
            require(response.provider==request.provider && response.actualModel.matches(Regex("[A-Za-z0-9._:/-]{1,160}")))
            val result=response.result
            database.withTransaction {
                val current=database.recognitionDao().get(task.id)
                if(current?.status==RecognitionStatus.RUNNING) {
                    database.recognitionDao().update(current.copy(status=RecognitionStatus.AWAITING_REVIEW,resultJson=result.encode(),actualModel=response.actualModel,error=null,updatedAt=System.currentTimeMillis()))
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable+Dispatchers.IO) {
                database.withTransaction {
                    val current=database.recognitionDao().get(task.id)
                    if(current?.status==RecognitionStatus.RUNNING) {
                        val text=when(failure) {
                            is RecognitionApiFailure -> failure.kind.message
                            is CancellationException -> "本地等待已取消；原请求可能仍处理或计费，不会自动重试"
                            else -> "识图未完成，请核对区域与网络后主动重新提交；原请求可能已计费"
                        }
                        database.recognitionDao().update(current.copy(status=if(failure is CancellationException) RecognitionStatus.INTERRUPTED else RecognitionStatus.FAILED,error=text,updatedAt=System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    suspend fun cancelForDraft(draftId: String) {
        database.recognitionDao().getAll().filter { it.draftId==draftId }.forEach { cancel(it.id) }
    }

    suspend fun cancel(id: String) = mutex.withLock { jobs[id]?.cancelAndJoin() }

    suspend fun <T> withRestorePaused(block: suspend ()->T): T = mutex.withLock {
        recovery.await()
        jobs.values.toList().forEach { it.cancelAndJoin() }
        block()
    }
}
