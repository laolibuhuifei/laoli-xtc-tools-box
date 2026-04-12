package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ActivityListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list) // 复用 app_list 布局
        setTitle(R.string.title_activity_list)

        // 隐藏搜索框，因为这是查看具体应用的活动列表
        findViewById<EditText>(R.id.et_search_app).visibility = View.GONE

        val packageName = intent.getStringExtra("packageName") ?: return
        val listView = findViewById<ListView>(R.id.lv_apps)

        try {
            val pm = packageManager
            val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = info.activities?.map { it.name } ?: emptyList()

            if (activities.isEmpty()) {
                Toast.makeText(this, "该应用没有导出的活动", Toast.LENGTH_SHORT).show()
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, activities)
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                val activityName = activities[position]
                execRootAmStart(packageName, activityName)
            }

            // 长按复制活动名
            listView.setOnItemLongClickListener { _, _, position, _ ->
                val activityName = activities[position]
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Activity Name", activityName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "已复制活动名: $activityName", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "获取活动列表失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun execRootAmStart(pkg: String, cls: String) {
        Thread {
            try {
                val p = Runtime.getRuntime().exec("su -mm")
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
}