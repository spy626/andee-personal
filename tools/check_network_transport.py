#!/usr/bin/env python3
"""Source regression gate for narrowly-scoped WebSocket hardening."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
body = (root / "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt").read_text()
server = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt").read_text()
client = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()

assert 'bindHost: String = "127.0.0.1"' in server, "server default not loopback"
assert 'bindHost = "127.0.0.1"' in body, "service not loopback"
assert "startWsServer()" in body, "local adb/extension entry was removed"
assert server.count("conn.remoteSocketAddress?.address?.isLoopbackAddress != true") >= 4, "missing onOpen/onMessage/debug boundary"
assert "NetworkPolicy.isSecureHubUrl(hc.url)" in body, "hub caller missing TLS check"
assert "NetworkPolicy.isSecureHubUrl(url)" in client, "hub socket missing TLS check"
print("PASS: legacy local integrations retained with loopback bind; remote Hub WSS required")
print("RESIDUAL RISK: local Android apps and remote WSS hub still lack application-level authentication")
