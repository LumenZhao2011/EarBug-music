package com.zhaoguanghao.earbugmusic.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.PlayerCore
import com.zhaoguanghao.earbugmusic.R

/**
 * 歌词列表：每行「原文 + 翻译」，当前行放大变红，其余灰暗。
 */
class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.VH>() {

    private val lines = mutableListOf<PlayerCore.LyricLine>()
    private var active = -1

    fun submit(list: List<PlayerCore.LyricLine>) {
        lines.clear()
        lines.addAll(list)
        active = -1
        notifyDataSetChanged()
    }

    fun itemCountSafe(): Int = lines.size

    fun setActive(index: Int) {
        if (index == active) return
        val old = active
        active = index
        if (old in lines.indices) notifyItemChanged(old)
        if (index in lines.indices) notifyItemChanged(index)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLine: TextView = view.findViewById(R.id.tvLyricLine)
        val tvTrans: TextView = view.findViewById(R.id.tvLyricTrans)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = lines.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = lines[position]
        val isActive = position == active
        val ctx = holder.tvLine.context

        holder.tvLine.text = line.text
        holder.tvLine.textSize = if (isActive) 19f else 16f
        holder.tvLine.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (isActive) R.color.primary else R.color.text_secondary
            )
        )
        holder.tvLine.alpha = if (isActive) 1f else 0.45f
        holder.tvLine.setTypeface(
            null,
            if (isActive) Typeface.BOLD else Typeface.NORMAL
        )

        val trans = line.trans
        if (!trans.isNullOrEmpty()) {
            holder.tvTrans.visibility = View.VISIBLE
            holder.tvTrans.text = trans
            holder.tvTrans.textSize = if (isActive) 15f else 13f
            holder.tvTrans.alpha = if (isActive) 0.9f else 0.4f
        } else {
            holder.tvTrans.visibility = View.GONE
            holder.tvTrans.text = ""
        }
    }
}
