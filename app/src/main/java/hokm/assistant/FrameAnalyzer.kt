package hokm.assistant

import android.graphics.Bitmap

/**
 * نقطه‌ی اتصال مرحله‌ی ۳ (تشخیص برگ‌ها).
 * فقط وقتی صدا زده می‌شود که اسکن روشن باشد و اپ حکم جلوی صفحه باشد.
 * bitmap بعد از برگشتن از analyze آزاد می‌شود؛ نگهش ندارید.
 */
interface FrameAnalyzer {
    fun analyze(frame: Bitmap)
}

/** فعلاً فقط فریم‌ها را می‌شمارد تا ببینیم اسکن درست و فقط داخل حکم کار می‌کند. */
class CountingAnalyzer : FrameAnalyzer {
    @Volatile var frames = 0
        private set
    override fun analyze(frame: Bitmap) { frames++ }
}
