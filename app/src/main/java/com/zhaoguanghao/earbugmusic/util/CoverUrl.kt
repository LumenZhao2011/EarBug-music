package com.zhaoguanghao.earbugmusic.util

/**
 * 网易云封面地址支持 ?param=WxH 参数。
 * 列表缩略图用小尺寸节省流量；大图（播放页）使用高清尺寸。
 */
object CoverUrl {

    /** 列表缩略图（默认 240px，兼顾清晰度与流量） */
    fun thumb(url: String?, size: Int = 240): String {
        if (url.isNullOrBlank()) return ""
        return url.substringBefore('?') + "?param=" + size + "y" + size
    }

    /** 播放页大图（默认 1080px 高清） */
    fun large(url: String?, size: Int = 1080): String {
        if (url.isNullOrBlank()) return ""
        return url.substringBefore('?') + "?param=" + size + "y" + size
    }

    /** 原始封面（写入文件标签用，不压缩） */
    fun original(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url
    }
}
