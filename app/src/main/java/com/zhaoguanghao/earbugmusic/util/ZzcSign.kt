package com.zhaoguanghao.earbugmusic.util

import android.util.Base64
import java.security.MessageDigest

/**
 * QQ 音乐 zzc 签名。
 * 移植自 Suxiaoqinx/QQMusicapi 的 src/algorithms/sign.js::zzcSign。
 * 输入为 JSON 字符串，返回 "zzc<part1><b64><part2>" 小写签名。
 */
object ZzcSign {
    private val PART_1 = intArrayOf(23, 14, 6, 36, 16, 7, 19)
    private val PART_2 = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE = intArrayOf(
        89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179
    )

    fun sign(payload: String): String {
        val hashHex = sha1Hex(payload).uppercase()
        val part1 = PART_1.map { hashHex[it] }.joinToString("")
        val part2 = PART_2.map { hashHex[it] }.joinToString("")
        val part3 = ByteArray(20)
        for (i in 0 until 20) {
            val pair = hashHex.substring(i * 2, i * 2 + 2).toInt(16)
            part3[i] = (SCRAMBLE[i] xor pair).toByte()
        }
        val b64 = Base64.encodeToString(part3, Base64.NO_WRAP).replace(Regex("[/+=]"), "")
        return "zzc" + part1 + b64 + part2
    }

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
