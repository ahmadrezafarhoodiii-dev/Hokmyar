import hokm.engine.*
import hokm.game.GameTracker
import hokm.vision.*
import java.io.File

private fun c(s: String) = Card(CardRecognizer.suitOf(s.last()), Rank.values().first { it.label == s.dropLast(1) })
private fun hand(vararg s: String) = s.map { HandObs(c(it), 1f, 0f, 250f) }

fun scan(trump: Suit, hand: List<HandObs>, table: Map<Int, String>, right: Int = 0, bottom: Int = 0, hakem: Int? = null, turn: Boolean = false, active: Int? = null) =
    ScanResult(trump, hand, table.mapValues { c(it.value) }, right, bottom, hakem, turn, table.keys, active)

fun main() {
    var fails = 0
    fun check(name: String, ok: Boolean) { println((if (ok) "PASS  " else "FAIL  ") + name); if (!ok) fails++ }

    // سناریوی ۱: یک دست کامل با اسکن‌های پشت‌سرهم. من حاکم‌ام و شروع می‌کنم.
    val myHand = listOf("AH", "KH", "5H", "AS", "3S", "QD", "9D", "2D", "AC", "KC", "7C", "6C", "4C")
    val t = GameTracker(Advisor(kotlin.random.Random(1), 150))
    var o = t.onScan(scan(Suit.HEARTS, hand(*myHand.toTypedArray()), emptyMap(), hakem = 0))
    check("شروع دست: حافظه ساخته شد و نوبت من است", t.memory.myCards.size == 13 && o.myTurn)
    check("پیشنهاد برای شروع داده شد", o.advice.isNotEmpty())
    // من AH را می‌زنم، بعد راست (۱) ۲H، بالا (۲) ۹H، چپ (۳) ۴H
    o = t.onScan(scan(Suit.HEARTS, hand(*(myHand - "AH").toTypedArray()), mapOf(0 to "AH")))
    check("برگ من ثبت شد", c("AH") in t.memory.playedCards && !o.myTurn)
    o = t.onScan(scan(Suit.HEARTS, hand(*(myHand - "AH").toTypedArray()), mapOf(0 to "AH", 1 to "2H", 2 to "9H")))
    check("دو برگ بعدی به ترتیب ثبت شد", t.memory.currentTrick.size == 3)
    o = t.onScan(scan(Suit.HEARTS, hand(*(myHand - "AH").toTypedArray()), mapOf(0 to "AH", 1 to "2H", 2 to "9H", 3 to "4H"), 0, 0))
    check("رگ ۱ تمام شد و من برنده‌ام (A♥)", t.memory.completedTricks.size == 1 && t.memory.completedTricks[0].winner == 0 && t.memory.currentLeader == 0)
    check("تا وقتی برگ‌ها روی میزند منتظر می‌ماند (برگ خودم هنوز روی میز است)", !o.myTurn)
    o = t.onScan(scan(Suit.HEARTS, hand(*(myHand - "AH").toTypedArray()), emptyMap(), 0, 1))
    check("بعد از پاک شدن میز، دوباره نوبت من و پیشنهاد دارد", o.myTurn && o.advice.isNotEmpty())
    // رگ ۲: من ۷C می‌زنم؛ راست: ۳C؛ بالا هم‌تیمی: ۹C ؛ چپ: ۸S (کات‌نکرده، از خاج ندارد)
    val h2 = (myHand - "AH" - "7C").toTypedArray()
    o = t.onScan(scan(Suit.HEARTS, hand(*h2), mapOf(0 to "7C", 1 to "3C", 2 to "9C", 3 to "8S"), 1, 0))
    check("چپ از خاج ندارد (کات ثبت شد)", Suit.CLUBS in t.memory.knownVoids(3))
    check("برنده‌ی رگ ۲ بالا (۹C) است و شروع‌کننده‌ی بعدی اوست", t.memory.completedTricks.size == 2 && t.memory.currentLeader == 2)
    // رگ ۳: بالا شروع می‌کند
    o = t.onScan(scan(Suit.HEARTS, hand(*h2), mapOf(2 to "KD"), 1, 1))
    check("وقتی بالا شروع می‌کند منتظر می‌مانیم", !o.myTurn && t.memory.currentTrick.size == 1)
    o = t.onScan(scan(Suit.HEARTS, hand(*h2), mapOf(2 to "KD", 3 to "5D"), 1, 1, turn = true))
    check("نوبت من با متن نوبت شماست و ترتیب درست", o.myTurn)
    check("پیشنهاد از بین برگ‌های مجاز (خشت) است", o.advice.isNotEmpty() && o.advice.all { it.card.suit == Suit.DIAMONDS })
    // دست جدید: شمارنده صفر و حکم عوض
    o = t.onScan(scan(Suit.SPADES, hand("AS", "KS", "QS", "JS", "10S", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"), emptyMap(), hakem = 1))
    check("دست جدید شروع شد", t.memory.tricksCompleted == 0 && t.memory.trump == Suit.SPADES && t.memory.myCards.size == 13)

    // سناریوی ۱ب: من برنده‌ام و رگ بعد را خودم شروع می‌کنم؛ نوبت از آواتار سبز (active=0) خوانده می‌شود
    val tL = GameTracker(Advisor(kotlin.random.Random(4), 120))
    val handL = listOf("AH", "KH", "5H", "AS", "3S", "QD", "9D", "2D", "AC", "KC", "7C", "6C", "4C")
    tL.onScan(scan(Suit.HEARTS, hand(*handL.toTypedArray()), emptyMap(), hakem = 0, active = 0))
    tL.onScan(scan(Suit.HEARTS, hand(*(handL - "AH").toTypedArray()), mapOf(0 to "AH", 1 to "2H", 2 to "9H", 3 to "4H"), active = null))
    val oL = tL.onScan(scan(Suit.HEARTS, hand(*(handL - "AH").toTypedArray()), emptyMap(), 0, 1, active = 0))
    check("بعد از بردن رگ و پاک شدن میز، شروع‌کننده منم و پیشنهاد می‌دهد", oL.myTurn && oL.advice.isNotEmpty())
    // رگ بعد حریف (راست) شروع می‌کند؛ آواتار راست سبز است
    val oR = tL.onScan(scan(Suit.HEARTS, hand(*(handL - "AH" - "QD").toTypedArray()), mapOf(0 to "QD"), 0, 1, active = 1))
    check("وقتی آواتار راست سبز است منتظر می‌ماند", !oR.myTurn)
    // دست بعد: صفر شدن شمارنده + حکم جدید حافظه را پاک می‌کند
    tL.onScan(scan(Suit.CLUBS, hand("AS", "KS", "QS", "JS", "10S", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"), emptyMap(), hakem = 2, active = 2))
    check("دست جدید: حافظه پاک شد و ۱۳ برگ تازه ثبت شد", tL.memory.tricksCompleted == 0 && tL.memory.playedCards.isEmpty() && tL.memory.trump == Suit.CLUBS && tL.memory.myCards.size == 13)

    // سناریوی ۲: اسکن وسط دست روشن می‌شود (مثل اسکرین‌شات G)
    val t2 = GameTracker(Advisor(kotlin.random.Random(2), 200))
    val o2 = t2.onScan(scan(Suit.DIAMONDS, hand("9H", "JH", "KH", "10C", "JC", "KC").map { it }, mapOf(1 to "AH", 2 to "2H", 3 to "3H"), right = 5, bottom = 2, hakem = 2, turn = true))
    check("وسط دست: نوبت من، ۷ رگ رفته", o2.myTurn && t2.memory.tricksCompleted == 7)
    println("  پیشنهاد: " + o2.advice.joinToString { "${it.card}(${"%.2f".format(it.score)})" })
    check("باید از خال دل (خال زمین) پیروی کند و ۹♥ بهترین است (A♥ حریف می‌برد، هم‌تیمی ۲♥ زده)", o2.advice.isNotEmpty() && o2.advice.first().card.suit == Suit.HEARTS)
    println(if (fails == 0) "\nهمه‌ی تست‌های ردیاب گذشت" else "\n$fails تست رد شد")
}
