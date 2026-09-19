package com.rbagent.assistant.automation

/** Har Android action ka typed model. */
sealed class RBAction {
    data class OpenApp(val appName: String, val query: String? = null) : RBAction()
    data class PlayYouTube(val query: String) : RBAction()
    data class CallContact(val contactName: String) : RBAction()
    data class DialNumber(val number: String) : RBAction()
    data class SendSms(val recipient: String, val message: String) : RBAction()
    data class SendEmail(val to: String, val subject: String, val body: String) : RBAction()
    data class WebSearch(val query: String) : RBAction()
    data class OpenUrl(val url: String) : RBAction()
    data class SetFlashlight(val on: Boolean) : RBAction()
    data class OpenSettingsPanel(val panel: String) : RBAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "") : RBAction()
    data class SetTimer(val seconds: Int, val label: String = "") : RBAction()
    data object OpenCamera : RBAction()
    data class NavigateTo(val place: String) : RBAction()
    data object CloseAssistant : RBAction()
    data object NoOp : RBAction()

    data class ExecutionResult(
        val success: Boolean,
        val message: String,
        val actionLabel: String
    )
}
