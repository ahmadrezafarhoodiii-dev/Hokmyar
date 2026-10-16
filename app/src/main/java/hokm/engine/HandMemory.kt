package hokm.engine

/**
 * حافظه‌ی یک «دست» (۱۳ رگ). با startNewHand همه‌چیز پاک می‌شود.
 * برگ‌های بازی‌شده‌ی همه، رگ‌ها، کات‌ها و نوبت را نگه می‌دارد.
 * برگی که روی زمین هست ولی تشخیص داده نشد «ناشناخته» ثبت می‌شود تا ترتیب و تعداد رگ‌ها خراب نشود.
 */
class HandMemory(val mySeat: Int = 0) {
    var trump: Suit? = null
        private set
    /** شروع‌کننده‌ی رگ جاری؛ ‎-1‎ یعنی هنوز معلوم نیست (مثلاً برنده‌ی رگ قبل ناشناخته بود). */
    var currentLeader: Int = 0
        private set

    private val _myCards = mutableSetOf<Card>()
    private val _played = mutableSetOf<Card>()
    private val _tricks = mutableListOf<Trick>()
    /** رگ جاری به ترتیب بازی: صندلی ← برگ (null = بازی شده ولی خوانده نشده). */
    private val _cur = LinkedHashMap<Int, Card?>()
    private val _voids = Array(4) { mutableSetOf<Suit>() }
    private val _teamTricks = IntArray(2)
    private var baseTricks = 0
    private var lenient = false

    val myCards: Set<Card> get() = _myCards
    val playedCards: Set<Card> get() = _played
    val completedTricks: List<Trick> get() = _tricks
    val currentTrick: List<Play> get() = _cur.entries.mapNotNull { e -> e.value?.let { Play(e.key, it) } }
    /** صندلی‌هایی که در رگ جاری برگ انداخته‌اند ولی برگشان خوانده نشده. */
    val unknownSeats: Set<Int> get() = _cur.entries.filter { it.value == null }.map { it.key }.toSet()
    /** تعداد برگ‌های روی زمین در رگ جاری (خوانده یا ناخوانده). */
    val currentTrickSize: Int get() = _cur.size
    val teamTricks: IntArray get() = _teamTricks.copyOf()
    fun knownVoids(seat: Int): Set<Suit> = _voids[seat]

    /** خال زمین، اگر برگ اول رگ خوانده شده باشد. */
    val ledSuit: Suit? get() = _cur.values.firstOrNull()?.suit
    val nextToPlay: Int get() = if (currentLeader < 0) -1 else (currentLeader + _cur.size) % 4

    val isHandDecided: Boolean get() = _teamTricks.any { it >= 7 }
    /** تعداد رگ‌های کامل‌شده (شامل رگ‌هایی که قبل از شروع ردیابی بازی شده‌اند). */
    val tricksCompleted: Int get() = baseTricks + _tricks.size
    val isHandComplete: Boolean get() = tricksCompleted == 13
    /** بازی تا رگ سیزدهم ادامه دارد (امتیاز کُت)؛ پس فقط ۱۳ رگ کامل «پایان دست» است. */
    val isHandOver: Boolean get() = isHandComplete

    /** تعداد برگ‌هایی که الان باید در دست من باشد (حتی اگر اسکن همه را نخوانده باشد). */
    val expectedMyHandSize: Int get() = handSize(mySeat)

    /** برگی که بعداً (مثلاً با تشخیص بهتر) در دست دیده شد و هنوز در حافظه نبود. */
    fun addMyCard(card: Card) {
        if (card in _played || card in _myCards) return
        if (_myCards.size < expectedMyHandSize) _myCards.add(card)
    }

    /** شمارنده‌های رگ از روی صفحه؛ منبع قابل‌اعتمادتر از محاسبه‌ی خودمان. */
    fun syncTeamTricks(myTeamTricks: Int, otherTeamTricks: Int) {
        _teamTricks[teamOf(mySeat)] = myTeamTricks; _teamTricks[1 - teamOf(mySeat)] = otherTeamTricks
    }

    /** برگ‌هایی که نه در دست من‌اند و نه بازی شده‌اند: یعنی دست بقیه. */
    fun unknownCards(): List<Card> = Card.FULL_DECK.filter { it !in _myCards && it !in _played }

    /** تعداد برگ‌های باقی‌مانده‌ی هر صندلی. */
    fun handSize(seat: Int): Int {
        val playedInTrick = if (_cur.containsKey(seat)) 1 else 0
        return 13 - tricksCompleted - playedInTrick
    }

    private fun resetCommon(trump: Suit, myCards: Collection<Card>, leader: Int) {
        this.trump = trump
        currentLeader = leader
        _myCards.clear(); _myCards.addAll(myCards)
        _played.clear(); _tricks.clear(); _cur.clear()
        _voids.forEach { it.clear() }
        _teamTricks.fill(0)
        baseTricks = 0; lenient = false
    }

