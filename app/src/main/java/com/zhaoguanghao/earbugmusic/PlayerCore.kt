package com.zhaoguanghao.earbugmusic

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.parser.NeteaseParser
import com.zhaoguanghao.earbugmusic.parser.ParserRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 全局播放核心：MediaPlayer + 队列 + 歌词时间轴。
 * Activity（播放页）与前台服务（通知栏）共用同一状态。
 */
object PlayerCore {

    /** 播放模式 */
    enum class PlayMode(val label: String) {
        SEQUENCE("顺序播放"),
        LOOP_ONE("单曲循环"),
        SINGLE("仅单次"),
        SHUFFLE("随机播放")
    }

    data class State(
        val song: Song? = null,
        val quality: String = "",
        val playing: Boolean = false,
        val loading: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val lyricText: String = "",
        val status: String = "",
        val playMode: PlayMode = PlayMode.SEQUENCE
    )

    val state = MutableStateFlow(State())

    /** 由 Activity/Service 注入，用于读取本地文件 */
    var appContext: Context? = null

    var playMode: PlayMode = PlayMode.SEQUENCE
        private set

    /** 注入上下文并恢复上次的播放模式 */
    fun attachContext(ctx: Context) {
        appContext = ctx.applicationContext
        val saved = ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            .getString("play_mode", null)
        if (saved != null) {
            PlayMode.entries.firstOrNull { it.name == saved }?.let { playMode = it }
            state.value = state.value.copy(playMode = playMode)
        }
    }

