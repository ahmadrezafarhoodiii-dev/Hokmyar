package hokm.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import hokm.engine.Card
import hokm.engine.Rank
import hokm.engine.Suit
import hokm.game.CounterOutput

/**
 * جدول کارت‌شمار: چهار ردیف (♠ ♥ ♦ ♣) و ۱۳ خانه (A تا ۲).
 *  سفید = هنوز بازی نشده (دست هم‌تیمی و حریف‌ها) · طلایی = برگ خودم · خاکستریِ کم‌رنگ = بازی شده
 * زیرش: خال‌هایی که هر بازیکن تمام کرده (راست، هم‌تیمی، چپ).
 * همه‌ی اندازه‌ها بر حسب پیکسل مرجع ۱۱۵۹ عرض هستند و با [sc] مقیاس می‌شوند.
 */
class CounterView(context: Context, private val sc: Float) : View(context) {

    private var data: CounterOutput = CounterOutput.EMPTY

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9000000") }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CCCCCC"); textAlign = Paint.Align.LEFT }
    private val frame = RectF()

    fun update(o: CounterOutput) { data = o; invalidate() }

    private fun suitSymbol(s: Suit) = when (s) { Suit.SPADES -> "♠"; Suit.HEARTS -> "♥"; Suit.DIAMONDS -> "♦"; Suit.CLUBS -> "♣" }
    private fun suitColor(s: Suit) = if (s == Suit.HEARTS || s == Suit.DIAMONDS) Color.parseColor("#FF5252") else Color.WHITE

    override fun onDraw(c: Canvas) {
        frame.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(frame, 14f * sc, 14f * sc, bg)
        val d = data
        val pad = 8f * sc
        // ── سطر وضعیت
        small.textSize = 20f * sc
        val header = when {
            !d.ready -> d.note.ifEmpty { "منتظر شروع دست…" }
            d.note.isNotEmpty() -> d.note
            else -> "رگ ${d.tricksDone}/13 · بازی‌نشده‌ی دیگران: ${d.unseen.values.sumOf { it.size }}" +
                (if (d.unrecorded > 0) " · ⚠ ${d.unrecorded} برگ ثبت‌نشده" else "")
        }
        c.drawText(header, pad, 26f * sc, small)
        if (!d.ready) return

        // ── جدول ۴×۱۳
        val top = 36f * sc
        val rowH = 36f * sc
        val labelW = 30f * sc
        val cellW = (width - labelW - pad) / 13f
        val suits = arrayOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS)
        val ranksDesc = Rank.values().reversed()
        for ((r, suit) in suits.withIndex()) {
            val cy = top + r * rowH + rowH * 0.72f
            txt.textSize = 26f * sc; txt.color = suitColor(suit); txt.isFakeBoldText = true
            c.drawText(suitSymbol(suit), pad + labelW / 2f, cy, txt)
            txt.isFakeBoldText = false; txt.textSize = 19f * sc
            val unseen = d.unseen[suit].orEmpty().toSet()
            for ((i, rank) in ranksDesc.withIndex()) {
                val card = Card(suit, rank)
                txt.color = when {
                    card in d.mine -> Color.parseColor("#FFC107")
                    rank in unseen -> Color.WHITE
                    else -> Color.parseColor("#55FFFFFF")
                }
                c.drawText(rank.label, pad + labelW + i * cellW + cellW / 2f, cy, txt)
            }
        }

        // ── خال‌های تمام‌شده‌ی بازیکن‌ها
        val y = top + 4 * rowH + rowH * 0.72f
        val groupW = (width - 2 * pad) / 3f
        val names = arrayOf("راست", "هم‌تیمی", "چپ")
        for (g in 0 until 3) {
            val seat = g + 1
            var x = pad + g * groupW
            small.textSize = 18f * sc; small.color = Color.parseColor("#CCCCCC")
            c.drawText(names[g], x, y, small)
            x += small.measureText(names[g]) + 6f * sc
            val vs = d.voids[seat].orEmpty()
            if (vs.isEmpty()) { c.drawText("—", x, y, small) }
            else for (s in suits) if (s in vs) {
                small.textSize = 24f * sc; small.color = suitColor(s)
                c.drawText(suitSymbol(s), x, y, small)
                x += small.measureText(suitSymbol(s)) + 3f * sc
            }
        }
    }
}
