package com.zhaoguanghao.earbugmusic.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R

/** 歌词列表：当前行放大、变亮，其余行降低透明度 */
class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.VH>() {

    private val lines = mutableListOf<String>()
    private var active = -1

    fun submit(list: List<String>) {
        lines.clear()
        lines.addAll(list)
        active = -1
        notifyDataSetChanged()
    }

    fun activeIndex(): Int = active

    fun setActive(index: Int) {
        if (index == active) return
        val old = active
        active = index
        if (old in lines.indices) notifyItemChanged(old)
        if (index in lines.indices) notifyItemChanged(index)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvLyricLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = lines.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = lines[position]
        val isActive = position == active
        val ctx = holder.tv.context
        holder.tv.setTextColor(
            if (isActive) ContextCompat.getColor(ctx, R.color.primary)
            else ContextCompat.getColor(ctx, R.color.text_secondary)
        )
        // 行高固定，字号变化不再影响布局；放大后也不会被裁剪
        holder.tv.textSize = if (isActive) 19f else 16f
        holder.tv.alpha = if (isActive) 1f else 0.45f
        holder.tv.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.tv.scaleX = 1f
        holder.tv.scaleY = 1f
    }
}
