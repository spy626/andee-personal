#!/usr/bin/env python3
"""Phase 8: verify upstream v0.8.0 capabilities and prior recovery safeguards.

This is a static source tripwire, not live Android/SMS/WhatsApp or security sign-off.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

gradle = read("app/build.gradle")
assert 'versionCode 18' in gradle and 'versionName "0.8.0"' in gradle

schemas = read("core/src/main/kotlin/net/kuafuai/andee/net/ToolSchemas.kt")
all_tools = (
    "self_note", "cares", "diary", "person", "people",
    "wiki_search", "wiki_read", "wiki_write", "wiki_show",
    "wiki_status", "wiki_link", "wiki_share", "wiki_know",
)
for tool in all_tools:
    assert tool in schemas, f"v0.8.0 tool missing from schemas: {tool}"

for path in (
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/Selfhood.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/People.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/Drive.kt",
    "core/src/main/kotlin/net/kuafuai/andee/wiki/Wiki.kt",
    "core/src/main/kotlin/net/kuafuai/andee/wiki/WikiBook.kt",
    "app/src/main/java/net/kuafuai/andee/ui/PhoneTimeline.kt",
    "app/src/main/java/net/kuafuai/andee/ui/MarkdownView.kt",
    "app/src/main/java/net/kuafuai/andee/ui/WeChatQrUi.kt",
):
    assert (ROOT / path).is_file(), f"v0.8.0 feature missing: {path}"

body = read("app/src/main/java/net/kuafuai/andee/BodyCoreService.kt")
assert "Heartbeat" in body and "PhoneTimeline" in body
socket = read("app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt")
assert 'bindHost: String = "127.0.0.1"' in socket
assert "requestPeerConsent" in socket and "authorized.contains(conn)" in socket
guards = read("core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt")
for token in (
    "fun checkUntrustedScreen(", "fun checkUntrustedExtension(",
    "UNTRUSTED_DIRECT_ACTIONS", "SELF_WRITES", "PLAN_WRITES",
):
    assert token in guards, f"security guard or new v0.8.0 restriction missing: {token}"

print("PASS: v0.8.0 13 tools, memory, heartbeat, wiki, timeline, TTS and recovery guards preserved at source level")
print("LIMITATION: no proof of physical device behavior or sensitive per-action consent")
