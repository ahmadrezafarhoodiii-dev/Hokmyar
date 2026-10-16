import hokm.engine.*
import hokm.game.GameTracker
import hokm.vision.*
import java.io.File

private fun lab(c: Card) = c.rank.label + CardRecognizer.suitChar(c.suit)
private fun cc(s: String) = Card(CardRecognizer.suitOf(s.last()), Rank.values().first { it.label == s.dropLast(1) })

/** اسکرین‌شات‌های واقعی یک دست (I→J→K→L→M) را پشت‌سرهم به ردیاب می‌دهد و پیشنهادها را با واقعیت می‌سنجد. */
fun main(args: Array<String>) {
    val rec = CardRecognizer(Templates.parse(File(args[1]).readText()))
    val tracker = GameTracker(Advisor(kotlin.random.Random(3), 150))
    // دست واقعی من در هر عکس و برگ‌های مجاز (طبق قانون پیروی از خال)
    val hand = mapOf("I" to "10H 3S 9D 10D 8C 10C KC", "J" to "10H 3S 9D 10D 8C 10C", "K" to "10H 3S 10D 8C 10C",
        "L" to "10H 3S 10D 8C 10C", "M" to "10H 3S 10D", "N" to "JH QH 6S JS 3D 8D 6C 10C", "O" to "8H 9H QH 5D KD 4C 6C 7C KC", "P" to "8H 9H QH 5D 4C 6C 7C KC")
    val legal = mapOf("I" to "8C 10C KC", "L" to "8C 10C", "M" to "10H", "N" to "3D 8D", "P" to "ANY", "K" to "ANY", "J" to "", "O" to "")
    var bad = 0
    for (img in args.drop(2)) {
        val f = loadFrame("${args[0]}/$img.png")
        val (eh, et) = tracker.exclusions()
        val r = rec.scan(f, eh, et)
        val t0 = System.currentTimeMillis()
        val o = tracker.onScan(r)
        val ms = System.currentTimeMillis() - t0
        val adv = o.advice.map { lab(it.card) }
        val mine = hand.getValue(img).split(" ").toSet()
        val lg = legal[img] ?: ""
        val notMine = adv.filter { it !in mine }
        val illegal = if (lg == "" || lg == "ANY") emptyList() else adv.filter { it !in lg.split(" ") }
        if (notMine.isNotEmpty() || illegal.isNotEmpty()) bad++
        println("$img turn=${o.myTurn} note=${o.note} advice=$adv (${ms}ms) notInMyHand=$notMine illegal=$illegal  table=${r.table.entries.sortedBy { it.key }.map { "${it.key}:${lab(it.value)}" }} dim=${r.hand.filter { it.dimmed }.map { lab(it.card) }}")
    }
    println(if (bad == 0) "FLOW OK: no suggestion outside my hand or against follow-suit" else "FLOW PROBLEMS: $bad")
}
