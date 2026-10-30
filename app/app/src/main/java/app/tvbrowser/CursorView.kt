package app.tvbrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** On-screen pointer drawn over the page. (px, py) is the tip, in this view's coordinates. */
class CursorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var px = -1f
        private set
    var py = -1f
        private set

    var pressedLook = false
        set(value) {
            field = value
            invalidate()
        }

    private val size = 30 * resources.displayMetrics.density
    private val arrow = Path().apply {
        val pts = floatArrayOf(0f, 0f, 0f, .86f, .22f, .67f, .37f, .99f, .5f, .93f, .35f, .62f, .62f, .62f)
        moveTo(pts[0] * size, pts[1] * size)
        for (i in 2 until pts.size step 2) lineTo(pts[i] * size, pts[i + 1] * size)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (px < 0 || px > w || py > h) moveTo(w / 2f, h / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(px, py)
        fill.color = if (pressedLook) Color.rgb(255, 213, 79) else Color.WHITE
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
