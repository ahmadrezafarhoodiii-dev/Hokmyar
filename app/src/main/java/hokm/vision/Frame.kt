package hokm.vision

/** تصویر صفحه به صورت ARGB. همه‌ی مختصات‌ها نسبت به اندازه‌ی مرجع ۱۱۵۹×۲۵۷۶ هستند. */
class Frame(val w: Int, val h: Int, val px: IntArray) {
    fun r(x: Int, y: Int) = (px[y * w + x] shr 16) and 0xFF
    fun g(x: Int, y: Int) = (px[y * w + x] shr 8) and 0xFF
    fun b(x: Int, y: Int) = px[y * w + x] and 0xFF

    /** اگر گوشی اندازه‌ی دیگری دارد، به اندازه‌ی مرجع می‌برد (نزدیک‌ترین همسایه). */
    fun toReference(): Frame {
        if (w == REF_W && h == REF_H) return this
        val out = IntArray(REF_W * REF_H)
        val sx = w.toFloat() / REF_W; val sy = h.toFloat() / REF_H
        for (y in 0 until REF_H) {
            val y0 = (y * sy).toInt().coerceIn(0, h - 1); val y1 = ((y + 1) * sy).toInt().coerceIn(y0 + 1, h)
            for (x in 0 until REF_W) {
                val x0 = (x * sx).toInt().coerceIn(0, w - 1); val x1 = ((x + 1) * sx).toInt().coerceIn(x0 + 1, w)
                // میانگین ناحیه‌ای (کوچک‌کردن بدون دندانه‌دار شدن)
                var r = 0; var g = 0; var b = 0; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = px[yy * w + xx]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
                }
                out[y * REF_W + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return Frame(REF_W, REF_H, out)
    }

    companion object { const val REF_W = 1159; const val REF_H = 2576 }
}

data class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w get() = x1 - x0
    val h get() = y1 - y0
    fun expand(m: Int, maxW: Int, maxH: Int) =
        Rect((x0 - m).coerceAtLeast(0), (y0 - m).coerceAtLeast(0), (x1 + m).coerceAtMost(maxW), (y1 + m).coerceAtMost(maxH))
}
