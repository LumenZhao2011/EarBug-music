package com.zhaoguanghao.earbugmusic

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zhaoguanghao.earbugmusic.download.DownloadManager
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.ui.CommentsDialog
import com.zhaoguanghao.earbugmusic.util.ImageLoader
import kotlinx.coroutines.launch

/**
 * 播放页控制器：只负责渲染，播放状态来自 PlayerCore（与通知栏共享）。
 */
class PlaybackController(
    private val activity: AppCompatActivity,
    page: View
) {
    private val ivCover = page.findViewById<ImageView>(R.id.ivCover)
    private val tvTitle = page.findViewById<TextView>(R.id.tvTitle)
    private val tvArtist = page.findViewById<TextView>(R.id.tvArtist)
    private val tvQuality = page.findViewById<TextView>(R.id.tvQuality)
    private val tvLyric = page.findViewById<TextView>(R.id.tvLyric)
    private val tvStatus = page.findViewById<TextView>(R.id.tvStatus)
    private val tvCurrent = page.findViewById<TextView>(R.id.tvCurrent)
    private val tvDuration = page.findViewById<TextView>(R.id.tvDuration)
    private val seekBar = page.findViewById<SeekBar>(R.id.seekBar)
    private val btnPlayPause = page.findViewById<ImageButton>(R.id.btnPlayPause)
    private val btnPrev = page.findViewById<ImageButton>(R.id.btnPrev)
    private val btnNext = page.findViewById<ImageButton>(R.id.btnNext)
    private val btnDownload = page.findViewById<Button>(R.id.btnDownload)
    private val btnComments = page.findViewById<ImageButton>(R.id.btnComments)
    private val btnLyrics = page.findViewById<ImageButton>(R.id.btnLyrics)
    private val btnFav = page.findViewById<ImageButton>(R.id.btnFav)
    private val tvPlayMode = page.findViewById<TextView>(R.id.tvPlayMode)
    private val playerScroll = page.findViewById<android.widget.ScrollView>(R.id.playerScroll)

    // 歌词浮层
    private val lyricsPanel = page.findViewById<View>(R.id.lyricsPanel)
    private val tvLyricsSong = page.findViewById<TextView>(R.id.tvLyricsSong)
    private val rvLyrics = page.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvLyrics)
    private val tvLyricsEmpty = page.findViewById<TextView>(R.id.tvLyricsEmpty)
    private val lyricsAdapter = com.zhaoguanghao.earbugmusic.ui.LyricsAdapter { timeMs ->
        PlayerCore.seekTo(timeMs)
        lastLyricScroll = -1
        scrollToCurrent(false)
    }
    private val btnCloseLyrics = page.findViewById<ImageButton>(R.id.btnCloseLyrics)
    private var lyricsShown = false
    private var lastLyricScroll = -1
    private var autoScrollPauseUntil = 0L

    private var lastSongId: String? = null
    private var dragging = false
    private var quality: Quality = Quality.LOSSLESS

    init {
        btnPlayPause.setOnClickListener {
            // 停止/冷启动恢复后没有底层播放器：重新装载，保证通知服务同步启动
            if (!PlayerCore.hasPlayer()) {
                val song = PlayerCore.currentSong()
                if (song == null) {
                    Toast.makeText(activity, "请先在搜索页选择歌曲", Toast.LENGTH_SHORT).show()
                } else {
                    load(song, quality)
                }
            } else {
                PlayerCore.toggle()
            }
        }
        btnPrev.setOnClickListener { PlayerCore.prev() }
        btnNext.setOnClickListener { PlayerCore.next() }
        btnDownload.setOnClickListener {
            val song = PlayerCore.currentSong()
            if (song == null) {
                Toast.makeText(activity, "请先选择歌曲", Toast.LENGTH_SHORT).show()
            } else if (!com.zhaoguanghao.earbugmusic.util.StoragePermission.canWrite(activity)) {
                Toast.makeText(activity, "请允许存储权限，文件才能保存到 Download 文件夹", Toast.LENGTH_SHORT).show()
                com.zhaoguanghao.earbugmusic.util.StoragePermission.request(activity)
            } else {
                DownloadManager.enqueue(activity.applicationContext, song, quality)
                com.zhaoguanghao.earbugmusic.util.Tips.success(activity, "已加入下载队列")
            }
        }
        btnComments.setOnClickListener {
            val song = PlayerCore.currentSong()
            if (song == null) {
                Toast.makeText(activity, "请先播放一首歌曲", Toast.LENGTH_SHORT).show()
            } else {
                CommentsDialog.show(activity, song)
            }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) tvCurrent.text = format(p.toLong())
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                dragging = true
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                dragging = false
                PlayerCore.seekTo(sb.progress.toLong())
            }
        })
        tvStatus.text = "选择歌曲开始播放"

        PlayerCore.attachContext(activity.applicationContext)

        // 上/下滑切换歌词页（不要求甩动速度，慢速滑动也能触发）
        val swipeListener = object : View.OnTouchListener {
            private var downY = 0f
            private var downX = 0f
            private var downAt = 0L

            override fun onTouch(v: View, ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        downX = ev.rawX
                        downAt = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = ev.rawY - downY
                        val dx = ev.rawX - downX
                        val quick = System.currentTimeMillis() - downAt < 1000
                        // 只有「下滑」打开歌词；上滑不触发（歌词页内上滑用于收起）
                        if (quick && kotlin.math.abs(dy) > 110 &&
                            kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f
                        ) {
                            if (dy < 0) {
                                // 上滑打开歌词：仅在播放页滚到顶部时才触发，避免和滚动冲突
                                if (!lyricsShown && v === playerScroll &&
                                    !playerScroll.canScrollVertically(-1)
                                ) {
                                    showLyrics()
                                }
                            } else if (lyricsShown && v !== playerScroll) {
                                // 歌词页下滑收起（仅当歌词列表已在顶部）
                                if (!rvLyrics.canScrollVertically(-1)) hideLyrics()
                            }
                        }
                    }
                }
                return false
            }
        }
        playerScroll.setOnTouchListener(swipeListener)
        lyricsPanel.setOnTouchListener(swipeListener)
        btnLyrics.setOnClickListener { if (lyricsShown) hideLyrics() else showLyrics() }
        btnFav.setOnClickListener {
            val song = PlayerCore.currentSong()
            if (song == null) {
                Toast.makeText(activity, "请先播放一首歌曲", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val added = com.zhaoguanghao.earbugmusic.util.Favorites.toggle(activity, song)
            btnFav.setImageResource(
                if (added) R.drawable.ic_heart_filled else R.drawable.ic_heart
            )
            com.zhaoguanghao.earbugmusic.util.Tips.success(
                activity,
                if (added) "已收藏" else "已取消收藏"
            )
        }
        btnCloseLyrics.setOnClickListener { hideLyrics() }

        // 播放模式菜单（淡灰双曲线弹窗）
        tvPlayMode.setOnClickListener {
            val modes = PlayerCore.PlayMode.entries
            com.zhaoguanghao.earbugmusic.ui.LightDialog.choose(
                activity, "播放模式", modes.map { it.label }, modes.indexOf(PlayerCore.playMode)
            ) { index ->
                PlayerCore.setPlayMode(modes[index])
            }
        }
    }

    // ==================== 歌词页 ====================

    private fun showLyrics() {
        val song = PlayerCore.currentSong()
        tvLyricsSong.text = song?.let { it.name + " · " + it.artist } ?: ""
        if (rvLyrics.adapter == null) {
            rvLyrics.layoutManager = LinearLayoutManager(activity)
            rvLyrics.adapter = lyricsAdapter
            rvLyrics.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                        autoScrollPauseUntil = System.currentTimeMillis() + 4000
                    }
                }
            })
        }
        fillLyrics()
        lyricsPanel.translationY = activity.resources.displayMetrics.heightPixels * 0.12f
        lyricsPanel.alpha = 0f
        lyricsPanel.visibility = View.VISIBLE
        lyricsPanel.animate().translationY(0f).alpha(1f).setDuration(240).start()
        lyricsShown = true
        rvLyrics.post { scrollToCurrent(false) }
    }

    private fun fillLyrics() {
        val lines = PlayerCore.lyricLines()
        lyricsAdapter.submit(lines)
        lastLyricScroll = -1
        tvLyricsEmpty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        rvLyrics.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    /** 返回键优先关闭歌词页；返回 true 表示已处理 */
    fun closeLyricsIfShown(): Boolean {
        if (!lyricsShown) return false
        hideLyrics()
        return true
    }

    private fun hideLyrics() {
        lyricsPanel.animate()
            .translationY(lyricsPanel.height * 0.2f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction { lyricsPanel.visibility = View.GONE }
            .start()
        lyricsShown = false
    }

    private fun scrollToCurrent(smooth: Boolean = true) {
        if (!lyricsShown) return
        val index = PlayerCore.currentLyricIndex(PlayerCore.state.value.positionMs)
        if (index < 0) return
        lyricsAdapter.setActive(index)
        if (System.currentTimeMillis() < autoScrollPauseUntil) return
        if (index == lastLyricScroll && smooth) return
        lastLyricScroll = index
        centerOn(index, smooth)
    }

    /** 把指定歌词行滚动到屏幕中央（平滑） */
    private fun centerOn(index: Int, smooth: Boolean) {
        val lm = rvLyrics.layoutManager as? LinearLayoutManager ?: return
        if (index !in 0 until lyricsAdapter.itemCount) return
        val holder = rvLyrics.findViewHolderForAdapterPosition(index)
        if (holder == null) {
            // 目标行不在可见范围：先粗定位再居中
            lm.scrollToPositionWithOffset(index, rvLyrics.height / 2)
            return
        }
        val itemHeight = holder.itemView.height
        if (itemHeight <= 0) return
        val targetTop = (rvLyrics.height - itemHeight) / 2
        val dy = holder.itemView.top - targetTop
        if (kotlin.math.abs(dy) < 2) return
        if (smooth) {
            rvLyrics.smoothScrollBy(0, dy)
        } else {
            rvLyrics.scrollBy(0, dy)
        }
    }

    /** 开始观察播放状态 */
    fun observe() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlayerCore.state.collect { st ->
                    val song = st.song
                    if (song != null && song.id != lastSongId) {
                        lastSongId = song.id
                        ivCover.setImageResource(R.drawable.ic_cover_placeholder)
                        ImageLoader.load(ivCover, com.zhaoguanghao.earbugmusic.util.CoverUrl.original(song.coverUrl))
                        val fav = com.zhaoguanghao.earbugmusic.util.Favorites.isFavorite(activity, song.id)
                        btnFav.setImageResource(
                            if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart
                        )
                        if (lyricsShown) {
                            tvLyricsSong.text = song.name + " · " + song.artist
                            fillLyrics()
                        }
                    }
                    if (lyricsShown) {
                        // 切歌后歌词列表长度变化时重新填充，避免“歌词卡没”
                        if (PlayerCore.lyricLines().size != lyricsAdapter.itemCountSafe()) {
                            fillLyrics()
                        }
                        scrollToCurrent()
                    }
                    tvTitle.text = song?.name ?: "未在播放"
                    tvArtist.text = song?.artist ?: ""
                    tvQuality.text = st.quality
                    tvStatus.text = st.status
                    tvLyric.text = st.lyricText
                    if (!dragging && st.durationMs > 0) {
                        seekBar.max = st.durationMs.toInt()
                        seekBar.progress = st.positionMs.toInt()
                        tvCurrent.text = format(st.positionMs)
                        tvDuration.text = format(st.durationMs)
                    }
                    btnPlayPause.setImageResource(
                        if (st.playing) R.drawable.ic_pause else R.drawable.ic_play
                    )
                    tvPlayMode.text = st.playMode.label
                }
            }
        }
    }

    /** 同步音质选择（下载按钮使用） */
    fun setQuality(q: Quality) {
        quality = q
    }

    /** 刷新收藏图标（进入播放页时调用） */
    fun refreshFavorite() {
        val song = PlayerCore.currentSong() ?: return
        val fav = com.zhaoguanghao.earbugmusic.util.Favorites.isFavorite(activity, song.id)
        btnFav.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
    }

    /** 从列表播放某首歌（同时启动通知栏服务） */
    fun load(song: Song, q: Quality) {
        quality = q
        PlayerCore.load(song, q)
        PlaybackService.start(activity)
    }

    private fun format(ms: Long): String {
        val total = (ms / 1000).toInt()
        return String.format("%02d:%02d", total / 60, total % 60)
    }
}
