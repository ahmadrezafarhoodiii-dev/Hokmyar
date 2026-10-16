package hokm.engine

import kotlin.random.Random

data class Suggestion(
    val card: Card,
    val expectedTeamTricks: Double,   // میانگین رگ‌های تیم من تا آخر دست (از ۱۳)
    val winHandProbability: Double,   // احتمال رسیدن تیم من به ۷ رگ
    val winTrickProbability: Double = 0.0   // احتمال بردن همین رگ توسط تیم من
) {
    val score: Double get() = winHandProbability * 10 + expectedTeamTricks
}

/** یک دنیای ممکن: دست‌ها + برگ‌هایی که برای صندلی‌های «ناخوانده»ی رگ جاری فرض شده. */
internal class World(val hands: Array<MutableList<Card>>, val unknownPlayed: Map<Int, Card>)

/** پخش تصادفی برگ‌های ناشناخته بین بازیکن‌ها، با رعایت «کات‌های» کشف‌شده و خال زمین. */
internal object WorldSampler {
    fun sample(memory: HandMemory, random: Random): World {
        var unknown = memory.unknownCards()
        // اگر اسکن همه‌ی برگ‌های دست من را نخوانده، برگ‌های «گم‌شده‌ی» من هم جزو دنیای نمونه‌گیری‌اند
        val myMissing = (memory.handSize(memory.mySeat) - memory.myCards.size).coerceAtLeast(0)
        val others = (0..3).filter { it != memory.mySeat || myMissing > 0 }
        val sizes = IntArray(4) { if (it == memory.mySeat) myMissing else memory.handSize(it) }
        // برگی که روی زمین هست ولی خوانده نشده: از برگ‌های ممکن (هم‌خال زمین، مگر کات‌شده) نمونه می‌گیریم
        val unkPlayed = HashMap<Int, Card>()
        val led = memory.ledSuit
        for (seat in memory.unknownSeats) {
            val cand = unknown.filter { c -> led == null || c.suit == led || led in memory.knownVoids(seat) }
            val pick = (cand.ifEmpty { unknown }).random(random)
            unkPlayed[seat] = pick; unknown = unknown - pick
        }
        // اگر وسط دست شروع کرده‌ایم، برگ‌های رگ‌های قبلی ناشناخته‌اند: فقط به اندازه‌ی جای خالی برمی‌داریم.
        val pool = if (unknown.size > sizes.sum()) unknown.shuffled(random).take(sizes.sum()) else unknown
        repeat(300) {
            tryAssign(memory, pool, others, sizes, random, respectVoids = true)?.let { return World(it, unkPlayed) }
        }
        // اگر قیدها غیرممکن شد (خطای اسکن)، بدون قید کات پخش می‌کنیم تا برنامه از کار نیفتد.
        return World(tryAssign(memory, pool, others, sizes, random, respectVoids = false)!!, unkPlayed)
    }

    private fun tryAssign(
        m: HandMemory, unknown: List<Card>, others: List<Int>, sizes: IntArray,
        random: Random, respectVoids: Boolean
    ): Array<MutableList<Card>>? {
        val remaining = sizes.copyOf()
        val hands = Array(4) { mutableListOf<Card>() }
        fun eligible(c: Card) = others.filter { !respectVoids || c.suit !in m.knownVoids(it) }
        // برگ‌هایی که کمترین انتخاب را دارند اول جا می‌گیرند.
        val ordered = unknown.shuffled(random).sortedBy { eligible(it).size }
        for (c in ordered) {
            val elig = eligible(c).filter { remaining[it] > 0 }
            if (elig.isEmpty()) return null
            var r = random.nextInt(elig.sumOf { remaining[it] })
            var pick = elig.last()
            for (s in elig) { if (r < remaining[s]) { pick = s; break }; r -= remaining[s] }
            hands[pick].add(c); remaining[pick]--
        }
        return hands
    }
}

class Advisor(private val random: Random = Random.Default, private val samples: Int = 400) {

    /** مرتب‌سازی با ترجیح «ارزان‌تر»: اگر برگ پایین‌تر هم‌خال تقریباً به اندازه‌ی برگ بالاتر خوب است، برگ بالاتر بی‌دلیل خرج نمی‌شود. */
    private fun rank(list: List<Suggestion>): List<Suggestion> {
        val sorted = list.sortedByDescending { it.score }
        val eps = 0.18
        fun dominated(c: Suggestion) = sorted.any { d ->
            d !== c && d.card.suit == c.card.suit && d.card.rank < c.card.rank && d.score >= c.score - eps
        }
        val (bad, good) = sorted.partition { dominated(it) }
        return good + bad
    }

