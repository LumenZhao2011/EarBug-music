package com.zhaoguanghao.earbugmusic.util

import android.content.Context

/** 已下载歌曲的本地位置索引（歌曲ID → 文件路径或 content:// URI） */
object LocalLibrary {

    private const val PREFS = "local_library"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, songId: String, location: String) {
        prefs(context).edit().putString(songId, location).apply()
    }

    fun get(context: Context, songId: String): String? =
        prefs(context).getString(songId, null)?.takeIf { it.isNotBlank() }

    fun remove(context: Context, songId: String) {
        prefs(context).edit().remove(songId).apply()
    }
}
