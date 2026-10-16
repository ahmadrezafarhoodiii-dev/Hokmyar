package hokm.engine

enum class Suit(val symbol: String, val fa: String) {
    SPADES("♠", "پیک"),
    HEARTS("♥", "دل"),
    DIAMONDS("♦", "خشت"),
    CLUBS("♣", "گشنیز")
}

/** ترتیب از کم‌ارزش به پرارزش؛ آس بالاترین است. */
enum class Rank(val label: String, val fa: String) {
    TWO("2", "۲"), THREE("3", "۳"), FOUR("4", "۴"), FIVE("5", "۵"),
    SIX("6", "۶"), SEVEN("7", "۷"), EIGHT("8", "۸"), NINE("9", "۹"),
    TEN("10", "۱۰"), JACK("J", "سرباز"), QUEEN("Q", "بیبی"),
    KING("K", "شاه"), ACE("A", "آس")
}

data class Card(val suit: Suit, val rank: Rank) {
    override fun toString() = "${rank.label}${suit.symbol}"

    companion object {
        val FULL_DECK: List<Card> = Suit.values().flatMap { s -> Rank.values().map { Card(s, it) } }
    }
}

/** صندلی‌ها به ترتیب نوبت بازی: ۰=من، ۱=نفر بعدی، ۲=هم‌تیمی من، ۳=نفر قبلی. */
fun partnerOf(seat: Int) = (seat + 2) % 4
fun teamOf(seat: Int) = seat % 2

data class Play(val seat: Int, val card: Card)
data class Trick(val leader: Int, val plays: List<Play>, val winner: Int)

/** آیا [c] از [current] (برنده‌ی فعلی رگ) بالاتر است؟ */
fun beats(c: Card, current: Card, led: Suit, trump: Suit): Boolean = when {
    c.suit == current.suit -> c.rank > current.rank
    c.suit == trump -> true            // current حکم نیست چون suit متفاوت است
    else -> false
}

fun trickWinner(plays: List<Play>, trump: Suit): Play {
    require(plays.isNotEmpty())
    val led = plays.first().card.suit
    var best = plays.first()
    for (p in plays.drop(1)) if (beats(p.card, best.card, led, trump)) best = p
    return best
}

/** برگ‌های مجاز: اگر خال زمین را دارد باید همان را بزند. */
fun legalCards(hand: Collection<Card>, led: Suit?): List<Card> {
    if (led == null) return hand.toList()
    val follow = hand.filter { it.suit == led }
    return if (follow.isNotEmpty()) follow else hand.toList()
}
