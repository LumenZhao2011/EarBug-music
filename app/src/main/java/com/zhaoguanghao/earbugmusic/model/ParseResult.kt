package com.zhaoguanghao.earbugmusic.model

import java.io.Serializable

/**
 * 解析结果：音频直链 + 音质信息。
 * url 即用于下载/播放的直链。
 */
data class ParseResult(
    val url: String,
    val quality: String,
    val bitrate: Int = 0,
    val size: Long = 0,
    val ext: String = "mp3",
    val lyric: String? = null,
    val coverUrl: String? = null,
    /** 标签用：平台原始名称/歌手/专辑（与 MusicParser 样本一致），null 时回退 Song 字段 */
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
) : Serializable
