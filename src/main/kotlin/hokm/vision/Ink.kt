package hokm.vision

import kotlin.math.abs

/** ماسک جوهر قرمز/مشکی روی یک ناحیه + روشنایی محلی برگ (برای تشخیص برگ‌های تیره‌شده). */
class Ink(val rect: Rect, val red: ByteArray, val black: ByteArray, val ref: FloatArray) {
    fun refAt(x: Int, y: Int): Float {
        val xx = (x - rect.x0).coerceIn(0, rect.w - 1); val yy = (y - rect.y0).coerceIn(0, rect.h - 1)
        return ref[yy * rect.w + xx]
    }
}

object InkMasks {
    fun compute(f: Frame, rect: Rect): Ink {
        val w = rect.w; val h = rect.h; val n = w * h
        val gray = FloatArray(n); val cream = FloatArray(n)
        val rr = IntArray(n); val gg = IntArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val p = f.px[(rect.y0 + y) * f.w + rect.x0 + x]
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val i = y * w + x
            rr[i] = r; gg[i] = g
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cream[i] = if (r > 105 && g > 90 && b > 62 && r >= g && g >= b && r - b > 20 && r - b < 125 && abs(r - g) < 50) 1f else 0f
        }
        val dens = boxMean(cream, w, h, 20)
        // روشنایی محلی برگ: بیشینه‌ی ۴×۴ بلوک‌ها روی شبکه‌ی کوچک (۱۶ برابر سریع‌تر؛ پنجره‌ی مؤثر ≈ ±۳۵ پیکسل)
        val sw = (w + 3) / 4; val sh = (h + 3) / 4
        val small = FloatArray(sw * sh)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y / 4) * sw + x / 4
            if (gray[y * w + x] > small[i]) small[i] = gray[y * w + x]
        }
        val smallRef = maxFilter(small, sw, sh, 8)
        val ref = FloatArray(n) { smallRef[((it / w) / 4) * sw + (it % w) / 4] }
        val red = ByteArray(n); val black = ByteArray(n)
        for (i in 0 until n) {
            val rf = ref[i]
            if (rf <= 90f) continue
            val r = rr[i]; val g = gg[i]
            if ((r - g) > 0.25f * rf && g < 0.5f * rf && r > 0.4f * rf) red[i] = 1
            if (gray[i] < 0.5f * rf && abs(r - g) < 0.12f * rf && dens[i] > 0.30f) black[i] = 1
        }
        return Ink(rect, close5(red, w, h), close5(black, w, h), ref)
    }

    private fun boxMean(src: FloatArray, w: Int, h: Int, rad: Int): FloatArray {
        val integ = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0.0
            for (x in 0 until w) {
                row += src[y * w + x]
                integ[(y + 1) * (w + 1) + x + 1] = integ[y * (w + 1) + x + 1] + row
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = (y - rad).coerceAtLeast(0); val y1 = (y + rad + 1).coerceAtMost(h)
            for (x in 0 until w) {
                val x0 = (x - rad).coerceAtLeast(0); val x1 = (x + rad + 1).coerceAtMost(w)
                val s = integ[y1 * (w + 1) + x1] - integ[y0 * (w + 1) + x1] - integ[y1 * (w + 1) + x0] + integ[y0 * (w + 1) + x0]
                out[y * w + x] = (s / ((x1 - x0) * (y1 - y0))).toFloat()
            }
        }
        return out
    }

    private fun maxFilter(src: FloatArray, w: Int, h: Int, rad: Int): FloatArray {
        val tmp = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0f
            val a = (x - rad).coerceAtLeast(0); val b = (x + rad).coerceAtMost(w - 1)
            for (k in a..b) { val v = src[y * w + k]; if (v > m) m = v }
            tmp[y * w + x] = m
        }
        val out = FloatArray(w * h)
        for (x in 0 until w) for (y in 0 until h) {
            var m = 0f
            val a = (y - rad).coerceAtLeast(0); val b = (y + rad).coerceAtMost(h - 1)
            for (k in a..b) { val v = tmp[k * w + x]; if (v > m) m = v }
            out[y * w + x] = m
        }
        return out
    }

    // هسته‌ی بیضوی ۵×۵ مثل OpenCV
    private val KX = intArrayOf(0, -2, -1, 0, 1, 2, -2, -1, 0, 1, 2, -2, -1, 0, 1, 2, 0)
    private val KY = intArrayOf(-2, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2)

    private fun close5(m: ByteArray, w: Int, h: Int): ByteArray {
        val d = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var any = false
            for (k in KX.indices) {
                val xx = x + KX[k]; val yy = y + KY[k]
                if (xx in 0 until w && yy in 0 until h && m[yy * w + xx].toInt() != 0) { any = true; break }
            }
            if (any) d[y * w + x] = 1
        }
        val e = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var all = true
            for (k in KX.indices) {
                val xx = x + KX[k]; val yy = y + KY[k]
                if (xx in 0 until w && yy in 0 until h && d[yy * w + xx].toInt() == 0) { all = false; break }
            }
            if (all) e[y * w + x] = 1
        }
        return e
    }
}
