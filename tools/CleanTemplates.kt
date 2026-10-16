import hokm.vision.*
import java.io.File

/** قالب‌های «عدد» که شبیه خال‌اند (نمونه‌ی آلوده) را حذف می‌کند. */
fun main(a: Array<String>) {
    val t = Templates.parse(File(a[0]).readText())
    var dropped = 0
    val ranks = HashMap<String, List<FloatArray>>()
    for ((k, list) in t.ranks) {
        val keep = ArrayList<FloatArray>()
        for (g in list) {
            val worst = t.suits.values.flatten().maxOf { Glyph.ncc(g, it) }
            if (worst >= 0.72f) { dropped++; println("drop rank $k sample (looks like a pip, ncc=${"%.2f".format(worst)})") } else keep.add(g)
        }
        ranks[k] = keep
    }
    File(a[1]).writeText(Templates(ranks, t.suits, t.trump).serialize())
    println("dropped $dropped; ranks left: " + ranks.map { "${it.key}=${it.value.size}" }.sorted())
}