    /**
     * برای نوبت من بهترین برگ‌ها را به ترتیب برمی‌گرداند (اولی بهترین).
     * legal: اگر از روی صفحه معلوم است کدام برگ‌ها مجازند (برگ‌های تیره‌نشده).
     */
    fun advise(memory: HandMemory, legal: Set<Card>? = null): List<Suggestion> {
        val trump = memory.trump ?: error("دست شروع نشده")
        check(memory.nextToPlay == memory.mySeat) { "الان نوبت من نیست" }
        var candidates = legalCards(memory.myCards, memory.ledSuit)
        if (legal != null) candidates = candidates.filter { it in legal }.ifEmpty { candidates }
        check(candidates.isNotEmpty()) { "دست من خالی است" }
        val myTeam = teamOf(memory.mySeat)
        val cur0 = memory.currentTrick
        if (candidates.size == 1) return listOf(Suggestion(candidates[0], 0.0, 0.0, 0.0))

        // ── تصمیم قطعی و فوری: رگ قطعاً باخته است و برگ من تغییری در نتیجه‌اش نمی‌دهد ──
        val led = memory.ledSuit
        if (led != null && memory.unknownSeats.isEmpty() && cur0.isNotEmpty() && candidates.all { it.suit == led }) {
            val win = trickWinner(cur0, trump)
            val partnerPlayed = cur0.any { it.seat == partnerOf(memory.mySeat) }
            val canWin = candidates.any { beats(it, win.card, led, trump) }
            if (teamOf(win.seat) != myTeam && partnerPlayed && !canWin)
                return candidates.sortedBy { it.rank.ordinal }.map { Suggestion(it, 0.0, 0.0, 0.0) }   // کم‌ارزش‌ترین
        }

        val tricksSum = DoubleArray(candidates.size)
        val wins = IntArray(candidates.size)
        val trickWins = IntArray(candidates.size)
        val endgame = memory.unknownSeats.isEmpty() &&
            (0..3).all { memory.handSize(it) + (if (cur0.any { p -> p.seat == it }) 1 else 0) <= 4 } && memory.expectedMyHandSize <= 4
        val n = if (endgame) minOf(samples, 60) else samples
        repeat(n) {
            val w = WorldSampler.sample(memory, random)
            val world = w.hands
            world[memory.mySeat].addAll(memory.myCards)
            val cur = memory.currentTrickWith(w.unknownPlayed)
            if (endgame) {
                val dd = DDSolver(trump, myTeam)
                val hands = LongArray(4) { dd.mask(world[it]) }
                val trickIdx = IntArray(4)
                cur.forEachIndexed { i, p -> trickIdx[i] = p.card.suit.ordinal * 13 + p.card.rank.ordinal }
                candidates.forEachIndexed { i, card ->
                    val h = hands.copyOf(); val t = trickIdx.copyOf()
                    val ci = card.suit.ordinal * 13 + card.rank.ordinal
                    h[memory.mySeat] = h[memory.mySeat] xor (1L shl ci); t[cur.size] = ci
                    val total = memory.teamTricks[myTeam] + dd.solve(h, memory.currentLeader, t, cur.size + 1)
                    tricksSum[i] += total.toDouble()
                    if (total >= 7) wins[i]++
                    val s2 = Sim.from(memory, world, trump, cur); s2.play(memory.mySeat, card); s2.finishCurrentTrick()
                    if (s2.firstWinnerTeam == myTeam) trickWins[i]++
                }
            } else candidates.forEachIndexed { i, card ->
                val sim = Sim.from(memory, world, trump, cur)
                sim.play(memory.mySeat, card)
                sim.rollOut()
                tricksSum[i] += sim.teamTricks[myTeam].toDouble()
                if (sim.teamTricks[myTeam] >= 7) wins[i]++
                if (sim.firstWinnerTeam == myTeam) trickWins[i]++
            }
        }
        return rank(candidates.indices.map {
            Suggestion(candidates[it], tricksSum[it] / n, wins[it].toDouble() / n, trickWins[it].toDouble() / n)
        })
    }

