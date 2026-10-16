package hokm.assistant

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** تشخیص اینکه کدام برنامه الان جلوی صفحه است (با دسترسی «آمار استفاده»). */
class ForegroundTracker(private val context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var current: String? = null
    private var lastQuery = System.currentTimeMillis() - 30 * 60_000L

    /** فقط رویدادهای جدید از آخرین پرسش را می‌خواند. */
    fun update(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == current) current = null
            }
        }
        lastQuery = now
        return current
    }

    companion object {
        fun hasUsageAccess(c: Context): Boolean {
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }
}
