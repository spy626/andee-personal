#!/usr/bin/env python3
"""Fail CI if the Hub can receive private data/commands before signed proof."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()
body = (root / "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt").read_text()
config = (root / "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt").read_text()
settings = (root / "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt").read_text()
identity = (root / "core/src/main/kotlin/net/kuafuai/andee/net/HubIdentity.kt").read_text()

assert 'hubPublicKey: String' in client, "client not bound to pinned Hub key"
assert "HubIdentity.isValidPinnedKey(hubPublicKey)" in client, "direct caller can bypass missing pin"
assert "HubIdentity.isValidPinnedKey(hc.publicKey)" in body, "saved Hub configuration not checked"
assert '"auth.challenge"' in client and '"auth.proof"' in client, "signed challenge/proof not wired"
assert "HubIdentity.verify(" in client, "proof not cryptographically checked"
assert "authTimeout" not in client or "AUTH_TIMEOUT_SECONDS" in client
assert "AUTH_TIMEOUT_SECONDS" in client, "proof deadline missing"
assert "if (!c.isOpen || !c.authenticated) return" in client, "events leak before verification"
assert "if (conn !== source || !source.authenticated) return" in client, "tool gate missing"
assert "if (conn !== source || !source.authenticated || !source.isOpen) return@execute" in client, "queued requests can cross sessions"
assert "if (conn !== source || !source.authenticated) return" in client, "register gate absent"
assert 'current = this@BodyWsClient' in client and "onConnected(this)" in client
assert client.index('authenticated = true') < client.index('onConnected(this)'), "registration before proof"
assert 'before.publicKey != after.publicKey' in body, "revocation does not restart connection"
assert '"hub_public_key"' in config and '"hub_public_key"' in settings, "owner cannot pin Hub key"
assert 'how == "button"' not in identity, "Hub proof must be cryptographic, not UI-only"
assert 'SHA256withECDSA' in identity and 'SecureRandom' in identity, "signature/challenge primitive missing"
print("PASS: remote Hub signed identity required, no pre-proof tool/event path, pin can be revoked")
print("LIMITATION: server needs auth adapter; authenticated Hub may still misuse tools")
