import hokm.engine.*
import hokm.vision.*
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** با دانستن برگ‌های دست (جواب درست)، جفت‌های عدد/خال هر عکس را خودکار برچسب می‌زند و نمونه‌ی قالب تولید می‌کند. */
fun main(args: Array<String>) {
    val fixtures = args[0]; val base = File(args[1]).readLines().filter { it.isNotBlank() }; val out = File(args[2])
    val tpl = buildTemplates(fixtures, base)
    val rec = CardRecognizer(tpl)
    val lines = ArrayList(base)
    for (img in "ABCEFGHIJKLMNOP") {
        val gt = GT_HAND.getValue(img.toString()).split(" ").filter { it.isNotEmpty() }
        if (gt.isEmpty()) continue
        val have = base.filter { it.startsWith("H $img ") }.map { it.split(" ")[2] }.toSet()
        val f = loadFrame("$fixtures/$img.png")
        val found = rec.labelHand(f, gt.map { cc(it) }.toSet())
        var added = 0
        for ((card, a, b, sc) in found) {
            val lab = card.rank.label + CardRecognizer.suitChar(card.suit)
            if (lab in have) continue
            if (sc < 1.6f) { println("  $img $lab weak ${"%.2f".format(sc)}"); continue }
            lines.add("H $img $lab ${a.cx} ${a.cy} ${b.cx} ${b.cy}"); added++
            if (card.rank == Rank.KING || card.rank == Rank.TEN) println("   sample $img $lab rank ${a.w}x${a.h} area=${a.area} pip ${b.w}x${b.h} score=${"%.2f".format(sc)}")
        }
        val missing = gt.filter { g -> found.none { it.first.rank.label + CardRecognizer.suitChar(it.first.suit) == g } }
        println("$img: found ${found.size}/${gt.size}, added $added new samples; unlabeled $missing")
    }
    out.writeText(lines.joinToString("\n") + "\n")
    println("samples ${base.size} -> ${lines.size}")
}

fun cc(s: String) = Card(CardRecognizer.suitOf(s.last()), Rank.values().first { it.label == s.dropLast(1) })
