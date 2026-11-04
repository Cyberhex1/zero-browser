package app.zerobrowser

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Arcade look: palette (mirrors res/values/colors.xml) and the bundled Space Grotesk font. */
object Arcade {
    const val GROUND = 0xFF07070B.toInt()
    const val SURFACE = 0xFF101018.toInt()
    const val LINE = 0xFF1F1F2A.toInt()
    const val LINE_STRONG = 0xFF3A3A48.toInt()
    const val TEXT = 0xFFF5F5F7.toInt()
    const val TEXT_MUTED = 0xFFA0A0B4.toInt()
    const val CYAN = 0xFF22D3EE.toInt()
    const val PINK = 0xFFF472B6.toInt()

    /** Tile colors, picked per site. */
    val NEON = intArrayOf(
        CYAN, PINK, 0xFFA3E635.toInt(), 0xFFFB923C.toInt(),
        0xFFA78BFA.toInt(), 0xFFFACC15.toInt(), 0xFF60A5FA.toInt(), 0xFFF87171.toInt(),
    )

    private var medium: Typeface? = null
    private var bold: Typeface? = null

    fun medium(context: Context): Typeface =
        medium ?: Typeface.createFromAsset(context.assets, "fonts/SpaceGrotesk-Medium.ttf").also { medium = it }

    fun bold(context: Context): Typeface =
        bold ?: Typeface.createFromAsset(context.assets, "fonts/SpaceGrotesk-Bold.ttf").also { bold = it }

    /** Applies the Arcade font to every TextView (buttons and fields included) under [view]. */
    fun applyFont(view: View) {
        when (view) {
            is TextView -> {
                val isBold = view.typeface?.isBold == true
                view.typeface = if (isBold) bold(view.context) else medium(view.context)
            }
            is ViewGroup -> for (i in 0 until view.childCount) applyFont(view.getChildAt(i))
        }
    }
}

/** Large remote-friendly button with a visible focus ring. */
fun Context.tvButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = true
    letterSpacing = 0.06f
    textSize = 18f
    typeface = Arcade.medium(this@tvButton)
    setTextColor(Arcade.TEXT)
    background = getDrawable(R.drawable.focus_bg)
    stateListAnimator = null
    minHeight = dp(56)
    minimumHeight = dp(56)
    setPadding(dp(22), dp(8), dp(22), dp(8))
    isFocusable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
}

fun Context.tvText(text: CharSequence, size: Float = 20f, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Arcade.TEXT)
        gravity = Gravity.CENTER_VERTICAL
        typeface = if (bold) Arcade.bold(this@tvText) else Arcade.medium(this@tvText)
    }

/** Small spaced-out uppercase label in an accent color, like "SAVED". */
fun Context.arcadeLabel(text: String, color: Int): TextView = tvText(text.uppercase(), 16f, bold = true).apply {
    letterSpacing = 0.2f
    setTextColor(color)
}

fun tvDialog(context: Context): AlertDialog.Builder = AlertDialog.Builder(context, R.style.Theme_Arcade_Dialog)
