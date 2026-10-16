import hokm.engine.*
import kotlin.random.Random

private fun c(s: String) = Card(when (s.last()) { 'S' -> Suit.SPADES; 'H' -> Suit.HEARTS; 'D' -> Suit.DIAMONDS; else -> Suit.CLUBS }, Rank.values().first { it.label == s.dropLast(1) })
private fun lab(x: Card) = x.rank.label + when (x.suit) { Suit.SPADES -> "S"; Suit.HEARTS -> "H"; Suit.DIAMONDS -> "D"; Suit.CLUBS -> "C" }

fun scenario(name: String, trump: Suit, hand: String, tricks: Int, myT: Int, oppT: Int, leader: Int, table: List<Pair<Int, String>>, expectFirst: String?) : Boolean {
    val m = HandMemory(0)
    m.startMidHand(trump, hand.split(" ").map { c(it) }, tricks, leader, myT, oppT)
    for ((seat, card) in table) m.recordPlay(seat, c(card))
    val t0 = System.currentTimeMillis()
    val adv = Advisor(Random(5), 120).advise(m)
    val ms = System.currentTimeMillis() - t0
    val first = lab(adv[0].card)
    val ok = expectFirst == null || first == expectFirst
    println((if (ok) "PASS " else "FAIL ") + "$name: ${ms}ms  " + adv.joinToString("  ") { "${lab(it.card)}[${"%.1f".format(it.score)} trick=${"%.0f".format(it.winTrickProbability * 100)}%]" })
    return ok
}

fun main() {
    var bad = 0
    // سطرهای جدول: (صندلی، برگ)؛ صندلی‌ها ۰ من، ۱ راست، ۲ بالا (هم‌تیمی)، ۳ چپ
    if (!scenario("Q: A♠ left, 10♠ partner -> play lowest spade", Suit.CLUBS, "2S 4S 5S 9S QS 5C KC", 6, 4, 2, 2, listOf(2 to "10S", 3 to "AS"), "2S")) bad++
    if (!scenario("R: A♦ right, can't win -> lowest diamond", Suit.CLUBS, "10H 8S 9S 10S KS 9D QD 4C 9C JC KC AC", 1, 0, 1, 1, listOf(1 to "AD", 2 to "5D", 3 to "3D"), "9D")) bad++
    if (!scenario("S: A♦ left, can't win -> lowest diamond", Suit.CLUBS, "5H 6H 4S 5S 10S 3D 7D 8C 10C QC KC AC", 1, 0, 1, 2, listOf(2 to "9D", 3 to "AD"), "3D")) bad++
    if (!scenario("T: no clubs, partner leads 8♣, could ruff with 3♦", Suit.DIAMONDS, "5H KH 9S QS KS 3D 5D 8D", 3, 1, 2, 2, listOf(2 to "8C", 3 to "6C"), "3D")) bad++
    if (!scenario("U: partner J♠, hold K♠ Q♠ -> lowest spade 6♠", Suit.DIAMONDS, "3H 5H KH 6S 7S 9S QS KS 3D 5D 8D 9C", 1, 0, 0, 2, listOf(2 to "JS", 3 to "2S"), "6S")) bad++
    // من شروع‌کننده‌ام: فقط پیشنهاد بدهد
    scenario("V: I lead (no cards on table)", Suit.HEARTS, "AS KS 5S 9H 4H 3D 2D 8C 6C", 4, 2, 2, 0, emptyList(), null)
    println(if (bad == 0) "ALL USER CASES OK" else "$bad cases wrong")
}
