package app.tvbrowser

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtension.PermissionPromptResponse
import org.mozilla.geckoview.WebExtensionController

/** Extension install / permission prompts, shown as TV dialogs on the foreground activity. */
object InstallPrompts : WebExtensionController.PromptDelegate {

    override fun onInstallPromptRequest(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
    ): GeckoResult<PermissionPromptResponse>? {
        val result = GeckoResult<PermissionPromptResponse>()
        val name = extension.metaData.name ?: extension.id
        ask("Add $name?", "Add", describe(permissions, origins)) { ok ->
            result.complete(PermissionPromptResponse(ok, false, false))
        }
        return result
    }

    override fun onUpdatePrompt(
        extension: WebExtension,
        newPermissions: Array<String>,
        newOrigins: Array<String>,
        newDataCollectionPermissions: Array<String>,
    ): GeckoResult<AllowOrDeny>? = askAllow(
        "${extension.metaData.name ?: extension.id} wants new permissions",
        describe(newPermissions, newOrigins),
    )

    override fun onOptionalPrompt(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
    ): GeckoResult<AllowOrDeny>? = askAllow(
        "${extension.metaData.name ?: extension.id} asks for more access",
        describe(permissions, origins),
    )

    private fun askAllow(title: String, message: String): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        ask(title, "Allow", message) { ok -> result.complete(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
        return result
    }

    private fun ask(title: String, yes: String, message: String, done: (Boolean) -> Unit) {
        val activity = Gecko.foreground() ?: return done(false)
        activity.runOnUiThread {
            var answered = false
            val finish = { ok: Boolean -> if (!answered) { answered = true; done(ok) } }
            tvDialog(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes) { _, _ -> finish(true) }
                .setNegativeButton("Cancel") { _, _ -> finish(false) }
                .setOnDismissListener { finish(false) }
                .show()
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                ?.requestFocus()
        }
    }

    private fun describe(permissions: Array<String>, origins: Array<String>): String {
        val lines = LinkedHashSet<String>()
        for (p in permissions) PERMISSION_TEXT[p]?.let(lines::add)
        for (o in origins) {
            lines += if (o == "<all_urls>" || o.startsWith("*://*/")) {
                "Read and change your data on all websites"
            } else {
                "Read and change your data on $o"
            }
        }
        return if (lines.isEmpty()) {
            "It needs no special permissions."
        } else {
            "It will be able to:\n\n" + lines.joinToString("\n") { "• $it" }
        }
    }

    private val PERMISSION_TEXT = mapOf(
        "<all_urls>" to "Read and change your data on all websites",
        "tabs" to "Access browser tabs",
        "history" to "Access browsing history",
        "bookmarks" to "Read and change bookmarks",
        "downloads" to "Download files",
        "cookies" to "Read and change cookies",
        "clipboardRead" to "Read the clipboard",
        "clipboardWrite" to "Write to the clipboard",
        "geolocation" to "Access your location",
        "notifications" to "Show notifications",
        "privacy" to "Change privacy settings",
        "proxy" to "Control proxy settings",
        "browsingData" to "Clear browsing data",
        "webNavigation" to "Access browser activity during navigation",
        "nativeMessaging" to "Talk to other apps",
    )
}
