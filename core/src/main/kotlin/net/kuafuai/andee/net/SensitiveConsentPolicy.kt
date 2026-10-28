package net.kuafuai.andee.net

/** Only a physical tap on the matching approval button authorizes an action. */
object SensitiveConsentPolicy {
    fun isApproved(button: String?, how: String): Boolean =
        button == "Approve" && how == "button"
}
