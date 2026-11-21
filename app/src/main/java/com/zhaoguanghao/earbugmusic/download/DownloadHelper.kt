package com.zhaoguanghao.earbugmusic.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.zhaoguanghao.earbugmusic.model.AudioTags
import com.zhaoguanghao.earbugmusic.model.ParseResult
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.net.Http
import com.zhaoguanghao.earbugmusic.tag.MetadataWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 下载器：先写临时文件并打标签，再保存到公共目录
 * Download/耳虫音乐/（Android 10+ 走 MediaStore，旧系统直写并扫描）。
 */
object DownloadHelper {

    /** 子目录名 */
    const val SUB_DIR = "耳虫音乐"

    /** 保存结果：display 用于展示，location 用于本地播放（绝对路径或 content://） */
    data class Saved(val display: String, val location: String)

    suspend fun download(
        context: Context,
        song: Song,
        result: ParseResult,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Saved = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "dl").also { it.mkdirs() }
        val safe = song.fileName().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val name = safe + "." + result.ext
        val tmp = File(tmpDir, name)
        val ok = Http.download(result.url, tmp, onProgress)
        if (!ok) throw Exception("下载失败，直链可能已失效")

        // 按样本规则写入元数据（失败不影响已下载文件）
        val tags = AudioTags(
            title = result.title ?: song.name,
            artist = (result.artist ?: song.artist).replace(" / ", "/"),
            album = result.album ?: song.album,
            lyrics = result.lyric,
            coverJpeg = fetchCoverJpeg(result.coverUrl)
        )
        MetadataWriter.write(tmp, tags)

        val saved = saveToDownload(context, tmp, name, mimeOf(result.ext))
        tmp.delete()
        saved
    }

    /** 保存到公共 Download/耳虫音乐，返回展示路径与播放定位 */
    private fun saveToDownload(context: Context, src: File, name: String, mime: String): Saved {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法写入 Download 目录")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                } ?: throw Exception("无法写入 Download 目录")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return Saved("Download/" + SUB_DIR + "/" + name, uri.toString())
        } else {
            // Android 9 及以下：直接写公共 Download 目录（需存储权限）
            try {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(base, SUB_DIR).also { it.mkdirs() }
                val dst = File(dir, name)
                src.copyTo(dst, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dst.absolutePath), null, null)
                return Saved(dst.absolutePath, dst.absolutePath)
            } catch (e: Exception) {
                // 权限被拒或目录不可写：退回应用专属目录，保证文件不丢
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                dir.mkdirs()
                val dst = File(dir, name)
                src.copyTo(dst, overwrite = true)
                return Saved(dst.absolutePath, dst.absolutePath)
            }
        }
    }

    private fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }

    /**
     * 拉取封面并统一转成 JPEG。
     * 网易云 CDN 的 .jpg 地址实际常返回 PNG（Content-Type 也写 image/jpg），
     * 因此不能只认 JPEG 魔数，需解码后重新编码为 JPEG 再写入标签。
     */
    private fun fetchCoverJpeg(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        val base = url.substringBefore('?')
        val candidates = listOf(url, base + "?param=500y500", base).distinct()
        for (candidate in candidates) {
            val raw = tryGetBytes(candidate) ?: continue
            if (raw.size < 8) continue
            // 本身是 JPEG：直接使用
            if (raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()) return raw
            // PNG / WebP / BMP 等：解码后转 JPEG
            runCatching {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    ?: return@runCatching null
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                bmp.recycle()
                out.toByteArray().takeIf { it.size > 0 }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun tryGetBytes(url: String): ByteArray? {
        return try {
            Http.getBytes(url.replace("http://", "https://")) ?: Http.getBytes(url)
        } catch (_: Exception) {
            null
        }
    }
}
