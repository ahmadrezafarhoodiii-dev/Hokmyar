package hokm.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * حالت پیش‌فرض: فقط یک دکمه‌ی شناور «خاموش». هیچ اسکنی انجام نمی‌شود و ضبط صفحه هم شروع نشده.
 * با زدن دکمه: مجوز ضبط صفحه گرفته می‌شود و اسکن روشن می‌شود. دوباره زدن: ضبط کاملاً متوقف می‌شود.
 * فریم‌ها فقط وقتی تحویل تحلیل‌گر می‌شوند که برنامه‌ی انتخاب‌شده (حکم) جلوی صفحه باشد؛ بقیه دور ریخته می‌شوند.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "hokm.assistant.START"
        const val ACTION_STOP = "hokm.assistant.STOP"
        const val ACTION_GRANTED = "hokm.assistant.GRANTED"
        const val ACTION_DENIED = "hokm.assistant.DENIED"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "hokm_overlay"
        private const val FRAME_INTERVAL_MS = 500L
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var button: TextView? = null

    private var scanning = false
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    private lateinit var tracker: ForegroundTracker
    private lateinit var analyzer: GameAnalyzer
    private var panel: TextView? = null
    private var lastFrameAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tracker = ForegroundTracker(this)
        analyzer = GameAnalyzer(this) { out -> main.post { showPanel(out) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_DENIED -> toast("مجوز ضبط صفحه داده نشد؛ اسکن خاموش ماند")
            ACTION_GRANTED -> {
                goForeground(withProjection = true)   // باید قبل از getMediaProjection باشد
                val data = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                if (data != null) startCapture(intent.getIntExtra(EXTRA_CODE, 0), data)
            }
            else -> { goForeground(withProjection = false); showButton() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        hidePanel()
        button?.let { runCatching { wm.removeView(it) } }
        button = null
        super.onDestroy()
    }

    // ───────── دکمه‌ی شناور ─────────

    private fun showButton() {
        if (button != null) return
        val size = (64 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 300 }
        tv.setOnTouchListener(DragOrTap(lp) { toggle() })
        wm.addView(tv, lp)
        button = tv
        render()
    }

    private fun render() {
        val b = button ?: return
        val color = if (scanning) Color.parseColor("#2E7D32") else Color.parseColor("#757575")
        b.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        b.text = if (scanning) "روشن\n${analyzer.frames}" else "خاموش"
    }

    private inner class DragOrTap(
        val lp: WindowManager.LayoutParams, val onTap: () -> Unit
    ) : View.OnTouchListener {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            return true
        }
    }

    private fun toggle() {
        if (scanning) { stopCapture(); return }
        if (Prefs.target(this) == null) { toast("اول در خود اپ، برنامه‌ی حکم را انتخاب کنید"); return }
        if (!ForegroundTracker.hasUsageAccess(this)) { toast("دسترسی «آمار استفاده» را در اپ بدهید"); return }
        startActivity(
            Intent(this, ProjectionRequestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ───────── ضبط صفحه ─────────

    private fun startCapture(resultCode: Int, data: Intent) {
        if (scanning) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(resultCode, data)
        val t = HandlerThread("hokm-capture").also { it.start() }
        val h = Handler(t.looper)

        // اندروید ۱۴+: قبل از ساخت VirtualDisplay باید callback ثبت شود
        val cb = object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopCapture() } }   // کاربر از نوار وضعیت ضبط را قطع کرد
        }
        proj.registerCallback(cb, h)

        val bounds = wm.maximumWindowMetrics.bounds
        val w = bounds.width(); val hgt = bounds.height()
        val dpi = resources.displayMetrics.densityDpi
        val r = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ onFrame(it) }, h)
        display = proj.createVirtualDisplay(
            "hokm-scan", w, hgt, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, h
        )
        projection = proj; projectionCallback = cb; reader = r; thread = t
        analyzer.reset()
        scanning = true
        render()
    }

    /** ضبط را کاملاً می‌بندد؛ بعد از این هیچ فریمی گرفته نمی‌شود. */
    private fun stopCapture() {
        if (!scanning && projection == null) return
        scanning = false
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.let { p ->
            projectionCallback?.let { p.unregisterCallback(it) }
            p.stop()
        }
        projection = null; projectionCallback = null
        thread?.quitSafely(); thread = null
        goForeground(withProjection = false)
        hidePanel()
        render()
    }

    private fun onFrame(r: ImageReader) {
        val image = r.acquireLatestImage() ?: return
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now
            // فریمِ برنامه‌های دیگر حتی به بیت‌مپ هم تبدیل نمی‌شود
            if (tracker.update() != Prefs.target(this)) return
            val bmp = image.toBitmap()
            analyzer.analyze(bmp)
            bmp.recycle()
            main.post { render() }
        } finally {
            image.close()
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val p = planes[0]
        val rowPadding = p.rowStride - p.pixelStride * width
        val full = Bitmap.createBitmap(width + rowPadding / p.pixelStride, height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(p.buffer)
        if (rowPadding == 0) return full
        return Bitmap.createBitmap(full, 0, 0, width, height).also { full.recycle() }
    }


    // ───────── پنل کوچک کارت‌های باقی‌مانده ─────────

    private fun showPanel(out: hokm.game.TrackerOutput) {
        if (!scanning) return

        val rankOrder = hokm.engine.Rank.values().reversed()
        val sb = StringBuilder()
        for (suit in hokm.engine.Suit.values()) {
            val cards = out.remainingCards
                .filter { it.suit == suit }
                .sortedByDescending { it.rank.ordinal }
            sb.append(suit.symbol).append(' ')
            if (cards.isEmpty()) sb.append('—')
            else sb.append(cards.joinToString(" ") { it.rank.label })
            sb.append('\n')
        }

        val seatLabels = arrayOf("شما", "راست", "هم‌تیمی", "چپ")
        val voidNotes = out.voidsBySeat
            .filter { (seat, suits) -> seat != 0 && suits.isNotEmpty() }
            .map { (seat, suits) -> "${seatLabels[seat]}: " + suits.joinToString("") { it.symbol } }
        if (voidNotes.isNotEmpty()) sb.append(voidNotes.joinToString("  ·  "))

        val tv = panel ?: TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 9f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(7, 4, 7, 4)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B8000000"))
                cornerRadius = 8f * resources.displayMetrics.density
            }
            val m = resources.displayMetrics
            val lp = WindowManager.LayoutParams(
                (0.92f * m.widthPixels).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (0.04f * m.widthPixels).toInt()
                // نوار باریک در فاصله‌ی زیر میز و بالای دست؛ ناحیه‌ی کارت‌های بازی و دست را نمی‌پوشاند.
                y = (0.755f * m.heightPixels).toInt()
            }
            wm.addView(this, lp)
            panel = this
        }
        tv.text = sb.toString().trimEnd()
        render()
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    // ───────── اعلان سرویس پیش‌زمینه ─────────

    private fun goForeground(withProjection: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "دستیار حکم", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("دستیار حکم")
            .setContentText(if (withProjection) "اسکن روشن است" else "دکمه‌ی شناور فعال؛ اسکن خاموش")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "بستن", stop
                ).build()
            ).build()
        val type = when {
            Build.VERSION.SDK_INT >= 34 ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (withProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            withProjection -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }
        startForeground(1, n, type)
    }

    private fun toast(s: String) = main.post { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
}
