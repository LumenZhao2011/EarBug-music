package com.zhaoguanghao.earbugmusic.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.zhaoguanghao.earbugmusic.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 简单的封面加载器：内存缓存 + 协程异步加载。
 */
object ImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cache = HashMap<String, Bitmap>()

    fun bitmapBlocking(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache[url]?.let { return it }
        return try {
            val bytes = Http.getBytes(url) ?: return null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) cache[url] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun load(iv: ImageView, url: String) {
        if (url.isBlank()) return
        cache[url]?.let { iv.setImageBitmap(it); return }
        iv.setTag(url)
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val bytes = Http.getBytes(url) ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
            if (bmp != null) {
                cache[url] = bmp
                if (iv.getTag() == url) iv.setImageBitmap(bmp)
            }
        }
    }
}