    /** احتمال اینکه هر برگ ناشناخته دست هر صندلی باشد (برای نمایش «احتمالاً کی چی دارد»). */
    fun estimateHoldings(memory: HandMemory, n: Int = 400): Map<Card, DoubleArray> {
        val counts = memory.unknownCards().associateWith { DoubleArray(4) }
        repeat(n) {
            val world = WorldSampler.sample(memory, random).hands
            for (seat in 0..3) for (c in world[seat]) counts[c]!![seat] += 1.0 / n
        }
        return counts
    }
}


/** حل دقیق (اطلاعات کامل) برای آخر بازی؛ هر دست حداکثر ۵ برگ. نتیجه: تعداد رگ‌های تیم من از این لحظه به بعد. */
internal class DDSolver(private val trump: Suit, private val myTeam: Int) {
    private data class Key(val a: Long, val b: Long, val c: Long, val d: Long, val leader: Int)
    private val memo = HashMap<Key, Int>()
    private val suitMask = LongArray(4) { s -> ((1L shl 13) - 1) shl (s * 13) }
    private fun idx(c: Card) = c.suit.ordinal * 13 + c.rank.ordinal
    fun mask(cards: Collection<Card>): Long { var m = 0L; for (c in cards) m = m or (1L shl idx(c)); return m }

    /** trick: برگ‌های بازی‌شده‌ی رگ فعلی به ترتیب (ایندکس برگ). */
    fun solve(hands: LongArray, leader: Int, trick: IntArray, k: Int): Int = search(hands, leader, trick, k)

    private fun beats(c: Int, cur: Int, led: Int): Boolean {
        val cs = c / 13; val ks = cur / 13
        return when {
            cs == ks -> c % 13 > cur % 13
            cs == trump.ordinal -> true
            else -> false
        }
    }

    private fun search(h: LongArray, leader: Int, trick: IntArray, k: Int): Int {
        if (k == 4) {
            var best = 0; val led = trick[0] / 13
            for (i in 1 until 4) if (beats(trick[i], trick[best], led)) best = i
            val winner = (leader + best) % 4
            val gain = if (winner % 2 == myTeam) 1 else 0
            if (h[0] == 0L && h[1] == 0L && h[2] == 0L && h[3] == 0L) return gain
            return gain + search(h, winner, trick, 0)
        }
        val key = if (k == 0) Key(h[0], h[1], h[2], h[3], leader) else null
        if (key != null) memo[key]?.let { return it }
        val seat = (leader + k) % 4
        var legal = h[seat]
        if (k > 0) { val led = trick[0] / 13; val follow = legal and suitMask[led]; if (follow != 0L) legal = follow }
        val maximize = seat % 2 == myTeam
        var best = if (maximize) -1 else 99
        var m = legal
        while (m != 0L) {
            val low = m and -m; val c = java.lang.Long.numberOfTrailingZeros(low); m = m xor low
            h[seat] = h[seat] xor low; trick[k] = c
            val v = search(h, leader, trick, k + 1)
            h[seat] = h[seat] or low
            if (maximize) { if (v > best) best = v } else if (v < best) best = v
        }
        if (key != null) memo[key] = best
        return best
    }
}

