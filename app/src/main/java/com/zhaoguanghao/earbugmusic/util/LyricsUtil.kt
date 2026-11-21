package com.zhaoguanghao.earbugmusic.util

/**
 * 歌词工具：把原始 LRC 与翻译 LRC 合并成样本中的格式：
 *   [mm:ss.SSS]原文 (翻译：译文)
 * 时间戳统一规范为 3 位毫秒（样本如 [00:21.180]）。
 */
object LyricsUtil {

    /** 合并原文与翻译并规范化时间戳；trans 为空时仅规范化 */
    fun merge(lrc: String, trans: String?): String {
        val map = HashMap<String, String>()
        if (!trans.isNullOrEmpty()) {
            for (line in trans.lines()) {
                val ts = extractTs(line) ?: continue
                val text = line.substring(ts.end).trim()
                if (text.isNotEmpty()) map[ts.normalized] = text
            }
        }
        val sb = StringBuilder()
        for (line in lrc.lines()) {
            val ts = extractTs(line) ?: continue
            val text = line.substring(ts.end).trim()
            if (text.isEmpty()) {
                sb.append(ts.normalized).append('\n')
            } else {
                val t = map[ts.normalized]
                if (t != null) {
                    sb.append(ts.normalized).append(text).append(" (翻译：").append(t).append(")\n")
                } else {
                    sb.append(ts.normalized).append(text).append('\n')
                }
            }
        }
        return sb.toString()
    }

    private data class Ts(val end: Int, val normalized: String)

    /**
     * 解析行首 LRC 时间戳（支持 [mm:ss]、[mm:ss.x]、[mm:ss.xx]、[mm:ss.xxx]）。
     * 非时间戳行（如 [ti:]、[ar:]、[offset:]）返回 null。
     */
    private fun extractTs(line: String): Ts? {
        if (!line.startsWith("[")) return null
        val close = line.indexOf(']')
        if (close <= 1) return null
        val inner = line.substring(1, close)
        val parts = inner.split(":")
        if (parts.size < 2) return null
        val min = parts[0].toIntOrNull() ?: return null
        val secPart = parts[1]
        val sec = secPart.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        if (min > 59 || sec > 59) return null
        var ms = 0
        val dotIdx = secPart.indexOfFirst { it == '.' || it == ':' }
        if (dotIdx >= 0) {
            val msRaw = secPart.substring(dotIdx + 1).takeWhile { it.isDigit() }
            ms = when (msRaw.length) {
                0 -> 0
                1 -> msRaw.toInt() * 100
                2 -> msRaw.toInt() * 10
                else -> msRaw.substring(0, 3).toInt()
            }
        }
        return Ts(close + 1, "[%02d:%02d.%03d]".format(min, sec, ms))
    }
}
