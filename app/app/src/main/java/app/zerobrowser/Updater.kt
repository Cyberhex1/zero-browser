package app.zerobrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from the GitHub releases page: finds the APK for this build (normal or legacy,
 * 32- or 64-bit), downloads it and hands it to Android's installer, which updates the app in place.
 * Only works because every release is signed with the same key.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Cyberhex1/zero-browser/releases/latest"

    /** [apkUrl] is empty when the release has no file for this build. */
    data class Release(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

    /** "0.3.2" for both "0.3.2" and "0.3.2-legacy". */
    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().substringBefore('-')

    /** The release file that matches this build, e.g. ZeroBrowser-legacy-armeabi-v7a.apk. */
    private fun assetName(context: Context): String {
        val abi = if (context.applicationInfo.nativeLibraryDir.orEmpty().contains("arm64")) "arm64-v8a" else "armeabi-v7a"
        return context.getString(R.string.update_asset_prefix) + abi + ".apk"
    }

    /** True if version [a] is newer than [b] (dotted numbers). */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** Network call: the latest release and this build's APK in it, or null if it has none. */
    fun fetchLatest(context: Context): Release? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val version = json.optString("tag_name").removePrefix("v")
        if (version.isEmpty()) return null
        val notes = plainNotes(json.optString("body"))
        val assets = json.optJSONArray("assets")
        val want = assetName(context)
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            if (a.optString("name") == want) {
                return Release(version, a.optString("browser_download_url"), a.optLong("size"), notes)
            }
        }
        return Release(version, "", 0, notes)
    }

    /** Release notes as plain text for a TV dialog: no tables, headings marks or link syntax. */
    private fun plainNotes(markdown: String): String =
        markdown.lines()
            .filterNot { it.trimStart().startsWith("|") }
            .joinToString("\n") { line ->
                line.replace(Regex("^#+\\s*"), "")
                    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                    .replace("**", "").replace("`", "")
                    .replace(Regex("^\\s*[-*]\\s+"), "• ")
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .let { if (it.length > 700) it.take(697) + "…" else it }

    /** Quiet check at most once a day (when enabled); remembers a newer version for Zero Home. */
    fun backgroundCheck(context: Context, onResult: () -> Unit) {
        val now = System.currentTimeMillis()
        if (!Prefs.autoUpdateCheck || now - Prefs.lastUpdateCheck < 24L * 60 * 60 * 1000) return
        Prefs.lastUpdateCheck = now
        Thread {
            try {
                val latest = fetchLatest(context)
                Prefs.availableUpdate = latest?.version
                    ?.takeIf { latest.apkUrl.isNotEmpty() && isNewer(it, installedVersion(context)) }
                onResult()
            } catch (e: Exception) {
                // Offline or rate-limited: try again tomorrow.
            }
        }.start()
    }

    /** A newer version found by a check and not yet installed, else null. */
    fun pendingUpdate(context: Context): String? =
        Prefs.availableUpdate?.takeIf { isNewer(it, installedVersion(context)) }

    /** True when Fire OS / Android lets this app install updates. */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
        }

    /** Deletes downloaded update files; called on launch, after the installer has run. */
    fun cleanup(context: Context) {
        updateDir(context).listFiles()?.forEach { it.delete() }
    }

    /** Network call: downloads the APK, reporting progress 0–100. */
    fun download(context: Context, release: Release, progress: (Int) -> Unit): File {
        val dir = updateDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ZeroBrowser-${release.version}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        progress(pct)
                    }
                }
            }
        }
        return file
    }

    /**
     * Opens Android's installer for [apk]. Returns false (after opening the right settings page)
     * when the app first needs permission to install updates.
     */
    fun install(activity: Activity, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            openInstallPermission(activity)
            return false
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        } else {
            Uri.fromFile(apk)
        }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    /** Android 8+: the system page where the user allows this app to install updates. */
    fun openInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
        }
    }

    /** App-private cache on Android 7+ (shared through the FileProvider); readable storage before that. */
    private fun updateDir(context: Context): File =
        if (Build.VERSION.SDK_INT >= 24) {
            File(context.cacheDir, "updates")
        } else {
            File(context.getExternalFilesDir(null) ?: context.cacheDir, "updates")
        }
}