/** شبیه‌سازی یک دست با اطلاعات کامل (دنیای نمونه‌گیری‌شده) و سیاست ساده‌ی «بازیکن خوب» برای همه. */
internal class Sim(
    val hands: Array<MutableList<Card>>,
    val trump: Suit,
    var leader: Int,
    val trick: MutableList<Play>,
    val teamTricks: IntArray,
    val unplayed: MutableSet<Card>
) {
    /** تیم برنده‌ی رگی که در لحظه‌ی شروع شبیه‌سازی جاری بود. */
    var firstWinnerTeam = -1

    companion object {
        fun from(m: HandMemory, world: Array<MutableList<Card>>, trump: Suit, trick: List<Play> = m.currentTrick) = Sim(
            hands = Array(4) { world[it].toMutableList() },
            trump = trump,
            leader = m.currentLeader,
            trick = trick.toMutableList(),
            teamTricks = m.teamTricks,
            unplayed = Card.FULL_DECK.filter { it !in m.playedCards && trick.none { p -> p.card == it } }.toMutableSet()
        )
    }

    fun play(seat: Int, card: Card) {
        hands[seat].remove(card)
        unplayed.remove(card)
        trick.add(Play(seat, card))
        if (trick.size == 4) {
            val w = trickWinner(trick, trump).seat
            teamTricks[teamOf(w)]++
            if (firstWinnerTeam < 0) firstWinnerTeam = teamOf(w)
            leader = w
            trick.clear()
        }
    }

    fun rollOut() {
        while (hands.any { it.isNotEmpty() }) {
            val seat = (leader + trick.size) % 4
            if (hands[seat].isEmpty()) break
            play(seat, choose(seat))
        }
    }

    /** فقط رگ جاری را تمام می‌کند (برای احتمال بردن همین رگ). */
    fun finishCurrentTrick() {
        while (firstWinnerTeam < 0) {
            val seat = (leader + trick.size) % 4
            if (hands[seat].isEmpty()) break
            play(seat, choose(seat))
        }
    }

    private fun cost(c: Card) = (if (c.suit == trump) 100 else 0) + c.rank.ordinal
    private fun lowest(cards: List<Card>) = cards.minBy { cost(it) }

    /** بالاترین برگ باقی‌مانده‌ی آن خال (غیر از برگ‌های دست خودم). */
    private fun isMaster(c: Card, hand: List<Card>) =
        unplayed.none { it.suit == c.suit && it.rank > c.rank && it !in hand }

    /** دور انداختن: از کوتاه‌ترین خالِ غیرحکم، کم‌ارزش‌ترین برگ. */
    private fun discard(legal: List<Card>, hand: List<Card>): Card {
        if (legal.all { it.suit == legal[0].suit }) return lowest(legal)
        val nonTrump = legal.filter { it.suit != trump }
        if (nonTrump.isEmpty()) return lowest(legal)
        val bySuit = nonTrump.groupBy { it.suit }
        // خالی که در آن مسلط داریم را نگه می‌داریم؛ از کوتاه‌ترین خالِ بدون مسلط می‌اندازیم
        val candidates = bySuit.filter { (_, cs) -> cs.none { isMaster(it, hand) } }.ifEmpty { bySuit }
        val suit = candidates.minBy { (_, cs) -> cs.size }.key
        return lowest(bySuit.getValue(suit))
    }

    private fun choose(seat: Int): Card {
        val hand = hands[seat]
        val led = trick.firstOrNull()?.card?.suit ?: return chooseLead(hand)
        val legal = legalCards(hand, led)
        if (legal.size == 1) return legal[0]
        val win = trickWinner(trick, trump)
        val partnerWinning = teamOf(win.seat) == teamOf(seat)
        val last = trick.size == 3
        val following = legal[0].suit == led
        if (partnerWinning) {
            // هم‌تیمی دارد می‌برد؛ ولی اگر برگش مسلط نیست و هنوز کسی بعد از من هست، با برگ مسلط خودم تضمینش کن
            if (!last && following && !isMaster(win.card, hand)) {
                legal.filter { isMaster(it, hand) && beats(it, win.card, led, trump) }.minByOrNull { it.rank }?.let { return it }
            }
            return discard(legal, hand)
        }
        val winners = legal.filter { beats(it, win.card, led, trump) }
        if (winners.isEmpty()) return discard(legal, hand)
        if (last || !following) return lowest(winners)      // آخر یا کات: ارزان‌ترین برنده
        // وسط رگ: ترجیحاً مسلط‌ترین ارزان؛ وگرنه بالاترین برگ تا کمتر بالا زده شود
        val masters = winners.filter { isMaster(it, hand) }
        return masters.minByOrNull { cost(it) } ?: winners.maxBy { cost(it) }
    }

    private fun chooseLead(hand: List<Card>): Card {
        val nonTrump = hand.filter { it.suit != trump }
        // اول برگ مسلط غیرحکم
        nonTrump.filter { isMaster(it, hand) }.maxByOrNull { it.rank }?.let { return it }
        val trumps = hand.filter { it.suit == trump }
        // حکم مسلط داریم: برای کشیدن حکم‌های حریف
        trumps.filter { isMaster(it, hand) }.maxByOrNull { it.rank }?.let { return it }
        if (nonTrump.isEmpty()) return lowest(hand)
        val bySuit = nonTrump.groupBy { it.suit }
        // با حکم کافی: از کوتاه‌ترین خال شروع کن تا بعداً کات کنی؛ وگرنه کم‌برگ از بلندترین خال
        val suit = if (trumps.size >= 2) bySuit.minBy { it.value.size }.key else bySuit.maxBy { it.value.size }.key
        return lowest(bySuit.getValue(suit))
    }
}

