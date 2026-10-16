import hokm.vision.*
import kotlin.math.hypot
fun main(args: Array<String>) {
    val img = args[0]
    val f = loadFrame("src/test/resources/fixtures/$img.png")
    val reg = CardRecognizer.HAND_REGION
    val ink = InkMasks.compute(f, reg.expand(30, f.w, f.h))
    val all = ArrayList<Comp>()
    for (red in booleanArrayOf(true, false)) all.addAll(Comps.mergeDigits(Comps.mergeFragments(Comps.extract(ink, red, reg))).filter { it.w in 14..100 && it.h in 18..100 })
    val pairs = ArrayList<Triple<Comp, Comp, Float>>()
    for (a in all) {
        // خال پایینِ a: نزدیک‌ترین هم‌رنگ با فاصله ۳۰ تا ۱۰۰ و کوچک‌تر از ۵۵ ارتفاع
        val b = all.filter { it !== a && it.red == a.red && it.cy - a.cy >= 8 && it.h <= 55 && it.w >= 18 && it.area >= 200 && hypot(it.cx - a.cx, it.cy - a.cy) in 30f..100f }
            .minByOrNull { hypot(it.cx - a.cx, it.cy - a.cy) } ?: continue
        if (a.h < 26 || a.area < 120) continue
        pairs.add(Triple(a, b, 0f))
    }
    println("$img geometry pairs=${pairs.size}: " + pairs.sortedBy { it.first.cx }.joinToString(" ") { "(${it.first.cx.toInt()},${it.first.cy.toInt()} ${it.first.w}x${it.first.h})" })
}
