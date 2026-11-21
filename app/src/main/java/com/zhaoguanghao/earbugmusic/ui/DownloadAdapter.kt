package com.zhaoguanghao.earbugmusic.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R
import com.zhaoguanghao.earbugmusic.download.DownloadStatus
import com.zhaoguanghao.earbugmusic.download.DownloadTask

/**
 * 下载任务列表：展示进度/品质，支持播放本地文件与取消/删除。
 * 使用稳定 ID + 局部刷新，避免高频进度更新打断按钮点击。
 */
class DownloadAdapter(
    private val onPlay: (DownloadTask) -> Unit = {},
    private val onDelete: (DownloadTask) -> Unit = {}
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    private val items = mutableListOf<DownloadTask>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    fun submit(list: List<DownloadTask>) {
        val sameIds = items.size == list.size &&
            items.indices.all { items[it].id == list[it].id }
        if (sameIds) {
            for (i in items.indices) {
                if (items[i] != list[i]) {
                    items[i] = list[i]
                    notifyItemChanged(i)
                }
            }
            return
        }
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvPercent: TextView = view.findViewById(R.id.tvPercent)
        val btnPlay: ImageButton = view.findViewById(R.id.btnItemPlay)
        val btnDelete: ImageButton = view.findViewById(R.id.btnItemDelete)
        val tvQuality: TextView = view.findViewById(R.id.tvQuality)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        val holder = VH(v)
        // 监听器只注册一次，后续刷新不会覆盖
        holder.btnPlay.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) items.getOrNull(pos)?.let(onPlay)
        }
        holder.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) items.getOrNull(pos)?.let(onDelete)
        }
        return holder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.tvName.text = t.song.name

        val label = "  " + t.song.platform.label
        val spannable = SpannableString(t.song.artist + label)
        spannable.setSpan(
            ForegroundColorSpan(t.song.platform.color),
            t.song.artist.length,
            t.song.artist.length + label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        holder.tvMeta.text = spannable
        Squircle.bg(holder.itemView, Squircle.color(holder.itemView, R.color.surface), 26f)
        holder.tvQuality.text = t.qualityText
        holder.tvStatus.text = t.statusText
        holder.progressBar.progress = (t.progress * 1000).toInt()
        holder.progressBar.max = 1000
        holder.tvPercent.text = String.format("%d%%", (t.progress * 100).toInt())

        val running = t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.PARSING
        holder.tvPercent.visibility = if (running) View.VISIBLE else View.GONE
        holder.progressBar.visibility = if (running) View.VISIBLE else View.GONE

        val done = t.status == DownloadStatus.SUCCESS
        holder.btnPlay.visibility = if (done) View.VISIBLE else View.GONE
        // 完成后 ✖ 换成垃圾桶
        holder.btnDelete.setImageResource(if (done) R.drawable.ic_delete else R.drawable.ic_close)
    }
}
