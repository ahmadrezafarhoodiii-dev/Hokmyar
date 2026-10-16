import hokm.engine.Card
import hokm.vision.*
import java.io.File
import kotlin.math.hypot

private val GT = mapOf(
    "A" to "9H 2S 7S JS 3D 6D 7D 9D QD 5C 8C 9C QC", "B" to "6H 7H 8H 10H JH 7S JS AS 4D 5D 6D JC AC",
    "C" to "4H JH 6S 9S 2D 7D QD 4C 5C 9C QC KC AC", "E" to "8H 9H 10H KH 5S 6S 9S 10S KS JD 2C QC KC",
    "F" to "9H 10H KH 5S 6S 9S 10S KS JD", "G" to "9H JH KH 10C JC KC", "H" to "9H JH KH 10C JC KC",
    "I" to "10H 3S 9D 10D 8C 10C KC", "J" to "10H 3S 9D 10D 8C 10C", "K" to "10H 3S 10D 8C 10C",
    "L" to "10H 3S 10D 8C 10C", "M" to "10H 3S 10D", "N" to "JH QH 6S JS 3D 8D 6C 10C",
    "O" to "8H 9H QH 5D KD 4C 6C 7C KC", "P" to "8H 9H QH 5D 4C 6C 7C KC")

private fun lab(c: Card) = c.rank.label + CardRecognizer.suitChar(c.suit)
private fun hex(a: FloatArray) = a.joinToString("") { "%02x".format((it.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) }

/** برگ‌هایی که تشخیص نداد را با کمک جواب درست (ترتیب چپ‌به‌راست + خال مطمئن) به قالب اضافه می‌کند. */
fun main(args: Array<String>) {
    val fixtures = args[0]; val base = File(args[1]).readText(); val out = StringBuilder(base)
    val rec = CardRecognizer(Templates.parse(base))
    var added = 0
    for ((img, gtStr) in GT) {
        val f = loadFrame("$fixtures/$img.png")
        val got = rec.detectHand(f).map { lab(it.card) }.toSet()
        val gt = gtStr.split(" ")
        val missing = gt.filter { it !in got }
        if (missing.isEmpty()) continue
        val pairs = rec.handPairs(f)
        for (suit in missing.map { it.last() }.toSet()) {
            val want = gt.filter { it.last() == suit && it in missing }          // به ترتیب x
            val cand = pairs.filter { it.sk == suit && it.ss >= 0.6f && it.rs < 0.68f }.sortedBy { it.a.cx }
            // خوشه‌بندی نزدیک‌ها (تکه‌های ۱ و ۰ یک برگ‌اند)
            val clusters = ArrayList<MutableList<CardRecognizer.PairInfo>>()
            for (p in cand) {
                val cl = clusters.firstOrNull { c -> hypot(c[0].b.cx - p.b.cx, c[0].b.cy - p.b.cy) < 18f }
                if (cl != null) cl.add(p) else clusters.add(mutableListOf(p))
            }
            // نشانه‌ی (index) واقعی: فاصله‌ی عدد تا خال حدود ۵۶، خال متوسط‌اندازه و عدد کوچک‌تر از خال‌های وسط برگ
            fun quality(cl: List<CardRecognizer.PairInfo>): Float {
                val p = cl.maxByOrNull { it.ss }!!
                val d = hypot(p.a.cx - p.b.cx, p.a.cy - p.b.cy)
                var q = p.ss - Math.abs(d - 56f) / 20f
                if (p.b.area in 500..1300) q += 0.5f else q -= 1f
                if (p.a.area in 250..1000) q += 0.3f else q -= 0.5f
                return q
            }
            if (clusters.size < want.size) { println("$img suit $suit: only ${clusters.size} clusters for ${want.size} wanted (skip) $want"); continue }
            val chosen = clusters.sortedByDescending { quality(it) }.take(want.size)
            for ((i, cl) in chosen.sortedBy { it[0].b.cx }.withIndex()) {
                var a = cl[0].a
                for (p in cl.drop(1)) if (hypot(p.a.cx - a.cx, p.a.cy - a.cy) < 60f && p.a !== a) a = rec.unionComps(a, p.a)
                val best = cl.maxByOrNull { it.ss }!!
                val (gr, gs) = rec.normGlyphs(a, best.b, best.ang)
                if (gr == null || gs == null) continue
                val r = want[i].dropLast(1)
                out.append("R $r ${hex(gr)}\nS $suit ${hex(gs)}\n"); added++
                println("$img: added ${want[i]} (cluster of ${cl.size}, ss=${"%.2f".format(best.ss)}, ang=${best.ang.toInt()})")
            }
        }
    }
    File(args[2]).writeText(out.toString())
    println("added $added samples -> ${args[2]}")
}
