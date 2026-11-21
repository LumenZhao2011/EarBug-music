package com.zhaoguanghao.earbugmusic.download

import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song

/** 下载任务状态 */
enum class DownloadStatus { WAITING, PARSING, DOWNLOADING, SUCCESS, FAILED }

/**
 * 下载任务：供下载管理列表实时展示进度/速度/结果。
 */
data class DownloadTask(
    val id: Long,
    val song: Song,
    var status: DownloadStatus = DownloadStatus.WAITING,
    var progress: Float = 0f,
    var doneBytes: Long = 0,
    var totalBytes: Long = 0,
    var speed: Long = 0,
    /** 请求的音质档位 */
    var requestQuality: Quality = Quality.LOSSLESS,
    var quality: String = "",
    var filePath: String? = null,
    /** 本地播放定位（绝对路径或 content://） */
    var location: String? = null,
    var error: String? = null
) {
    /** 展示用品质：已解析显示真实品质，未解析显示所选档位 */
    val qualityText: String
        get() = if (quality.isNotBlank()) quality else
            com.zhaoguanghao.earbugmusic.model.QualityCatalog
                .labelOf(com.zhaoguanghao.earbugmusic.model.Platform.NETEASE, requestQuality) + "（待获取）"

    val statusText: String
        get() = when (status) {
            DownloadStatus.WAITING -> "等待中"
            DownloadStatus.PARSING -> "解析直链中…"
            DownloadStatus.DOWNLOADING -> "下载中 " + formatBytes(doneBytes) + "/" + formatBytes(totalBytes) +
                "  " + formatSpeed(speed)
            DownloadStatus.SUCCESS -> "已完成 · " + quality +
                (filePath?.let { "\n" + it } ?: "")
            DownloadStatus.FAILED -> "失败：" + (error ?: "未知错误")
        }

    companion object {
        fun formatBytes(b: Long): String {
            if (b <= 0) return "0B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var v = b.toDouble()
            var i = 0
            while (v >= 1024 && i < units.size - 1) {
                v /= 1024
                i++
            }
            return String.format("%.1f%s", v, units[i])
        }

        fun formatSpeed(bps: Long): String = if (bps > 0) formatBytes(bps) + "/s" else ""
    }
}
