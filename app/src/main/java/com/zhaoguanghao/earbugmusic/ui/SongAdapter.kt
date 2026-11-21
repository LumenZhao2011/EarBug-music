package com.zhaoguanghao.earbugmusic.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.ui.Squircle
import com.zhaoguanghao.earbugmusic.util.ImageLoader

/** 歌曲卡片适配器：点击卡片播放，点击下载按钮入队 */
class SongAdapter(
    private val onPlay: (Song) -> Unit,
    private val onDownload: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private val items = mutableListOf<Song>()

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivCover)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val btnDownload: Button = view.findViewById(R.id.btnDownload)
        val btnPlay: Button = view.findViewById(R.id.btnPlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = items[position]
        holder.tvName.text = song.name

        val label = "  " + song.platform.label
        val spannable = SpannableString(song.artist + label)
        spannable.setSpan(
            ForegroundColorSpan(song.platform.color),
            song.artist.length,
            song.artist.length + label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        holder.tvArtist.text = spannable

        ImageLoader.load(holder.ivCover, song.coverUrl)
        // 双曲线圆角
        Squircle.bg(holder.itemView, Squircle.color(holder.itemView, R.color.surface), 26f)
        Squircle.bg(
            holder.btnPlay, android.graphics.Color.TRANSPARENT, 20f,
            Squircle.color(holder.btnPlay, R.color.primary), 1f
        )
        Squircle.bg(holder.btnDownload, Squircle.color(holder.btnDownload, R.color.primary), 20f)
        holder.btnPlay.setOnClickListener { onPlay(song) }
        holder.btnDownload.setOnClickListener { onDownload(song) }
    }
}
