package com.zhaoguanghao.earbugmusic.model

/** 输入模式：歌曲 / 专辑 / 歌单 / 热歌榜（均可批量下载专辑、歌单、榜单） */
enum class ParseMode(val label: String, val hint: String) {
    SONG("歌曲", "输入歌曲 ID 或歌曲链接，或关键词搜索"),
    ALBUM("专辑", "输入专辑 ID 或专辑链接"),
    PLAYLIST("歌单", "输入歌单 ID 或歌单链接"),
    TOPLIST("热歌榜", "点击开始解析热歌榜");
}