    fun setPlayMode(mode: PlayMode) {
        playMode = mode
        state.value = state.value.copy(playMode = mode)
        appContext?.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            ?.edit()?.putString("play_mode", mode.name)?.apply()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var queue: List<Song> = emptyList()
    private var index = -1
    private var quality: Quality = Quality.LOSSLESS
    /** 一行歌词：原文 + 可选翻译 */
    data class LyricLine(val timeMs: Long, val text: String, val trans: String?)

    private var lyric: List<LyricLine> = emptyList()
    private var loadJob: Job? = null
    private var pendingSeekMs: Long = -1L

    private val ticker = object : Runnable {
        override fun run() {
            val player = mp ?: return
            try {
                if (player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    state.value = state.value.copy(
                        positionMs = pos,
                        durationMs = player.duration.toLong(),
                        playing = true,
                        lyricText = lyricAt(pos)
                    )
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 400)
        }
    }

    fun setQueue(list: List<Song>) {
        val current = state.value.song
        queue = list
        index = if (current != null) queue.indexOfFirst { it.id == current.id } else -1
    }

    fun currentSong(): Song? = state.value.song ?: queue.getOrNull(index)

    /** 是否已有底层播放器（停止/恢复后为 false） */
    fun hasPlayer(): Boolean = mp != null

    fun playAt(i: Int, q: Quality) {
        if (i !in queue.indices) return
        index = i
        load(queue[i], q, updateIndex = false)
    }

    fun load(song: Song, q: Quality, updateIndex: Boolean = true) {
        quality = q
        if (updateIndex) {
            val i = queue.indexOf(song)
            if (i >= 0) index = i
        }
        lyric = emptyList()
        // 优先播放本地已下载文件
        val local = appContext?.let {
            com.zhaoguanghao.earbugmusic.util.LocalLibrary.get(it, song.id)
        }
        if (local != null) {
            state.value = State(song = song, loading = true, status = "加载本地文件…", playMode = playMode)
            releasePlayer()
            loadJob = scope.launch {
                runCatching {
                    val lrc = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        NeteaseParser.lyric(song.id)
                    } ?: ""
                    lyric = parseLyric(lrc)
                }
                startLocal(local, song)
            }
            return
        }
        state.value = State(song = song, loading = true, status = "解析中…", playMode = playMode)
        releasePlayer()
        loadJob = scope.launch {
            try {
                val result = ParserRegistry.forPlatform(song.platform).resolve(song, quality)
                state.value = state.value.copy(quality = result.quality, status = "准备播放…")
                runCatching {
                    val lrc = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        NeteaseParser.lyric(song.id)
                    } ?: ""
                    lyric = parseLyric(lrc)
                }
                startPlay(result.url)
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, status = e.message ?: "解析失败")
            }
        }
    }

    /** 播放本地文件（绝对路径或 content://） */
    private fun startLocal(location: String, song: Song) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            if (location.startsWith("content://")) {
                val ctx = appContext ?: throw Exception("上下文丢失")
                player.setDataSource(ctx, Uri.parse(location))
            } else {
                player.setDataSource(location)
            }
            player.setOnPreparedListener { p ->
                if (pendingSeekMs >= 0) {
                    p.seekTo(pendingSeekMs.toInt())
                    pendingSeekMs = -1L
                }
                p.start()
                state.value = state.value.copy(
                    playing = true,
                    loading = false,
                    quality = "本地文件",
                    durationMs = p.duration.toLong(),
                    status = "播放中（本地）"
                )
                handler.post(ticker)
            }
            player.setOnErrorListener { _, _, _ ->
                state.value = state.value.copy(playing = false, loading = false, status = "本地文件播放失败")
                true
            }
            player.setOnCompletionListener {
                state.value = state.value.copy(playing = false)
                onTrackFinished()
            }
            player.prepareAsync()
            mp = player
        } catch (e: Exception) {
            state.value = state.value.copy(loading = false, status = "本地文件无法播放：" + (e.message ?: ""))
        }
    }

    private fun startPlay(url: String) {
        releasePlayer()
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener { p ->
                if (pendingSeekMs >= 0) {
                    p.seekTo(pendingSeekMs.toInt())
                    pendingSeekMs = -1L
                }
                p.start()
                state.value = state.value.copy(
                    playing = true,
                    loading = false,
                    durationMs = p.duration.toLong(),
                    status = "播放中"
                )
                handler.post(ticker)
            }
            player.setOnErrorListener { _, _, _ ->
                state.value = state.value.copy(playing = false, loading = false, status = "播放失败（格式可能不被系统支持）")
                true
            }
            player.setOnCompletionListener {
                state.value = state.value.copy(playing = false)
                onTrackFinished()
            }
            player.prepareAsync()
            mp = player
        } catch (e: Exception) {
            state.value = state.value.copy(loading = false, status = "播放失败：" + (e.message ?: ""))
        }
    }

    /** 一曲播完：按播放模式决定下一步 */
    private fun onTrackFinished() {
        if (queue.isEmpty()) return
        when (playMode) {
            PlayMode.LOOP_ONE -> playAt(index, quality)
            PlayMode.SINGLE -> {
                // 仅播放一次，播完停止
            }
            PlayMode.SEQUENCE -> next()
            PlayMode.SHUFFLE -> {
                if (queue.size <= 1) {
                    playAt(index, quality)
                } else {
                    var n = kotlin.random.Random.nextInt(queue.size)
                    if (n == index) n = (n + 1) % queue.size
                    playAt(n, quality)
                }
            }
        }
    }

    fun toggle() {
        if (state.value.loading) return
        val player = mp
        if (player == null) {
            // 冷启动恢复后第一次点播放：按上次位置继续
            currentSong()?.let { load(it, quality, updateIndex = false) }
            return
        }
        if (player.isPlaying) {
            player.pause()
            state.value = state.value.copy(playing = false)
        } else {
            player.start()
            state.value = state.value.copy(playing = true)
        }
    }

    /** 主动暂停（耳机拔出等场景） */
    fun pause() {
        val player = mp ?: return
        try {
            if (player.isPlaying) player.pause()
        } catch (_: Exception) {
        }
        state.value = state.value.copy(playing = false)
        saveSession()
    }

    fun next() {
        if (queue.isEmpty()) return
        playAt((index + 1) % queue.size, quality)
    }

    fun prev() {
        if (queue.isEmpty()) return
        playAt((index - 1 + queue.size) % queue.size, quality)
    }

    fun seekTo(ms: Long) {
        mp?.seekTo(ms.toInt())
        state.value = state.value.copy(positionMs = ms, lyricText = lyricAt(ms))
    }

    fun release() {
        saveSession()
        loadJob?.cancel()
        releasePlayer()
        state.value = State()
    }

    /** 停止播放：保留当前歌曲信息，方便再次播放 */
    fun stop() {
        saveSession()
        loadJob?.cancel()
        releasePlayer()
        state.value = state.value.copy(
            playing = false,
            loading = false,
            positionMs = 0L,
            status = "已停止"
        )
    }

    // ==================== 上次播放记忆 ====================

    private const val SESSION_PREF = "player_session"

    fun saveSession() {
        val ctx = appContext ?: return
        val st = state.value
        val song = st.song ?: return
        val json = org.json.JSONObject().apply {
            put("id", song.id)
            put("name", song.name)
            put("artist", song.artist)
            put("album", song.album)
            put("cover", song.coverUrl)
            put("duration", song.durationMs)
        }
        ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE).edit()
            .putString("song", json.toString())
            .putLong("pos", st.positionMs)
            .putString("quality", quality.name)
            .apply()
    }

    /** 冷启动恢复上次播放的歌曲（暂停在上次位置） */
    fun restoreSession() {
        val ctx = appContext ?: return
        if (state.value.song != null) return
        val prefs = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString("song", null) ?: return
        val o = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return
        val song = Song(
            platform = com.zhaoguanghao.earbugmusic.model.Platform.NETEASE,
            id = o.optString("id"),
            name = o.optString("name"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            coverUrl = o.optString("cover"),
            durationMs = o.optLong("duration", 0)
        )
        if (song.id.isBlank()) return
        val pos = prefs.getLong("pos", 0L)
        quality = runCatching {
            Quality.valueOf(prefs.getString("quality", "STANDARD") ?: "STANDARD")
        }.getOrDefault(Quality.STANDARD)
        queue = listOf(song)
        index = 0
        pendingSeekMs = pos
        state.value = State(
            song = song,
            positionMs = pos,
            durationMs = song.durationMs,
            playMode = playMode,
            status = "上次播放 · 点击继续"
        )
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        mp?.release()
        mp = null
    }

    // ==================== 歌词 ====================

    private fun parseLyric(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (line in lrc.lines()) {
            val m = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]").find(line) ?: continue
            val min = m.groupValues[1].toIntOrNull() ?: continue
            val sec = m.groupValues[2].toIntOrNull() ?: continue
            val msRaw = m.groupValues[3]
            val ms = when (msRaw.length) {
                0 -> 0L
                1 -> msRaw.toLong() * 100
                2 -> msRaw.toLong() * 10
                else -> msRaw.toLong()
            }
            val raw = line.substring(m.range.last + 1).trim()
            if (raw.isEmpty()) continue
            // 合并格式：原文 (翻译：译文)
            var text = raw
            var trans: String? = null
            val mark = raw.lastIndexOf(" (翻译：")
            if (mark >= 0 && raw.endsWith(")")) {
                text = raw.substring(0, mark).trim()
                trans = raw.substring(mark + 5, raw.length - 1).trim().ifEmpty { null }
            }
            if (text.isEmpty()) continue
            out.add(LyricLine(min * 60000L + sec * 1000L + ms, text, trans))
        }
        out.sortBy { it.timeMs }
        return out
    }

    /** 全部歌词行（歌词页用） */
    fun lyricLines(): List<LyricLine> = lyric

    /** 当前歌词行下标（不存在时 -1） */
    fun currentLyricIndex(pos: Long): Int {
        if (lyric.isEmpty()) return -1
        var lo = 0
        var hi = lyric.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lyric[mid].timeMs <= pos) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return idx
    }

    private fun lyricAt(pos: Long): String {
        val idx = currentLyricIndex(pos)
        if (idx < 0) return ""
        val line = lyric[idx]
        return if (line.trans.isNullOrEmpty()) line.text else line.text + "\n" + line.trans
    }
}
