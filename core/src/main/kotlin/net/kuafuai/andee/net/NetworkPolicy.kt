package net.kuafuai.andee.net

import java.net.URI

/**
 * TLS is required for hub transport; app-layer authorization is still pending.
 * A malicious server at a user-configured WSS URL still has device privileges.
 */
object NetworkPolicy {
    fun isSecureHubUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("wss", ignoreCase = true)) return false
        if (uri.host.isNullOrBlank() || uri.isOpaque) return false
        if (uri.rawUserInfo != null || uri.rawFragment != null) return false
        if (uri.port != -1 && uri.port !in 1..65535) return false
        return true
    }
}
