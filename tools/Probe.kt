import hokm.engine.Card
import hokm.vision.*
import java.io.File

private fun lab(c: Card) = c.rank.label + CardRecognizer.suitChar(c.suit)
private val GT = mapOf(
    "I" to ("10H 3S 9D 10D 8C 10C KC" to "2:"), "J" to ("10H 3S 9D 10D 8C 10C" to ""), "K" to ("10H 3S 10D 8C 10C" to ""),
    "L" to ("10H 3S 10D 8C 10C" to ""), "M" to ("10H 3S 10D" to ""), "N" to ("JH QH 6S JS 3D 8D 6C 10C" to ""),
    "O" to ("8H 9H QH 5D KD 4C 6C 7C KC" to ""), "P" to ("8H 9H QH 5D 4C 6C 7C KC" to ""))
private val GTT = mapOf("I" to "3:4C", "J" to "3:4C 1:AC 0:KC", "K" to "", "L" to "1:QC 2:5C 3:QD", "M" to "3:JH",
    "N" to "2:2D 3:5D 1:AD", "O" to "3:7D 0:4D", "P" to "")

fun main(args: Array<String>) {
    val tpl = Templates.parse(File(args[1]).readText())
    val rec = CardRecognizer(tpl)
    var hh = 0; var ht = 0; var hf = 0; var th = 0; var tt = 0; var tf = 0
    for (img in args.drop(2)) {
        val f = loadFrame("${args[0]}/$img.png")
        val r = rec.scan(f)
        val got = r.hand.map { lab(it.card) }
        val exp = GT[img]?.first?.split(" ") ?: emptyList()
        val tg = r.table.entries.sortedBy { it.key }.map { "${it.key}:${lab(it.value)}" }
        val te = (GTT[img] ?: "").split(" ").filter { it.isNotEmpty() }
        ht += exp.size; hh += exp.count { it in got }; hf += got.count { it !in exp }
        tt += te.size; th += te.count { it in tg }; tf += tg.count { it !in te }
        println("$img hand ${exp.count { it in got }}/${exp.size} missing=${exp.filter { it !in got }} extra=${got.filter { it !in exp }} dim=${r.hand.filter { it.dimmed }.map { lab(it.card) }}")
        println("   table got=$tg exp=$te  trump=${r.trump} tricks=${r.tricksRight}/${r.tricksBottom} hakem=${r.hakemSeat} turnText=${r.turnText}")
    }
    println("HAND $hh/$ht false $hf   TABLE $th/$tt false $tf")
}
