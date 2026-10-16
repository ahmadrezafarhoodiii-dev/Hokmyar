package hokm.vision

import hokm.engine.Card
import hokm.engine.Rank
import hokm.engine.Suit
import kotlin.math.abs
import kotlin.math.hypot

data class Quad(val first: Card, val second: Comp, val third: Comp, val fourth: Float)

data class HandObs(val card: Card, val score: Float, val x: Float, val brightness: Float, var dimmed: Boolean = false)

/** نتیجه‌ی اسکن یک فریم. صندلی‌ها: ۰ من، ۱ راست، ۲ بالا (هم‌تیمی)، ۳ چپ. */
data class ScanResult(
    val trump: Suit?,
    val hand: List<HandObs>,
    val table: Map<Int, Card>,
    val tricksRight: Int,
    val tricksBottom: Int,
    val hakemSeat: Int?,
    val turnText: Boolean,
    val presence: Set<Int> = emptySet(),
    /** صندلی‌ای که الان نوبتش است (آواتارش سبز می‌شود): ۰ من، ۱ راست، ۲ بالا، ۳ چپ. */
    val activeSeat: Int? = null
) {
    val tricksSeen get() = tricksRight + tricksBottom
    val anyDimmed get() = hand.any { it.dimmed }
}

class CardRecognizer(private val tpl: Templates) {

    companion object {
        val HAND_REGION = Rect(0, 2060, 1159, 2576)   // برگی که انگشت بالا کشیده از ۲۱۰۰ هم بالاتر می‌آید
        val TABLE_REGION = Rect(200, 560, 960, 1520)
        val TRUMP_BOX = Rect(105, 395, 235, 505)
        private val RANK_KEYS = Rank.values().associateBy { it.label }
        fun suitOf(c: Char) = when (c) { 'S' -> Suit.SPADES; 'H' -> Suit.HEARTS; 'D' -> Suit.DIAMONDS; else -> Suit.CLUBS }
        fun suitChar(s: Suit) = when (s) { Suit.SPADES -> 'S'; Suit.HEARTS -> 'H'; Suit.DIAMONDS -> 'D'; Suit.CLUBS -> 'C' }
    }

    private val jitter = doubleArrayOf(-8.0, 0.0, 8.0)

    private fun clsRank(c: Comp, ang: Double): Pair<Float, String> {
        var best = -2f; var key = ""
        for (j in jitter) {
            val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue
            for ((k, l) in tpl.ranks) for (t in l) { val s = Glyph.ncc(g, t); if (s > best) { best = s; key = k } }
        }
        return best to key
    }

    private fun clsSuit(c: Comp, ang: Double): Pair<Float, Char> {
        val allowed = if (c.red) "HD" else "SC"
        var best = -2f; var key = '?'
        for (j in jitter) {
            val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue
            for ((k, l) in tpl.suits) { if (k !in allowed) continue; for (t in l) { val s = Glyph.ncc(g, t); if (s > best) { best = s; key = k } } }
        }
        return best to key
    }


    private fun rankScores(c: Comp, ang: Double): Map<String, Float> {
        val out = HashMap<String, Float>()
        for (j in jitter) {
            val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue
            for ((k, l) in tpl.ranks) for (t in l) { val v = Glyph.ncc(g, t); if (v > (out[k] ?: -2f)) out[k] = v }
        }
        return out
    }

    private fun suitScores(c: Comp, ang: Double): Map<Char, Float> {
        val allowed = if (c.red) "HD" else "SC"
        val out = HashMap<Char, Float>()
        for (j in jitter) {
            val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue
            for ((k, l) in tpl.suits) { if (k !in allowed) continue; for (t in l) { val v = Glyph.ncc(g, t); if (v > (out[k] ?: -2f)) out[k] = v } }
        }
        return out
    }

    /** بهترین برگ برای یک جفت (عدد، خال) با کنار گذاشتن برگ‌های غیرممکن. */
    private fun bestCard(a: Comp, b: Comp, ang: Double, exclude: Set<Card>, minR: Float, minS: Float): Cand? {
        val rs = rankScores(a, ang); val ss = suitScores(b, ang)
        var best: Cand? = null
        for ((rk, r) in rs) {
            if (r < minR) continue
            for ((sk, sv) in ss) {
                if (sv < minS) continue
                val card = Card(suitOf(sk), RANK_KEYS.getValue(rk))
                if (card in exclude) continue
                if (best == null || r + sv > best.score) best = Cand(r + sv, r, sv, card, a, b)
            }
        }
        return best
    }


