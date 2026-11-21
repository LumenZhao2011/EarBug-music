package com.zhaoguanghao.earbugmusic

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 首次启动欢迎页：
 * 图标从中部缓慢上移 → 文案渐显 → 屏幕 5/6 处的圆形「好哒！」按钮出现。
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private lateinit var logo: ImageView
    private lateinit var tvHello: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var tvSlogan: TextView
    private lateinit var btnStart: com.google.android.material.button.MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("welcome", MODE_PRIVATE)

        // 非首次启动：直接进入主界面
        if (prefs.getBoolean("done", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
            return
        }

        setContentView(R.layout.activity_splash)

        content = findViewById(R.id.llContent)
        logo = findViewById(R.id.ivLogo)
        tvHello = findViewById(R.id.tvHello)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvSlogan = findViewById(R.id.tvSlogan)
        btnStart = findViewById(R.id.btnStart)

        val screenH = resources.displayMetrics.heightPixels
        val screenDp = resources.displayMetrics.density

        // 文案与按钮起点位置
        content.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = (screenH * 0.16f).toInt()
        }
        btnStart.layoutParams = (btnStart.layoutParams as FrameLayout.LayoutParams).apply {
            bottomMargin = (screenH / 6f - 48f * screenDp).toInt().coerceAtLeast(40)
        }

        tvHello.alpha = 0f
        tvWelcome.alpha = 0f
        tvSlogan.alpha = 0f

        // 初始位于屏幕中部，然后缓慢上移到目标位置
        content.post {
            val targetTop = content.top
            val startOffset = screenH / 2f - targetTop - content.height / 2f
            content.translationY = startOffset

            val move = ObjectAnimator.ofFloat(content, "translationY", startOffset, 0f).apply {
                duration = 1300
                interpolator = DecelerateInterpolator(1.4f)
                startDelay = 250
            }
            val logoIn = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
                duration = 420
            }
            val helloIn = ObjectAnimator.ofFloat(tvHello, "alpha", 0f, 1f).apply {
                duration = 380
            }
            val welcomeIn = ObjectAnimator.ofFloat(tvWelcome, "alpha", 0f, 1f).apply {
                duration = 380
            }
            val sloganIn = ObjectAnimator.ofFloat(tvSlogan, "alpha", 0f, 1f).apply {
                duration = 420
            }

            AnimatorSet().apply {
                play(logoIn)
                play(move).after(120)
                play(helloIn).after(move)
                play(welcomeIn).after(helloIn).with(sloganIn)
                start()
            }

            // 最后一个节拍：显示按钮
            content.postDelayed({
                btnStart.visibility = View.VISIBLE
                btnStart.alpha = 0f
                btnStart.scaleX = 0.9f
                btnStart.scaleY = 0.9f
                btnStart.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .start()
            }, 2650)
        }

        btnStart.setOnClickListener {
            prefs.edit().putBoolean("done", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
