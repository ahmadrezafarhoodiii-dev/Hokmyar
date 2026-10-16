import hokm.vision.*
import java.io.File
fun main(a: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(a[1]).readText()))
    val r = Rect(a[3].toInt(), a[4].toInt(), a[5].toInt(), a[6].toInt())
    rec.debugComps(loadFrame("${a[0]}/${a[2]}.png"), r).forEach { println(it) }
}
