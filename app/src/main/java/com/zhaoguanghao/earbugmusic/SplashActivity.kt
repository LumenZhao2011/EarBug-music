package com.zhaoguanghao.earbugmusic

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** 启动页：图标缩放淡入 + 文案渐显，短暂停留后进入主界面 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.ivLogo)
        val name = findViewById<TextView>(R.id.tvAppName)
        val slogan = findViewById<TextView>(R.id.tvSlogan)

        logo.alpha = 0f
        logo.scaleX = 0.86f
        logo.scaleY = 0.86f
        name.alpha = 0f
        slogan.alpha = 0f

        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        name.animate().alpha(1f).setStartDelay(180).setDuration(360).start()
        slogan.animate().alpha(1f).setStartDelay(320).setDuration(360).start()

        handler.postDelayed({ goMain() }, 1150)
    }

    private fun goMain() {
        if (isFinishing) return
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
