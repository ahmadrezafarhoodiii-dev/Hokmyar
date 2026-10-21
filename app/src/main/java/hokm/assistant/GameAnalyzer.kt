package hokm.assistant

import android.content.Context
import android.graphics.Bitmap
import hokm.engine.Card
import hokm.game.CardTracker
import hokm.game.CounterOutput
import hokm.vision.CardRecognizer
import hokm.vision.Frame
import hokm.vision.HandObs
import hokm.vision.Templates

/**
 * فریم → اسکن سبک (نوبت، شمارنده‌ی رگ‌ها، حکم، کجا برگ هست) → فقط در صورت نیاز خواندن برگ‌ها → ردیاب → شمارش.
 * خواندن میز فقط وقتی میز عوض شده؛ خواندن دست فقط اول کار، سر هر رگ جدید یا وقتی برگ خودم روی میز آمد (سه فریم رأی‌گیری).
 */
class GameAnalyzer(ctx: Context, private val onOutput: (CounterOutput) -> Unit) : FrameAnalyzer {
    private val recognizer = CardRecognizer(
        Templates.parse(ctx.assets.open("templates.txt").bufferedReader().use { it.readText() })
    )
    private val tracker = CardTracker()
    @Volatile var frames = 0
        private set

    private var lastPresence: Set<Int> = emptySet()
    private var cachedTable: Map<Int, Card> = emptyMap()
    private var cachedHand: List<HandObs> = emptyList()
    private val reads = ArrayDeque<List<HandObs>>()
    private var burst = 0
    private var tableTries = 0
    private var handAge = 100
    private var lastTricks = -1

    fun reset() {
        tracker.reset(); reads.clear(); burst = 0
        cachedHand = emptyList(); cachedTable = emptyMap(); lastPresence = emptySet(); lastTricks = -1
    }

    override fun analyze(frame: Bitmap) {
        try {
            val px = IntArray(frame.width * frame.height)
            frame.getPixels(px, 0, frame.width, 0, 0, frame.width, frame.height)
            val f = Frame(frame.width, frame.height, px).toReference()
            val light = recognizer.scanLight(f)
            val (excludeHand, excludeTable) = tracker.exclusions()

            // میز
            val presChanged = light.presence != lastPresence
            if (presChanged) tableTries = 0
            cachedTable = cachedTable.filterKeys { it in light.presence }
            val missing = light.presence.any { it !in cachedTable.keys }
            if (light.presence.isNotEmpty() && (presChanged || (missing && tableTries < 3))) {
                cachedTable = recognizer.detectTable(f, excludeTable, light.presence); tableTries++
            }
            val meChanged = (0 in light.presence) != (0 in lastPresence)

            // دست: سه خواندنِ پشت‌سرهم و رأی‌گیری
            val needHand = cachedHand.isEmpty() || meChanged || light.tricksSeen != lastTricks || handAge >= 20
            var handFresh = false
            if (needHand || burst > 0) {
                val r = recognizer.detectHand(f, excludeHand)
                if (needHand) { reads.clear(); burst = 2 } else burst--
                reads.addLast(r); while (reads.size > 3) reads.removeFirst()
                cachedHand = consensus(reads); handAge = 0; handFresh = true
            } else handAge++
            lastPresence = light.presence; lastTricks = light.tricksSeen

            val out = tracker.onScan(light.copy(hand = cachedHand, table = cachedTable, handFresh = handFresh))
            frames++
            onOutput(out)
        } catch (t: Throwable) {
            android.util.Log.w("HokmCounter", "scan failed", t)
        }
    }

    /** با سه خواندن، برگی که در حداقل دو خواندن دیده شده نگه داشته می‌شود. */
    private fun consensus(rs: List<List<HandObs>>): List<HandObs> {
        val need = if (rs.size <= 2) 1 else 2
        val counts = HashMap<Card, Int>()
        for (rd in rs) for (o in rd) counts[o.card] = (counts[o.card] ?: 0) + 1
        val latest = HashMap<Card, HandObs>()
        for (rd in rs) for (o in rd) latest[o.card] = o
        return counts.filter { it.value >= need }.keys.mapNotNull { latest[it] }.sortedBy { it.x }
    }
}
