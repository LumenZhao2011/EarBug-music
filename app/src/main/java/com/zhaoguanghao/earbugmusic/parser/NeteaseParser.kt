package com.zhaoguanghao.earbugmusic.parser

import com.zhaoguanghao.earbugmusic.model.Comment
import com.zhaoguanghao.earbugmusic.model.ParseResult
import com.zhaoguanghao.earbugmusic.model.Platform
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.net.Http
import com.zhaoguanghao.earbugmusic.util.EapiCrypto
import com.zhaoguanghao.earbugmusic.util.LyricsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 网易云音乐解析器（无损优先）。
 *
 * 主流程：苏晓晴(小琴)解析 API（nextmusic.toubiec.cn，即样本 MusicParser 工具的同源接口）：
 *  - /api/ip          获取本机出口 IP
 *  - /api/getSongUrl、/api/getMusicUrl   按 jymaster→jyeffect→sky→hires→lossless 取无损直链
 *  - /api/getSongInfo 获取与样本一致的 名称/歌手(/)连接/专辑/封面
 *  - /api/getSongLyric 获取 lrc+tlyric，按样本格式合并
 * 兜底：eapi 接口（无 VIP 时只能取免费音质）。
 */
object NeteaseParser : MusicParser {

    override val platform = Platform.NETEASE

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
    private const val REFERER = "https://music.163.com/"
    private const val SEARCH_API = "https://music.163.com/api/cloudsearch/pc"
    private const val API_BASE = "https://nextmusic.toubiec.cn/api"
    private const val SONG_URL_API = "https://interface3.music.163.com/eapi/song/enhance/player/url/v1"
    private const val LYRIC_API = "https://interface3.music.163.com/api/song/lyric"

