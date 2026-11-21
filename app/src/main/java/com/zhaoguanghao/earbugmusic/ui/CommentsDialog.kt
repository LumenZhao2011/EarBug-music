package com.zhaoguanghao.earbugmusic.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.parser.NeteaseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 网易云歌曲评论区弹窗（浅底黑字） */
object CommentsDialog {

    fun show(activity: AppCompatActivity, song: Song) {
        val density = activity.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.zhaoguanghao.earbugmusic.R.drawable.bg_dialog_light)
            setPadding(pad, pad, pad, (12 * density).toInt())
        }
        val title = TextView(activity).apply {
            text = "评论 · " + song.name
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val status = TextView(activity).apply {
            text = "加载中…"
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }
        val adapter = CommentAdapter()
        val rv = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        val close = TextView(activity).apply {
            text = "关闭"
            setTextColor(Color.parseColor("#E8352C"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }

        box.addView(title)
        box.addView(status)
        box.addView(
            rv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * 0.55).toInt()
            )
        )
        box.addView(close)

        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        close.setOnClickListener { dialog.dismiss() }
        dialog.show()

        activity.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { NeteaseParser.comments(song.id) }
                adapter.submit(list)
                status.text = "共 " + list.size + " 条"
            } catch (e: Exception) {
                status.text = e.message ?: "获取评论失败"
            }
        }
    }
}
