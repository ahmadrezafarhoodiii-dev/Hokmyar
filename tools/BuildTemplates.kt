import hokm.vision.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot

fun loadFrame(path: String): Frame {
    val img = ImageIO.read(File(path))
    val px = IntArray(img.width * img.height)
    img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
    return Frame(img.width, img.height, px)
}

/** قالب‌ها را از اسکرین‌شات‌های برچسب‌خورده می‌سازد: java -cp ... BuildTemplatesKt fixturesDir samples.txt out.txt */
fun main(args: Array<String>) {
    val t = buildTemplates(args[0], File(args[1]).readLines().filter { it.isNotBlank() }, println = true)
    File(args[2]).writeText(t.serialize())
}

fun buildTemplates(fixtures: String, samples: List<String>, println: Boolean = false): Templates {
    val ranks = HashMap<String, MutableList<FloatArray>>()
    val suits = HashMap<Char, MutableList<FloatArray>>()
    val frames = HashMap<String, Frame>()
    val inks = HashMap<String, Ink>()
    fun comps(img: String, hand: Boolean, red: Boolean): List<Comp> {
        val f = frames.getOrPut(img) { loadFrame("$fixtures/$img.png") }
        val reg = if (hand) CardRecognizer.HAND_REGION else CardRecognizer.TABLE_REGION
        val ink = inks.getOrPut(img + hand) { InkMasks.compute(f, reg.expand(30, f.w, f.h)) }
        return Comps.mergeDigits(Comps.mergeFragments(Comps.extract(ink, red, reg)))
    }
    var used = 0
    for (line in samples) {
        val p = line.split(" ")
        val hand = p[0] == "H"; val img = p[1]; val label = p[2]
        val rx = p[3].toFloat(); val ry = p[4].toFloat(); val px = p[5].toFloat(); val py = p[6].toFloat()
        val red = label.last() == 'H' || label.last() == 'D'
        val cs = comps(img, hand, red)
        val a = cs.filter { it.w >= 12 }.minByOrNull { hypot(it.cx - rx, it.cy - ry) }
        val b = cs.filter { it !== a && it.w >= 18 }.minByOrNull { hypot(it.cx - px, it.cy - py) }
        if (a == null || b == null || hypot(a.cx - rx, a.cy - ry) > 14f || hypot(b.cx - px, b.cy - py) > 14f) {
            kotlin.io.println("skip $line"); continue
        }
        val ang = Glyph.fixRot(b.cx - a.cx, b.cy - a.cy)
        val gr = Glyph.norm(a.mask, a.w, a.h, ang) ?: continue
        val gs = Glyph.norm(b.mask, b.w, b.h, ang) ?: continue
        ranks.getOrPut(label.dropLast(1)) { ArrayList() }.add(gr)
        suits.getOrPut(label.last()) { ArrayList() }.add(gs)
        used++
    }
    // نشان حکم: از اسکرین‌شات‌های A (خاج)، C (پیک)، E (دل)، G (خشت)
    val trump = HashMap<Char, FloatArray>()
    val dummy = CardRecognizer(Templates(emptyMap(), emptyMap(), emptyMap()))
    for ((img, k) in listOf("A" to 'C', "C" to 'S', "E" to 'H', "G" to 'D'))
        trump[k] = dummy.trumpShapeForTemplate(loadFrame("$fixtures/$img.png")).second
    if (println) kotlin.io.println("templates from $used samples; ranks=${ranks.keys.sorted()} suits=${suits.keys}")
    return Templates(ranks, suits, trump)
}
