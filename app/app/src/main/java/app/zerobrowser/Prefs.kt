package app.zerobrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/** A page pinned to the home screen. The thumbnail, if any, is thumbs/<id>.png in app storage. */
data class Tile(val id: String, val title: String, val url: String)

/** User settings and home-screen tiles, kept in SharedPreferences. */
object Prefs {
    private lateinit var prefs: SharedPreferences
    private lateinit var thumbDir: File

    /** Under ~3 GB RAM (every Fire TV stick): lighter defaults. */
    var lowMemoryDevice = false
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        lowMemoryDevice = am.isLowRamDevice || info.totalMem < 3L * 1024 * 1024 * 1024
        prefs = context.applicationContext.getSharedPreferences("zero", Context.MODE_PRIVATE)
        thumbDir = File(context.applicationContext.filesDir, "thumbs").apply { mkdirs() }
    }

    // ---- Settings ----

    enum class Engine(val label: String, val searchUrl: String) {
        GOOGLE("Google", "https://www.google.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        BING("Bing", "https://www.bing.com/search?q="),
    }

    var engine: Engine
        get() = Engine.entries.firstOrNull { it.name == prefs.getString("engine", null) } ?: Engine.GOOGLE
        set(value) = prefs.edit().putString("engine", value.name).apply()

    fun searchUrl(query: String): String = engine.searchUrl + URLEncoder.encode(query, "UTF-8")

    /** Turns address-bar text into a URL: web addresses as-is, anything else a search. */
    fun toUrl(input: String): String? {
        val text = input.trim()
        return when {
            text.isEmpty() -> null
            text.contains("://") || text.startsWith("about:") -> text
            !text.contains(' ') && text.contains('.') -> "https://$text"
            else -> searchUrl(text)
        }
    }

    /**
     * Desktop layouts suit a big screen but are much heavier, so low-memory devices
     * default to mobile sites. Either can be chosen in Settings.
     */
    var desktopMode: Boolean
        get() = prefs.getBoolean("desktop", !lowMemoryDevice)
        set(value) = prefs.edit().putBoolean("desktop", value).apply()

    enum class CursorSpeed(val label: String, val factor: Float) {
        SLOW("Slow", 0.6f),
        NORMAL("Normal", 1f),
        FAST("Fast", 1.6f),
    }

    var cursorSpeed: CursorSpeed
        get() = CursorSpeed.entries.firstOrNull { it.name == prefs.getString("cursor", null) } ?: CursorSpeed.NORMAL
        set(value) = prefs.edit().putString("cursor", value.name).apply()

    /** Check GitHub for a newer release once a day on launch. */
    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(value) = prefs.edit().putBoolean("autoUpdate", value).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    /** Newest version seen by a check (shown on Zero Home while newer than the installed one). */
    var availableUpdate: String?
        get() = prefs.getString("availableUpdate", null)
        set(value) = prefs.edit().putString("availableUpdate", value).apply()

    var adBlockOffered: Boolean
        get() = prefs.getBoolean("adBlockOffered", false)
        set(value) = prefs.edit().putBoolean("adBlockOffered", value).apply()

    /** Black border around web pages, for TVs that cut off the picture edges (overscan). */
    enum class ScreenBorder(val label: String, val fraction: Float) {
        OFF("Off", 0f),
        SMALL("Small", 0.5f),
        LARGE("Large", 1f),
    }

    var screenBorder: ScreenBorder
        get() = ScreenBorder.entries.firstOrNull { it.name == prefs.getString("border", null) } ?: ScreenBorder.OFF
        set(value) = prefs.edit().putString("border", value.name).apply()

    /** Seconds without input before the cursor hides; 0 = never. */
    var cursorHideSeconds: Int
        get() = prefs.getInt("cursorHide", 6)
        set(value) = prefs.edit().putInt("cursorHide", value).apply()

    /** Page opened on launch and by Home: null means the Zero Home screen. */
    var homePage: String?
        get() = prefs.getString("homePage", null)
        set(value) = prefs.edit().putString("homePage", value).apply()

    // ---- Recently visited ----

    private const val MAX_RECENTS = 10

    var recents: List<Tile>
        get() = readTiles("recents") ?: emptyList()
        private set(value) = writeTiles("recents", value)

    fun addRecent(title: String?, url: String) {
        if (!url.startsWith("http")) return
        val label = title?.takeIf { it.isNotBlank() } ?: hostOf(url)
        recents = (listOf(Tile(newId(), label, url)) + recents.filter { it.url != url }).take(MAX_RECENTS)
    }

    /** Updates the title of the newest recent entry once the page reports it. */
    fun retitleRecent(url: String, title: String) {
        if (title.isBlank()) return
        val list = recents
        if (list.firstOrNull()?.url == url) recents = listOf(list[0].copy(title = title)) + list.drop(1)
    }

    fun clearRecents() {
        recents = emptyList()
    }

    fun hostOf(url: String): String =
        android.net.Uri.parse(url).host?.removePrefix("www.") ?: url

    // ---- Home tiles ----

    private val DEFAULT_TILES = listOf(
        "Google" to "https://www.google.com",
        "YouTube" to "https://www.youtube.com",
        "Wikipedia" to "https://www.wikipedia.org",
        "Reddit" to "https://www.reddit.com",
        "BBC News" to "https://www.bbc.com/news",
        "Weather" to "https://weather.com",
    )

    /** Saved pages, in home-screen order. Starts with a few defaults the user can remove. */
    var tiles: List<Tile>
        get() = readTiles("tiles") ?: defaultTiles()
        set(value) {
            writeTiles("tiles", value)
            // Drop thumbnails of removed tiles.
            val keep = value.map { "${it.id}.png" }.toSet()
            thumbDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }

    fun savedTile(url: String): Tile? = tiles.firstOrNull { sameUrl(it.url, url) }

    private fun sameUrl(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')

    private fun defaultTiles() = DEFAULT_TILES.map { (title, url) -> Tile(newId(), title, url) }

    fun restoreDefaultTiles() {
        tiles = defaultTiles()
    }

    fun clearSaved() {
        tiles = emptyList()
    }

    private fun readTiles(key: String): List<Tile>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Tile(o.getString("id"), o.getString("title"), o.getString("url"))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeTiles(key: String, value: List<Tile>) {
        val arr = JSONArray()
        value.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun thumbFile(tile: Tile) = File(thumbDir, "${tile.id}.png")
}
