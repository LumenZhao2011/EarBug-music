package com.zhaoguanghao.earbugmusic.model

/**
 * 音质等级（按由低到高排序，各平台在其官方叫法下映射到对应等级）。
 */
enum class Quality(val rank: Int) {
    STANDARD(0),   // 标准
    HIGH(1),       // 较高（网易云）/ 高品质（QQ）
    EXHIGH(2),     // 极高（网易云）
    LOSSLESS(3),   // 无损（网易云/酷狗）/ SQ 无损（QQ）
    HIRES(4),      // Hi-Res
    MASTER(5)      // 超清母带（网易云）/ 臻品母带（QQ）
}
