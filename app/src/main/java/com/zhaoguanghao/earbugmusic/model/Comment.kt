package com.zhaoguanghao.earbugmusic.model

/** 网易云歌曲评论 */
data class Comment(
    val user: String,
    val avatar: String,
    val content: String,
    val liked: Int,
    val timeText: String
)
