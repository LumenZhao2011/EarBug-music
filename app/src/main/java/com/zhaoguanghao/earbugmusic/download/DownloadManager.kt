package com.zhaoguanghao.earbugmusic.download

import android.content.Context
import com.zhaoguanghao.earbugmusic.model.Platform
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.parser.ParserRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 全局下载管理器：顺序队列、实时进度、取消/删除、历史持久化。
 */
object DownloadManager {

    private const val PREFS = "download_history"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seq = AtomicLong(0)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private val queue = Channel<Pair<Context, DownloadTask>>(Channel.UNLIMITED)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val lastEmit = ConcurrentHashMap<Long, Long>()
    private var appContext: Context? = null

    init {
        scope.launch {
            for ((ctx, task) in queue) {
                process(ctx, task)
            }
        }
    }

    fun enqueue(context: Context, song: Song, quality: Quality = Quality.STANDARD) {
        val app = context.applicationContext
        appContext = app
        val task = DownloadTask(id = seq.incrementAndGet(), song = song, requestQuality = quality)
        _tasks.update { it + task }
        persist()
        queue.trySend(app to task)
    }

    fun enqueueAll(context: Context, songs: List<Song>, quality: Quality = Quality.STANDARD) {
        songs.forEach { enqueue(context, it, quality) }
    }

    /** 该任务是否已有本地文件（含下载中的临时文件） */
    fun hasLocalFile(task: DownloadTask): Boolean {
        val ctx = appContext ?: return false
        val loc = task.location
            ?: com.zhaoguanghao.earbugmusic.util.LocalLibrary.get(ctx, task.song.id)
        if (loc.isNullOrBlank()) {
            val safe = safeName(task.song)
            return File(ctx.cacheDir, "dl").listFiles()?.any { it.name.startsWith(safe) } == true
        }
        return if (loc.startsWith("content://")) {
            runCatching {
                ctx.contentResolver.openFileDescriptor(android.net.Uri.parse(loc), "r")?.use { true } ?: false
            }.getOrDefault(false)
        } else {
            File(loc).exists()
        }
    }

    /** 删除任务；deleteFile=true 时同时删除本地文件 */
    fun remove(task: DownloadTask, deleteFile: Boolean) {
        val ctx = appContext
        cancelled.add(task.id)
        if (deleteFile && ctx != null) {
            val loc = task.location
                ?: com.zhaoguanghao.earbugmusic.util.LocalLibrary.get(ctx, task.song.id)
            if (!loc.isNullOrBlank()) {
                runCatching {
                    if (loc.startsWith("content://")) {
                        ctx.contentResolver.delete(android.net.Uri.parse(loc), null, null)
                    } else {
                        File(loc).delete()
                    }
                }
            }
            com.zhaoguanghao.earbugmusic.util.LocalLibrary.remove(ctx, task.song.id)
            val safe = safeName(task.song)
            File(ctx.cacheDir, "dl").listFiles()
                ?.filter { it.name.startsWith(safe) }
                ?.forEach { it.delete() }
        }
        _tasks.update { list -> list.filterNot { it.id == task.id } }
        persist()
    }

    fun unfinishedCount(): Int = _tasks.value.count {
        it.status == DownloadStatus.WAITING || it.status == DownloadStatus.PARSING ||
            it.status == DownloadStatus.DOWNLOADING
    }

    // ==================== 持久化 ====================

