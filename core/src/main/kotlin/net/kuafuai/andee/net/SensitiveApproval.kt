package net.kuafuai.andee.net

/**
 * Fail-closed mechanical approval decision. A model cannot supply an approval
 * flag; only the local CardUi's real button-click result is accepted. Never
 * accept speech matching, substituted button text or an expired response.
 */
object SensitiveApproval {
    fun userTappedApprove(how: String?, button: String?, approveLabel: String): Boolean =
        approveLabel.isNotBlank() && how == "button" && button == approveLabel
}
