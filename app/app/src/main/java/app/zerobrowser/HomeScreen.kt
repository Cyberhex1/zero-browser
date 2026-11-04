package app.zerobrowser

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The Zero Home screen: search field, saved pages and recently visited pages as big
 * remote-friendly tiles. Navigated with normal D-pad focus (no cursor).
 */
class HomeScreen(
    private val activity: Activity,
    private val root: ScrollView,
    private val onOpen: (url: String) -> Unit,
    private val onSettings: () -> Unit,
    private val onAddons: () -> Unit,
) {
    private enum class Kind { SAVED, RECENT }
    private data class TileRef(val tile: Tile, val kind: Kind)

    private val search: EditText
    private val savedGrid: GridLayout
    private val recentLabel: TextView
    private val recentGrid: GridLayout

    init {
        val a = activity
        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(24))
        }

        val header = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            a.tvText(a.getString(R.string.app_name), 30f, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(a.tvButton("Add-ons") { onAddons() })
        header.addView(a.tvButton("⚙  Settings") { onSettings() })
        content.addView(header)

        search = EditText(a).apply {
            hint = a.getString(R.string.url_hint)
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 24f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = a.getDrawable(R.drawable.focus_bg)
            setPadding(a.dp(22), a.dp(14), a.dp(22), a.dp(14))
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                    Prefs.toUrl(text.toString())?.let { url -> setText(""); onOpen(url) }
                    true
                } else {
                    false
                }
            }
        }
        content.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(a.dp(6), a.dp(10), a.dp(6), a.dp(6)) })

        content.addView(sectionLabel("Saved"))
        savedGrid = grid()
        content.addView(savedGrid)

        recentLabel = sectionLabel("Recent")
        content.addView(recentLabel)
        recentGrid = grid()
        content.addView(recentGrid)

        content.addView(a.tvText("Hold Select (or press Menu ☰) on a tile for options.", 16f).apply {
            setTextColor(Color.GRAY)
            setPadding(a.dp(6), a.dp(18), 0, 0)
        })

        root.clipChildren = false
        root.addView(content)
    }

    private fun sectionLabel(text: String) = activity.tvText(text, 22f, bold = true).apply {
        setTextColor(Color.rgb(255, 213, 79))
        setPadding(activity.dp(6), activity.dp(18), 0, activity.dp(4))
    }

    private fun grid() = GridLayout(activity).apply {
        columnCount = COLUMNS
        clipChildren = false
        clipToPadding = false
    }

    /** Rebuilds both rows from storage; focuses [focusId] if given, else the first tile. */
    fun refresh(focusId: String? = null) {
        fill(savedGrid, Prefs.tiles, Kind.SAVED)
        savedGrid.addView(addTile())
        val recents = Prefs.recents
        fill(recentGrid, recents, Kind.RECENT)
        val showRecent = if (recents.isEmpty()) View.GONE else View.VISIBLE
        recentLabel.visibility = showRecent
        recentGrid.visibility = showRecent

        root.post {
            val target = focusId?.let { id -> findTileView(id) } ?: savedGrid.getChildAt(0)
            (target ?: search).requestFocus()
        }
    }

    private fun findTileView(id: String): View? =
        listOf(savedGrid, recentGrid).asSequence()
            .flatMap { g -> (0 until g.childCount).asSequence().map { g.getChildAt(it) } }
            .firstOrNull { (it.tag as? TileRef)?.tile?.id == id }

    private fun tileWidth(): Int {
        val usable = root.width.takeIf { it > 0 } ?: (activity.resources.displayMetrics.widthPixels -
            2 * activity.resources.getDimensionPixelSize(R.dimen.overscan_h))
        // Minus the content padding (12dp each side) and each tile's margins (8dp each side).
        return (usable - activity.dp(24)) / COLUMNS - activity.dp(16)
    }

    private fun fill(grid: GridLayout, tiles: List<Tile>, kind: Kind) {
        grid.removeAllViews()
        tiles.forEach { grid.addView(tileView(it, kind)) }
    }

    private fun tileFrame(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        background = activity.getDrawable(R.drawable.focus_bg)
        setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
        layoutParams = GridLayout.LayoutParams().apply {
            width = tileWidth()
            setMargins(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
        }
        setOnFocusChangeListener { v, focused ->
            v.animate().scaleX(if (focused) 1.07f else 1f).scaleY(if (focused) 1.07f else 1f)
                .setDuration(120).start()
            if (focused) v.elevation = activity.dp(8).toFloat() else v.elevation = 0f
        }
    }

    private fun tileView(tile: Tile, kind: Kind): View {
        val a = activity
        val frame = tileFrame()
        frame.tag = TileRef(tile, kind)
        val thumbW = tileWidth() - a.dp(16)
        val thumbH = thumbW * 9 / 16

        val thumb = Prefs.thumbFile(tile).takeIf { kind == Kind.SAVED && it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) }
        val art: View = if (thumb != null) {
            ImageView(a).apply {
                setImageBitmap(thumb)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        } else {
            FrameLayout(a).apply {
                setBackgroundColor(colorFor(tile.url))
                addView(
                    a.tvText(initialOf(tile), 36f, bold = true).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            }
        }
        frame.addView(art, LinearLayout.LayoutParams(thumbW, thumbH))
        frame.addView(a.tvText(tile.title, 17f).apply {
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(a.dp(2), a.dp(6), a.dp(2), 0)
        })
        frame.setOnClickListener { onOpen(tile.url) }
        frame.setOnLongClickListener { showOptions(frame.tag as TileRef); true }
        return frame
    }

    private fun addTile(): View {
        val a = activity
        val frame = tileFrame()
        val thumbW = tileWidth() - a.dp(16)
        frame.addView(
            a.tvText("+", 44f, bold = true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
            },
            LinearLayout.LayoutParams(thumbW, thumbW * 9 / 16),
        )
        frame.addView(a.tvText("Add a page", 18f).apply { setPadding(a.dp(2), a.dp(6), 0, 0) })
        frame.setOnClickListener { editTile(null) }
        return frame
    }

    /** Menu key on the home screen: options for the focused tile. */
    fun onMenu() {
        (activity.currentFocus?.tag as? TileRef)?.let(::showOptions)
    }

    private fun showOptions(ref: TileRef) {
        val tile = ref.tile
        val items = mutableListOf<Pair<String, () -> Unit>>("Open" to { onOpen(tile.url) })
        when (ref.kind) {
            Kind.SAVED -> {
                items += "Rename" to { editTile(tile) }
                items += "Move left" to { move(tile, -1) }
                items += "Move right" to { move(tile, +1) }
                items += "Remove from Home" to {
                    Prefs.tiles = Prefs.tiles.filter { it.id != tile.id }
                    refresh()
                }
            }
            Kind.RECENT -> {
                if (Prefs.savedTile(tile.url) == null) {
                    items += "Save to Home" to {
                        val saved = Tile(Prefs.newId(), tile.title, tile.url)
                        Prefs.tiles = Prefs.tiles + saved
                        toast("Saved to Home")
                        refresh(saved.id)
                    }
                }
                items += "Clear recent pages" to {
                    Prefs.clearRecents()
                    refresh()
                }
            }
        }
        tvDialog(activity)
            .setTitle(tile.title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun move(tile: Tile, delta: Int) {
        val list = Prefs.tiles.toMutableList()
        val i = list.indexOfFirst { it.id == tile.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        Prefs.tiles = list
        refresh(tile.id)
    }

    /** Add (tile == null) or rename a saved page. */
    private fun editTile(tile: Tile?) {
        val a = activity
        val name = EditText(a).apply {
            hint = "Name"
            setSingleLine()
            setText(tile?.title ?: "")
        }
        val address = EditText(a).apply {
            hint = "Web address, e.g. youtube.com"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(tile?.url ?: "")
        }
        val form = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(20), a.dp(8), a.dp(20), 0)
            addView(name)
            addView(address)
        }
        tvDialog(a)
            .setTitle(if (tile == null) "Add a page" else "Rename")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val url = Prefs.toUrl(address.text.toString()) ?: return@setPositiveButton
                val title = name.text.toString().trim().ifEmpty { Prefs.hostOf(url) }
                val updated = if (tile == null) {
                    Tile(Prefs.newId(), title, url).also { Prefs.tiles = Prefs.tiles + it }
                } else {
                    tile.copy(title = title, url = url).also { t ->
                        Prefs.tiles = Prefs.tiles.map { if (it.id == t.id) t else it }
                    }
                }
                refresh(updated.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    private fun initialOf(tile: Tile): String =
        (tile.title.firstOrNull { it.isLetterOrDigit() } ?: '?').uppercaseChar().toString()

    private fun colorFor(url: String): Int {
        val palette = intArrayOf(
            Color.rgb(30, 136, 229), Color.rgb(229, 57, 53), Color.rgb(67, 160, 71),
            Color.rgb(142, 36, 170), Color.rgb(251, 140, 0), Color.rgb(0, 137, 123),
            Color.rgb(84, 110, 122), Color.rgb(216, 27, 96),
        )
        return palette[(Prefs.hostOf(url).hashCode() and 0x7fffffff) % palette.size]
    }

    companion object {
        private const val COLUMNS = 5
    }
}