    fun restore(context: Context) {
        val app = context.applicationContext
        appContext = app
        val raw = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tasks", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DownloadTask>()
            var maxId = 0L
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0)
                maxId = maxOf(maxId, id)
                val statusName = o.optString("status", "FAILED")
                val status = runCatching { DownloadStatus.valueOf(statusName) }.getOrDefault(DownloadStatus.FAILED)
                val finished = status == DownloadStatus.SUCCESS
                val song = Song(
                    platform = Platform.NETEASE,
                    id = o.optString("songId"),
                    name = o.optString("name"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    coverUrl = o.optString("cover"),
                    durationMs = o.optLong("duration", 0)
                )
                list.add(
                    DownloadTask(
                        id = id,
                        song = song,
                        status = if (finished) DownloadStatus.SUCCESS else DownloadStatus.FAILED,
                        progress = if (finished) 1f else 0f,
                        requestQuality = runCatching {
                            Quality.valueOf(o.optString("requestQuality", "STANDARD"))
                        }.getOrDefault(Quality.STANDARD),
                        quality = o.optString("quality"),
                        filePath = o.optString("filePath").takeIf { it.isNotBlank() },
                        location = o.optString("location").takeIf { it.isNotBlank() },
                        error = if (finished) null else "应用重启，请重新下载"
                    )
                )
            }
            _tasks.value = list
            seq.set(maxId)
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        _tasks.value.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("songId", t.song.id)
                    put("name", t.song.name)
                    put("artist", t.song.artist)
                    put("album", t.song.album)
                    put("cover", t.song.coverUrl)
                    put("duration", t.song.durationMs)
                    put("status", t.status.name)
                    put("quality", t.quality)
                    put("requestQuality", t.requestQuality.name)
                    put("filePath", t.filePath ?: "")
                    put("location", t.location ?: "")
                }
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("tasks", arr.toString()).apply()
    }

    // ==================== 下载流程 ====================

    private suspend fun process(context: Context, task: DownloadTask) {
        if (cancelled.contains(task.id)) return
        try {
            update(task) { it.status = DownloadStatus.PARSING }
            val result = ParserRegistry.forPlatform(task.song.platform).resolve(task.song, task.requestQuality)
            if (cancelled.contains(task.id)) return
            update(task) {
                it.quality = result.quality
                it.status = DownloadStatus.DOWNLOADING
            }
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var emaSpeed = 0.0
            val saved = DownloadHelper.download(context, task.song, result) { done, total ->
                if (cancelled.contains(task.id)) throw CancellationException("cancelled")
                val now = System.currentTimeMillis()
                // 限流：每任务 500ms 最多一次 UI 刷新，避免高频刷新吞掉按钮点击
                if (now - (lastEmit[task.id] ?: 0L) < 500) return@download
                lastEmit[task.id] = now
                val elapsed = now - lastTime
                val progress = if (total > 0) done.toFloat() / total else 0f
                if (elapsed >= 800) {
                    // 1 秒级采样 + 指数平滑，速度不跳
                    val instant = (done - lastBytes) * 1000.0 / elapsed
                    emaSpeed = if (emaSpeed <= 0.0) instant else emaSpeed * 0.5 + instant * 0.5
                    val speed = emaSpeed.toLong().coerceIn(0L, 200L * 1024 * 1024)
                    update(task) { t ->
                        t.doneBytes = done
                        t.totalBytes = total
                        t.progress = progress
                        t.speed = speed
                    }
                    lastBytes = done
                    lastTime = now
                } else {
                    update(task) { t ->
                        t.doneBytes = done
                        t.totalBytes = total
                        t.progress = progress
                    }
                }
            }
            if (cancelled.contains(task.id)) {
                cancelled.remove(task.id)
                return
            }
            com.zhaoguanghao.earbugmusic.util.LocalLibrary.put(context, task.song.id, saved.location)
            lastEmit.remove(task.id)
            update(task) {
                it.status = DownloadStatus.SUCCESS
                it.progress = 1f
                it.filePath = saved.display
                it.location = saved.location
            }
            persist()
        } catch (e: Exception) {
            if (e is CancellationException) {
                cancelled.remove(task.id)
                return
            }
            if (cancelled.contains(task.id)) {
                cancelled.remove(task.id)
                return
            }
            lastEmit.remove(task.id)
            update(task) {
                it.status = DownloadStatus.FAILED
                it.error = e.message
            }
            persist()
        }
    }

    private fun safeName(song: Song): String =
        song.fileName().replace(Regex("[\\\\/:*?\"<>|]"), "_")

    /** 用副本更新，保证 StateFlow 能感知变化 */
    private fun update(task: DownloadTask, block: (DownloadTask) -> Unit) {
        _tasks.update { list ->
            list.map {
                if (it.id == task.id) it.copy().also(block) else it
            }
        }
    }
}
