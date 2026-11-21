package com.zhaoguanghao.earbugmusic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** 圆角裁剪的 ImageView（缩略图用） */
class RoundImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** 圆角半径（px），默认 14dp */
    var cornerRadius: Float =
        context.resources.displayMetrics.density * 14f
        set(value) {
            field = value
            invalidate()
        }

    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return super.onDraw(canvas)
        val r = min(cornerRadius, min(width, height) / 2f)
        if (r <= 0f) return super.onDraw(canvas)
        clipPath.reset()
        clipPath.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(), r, r, Path.Direction.CW
        )
        val save = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }
}
