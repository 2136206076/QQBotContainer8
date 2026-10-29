package com.yuanbao.qqbot

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var etQQ: EditText

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        tvLog = findViewById(R.id.tvLog)
        etQQ = findViewById(R.id.etQQ)

        askPerms()

        LogBus.listen { line ->
            tvLog.append(line + "\n")
        }

        findViewById<Button>(R.id.btnDeploy).setOnClickListener {
            val src = etQQ.text.toString().trim()
            if (src.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("QQ 从哪来？")
                    .setMessage(
                        "QQ 必须是 3.2.32-52194 arm64，官方 CDN 常对手机 IP 返 403，App 没法替你下载。\n\n" +
                        "三选一：\n" +
                        "1) 把你之前打包的 QQ-3.2.32-52194-arm64.tar.gz 放到手机，这里填 /sdcard/Download/QQ.tar.gz\n" +
                        "2) 填一个能直下的 URL\n" +
                        "3) 留空继续 —— 后面会跳过，等你有包了再重跑部署"
                    )
                    .setPositiveButton("留空继续") { _, _ -> fire(DeployService.DEPLOY, "") }
                    .setNegativeButton("取消", null)
                    .show()
            } else fire(DeployService.DEPLOY, src)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener { fire(DeployService.START) }
        findViewById<Button>(R.id.btnStop).setOnClickListener { fire(DeployService.STOP) }
        findViewById<Button>(R.id.btnStatus).setOnClickListener { fire(DeployService.STATUS) }
        findViewById<Button>(R.id.btnVer).setOnClickListener { fire(DeployService.VERSIONS) }
        findViewById<Button>(R.id.btnDiag).setOnClickListener { fire(DeployService.DIAG) }

        findViewById<Button>(R.id.btnConsole).setOnClickListener {
            WebActivity.open(this, "控制台", "http://127.0.0.1:${Cfg.AGENT_PORT}/")
        }
        findViewById<Button>(R.id.btnVnc).setOnClickListener {
            WebActivity.open(this, "扫码登录", "http://127.0.0.1:${Cfg.NOVNC_PORT}/vnc.html?autoconnect=true")
        }
        findViewById<Button>(R.id.btnKeep).setOnClickListener { showKeepAlive() }

        checkProot()
    }

    private fun checkProot() {
        if (!Proot.ready(this)) {
            LogBus.log("⚠ 这个 APK 里没打包 proot：构建前请先在电脑上执行 bash tools/fetch-proot.sh")
        } else {
            LogBus.log("· proot 就绪：${Proot.prootBin(this).absolutePath}")
            LogBus.log("· 点「一键部署」开始；已部署过就直接点「启动」")
        }
    }

    private fun fire(act: String, src: String = "") {
        tvLog.append("\n== $act ==\n")
        DeployService.go(this, act, src)
    }

    private fun askPerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }

    private fun showKeepAlive() {
        val cmds = listOf(
            "adb shell device_config set_sync_disabled_for_tests persistent",
            "adb shell device_config put activity_manager max_phantom_processes 2147483647",
            "adb shell settings put global settings_enable_monitor_phantom_procs false",
            "adb shell dumpsys deviceidle whitelist +com.yuanbao.qqbot"
        )
        val text = """
            容器 App 也一样要过安卓这道关，三件事：

            1) 电池优化：系统设置 → 应用 → 本 App → 电池 → 选「不受限制」。

            2) 幽灵进程杀手（Android 12+ 必做，QQ 的 Chromium 子进程一开就超限，会被静默杀掉）：
               用「无线调试」或 LADB / Shizuku 拿到 adb 后执行下面 4 条（只需一次）。

            3) 保持通知可见：本 App 已用前台服务常驻通知，别把它滑掉。

            ${cmds.joinToString("\n            ")}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("保活")
            .setMessage(text)
            .setNeutralButton("复制 adb 命令") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", cmds.joinToString("\n")))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("知道了", null)
            .show()
    }
}
