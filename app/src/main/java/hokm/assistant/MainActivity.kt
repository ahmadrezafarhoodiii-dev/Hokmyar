package hokm.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String) {
        override fun toString() = label
    }

    private lateinit var status: TextView
    private lateinit var selected: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }

        root.addView(TextView(this).apply { text = "کارت‌شمار حکم"; textSize = 22f; gravity = Gravity.CENTER })
        status = TextView(this); root.addView(status)
        root.addView(Button(this).apply {
            text = "۱) اجازه‌ی نمایش روی برنامه‌ها"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        root.addView(Button(this).apply {
            text = "۲) دسترسی به آمار استفاده (برای تشخیص جلوی‌صفحه بودن حکم)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        })
        selected = TextView(this).apply { textSize = 16f }
        root.addView(TextView(this).apply { text = "۳) برنامه‌ی حکم را از لیست انتخاب کنید:" })
        root.addView(selected)

        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        val list = ListView(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, apps)
            setOnItemClickListener { _, _, pos, _ ->
                Prefs.setTarget(this@MainActivity, apps[pos].pkg, apps[pos].label)
                refresh()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "۴) روشن کردن دکمه‌ی شناور (اسکن خاموش شروع می‌شود)"
            setOnClickListener { startOverlay() }
        })
        root.addView(Button(this).apply {
            text = "بستن دکمه‌ی شناور"
            setOnClickListener { startService(Intent(this@MainActivity, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)) }
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val usage = ForegroundTracker.hasUsageAccess(this)
        status.text = "نمایش روی برنامه‌ها: ${if (overlay) "✅" else "❌"}    آمار استفاده: ${if (usage) "✅" else "❌"}"
        selected.text = "انتخاب‌شده: " + (Prefs.targetLabel(this) ?: "هیچ‌کدام")
    }

    private fun startOverlay() {
        when {
            !Settings.canDrawOverlays(this) -> toast("اول مجوز نمایش روی برنامه‌ها را بدهید")
            !ForegroundTracker.hasUsageAccess(this) -> toast("اول دسترسی آمار استفاده را بدهید")
            Prefs.target(this) == null -> toast("اول برنامه‌ی حکم را انتخاب کنید")
            else -> {
                startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
                toast("دکمه‌ی خاموش آماده است. وارد بازی حکم شوید و وقتی ورق‌ها آمد، دکمه را بزنید.")
                moveTaskToBack(true)
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
