package com.zhaoguanghao.earbugmusic.model

/** 平台音质选项：按平台官方叫法展示 */
data class QualityOption(val quality: Quality, val label: String)

object QualityCatalog {

    fun options(platform: Platform): List<QualityOption> = listOf(
        QualityOption(Quality.STANDARD, "标准"),
        QualityOption(Quality.HIGH, "较高"),
        QualityOption(Quality.EXHIGH, "极高"),
        QualityOption(Quality.LOSSLESS, "无损"),
        QualityOption(Quality.HIRES, "Hi-Res"),
        QualityOption(Quality.MASTER, "超清母带")
    )

    fun defaultOption(platform: Platform): QualityOption = options(platform).last()

    fun labelOf(platform: Platform, quality: Quality): String =
        options(platform).firstOrNull { it.quality == quality }?.label ?: quality.name
}
