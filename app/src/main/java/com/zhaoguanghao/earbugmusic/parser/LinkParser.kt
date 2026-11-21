package com.zhaoguanghao.earbugmusic.parser

import com.zhaoguanghao.earbugmusic.model.ParseMode
import com.zhaoguanghao.earbugmusic.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网易云 ID / 链接解析（对应 wyapi 那套：歌曲ID、歌曲链接、专辑ID、歌单链接、专辑链接）。
 */
object LinkParser {

    @Volatile
    var notice: String? = null

    fun looksLikeLink(input: String): Boolean =
        input.contains("music.163.com") || input.contains("163cn.tv") || input.contains("y.music.163.com")

    private fun digits(s: String): String? = if (s.matches(Regex("\\d{3,}"))) s else null

    fun extractSongId(input: String): String? {
        val s = input.trim()
        digits(s)?.let { return it }
        Regex("music[.]163[.]com[^\\s]*[/?&]id=(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        Regex("music[.]163[.]com/song/(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    fun extractAlbumId(input: String): String? {
        val s = input.trim()
        digits(s)?.let { return it }
        Regex("music[.]163[.]com[^\\s]*album[^\\s]*[/?&]id=(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        Regex("music[.]163[.]com/album/(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    fun extractPlaylistId(input: String): String? {
        val s = input.trim()
        digits(s)?.let { return it }
        Regex("music[.]163[.]com[^\\s]*playlist[^\\s]*[/?&]id=(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        Regex("music[.]163[.]com/playlist/(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        Regex("music[.]163[.]com/discover/toplist[^\\s]*[/?&]id=(\\d{3,})").find(s)?.let { return it.groupValues[1] }
        return null
    }

    /** 根据输入自动判断类型（粘贴链接时用） */
    fun detectMode(input: String): ParseMode? {
        val s = input.trim()
        if (!looksLikeLink(s)) return null
        return when {
            Regex("music[.]163[.]com/album|[?&]album").containsMatchIn(s) -> ParseMode.ALBUM
            Regex("playlist|discover/toplist").containsMatchIn(s) -> ParseMode.PLAYLIST
            else -> ParseMode.SONG
        }
    }

    suspend fun parse(mode: ParseMode, input: String): List<Song> = withContext(Dispatchers.IO) {
        notice = null
        val s = input.trim()
        when (mode) {
            ParseMode.SONG -> {
                val id = extractSongId(s) ?: throw Exception("无法识别的歌曲 ID / 链接")
                listOf(NeteaseParser.songById(id))
            }
            ParseMode.ALBUM -> {
                val id = extractAlbumId(s) ?: throw Exception("无法识别的专辑 ID / 链接")
                NeteaseParser.albumSongs(id)
            }
            ParseMode.PLAYLIST -> {
                val id = extractPlaylistId(s) ?: throw Exception("无法识别的歌单 ID / 链接")
                NeteaseParser.playlistSongs(id)
            }
            ParseMode.TOPLIST -> NeteaseParser.topList()
        }
    }
}