    fun startNewHand(trump: Suit, myCards: Collection<Card>, firstLeader: Int) {
        require(myCards.size == 13 && myCards.toSet().size == 13) { "دست من باید ۱۳ برگ متفاوت باشد" }
        require(firstLeader in 0..3)
        resetCommon(trump, myCards, firstLeader)
    }

    /**
     * شروع ردیابی وسط دست (مثلاً وقتی اسکن را دیر روشن کرده‌اید): برگ‌های رگ‌های قبلی را نمی‌دانیم.
     * دست من ممکن است ناقص خوانده شده باشد.
     */
    fun startMidHand(trump: Suit, myCards: Collection<Card>, tricksCompleted: Int, leader: Int, myTeamTricks: Int, otherTeamTricks: Int) {
        require(tricksCompleted in 0..12 && leader in -1..3)
        resetCommon(trump, myCards, leader)
        _teamTricks[teamOf(mySeat)] = myTeamTricks; _teamTricks[1 - teamOf(mySeat)] = otherTeamTricks
        baseTricks = tricksCompleted; lenient = true
    }

    /** وقتی رگ جدید هنوز شروع نشده، نفر شروع‌کننده را اصلاح می‌کند. */
    fun setLeader(seat: Int) {
        check(_cur.isEmpty()) { "وسط رگ نمی‌شود شروع‌کننده را عوض کرد" }
        currentLeader = seat
    }

    /** شمارنده‌ی صفحه از حافظه جلوتر است (رگ‌هایی را ندیدیم): رگ‌های جاری را کنار می‌گذارد. */
    fun fastForwardTricks(target: Int) {
        val t = target.coerceAtMost(13)
        if (t <= tricksCompleted) return
        baseTricks += t - tricksCompleted
        _cur.clear()
        currentLeader = -1
    }

    private fun finishIfComplete(): Int? {
        if (_cur.size < 4) return null
        val t = trump ?: return null
        val plays = _cur.entries.mapNotNull { e -> e.value?.let { Play(e.key, it) } }
        val leader = currentLeader
        var winner: Int? = null
        if (plays.size == 4) {
            winner = trickWinner(plays, t).seat
            _tricks.add(Trick(leader, plays, winner))
            _teamTricks[teamOf(winner)]++
            currentLeader = winner
        } else {
            // برنده معلوم نیست؛ شمارنده‌ی صفحه و نفر بعدی که برگ می‌اندازد مشخصش می‌کند
            baseTricks++
            currentLeader = -1
        }
        _cur.clear()
        return winner
    }

    /** ثبت یک برگ. اگر رگ کامل شد، برنده‌ی رگ (شماره‌ی صندلی) برگردانده می‌شود. */
    fun recordPlay(seat: Int, card: Card): Int? {
        check(trump != null) { "دست شروع نشده" }
        check(!isHandOver) { "دست تمام شده؛ startNewHand را صدا بزنید" }
        check(currentLeader >= 0) { "شروع‌کننده‌ی رگ معلوم نیست" }
        check(seat == nextToPlay) { "نوبت صندلی $nextToPlay است نه $seat" }
        check(card !in _played) { "$card قبلاً بازی شده" }
        if (seat == mySeat) {
            if (card in _myCards) _myCards.remove(card)
            else check(lenient) { "$card در دست من نیست" }
        } else {
            check(card !in _myCards) { "$card در دست من است و نمی‌تواند دست دیگری باشد" }
        }
        val led = ledSuit
        if (led != null && card.suit != led) _voids[seat].add(led)   // پیروی نکرد => از آن خال ندارد
        _played.add(card)
        _cur[seat] = card
        return finishIfComplete()
    }

    /** برگی روی زمین هست ولی خوانده نشد. */
    fun recordUnknownPlay(seat: Int): Int? {
        check(trump != null && !isHandOver && currentLeader >= 0) { "حافظه آماده نیست" }
        check(seat == nextToPlay) { "نوبت صندلی $nextToPlay است نه $seat" }
        _cur[seat] = null
        return finishIfComplete()
    }

    /** برگی که قبلاً ناشناخته ثبت شده بود بالاخره خوانده شد. */
    fun resolveUnknown(seat: Int, card: Card) {
        if (!_cur.containsKey(seat) || _cur[seat] != null) return
        if (card in _played) return
        if (seat == mySeat) _myCards.remove(card) else check(card !in _myCards) { "$card در دست من است" }
        val led = _cur.values.firstOrNull()?.suit
        if (led != null && card.suit != led) _voids[seat].add(led)
        _played.add(card)
        _cur[seat] = card
    }

    /** رگ جاری را با برگ‌های نمونه‌گیری‌شده برای صندلی‌های ناشناخته کامل می‌کند (برای شبیه‌سازی). */
    fun currentTrickWith(sampled: Map<Int, Card>): List<Play> =
        _cur.entries.mapNotNull { e -> (e.value ?: sampled[e.key])?.let { Play(e.key, it) } }
}
