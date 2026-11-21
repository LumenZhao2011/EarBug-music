package com.zhaoguanghao.earbugmusic.model

import java.io.Serializable

/**
 * 统一的三平台歌曲模型。
 * id 在不同平台含义不同：
 *  - 网易云：歌曲数字 id
 *  - QQ：songmid（字符串）
 *  - 酷狗：文件 hash
 */
data class Song(
    val platform: Platform,
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long = 0L,
    /** 平台附加信息：如酷狗各音质 hash（hash128/hash320/hashsq）、QQ 的 mediaMid */
    val extra: Map<String, String> = emptyMap()
) : Serializable {

    /**
     * 按样本约定生成下载文件名：歌曲名 - 歌手（多歌手用空格分隔，如
     * "One Last Kiss - 宇多田ヒカル"、"Color Your Night - Lotus Juice 高橋あず美 …"）。
     * 对超长名称做截断，避免触发 Windows 单段文件名长度限制。
     */
    fun fileName(): String {
        val artists = artist.replace(" / ", " ").trim()
        var result = if (artists.isEmpty()) name else name + " - " + artists
        if (result.length > 200) result = result.substring(0, 200)
        return result.trim()
    }
}
