import hokm.engine.*
import hokm.game.GameTracker
import hokm.vision.*
import java.io.File

private fun lab(c: Card) = c.rank.label + CardRecognizer.suitChar(c.suit)

/** اسکرین‌شات‌های جدید کاربر: هر کدام را جداگانه (ردیاب تازه، مثل روشن کردن اسکن وسط دست) می‌دهیم و پیشنهاد اول را با درست مقایسه می‌کنیم. */
fun main(args: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(args[1]).readText()))
    val expect = mapOf("Q" to "WAIT", "R" to "9D", "S" to "3D", "T" to "3D", "U" to "6S")
    var bad = 0
    for (img in args.drop(2)) {
        val tracker = GameTracker(Advisor(kotlin.random.Random(3), 120))
        val f = loadFrame("${args[0]}/$img.png")
        val r = rec.scan(f)
        val t0 = System.currentTimeMillis()
        val o = tracker.onScan(r)
        val ms = System.currentTimeMillis() - t0
        val adv = o.advice.map { lab(it.card) }
        val exp = expect[img]
        val ok = when (exp) { null -> true; "WAIT" -> !o.myTurn; else -> adv.firstOrNull() == exp }
        if (!ok) bad++
        println("$img ${if (ok) "OK " else "BAD"} turn=${o.myTurn} active=${r.activeSeat} presence=${r.presence.sorted()} note=${o.note} advice=$adv (${ms}ms)")
        println("     table=${r.table.entries.sortedBy { it.key }.map { "${it.key}:${lab(it.value)}" }} hand=${r.hand.map { lab(it.card) + if (it.dimmed) "~" else "" }} tricks=${r.tricksRight}/${r.tricksBottom}")
    }
    println(if (bad == 0) "USER SCREENSHOTS OK" else "$bad wrong")
}
