import hokm.vision.*
import hokm.game.CardTracker
import java.io.File
import javax.imageio.ImageIO
fun load(p: String): Frame { val im = ImageIO.read(File(p)); val px = IntArray(im.width * im.height); im.getRGB(0, 0, im.width, im.height, px, 0, im.width); return Frame(im.width, im.height, px) }
fun main(a: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(a[1]).readText())); val t = CardTracker()
    for (img in a.drop(2)) {
        val (eh, et) = t.exclusions(); val r = rec.scan(load("${a[0]}/$img.png"), eh, et); val o = t.onScan(r)
        println("$img ready=${o.ready} tricks=${o.tricksDone} unseen=${o.unseen.values.sumOf { it.size }} mine=${o.mine.size} unrecorded=${o.unrecorded} voids=${o.voids.mapValues { e -> e.value.map { s -> s.symbol } }.filter { it.value.isNotEmpty() }}")
    }
}
