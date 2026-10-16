import hokm.vision.*
import java.io.File
fun main(a: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(a[1]).readText()))
    val x0 = a[3].toInt(); val y0 = a[4].toInt(); val x1 = a[5].toInt(); val y1 = a[6].toInt()
    rec.debugTable(loadFrame("${a[0]}/${a[2]}.png")).filter { l ->
        val m = Regex("rank@\\((\\d+),(\\d+)\\)").find(l)!!
        m.groupValues[1].toInt() in x0..x1 && m.groupValues[2].toInt() in y0..y1 }.forEach { println(it) }
}
