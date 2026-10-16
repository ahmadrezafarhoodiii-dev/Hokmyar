package hokm.vision

class Comp(
    val red: Boolean, var x: Int, var y: Int, var w: Int, var h: Int,
    var area: Int, var cx: Float, var cy: Float, var mask: ByteArray
)

object Comps {
    /** اجزای متصل (۸-همسایگی) داخل region که در مختصات مطلق فریم است. */
    fun extract(ink: Ink, red: Boolean, region: Rect): List<Comp> {
        val m = if (red) ink.red else ink.black
        val w = ink.rect.w
        val ox = ink.rect.x0; val oy = ink.rect.y0
        val rx0 = region.x0 - ox; val ry0 = region.y0 - oy; val rx1 = region.x1 - ox; val ry1 = region.y1 - oy
        val seen = BooleanArray(m.size)
        val out = ArrayList<Comp>()
        var stack = IntArray(4096)
        for (sy in ry0 until ry1) for (sx in rx0 until rx1) {
            val s = sy * w + sx
            if (m[s].toInt() == 0 || seen[s]) continue
            var sp = 0; stack[sp++] = s; seen[s] = true
            val pts = ArrayList<Int>()
            var minX = sx; var maxX = sx; var minY = sy; var maxY = sy; var sumX = 0L; var sumY = 0L
            while (sp > 0) {
                val p = stack[--sp]; pts.add(p)
                val py = p / w; val px = p % w
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
                sumX += px; sumY += py
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = px + dx; val ny = py + dy
                    if (nx < rx0 || nx >= rx1 || ny < ry0 || ny >= ry1) continue
                    val q = ny * w + nx
                    if (m[q].toInt() != 0 && !seen[q]) {
                        seen[q] = true
                        if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                        stack[sp++] = q
                    }
                }
            }
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            if (pts.size < 80 || bw > 150 || bh > 150) continue
            val mask = ByteArray(bw * bh)
            for (p in pts) mask[(p / w - minY) * bw + (p % w - minX)] = 1
            out.add(Comp(red, minX + ox, minY + oy, bw, bh, pts.size,
                (sumX.toFloat() / pts.size) + ox, (sumY.toFloat() / pts.size) + oy, mask))
        }
        return out
    }

    internal fun union(a: Comp, b: Comp): Comp {
        val x0 = minOf(a.x, b.x); val y0 = minOf(a.y, b.y)
        val x1 = maxOf(a.x + a.w, b.x + b.w); val y1 = maxOf(a.y + a.h, b.y + b.h)
        val w = x1 - x0; val h = y1 - y0
        val m = ByteArray(w * h)
        for (q in arrayOf(a, b)) for (yy in 0 until q.h) for (xx in 0 until q.w)
            if (q.mask[yy * q.w + xx].toInt() != 0) m[(q.y - y0 + yy) * w + (q.x - x0 + xx)] = 1
        var sx = 0L; var sy = 0L; var c = 0
        for (yy in 0 until h) for (xx in 0 until w) if (m[yy * w + xx].toInt() != 0) { sx += xx; sy += yy; c++ }
        return Comp(a.red, x0, y0, w, h, a.area + b.area, sx.toFloat() / c + x0, sy.toFloat() / c + y0, m)
    }


    /**
     * دو چیز که به هم چسبیده‌اند (مثلاً خالِ کوچک و «A» زیرش) را در باریک‌ترین ردیفِ وسط جدا می‌کند.
     * نسخه‌ی اصلی هم نگه داشته می‌شود؛ تشخیص بین نسخه‌ها انتخاب می‌کند.
     */
    fun splitNecks(input: List<Comp>): List<Comp> {
        val out = ArrayList<Comp>(input)
        for (c in input) {
            if (c.h < 55 || c.w > 50 || c.h > 100) continue
            val rows = IntArray(c.h) { y -> (0 until c.w).count { x -> c.mask[y * c.w + x].toInt() != 0 } }
            val maxRow = rows.max()
            val lo = (c.h * 0.3).toInt(); val hi = (c.h * 0.7).toInt()
            var cut = -1; var best = Int.MAX_VALUE
            for (r in lo..hi) if (rows[r] < best) { best = rows[r]; cut = r }
            if (cut < 0 || best > 0.35 * maxRow) continue
            fun part(y0: Int, y1: Int): Comp? {
                if (y1 <= y0) return null
                var minX = c.w; var maxX = -1; var minY = c.h; var maxY = -1; var n = 0L; var sx = 0L; var sy = 0L
                for (y in y0 until y1) for (x in 0 until c.w) if (c.mask[y * c.w + x].toInt() != 0) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                    n++; sx += x; sy += y
                }
                if (n < 80) return null
                val w = maxX - minX + 1; val h = maxY - minY + 1
                val m = ByteArray(w * h)
                for (y in minY..maxY) for (x in minX..maxX) m[(y - minY) * w + (x - minX)] = c.mask[y * c.w + x]
                return Comp(c.red, c.x + minX, c.y + minY, w, h, n.toInt(), c.x + sx.toFloat() / n, c.y + sy.toFloat() / n, m)
            }
            val top = part(0, cut); val bottom = part(cut + 1, c.h)
            if (top != null && bottom != null) { out.add(top); out.add(bottom) }
        }
        return out
    }

    /** تکه‌های کوچکِ شکسته‌ی یک حرف را به نزدیک‌ترین جزء بزرگ‌تر می‌چسباند. */
    fun mergeFragments(input: List<Comp>, gap: Int = 8): List<Comp> {
        var comps = input.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            for (s in comps.filter { it.area < 260 && it.h < 42 }) {
                var best: Comp? = null; var bestD = Int.MAX_VALUE
                for (c in comps) {
                    if (c === s || c.red != s.red) continue
                    val gx = maxOf(c.x - (s.x + s.w), s.x - (c.x + c.w), 0)
                    val gy = maxOf(c.y - (s.y + s.h), s.y - (c.y + c.h), 0)
                    if (gx <= gap && gy <= gap && c.area >= s.area) {
                        val w = maxOf(c.x + c.w, s.x + s.w) - minOf(c.x, s.x)
                        val h = maxOf(c.y + c.h, s.y + s.h) - minOf(c.y, s.y)
                        if (w <= 70 && h <= 75 && gx + gy < bestD) { bestD = gx + gy; best = c }
                    }
                }
                if (best != null) {
                    val n = union(best, s)
                    comps = comps.filter { it !== best && it !== s }.toMutableList(); comps.add(n)
                    changed = true; break
                }
            }
        }
        return comps
    }

    /** «۱» و «۰» کنار هم (برگ ۱۰) را یکی می‌کند. */
    fun mergeDigits(input: List<Comp>): List<Comp> {
        val comps = input.sortedBy { it.x }
        val used = HashSet<Int>(); val res = ArrayList<Comp>()
        for (i in comps.indices) {
            if (i in used) continue
            var a = comps[i]
            for (j in i + 1 until comps.size) {
                val b = comps[j]
                if (j in used || b.red != a.red) continue
                val dx = maxOf(b.x - (a.x + a.w), a.x - (b.x + b.w), 0)
                val dy = maxOf(b.y - (a.y + a.h), a.y - (b.y + b.h), 0)
                val bothTall = a.h in 30..80 && b.h in 30..80
                if (dx <= 12 && dy <= 12 && bothTall && a.w <= 42 && b.w >= 20 && Math.abs(a.cy - b.cy) < 40 && b.x >= a.x - 12) {
                    a = union(a, b); used.add(j)
                }
            }
            res.add(a)
        }
        return res
    }
}
