package app.zerobrowser

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Our own dialog in the Arcade look. Fire OS replaces the system AlertDialog's styling
 * with its own, so this draws everything itself. Builder methods mirror AlertDialog.Builder.
 */
class ArcadeDialog(private val context: Context) {
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var view: View? = null
    private var items: Array<String>? = null
    private var itemsListener: ((DialogInterface, Int) -> Unit)? = null
    private var singleChecked = -1
    private var single = false
    private var multiChecked: BooleanArray? = null
    private var multiListener: ((DialogInterface, Int, Boolean) -> Unit)? = null
    private var positive: Pair<String, ((DialogInterface, Int) -> Unit)?>? = null
    private var negative: Pair<String, ((DialogInterface, Int) -> Unit)?>? = null
    private var onDismiss: ((DialogInterface) -> Unit)? = null
    private var onCancel: ((DialogInterface) -> Unit)? = null

    fun setTitle(text: CharSequence?) = apply { title = text }
    fun setMessage(text: CharSequence?) = apply { message = text }
    fun setView(v: View) = apply { view = v }

    fun setItems(labels: Array<String>, listener: (DialogInterface, Int) -> Unit) = apply {
        items = labels
        itemsListener = listener
    }

    fun setSingleChoiceItems(labels: Array<String>, checked: Int, listener: (DialogInterface, Int) -> Unit) = apply {
        items = labels
        single = true
        singleChecked = checked
        itemsListener = listener
    }

    fun setMultiChoiceItems(
        labels: Array<String>,
        checked: BooleanArray,
        listener: (DialogInterface, Int, Boolean) -> Unit,
    ) = apply {
        items = labels
        multiChecked = checked
        multiListener = listener
    }

    fun setPositiveButton(label: String, listener: ((DialogInterface, Int) -> Unit)?) =
        apply { positive = label to listener }

    fun setNegativeButton(label: String, listener: ((DialogInterface, Int) -> Unit)?) =
        apply { negative = label to listener }

    fun setOnDismissListener(listener: (DialogInterface) -> Unit) = apply { onDismiss = listener }
    fun setOnCancelListener(listener: (DialogInterface) -> Unit) = apply { onCancel = listener }

    fun show(): Dialog {
        val c = context
        val dialog = Dialog(c)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = c.getDrawable(R.drawable.panel_bg)
            setPadding(c.dp(32), c.dp(26), c.dp(32), c.dp(22))
        }
        title?.let {
            panel.addView(c.tvText(it, 26f, bold = true).apply { setPadding(c.dp(4), 0, 0, c.dp(10)) })
        }
        message?.let {
            panel.addView(c.tvText(it, 19f).apply {
                setTextColor(Arcade.TEXT)
                setLineSpacing(0f, 1.15f)
                setPadding(c.dp(4), 0, 0, c.dp(12))
            })
        }
        view?.let { v ->
            styleFields(v)
            (v.parent as? ViewGroup)?.removeView(v)
            panel.addView(v)
        }

        val rows = mutableListOf<Button>()
        items?.let { labels ->
            val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            labels.forEachIndexed { i, label ->
                val row = c.tvButton(label) { }.apply {
                    isAllCaps = false
                    letterSpacing = 0f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { setMargins(0, c.dp(3), 0, c.dp(3)) }
                }
                rows += row
                list.addView(row)
            }
            fun refreshMarks() = rows.forEachIndexed { i, b ->
                b.text = when {
                    multiChecked != null -> (if (multiChecked!![i]) "■  " else "□  ") + labels[i]
                    single -> (if (i == singleChecked) "●  " else "○  ") + labels[i]
                    else -> labels[i]
                }
            }
            refreshMarks()
            rows.forEachIndexed { i, b ->
                b.setOnClickListener {
                    val m = multiChecked
                    if (m != null) {
                        m[i] = !m[i]
                        refreshMarks()
                        multiListener?.invoke(dialog, i, m[i])
                    } else {
                        itemsListener?.invoke(dialog, i)
                        if (!single) dialog.dismiss()
                    }
                }
            }
            // Long lists scroll; keep the dialog within the screen.
            val maxH = (c.resources.displayMetrics.heightPixels * 0.6f).toInt()
            panel.addView(object : ScrollView(c) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
                }
            }.apply { addView(list) })
        }

        val buttons = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, c.dp(12), 0, 0)
        }
        var positiveButton: Button? = null
        negative?.let { (label, listener) ->
            buttons.addView(c.tvButton(label) { listener?.invoke(dialog, DialogInterface.BUTTON_NEGATIVE); dialog.dismiss() })
        }
        positive?.let { (label, listener) ->
            positiveButton = c.tvButton(label) { listener?.invoke(dialog, DialogInterface.BUTTON_POSITIVE); dialog.dismiss() }
                .apply { setTextColor(Arcade.CYAN) }
            buttons.addView(positiveButton)
        }
        if (buttons.childCount > 0) panel.addView(buttons)

        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((c.resources.displayMetrics.widthPixels * 0.55f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.7f)
        }
        dialog.setOnDismissListener { d -> onDismiss?.invoke(d) }
        dialog.setOnCancelListener { d -> onCancel?.invoke(d) }
        dialog.show()

        // Start in an empty text field (e.g. "Add a page"), else on the selected choice,
        // else the main button, else the first item.
        val first: View? = emptyField(view)
            ?: rows.getOrNull(singleChecked.takeIf { single && it >= 0 } ?: -1)
            ?: positiveButton ?: rows.firstOrNull()
        first?.requestFocus()
        return dialog
    }

    private fun emptyField(v: View?): EditText? = when (v) {
        is EditText -> v.takeIf { it.text.isNullOrEmpty() }
        is ViewGroup -> (0 until v.childCount).asSequence().mapNotNull { emptyField(v.getChildAt(it)) }.firstOrNull()
        else -> null
    }

    /** Text fields passed in with setView get the Arcade field look. */
    private fun styleFields(v: View) {
        when (v) {
            is EditText -> v.apply {
                background = context.getDrawable(R.drawable.focus_bg)
                setTextColor(Arcade.TEXT)
                setHintTextColor(Arcade.TEXT_MUTED)
                typeface = Arcade.medium(context)
                textSize = 20f
                setPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
                (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, context.dp(4), 0, context.dp(4))
            }
            is ViewGroup -> {
                v.setPadding(0, v.paddingTop, 0, v.paddingBottom)
                for (i in 0 until v.childCount) styleFields(v.getChildAt(i))
            }
        }
    }
}
