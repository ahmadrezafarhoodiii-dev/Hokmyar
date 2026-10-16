package hokm.game

import hokm.engine.*
import hokm.vision.ScanResult

/** خروجی هر اسکن برای نمایش روی صفحه. */
data class TrackerOutput(
    val trump: Suit?,
    val myTurn: Boolean,
    val advice: List<Suggestion>,
    val handText: String,
    val note: String,
    val remainingCards: List<Card> = emptyList(),
    val voidsBySeat: Map<Int, Set<Suit>> = emptyMap()
)

/**
 * اسکن‌های پشت‌سرهم را به حافظه‌ی دست تبدیل می‌کند.
 * - نوبت و شروع‌کننده از آواتار سبزِ بازیکن فعال خوانده می‌شود (نه حدس)؛ شروع‌کننده = بازیکن فعال منهای تعداد برگ‌های روی زمین.
 * - هر برگ تازه (ترتیب: من ← راست ← بالا ← چپ) ثبت می‌شود؛ برگ ناخوانده «ناشناخته» ثبت می‌شود تا ترتیب خراب نشود.
 * - برنده‌ی هر رگ (ما یا حریف)، کات‌ها و شمارنده‌ی رگ‌ها حفظ می‌شود؛ با تمام شدن دست (یا حکم/۱۳ برگ جدید) حافظه پاک می‌شود.
 * - برگ‌های مجاز از تیره‌شدن برگ‌ها یا خال زمین؛ پیشنهاد فقط از آن‌هاست.
 */
class GameTracker(private val advisor: Advisor = Advisor(samples = 120)) {
    private val mem = HandMemory(0)
    private var started = false
    private var awaitClear = false
    private var cachedKey = ""
    private var cachedAdvice: List<Suggestion> = emptyList()
    var resyncs = 0; private set

    val memory: HandMemory get() = mem

    fun reset() { started = false; cachedKey = ""; awaitClear = false }

    /** برای تشخیص بهتر: برگ‌های بازی‌شده نمی‌توانند در دست من باشند و برگ‌های من نمی‌توانند دست دیگران باشند. */
    fun exclusions(): Pair<Set<Card>, Set<Card>> =
        if (!started) emptySet<Card>() to emptySet() else mem.playedCards.toSet() to mem.myCards.toSet()

    private val seatName = arrayOf("شما", "بازیکن راست", "هم‌تیمی", "بازیکن چپ")

    fun onScan(s: ScanResult): TrackerOutput {
        val trump = s.trump ?: return TrackerOutput(null, false, emptyList(), "", "حکم هنوز مشخص نیست")
        val myCards = s.hand.map { it.card }.distinct()
        if (myCards.isEmpty() && !started) return TrackerOutput(trump, false, emptyList(), "", "دست شما هنوز خوانده نشده")

        if (needsNewHand(s, trump, myCards)) startHand(s, trump, myCards)
        else if (started) {
            if (s.tricksSeen > mem.tricksCompleted && s.tricksSeen <= 13) { mem.fastForwardTricks(s.tricksSeen); awaitClear = s.presence.size == 4 }
            try { record(s) } catch (e: IllegalStateException) { resyncs++; startHand(s, trump, myCards) }
        }

        val active = s.activeSeat
        val meOnTable = 0 in s.table || 0 in s.presence
        val screenSaysMine = (s.anyDimmed || s.turnText) && !meOnTable
        var myTurn = started && !mem.isHandOver && mem.myCards.isNotEmpty() &&
            (if (active != null) active == 0 else (!meOnTable && (screenSaysMine || mem.nextToPlay == 0)))
        if (myTurn && mem.nextToPlay != 0) {
            // نوبت من است ولی حافظه هماهنگ نیست: شروع‌کننده‌ی رگ خالی را اصلاح یا حافظه را دوباره می‌سازیم
            if (mem.currentTrickSize == 0) mem.setLeader(0)
            else { resyncs++; startHand(s, trump, myCards) }
        }
        if (started) {
            // برگ‌هایی از دست که تازه خوانده شدند، تا جایی که تعداد دست اجازه می‌دهد، اضافه می‌شوند
            for (c in myCards) mem.addMyCard(c)
            if (s.tricksSeen == mem.tricksCompleted) mem.syncTeamTricks(s.tricksBottom, s.tricksRight)
        }
        myTurn = myTurn && mem.nextToPlay == 0

        var advice = emptyList<Suggestion>()
        var note = when {
            !started -> ""
            mem.isHandOver -> "دست تمام شد"
            myTurn -> "نوبت شماست"
            active != null && active != 0 -> "منتظر ${seatName[active]}"
            else -> "منتظر بازیکنان دیگر"
        }
        if (myTurn) {
            val legal: Set<Card>? = if (s.anyDimmed) s.hand.filter { !it.dimmed }.map { it.card }.toSet() else null
            val key = "${mem.tricksCompleted}/${mem.currentTrickSize}/${mem.unknownSeats}/${mem.myCards.size}/${legal?.size}"
            if (key != cachedKey) {
                cachedAdvice = try { advisor.advise(mem, legal) } catch (e: IllegalStateException) { emptyList() }
                cachedKey = key
            }
            advice = cachedAdvice.filter { it.card in mem.myCards && (legal == null || it.card in legal) }
            if (advice.isEmpty() && legal != null && legal.isNotEmpty()) {
                advice = legal.sortedBy { it.rank.ordinal }.map { Suggestion(it, 0.0, 0.0, 0.0) }
                note = "نوبت شماست (حافظه ناقص)"
            }
        }
        val remaining = if (started) mem.unknownCards() else emptyList()
        val voids = if (started) (0..3).associateWith { seat -> mem.knownVoids(seat).toSet() } else emptyMap()
        return TrackerOutput(trump, myTurn, advice, "", note, remaining, voids)
    }

