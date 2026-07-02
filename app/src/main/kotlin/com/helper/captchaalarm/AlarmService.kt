package com.helper.captchaalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 抓取告警前台服务。
 *
 * 触发：adb shell am start-foreground-service -n com.helper.captchaalarm/.AlarmService \
 *        -a com.helper.captchaalarm.PLAY --ez loop true
 * 停止：用户点击悬浮窗 → OverlayManager 回调 → [stopPlayback]（同进程直接调用）。
 *
 * 设计要点（对 app.md 的简化）：
 *  - 一个 Service 同时承担播放与停止，悬浮窗点击回调直接调本类的 stopPlayback()，
 *    无需第二个 Service / 广播，零跨进程通信。
 *  - isPlaying 防叠加：多次 adb 触发不会叠加两路铃声。
 *  - logcat 同步点：状态变化时打印 CAPTCHA_ALARM 标签的 STATE= 行，
 *    未来 Python 主流程可用 `adb logcat -s CAPTCHA_ALARM:I` 阻塞等待 STATE=STOPPED。
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var loopMode = true
    private lateinit var audioManager: AudioManager
    private var overlay: OverlayManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 通知渠道必须在 startForeground 之前创建
        createNotificationChannel()
        // 悬浮窗由本服务持有，点击回调直接调 stopPlayback
        overlay = OverlayManager(this) { stopPlayback() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ★ 收到调用后 5 秒内必须 startForeground，否则 ANR/崩溃
        startForeground(NOTIFICATION_ID, buildNotification())

        val action = intent?.action ?: ACTION_PLAY
        loopMode = intent?.getBooleanExtra(EXTRA_LOOP, true) ?: true

        when (action) {
            ACTION_STOP -> stopPlayback()
            ACTION_ARM -> {
                // 仅驻留并显示悬浮窗，不发声
                overlay?.ensureAdded()
                overlay?.setState(OverlayManager.State.IDLE)
            }
            else -> {
                overlay?.ensureAdded()
                startPlayback()
            }
        }
        return START_STICKY
    }

    /** 开始播放（防叠加）。 */
    private fun startPlayback() {
        if (isPlaying) {
            Log.i(TAG, "already playing, ignore duplicate trigger")
            return
        }
        try {
            requestAudioFocus()
            // 用 STREAM_RING（电话铃流），独立于抖音的媒体音量
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) * 4 / 5,
                0
            )
            if (mediaPlayer == null) {
                // MediaPlayer.create() 返回的已是 prepared 状态，直接 start 即可
                mediaPlayer = MediaPlayer.create(this, R.raw.alarm)?.apply {
                    setAudioStreamType(AudioManager.STREAM_RING)
                    isLooping = loopMode
                    setOnCompletionListener {
                        // loopMode=false 时自然结束也视为已停止
                        if (!loopMode) {
                            isPlaying = false
                            overlay?.setState(OverlayManager.State.IDLE)
                            Log.i(TAG, "STATE=STOPPED")
                        }
                    }
                }
            }
            mediaPlayer?.apply {
                isLooping = loopMode
                start()
            }
            isPlaying = true
            overlay?.setState(OverlayManager.State.PLAYING)
            Log.i(TAG, "STATE=PLAYING")
        } catch (e: Exception) {
            Log.e(TAG, "startPlayback failed", e)
        }
    }

    /**
     * 停止播放（由悬浮窗点击直接调用）。
     * 注意：只暂停、不 release，便于复用；悬浮窗保留为待命态。
     */
    fun stopPlayback() {
        if (!isPlaying) return
        try {
            mediaPlayer?.run {
                if (isPlaying) pause()
            }
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "stopPlayback failed", e)
        }
        isPlaying = false
        overlay?.setState(OverlayManager.State.IDLE)
        Log.i(TAG, "STATE=STOPPED")   // ★ Python 同步点
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlaying = false
        overlay?.remove()
        abandonAudioFocus()
        Log.i(TAG, "STATE=STOPPED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────── 音频焦点（让抖音 STREAM_MUSIC 降音）───────────────
    private fun requestAudioFocus() {
        // MAY_DUCK：其他音源（抖音）降低音量
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // ─────────────── 通知 ───────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "抓取告警", NotificationManager.IMPORTANCE_LOW
            ).apply {
                // LOW + 无声：通知本身不出声，避免与铃声叠加
                setSound(null, null)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        @Suppress("DEPRECATION")
        return android.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("后台待命")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(android.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_PLAY = "com.helper.captchaalarm.PLAY"
        const val ACTION_STOP = "com.helper.captchaalarm.STOP"
        const val ACTION_ARM = "com.helper.captchaalarm.ARM"
        const val EXTRA_LOOP = "loop"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_channel"
        const val TAG = "CAPTCHA_ALARM"
    }
}
