package com.zhaoguanghao.earbugmusic.util

import android.content.Context
import android.widget.Toast

/**
 * 轻提示（Toast）开关。
 * 操作成功的提示默认关闭，避免频繁弹窗打扰；错误/引导类提示仍会显示。
 */
object Tips {

    /** 设为 true 可恢复「已加入下载队列」这类成功提示 */
    @Volatile
    var enabled: Boolean = false

    fun success(context: Context, message: String) {
        if (!enabled) return
        show(context, message)
    }

    fun show(context: Context, message: String) {
        try {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }
}
