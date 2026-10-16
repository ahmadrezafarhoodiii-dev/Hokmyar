package hokm.assistant

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("hokm", Context.MODE_PRIVATE)
    fun target(c: Context): String? = sp(c).getString("target", null)
    fun targetLabel(c: Context): String? = sp(c).getString("target_label", null)
    fun setTarget(c: Context, pkg: String, label: String) =
        sp(c).edit().putString("target", pkg).putString("target_label", label).apply()
}
