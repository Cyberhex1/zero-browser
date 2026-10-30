package app.zerobrowser

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.concurrent.Executors

/** Installed extensions (on/off/remove) and one-press installs of popular add-ons from AMO. */
class AddonsActivity : Activity() {

    private data class AmoAddon(
        val guid: String,
        val name: String,
        val summary: String,
        val users: Long,
        val xpiUrl: String,
    )

    private lateinit var installedBox: LinearLayout
    private lateinit var popularBox: LinearLayout
    private lateinit var status: TextView

    private val controller by lazy { Gecko.runtime(this).webExtensionController }
    private val io = Executors.newSingleThreadExecutor()
    private var installed: List<WebExtension> = emptyList()
    private var popular: List<AmoAddon> = listOf(UBLOCK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.overscan_h),
                resources.getDimensionPixelSize(R.dimen.overscan_v),
                resources.getDimensionPixelSize(R.dimen.overscan_h),
                resources.getDimensionPixelSize(R.dimen.overscan_v),
            )
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(tvButton("Back") { finish() })
        header.addView(tvText("  Add-ons", 30f, bold = true))
        content.addView(header)

        content.addView(section("Installed"))
        installedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(installedBox)

        content.addView(section("Popular add-ons (Firefox for Android)"))
        status = tvText("Loading the list…", 18f).apply { setTextColor(Color.LTGRAY) }
        content.addView(status)
        popularBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(popularBox)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 19, 22))
            isFillViewport = true
            addView(content)
        })

        renderPopular()
        refreshInstalled()
        loadPopular()
    }

    override fun onResume() {
        super.onResume()
        Gecko.setForeground(this)
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun section(title: String) = tvText(title, 24f, bold = true).apply {
        setPadding(dp(6), dp(24), dp(6), dp(8))
        setTextColor(Color.rgb(255, 213, 79))
    }

    private fun row(text: CharSequence, vararg buttons: Button): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(
                tvText(text, 18f),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            buttons.forEach { addView(it) }
        }

    // ---- Installed ----

    private fun refreshInstalled() {
        controller.list().accept({ list ->
            installed = list.orEmpty().filter { !it.isBuiltIn }
            renderInstalled()
            renderPopular()
        }, { e -> toast("Couldn't read add-ons: ${e?.message}") })
    }

    private fun renderInstalled() {
        installedBox.removeAllViews()
        if (installed.isEmpty()) {
            installedBox.addView(tvText("No add-ons yet. Pick one below.", 18f).apply {
                setPadding(dp(6), 0, 0, 0)
                setTextColor(Color.LTGRAY)
            })
            return
        }
        for (ext in installed) {
            val meta = ext.metaData
            val on = meta.enabled
            val label = "${meta.name ?: ext.id}   v${meta.version}" + if (on) "" else "   (off)"
            val buttons = mutableListOf<Button>()
            buttons += tvButton(if (on) "Turn off" else "Turn on") {
                val r = if (on) {
                    controller.disable(ext, WebExtensionController.EnableSource.USER)
                } else {
                    controller.enable(ext, WebExtensionController.EnableSource.USER)
                }
                r.accept({ refreshInstalled() }, { e -> toast("Failed: ${e?.message}") })
            }
            meta.optionsPageUrl?.let { url ->
                buttons += tvButton("Settings") {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .setData(Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            }
            buttons += tvButton("Remove") {
                tvDialog(this)
                    .setTitle("Remove ${meta.name ?: ext.id}?")
                    .setPositiveButton("Remove") { _, _ ->
                        controller.uninstall(ext).accept({ refreshInstalled() }, { e -> toast("Failed: ${e?.message}") })
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            installedBox.addView(row(label, *buttons.toTypedArray()))
        }
    }

    // ---- Popular (AMO) ----

    private fun loadPopular() {
        io.execute {
            try {
                val conn = URL(AMO_SEARCH).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).getJSONArray("results")
                val list = mutableListOf(UBLOCK)
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    val guid = o.optString("guid")
                    val xpi = o.optJSONObject("current_version")?.optJSONObject("file")?.optString("url").orEmpty()
                    if (guid.isEmpty() || xpi.isEmpty() || guid == UBLOCK.guid) continue
                    list += AmoAddon(
                        guid = guid,
                        name = localized(o, "name").ifEmpty { guid },
                        summary = localized(o, "summary"),
                        users = o.optLong("average_daily_users"),
                        xpiUrl = xpi,
                    )
                }
                runOnUiThread {
                    popular = list
                    status.visibility = View.GONE
                    renderPopular()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Couldn't load the add-on list (${e.message}). uBlock Origin is still available." }
            }
        }
    }

    /** AMO translated fields are either a plain string or a {locale: text} object. */
    private fun localized(o: JSONObject, key: String): String {
        val v = o.opt(key)
        if (v is String) return v
        if (v is JSONObject) {
            v.optString("en-US").takeIf { it.isNotEmpty() }?.let { return it }
            v.keys().asSequence().firstOrNull()?.let { return v.optString(it) }
        }
        return ""
    }

    private fun renderPopular() {
        popularBox.removeAllViews()
        val have = installed.map { it.id }.toSet()
        val fmt = NumberFormat.getIntegerInstance()
        for (addon in popular) {
            val summary = addon.summary.let { if (it.length > 140) it.take(137) + "…" else it }
            val users = if (addon.users > 0) "\n${fmt.format(addon.users)} users" else ""
            val text = "${addon.name}\n$summary$users"
            val button = if (addon.guid in have) {
                tvButton("Installed") { }.apply { isEnabled = false }
            } else {
                tvButton("Install") { }.also { b -> b.setOnClickListener { install(addon, b) } }
            }
            popularBox.addView(row(text, button))
        }
    }

    private fun install(addon: AmoAddon, button: Button) {
        button.isEnabled = false
        button.text = "Installing…"
        controller.install(addon.xpiUrl).accept({ ext ->
            toast("${ext?.metaData?.name ?: addon.name} added")
            refreshInstalled()
        }, { e ->
            button.isEnabled = true
            button.text = "Install"
            val code = (e as? WebExtension.InstallException)?.code
            if (code != WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED) {
                toast("Couldn't install ${addon.name}: ${e?.message ?: code}")
            }
        })
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val AMO_SEARCH =
            "https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&sort=users&page_size=40&lang=en-US"

        private val UBLOCK = AmoAddon(
            guid = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            summary = "Recommended. An efficient wide-spectrum content blocker: blocks ads, trackers and malware sites.",
            users = 0,
            xpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
        )
    }
}
