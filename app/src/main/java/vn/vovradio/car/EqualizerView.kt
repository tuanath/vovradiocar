package vn.vovradio.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/** Thanh sóng nhạc nhỏ dưới tên kênh, nhảy khi đang phát. */
class EqualizerView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6FB6FF.toInt() }
    private val bars = 30
    private val heights = FloatArray(bars) { 0.18f }
    private val rect = RectF()

    var active = false
        set(v) {
            if (field == v) return
            field = v
            if (!v) heights.fill(0.18f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = w / bars
        val bw = gap * 0.42f
        paint.alpha = if (active) 235 else 80
        for (i in 0 until bars) {
            if (active) heights[i] = (heights[i] * 0.55f + Random.nextFloat() * 0.45f).coerceIn(0.15f, 1f)
            val bh = h * heights[i]
            val x = i * gap + (gap - bw) / 2
            rect.set(x, (h - bh) / 2, x + bw, (h + bh) / 2)
            canvas.drawRoundRect(rect, bw / 2, bw / 2, paint)
        }
        if (active) postInvalidateDelayed(110)
    }
}
