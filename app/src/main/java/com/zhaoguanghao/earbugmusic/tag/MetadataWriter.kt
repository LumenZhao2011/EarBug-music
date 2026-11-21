package com.zhaoguanghao.earbugmusic.tag

import com.zhaoguanghao.earbugmusic.model.AudioTags
import java.io.File

/** 按扩展名分发元数据写入 */
object MetadataWriter {
    fun write(file: File, tags: AudioTags): Boolean = when (file.extension.lowercase()) {
        "flac" -> FlacTagger.write(file, tags)
        "mp3" -> Id3Tagger.write(file, tags)
        else -> false
    }
}
