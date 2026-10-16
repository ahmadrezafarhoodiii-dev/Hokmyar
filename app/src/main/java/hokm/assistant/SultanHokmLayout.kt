package hokm.assistant

/**
 * چیدمان صفحه‌ی «سلطان حکم» (com.crazy.dragon.hokm) به صورت کسری از اندازه‌ی صفحه.
 * اندازه‌ی مرجع اسکرین‌شات‌ها: ۱۱۵۹ × ۲۵۷۶.
 * ترتیب صندلی‌ها در موتور: ۰ من (پایین)، ۱ راست، ۲ بالا (هم‌تیمی)، ۳ چپ.
 * ترتیب نوبت: من ← راست ← بالا ← چپ.
 */
object SultanHokmLayout {
    const val REF_W = 1159
    const val REF_H = 2576
    const val PACKAGE = "com.crazy.dragon.hokm"

    /** x0, y0, x1, y1 */
    val trumpBadge = floatArrayOf(0.08f, 0.14f, 0.38f, 0.20f)
    val tableCards = floatArrayOf(0.17f, 0.22f, 0.83f, 0.59f)
    val myHand = floatArrayOf(0.00f, 0.83f, 1.00f, 1.00f)
    /** پنل مزاحم گیم‌تربو بالا راست */
    val ignore = floatArrayOf(0.77f, 0.04f, 1.00f, 0.23f)

    val seatNames = arrayOf("me", "right", "partner(top)", "left")
}
