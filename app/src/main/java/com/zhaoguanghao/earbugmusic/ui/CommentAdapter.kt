package com.zhaoguanghao.earbugmusic.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhaoguanghao.earbugmusic.R
import com.zhaoguanghao.earbugmusic.model.Comment

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.VH>() {

    private val items = mutableListOf<Comment>()

    fun submit(list: List<Comment>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvUser: TextView = view.findViewById(R.id.tvUser)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvLiked: TextView = view.findViewById(R.id.tvLiked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvUser.text = c.user
        holder.tvTime.text = c.timeText
        holder.tvContent.text = c.content
        holder.tvLiked.text = if (c.liked > 0) "♥ " + c.liked else ""
    }
}
