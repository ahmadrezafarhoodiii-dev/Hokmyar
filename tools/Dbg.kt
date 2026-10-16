import hokm.vision.*
import java.io.File
fun main(a: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(a[1]).readText()))
    rec.debugHand(loadFrame("${a[0]}/${a[2]}.png")).forEach { println(it) }
}
