package com.zhaoguanghao.earbugmusic.tag

import com.zhaoguanghao.earbugmusic.model.AudioTags
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * FLAC 元数据写入，与样本文件（MusicParser 工具产出）的管线完全一致：
 *
 * 源文件（网易云 CDN 原版）块结构：[STREAMINFO, SEEKTABLE, VORBIS_COMMENT, PADDING]
 * 输出结构：               [STREAMINFO, SEEKTABLE, PADDING, VORBIS_COMMENT, PICTURE]
 *
 * 即：保留 STREAMINFO / SEEKTABLE / PADDING 三个块（含失效偏移，与样本一致），
 * 去掉原 VORBIS_COMMENT，追加新的 VORBIS_COMMENT（vendor=MusicParser，
 * 4 个标签 TITLE/ARTIST/ALBUM/LYRICS）与 PICTURE（类型 3、image/jpg、
 * Cover、宽高 0、位深 24、颜色 0）。音频帧数据逐字节流式复制。
 *
 * 这样重建出的文件与 MusicParser 下载器产出**哈希一致**（已验证）。
 */
object FlacTagger {

    /** 与样本一致：写入工具标识 */
    private const val VENDOR = "MusicParser"

    fun write(file: File, tags: AudioTags): Boolean {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4) return false
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (!magic.contentEquals("fLaC".toByteArray(Charsets.US_ASCII))) return false

                // 遍历源块：保留 STREAMINFO/SEEKTABLE/PADDING，记录音频帧起点
                val kept = mutableListOf<Pair<Int, ByteArray>>()
                var audioOffset = 4L
                while (true) {
                    val h = ByteArray(4)
                    raf.readFully(h)
                    val last = (h[0].toInt() and 0x80) != 0
                    val type = h[0].toInt() and 0x7F
                    val size = ((h[1].toInt() and 0xFF) shl 16) or
                        ((h[2].toInt() and 0xFF) shl 8) or
                        (h[3].toInt() and 0xFF)
                    if (type == 0 || type == 3 || type == 1) {
                        val data = ByteArray(size)
                        raf.readFully(data)
                        kept.add(type to data)
                    } else {
                        raf.skipBytes(size)
                    }
                    if (last) {
                        audioOffset = raf.filePointer
                        break
                    }
                }

                // 输出块：保留块 + VORBIS_COMMENT + PICTURE
                val comment = buildVorbisComment(tags)
                val picture = tags.coverJpeg?.let { buildPicture(it) }
                tmp.outputStream().use { out ->
                    out.write("fLaC".toByteArray(Charsets.US_ASCII))
                    val blocks = kept.toMutableList()
                    blocks.add(4 to comment)
                    if (picture != null) blocks.add(6 to picture)
                    for (i in blocks.indices) {
                        val (t, d) = blocks[i]
                        writeBlock(out, t, d, last = i == blocks.size - 1)
                    }
                    // 音频帧流式复制
                    raf.seek(audioOffset)
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = raf.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!file.delete()) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return false
            }
            return true
        } catch (_: Exception) {
            tmp.delete()
            return false
        }
    }

    private fun writeBlock(out: OutputStream, type: Int, data: ByteArray, last: Boolean) {
        out.write((if (last) 0x80 else 0x00) or type)
        val size = data.size
        out.write((size shr 16) and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write(size and 0xFF)
        out.write(data)
    }

    /** VORBIS_COMMENT：vendor + TITLE/ARTIST/ALBUM/LYRICS（小端 u32 长度） */
    private fun buildVorbisComment(tags: AudioTags): ByteArray {
        val baos = ByteArrayOutputStream()
        val vendor = VENDOR.toByteArray(Charsets.UTF_8)
        writeLE32(baos, vendor.size)
        baos.write(vendor)
        val comments = listOf(
            "TITLE=" + tags.title,
            "ARTIST=" + tags.artist,
            "ALBUM=" + tags.album,
            "LYRICS=" + (tags.lyrics ?: "")
        )
        writeLE32(baos, comments.size)
        for (c in comments) {
            val b = c.toByteArray(Charsets.UTF_8)
            writeLE32(baos, b.size)
            baos.write(b)
        }
        return baos.toByteArray()
    }

    /** PICTURE：类型 3、image/jpg、Cover、宽高 0、位深 24、颜色 0（与样本一致，大端） */
    private fun buildPicture(jpeg: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        writeBE32(baos, 3)
        val mime = "image/jpg".toByteArray(Charsets.US_ASCII)
        writeBE32(baos, mime.size)
        baos.write(mime)
        val desc = "Cover".toByteArray(Charsets.UTF_8)
        writeBE32(baos, desc.size)
        baos.write(desc)
        writeBE32(baos, 0)  // width
        writeBE32(baos, 0)  // height
        writeBE32(baos, 24) // depth
        writeBE32(baos, 0)  // colors
        writeBE32(baos, jpeg.size)
        baos.write(jpeg)
        return baos.toByteArray()
    }

    private fun writeLE32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeBE32(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 24) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
