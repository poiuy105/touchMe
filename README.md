# 抓取告警 App（captcha-alarm）

一个极简 Android App：配合 **adb** 在抓取流程遇到「人机验证/异常页」时**响铃提醒**，用户**点屏幕上的悬浮窗**即可停止，悬浮窗常驻、可被多次触发。

- 目标设备：小米 MI 8 / Android 9（API 28）/ 无 root
- 语言：Kotlin
- 编译：GitHub Actions 自动出 debug APK（本机无需 Android Studio）

---

## 架构

- `AlarmService`（前台服务）：持有 `MediaPlayer` + 悬浮窗，PLAY/STOP 两个 action。悬浮窗点击回调**同进程直接**调 `stopPlayback()`，无跨进程通信。
- `OverlayManager`：原生 `WindowManager` + `TYPE_APPLICATION_OVERLAY` 悬浮窗（待命=灰，播放=红），替代第三方 EasyFloat。
- `MainActivity`：仅首次启动用——引导悬浮窗权限、提供「模拟告警/停止/退出」测试按钮。

### 对 app.md 的关键修正
| 议题 | app.md | 本 App | 理由 |
|---|---|---|---|
| 触发命令 | `am start-service` | `am start-foreground-service` | Android 8+ 后台 startService 崩溃 |
| 停止链路 | 悬浮窗→StopSoundService→广播→PlaySoundService | Service 自管悬浮窗，点击直接停 | 零跨进程通信，最可靠 |
| 悬浮窗 | EasyFloat | 原生 WindowManager | 少一个三方依赖 |
| 循环/单次 | 硬编码 | Intent extra `loop` 可控 | 可配置 |

---

## 编译（GitHub Actions）

push 到 `main`/`master` 后，`.github/workflows/build.yml` 自动编译，产物 `app-debug.apk` 在 Actions 的 Artifacts 里下载。

> 仓库不提交 `gradlew`/`gradle-wrapper.jar`（见 `.gitignore`）；CI 会先用 `gradle wrapper` 生成再用它编译。本机若想本地编译，装好 JDK 17 后运行：`gradle wrapper && ./gradlew :app:assembleDebug`。

---

## 部署（一次性）

设 `ADB="C:\Users\HP\Downloads\platform-tools\adb.exe"`，`DEV=-s 70faf03f`，`PKG=com.helper.captchaalarm`。

```bash
# 1) 安装
$ADB $DEV install -r app-debug.apk

# 2) 一键授予悬浮窗权限（adb appops，实测可行，免手动设置）
$ADB $DEV shell appops set $PKG SYSTEM_ALERT_WINDOW allow

# 3) 加入电池白名单（防 MIUI 杀后台）
$ADB $DEV shell dumpsys deviceidle whitelist +$PKG

# 4) 让服务驻留并显示悬浮窗（首次需点一下 App 图标，或在 App 内点「启用悬浮窗」）
#    之后可全程用 adb 触发，无需再开界面
```

> 若 MIUI 仍不显示悬浮窗：到「设置→授权管理→抓取告警」开启「显示悬浮窗」与「后台弹出界面」。

---

## 使用

**触发告警（默认无限循环响铃）：**
```bash
$ADB $DEV shell am start-foreground-service \
    -n $PKG/.AlarmService -a com.helper.captchaalarm.PLAY --ez loop true
```

**停止：** 点手机屏幕上的悬浮窗（**不需要 adb 命令**）。悬浮窗变灰保留，等待下次触发。

**单次响铃（响一遍自动停）：** 把 `--ez loop true` 改成 `false`。

**强制停止（调试/紧急）：** `$ADB $DEV shell am force-stop $PKG`

**多次触发不会叠加：** Service 内 `isPlaying` 判重，重复触发会被忽略。

---

## 验证清单

1. `am start-foreground-service ... PLAY --ez loop true` → 铃响、悬浮窗变红。
2. 点悬浮窗 → 铃停、悬浮窗变灰保留。
3. `adb logcat -s CAPTCHA_ALARM:I` → 点悬浮窗后看到 `STATE=STOPPED`（这是未来 Python 主流程集成的同步点）。
4. 连续两次触发 → 不叠加两路铃声。
5. 抖音播放视频时触发 → 铃声盖过、抖音降音（AudioFocus MAY_DUCK）；点悬浮窗后抖音音量恢复。

---

## 未来集成（暂未做）

后续可在 Python 主流程 `deep_collect.py` 的采集循环里：dump XML 后调 `page_classifier.classify_file()`，判定 CAPTCHA/LOGIN/DIALOG 时执行上述触发命令，并 `adb logcat -s CAPTCHA_ALARM:I` 阻塞等待 `STATE=STOPPED`（= 用户已点悬浮窗 = 人机验证已处理），再重新 dump 继续。

降级方案：本机另有 `alarm_ring.py`（ES 浏览器播放 + getevent 停止），作为无此 App 时的备选。
