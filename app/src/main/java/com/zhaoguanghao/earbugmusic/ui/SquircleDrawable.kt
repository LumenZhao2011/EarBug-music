package com.zhaoguanghao.earbugmusic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat

/**
 * 圆角背景（基于系统 GradientDrawable，保证在所有设备渲染）。
 * 半径给得比普通卡片更大，视觉上接近 Apple 的连续圆角。
 */
class SquircleDrawable(
    private val fillColor: Int,
    private val radiusPx: Float,
    private val strokeColor: Int = Color.TRANSPARENT,
    private val strokeWidthPx: Float = 0f
) : Drawable() {

    private val delegate: GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        cornerRadius = radiusPx
        if (strokeWidthPx > 0f) {
            setStroke(strokeWidthPx.toInt().coerceAtLeast(1), strokeColor)
        }
    }

    override fun draw(canvas: Canvas) {
        delegate.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        delegate.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        delegate.colorFilter = cf
    }

    override fun getOpacity(): Int = delegate.opacity

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        delegate.setBounds(left, top, right, bottom)
    }
}

/** 便捷设置圆角背景 */
object Squircle {
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun bg(
        view: View,
        fillColor: Int,
        radiusDp: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Float = 0f
    ) {
        val ctx = view.context
        view.background = SquircleDrawable(
            fillColor,
            dp(ctx, radiusDp),
            strokeColor,
            dp(ctx, strokeDp)
        )
    }

    fun color(view: View, resId: Int): Int = ContextCompat.getColor(view.context, resId)
}
