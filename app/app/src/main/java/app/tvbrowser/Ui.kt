package app.tvbrowser

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Large remote-friendly button with a visible focus ring. */
fun Context.tvButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 20f
    setTextColor(Color.WHITE)
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
    ).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
}

fun Context.tvText(text: CharSequence, size: Float = 20f, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun tvDialog(context: Context): AlertDialog.Builder =
    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
