package com.zhaoguanghao.earbugmusic.tag

import com.zhaoguanghao.earbugmusic.model.AudioTags
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * MP3 的 ID3v2.3 标签写入（样本规则的 MP3 等价实现）：
 *  - TIT2 标题
 *  - TPE1 歌手（多歌手用 / 连接）
 *  - TALB 专辑
 *  - USLT 歌词
 *  - APIC 封面（front cover，描述 Cover）
 * 文本统一用 UTF-16（带 BOM）编码，保证中文/日文正常显示。
 */
object Id3Tagger {

    fun write(file: File, tags: AudioTags): Boolean {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            val audio = readAudioPayload(file) ?: return false
            val frames = ByteArrayOutputStream()
            addTextFrame(frames, "TIT2", tags.title)
            addTextFrame(frames, "TPE1", tags.artist)
            addTextFrame(frames, "TALB", tags.album)
            tags.lyrics?.let { addUsltFrame(frames, it) }
            tags.coverJpeg?.let { addApicFrame(frames, it) }

            val body = frames.toByteArray()
            tmp.outputStream().use { out ->
                out.write("ID3".toByteArray(Charsets.US_ASCII))
                out.write(3) // 主版本 v2.3
                out.write(0)
                out.write(0) // flags
                val size = body.size
                out.write((size shr 21) and 0x7F)
                out.write((size shr 14) and 0x7F)
                out.write((size shr 7) and 0x7F)
                out.write(size and 0x7F)
                out.write(body)
                out.write(audio)
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

    /** 读出音频负载（跳过已有的 ID3v2 头） */
    private fun readAudioPayload(file: File): ByteArray? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size >= 10 &&
                bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
            ) {
                val size = (((bytes[6].toInt() and 0x7F) shl 21) or
                    ((bytes[7].toInt() and 0x7F) shl 14) or
                    ((bytes[8].toInt() and 0x7F) shl 7) or
                    (bytes[9].toInt() and 0x7F))
                val start = 10 + size
                if (start < bytes.size) bytes.copyOfRange(start, bytes.size) else bytes
            } else bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun addTextFrame(out: ByteArrayOutputStream, id: String, text: String) {
        val body = ByteArrayOutputStream()
        body.write(1) // UTF-16 带 BOM
        body.write(0xFF)
        body.write(0xFE)
        body.write(text.toByteArray(Charsets.UTF_16LE))
        writeFrame(out, id, body.toByteArray())
    }

    private fun addUsltFrame(out: ByteArrayOutputStream, lyrics: String) {
        val body = ByteArrayOutputStream()
        body.write(1)          // UTF-16 带 BOM
        body.write("eng".toByteArray(Charsets.US_ASCII)) // 语言
        body.write(0xFF)
        body.write(0xFE)
        body.write(0)          // 空内容描述符（UTF-16 空串 + 结束符）
        body.write(0)
        body.write(lyrics.toByteArray(Charsets.UTF_16LE))
        writeFrame(out, "USLT", body.toByteArray())
    }

    private fun addApicFrame(out: ByteArrayOutputStream, jpeg: ByteArray) {
        val body = ByteArrayOutputStream()
        body.write(1) // 描述符编码：UTF-16 带 BOM
        body.write("image/jpeg".toByteArray(Charsets.US_ASCII))
        body.write(0) // MIME 结束符
        body.write(3) // 图片类型 3 = front cover
        body.write(0xFF)
        body.write(0xFE)
        body.write("Cover".toByteArray(Charsets.UTF_16LE))
        body.write(0) // 描述符结束符（UTF-16）
        body.write(0)
        body.write(jpeg)
        writeFrame(out, "APIC", body.toByteArray())
    }

    private fun writeFrame(out: ByteArrayOutputStream, id: String, body: ByteArray) {
        out.write(id.toByteArray(Charsets.US_ASCII))
        val size = body.size
        out.write((size shr 24) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write(size and 0xFF)
        out.write(0) // flags
        out.write(0)
        out.write(body)
    }
}
