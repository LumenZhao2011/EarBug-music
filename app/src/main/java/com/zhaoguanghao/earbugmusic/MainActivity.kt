package com.zhaoguanghao.earbugmusic

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.zhaoguanghao.earbugmusic.download.DownloadManager
import com.zhaoguanghao.earbugmusic.model.ParseMode
import com.zhaoguanghao.earbugmusic.model.Platform
import com.zhaoguanghao.earbugmusic.model.Quality
import com.zhaoguanghao.earbugmusic.model.QualityCatalog
import com.zhaoguanghao.earbugmusic.model.QualityOption
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.parser.LinkParser
import com.zhaoguanghao.earbugmusic.parser.NeteaseParser
import com.zhaoguanghao.earbugmusic.ui.DownloadAdapter
import com.zhaoguanghao.earbugmusic.ui.SongAdapter
import kotlinx.coroutines.launch

/**
 * 耳虫音乐：仅网易云。
 *  - 模式：歌曲 / 专辑 / 歌单 / 热歌榜（专辑、歌单支持批量下载）
 *  - 音质：标准 / 较高 / 极高 / 无损 / Hi-Res / 超清母带
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pageSearch: android.view.View
    private lateinit var pagePlayer: android.view.View
    private lateinit var pageDownloads: android.view.View
    private lateinit var pageFavorites: android.view.View
    private lateinit var navItems: List<LinearLayout>
    private lateinit var navIcons: List<ImageView>
    private lateinit var navLabels: List<TextView>

    private lateinit var etInput: EditText
    private lateinit var actvMode: MaterialAutoCompleteTextView
    private lateinit var actvQuality: MaterialAutoCompleteTextView
    private lateinit var rvSongs: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: SongAdapter

    private var mode: ParseMode = ParseMode.SONG
    private var lastSeenSongId: String? = null
    private var qualityOptions: List<QualityOption> = emptyList()
    private var selectedQuality: Quality = Quality.LOSSLESS

    private lateinit var playback: PlaybackController

    private lateinit var downloadAdapter: DownloadAdapter
    private lateinit var tvDlEmpty: TextView

    private lateinit var favAdapter: SongAdapter
    private lateinit var tvFavEmpty: TextView
    private lateinit var tvFavCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pageSearch = findViewById(R.id.pageSearch)
        pagePlayer = findViewById(R.id.pagePlayer)
        pageDownloads = findViewById(R.id.pageDownloads)
        pageFavorites = findViewById(R.id.pageFavorites)
        navItems = listOf(
            findViewById(R.id.navSearch),
            findViewById(R.id.navPlay),
            findViewById(R.id.navDownloads),
            findViewById(R.id.navFav)
        )
        navIcons = listOf(
            findViewById(R.id.navSearchIcon),
            findViewById(R.id.navPlayIcon),
            findViewById(R.id.navDownloadsIcon),
            findViewById(R.id.navFavIcon)
        )
        navLabels = listOf(
            findViewById(R.id.navSearchLabel),
            findViewById(R.id.navPlayLabel),
            findViewById(R.id.navDownloadsLabel),
            findViewById(R.id.navFavLabel)
        )
        navItems.forEachIndexed { i, item -> item.setOnClickListener { selectTab(i) } }

        PlayerCore.attachContext(applicationContext)
        DownloadManager.restore(applicationContext)
        setupSearchPage()
        playback = PlaybackController(this, pagePlayer)
        playback.observe()
        setupDownloadsPage()
        setupFavoritesPage()
        PlayerCore.restoreSession()
        selectTab(0)
        requestNotificationPermission()

        // 切歌（含通知栏/耳机下一首）时，强制切到播放页，保证歌词跟着刷新
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PlayerCore.state.collect { st ->
                    val id = st.song?.id
                    if (id != null && id != lastSeenSongId) {
                        lastSeenSongId = id
                        if (st.playing || st.loading) selectTab(1)
                    }
                }
            }
        }
    }

    // ==================== Tab ====================

    private fun selectTab(index: Int) {
        // 切换页面时收起键盘
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            currentFocus?.clearFocus()
        } catch (_: Exception) {
        }
        val pages = listOf(pageSearch, pagePlayer, pageDownloads, pageFavorites)
        val target = pages[index]
        val alreadyVisible = target.visibility == android.view.View.VISIBLE
        pages.forEachIndexed { i, v ->
            if (i != index) v.visibility = android.view.View.GONE
        }
        target.visibility = android.view.View.VISIBLE
        if (index == 3) refreshFavorites()
        if (!alreadyVisible) {
            target.alpha = 0f
            target.translationY = 12f * resources.displayMetrics.density
            target.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            target.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(170)
                .setInterpolator(
                    androidx.core.view.animation.PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
                )
                .withEndAction { target.setLayerType(android.view.View.LAYER_TYPE_NONE, null) }
                .start()
        }

        val selectedColor = ContextCompat.getColor(this, R.color.primary)
        val normalColor = ContextCompat.getColor(this, R.color.text_secondary)
        navItems.forEachIndexed { i, item ->
            val selected = i == index
            item.setBackgroundResource(if (selected) R.drawable.bg_nav_selected else 0)
            navIcons[i].setColorFilter(if (selected) selectedColor else normalColor)
            navLabels[i].setTextColor(if (selected) selectedColor else normalColor)
        }
    }

    // ==================== 搜索页 ====================

    private fun setupSearchPage() {
        etInput = pageSearch.findViewById(R.id.etInput)
        actvMode = pageSearch.findViewById(R.id.actvMode)
        actvQuality = pageSearch.findViewById(R.id.actvQuality)
        rvSongs = pageSearch.findViewById(R.id.rvSongs)
        tvEmpty = pageSearch.findViewById(R.id.tvEmpty)
        progressBar = pageSearch.findViewById(R.id.progressBar)

        adapter = SongAdapter(
            onPlay = { song ->
                // 播放列表 = 当前页面的所有歌曲
                PlayerCore.setQueue(adapter.snapshot())
                playback.load(song, selectedQuality)
                selectTab(1)
            },
            onDownload = { song -> enqueueSong(song) },
            onFavoriteChanged = { _, _ -> refreshFavoritesIfVisible() }
        )
        rvSongs.layoutManager = LinearLayoutManager(this)
        rvSongs.adapter = adapter

        // 模式：使用自定义淡灰双曲线弹窗（禁用系统白色下拉）
        val modes = listOf(ParseMode.SONG, ParseMode.ALBUM, ParseMode.PLAYLIST)
        actvMode.setText(mode.label, false)
        bindPickerField(actvMode) {
            com.zhaoguanghao.earbugmusic.ui.LightDialog.choose(
                this, "模式", modes.map { it.label }, modes.indexOf(mode)
            ) { index ->
                mode = modes[index]
                actvMode.setText(mode.label, false)
                etInput.hint = mode.hint
            }
        }
        etInput.hint = mode.hint

        // 音质：同样使用自定义弹窗；记住上次选择，首次默认「标准」
        qualityOptions = QualityCatalog.options(Platform.NETEASE)
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        selectedQuality = prefs.getString("quality", null)
            ?.let { name -> Quality.entries.firstOrNull { it.name == name } }
            ?: Quality.STANDARD
        val def = qualityOptions.firstOrNull { it.quality == selectedQuality } ?: qualityOptions.first()
        selectedQuality = def.quality
        actvQuality.setText(def.label, false)
        bindPickerField(actvQuality) {
            com.zhaoguanghao.earbugmusic.ui.LightDialog.choose(
                this, "音质", qualityOptions.map { it.label },
                qualityOptions.indexOfFirst { it.quality == selectedQuality }
            ) { index ->
                selectedQuality = qualityOptions[index].quality
                actvQuality.setText(qualityOptions[index].label, false)
                prefs.edit().putString("quality", selectedQuality.name).apply()
            }
        }

        // 热歌榜（右上角图表按钮）
        pageSearch.findViewById<ImageButton>(R.id.btnTopList).setOnClickListener { loadTopList() }

        pageSearch.findViewById<android.view.View>(R.id.searchBar).let { bar ->
            com.zhaoguanghao.earbugmusic.ui.Squircle.bg(
                bar,
                com.zhaoguanghao.earbugmusic.ui.Squircle.color(bar, R.color.surface),
                30f,
                com.zhaoguanghao.earbugmusic.ui.Squircle.color(bar, R.color.outline),
                1f
            )
        }

        pageSearch.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener { doAction() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                doAction()
                true
            } else false
        }
    }

    private fun adapterItems(): List<Song> = adapterSnapshot

    private var adapterSnapshot: List<Song> = emptyList()

    /** 统一入口：按模式解析/搜索 */
    private fun doAction() {
        val input = etInput.text.toString().trim()
        hideKeyboard()
        if (mode != ParseMode.TOPLIST && input.isEmpty()) {
            toastError("请输入内容")
            return
        }
        progressBar.visibility = android.view.View.VISIBLE
        tvEmpty.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                when (mode) {
                    ParseMode.SONG -> {
                        // 纯数字 ID / 链接 → 解析；否则搜索
                        val isIdOrLink = input.matches(Regex("\\d{3,}")) || LinkParser.looksLikeLink(input)
                        val songs = if (isIdOrLink) {
                            LinkParser.parse(ParseMode.SONG, input)
                        } else {
                            NeteaseParser.search(input)
                        }
                        showSongs(songs)
                        if (isIdOrLink) toast("已解析：" + songs.firstOrNull()?.name)
                    }
                    ParseMode.ALBUM -> {
                        val songs = LinkParser.parse(ParseMode.ALBUM, input)
                        showSongs(songs)
                        enqueueSongs(songs, "专辑")
                    }
                    ParseMode.PLAYLIST -> {
                        val songs = LinkParser.parse(ParseMode.PLAYLIST, input)
                        showSongs(songs)
                        enqueueSongs(songs, "歌单")
                    }
                    ParseMode.TOPLIST -> {
                        val songs = LinkParser.parse(ParseMode.TOPLIST, "")
                        showSongs(songs)
                        toast("热歌榜已加载 " + songs.size + " 首")
                    }
                }
            } catch (e: Exception) {
                toastError(e.message ?: "操作失败")
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun showSongs(songs: List<Song>) {
        adapterSnapshot = songs
        adapter.submit(songs)
        rvSongs.scrollToPosition(0)
        PlayerCore.setQueue(songs)
        tvEmpty.visibility = if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ==================== 下载页 ====================

    private fun setupDownloadsPage() {
        tvDlEmpty = pageDownloads.findViewById(R.id.tvDlEmpty)
        downloadAdapter = DownloadAdapter(
            onPlay = { task ->
                // 从下载页播放时，播放列表切换为「已下载完成」的曲目
                val downloaded = DownloadManager.tasks.value
                    .filter { it.status == com.zhaoguanghao.earbugmusic.download.DownloadStatus.SUCCESS }
                    .map { it.song }
                PlayerCore.setQueue(if (downloaded.isEmpty()) listOf(task.song) else downloaded)
                playback.load(task.song, selectedQuality)
                selectTab(1)
            },
            onRetry = { task ->
                DownloadManager.retry(task)
                toast("已重新加入下载队列")
            },
            onDelete = { task ->
                if (DownloadManager.hasLocalFile(task)) {
                    com.zhaoguanghao.earbugmusic.ui.LightDialog.confirm(
                        this,
                        "删除下载任务",
                        "这同样也会删除已下载的本地文件，确认吗？",
                        "确认", "取消"
                    ) {
                        DownloadManager.remove(task, true)
                        toast("已删除任务和本地文件")
                    }
                } else {
                    DownloadManager.remove(task, false)
                    toast("已删除任务")
                }
            }
        )
        pageDownloads.findViewById<RecyclerView>(R.id.rvDownloads).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = downloadAdapter
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadManager.tasks.collect { list ->
                    downloadAdapter.submit(list)
                    tvDlEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        }
    }

    /** 加载热歌榜 */
    private fun loadTopList() {
        hideKeyboard()
        progressBar.visibility = android.view.View.VISIBLE
        tvEmpty.visibility = android.view.View.GONE
        lifecycleScope.launch {
            try {
                val songs = LinkParser.parse(ParseMode.TOPLIST, "")
                showSongs(songs)
                toast("热歌榜已加载 " + songs.size + " 首")
            } catch (e: Exception) {
                toastError(e.message ?: "加载失败")
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }

    /** 收起键盘 */
    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)
            etInput.clearFocus()
        } catch (_: Exception) {
        }
    }

    /** 让选择框单击即响应（不先获取焦点、不弹键盘，避免“要点两次”） */
    private fun bindPickerField(field: android.widget.TextView, onClick: () -> Unit) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isCursorVisible = false
        field.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                field.performClick()
            }
            true
        }
        field.setOnClickListener { onClick() }
    }

    /** 单曲入队：本地已存在时先确认 */
    private fun enqueueSong(song: Song) {
        if (!ensureStorage()) return
        val exists = com.zhaoguanghao.earbugmusic.util.LocalLibrary
            .get(applicationContext, song.id) != null
        if (exists) {
            com.zhaoguanghao.earbugmusic.ui.LightDialog.confirm(
                this,
                "已下载过",
                "本地已有《" + song.name + "》，是否重新下载？",
                "确认", "取消"
            ) {
                DownloadManager.enqueue(applicationContext, song, selectedQuality)
                toast("已加入下载队列")
                selectTab(2)
            }
            return
        }
        DownloadManager.enqueue(applicationContext, song, selectedQuality)
        toast("已加入下载队列")
        selectTab(2)
    }

    /** 批量入队：批量中已有本地文件时先确认 */
    private fun enqueueSongs(songs: List<Song>, title: String) {
        if (!ensureStorage()) return
        val duplicated = songs.count {
            com.zhaoguanghao.earbugmusic.util.LocalLibrary.get(applicationContext, it.id) != null
        }
        val doEnqueue = {
            DownloadManager.enqueueAll(applicationContext, songs, selectedQuality)
            toast(title + "已解析 " + songs.size + " 首，加入下载队列")
        }
        if (duplicated > 0) {
            com.zhaoguanghao.earbugmusic.ui.LightDialog.confirm(
                this,
                "已有本地文件",
                "其中 " + duplicated + " 首已下载过，是否重新下载？",
                "确认", "取消"
            ) { doEnqueue() }
        } else {
            doEnqueue()
        }
    }

    /** 检查存储权限（Android 9 及以下需要），未授权则申请并返回 false */
    private fun ensureStorage(): Boolean {
        if (com.zhaoguanghao.earbugmusic.util.StoragePermission.canWrite(this)) return true
        toastError("请允许存储权限，文件才能保存到 Download 文件夹")
        com.zhaoguanghao.earbugmusic.util.StoragePermission.request(this)
        return false
    }

    // ==================== 红心歌曲 ====================

    private fun setupFavoritesPage() {
        tvFavEmpty = pageFavorites.findViewById(R.id.tvFavEmpty)
        tvFavCount = pageFavorites.findViewById(R.id.tvFavCount)
        favAdapter = SongAdapter(
            onPlay = { song ->
                val favs = com.zhaoguanghao.earbugmusic.util.Favorites.all(this)
                PlayerCore.setQueue(favs)
                playback.load(song, selectedQuality)
                selectTab(1)
            },
            onDownload = { song -> enqueueSong(song) },
            onFavoriteChanged = { _, _ -> refreshFavorites() }
        )
        pageFavorites.findViewById<RecyclerView>(R.id.rvFavorites).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favAdapter
        }
        refreshFavorites()
    }

    private fun refreshFavorites() {
        if (!this::favAdapter.isInitialized) return
        val list = com.zhaoguanghao.earbugmusic.util.Favorites.all(this)
        favAdapter.submit(list)
        tvFavCount.text = list.size.toString() + " 首"
        tvFavEmpty.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun refreshFavoritesIfVisible() {
        if (this::favAdapter.isInitialized && pageFavorites.visibility == android.view.View.VISIBLE) {
            refreshFavorites()
        }
    }

    /** 成功类提示：默认不打扰 */
    private fun toast(msg: String) {
        com.zhaoguanghao.earbugmusic.util.Tips.success(this, msg)
    }

    /** 错误 / 引导类提示：始终显示 */
    private fun toastError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** 通知权限（Android 13+）与写存储权限（Android 9 及以下） */
    private fun requestNotificationPermission() {
        val wanted = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            wanted.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1001)
    }

    override fun onPause() {
        PlayerCore.saveSession()
        super.onPause()
    }

    override fun onDestroy() {
        // 播放核心与通知服务保持运行，不在此释放
        super.onDestroy()
    }
}
