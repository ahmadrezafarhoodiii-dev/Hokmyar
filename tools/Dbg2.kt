import hokm.vision.*
import java.io.File
fun main(a: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(a[1]).readText()))
    val f = loadFrame("${a[0]}/${a[2]}.png")
    rec.detectHand(f).forEach { println("${it.card.rank.label}${CardRecognizer.suitChar(it.card.suit)} x=${it.x.toInt()} score=${"%.2f".format(it.score)}") }
}
