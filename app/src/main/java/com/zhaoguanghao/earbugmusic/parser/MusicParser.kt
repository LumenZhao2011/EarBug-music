package com.zhaoguanghao.earbugmusic.parser

import com.zhaoguanghao.earbugmusic.model.ParseResult
import com.zhaoguanghao.earbugmusic.model.Platform
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song

/** 单平台解析器接口：搜索 + 解析直链 */
interface MusicParser {
    val platform: Platform

    suspend fun search(keyword: String, limit: Int = 30): List<Song>

    suspend fun resolve(song: Song, quality: Quality): ParseResult
}
