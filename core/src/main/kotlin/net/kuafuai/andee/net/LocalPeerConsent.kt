package net.kuafuai.andee.net

/**
 * Mechanical approval check for a single live localhost WebSocket connection.
 * Only a physical on-device button tap while unlocked/visible can grant it.
 *
 * This is explicit session authorization, NOT cryptographic client identity.
 * Local clients must re-request approval on reconnect; no token or allowlist.
 */
object LocalPeerConsent {
    fun approved(
        how: String,
        button: String?,
        approveLabel: String,
        deviceUnlocked: Boolean,
        uiVisible: Boolean,
    ): Boolean = deviceUnlocked &&
        uiVisible &&
        how == "button" &&
        approveLabel.isNotBlank() &&
        button == approveLabel
}
