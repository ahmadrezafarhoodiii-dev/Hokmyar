package hokm.vision

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Glyph {
    const val W = 24
    const val H = 32

    /** ماسک را (با زاویه‌ی angleDeg مثل OpenCV) می‌چرخاند، دور تا دور برش می‌دهد و به ۲۴×۳۲ می‌برد. */
    fun norm(mask: ByteArray, w: Int, h: Int, angleDeg: Double): FloatArray? {
        val th = Math.toRadians(angleDeg)
        val c = cos(th).toFloat(); val s = sin(th).toFloat()
        val cx0 = w / 2f; val cy0 = h / 2f
        var cnt = 0
        for (v in mask) if (v.toInt() != 0) cnt++
        if (cnt == 0) return null
        val xs = FloatArray(cnt * 4); val ys = FloatArray(cnt * 4)
        var k = 0
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x].toInt() == 0) continue
            for (sy in 0..1) for (sx in 0..1) {
                val dx = x + 0.25f + 0.5f * sx - cx0; val dy = y + 0.25f + 0.5f * sy - cy0
                val rx = c * dx + s * dy; val ry = -s * dx + c * dy
                xs[k] = rx; ys[k] = ry; k++
                if (rx < minX) minX = rx; if (rx > maxX) maxX = rx
                if (ry < minY) minY = ry; if (ry > maxY) maxY = ry
            }
        }
        val bw = (maxX - minX).coerceAtLeast(1e-3f); val bh = (maxY - minY).coerceAtLeast(1e-3f)
        val acc = FloatArray(W * H)
        for (i in 0 until k) {
            val bx = (((xs[i] - minX) / bw) * W).toInt().coerceIn(0, W - 1)
            val by = (((ys[i] - minY) / bh) * H).toInt().coerceIn(0, H - 1)
            acc[by * W + bx] += 1f
        }
        val expected = (bw * bh * 4f) / (W * H)
        for (i in acc.indices) acc[i] = (acc[i] / expected).coerceAtMost(1f)
        return acc
    }

    fun ncc(a: FloatArray, b: FloatArray): Float {
        var ma = 0f; var mb = 0f
        for (i in a.indices) { ma += a[i]; mb += b[i] }
        ma /= a.size; mb /= b.size
        var sab = 0f; var saa = 0f; var sbb = 0f
        for (i in a.indices) { val x = a[i] - ma; val y = b[i] - mb; sab += x * y; saa += x * x; sbb += y * y }
        val d = sqrt(saa * sbb)
        return if (d > 1e-6f) sab / d else -1f
    }

    /** زاویه‌ای که بردار عدد→خال را به سمت پایین می‌برد (قرارداد OpenCV). */
    fun fixRot(dx: Float, dy: Float): Double = -Math.toDegrees(Math.atan2(dx.toDouble(), dy.toDouble()))
}

/** قالب‌های عدد (۱۳ کلاس)، خال (۴ کلاس) و نشان حکم. */
class Templates(
    val ranks: Map<String, List<FloatArray>>,
    val suits: Map<Char, List<FloatArray>>,
    val trump: Map<Char, FloatArray>
) {
    fun serialize(): String {
        val sb = StringBuilder()
        fun hex(a: FloatArray) = a.joinToString("") { "%02x".format((it.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) }
        for ((k, l) in ranks) for (a in l) sb.append("R $k ${hex(a)}\n")
        for ((k, l) in suits) for (a in l) sb.append("S $k ${hex(a)}\n")
        for ((k, a) in trump) sb.append("T $k ${hex(a)}\n")
        return sb.toString()
    }

    companion object {
        fun parse(text: String): Templates {
            val r = HashMap<String, MutableList<FloatArray>>()
            val s = HashMap<Char, MutableList<FloatArray>>()
            val t = HashMap<Char, FloatArray>()
            fun un(h: String) = FloatArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16) / 255f }
            for (line in text.lineSequence()) {
                val p = line.trim().split(" ")
                if (p.size != 3) continue
                when (p[0]) {
                    "R" -> r.getOrPut(p[1]) { ArrayList() }.add(un(p[2]))
                    "S" -> s.getOrPut(p[1][0]) { ArrayList() }.add(un(p[2]))
                    "T" -> t[p[1][0]] = un(p[2])
                }
            }
            return Templates(r, s, t)
        }
    }
}
