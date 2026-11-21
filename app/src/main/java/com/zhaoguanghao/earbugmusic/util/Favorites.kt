package com.zhaoguanghao.earbugmusic.util

import android.content.Context
import com.zhaoguanghao.earbugmusic.model.Platform
import com.zhaoguanghao.earbugmusic.model.Song
import org.json.JSONArray
import org.json.JSONObject

/** 红心歌曲（收藏）本地存储 */
object Favorites {

    private const val PREFS = "favorites"
    private const val KEY = "songs"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Song> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    Song(
                        platform = Platform.NETEASE,
                        id = o.optString("id"),
                        name = o.optString("name"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        coverUrl = o.optString("cover"),
                        durationMs = o.optLong("duration", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(ctx: Context, songId: String): Boolean =
        all(ctx).any { it.id == songId }

    /** 切换收藏状态，返回切换后的状态 */
    fun toggle(ctx: Context, song: Song): Boolean {
        val list = all(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == song.id }
        val added: Boolean
        if (idx >= 0) {
            list.removeAt(idx)
            added = false
        } else {
            list.add(0, song)
            added = true
        }
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("artist", s.artist)
                    put("album", s.album)
                    put("cover", s.coverUrl)
                    put("duration", s.durationMs)
                }
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
        return added
    }
}
