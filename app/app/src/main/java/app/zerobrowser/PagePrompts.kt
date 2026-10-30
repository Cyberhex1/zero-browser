package app.zerobrowser

import android.app.Activity
import android.widget.EditText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BeforeUnloadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt

/** alert / confirm / prompt / <select> from web pages, as TV dialogs. */
class PagePrompts(private val activity: Activity) : GeckoSession.PromptDelegate {

    override fun onAlertPrompt(session: GeckoSession, prompt: AlertPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        tvDialog(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton("OK", null)
            .setOnDismissListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: ButtonPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var response: PromptResponse? = null
        tvDialog(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton("OK") { _, _ -> response = prompt.confirm(ButtonPrompt.Type.POSITIVE) }
            .setNegativeButton("Cancel") { _, _ -> response = prompt.confirm(ButtonPrompt.Type.NEGATIVE) }
            .setOnDismissListener { result.complete(response ?: prompt.dismiss()) }
            .show()
        return result
    }

    override fun onTextPrompt(session: GeckoSession, prompt: TextPrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var response: PromptResponse? = null
        val input = EditText(activity).apply {
            setText(prompt.defaultValue ?: "")
            setSingleLine()
        }
        tvDialog(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> response = prompt.confirm(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { result.complete(response ?: prompt.dismiss()) }
            .show()
        return result
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: ChoicePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        var response: PromptResponse? = null
        val choices = flatten(prompt.choices)
        val labels = choices.map { it.label }.toTypedArray()
        val builder = tvDialog(activity).setTitle(prompt.title ?: prompt.message)
        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            val checked = choices.map { it.selected }.toBooleanArray()
            builder.setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton("OK") { _, _ ->
                    response = prompt.confirm(choices.filterIndexed { i, _ -> checked[i] }.toTypedArray())
                }
                .setNegativeButton("Cancel", null)
        } else {
            builder.setSingleChoiceItems(labels, choices.indexOfFirst { it.selected }) { dialog, which ->
                response = prompt.confirm(choices[which])
                dialog.dismiss()
            }
        }
        builder.setOnDismissListener { result.complete(response ?: prompt.dismiss()) }.show()
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: BeforeUnloadPrompt,
    ): GeckoResult<PromptResponse> = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    private fun flatten(choices: Array<ChoicePrompt.Choice>): List<ChoicePrompt.Choice> =
        choices.flatMap { c ->
            when {
                c.items != null -> flatten(c.items!!)
                c.separator || c.disabled -> emptyList()
                else -> listOf(c)
            }
        }
}
