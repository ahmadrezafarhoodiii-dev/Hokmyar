import hokm.engine.Card
import hokm.vision.*
import java.io.File

private fun c(s: String) = Card(CardRecognizer.suitOf(s.last()), hokm.engine.Rank.values().first { it.label == s.dropLast(1) })

val GT_HAND = mapOf(
    "A" to "9H 2S 7S JS 3D 6D 7D 9D QD 5C 8C 9C QC", "B" to "6H 7H 8H 10H JH 7S JS AS 4D 5D 6D JC AC",
    "C" to "4H JH 6S 9S 2D 7D QD 4C 5C 9C QC KC AC", "E" to "8H 9H 10H KH 5S 6S 9S 10S KS JD 2C QC KC",
    "F" to "9H 10H KH 5S 6S 9S 10S KS JD", "G" to "9H JH KH 10C JC KC", "H" to "9H JH KH 10C JC KC", "D" to "",
    "I" to "10H 3S 9D 10D 8C 10C KC", "J" to "10H 3S 9D 10D 8C 10C", "K" to "10H 3S 10D 8C 10C", "L" to "10H 3S 10D 8C 10C",
    "M" to "10H 3S 10D", "N" to "JH QH 6S JS 3D 8D 6C 10C", "O" to "8H 9H QH 5D KD 4C 6C 7C KC", "P" to "8H 9H QH 5D 4C 6C 7C KC")
val GT_TABLE = mapOf(
    "A" to mapOf<Int, String>(), "B" to mapOf(),
    "C" to mapOf(2 to "AH", 3 to "2H", 1 to "7H"), "D" to mapOf(2 to "7S", 3 to "JD", 1 to "10C", 0 to "9C"),
    "E" to mapOf(2 to "5C", 3 to "9C"), "F" to mapOf(2 to "6H", 3 to "4H", 0 to "8H"),
    "G" to mapOf(2 to "2H", 3 to "3H", 1 to "AH"), "H" to mapOf(2 to "2H", 3 to "3H", 1 to "AH"),
    "I" to mapOf(3 to "4C"), "J" to mapOf(3 to "4C", 1 to "AC", 0 to "KC"), "K" to mapOf(), "L" to mapOf(2 to "5C", 3 to "QD", 1 to "QC"),
    "M" to mapOf(3 to "JH"), "N" to mapOf(2 to "2D", 3 to "5D", 1 to "AD"), "O" to mapOf(3 to "7D", 0 to "4D"), "P" to mapOf())
val GT_TRUMP = mapOf("A" to 'C', "B" to 'C', "C" to 'S', "D" to 'S', "E" to 'H', "F" to 'H', "G" to 'D', "H" to 'D',
    "I" to 'H', "J" to 'H', "K" to 'H', "L" to 'H', "M" to 'H', "N" to 'D', "O" to 'C', "P" to 'C')
val GT_COUNT = mapOf("A" to (0 to 0), "B" to (0 to 0), "C" to (0 to 0), "D" to (6 to 6), "E" to (0 to 0), "F" to (1 to 2), "G" to (5 to 2), "H" to (5 to 2),
    "I" to (1 to 5), "J" to (1 to 5), "K" to (3 to 5), "L" to (3 to 5), "M" to (5 to 5), "N" to (2 to 3), "O" to (1 to 2), "P" to (2 to 3))
val GT_CROWN = mapOf("A" to 0, "B" to 0, "C" to 1, "D" to 1, "E" to 2, "F" to 2, "G" to 2, "H" to 2,
    "I" to 2, "J" to 2, "K" to 2, "L" to 2, "M" to 2, "N" to 3, "O" to 0, "P" to 0)
val GT_TURN = mapOf("E" to true, "G" to true, "L" to true, "N" to true)
val GT_DIM = mapOf("E" to "8H 9H 10H KH 5S 6S 9S 10S KS JD", "G" to "10C JC KC", "H" to "10C JC KC")

fun main(args: Array<String>) {
    val fixtures = args[0]; val samples = File(args[1]).readLines().filter { it.isNotBlank() }
    var hHit = 0; var hTot = 0; var hFalse = 0; var tHit = 0; var tTot = 0; var tFalse = 0
    var okTrump = 0; var okCount = 0; var okCrown = 0; var okTurn = 0; var okDim = 0
    for (img in args.getOrElse(2) { "ABCDEFGH" }) {
        // آزمون «یک اسکرین‌شات کنار گذاشته‌شده»: قالب‌ها بدون نمونه‌های همین عکس ساخته می‌شوند
        val tpl = buildTemplates(fixtures, samples.filter { it.split(" ")[1] != img.toString() })
        val trumpTpl = buildTemplates(fixtures, emptyList()).trump
        val rec = CardRecognizer(Templates(tpl.ranks, tpl.suits, trumpTpl))
        val t0 = System.currentTimeMillis()
        val res = rec.scan(loadFrame("$fixtures/$img.png"))
        val ms = System.currentTimeMillis() - t0
        val exp = GT_HAND.getValue(img.toString()).split(" ").filter { it.isNotEmpty() }.map { c(it) }
        val got = res.hand.map { it.card }
        hTot += exp.size; hHit += exp.count { it in got }; hFalse += got.count { it !in exp }
        val et = GT_TABLE.getValue(img.toString()).mapValues { c(it.value) }
        tTot += et.size; tHit += et.count { res.table[it.key] == it.value }; tFalse += res.table.count { et[it.key] != it.value }
        val trumpOk = res.trump?.let { CardRecognizer.suitChar(it) } == GT_TRUMP[img.toString()]
        val cntOk = (res.tricksRight to res.tricksBottom) == GT_COUNT[img.toString()]
        val crownOk = res.hakemSeat == GT_CROWN[img.toString()]
        val turnOk = res.turnText == (GT_TURN[img.toString()] ?: false)
        val expDim = (GT_DIM[img.toString()] ?: "").split(" ").filter { it.isNotEmpty() }.map { c(it) }.toSet()
        val dimOk = res.hand.filter { it.dimmed }.map { it.card }.toSet() == expDim
        if (trumpOk) okTrump++; if (cntOk) okCount++; if (crownOk) okCrown++; if (turnOk) okTurn++; if (dimOk) okDim++
        println("$img  ${ms}ms  hand ${exp.count { it in got }}/${exp.size} (false ${got.count { it !in exp }})  table ${et.count { res.table[it.key] == it.value }}/${et.size} (false ${res.table.count { et[it.key] != it.value }})" +
            "  trump ${res.trump} ${if (trumpOk) "ok" else "WRONG"}  tricks ${res.tricksRight}/${res.tricksBottom} ${if (cntOk) "ok" else "WRONG"}" +
            "  hakem ${res.hakemSeat} ${if (crownOk) "ok" else "WRONG"}  turnText ${res.turnText} ${if (turnOk) "ok" else "WRONG"}  dim ${if (dimOk) "ok" else "WRONG"}")
        val missing = exp.filter { it !in got }; val extra = got.filter { it !in exp }
        if (missing.isNotEmpty() || extra.isNotEmpty()) println("     hand missing $missing extra $extra")
        val tm = et.filter { res.table[it.key] != it.value }
        if (tm.isNotEmpty() || res.table.any { et[it.key] == null }) println("     table expected $et got ${res.table}")
    }
    println("\nHAND  $hHit/$hTot  false $hFalse")
    println("TABLE $tHit/$tTot  false $tFalse")
    println("trump $okTrump  tricks $okCount  hakem $okCrown  turnText $okTurn  dim $okDim")
}
