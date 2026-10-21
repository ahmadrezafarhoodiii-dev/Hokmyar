import hokm.engine.*
import hokm.game.CardTracker
import hokm.game.CounterOutput
import hokm.vision.*

private fun c(s: String) = Card(CardRecognizer.suitOf(s.last()), Rank.values().first { it.label == s.dropLast(1) })
private fun hand(vararg s: String) = s.map { HandObs(c(it), 1f, 0f, 250f) }
private fun scan(trump: Suit?, hand: List<HandObs>, table: Map<Int, String>, right: Int = 0, bottom: Int = 0, hakem: Int? = null, active: Int? = null, fresh: Boolean = true) =
    ScanResult(trump, hand, table.mapValues { c(it.value) }, right, bottom, hakem, false, table.keys, active, fresh)
private fun left(o: CounterOutput) = o.unseen.values.sumOf { it.size }
private fun has(o: CounterOutput, s: String) = c(s).let { card -> o.unseen[card.suit].orEmpty().contains(card.rank) }

fun main() {
    var fails = 0
    fun check(name: String, ok: Boolean) { println((if (ok) "PASS  " else "FAIL  ") + name); if (!ok) fails++ }

    val mine = listOf("AH", "KH", "5H", "AS", "3S", "QD", "9D", "2D", "AC", "KC", "7C", "6C", "4C")
    val t = CardTracker()
    var o = t.onScan(scan(null, emptyList(), emptyMap()))
    check("قبل از شناخته شدن حکم و دست: ready=false (جدول خالی/گمراه‌کننده نشان داده نمی‌شود)", !o.ready)
    o = t.onScan(scan(Suit.HEARTS, hand(*mine.toTypedArray()), emptyMap(), hakem = 0, active = 0))
    check("اول دست: ۳۹ برگ بازی‌نشده‌ی دیگران و هیچ برگ من در آن نیست", o.ready && left(o) == 39 && mine.none { has(o, it) })
    check("برگ‌های خودم جدا نگه داشته می‌شود (۱۳ برگ)", o.mine.size == 13)

    // من AH می‌زنم؛ راست ۲H، بالا ۹H، چپ ۸S (از دل ندارد!)
    val h1 = (mine - "AH").toTypedArray()
    t.onScan(scan(Suit.HEARTS, hand(*h1), mapOf(0 to "AH")))
    t.onScan(scan(Suit.HEARTS, hand(*h1), mapOf(0 to "AH", 1 to "2H", 2 to "9H")))
    o = t.onScan(scan(Suit.HEARTS, hand(*h1), mapOf(0 to "AH", 1 to "2H", 2 to "9H", 3 to "8S")))
    check("بعد از رگ اول: ۲H ۹H ۸S دیگر بازی‌نشده نیستند", !has(o, "2H") && !has(o, "9H") && !has(o, "8S") && left(o) == 36)
    check("چپ از دل ندارد؛ راست و هم‌تیمی هنوز دارند", Suit.HEARTS in o.voids[3].orEmpty() && o.voids[1].orEmpty().isEmpty() && o.voids[2].orEmpty().isEmpty())
    check("همه‌ی رگ‌ها ثبت شده (unrecorded = ۰)", o.unrecorded == 0 && o.tricksDone == 1)

    // رگ ۲: چپ شروع می‌کند ♣، راست ♣ ندارد و ♦ می‌اندازد
    t.onScan(scan(Suit.HEARTS, hand(*h1), emptyMap(), 0, 1, active = 3))      // میز پاک شد
    t.onScan(scan(Suit.HEARTS, hand(*h1), mapOf(3 to "9C"), 0, 1, active = 0))
    val h2 = (h1.toList() - hand("7C")[0]).toTypedArray()
    t.onScan(scan(Suit.HEARTS, hand(*(mine - "AH" - "7C").toTypedArray()), mapOf(3 to "9C", 0 to "7C"), 0, 1, active = 1))
    o = t.onScan(scan(Suit.HEARTS, hand(*(mine - "AH" - "7C").toTypedArray()), mapOf(3 to "9C", 0 to "7C", 1 to "5D", 2 to "10C"), 0, 1))
    check("راست از خاج ندارد (کات دوم ثبت شد)", Suit.CLUBS in o.voids[1].orEmpty())
    check("۹♣ ۱۰♣ ۵♦ هم از بازی‌نشده‌ها کم شدند", !has(o, "9C") && !has(o, "10C") && !has(o, "5D"))

    // برگی که ترتیبش ناسازگار است هم «بازی‌شده» حساب می‌شود
    val t2 = CardTracker()
    t2.onScan(scan(Suit.SPADES, hand("AS", "KS", "QS", "JS", "10S", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"), emptyMap(), hakem = 0, active = 0))
    o = t2.onScan(scan(Suit.SPADES, hand("AS", "KS", "QS", "JS", "10S", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"), mapOf(2 to "KH", 1 to "AH", 3 to "QD"), active = 0))
    check("برگ‌های دیده‌شده روی میز، حتی با ناسازگاری ترتیب، از بازی‌نشده‌ها حذف می‌شوند", !has(o, "KH") && !has(o, "AH") && !has(o, "QD"))

    // اسکن وسط دست روشن شد: ۶ رگ بی‌خبری → هشدار
    val t3 = CardTracker()
    o = t3.onScan(scan(Suit.DIAMONDS, hand("9H", "JH", "KH", "10C", "JC", "KC", "2S"), mapOf(), right = 3, bottom = 3, active = 0))
    check("وسط دست: ۲۴ برگ ثبت‌نشده گزارش می‌شود تا کاربر بداند شمارش کامل نیست", o.ready && o.unrecorded == 24 && o.tricksDone == 6)

    // سوسو‌زدن یک فریمِ حکم تاریخچه را پاک نمی‌کند
    val playedBefore = o.played.size
    t.onScan(scan(Suit.CLUBS, hand(*(mine - "AH" - "7C").toTypedArray()), emptyMap(), 0, 2, active = 2))
    o = t.onScan(scan(Suit.HEARTS, hand(*(mine - "AH" - "7C").toTypedArray()), emptyMap(), 0, 2, active = 2))
    check("سوسو‌زدن حکم، حافظه را پاک نمی‌کند", o.ready && o.played.size >= 7 && playedBefore >= 0)

    // دست جدید (شمارنده صفر + ۱۳ برگ تازه) حافظه را پاک می‌کند
    val fresh = hand("AD", "KD", "QD", "JD", "10D", "9D", "8D", "7D", "6D", "5D", "4D", "3D", "2D")
    t.onScan(scan(Suit.CLUBS, fresh, emptyMap(), hakem = 2, active = 2))
    o = t.onScan(scan(Suit.CLUBS, fresh, emptyMap(), hakem = 2, active = 2))
    check("دست جدید: بازی‌شده‌ها و کات‌ها پاک شد و دوباره ۳۹ برگ بازی‌نشده", o.ready && o.played.isEmpty() && o.voids.values.all { it.isEmpty() } && left(o) == 39 && o.tricksDone == 0)

    println(if (fails == 0) "\nهمه‌ی تست‌های کارت‌شمار گذشت" else "\n$fails تست رد شد")
    if (fails > 0) kotlin.system.exitProcess(1)
}
