package app.zerobrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * On-screen pointer. The view is only as big as the arrow and is moved with translation,
 * so moving it doesn't redraw the rest of the screen. (px, py) is the tip, in the parent's
 * content coordinates (inside its padding).
 */
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

    private val density = resources.displayMetrics.density
    private val size = 30 * density
    private val pad = 3 * density
    private val arrow = Path().apply {
        val pts = floatArrayOf(0f, 0f, 0f, .86f, .22f, .67f, .37f, .99f, .5f, .93f, .35f, .62f, .62f, .62f)
        moveTo(pts[0] * size, pts[1] * size)
        for (i in 2 until pts.size step 2) lineTo(pts[i] * size, pts[i + 1] * size)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
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
        translationX = x - pad
        translationY = y - pad
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (size + 2 * pad).toInt() + 1
        setMeasuredDimension(side, side)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Start in the middle of the parent once it has a size.
        (parent as? View)?.let { p ->
            p.post {
                if (px < 0) {
                    moveTo(
                        (p.width - p.paddingLeft - p.paddingRight) / 2f,
                        (p.height - p.paddingTop - p.paddingBottom) / 2f,
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(pad, pad)
        fill.color = if (pressedLook) Color.rgb(255, 213, 79) else Color.WHITE
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