    /** 音质尝试顺序：母带 → 高清环绕声 → 沉浸声 → Hi-Res → 无损 */
    private val LEVELS = listOf("jymaster", "jyeffect", "sky", "hires", "lossless")

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Origin" to "https://wyapi.toubiec.cn",
        "Referer" to "https://wyapi.toubiec.cn/",
        "User-Agent" to UA,
        "Content-Type" to "application/json"
    )

    override suspend fun search(keyword: String, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        try {
            val body = Http.postForm(
                SEARCH_API,
                mapOf("s" to keyword, "type" to "1", "limit" to limit.toString(), "offset" to "0"),
                mapOf("User-Agent" to UA, "Referer" to REFERER)
            ) ?: return@withContext result
            val json = JSONObject(body)
            if (json.optInt("code") != 200) return@withContext result
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return@withContext result
            for (i in 0 until songs.length()) {
                val s = songs.optJSONObject(i) ?: continue
                val artists = buildString {
                    val ar = s.optJSONArray("ar")
                    if (ar != null) for (j in 0 until ar.length()) {
                        if (j > 0) append(" / ")
                        append(ar.optJSONObject(j)?.optString("name") ?: "")
                    }
                }
                val al = s.optJSONObject("al")
                result.add(
                    Song(
                        platform = platform,
                        id = s.optString("id"),
                        name = s.optString("name"),
                        artist = artists,
                        album = al?.optString("name") ?: "",
                        coverUrl = al?.optString("picUrl") ?: "",
                        durationMs = s.optLong("dt", 0)
                    )
                )
            }
        } catch (_: Exception) {
        }
        result
    }

    override suspend fun resolve(song: Song, quality: Quality): ParseResult = withContext(Dispatchers.IO) {
        val wantLossless = quality.rank >= Quality.LOSSLESS.rank
        if (wantLossless) {
            // 无损/Hi-Res/母带：只有解析服务能提供，且服务端账号轮换偶发返回 null，重试几次
            var lastError: String? = null
            for (attempt in 0 until 4) {
                if (attempt > 0) delay(700)
                try {
                    return@withContext resolveViaXiaoqin(song, levelOf(quality))
                } catch (e: Exception) {
                    lastError = e.message
                }
            }
            throw Exception(lastError ?: "该歌曲暂无该音质（可能受版权限制）")
        }
        // 标准/较高/极高：官方接口优先
        var officialError: String? = null
        try {
            return@withContext resolveViaOfficial(song, quality)
        } catch (e: Exception) {
            officialError = e.message
        }
        for (attempt in 0 until 2) {
            try {
                return@withContext resolveViaXiaoqin(song, levelOf(quality))
            } catch (_: Exception) {
            }
        }
        throw Exception(officialError ?: "解析失败：该歌曲可能受版权限制")
    }

    /** 音质档位 → 官方接口 br */
    private fun brOf(quality: Quality): Int = when (quality) {
        Quality.STANDARD -> 128000
        Quality.HIGH -> 192000
        Quality.EXHIGH -> 320000
        Quality.LOSSLESS, Quality.HIRES, Quality.MASTER -> 999000
    }

    private fun levelRank(level: String): Int = when (level) {
        "standard" -> 0
        "higher" -> 1
        "exhigh" -> 2
        "lossless" -> 3
        "hires" -> 4
        "jyeffect" -> 4      // 高清环绕声
        "sky" -> 4           // 沉浸环绕声
        "jymaster" -> 5      // 超清母带
        "dolby" -> 5         // 杜比全景声
        else -> -1
    }

    private fun labelOfLevel(level: String, br: Int): String {
        val name = when (level) {
            "standard" -> "标准"
            "higher" -> "较高"
            "exhigh" -> "极高"
            "lossless" -> "无损"
            "hires" -> "Hi-Res"
            "jyeffect" -> "高清环绕声"
            "sky" -> "沉浸环绕声"
            "dolby" -> "杜比全景声"
            "jymaster" -> "超清母带"
            else -> "未知音质"
        }
        return if (br > 0) name + " " + (br / 1000) + "K" else name
    }

    /** 官方接口取直链；音质不足时明确报错（不静默降级） */
    private fun resolveViaOfficial(song: Song, quality: Quality): ParseResult {
        val br = brOf(quality)
        val url = "https://music.163.com/api/song/enhance/player/url?ids=%5B" + song.id + "%5D&br=" + br
        val body = Http.get(url, headers()) ?: throw Exception("网络异常，无法获取直链")
        val d = JSONObject(body).optJSONArray("data")?.optJSONObject(0) ?: throw Exception("解析失败")
        val code = d.optInt("code", 0)
        val u = d.optString("url")
        if (u.isNullOrEmpty()) {
            throw Exception(
                when (code) {
                    -110 -> "该歌曲受版权限制或需要登录（-110）"
                    404 -> "该歌曲暂无可用版权（404）"
                    else -> "该歌曲无法播放（code " + code + "）"
                }
            )
        }
        val level = d.optString("level", "")
        val want = levelRank(levelOf(quality))
        val got = levelRank(level)
        if (got < want) {
            throw Exception(
                "该音质不可用：匿名最高「极高 320K」，无损/Hi-Res/母带需要 VIP。" +
                    "请在音质里选「极高」再试"
            )
        }
        return ParseResult(
            url = u,
            quality = labelOfLevel(level, d.optInt("br", br)),
            size = d.optLong("size", 0),
            ext = d.optString("type", "mp3").ifBlank { "mp3" },
            lyric = runCatching { lyric(song.id) }.getOrNull(),
            coverUrl = song.coverUrl,
            title = song.name,
            artist = song.artist,
            album = song.album
        )
    }

    /** 音质档位 → 网易云 level（官方档位名） */
    private fun levelOf(quality: Quality): String = when (quality) {
        Quality.MASTER -> "jymaster"
        Quality.HIRES -> "hires"
        Quality.LOSSLESS -> "lossless"
        Quality.EXHIGH -> "exhigh"
        Quality.HIGH -> "higher"
        Quality.STANDARD -> "standard"
    }
    /** 原始 LRC（播放页滚动歌词用）：公开歌词接口，含翻译合并 */
    fun lyric(id: String): String? {
        return try {
            val url = "https://music.163.com/api/song/lyric?id=" + id + "&lv=-1&kv=-1&tv=-1"
            val body = Http.get(url, headers()) ?: return null
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc")?.optString("lyric") ?: ""
            if (lrc.isBlank()) return null
            val tlyric = json.optJSONObject("tlyric")?.optString("lyric") ?: ""
            LyricsUtil.merge(lrc, tlyric.ifBlank { null })
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 详情 / 专辑 / 歌单 / 榜单 / 评论 ====================

    private fun headers(): Map<String, String> = mapOf("User-Agent" to UA, "Referer" to REFERER)

    private fun songFromJson(s: JSONObject): Song {
        val artists = buildString {
            val ar = s.optJSONArray("ar")
            if (ar != null) for (j in 0 until ar.length()) {
                if (j > 0) append(" / ")
                append(ar.optJSONObject(j)?.optString("name") ?: "")
            }
        }
        val al = s.optJSONObject("al")
        return Song(
            platform = Platform.NETEASE,
            id = s.optString("id"),
            name = s.optString("name"),
            artist = artists,
            album = al?.optString("name") ?: "",
            coverUrl = al?.optString("picUrl") ?: "",
            durationMs = s.optLong("dt", 0)
        )
    }

    /** 批量歌曲详情（100 首一批） */
    fun songDetail(ids: List<String>): List<Song> {
        val result = mutableListOf<Song>()
        for (chunk in ids.chunked(100)) {
            val c = JSONArray()
            for (id in chunk) c.put(JSONObject().put("id", id.toLongOrNull() ?: 0L).put("v", 0))
            val body = Http.postForm(
                "https://interface3.music.163.com/api/v3/song/detail",
                mapOf("c" to c.toString()), headers()
            ) ?: continue
            val songs = JSONObject(body).optJSONArray("songs") ?: continue
            for (i in 0 until songs.length()) {
                result.add(songFromJson(songs.optJSONObject(i) ?: continue))
            }
        }
        return result
    }

    /** 按歌曲 ID 获取单曲 */
    fun songById(id: String): Song =
        songDetail(listOf(id)).firstOrNull() ?: throw Exception("未找到该歌曲")

    /** 专辑全部歌曲 */
    fun albumSongs(albumId: String): List<Song> {
        val body = Http.get("https://music.163.com/api/album/" + albumId, headers())
            ?: throw Exception("获取专辑失败")
        val json = JSONObject(body)
        if (json.optInt("code") != 200) throw Exception("获取专辑失败")
        val songs = json.optJSONArray("songs") ?: throw Exception("专辑为空")
        val list = mutableListOf<Song>()
        for (i in 0 until songs.length()) {
            songs.optJSONObject(i)?.let { list.add(songFromJson(it)) }
        }
        if (list.isEmpty()) throw Exception("专辑为空")
        return list
    }

    /** 歌单全部歌曲 */
    fun playlistSongs(playlistId: String): List<Song> {
        val body = Http.postForm(
            "https://music.163.com/api/v6/playlist/detail",
            mapOf("id" to playlistId, "n" to "1000"), headers()
        ) ?: throw Exception("获取歌单失败")
        val json = JSONObject(body)
        if (json.optInt("code") != 200) throw Exception("获取歌单失败")
        val tracks = json.optJSONObject("playlist")?.optJSONArray("trackIds")
            ?: throw Exception("歌单为空")
        val ids = mutableListOf<String>()
        for (i in 0 until minOf(tracks.length(), 500)) {
            tracks.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
        }
        if (ids.isEmpty()) throw Exception("歌单为空")
        return songDetail(ids)
    }

    /** 热歌榜（网易云官方榜单歌单 3778678） */
    fun topList(): List<Song> = playlistSongs("3778678")

    /** 歌曲评论（热门 + 最新） */
    fun comments(songId: String, limit: Int = 50): List<Comment> {
        val url = "https://music.163.com/api/v1/resource/comments/R_SO_4_" + songId +
            "?limit=" + limit + "&offset=0"
        val body = Http.get(url, headers()) ?: throw Exception("获取评论失败")
        val json = JSONObject(body)
        val out = mutableListOf<Comment>()
        fun collect(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val user = c.optJSONObject("user")
                out.add(
                    Comment(
                        user = user?.optString("nickname") ?: "匿名用户",
                        avatar = user?.optString("avatarUrl") ?: "",
                        content = c.optString("content"),
                        liked = c.optInt("likedCount", 0),
                        timeText = c.optString("timeStr", "")
                    )
                )
            }
        }
        collect(json.optJSONArray("hotComments"))
        collect(json.optJSONArray("comments"))
        if (out.isEmpty()) throw Exception("暂无评论")
        return out
    }

    // ==================== 小琴 API 流程 ====================

    private fun resolveViaXiaoqin(song: Song, level: String): ParseResult {
        val headers = apiHeaders()
        val ts = System.currentTimeMillis()

        // 出口 IP
        val ipResp = Http.postJson(API_BASE + "/ip", "{\"timestamp\":" + ts + "}", headers)
            ?: throw Exception("解析服务不可用")
        val ip = JSONObject(ipResp).optJSONObject("data")?.optString("ip")
            ?: throw Exception("解析服务不可用")

        // 逐等级取直链
        var url: String? = null
        var gotLevel: String? = null
        var gotSize: Long = 0
        for (ep in listOf("getSongUrl", "getMusicUrl")) {
            val payload = JSONObject()
                .put("id", song.id)
                .put("level", level)
                .put("timestamp", ts)
                .put("ip", ip)
                .toString()
            val resp = Http.postJson(API_BASE + "/" + ep, payload, headers) ?: continue
            try {
                val j = JSONObject(resp)
                if (j.optInt("code") != 200) continue
                val d = j.optJSONObject("data") ?: continue
                val u = d.optString("url")
                if (u.isNullOrEmpty() || !u.startsWith("http")) continue
                val returned = d.optString("level", level)
                // 服务端返回等级低于所选才视为不可用（同档/更高档均接受，不静默降级）
                if (levelRank(returned) in 0 until levelRank(level)) {
                    throw Exception(
                        "该歌曲最高只提供「" + labelOfLevel(returned, d.optInt("br", 0)) +
                            "」，没有「" + labelOfLevel(level, 0) + "」音质"
                    )
                }
                url = u
                gotLevel = returned
                gotSize = d.optLong("size", 0)
                break
            } catch (e: Exception) {
                if (e.message?.startsWith("该音质不可用") == true) throw e
            }
        }
        if (url == null) throw Exception("解析失败：该歌曲受版权限制")

        // 歌曲信息（与样本一致的名称/歌手/专辑/封面）
        var name = song.name
        var artist = song.artist.replace(" / ", "/")
        var album = song.album
        var cover = song.coverUrl
        try {
            val infoPayload = JSONObject()
                .put("id", song.id)
                .put("timestamp", ts)
                .put("ip", ip)
                .toString()
            val infoResp = Http.postJson(API_BASE + "/getSongInfo", infoPayload, headers)
            if (infoResp != null) {
                val d = JSONObject(infoResp).optJSONObject("data")
                if (d != null) {
                    name = d.optString("name", name)
                    artist = d.optString("singer", artist)
                    album = d.optString("album", album)
                    cover = d.optString("picimg", cover)
                }
            }
        } catch (_: Exception) {
        }

        // 歌词（lrc + tlyric 按样本格式合并）
        val lyric = fetchLyricViaApi(song.id, ts, ip, headers)
            ?: fetchLyricViaEapi(song.id)

        return ParseResult(
            url = url,
            quality = qualityLabel(gotLevel),
            size = gotSize,
            ext = "flac",
            lyric = lyric,
            coverUrl = cover,
            title = name,
            artist = artist,
            album = album
        )
    }

    private fun fetchLyricViaApi(id: String, ts: Long, ip: String, headers: Map<String, String>): String? {
        return try {
            val payload = JSONObject().put("id", id).put("timestamp", ts).put("ip", ip).toString()
            val resp = Http.postJson(API_BASE + "/getSongLyric", payload, headers) ?: return null
            val d = JSONObject(resp).optJSONObject("data") ?: return null
            val lrc = d.optString("lrc", "")
            if (lrc.isBlank()) return null
            val tlyric = d.optString("tlyric", "")
            val merged = LyricsUtil.merge(lrc, tlyric)
            if (merged.isBlank()) null else merged
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLyricViaEapi(id: String): String? {
        return try {
            val body = Http.postForm(
                LYRIC_API,
                mapOf(
                    "id" to id, "cp" to "false", "tv" to "0", "lv" to "0", "rv" to "0",
                    "kv" to "0", "yv" to "0", "ytv" to "0", "yrv" to "0"
                ),
                mapOf("User-Agent" to UA, "Referer" to REFERER)
            ) ?: return null
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc")?.optString("lyric")
            if (lrc.isNullOrEmpty()) return null
            val tlyric = json.optJSONObject("tlyric")?.optString("lyric")
            LyricsUtil.merge(lrc, tlyric)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== eapi 兜底 ====================

    private fun tryEapi(id: String, level: String, coverUrl: String): ParseResult? {
        return try {
            val config = JSONObject().apply {
                put("os", "pc")
                put("appver", "")
                put("osver", "")
                put("deviceId", "pyncm!")
                put("requestId", (20000000..29999999).random().toString())
            }
            val payload = JSONObject().apply {
                put("ids", JSONArray(listOf(id.toLongOrNull() ?: id)))
                put("level", level)
                put("encodeType", if (level == "dolby") "mp4" else "flac")
                put("header", config.toString())
            }
            val params = EapiCrypto.encrypt(SONG_URL_API, payload)
            val body = Http.postForm(
                SONG_URL_API,
                mapOf("params" to params),
                mapOf("User-Agent" to UA, "Referer" to REFERER)
            ) ?: return null
            val json = JSONObject(body)
            if (json.optInt("code") != 200) return null
            val data = json.optJSONArray("data")?.optJSONObject(0) ?: return null
            val url = data.optString("url")
            if (url.isNullOrEmpty()) return null
            val type = data.optString("type", "flac")
            ParseResult(
                url = url,
                quality = qualityLabel(level),
                bitrate = data.optInt("br", 0),
                size = data.optLong("size", 0),
                ext = if (type.isEmpty()) "mp3" else type,
                lyric = fetchLyricViaEapi(id),
                coverUrl = coverUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun qualityLabel(level: String?): String = when (level) {
        "jymaster" -> "超清母带"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "hires" -> "Hi-Res 无损"
        "lossless" -> "无损 FLAC"
        "exhigh" -> "极高 320K"
        "higher" -> "较高"
        else -> "无损"
    }
}