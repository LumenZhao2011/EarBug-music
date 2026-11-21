package com.zhaoguanghao.earbugmusic.parser

import com.zhaoguanghao.earbugmusic.model.Platform

/** 解析器注册表（仅网易云） */
object ParserRegistry {
    val all: List<MusicParser> = listOf(NeteaseParser)

    fun forPlatform(p: Platform): MusicParser = all.first { it.platform == p }
}
