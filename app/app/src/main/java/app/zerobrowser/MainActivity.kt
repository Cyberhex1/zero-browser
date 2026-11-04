package app.zerobrowser

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import kotlin.math.min

class MainActivity : Activity() {

    private lateinit var root: View
    private lateinit var geckoView: GeckoView
    private lateinit var cursor: CursorView
    private lateinit var progress: ProgressBar
    private lateinit var topBar: View
    private lateinit var urlField: EditText
    private lateinit var actionWrap: View
    private lateinit var actionRow: LinearLayout
    private lateinit var popupLayer: View
    private lateinit var popupView: GeckoView
    private lateinit var homeLayer: View
    private lateinit var home: HomeScreen
    private lateinit var saveButton: android.widget.Button

    private lateinit var session: GeckoSession
    private var popupSession: GeckoSession? = null

    private var canGoBack = false
    private var canGoForward = false
    private var currentUrl = ""
    private var currentTitle: String? = null
    private var fullscreen = false
    private var appliedDesktopMode = true
    private var tapDownTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideCursor = Runnable { cursor.visibility = View.INVISIBLE }
    // Lazy: CursorMover reads resources, which aren't available until onCreate.
    private val mover by lazy { CursorMover() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        geckoView = findViewById(R.id.gecko)
        cursor = findViewById(R.id.cursor)
        progress = findViewById(R.id.progress)
        topBar = findViewById(R.id.top_bar)
        urlField = findViewById(R.id.url)
        actionWrap = findViewById(R.id.action_wrap)
        actionRow = findViewById(R.id.action_row)
        popupLayer = findViewById(R.id.popup_layer)
        popupView = findViewById(R.id.popup_gecko)
        homeLayer = findViewById(R.id.home_layer)
        saveButton = findViewById(R.id.btn_save)
        home = HomeScreen(
            this,
            findViewById(R.id.home_layer),
            onOpen = { url -> open(url) },
            onSettings = { openSettings() },
            onAddons = { startActivity(Intent(this, AddonsActivity::class.java)) },
        )

        openSession()
        val start = intent?.dataString ?: Prefs.homePage
        if (start != null) session.loadUri(start) else showHome()

        findViewById<View>(R.id.btn_back).setOnClickListener { session.goBack() }
        findViewById<View>(R.id.btn_forward).setOnClickListener { session.goForward() }
        findViewById<View>(R.id.btn_reload).setOnClickListener { session.reload(); hideBar() }
        findViewById<View>(R.id.btn_home).setOnClickListener { goHome() }
        saveButton.setOnClickListener { toggleSaved() }
        findViewById<View>(R.id.btn_addons).setOnClickListener {
            hideBar()
            startActivity(Intent(this, AddonsActivity::class.java))
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener { openSettings() }
        urlField.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                navigate(urlField.text.toString())
                true
            } else {
                false
            }
        }

