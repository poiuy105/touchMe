package com.helper.captchaalarm

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * 原生悬浮窗管理（替代 app.md 中的 EasyFloat 第三方库）。
 *
 * 用 WindowManager + TYPE_APPLICATION_OVERLAY（API26+，minSdk23 满足）。
 * 一个圆形小色块常驻屏幕：待命=灰，播放=红。点击触发 [onClick]（由 AlarmService 注入，
 * 直接调 stopPlayback）。点击后不 removeView，仅改色为待命，保持常驻等待下次触发。
 */
class OverlayManager(
    private val context: Context,
    private val onClick: () -> Unit
) {

    enum class State { IDLE, PLAYING }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var added = false

    private val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = dip(16)
        y = dip(220)
    }

    /** 若悬浮窗权限已授予且尚未添加，则添加。 */
    fun ensureAdded() {
        if (added || !Settings.canDrawOverlays(context)) return
        if (view == null) view = buildView()
        try {
            windowManager.addView(view, params)
            added = true
        } catch (e: Exception) {
            // MIUI 偶发拒绝添加，忽略；下次触发再试
        }
    }

    /** 切换状态：只改外观，不移除。 */
    fun setState(state: State) {
        val v = view ?: return
        val (colorRes, textRes) = when (state) {
            State.IDLE -> R.color.float_idle to R.string.float_idle
            State.PLAYING -> R.color.float_playing to R.string.float_playing
        }
        v.findViewById<LinearLayout>(R.id.float_root)
            ?.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        v.findViewById<TextView>(R.id.float_text)
            ?.setText(textRes)
    }

    fun remove() {
        if (!added) return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        added = false
    }

    private fun buildView(): View {
        val v = LayoutInflater.from(context).inflate(R.layout.view_float_alarm, null)
        v.setOnClickListener { onClick() }
        setState(State.IDLE)
        return v
    }

    private fun dip(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
