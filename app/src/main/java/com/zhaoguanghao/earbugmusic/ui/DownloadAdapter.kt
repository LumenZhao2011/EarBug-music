package com.zhaoguanghao.earbugmusic.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R
import com.zhaoguanghao.earbugmusic.download.DownloadStatus
import com.zhaoguanghao.earbugmusic.download.DownloadTask
import com.zhaoguanghao.earbugmusic.util.CoverUrl
import com.zhaoguanghao.earbugmusic.util.ImageLoader

/**
 * 下载任务列表：封面 + 进度 + 播放 / 重试 / 取消。
 */
class DownloadAdapter(
    private val onPlay: (DownloadTask) -> Unit = {},
    private val onRetry: (DownloadTask) -> Unit = {},
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

    fun snapshot(): List<DownloadTask> = items.toList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivDlCover)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvPercent: TextView = view.findViewById(R.id.tvPercent)
        val btnPlay: ImageButton = view.findViewById(R.id.btnItemPlay)
        val btnRetry: ImageButton = view.findViewById(R.id.btnItemRetry)
        val btnDelete: ImageButton = view.findViewById(R.id.btnItemDelete)
        val tvQuality: TextView = view.findViewById(R.id.tvQuality)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        val holder = VH(v)
        holder.btnPlay.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) items.getOrNull(pos)?.let(onPlay)
        }
        holder.btnRetry.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) items.getOrNull(pos)?.let(onRetry)
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
        holder.tvMeta.text = t.song.artist
        holder.tvQuality.text = t.qualityText
        holder.tvStatus.text = t.statusText

        holder.ivCover.setImageResource(R.drawable.ic_cover_placeholder)
        ImageLoader.load(holder.ivCover, CoverUrl.thumb(t.song.coverUrl, 260))

        Squircle.bg(holder.itemView, Squircle.color(holder.itemView, R.color.surface), 26f)

        holder.progressBar.progress = (t.progress * 1000).toInt()
        holder.progressBar.max = 1000
        holder.tvPercent.text = String.format("%d%%", (t.progress * 100).toInt())

        val running = t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.PARSING
        holder.tvPercent.visibility = if (running) View.VISIBLE else View.GONE
        holder.progressBar.visibility = if (running) View.VISIBLE else View.GONE

        val done = t.status == DownloadStatus.SUCCESS
        val failed = t.status == DownloadStatus.FAILED
        holder.btnPlay.visibility = if (done) View.VISIBLE else View.GONE
        holder.btnRetry.visibility = if (failed) View.VISIBLE else View.GONE
        holder.btnDelete.setImageResource(if (done) R.drawable.ic_delete else R.drawable.ic_close)
    }
}
