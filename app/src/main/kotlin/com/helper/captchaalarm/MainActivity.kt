package com.helper.captchaalarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.helper.captchaalarm.databinding.ActivityMainBinding

/**
 * 仅首次启动使用：引导悬浮窗权限、手动测试。
 *
 * 正常使用流程：adb install → adb appops set（一键授权）→ 桌面点 App 图标
 * 让服务驻留并显示悬浮窗 → 之后全部用 adb 触发，无需再打开此界面。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnArm.setOnClickListener {
            if (!canDrawOverlays()) {
                requestOverlayPermission()
            } else {
                // 启动服务使其驻留并显示悬浮窗（loop=false，仅驻留不发声）
                startAlarmService(loop = false, play = false)
                binding.tvStatus.text = getString(R.string.status_armed)
            }
        }

        binding.btnTestPlay.setOnClickListener {
            // 模拟 adb 触发：响铃
            startAlarmService(loop = true, play = true)
        }

        binding.btnStop.setOnClickListener {
            stopPlayback()
        }

        binding.btnExit.setOnClickListener {
            stopService(Intent(this, AlarmService::class.java))
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvStatus.text = if (canDrawOverlays()) {
            getString(R.string.status_permission_ok)
        } else {
            getString(R.string.status_need_permission)
        }
    }

    private fun startAlarmService(loop: Boolean, play: Boolean) {
        val intent = Intent(this, AlarmService::class.java)
            .putExtra(AlarmService.EXTRA_LOOP, loop)
        // play=false 时仅驻留并显示悬浮窗（ARM），不发声；true 时响铃（PLAY）
        intent.action = if (play) AlarmService.ACTION_PLAY else AlarmService.ACTION_ARM
        // 首次进 App（前台）启动，用 startForegroundService 即可
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPlayback() {
        val intent = Intent(this, AlarmService::class.java)
            .setAction(AlarmService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
