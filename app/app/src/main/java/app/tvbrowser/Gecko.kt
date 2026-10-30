package app.tvbrowser

import android.app.Activity
import android.content.Context
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.lang.ref.WeakReference

/** Process-wide Gecko runtime plus the extension state the UI shows. */
object Gecko {
    private var runtime: GeckoRuntime? = null
    private var foreground: WeakReference<Activity>? = null

    /** Latest toolbar action per extension id, shown as buttons in the top bar. */
    val actions = LinkedHashMap<String, Pair<WebExtension, WebExtension.Action>>()
    var actionsChanged: (() -> Unit)? = null

    /** Set by MainActivity: opens (or toggles) an extension popup and returns its session. */
    var popupHost: ((toggle: Boolean) -> GeckoResult<GeckoSession>?)? = null

    /** Set by MainActivity: loads a URL an extension asked to open in a new tab. */
    var openTab: ((String) -> Unit)? = null

    fun runtime(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val settings = GeckoRuntimeSettings.Builder()
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .consoleOutput(false)
            .build()
        val rt = GeckoRuntime.create(context.applicationContext, settings)
        runtime = rt

        val controller = rt.webExtensionController
        controller.enableExtensionProcessSpawning()
        controller.setPromptDelegate(InstallPrompts)
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) = attach(extension)
            override fun onEnabled(extension: WebExtension) = attach(extension)
            override fun onDisabled(extension: WebExtension) = forget(extension)
            override fun onUninstalled(extension: WebExtension) = forget(extension)
        })
        controller.list().accept { list -> list?.filter { !it.isBuiltIn }?.forEach(::attach) }
        return rt
    }

    fun setForeground(activity: Activity?) {
        foreground = activity?.let { WeakReference(it) }
    }

    fun foreground(): Activity? = foreground?.get()?.takeUnless { it.isFinishing }

    private fun attach(ext: WebExtension) {
        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action,
            ) {
                // Per-tab overrides land here too; with one tab the latest one is the right one.
                actions[extension.id] = extension to action
                actionsChanged?.invoke()
            }

            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action) =
                popupHost?.invoke(false)

            override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action) =
                popupHost?.invoke(true)
        })
        ext.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                extension: WebExtension,
                createDetails: WebExtension.CreateTabDetails,
            ): GeckoResult<GeckoSession>? {
                createDetails.url?.let { url -> openTab?.invoke(url) }
                return GeckoResult.fromValue(null)
            }
        })
    }

    private fun forget(ext: WebExtension) {
        actions.remove(ext.id)
        actionsChanged?.invoke()
    }
}
