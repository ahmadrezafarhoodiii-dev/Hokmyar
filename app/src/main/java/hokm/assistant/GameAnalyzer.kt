package hokm.assistant

import android.content.Context
import android.graphics.Bitmap
import hokm.engine.Card
import hokm.game.GameTracker
import hokm.game.TrackerOutput
import hokm.vision.CardRecognizer
import hokm.vision.Frame
import hokm.vision.HandObs
import hokm.vision.Templates

/**
 * فریم → اسکن سبک (نوبت، شمارنده‌ها، حکم، کجا برگ هست) → فقط در صورت نیاز خواندن برگ‌ها → ردیاب → پیشنهاد.
 * خواندن برگ‌های میز فقط وقتی میز عوض شده و خواندن دست فقط اول نوبت من / رگ جدید انجام می‌شود، تا سریع باشد.
 */
class GameAnalyzer(ctx: Context, private val onOutput: (TrackerOutput) -> Unit) : FrameAnalyzer {
    private val recognizer = CardRecognizer(
        Templates.parse(ctx.assets.open("templates.txt").bufferedReader().use { it.readText() })
    )
    private val tracker = GameTracker()
    @Volatile var frames = 0
        private set

    private var lastPresence: Set<Int> = emptySet()
    private var cachedTable: Map<Int, Card> = emptyMap()
    private var cachedHand: List<HandObs> = emptyList()
    private var tableTries = 0
    private var handAge = 100
    private var lastTricks = -1
    private var lastActive: Int? = null
    private var handReadThisTurn = false

    fun reset() { tracker.reset(); cachedHand = emptyList(); cachedTable = emptyMap(); lastPresence = emptySet(); lastTricks = -1; lastActive = null }

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

            // دست
            val myTurnStart = light.activeSeat == 0 && lastActive != 0
            val needHand = cachedHand.isEmpty() || myTurnStart || meChanged || light.tricksSeen != lastTricks ||
                (light.activeSeat == 0 && !handReadThisTurn) || handAge >= 12
            if (needHand) {
                cachedHand = recognizer.detectHand(f, excludeHand); handAge = 0
                if (light.activeSeat == 0) handReadThisTurn = true
            } else handAge++
            if (light.activeSeat != 0) handReadThisTurn = false
            lastPresence = light.presence; lastTricks = light.tricksSeen; lastActive = light.activeSeat

            val out = tracker.onScan(light.copy(hand = cachedHand, table = cachedTable))
            frames++
            onOutput(out)
        } catch (t: Throwable) {
            android.util.Log.w("HokmAnalyzer", "scan failed", t)
        }
    }
}
