package com.zhaoguanghao.earbugmusic.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zhaoguanghao.earbugmusic.R

/**
 * 淡灰底 + 深色字的双曲线弹窗（选项 / 确认），替代系统白色直角弹窗。
 */
object LightDialog {

    private fun color(ctx: Context, id: Int) = ContextCompat.getColor(ctx, id)

    /** 收起动画后关闭（短促，避免“迟钝感”） */
    private fun dismissWithAnim(dialog: Dialog, content: LinearLayout) {
        content.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        content.animate()
            .alpha(0f)
            .translationY(24f * content.resources.displayMetrics.density)
            .setDuration(110)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                content.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                runCatching { dialog.dismiss() }
            }
            .start()
    }
    private fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun card(ctx: Context): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // 用 XML 圆角形状，确保任何设备都能显示底色
            setBackgroundResource(R.drawable.bg_dialog_light)
            setPadding(0, dp(ctx, 10f), 0, dp(ctx, 10f))
        }
        return box
    }

    private fun newDialog(activity: AppCompatActivity, content: LinearLayout): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(content)
        dialog.window?.setWindowAnimations(0)
        // 弹出动画：等首帧布局完成后再开始，并走硬件层，避免掉帧
        dialog.setOnShowListener {
            content.post {
                val offset = 36f * content.resources.displayMetrics.density
                content.alpha = 0f
                content.translationY = offset
                content.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .setInterpolator(
                        androidx.core.view.animation.PathInterpolatorCompat.create(0.0f, 0f, 0.2f, 1f)
                    )
                    .withEndAction {
                        content.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                    }
                    .start()
            }
        }
        dialog.window?.let { w ->
            // 窗口与内容都设置淡灰底，确保任何设备都能显示
            w.setBackgroundDrawableResource(R.drawable.bg_dialog_light)
            w.setDimAmount(0.5f)
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.86).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }

    /** 选项弹窗 */
    fun choose(
        activity: AppCompatActivity,
        title: String,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ) {
        val ctx = activity
        val box = card(ctx)

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(color(ctx, R.color.light_text_secondary))
            textSize = 13f
            setPadding(dp(ctx, 26f), dp(ctx, 8f), dp(ctx, 26f), dp(ctx, 6f))
        }
        box.addView(tvTitle)

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        options.forEachIndexed { index, label ->
            val row = TextView(ctx).apply {
                text = label
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 26f), dp(ctx, 15f), dp(ctx, 26f), dp(ctx, 15f))
                setTextColor(
                    if (index == selected) color(ctx, R.color.primary) else color(ctx, R.color.light_text)
                )
                setTypeface(null, if (index == selected) Typeface.BOLD else Typeface.NORMAL)
            }
            list.addView(row)
        }
        box.addView(list)

        val dialog = newDialog(activity, box)
        options.forEachIndexed { index, _ ->
            (list.getChildAt(index) as TextView).setOnClickListener {
                dismissWithAnim(dialog, box)
                onPick(index)
            }
        }
        dialog.show()
    }

    /** 确认弹窗 */
    fun confirm(
        activity: AppCompatActivity,
        title: String,
        message: String,
        positive: String = "确认",
        negative: String = "取消",
        onConfirm: () -> Unit
    ) {
        val ctx = activity
        val box = card(ctx)

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(color(ctx, R.color.light_text))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(ctx, 26f), dp(ctx, 16f), dp(ctx, 26f), dp(ctx, 8f))
        }
        val tvMsg = TextView(ctx).apply {
            text = message
            setTextColor(color(ctx, R.color.light_text_secondary))
            textSize = 14f
            setPadding(dp(ctx, 26f), 0, dp(ctx, 26f), dp(ctx, 20f))
        }
        box.addView(tvTitle)
        box.addView(tvMsg)

        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 16f), 0, dp(ctx, 16f), dp(ctx, 8f))
        }
        val btnCancel = TextView(ctx).apply {
            text = negative
            textSize = 15f
            setTextColor(color(ctx, R.color.light_text_secondary))
            setPadding(dp(ctx, 18f), dp(ctx, 10f), dp(ctx, 18f), dp(ctx, 10f))
        }
        val btnOk = TextView(ctx).apply {
            text = positive
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(color(ctx, R.color.primary))
            setPadding(dp(ctx, 18f), dp(ctx, 10f), dp(ctx, 18f), dp(ctx, 10f))
        }
        buttons.addView(btnCancel)
        buttons.addView(btnOk)
        box.addView(buttons)

        val dialog = newDialog(activity, box)
        btnCancel.setOnClickListener { dismissWithAnim(dialog, box) }
        btnOk.setOnClickListener {
            dismissWithAnim(dialog, box)
            onConfirm()
        }
        dialog.show()
    }
}
