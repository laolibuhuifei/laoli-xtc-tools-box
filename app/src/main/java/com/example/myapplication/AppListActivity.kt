package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    private lateinit var allApps: List<ApplicationInfo>
    private var filteredApps: MutableList<ApplicationInfo> = mutableListOf()
    private lateinit var adapter: ArrayAdapter<ApplicationInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)
        setTitle(R.string.title_app_list)

        val listView = findViewById<ListView>(R.id.lv_apps)
        val etSearch = findViewById<EditText>(R.id.et_search_app)
        val pm = packageManager

        // 获取所有应用
        allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .sortedBy { it.loadLabel(pm).toString() }
        
        filteredApps.addAll(allApps)

        adapter = object : ArrayAdapter<ApplicationInfo>(this, android.R.layout.simple_list_item_2, android.R.id.text1, filteredApps) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val app = getItem(position)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)
                text1.text = app?.loadLabel(pm)
                text2.text = app?.packageName
                return view
            }
        }

        listView.adapter = adapter

        // 搜索功能实现
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString(), pm)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps[position]
            val intent = Intent(this, ActivityListActivity::class.java)
            intent.putExtra("packageName", app.packageName)
            startActivity(intent)
        }

        // 长按复制包名
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val app = filteredApps[position]
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Package Name", app.packageName)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已复制包名: ${app.packageName}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun filter(query: String, pm: PackageManager) {
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            val lowerCaseQuery = query.lowercase()
            for (app in allApps) {
                val label = app.loadLabel(pm).toString().lowercase()
                val pkgName = app.packageName.lowercase()
                if (label.contains(lowerCaseQuery) || pkgName.contains(lowerCaseQuery)) {
                    filteredApps.add(app)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }
}