    private fun needsNewHand(s: ScanResult, trump: Suit, myCards: List<Card>): Boolean {
        if (!started) return true
        if (mem.trump != trump) return true
        if (s.tricksSeen + 1 < mem.tricksCompleted) return true                       // شمارنده بازنشانی شد
        if (s.tricksSeen == 0 && mem.tricksCompleted >= 2) return true
        if (mem.isHandOver && myCards.size >= 8) return true                           // دست قبلی تمام شده و دست جدید پخش شد
        if (myCards.size >= mem.expectedMyHandSize + 3 && s.tricksSeen <= 1) return true  // ۱۳ برگ تازه دیده شد
        return false
    }

    private fun startHand(s: ScanResult, trump: Suit, myCards: List<Card>) {
        val tricks = s.tricksSeen.coerceAtMost(12)
        val leader = guessLeader(s)
        if (tricks == 0 && myCards.size == 13 && s.table[0] == null && 0 !in s.presence && leader >= 0) {
            mem.startNewHand(trump, myCards, leader)
        } else {
            // شمارنده‌ی پایین = تیم من، ستون راست = حریف‌ها
            mem.startMidHand(trump, myCards, tricks, leader, s.tricksBottom, s.tricksRight)
        }
        started = true; cachedKey = ""
        awaitClear = s.presence.size == 4
        try { record(s) } catch (e: IllegalStateException) { /* ناسازگاری باقی‌مانده را نادیده می‌گیریم */ }
    }

    /** شروع‌کننده‌ی رگ فعلی؛ ‎-1‎ اگر معلوم نیست. */
    private fun guessLeader(s: ScanResult): Int {
        val k = s.presence.size
        arcStart(s.presence)?.let { return it }
        s.activeSeat?.let { a -> if (k in 0..3) return (a - k + 4) % 4 }
        if (k == 0) {
            if (s.turnText || s.anyDimmed) return 0
            if (s.tricksSeen == 0 && s.hakemSeat != null) return s.hakemSeat
        }
        return if (s.tricksSeen == 0) (s.hakemSeat ?: 0) else -1
    }

    /** اگر صندلی‌ها یک قوس پیوسته (در ترتیب نوبت) بسازند و کمتر از ۴ باشند، شروع قوس را برمی‌گرداند. */
    private fun arcStart(seats: Set<Int>): Int? {
        if (seats.isEmpty() || seats.size >= 4) return null
        for (s0 in seats) {
            if (((s0 + 3) % 4) in seats) continue
            if ((0 until seats.size).all { (s0 + it) % 4 in seats }) return s0
        }
        return null
    }

    private fun record(s: ScanResult) {
        if (mem.isHandOver) return
        val pres = s.presence
        // برگ‌های رگِ تمام‌شده هنوز روی میزند: تا پاک شدن میز نادیده بگیر
        if (awaitClear) { if (pres.size >= 4) return else awaitClear = false }
        if (mem.currentTrickSize == 0) {
            if (pres.isEmpty()) {
                // رگ هنوز شروع نشده: شروع‌کننده همان بازیکن فعال است
                val a = s.activeSeat
                if (a != null && a != mem.currentLeader) mem.setLeader(a)
                return
            }
            val leader = guessLeader(s)
            if (leader >= 0 && leader != mem.currentLeader) mem.setLeader(leader)
        }
        if (mem.currentLeader < 0) return
        for ((seat, card) in s.table) mem.resolveUnknown(seat, card)
        val before = mem.tricksCompleted
        while (!mem.isHandOver) {
            val seat = mem.nextToPlay
            if (seat !in pres) break
            val card = s.table[seat]
            if (card != null) {
                if (card in mem.playedCards) break      // قبلاً ثبت شده (رگ تمام‌شده‌ای که هنوز پاک نشده)
                mem.recordPlay(seat, card)
            } else mem.recordUnknownPlay(seat)
            if (mem.currentTrickSize == 0) break        // رگ تمام شد
        }
        if (mem.tricksCompleted > before && pres.size >= 4) awaitClear = true
    }
}
