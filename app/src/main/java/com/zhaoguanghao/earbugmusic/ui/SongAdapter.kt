package com.zhaoguanghao.earbugmusic.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.util.CoverUrl
import com.zhaoguanghao.earbugmusic.util.Favorites
import com.zhaoguanghao.earbugmusic.util.ImageLoader

/** 歌曲卡片：播放 / 下载 / 收藏 */
class SongAdapter(
    private val onPlay: (Song) -> Unit,
    private val onDownload: (Song) -> Unit,
    private val onFavoriteChanged: (Song, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private val items = mutableListOf<Song>()

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun snapshot(): List<Song> = items.toList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivCover)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val btnDownload: Button = view.findViewById(R.id.btnDownload)
        val btnPlay: Button = view.findViewById(R.id.btnPlay)
        val btnFav: ImageButton = view.findViewById(R.id.btnFav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = items[position]
        holder.tvName.text = song.name
        holder.tvArtist.text = song.artist

        holder.ivCover.setImageResource(R.drawable.ic_cover_placeholder)
        ImageLoader.load(holder.ivCover, CoverUrl.thumb(song.coverUrl, 400))

        Squircle.bg(holder.itemView, Squircle.color(holder.itemView, R.color.surface), 26f)
        Squircle.bg(
            holder.btnPlay, android.graphics.Color.TRANSPARENT, 20f,
            Squircle.color(holder.btnPlay, R.color.primary), 1f
        )
        Squircle.bg(holder.btnDownload, Squircle.color(holder.btnDownload, R.color.primary), 20f)

        val ctx = holder.itemView.context
        val fav = Favorites.isFavorite(ctx, song.id)
        holder.btnFav.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart)

        holder.btnPlay.setOnClickListener { onPlay(song) }
        holder.btnDownload.setOnClickListener { onDownload(song) }
        holder.btnFav.setOnClickListener {
            val added = Favorites.toggle(ctx, song)
            holder.btnFav.setImageResource(
                if (added) R.drawable.ic_heart_filled else R.drawable.ic_heart
            )
            onFavoriteChanged(song, added)
        }
    }
}
