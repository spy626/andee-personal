#!/usr/bin/env python3
"""Guard network owner-consent boundary; do not confuse with E2E Android tests."""
from pathlib import Path
r = Path(__file__).resolve().parents[1]
s = (r / "app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt").read_text()
b = (r / "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt").read_text()
p = (r / "core/src/main/kotlin/net/kuafuai/andee/net/LocalPeerConsent.kt").read_text()

assert 'bindHost: String = "127.0.0.1"' in s, "LAN bind must remain closed"
assert 'requestPeerConsent: ((Boolean) -> Unit) -> Unit = { done -> done(false) }' in s, "default grant is forbidden"
assert "authorized.add(conn)" in s, "no per-socket authorization state"
assert "if (!authorized.contains(conn))" in s, "dispatch must deny unapproved peers"
assert "private fun dispatchFrame(" in s, "frame routing missing"
assert s.index("if (!authorized.contains(conn))") < s.index("dispatchFrame(conn, message)"), "dispatch before authentication"
assert "active.add(conn)" in s and "awaiting[conn] = AwaitingPeer()" in s, "passive viewers must require approval"
assert "awaiting.remove(conn)" in s and "authorized.remove(conn)" in s, "disconnect must revoke approval"
assert 'if (registry.connection() === conn) handleExtensionResponse(msg)' in s, "extension responses lack sender binding"
assert "MAX_PENDING_FRAMES" in s and "MAX_PENDING_FRAME_CHARS" in s, "untrusted queue unbounded"
assert 'nextPromptAllowedAtMs' in s and 'consentInProgress' in s, "consent prompt rate limit missing"
assert "requestPeerConsent = { done ->" in b, "service callback not wired"
assert "CardUi.isCardUp()" in b and "dispatcher.isTaskActive()" in b, "consent could interrupt existing task/cards"
assert "result.how, result.button, approve" in b, "result must bind to physical approval label"
assert 'how == "button"' in p and 'deviceUnlocked' in p and 'uiVisible' in p, "must require physical unlocked UI"
assert 'captureSpeech = false' in b, "voice approval not allowed"
print("PASS: socket-bound owner consent and fail-closed per-connection local controls")
print("LIMITATION: manual local approval is not cryptographic pairing; on-device behavior untested")
