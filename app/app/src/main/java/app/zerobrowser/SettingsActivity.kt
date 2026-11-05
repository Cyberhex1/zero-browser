package app.zerobrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import org.mozilla.geckoview.StorageController

/** Two-pane TV settings: categories on the left, the selected category's options on the right. */
class SettingsActivity : Activity() {

    private enum class Pane(val label: String) {
        GENERAL("General"), PRIVACY("Privacy"), ADDONS("Add-ons"), CONTROLS("Controls"), ABOUT("About")
    }

    private lateinit var pane: LinearLayout
    private val menuButtons = mutableMapOf<Pane, Button>()
    private lateinit var paneScroll: ScrollView
    private var current: Pane? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val h = resources.getDimensionPixelSize(R.dimen.overscan_h)
        val v = resources.getDimensionPixelSize(R.dimen.overscan_v)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Arcade.GROUND)
            setPadding(h, v, h, v)
        }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menu.addView(tvText("SETTINGS", 30f, bold = true).apply {
            letterSpacing = 0.06f
            setPadding(dp(6), 0, 0, dp(16))
        })
        Pane.entries.forEach { p ->
            val b = tvButton(p.label) { focusPane() }
            b.layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
            b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            b.setOnFocusChangeListener { _, focused -> if (focused && current != p) show(p) }
            menuButtons[p] = b
            menu.addView(b)
        }
        menu.addView(tvButton("Done") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(6), dp(24), dp(6), dp(6)) }
        })
        root.addView(menu)

        pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(8), dp(24))
        }
        paneScroll = ScrollView(this).apply { addView(pane) }
        root.addView(paneScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        setContentView(root)

        val start = intent.getStringExtra(EXTRA_PANE)?.let { name -> Pane.entries.firstOrNull { it.name == name } }
        (menu.getChildAt(1 + (start ?: Pane.GENERAL).ordinal)).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        Gecko.setForeground(this)
    }

    private fun focusPane() {
        pane.post { firstFocusable(pane)?.requestFocus() }
    }

    private fun firstFocusable(v: View): View? {
        if (v.isFocusable) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun show(p: Pane, keepFocusTag: String? = null) {
        current = p
        // The open category stays yellow while focus is in the right pane.
        menuButtons.forEach { (k, b) -> b.setTextColor(if (k == p) Arcade.PINK else Arcade.TEXT) }
        pane.removeAllViews()
        paneScroll.scrollTo(0, 0)
        pane.addView(tvText(p.label.uppercase(), 26f, bold = true).apply {
            letterSpacing = 0.06f
            setPadding(dp(6), 0, 0, dp(8))
        })
        when (p) {
            Pane.GENERAL -> general()
            Pane.PRIVACY -> privacy()
            Pane.ADDONS -> addons()
            Pane.CONTROLS -> controls()
            Pane.ABOUT -> about()
        }
        keepFocusTag?.let { tag -> pane.post { pane.findViewWithTag<View>(tag)?.requestFocus() } }
    }

    // ---- Building blocks ----

    private fun heading(title: String, subtitle: String? = null) {
        pane.addView(arcadeLabel(title, Arcade.CYAN).apply { setPadding(dp(6), dp(20), 0, 0) })
        subtitle?.let {
            pane.addView(tvText(it, 16f).apply {
                setTextColor(Arcade.TEXT_MUTED)
                setPadding(dp(6), dp(2), 0, 0)
            })
        }
    }

    private fun buttons(vararg b: Button) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        b.forEach {
            it.setSingleLine()
            row.addView(it)
        }
        pane.addView(row)
    }

    /** One button per option; the selected one is marked with ✓. */
    private fun <T> choice(key: String, options: List<T>, label: (T) -> String, selected: T, pick: (T) -> Unit) {
        buttons(*options.map { o ->
            tvButton((if (o == selected) "✓  " else "") + label(o)) {
                pick(o)
                show(current!!, "$key:${label(o)}")
            }.apply { tag = "$key:${label(o)}" }
        }.toTypedArray())
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        tvDialog(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Clear") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- Panes ----

    private fun general() {
        val home = Prefs.homePage
        heading("Home page", if (home == null) "Zero Home (saved and recent pages)" else home)
        buttons(
            tvButton((if (home == null) "✓  " else "") + "Zero Home") {
                Prefs.homePage = null
                show(Pane.GENERAL, "home:zero")
            }.apply { tag = "home:zero" },
            tvButton((if (home != null) "✓  " else "") + "Custom page…") { askHomePage() }
                .apply { tag = "home:custom" },
        )

        heading("Search engine", "Used when you type words instead of a web address.")
        choice("engine", Prefs.Engine.entries.toList(), { it.label }, Prefs.engine) { Prefs.engine = it }

        heading("Websites", "Desktop sites suit a TV screen best. Mobile sites are lighter and simpler.")
        choice("desktop", listOf(true, false), { if (it) "Desktop" else "Mobile" }, Prefs.desktopMode) {
            Prefs.desktopMode = it
        }

        heading("Screen border", "Web pages fill the screen. If your TV cuts off the edges, add a border.")
        choice("border", Prefs.ScreenBorder.entries.toList(), { it.label }, Prefs.screenBorder) {
            Prefs.screenBorder = it
        }

        heading("Cursor speed", "How fast the cursor moves while you hold the D-pad.")
        choice("speed", Prefs.CursorSpeed.entries.toList(), { it.label }, Prefs.cursorSpeed) { Prefs.cursorSpeed = it }

        heading("Hide cursor after", "The cursor reappears as soon as you press the D-pad.")
        choice("hide", listOf(3, 6, 10, 0), { if (it == 0) "Never" else "$it sec" }, Prefs.cursorHideSeconds) {
            Prefs.cursorHideSeconds = it
        }
    }

    private fun askHomePage() {
        val input = EditText(this).apply {
            hint = "e.g. youtube.com"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.homePage ?: "")
        }
        tvDialog(this)
            .setTitle("Home page address")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                Prefs.toUrl(input.text.toString())?.let {
                    Prefs.homePage = it
                    show(Pane.GENERAL, "home:custom")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun privacy() {
        heading("History", "Removes the Recent row and the Back/Forward history.")
        buttons(tvButton("Clear history") {
            confirm("Clear history?", "Recently visited pages and Back/Forward history will be removed.") {
                Prefs.clearRecents()
                Gecko.purgeHistoryRequested = true
                toast("History cleared")
            }
        })

        heading("Cookies and site data", "Signs you out of websites and clears their stored data and cache.")
        buttons(tvButton("Clear cookies and site data") {
            confirm("Clear cookies and site data?", "You will be signed out of websites.") {
                Gecko.runtime(this).storageController.clearData(StorageController.ClearFlags.ALL)
                    .accept({ toast("Cookies and site data cleared") }, { toast("Couldn't clear site data") })
            }
        })

        heading("Saved pages", "The tiles in the Saved row of Zero Home.")
        buttons(
            tvButton("Clear saved pages") {
                confirm("Clear saved pages?", "All tiles in the Saved row will be removed.") {
                    Prefs.clearSaved()
                    toast("Saved pages cleared")
                }
            },
            tvButton("Restore default pages") {
                Prefs.restoreDefaultTiles()
                toast("Default pages restored")
            },
        )
    }

    private fun addons() {
        heading(
            "Add-ons",
            "Install Firefox for Android add-ons such as uBlock Origin, turn them on or off, " +
                "change their settings or remove them.",
        )
        buttons(tvButton("Open Add-ons") { startActivity(Intent(this, AddonsActivity::class.java)) })
    }

    private fun controls() {
        heading("Remote controls", "How to use Zero Browser with the Fire TV remote.")
        for ((key, what) in CONTROLS) {
            // Focusable rows so the D-pad can scroll through the list.
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isFocusable = true
                background = getDrawable(R.drawable.focus_bg)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) }
            }
            row.addView(tvText(key, 18f, bold = true).apply {
                setTextColor(Arcade.CYAN)
                gravity = Gravity.TOP
            }, LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(tvText(what, 18f).apply { gravity = Gravity.TOP },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            pane.addView(row)
        }
    }

    private fun about() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        heading(getString(R.string.app_name), "Version $version")

        heading("Updates", "Installs the newest release over this one. Your saved pages, settings and add-ons stay.")
        buttons(tvButton("Check for updates") { }.also { b -> b.setOnClickListener { checkForUpdates(b) } })
        heading("Browser engine", "Mozilla GeckoView ${getString(R.string.engine_version)}")
        if (getString(R.string.engine_version).startsWith("144.")) {
            pane.addView(tvText(
                "This is the legacy build for Fire OS 5/6 (Android 5–7). Mozilla no longer updates " +
                    "this engine, so it gets no further security fixes.", 16f,
            ).apply {
                setTextColor(Arcade.TEXT_MUTED)
                setPadding(dp(6), dp(4), 0, 0)
            })
        }
        heading("Source code", REPO_URL)
        buttons(tvButton("Open on GitHub") {
            startActivity(Intent(this, MainActivity::class.java).setData(Uri.parse(REPO_URL)))
            finish()
        })
        heading("License", "Mozilla Public License 2.0")
    }

    private fun checkForUpdates(button: Button) {
        val label = button.text
        button.isEnabled = false
        button.text = "Checking…"
        fun reset() = runOnUiThread {
            button.isEnabled = true
            button.text = label
        }
        Thread {
            try {
                val installed = Updater.installedVersion(this)
                val latest = Updater.fetchLatest(this)
                runOnUiThread {
                    if (latest == null || !Updater.isNewer(latest.version, installed)) {
                        reset()
                        toast("You have the latest version ($installed)")
                    } else {
                        tvDialog(this)
                            .setTitle("Update to ${latest.version}?")
                            .setMessage(
                                "Zero Browser $installed is installed. Version ${latest.version} " +
                                    "(${latest.sizeBytes / (1024 * 1024)} MB) will download and install over it.",
                            )
                            .setPositiveButton("Update") { _, _ -> downloadUpdate(button, latest, ::reset) }
                            .setNegativeButton("Not now") { _, _ -> reset() }
                            .setOnCancelListener { reset() }
                            .show()
                    }
                }
            } catch (e: Exception) {
                reset()
                runOnUiThread { toast("Couldn't check for updates (${e.message})") }
            }
        }.start()
    }

    private fun downloadUpdate(button: Button, release: Updater.Release, reset: () -> Unit) {
        button.text = "Downloading 0%"
        Thread {
            try {
                val apk = Updater.download(this, release) { pct -> runOnUiThread { button.text = "Downloading $pct%" } }
                runOnUiThread {
                    reset()
                    if (!Updater.install(this, apk)) {
                        toast("Allow Zero Browser to install updates, then press Check for updates again")
                    }
                }
            } catch (e: Exception) {
                reset()
                runOnUiThread { toast("Download failed (${e.message})") }
            }
        }.start()
    }

    companion object {
        const val EXTRA_PANE = "pane"
        private const val REPO_URL = "https://github.com/Cyberhex1/zero-browser"

        val CONTROLS = listOf(
            "D-pad" to "Moves the cursor. Tap for small steps; hold to move faster the longer you hold.",
            "Edge of the screen" to "Keep pushing the cursor against an edge to scroll the page that way.",
            "Select" to "Clicks where the cursor is. Hold Select for a long-press.",
            "Rewind / Fast-forward" to "Scroll up or down by one screen.",
            "Back" to "Goes back a page. From the first page it returns to Zero Home. " +
                "Also closes the top bar, extension panels and fullscreen video. On Zero Home it asks whether to exit.",
            "Menu ☰" to "Shows or hides the top bar. On Zero Home, opens the options for the selected tile.",
            "Top bar" to "Address bar (press Select to type, then Go), Back, Forward, Reload, Home, " +
                "★ Save, Add-ons and Settings. Move between them with the D-pad.",
            "Zero Home" to "D-pad moves between tiles; Select opens one. Hold Select (or press Menu) on a tile " +
                "to open, rename, move or remove it. Type in the search box to search or go to an address.",
            "★ Save" to "In the top bar: saves the current page as a tile on Zero Home. " +
                "On a saved page it reads ★ Unsave and removes it.",
            "Add-ons" to "Install add-ons like uBlock Origin from the Add-ons screen. Their buttons appear in the " +
                "top bar; their panels open in a large box you use with the cursor. Back closes the panel.",
            "Typing on web pages" to "Move the cursor onto a text box and press Select; the on-screen keyboard opens.",
            "Screen edges" to "Web pages fill the whole screen. If your TV cuts off the edges, choose " +
                "Settings → General → Screen border.",
            "Cursor auto-hide" to "The cursor hides after a few seconds without input (change this under " +
                "General). Press the D-pad to bring it back.",
        )
    }
}
