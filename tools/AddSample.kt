import hokm.vision.*
import java.io.File
import kotlin.math.hypot

/** نمونه‌ی برچسب‌خورده‌ی دستی (زمین): AddSampleKt fixtures templates.txt out.txt "R AD 805 1175 808 1136" ... */
fun main(a: Array<String>) {
    val base = File(a[1]).readText(); val out = StringBuilder(base)
    val rec = CardRecognizer(Templates.parse(base))
    fun hex(v: FloatArray) = v.joinToString("") { "%02x".format((it.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) }
    for (spec in a.drop(3)) {
        val p = spec.split(" "); val img = p[0]; val label = p[1]
        val rx = p[2].toFloat(); val ry = p[3].toFloat(); val px = p[4].toFloat(); val py = p[5].toFloat()
        val pairs = rec.tablePairs(loadFrame("${a[0]}/$img.png"))
        val best = pairs.filter { hypot(it.a.cx - rx, it.a.cy - ry) < 10f && hypot(it.b.cx - px, it.b.cy - py) < 10f }.maxByOrNull { it.rs + it.ss }
        if (best == null) { println("no pair for $spec"); continue }
        val (gr, gs) = rec.normGlyphs(best.a, best.b, best.ang)
        if (gr == null || gs == null) continue
        out.append("R ${label.dropLast(1)} ${hex(gr)}\nS ${label.last()} ${hex(gs)}\n")
        println("added $label from $img (ang=${best.ang.toInt()})")
    }
    File(a[2]).writeText(out.toString())
}
