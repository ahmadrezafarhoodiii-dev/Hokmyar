import hokm.engine.*
import kotlin.random.Random

private fun c(s: String) = Card(when (s.last()) { 'S' -> Suit.SPADES; 'H' -> Suit.HEARTS; 'D' -> Suit.DIAMONDS; else -> Suit.CLUBS }, Rank.values().first { it.label == s.dropLast(1) })

fun main() {
    // وضعیت: 5 رگ مانده، حکم دل؛ من اول شروع می‌کنم؛ بقیه‌ی برگ‌ها ناشناخته (۱۳ برگ پیش‌تر بازی شده را «فراموش‌شده» می‌گیریم)
    val m = HandMemory(0)
    m.startMidHand(Suit.HEARTS, listOf("AS", "KS", "2H", "9D", "3C").map { c(it) }, tricksCompleted = 8, leader = 0, myTeamTricks = 4, otherTeamTricks = 4)
    val t0 = System.currentTimeMillis()
    val r = Advisor(Random(5), 200).advise(m)
    println("endgame 5 cards: ${System.currentTimeMillis() - t0} ms  " + r.joinToString { "${it.card}:${"%.2f".format(it.winHandProbability)}/${"%.2f".format(it.expectedTeamTricks)}" })
    // سناریوی قطعی: ۱ رگ مانده. همه‌ی برگ‌ها معلوم نیست ولی من برگ برنده دارم
    val m2 = HandMemory(0)
    m2.startMidHand(Suit.SPADES, listOf("AH", "2H").map { c(it) }, tricksCompleted = 11, leader = 0, myTeamTricks = 6, otherTeamTricks = 5)
    val r2 = Advisor(Random(5), 100).advise(m2)
    println("2 cards: " + r2.joinToString { "${it.card}:${"%.2f".format(it.winHandProbability)}" })
    // 6 برگ (حالت غیردقیق) برای مقایسه‌ی سرعت
    val m3 = HandMemory(0)
    m3.startMidHand(Suit.HEARTS, listOf("AS", "KS", "2H", "9D", "3C", "QD").map { c(it) }, tricksCompleted = 7, leader = 0, myTeamTricks = 3, otherTeamTricks = 4)
    val t1 = System.currentTimeMillis()
    val r3 = Advisor(Random(5), 400).advise(m3)
    println("6 cards (rollouts): ${System.currentTimeMillis() - t1} ms  " + r3.take(3).joinToString { "${it.card}:${"%.2f".format(it.winHandProbability)}" })
}
