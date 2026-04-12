package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import android.view.GestureDetector
import android.view.MotionEvent
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private val noticeApiUrl = "http://8.148.213.26/api.php"
    private val defaultDpi = 320

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        loadNoticeAndShowDialog()

        // 浏览器入口
        findViewById<Button>(R.id.btn_open_browser).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }

        // 活动打开器入口
        findViewById<Button>(R.id.btn_activity_launcher).setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        // WiFi ADB 调试开关 (使用 Shell 命令强制获取 IP)
        val switchWifiAdb: SwitchMaterial = findViewById(R.id.switch_wifi_adb)
        val tvWifiAdbInfo: TextView = findViewById(R.id.tv_wifi_adb_info)
        
        switchWifiAdb.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                setWifiAdb(true)
                // 开启后，通过 Shell 尝试获取 IP
                val ip = getIpFromShell()
                if (ip != null) {
                    tvWifiAdbInfo.text = "WiFi ADB 已开启\n地址：$ip:5555"
                    Toast.makeText(this, "WiFi ADB 开启成功", Toast.LENGTH_SHORT).show()
                } else {
                    tvWifiAdbInfo.text = "WiFi ADB 已开启\n地址：未知 (请检查 WiFi 连接):5555"
                    Toast.makeText(this, "已开启，但无法获取 IP 地址", Toast.LENGTH_SHORT).show()
                }
            } else {
                setWifiAdb(false)
                tvWifiAdbInfo.text = getString(R.string.wifi_adb_off)
                Toast.makeText(this, "WiFi ADB 已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        // 弹窗提示
        val editText: EditText = findViewById(R.id.tanchuang)
        val btnPopup: Button = findViewById(R.id.bt_android)
        btnPopup.setOnClickListener {
            val inputContent = editText.text.toString()
            if (inputContent.isBlank()) {
                Toast.makeText(this, "输入内容为空！", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, inputContent, Toast.LENGTH_SHORT).show()
            }
        }

        // 功能按钮初始化
        findViewById<Button>(R.id.btn_open).setOnClickListener {
            execRootAmStart("com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.CellBroadcastSettings")
        }
        findViewById<Button>(R.id.btn_open2).setOnClickListener {
            execRootAmStart("com.android.emergency", "com.android.emergency.edit.EditMedicalInfoActivity")
        }
        findViewById<Button>(R.id.btn_open4).setOnClickListener {
            execRootAmStart("com.xtc.selftest", "com.xtc.selftest.ui.activity.SelfTestActivity")
        }

        // 切换输入法设置
        findViewById<Button>(R.id.btn_switch_ime).setOnClickListener {
            execRootAmStart("com.baidu.input.xtcime", "com.baidu.input.xtcime.demo.SettingActivity")
        }

        // DPI 设置
        val etDpi: EditText = findViewById(R.id.et_dpi)
        findViewById<Button>(R.id.btn_set_dpi).setOnClickListener {
            val dpi = etDpi.text.toString().toIntOrNull()
            if (dpi != null) setSystemDpi(dpi) else Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_reset_dpi).setOnClickListener { setSystemDpi(defaultDpi) }

        // 电量设置
        val etBattery: EditText = findViewById(R.id.et_battery)
        findViewById<Button>(R.id.btn_set_battery).setOnClickListener {
            val level = etBattery.text.toString().toIntOrNull()
            if (level != null) setFakeBattery(level) else Toast.makeText(this, "请输入数字", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_reset_battery).setOnClickListener { resetFakeBattery() }

        // 触屏显示
        findViewById<Button>(R.id.btn_show_touch).setOnClickListener { setShowTouches(true) }
        findViewById<Button>(R.id.btn_hide_touch).setOnClickListener { setShowTouches(false) }

        // 短信压力测试
        findViewById<Button>(R.id.btn_open_web).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        // Shell 执行
        val etCmd: EditText = findViewById(R.id.et_cmd)
        val tvOutput: TextView = findViewById(R.id.tv_output)
        findViewById<Button>(R.id.btn_exec_cmd).setOnClickListener {
            val cmd = etCmd.text.toString().trim()
            if (cmd.isNotEmpty()) execRootCmdWithOutput(cmd, tvOutput) else Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
        }

        // 关于页面 (MainActivity2)
        findViewById<Button>(R.id.btn_jump).setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
        }
    }

    private fun setWifiAdb(enable: Boolean) {
        val port = if (enable) "5555" else "-1"
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                process.outputStream.bufferedWriter().use {
                    it.write("setprop service.adb.tcp.port $port\n")
                    it.write("stop adbd\n")
                    it.write("start adbd\n")
                    it.write("exit\n")
                }
                process.waitFor()
            } catch (e: Exception) { 
                e.printStackTrace()
            }
        }.start()
    }

    private fun getIpFromShell(): String? {
        return try {
            // 通过 su 执行 ip addr show 命令获取 wlan0 信息
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip addr show wlan0"))
            val output = process.inputStream.bufferedReader().readText()
            // 使用正则匹配 IPv4 地址
            val match = Regex("inet\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(output)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadNoticeAndShowDialog() {
        Thread {
            try {
                val conn = java.net.URL(noticeApiUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.getInt("code") == 200) {
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle(json.getString("title"))
                            .setMessage(json.getString("content"))
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun execRootAmStart(pkg: String, cls: String) {
        Thread {
            try {
                val p = Runtime.getRuntime().exec("su")
                p.outputStream.bufferedWriter().use {
                    it.write("am start -n $pkg/$cls\nexit\n")
                }
                val success = p.waitFor() == 0
                runOnUiThread {
                    Toast.makeText(this, if (success) "启动成功" else "启动失败（需ROOT）", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "执行异常", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun setSystemDpi(dpi: Int) {
        Thread {
            try {
                Runtime.getRuntime().exec("su").outputStream.bufferedWriter().use {
                    it.write("wm density $dpi\nexit\n")
                }
                runOnUiThread { Toast.makeText(this, "DPI已尝试修改为：$dpi", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun setFakeBattery(level: Int) {
        Thread {
            try {
                Runtime.getRuntime().exec("su").outputStream.bufferedWriter().use {
                    it.write("dumpsys battery set level $level\nexit\n")
                }
                runOnUiThread { Toast.makeText(this, "电量已修改为：$level", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun resetFakeBattery() {
        Thread {
            try {
                Runtime.getRuntime().exec("su").outputStream.bufferedWriter().use {
                    it.write("dumpsys battery reset\nexit\n")
                }
                runOnUiThread { Toast.makeText(this, "已恢复真实电量", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun execRootCmdWithOutput(cmd: String, outputView: TextView) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                process.outputStream.bufferedWriter().use { it.write("$cmd\nexit\n") }
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                runOnUiThread {
                    outputView.text = "命令：$cmd\n退出码：$exitCode\n输出：\n$output\n错误：\n$error"
                }
            } catch (e: Exception) {
                runOnUiThread { outputView.text = "异常：${e.message}" }
            }
        }.start()
    }

    private fun setShowTouches(enable: Boolean) {
        Thread {
            try {
                val value = if (enable) "1" else "0"
                Runtime.getRuntime().exec("su").outputStream.bufferedWriter().use {
                    it.write("settings put system show_touches $value\nexit\n")
                }
                runOnUiThread { Toast.makeText(this, if (enable) "已开启触屏显示" else "已关闭触屏显示", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            @Suppress("SetJavaScriptEnabled")
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("http://8.148.213.26/disclaimer.php")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 != null && e2.x - e1.x > 150 && Math.abs(vx) > 200) {
                    finish()
                    return true
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }
}