    /**
     * عدد برگ را می‌خواند؛ برای «۱۰» که روی برگ‌های کج دو تکه (۱ و ۰) می‌شود، تکه‌ی «۱» را
     * در جهت چپِ خود برگ پیدا و می‌چسباند (جهت برگ از بردار عدد→خال معلوم است).
     */
    private fun rankWithMerge(a: Comp, ang: Double, dx: Float, dy: Float, cand: List<Comp>): Triple<Float, String, Comp> {
        val first = clsRank(a, ang)
        var best = Triple(first.first, first.second, a)
        val d = hypot(dx, dy)
        val ux = dx / d; val uy = dy / d; val wx = uy; val wy = -ux
        for (c in cand) {
            if (c === a || c.red != a.red) continue
            val vx = c.cx - a.cx; val vy = c.cy - a.cy
            val along = vx * wx + vy * wy; val lat = vx * ux + vy * uy
            if (along < -48f || along > -10f || abs(lat) > 18f) continue
            if (c.h !in 25..80 || c.w > 60) continue
            val m = Comps.union(c, a)
            val r = clsRank(m, ang)
            if (r.first > best.first) best = Triple(r.first, r.second, m)
        }
        return best
    }


    /** خود جزء + نسخه‌هایی که «۱» کنارش (در جهت چپِ برگ) چسبانده شده، برای تشخیص «۱۰» روی برگ‌های کج. */
    private fun rankVariants(a: Comp, dx: Float, dy: Float, cand: List<Comp>): List<Comp> {
        val out = arrayListOf(a)
        val d = hypot(dx, dy)
        val ux = dx / d; val uy = dy / d; val wx = uy; val wy = -ux
        for (c in cand) {
            if (c === a || c.red != a.red) continue
            val vx = c.cx - a.cx; val vy = c.cy - a.cy
            val along = vx * wx + vy * wy; val lat = vx * ux + vy * uy
            if (along < -48f || along > -10f || abs(lat) > 18f) continue
            if (c.h !in 25..80 || c.w > 60) continue
            out.add(Comps.union(c, a))
        }
        // حرف‌هایی که چند تکه شده‌اند (مثل «A» کمرنگ و وارونه): تکه‌های هم‌رنگِ کنار هم (فاصله ≤ ۱۰ پیکسل) را بچسبان
        val near = cand.filter { c ->
            c !== a && c.red == a.red && c.area >= 60 &&
                maxOf(c.x - (a.x + a.w), a.x - (c.x + c.w), 0) <= 10 && maxOf(c.y - (a.y + a.h), a.y - (c.y + c.h), 0) <= 10
        }
        var all = a
        for (c in near) {
            val u = Comps.union(a, c)
            if (u.w <= 75 && u.h <= 80) { out.add(u); all = Comps.union(all, c) }
        }
        if (near.size >= 2 && all.w <= 75 && all.h <= 80) out.add(all)
        return out
    }

    private fun bestCardMerged(a: Comp, b: Comp, ang: Double, dx: Float, dy: Float, cand: List<Comp>,
                               exclude: Set<Card>, minR: Float, minS: Float): Cand? {
        var best: Cand? = null
        for (v in rankVariants(a, dx, dy, cand)) {
            val c = bestCard(v, b, ang, exclude, minR, minS) ?: continue
            if (best == null || c.score > best.score) best = c
        }
        return best
    }

    private class Cand(val score: Float, val rankScore: Float, val suitScore: Float, val card: Card, val a: Comp, val b: Comp)

    /** هم نسخه‌ی بدون چسباندن «۱۰» و هم نسخه‌ی چسبیده را کاندید می‌کند تا چسباندن اشتباه چیزی را گم نکند. */
    private fun comps(ink: Ink, region: Rect, wMin: Int, hMin: Int, wMax: Int, hMax: Int): List<Comp> {
        val out = ArrayList<Comp>()
        for (red in booleanArrayOf(true, false)) {
            val frag = Comps.splitNecks(Comps.mergeFragments(Comps.extract(ink, red, region)))
            val merged = Comps.mergeDigits(frag).filter { m -> frag.none { it === m } }
            out.addAll((frag + merged).filter { it.w in wMin..wMax && it.h in hMin..hMax })
        }
        return out
    }

