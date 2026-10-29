#!/usr/bin/env python3
"""CI checks that Phase 4 remains opt-in and doesn't expose legacy Hub sockets."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
bridge = (root / "tools/hub_signed_adapter.py").read_text()
client = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()
requirements = (root / "tools/requirements-hub-adapter.txt").read_text()
workflow = (root / ".github/workflows/build.yml").read_text()

assert 'parts.hostname not in {"127.0.0.1", "::1"}' in bridge, "legacy Hub network exposure"
assert "if not hmac.compare_digest(request.path," in bridge, "unauthorized WebSocket handshakes"
assert "HTTPStatus.FORBIDDEN" in bridge, "invalid capability not refused"
assert "ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)" in bridge, "TLS server absent"
assert "read_private_file(tls_key)" in bridge, "TLS key permissions unchecked"
assert "read_access_token(a.token_file)" in bridge, "private random capability missing"
assert "parse_challenge(frame, self.cfg.device_id)" in bridge, "device ID/nonce not checked"
assert "sign_challenge(self.cfg.identity, device_id, nonce)" in bridge, "signed identity missing"
assert "await phone.send(proof)" in bridge, "proof not sent"
assert "await self._relay(phone, legacy)" in bridge, "original Hub relay missing"
assert bridge.index("await phone.send(proof)") < bridge.index("await self._relay(phone, legacy)"), "relay before proof"
assert '"starting hub client → $url' not in client, "secret bearer path logged on Android"
assert "python -m unittest discover" in workflow, "CI integration tests missing"
assert "websockets==16.0" in requirements and "cryptography==46.0.4" in requirements
print("PASS: experimental signed Hub adapter TLS/capability/loopback controls")
print("LIMITATION: mock Hub integration is not real Android or main-phone acceptance")
