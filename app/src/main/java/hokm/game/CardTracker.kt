package hokm.game

import hokm.engine.*
import hokm.vision.ScanResult

/**
 * خروجی کارت‌شمار برای نمایش روی صفحه.
 * ready=false یعنی هنوز دست شناخته نشده (پنل باید «در انتظار…» نشان بدهد، نه جدول خالی).
 */
data class CounterOutput(
    val ready: Boolean,
    val trump: Suit?,
    val note: String,
    /** برگ‌های بازی‌نشده‌ای که در دست من نیست (یعنی دست هم‌تیمی و دو حریف)، به تفکیک خال و از بزرگ به کوچک. */
    val unseen: Map<Suit, List<Rank>>,
    /** برگ‌های باقی‌مانده‌ی دست خودم. */
    val mine: Set<Card>,
    /** برگ‌های بازی‌شده تا این لحظه. */
    val played: Set<Card>,
    /** خال‌هایی که هر بازیکن ندارد (از ناتوانی در پیروی از خال زمین): صندلی ۱ راست، ۲ هم‌تیمی، ۳ چپ. */
    val voids: Map<Int, Set<Suit>>,
    /** تعداد برگ‌هایی که باید بازی شده باشند ولی ثبت نشده‌اند؛ اگر >۰ باشد شمارش ممکن است چند برگ بیشتر نشان بدهد. */
    val unrecorded: Int,
    val tricksDone: Int
) {
    companion object {
        val EMPTY = CounterOutput(false, null, "", emptyMap(), emptySet(), emptySet(), emptyMap(), 0, 0)
    }
}

/**
 * اسکن‌های پشت‌سرهم را به حافظه‌ی دست تبدیل می‌کند و فقط «شمارش» می‌دهد:
 * چه برگ‌هایی بازی نشده، و هر بازیکن از چه خال‌هایی خالی شده.
 * - نوبت و شروع‌کننده‌ی رگ از آواتار سبزِ بازیکن فعال خوانده می‌شود.
 * - هر برگ تازه (ترتیب: من ← راست ← بالا ← چپ) ثبت می‌شود؛ برگ ناخوانده «ناشناخته» ثبت می‌شود تا ترتیب خراب نشود.
 * - برگی که روی میز دیده شود حتی با ناسازگاری ترتیب هم «بازی‌شده» حساب می‌شود.
 * - با تمام شدن دست (یا ۱۳ برگ تازه / صفر شدن شمارنده‌ی رگ‌ها) حافظه پاک می‌شود؛ ناسازگاری فقط رگ جاری را از نو می‌سازد.
 */
class CardTracker {
    private val mem = HandMemory(0)
    private var started = false
    private var awaitClear = false
    private var newHandVotes = 0
    var resyncs = 0; private set

    val memory: HandMemory get() = mem

    fun reset() { started = false; awaitClear = false; newHandVotes = 0 }

    /** برای تشخیص بهتر: برگ‌های بازی‌شده نمی‌توانند در دست من باشند و برگ‌های من نمی‌توانند دست دیگران باشند. */
    fun exclusions(): Pair<Set<Card>, Set<Card>> =
        if (!started) emptySet<Card>() to emptySet() else mem.allPlayed to mem.myCards.toSet()

    fun onScan(s: ScanResult): CounterOutput {
        val trump = s.trump ?: return if (started) snapshot("حکم لحظه‌ای خوانده نشد") else CounterOutput.EMPTY.copy(note = "منتظر انتخاب حکم…")
        val myCards = s.hand.map { it.card }.distinct()
        if (myCards.isEmpty() && !started) return CounterOutput.EMPTY.copy(trump = trump, note = "دست شما هنوز خوانده نشده…")

        if (needsNewHand(s, trump, myCards)) startHand(s, trump, myCards)
        else if (started) {
            if (s.tricksSeen > mem.tricksCompleted && s.tricksSeen <= 13) { mem.fastForwardTricks(s.tricksSeen); awaitClear = s.presence.size == 4 }
            try { record(s) } catch (e: IllegalStateException) { softResync(s) }
        }
        if (started) {
            // هر برگی که روی میز خوانده شده، بازی‌شده است؛ حتی اگر ثبت ترتیبش نشد
            for ((seat, card) in s.table) if (seat != 0) mem.noteSeenCard(card)
            if (s.handFresh && myCards.size == mem.expectedMyHandSize) mem.setMyCards(myCards)
            else if (s.handFresh && myCards.isNotEmpty()) mem.trimMyCards(myCards)
            for (c in myCards) mem.addMyCard(c)
            if (s.tricksSeen == mem.tricksCompleted) mem.syncTeamTricks(s.tricksBottom, s.tricksRight)
        }
        val note = when {
            !started -> ""
            mem.isHandOver -> "دست تمام شد"
            else -> ""
        }
        return snapshot(note)
    }

    private fun snapshot(note: String): CounterOutput {
        if (!started) return CounterOutput.EMPTY.copy(note = note)
        val unseen = HashMap<Suit, List<Rank>>()
        val left = mem.unknownCards()
        for (suit in Suit.values()) unseen[suit] = left.filter { it.suit == suit }.map { it.rank }.sortedByDescending { it.ordinal }
        val voids = (1..3).associateWith { seat -> mem.knownVoids(seat).toSet() }
        return CounterOutput(true, mem.trump, note, unseen, mem.myCards.toSet(), mem.allPlayed, voids, mem.unrecordedPlays, mem.tricksCompleted)
    }

    /** شروع دست جدید فقط وقتی پذیرفته می‌شود که در دو اسکن پشت‌سرهم نشانه‌ها تأیید شوند (تا سوسو‌زدن نشان حکم یا شمارنده حافظه را پاک نکند). */
    private fun needsNewHand(s: ScanResult, trump: Suit, myCards: List<Card>): Boolean {
        if (!started) return true
        val signal =
            (mem.isHandOver && myCards.size >= 8) ||
            (s.tricksSeen == 0 && mem.tricksCompleted >= 2 && myCards.size >= 10) ||
            (trump != mem.trump && (s.tricksSeen < mem.tricksCompleted || mem.isHandOver || myCards.size >= 11)) ||
            (s.tricksSeen + 2 < mem.tricksCompleted) ||
            (myCards.size >= mem.expectedMyHandSize + 3 && s.tricksSeen <= 1 && mem.tricksCompleted >= 2)
        newHandVotes = if (signal) newHandVotes + 1 else 0
        if (newHandVotes >= 2) { newHandVotes = 0; return true }
        return false
    }

    /** حافظه با صفحه هماهنگ نیست: فقط رگ جاری از نو ساخته می‌شود؛ تاریخچه‌ی رگ‌های قبلی حفظ می‌شود. */
    private fun softResync(s: ScanResult) {
        resyncs++
        mem.resetCurrentTrick(); awaitClear = false
        try { record(s) } catch (e: IllegalStateException) { /* آنچه ثبت شد کافی است */ }
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
        started = true
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
                if (seat != 0 && card in mem.myCards) mem.recordUnknownPlay(seat)   // احتمالاً بد خوانده شده؛ به‌عنوان ناشناخته
                else {
                    if (seat == 0 && card !in mem.myCards) mem.addMyCard(card)          // برگ خودم که در دست خوانده نشده بود
                    if (seat == 0 && card !in mem.myCards) mem.recordUnknownPlay(seat) else mem.recordPlay(seat, card)
                }
            } else mem.recordUnknownPlay(seat)
            if (mem.currentTrickSize == 0) break        // رگ تمام شد
        }
        if (mem.tricksCompleted > before && pres.size >= 4) awaitClear = true
    }
}
