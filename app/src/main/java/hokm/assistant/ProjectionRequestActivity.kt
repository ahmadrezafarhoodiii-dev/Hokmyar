package hokm.assistant

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** پنجره‌ی مجوز ضبط صفحه را نشان می‌دهد و نتیجه را به سرویس می‌دهد. */
class ProjectionRequestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val i = Intent(this, OverlayService::class.java)
        if (resultCode == RESULT_OK && data != null) {
            i.action = OverlayService.ACTION_GRANTED
            i.putExtra(OverlayService.EXTRA_CODE, resultCode)
            i.putExtra(OverlayService.EXTRA_DATA, data)
        } else {
            i.action = OverlayService.ACTION_DENIED
        }
        startService(i)
        finish()
    }
}