        Gecko.actionsChanged = { runOnUiThread { rebuildActions() } }
        Gecko.popupHost = { toggle -> showPopup(toggle) }
        Gecko.openTab = { url -> runOnUiThread { open(url) } }
        rebuildActions()
        bumpCursor()
    }

    private fun openSession() {
        val runtime = Gecko.runtime(this)
        appliedDesktopMode = Prefs.desktopMode
        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .userAgentMode(if (appliedDesktopMode) USER_AGENT_DESKTOP else USER_AGENT_MOBILE)
                .viewportMode(if (appliedDesktopMode) VIEWPORT_DESKTOP else VIEWPORT_MOBILE)
                .build(),
        )
        session.navigationDelegate = navigation
        session.contentDelegate = content
        session.progressDelegate = progressDelegate
        session.promptDelegate = PagePrompts(this)
        session.open(runtime)
        geckoView.setSession(session)
        runtime.webExtensionController.setTabActive(session, true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { open(it) }
    }

    override fun onResume() {
        super.onResume()
        Gecko.setForeground(this)
        rebuildActions()
        if (Gecko.purgeHistoryRequested) {
            Gecko.purgeHistoryRequested = false
            session.purgeHistory()
        }
        if (Prefs.desktopMode != appliedDesktopMode) {
            appliedDesktopMode = Prefs.desktopMode
            session.settings.userAgentMode = if (appliedDesktopMode) USER_AGENT_DESKTOP else USER_AGENT_MOBILE
            session.settings.viewportMode = if (appliedDesktopMode) VIEWPORT_DESKTOP else VIEWPORT_MOBILE
            if (currentUrl.isNotEmpty()) session.reload()
        }
        if (homeLayer.visibility == View.VISIBLE) home.refresh()
    }

    // ---- Zero Home ----

    /** Opens [url] in the page view, leaving Zero Home if it is showing. */
    private fun open(url: String) {
        closePopup()
        hideBar()
        hideHome()
        session.loadUri(url)
    }

    private fun goHome() {
        val page = Prefs.homePage
        if (page != null) open(page) else showHome()
    }

    private fun showHome() {
        closePopup()
        hideBar()
        mover.clear()
        handler.removeCallbacks(hideCursor)
        cursor.visibility = View.INVISIBLE
        homeLayer.visibility = View.VISIBLE
        session.setActive(false)
        home.refresh()
    }

    private fun hideHome() {
        if (homeLayer.visibility != View.VISIBLE) return
        homeLayer.visibility = View.GONE
        session.setActive(true)
        geckoView.requestFocus()
        bumpCursor()
    }

    private fun openSettings() {
        hideBar()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** Top bar "Save to Home": pins the current page with a thumbnail, or unpins it. */
    private fun toggleSaved() {
        val url = currentUrl.takeIf { it.startsWith("http") } ?: return
        Prefs.savedTile(url)?.let { saved ->
            Prefs.tiles = Prefs.tiles.filter { it.id != saved.id }
            toast("Removed from Home")
            updateSaveButton()
            return
        }
        val tile = Tile(Prefs.newId(), currentTitle?.takeIf { it.isNotBlank() } ?: Prefs.hostOf(url), url)
        Prefs.tiles = Prefs.tiles + tile
        updateSaveButton()
        toast("Saved to Home")
        geckoView.capturePixels().accept({ bmp -> if (bmp != null) saveThumbnail(tile, bmp) }, { })
    }

    private fun saveThumbnail(tile: Tile, page: android.graphics.Bitmap) {
        Thread {
            // Top of the page at 16:9, scaled to tile size.
            val h = min(page.height, page.width * 9 / 16)
            val crop = android.graphics.Bitmap.createBitmap(page, 0, 0, page.width, h)
            val thumb = android.graphics.Bitmap.createScaledBitmap(crop, THUMB_W, THUMB_W * 9 / 16, true)
            Prefs.thumbFile(tile).outputStream().use {
                thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
            }
        }.start()
    }

    private fun updateSaveButton() {
        val saved = Prefs.savedTile(currentUrl) != null
        saveButton.setText(if (saved) R.string.remove_from_home else R.string.save_to_home)
        saveButton.isEnabled = currentUrl.startsWith("http")
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    override fun onPause() {
        mover.clear()
        super.onPause()
    }

    override fun onDestroy() {
        Gecko.actionsChanged = null
        Gecko.popupHost = null
        Gecko.openTab = null
        closePopup()
        session.close()
        super.onDestroy()
    }

    // ---- Remote control ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (!down) {
                    if (homeLayer.visibility == View.VISIBLE) home.onMenu() else toggleBar()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down) onBackKey()
                return true
            }
        }
        // Top bar and Zero Home use normal focus navigation instead of the cursor.
        if (topBar.visibility == View.VISIBLE || homeLayer.visibility == View.VISIBLE) {
            return super.dispatchKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) mover.set(event.keyCode, down)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.repeatCount == 0) {
                    bumpCursor()
                    cursor.pressedLook = down
                    touch(if (down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP)
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) {
                    val page = target().first.height * 0.8
                    val dir = if (event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -1 else 1
                    targetSession()?.panZoomController?.scrollBy(
                        ScreenLength.zero(),
                        ScreenLength.fromPixels(dir * page),
                    )
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onBackKey() {
        when {
            popupLayer.visibility == View.VISIBLE -> closePopup()
            topBar.visibility == View.VISIBLE -> hideBar()
            fullscreen -> session.exitFullScreen()
            homeLayer.visibility == View.VISIBLE -> confirmExit()
            canGoBack -> session.goBack()
            Prefs.homePage == null -> showHome()
            else -> confirmExit()
        }
    }

    private fun confirmExit() {
        tvDialog(this)
            .setTitle(getString(R.string.app_name))
            .setMessage("Exit ${getString(R.string.app_name)}?")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Stay", null)
            .show()
    }

    /** The GeckoView the cursor acts on: the extension popup when open, else the page. */
    private fun target(): Pair<GeckoView, GeckoSession?> =
        if (popupLayer.visibility == View.VISIBLE) popupView to popupSession else geckoView to session

    private fun targetSession(): GeckoSession? = target().second

    /** Offset of [view] relative to the cursor view, so cursor coordinates map onto it. */
    private fun offsetOf(view: View): Pair<Float, Float> {
        val a = IntArray(2)
        val b = IntArray(2)
        view.getLocationOnScreen(a)
        cursor.getLocationOnScreen(b)
        return (a[0] - b[0]).toFloat() to (a[1] - b[1]).toFloat()
    }

    private fun moveCursor(dx: Float, dy: Float) {
        bumpCursor()
        val view = target().first
        val (ox, oy) = offsetOf(view)
        val maxX = ox + view.width - 1
        val maxY = oy + view.height - 1
        val wantX = cursor.px + dx
        val wantY = cursor.py + dy
        val x = wantX.coerceIn(ox, maxX)
        val y = wantY.coerceIn(oy, maxY)
        cursor.moveTo(x, y)

        // Pushing against an edge scrolls the page instead.
        val overX = wantX - x
        val overY = wantY - y
        if (overX != 0f || overY != 0f) {
            targetSession()?.panZoomController?.scrollBy(
                ScreenLength.fromPixels(overX * EDGE_SCROLL_FACTOR.toDouble()),
                ScreenLength.fromPixels(overY * EDGE_SCROLL_FACTOR.toDouble()),
                PanZoomController.SCROLL_BEHAVIOR_AUTO,
            )
        }
        hover()
    }

    private fun pointerEvent(action: Int, downTime: Long, mouse: Boolean): Pair<GeckoView, MotionEvent> {
        val view = target().first
        val (ox, oy) = offsetOf(view)
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = if (mouse) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = cursor.px - ox
            y = cursor.py - oy
            pressure = 1f
            size = 1f
        })
        val source = if (mouse) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN
        val ev = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, source, 0,
        )
        return view to ev
    }

    private fun touch(action: Int) {
        if (action == MotionEvent.ACTION_DOWN) tapDownTime = SystemClock.uptimeMillis()
        val (view, ev) = pointerEvent(action, tapDownTime, mouse = false)
        if (action == MotionEvent.ACTION_DOWN) view.requestFocus()
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun hover() {
        val now = SystemClock.uptimeMillis()
        val (view, ev) = pointerEvent(MotionEvent.ACTION_HOVER_MOVE, now, mouse = true)
        view.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun bumpCursor() {
        if (topBar.visibility == View.VISIBLE) return
        cursor.visibility = View.VISIBLE
        handler.removeCallbacks(hideCursor)
        val hideAfter = Prefs.cursorHideSeconds
        if (hideAfter > 0) handler.postDelayed(hideCursor, hideAfter * 1000L)
    }

    /** Moves the cursor every frame while D-pad keys are held, speeding up the longer they are. */
    private inner class CursorMover : Choreographer.FrameCallback {
        private var left = false
        private var right = false
        private var up = false
        private var down = false
        private var running = false
        private var startNanos = 0L
        private val density = resources.displayMetrics.density

        fun set(keyCode: Int, pressed: Boolean) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> left = pressed
                KeyEvent.KEYCODE_DPAD_RIGHT -> right = pressed
                KeyEvent.KEYCODE_DPAD_UP -> up = pressed
                KeyEvent.KEYCODE_DPAD_DOWN -> down = pressed
            }
            if (pressed && !running) {
                running = true
                startNanos = System.nanoTime()
                // Immediate small step so a quick tap moves precisely.
                step(BASE_SPEED_DP * density * 2 * Prefs.cursorSpeed.factor)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun clear() {
            left = false; right = false; up = false; down = false
        }

        private fun step(speed: Float) {
            val dx = (if (right) 1 else 0) - (if (left) 1 else 0)
            val dy = (if (down) 1 else 0) - (if (up) 1 else 0)
            if (dx != 0 || dy != 0) moveCursor(dx * speed, dy * speed)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!(left || right || up || down)) {
                running = false
                return
            }
            val held = (frameTimeNanos - startNanos) / 1e9f
            // Short hold delay before continuous motion, then linear acceleration up to a cap.
            if (held > HOLD_DELAY_S) {
                val speed = min(MAX_SPEED_DP, BASE_SPEED_DP + ACCEL_DP * (held - HOLD_DELAY_S))
                step(speed * density * Prefs.cursorSpeed.factor)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ---- Top bar ----

    private fun toggleBar() {
        if (topBar.visibility == View.VISIBLE) hideBar() else showBar()
    }

    private fun showBar() {
        if (popupLayer.visibility == View.VISIBLE) closePopup()
        mover.clear()
        topBar.visibility = View.VISIBLE
        cursor.visibility = View.INVISIBLE
        urlField.setText(currentUrl)
        updateSaveButton()
        urlField.requestFocus()
    }

    private fun hideBar() {
        if (topBar.visibility != View.VISIBLE) return
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(urlField.windowToken, 0)
        topBar.visibility = View.GONE
        geckoView.requestFocus()
        bumpCursor()
    }

    private fun navigate(text: String) {
        Prefs.toUrl(text)?.let(::open)
    }

    private fun rebuildActions() {
        actionRow.removeAllViews()
        for ((ext, action) in Gecko.actions.values) {
            var label = action.title ?: ext.metaData.name ?: ext.id
            action.badgeText?.takeIf { it.isNotBlank() }?.let { label += "  ($it)" }
            val button = tvButton(label) {
                hideBar()
                action.click()
            }
            button.isEnabled = action.enabled != false
            val px = dp(28)
            action.icon?.getBitmap(px)?.accept({ bmp ->
                if (bmp != null) {
                    val d = BitmapDrawable(resources, bmp).apply { setBounds(0, 0, px, px) }
                    button.setCompoundDrawables(d, null, null, null)
                    button.compoundDrawablePadding = dp(10)
                }
            }, { })
            actionRow.addView(button)
        }
        actionWrap.visibility = if (Gecko.actions.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---- Extension popups ----

    private fun showPopup(toggle: Boolean): GeckoResult<GeckoSession>? {
        if (toggle && popupSession != null) {
            closePopup()
            return null
        }
        closePopup()
        hideBar()
        val popup = GeckoSession()
        popup.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) = closePopup()
        }
        popup.promptDelegate = PagePrompts(this)
        popup.open(Gecko.runtime(this))
        popupView.setSession(popup)
        popupSession = popup
        popupLayer.visibility = View.VISIBLE
        popupLayer.post {
            val (ox, oy) = offsetOf(popupView)
            cursor.moveTo(ox + popupView.width / 2f, oy + popupView.height / 3f)
            bumpCursor()
        }
        return GeckoResult.fromValue(popup)
    }

    private fun closePopup() {
        val popup = popupSession ?: return
        popupSession = null
        popupView.releaseSession()
        popup.close()
        popupLayer.visibility = View.GONE
        geckoView.requestFocus()
    }

    // ---- Gecko delegates ----

    private val navigation = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            url?.let {
                if (it != currentUrl) currentTitle = null
                currentUrl = it
                if (!urlField.hasFocus()) urlField.setText(it)
                Prefs.addRecent(currentTitle, it)
                updateSaveButton()
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            this@MainActivity.canGoBack = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            this@MainActivity.canGoForward = canGoForward
        }

        // Single-tab browser: open target=_blank links in place.
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            session.loadUri(uri)
            return null
        }
    }

    private val content = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            currentTitle = title
            if (title != null) Prefs.retitleRecent(currentUrl, title)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            fullscreen = fullScreen
            val h = if (fullScreen) 0 else resources.getDimensionPixelSize(R.dimen.overscan_h)
            val v = if (fullScreen) 0 else resources.getDimensionPixelSize(R.dimen.overscan_v)
            root.setPadding(h, v, h, v)
            if (fullScreen) cursor.visibility = View.INVISIBLE
        }

        override fun onCrash(session: GeckoSession) = restartSession()
        override fun onKill(session: GeckoSession) = restartSession()
    }

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onProgressChange(session: GeckoSession, progress: Int) {
            this@MainActivity.progress.progress = progress
            this@MainActivity.progress.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
        }
    }

    private fun restartSession() {
        geckoView.releaseSession()
        session.close()
        openSession()
        if (currentUrl.isNotEmpty()) session.loadUri(currentUrl)
    }

    companion object {
        private const val USER_AGENT_DESKTOP = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        private const val USER_AGENT_MOBILE = GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        private const val VIEWPORT_DESKTOP = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        private const val VIEWPORT_MOBILE = GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        private const val THUMB_W = 480
        private const val BASE_SPEED_DP = 3f
        private const val ACCEL_DP = 18f
        private const val MAX_SPEED_DP = 24f
        private const val HOLD_DELAY_S = 0.12f
        private const val EDGE_SCROLL_FACTOR = 2.5f
    }
}
