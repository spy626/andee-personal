#!/usr/bin/env python3
"""Prevent accidental re-exposure of Andee's unauthenticated legacy driver port."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
body = (root / "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt").read_text()
server = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt").read_text()
client = (root / "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()

assert not re.search(r"(?m)^\s*startWsServer\(\)\s*$", body), "Legacy driver server auto-started"
assert 'bindHost: String = "127.0.0.1"' in server, "Legacy server bind is not loopback"
assert 'bindHost = "127.0.0.1"' in body, "Legacy server instantiation bind is not loopback"
assert server.count("conn.remoteSocketAddress?.address?.isLoopbackAddress != true") >= 2, "Missing socket boundary checks"
assert "NetworkPolicy.isSecureHubUrl(hc.url)" in body, "Hub caller missing WSS policy"
assert "NetworkPolicy.isSecureHubUrl(url)" in client, "Hub client missing WSS policy"
print("PASS: legacy inbound transport disabled and hub WSS safeguards present")
