package com.zhaoguanghao.earbugmusic.model

/**
 * 写入下载文件的元数据，规则与样本文件一致
 * （样本由 MusicParser 工具写入，vendor=MusicParser）：
 *  - title  歌曲名（TITLE）
 *  - artist 歌手，多个歌手用 "/" 连接（ARTIST）
 *  - album  专辑名（ALBUM）
 *  - lyrics LRC 歌词文本，时间戳 [mm:ss.SSS]，含行内翻译（LYRICS）
 *  - coverJpeg 封面 JPEG 数据，null 表示不写封面
 */
data class AudioTags(
    val title: String,
    val artist: String,
    val album: String,
    val lyrics: String?,
    val coverJpeg: ByteArray?
)