    // ───────── دست من ─────────
    fun detectHand(f: Frame, exclude: Set<Card> = emptySet()): List<HandObs> {
        val reg = HAND_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 18, 100, 100)
        val found = ArrayList<Cand>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (dy < 8 || a.h < 26 || a.area < 120 || b.area < 200 || b.h > 55 || b.w < 18) continue
            val ang = Glyph.fixRot(dx, dy)
            if (abs(ang) > 85) continue
            bestCardMerged(a, b, ang, dx, dy, cand, exclude, 0.66f, 0.78f)?.let { found.add(it) }
        }
        found.sortByDescending { it.score }
        val kept = ArrayList<Cand>()
        for (c in found) if (kept.none { hypot(it.a.cx - c.a.cx, it.a.cy - c.a.cy) <= 30f }) kept.add(c)
        val obs = kept.sortedBy { it.a.cx }.map {
            HandObs(it.card, it.rankScore, it.a.cx, ink.refAt(it.a.cx.toInt(), it.a.cy.toInt()))
        }
        val maxB = obs.maxOfOrNull { it.brightness } ?: 0f
        obs.forEach { it.dimmed = it.brightness < 0.88f * maxB }
        return obs
    }

    private fun seatOf(cx: Float, cy: Float): Int {
        val dx = cx - 580f; val dy = cy - 1000f
        return if (abs(dy) >= abs(dx)) (if (dy < 0) 2 else 0) else (if (dx < 0) 3 else 1)
    }


    /** برای عیب‌یابی: همه‌ی جفت‌های ممکن با امتیازشان (بدون آستانه). */
    fun debugHand(f: Frame): List<String> {
        val reg = HAND_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 18, 100, 100)
        val out = ArrayList<String>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (dy < 8 || a.h < 26 || a.area < 120 || b.area < 200 || b.h > 55 || b.w < 18) continue
            val ang = Glyph.fixRot(dx, dy)
            if (abs(ang) > 85) continue
            val (rs, rk, rc) = rankWithMerge(a, ang, dx, dy, cand); val (ss, sk) = clsSuit(b, ang)
            val r0 = clsRank(a, ang)
            out.add("rank@(${a.cx.toInt()},${a.cy.toInt()}) ${rk}${sk} r=${"%.2f".format(rs)} (alone ${r0.second} ${"%.2f".format(r0.first)}, merged=${rc !== a}) s=${"%.2f".format(ss)} ang=${ang.toInt()} sz=${rc.w}x${rc.h}")
        }
        return out.sortedBy { it }
    }


    /** جفت عدد+خال بدون آستانه (برای ساخت قالب‌ها و عیب‌یابی). */
    class PairInfo(val a: Comp, val b: Comp, val ang: Double, val rs: Float, val rk: String, val ss: Float, val sk: Char)

    fun handPairs(f: Frame): List<PairInfo> {
        val reg = HAND_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 18, 100, 100)
        val out = ArrayList<PairInfo>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (dy < 8 || a.h < 26 || a.area < 120 || b.area < 200 || b.h > 55 || b.w < 18) continue
            val ang = Glyph.fixRot(dx, dy)
            if (abs(ang) > 85) continue
            val (rs, rk) = clsRank(a, ang); val (ss, sk) = clsSuit(b, ang)
            out.add(PairInfo(a, b, ang, rs, rk, ss, sk))
        }
        return out
    }


    /** جفت‌های عدد+خالِ زمین بدون آستانه (برای ساخت قالب). */
    fun tablePairs(f: Frame): List<PairInfo> {
        val reg = TABLE_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 22, 80, 85)
        val out = ArrayList<PairInfo>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (!(a.h >= 30 && a.area >= 150 && b.area >= 150 && b.h <= 50 && b.w >= 14)) continue
            val ang = Glyph.fixRot(dx, dy)
            val (rs, rk) = clsRank(a, ang); val (ss, sk) = clsSuit(b, ang)
            out.add(PairInfo(a, b, ang, rs, rk, ss, sk))
        }
        return out
    }

    fun normGlyphs(a: Comp, b: Comp, ang: Double) = Glyph.norm(a.mask, a.w, a.h, ang) to Glyph.norm(b.mask, b.w, b.h, ang)
    fun unionComps(a: Comp, b: Comp) = Comps.union(a, b)

    // ───────── برگ‌های وسط زمین ─────────
    fun detectTable(f: Frame, excludeOthers: Set<Card> = emptySet(), presence: Set<Int>? = null): Map<Int, Card> {
        val reg = TABLE_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 22, 80, 85)
        class T(val score: Float, val card: Card, val cx: Float, val cy: Float)
        val found = ArrayList<T>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (!(a.h >= 30 && a.area >= 150 && b.area >= 150 && b.h <= 50 && b.w >= 14)) continue
            val ang = Glyph.fixRot(dx, dy)
            val ux = dx / d; val uy = dy / d; val wx = uy; val wy = -ux
            val cx = a.cx + 125 * ux + 90 * wx; val cy = a.cy + 125 * uy + 90 * wy
            val seat = seatOf(cx, cy)
            if (presence != null && seat !in presence) continue      // جایی که برگی نیست، برگی هم نمی‌خوانیم
            val c = bestCardMerged(a, b, ang, dx, dy, cand, if (seat == 0) emptySet() else excludeOthers, 0.64f, 0.80f) ?: continue
            found.add(T(c.score, c.card, cx, cy))
        }
        found.sortByDescending { it.score }
        val kept = ArrayList<T>()
        for (t in found) if (kept.none { hypot(it.cx - t.cx, it.cy - t.cy) <= 90f }) kept.add(t)
        val out = HashMap<Int, Card>()
        for (t in kept) {
            val seat = seatOf(t.cx, t.cy)
            if (seat !in out) out[seat] = t.card
        }
        return out
    }


    /** برای ساخت قالب: با دانستن مجموعه‌ی برگ‌های دست، هر جفت را به یکی از آن‌ها نسبت می‌دهد. */
    fun labelHand(f: Frame, known: Set<Card>): List<Quad> {
        val reg = HAND_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 18, 100, 100)
        val options = ArrayList<Quad>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f || dy < 8 || a.h < 26 || a.area < 120 || b.area < 200 || b.h > 55 || b.w < 18) continue
            val ang = Glyph.fixRot(dx, dy)
            if (abs(ang) > 85) continue
            for (card in known) {
                if ((card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) != a.red) continue
                val rs = scoreRank(a, ang, card.rank.label); val ss = scoreSuit(b, ang, suitChar(card.suit))
                options.add(Quad(card, a, b, rs + ss))
            }
        }
        options.sortByDescending { it.fourth }
        val usedCard = HashSet<Card>(); val usedA = ArrayList<Comp>(); val res = ArrayList<Quad>()
        for (o in options) {
            if (o.first in usedCard) continue
            if (usedA.any { hypot(it.cx - o.second.cx, it.cy - o.second.cy) < 30f }) continue
            usedCard.add(o.first); usedA.add(o.second); res.add(o)
        }
        return res
    }

    private fun scoreRank(c: Comp, ang: Double, key: String): Float {
        var best = -2f
        for (j in jitter) { val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue; for (t in tpl.ranks[key] ?: emptyList()) best = maxOf(best, Glyph.ncc(g, t)) }
        return best
    }
    private fun scoreSuit(c: Comp, ang: Double, key: Char): Float {
        var best = -2f
        for (j in jitter) { val g = Glyph.norm(c.mask, c.w, c.h, ang + j) ?: continue; for (t in tpl.suits[key] ?: emptyList()) best = maxOf(best, Glyph.ncc(g, t)) }
        return best
    }


    /** حضور فیزیکی برگ در هر جای زمین (حتی اگر خوانده نشده باشد). */
    fun detectPresence(f: Frame): Set<Int> {
        val boxes = mapOf(2 to Rect(500, 800, 690, 880), 3 to Rect(335, 1000, 455, 1185), 1 to Rect(715, 1000, 825, 1185), 0 to Rect(500, 1255, 680, 1440))
        val out = HashSet<Int>()
        for ((seat, r) in boxes) {
            var cream = 0; var n = 0
            for (y in r.y0 until r.y1 step 2) for (x in r.x0 until r.x1 step 2) {
                val p = f.px[y * f.w + x]; val rr = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
                n++
                if (rr > 105 && g > 90 && b > 55 && rr >= g && g >= b && rr - b > 15) cream++
            }
            if (n > 0 && cream.toFloat() / n > 0.45f) out.add(seat)
        }
        return out
    }


    /** نوبت: آواتار بازیکنی که باید بازی کند سبز می‌شود (ساعت شمارشی بازی). */
    fun detectActiveSeat(f: Frame): Int? {
        val boxes = mapOf(0 to Rect(640, 2430, 742, 2532), 1 to Rect(1012, 1893, 1118, 2000), 2 to Rect(645, 195, 748, 300), 3 to Rect(48, 1895, 152, 2005))
        var best = -1; var bestFrac = 0.20f
        for ((seat, r) in boxes) {
            var n = 0; var green = 0
            for (y in r.y0 until r.y1.coerceAtMost(f.h)) for (x in r.x0 until r.x1.coerceAtMost(f.w)) {
                val p = f.px[y * f.w + x]; val rr = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
                n++
                if (g > rr + 12 && g > b + 12) green++
            }
            val frac = if (n > 0) green.toFloat() / n else 0f
            if (frac > bestFrac) { bestFrac = frac; best = seat }
        }
        return if (best >= 0) best else null
    }

    /** اسکن سبک (ارزان): بدون خواندن برگ‌ها. برای اینکه فقط وقتی لازم است اسکن سنگین انجام شود. */
    fun scanLight(f: Frame): ScanResult {
        val (tr, tb) = countTricks(f)
        return ScanResult(detectTrump(f), emptyList(), emptyMap(), tr, tb, detectHakem(f), detectTurnText(f), detectPresence(f), detectActiveSeat(f))
    }


    /** عیب‌یابی زمین: همه‌ی جفت‌ها با امتیاز (بدون آستانه). */
    fun debugTable(f: Frame): List<String> {
        val reg = TABLE_REGION
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val cand = comps(ink, reg, 14, 22, 80, 85)
        val out = ArrayList<String>()
        for (a in cand) for (b in cand) {
            if (a === b || a.red != b.red) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy; val d = hypot(dx, dy)
            if (d < 30f || d > 100f) continue
            if (!(a.h >= 30 && a.area >= 150 && b.area >= 150 && b.h <= 50 && b.w >= 14)) continue
            val ang = Glyph.fixRot(dx, dy)
            val (rs, rk, rc) = rankWithMerge(a, ang, dx, dy, cand); val (ss, sk) = clsSuit(b, ang)
            val ux = dx / d; val uy = dy / d
            out.add("rank@(${a.cx.toInt()},${a.cy.toInt()}) ${rk}${sk} r=${"%.2f".format(rs)} s=${"%.2f".format(ss)} ang=${ang.toInt()} aSize=${a.w}x${a.h} a=${a.area} pip@(${b.cx.toInt()},${b.cy.toInt()}) ${b.w}x${b.h} area=${b.area} center=(${(a.cx + 125 * ux + 90 * uy).toInt()},${(a.cy + 125 * uy - 90 * ux).toInt()})")
        }
        return out
    }

    fun debugComps(f: Frame, reg: Rect): List<String> {
        val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
        val out = ArrayList<String>()
        for (red in booleanArrayOf(true, false)) for (c in Comps.splitNecks(Comps.mergeFragments(Comps.extract(ink, red, reg))))
            out.add("${if (red) "red" else "blk"} @(${c.x},${c.y}) ${c.w}x${c.h} area=${c.area}")
        return out
    }

    // ───────── نشان حکم ─────────
    private fun trumpShape(f: Frame): Pair<Boolean, FloatArray> {
        val bx = TRUMP_BOX
        val w = bx.w; val h = bx.h
        val neutral = ByteArray(w * h); val gold = ByteArray(w * h)
        var nCount = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = f.px[(bx.y0 + y) * f.w + bx.x0 + x]
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            if (abs(r - g) < 22 && gray < 70) { neutral[y * w + x] = 1; nCount++ }
            if (r > 150 && g > 100 && b < 110 && r - b > 70 && r > g) gold[y * w + x] = 1
        }
        val blackIcon = nCount > 1500
        val m: ByteArray
        if (blackIcon) m = neutral else {
            // پر کردن شکل بین چپ‌ترین و راست‌ترین پیکسل طلایی هر ردیف
            m = ByteArray(w * h)
            for (y in 0 until h) {
                var l = -1; var r = -1
                for (x in 0 until w) if (gold[y * w + x].toInt() != 0 && x in 5 until (w - 5)) { if (l < 0) l = x; r = x }
                if (l >= 0) for (x in l..r) m[y * w + x] = 1
            }
        }
        // کوچک‌کردن به ۳۲×۳۲ با میانگین‌گیری
        val out = FloatArray(32 * 32); val cnt = FloatArray(32 * 32)
        for (y in 0 until h) for (x in 0 until w) {
            val ox = x * 32 / w; val oy = y * 32 / h
            out[oy * 32 + ox] += m[y * w + x].toFloat(); cnt[oy * 32 + ox] += 1f
        }
        for (i in out.indices) out[i] = out[i] / cnt[i].coerceAtLeast(1f)
        return blackIcon to out
    }

    fun detectTrump(f: Frame): Suit? {
        val (black, shape) = trumpShape(f)
        val allowed = if (black) "SC" else "HD"
        var best = 0.5f; var key: Char? = null
        for ((k, t) in tpl.trump) { if (k !in allowed) continue; val s = Glyph.ncc(shape, t); if (s > best) { best = s; key = k } }
        return key?.let { suitOf(it) }
    }

    /** شکل نشان حکم برای ساخت قالب. */
    fun trumpShapeForTemplate(f: Frame) = trumpShape(f)

    // ───────── شمارنده‌ی رگ‌ها ─────────
    private fun slotPresent(f: Frame, x0: Int, y0: Int): Boolean {
        var sb = 0.0; var sr = 0.0; var n = 0
        for (y in (y0 + 10) until (y0 + 82)) for (x in (x0 + 8) until (x0 + 56)) {
            if (x !in 0 until f.w || y !in 0 until f.h) continue
            val p = f.px[y * f.w + x]; sr += (p shr 16) and 255; sb += p and 255; n++
        }
        if (n == 0) return false
        val b = sb / n; val r = sr / n
        return b > r + 12 && b > 70
    }

    fun countTricks(f: Frame): Pair<Int, Int> {
        var right = 0; var bottom = 0
        for (k in 0 until 6) {
            if (slotPresent(f, 832, 1474 - 102 * k)) right++
            if (slotPresent(f, (303 + 81.5 * k).toInt(), 1708)) bottom++
        }
        return right to bottom
    }

    // ───────── تاج حاکم و متن «نوبت شماست» ─────────
    private fun count(f: Frame, r: Rect, pred: (Int, Int, Int) -> Boolean): Int {
        var c = 0
        for (y in r.y0 until r.y1.coerceAtMost(f.h)) for (x in r.x0 until r.x1.coerceAtMost(f.w)) {
            val p = f.px[y * f.w + x]
            if (pred((p shr 16) and 255, (p shr 8) and 255, p and 255)) c++
        }
        return c
    }

    fun detectHakem(f: Frame): Int? {
        val boxes = mapOf(0 to Rect(690, 2340, 800, 2440), 1 to Rect(1050, 1840, 1159, 1930), 2 to Rect(680, 130, 800, 230), 3 to Rect(0, 1830, 130, 1930))
        var best: Pair<Int, Int>? = null
        for ((seat, r) in boxes) {
            val c = count(f, r) { rr, g, b -> rr > 190 && g > 140 && b < 90 && rr - b > 110 }
            if (best == null || c > best.second) best = seat to c
        }
        return if (best != null && best.second >= 600) best.first else null
    }

    fun detectTurnText(f: Frame): Boolean =
        count(f, Rect(370, 680, 800, 770)) { r, g, b -> r > 200 && g > 150 && b < 60 && r - b > 150 } > 1500

    // ───────── همه با هم ─────────
    fun scan(frameIn: Frame, excludeHand: Set<Card> = emptySet(), excludeTable: Set<Card> = emptySet()): ScanResult {
        val f = frameIn.toReference()
        val (tr, tb) = countTricks(f)
        val pres = detectPresence(f)
        return ScanResult(detectTrump(f), detectHand(f, excludeHand), detectTable(f, excludeTable, pres), tr, tb, detectHakem(f), detectTurnText(f), pres, detectActiveSeat(f))
    }
}
