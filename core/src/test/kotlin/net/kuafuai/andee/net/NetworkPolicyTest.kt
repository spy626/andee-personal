package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    @Test fun validTlsEndpoints() {
        listOf("wss://example.org/hub", "wss://example.org:443/ws",
            "WSS://example.org/hub", "wss://[::1]:9000/ws")
            .forEach { assertTrue(it, NetworkPolicy.isSecureHubUrl(it)) }
    }

    @Test fun rejectsPlaintextAndOtherSchemes() {
        listOf("ws://192.168.1.2:9100", "http://example.org",
            "https://example.org", "file:///tmp/socket")
            .forEach { assertFalse(it, NetworkPolicy.isSecureHubUrl(it)) }
    }

    @Test fun rejectsMalformedOrMisleadingEndpoints() {
        listOf("", "not-a-url", "wss:///missing-host", "wss://example.org:0/",
            "wss://user:secret@example.org/ws", "wss://example.org/ws#fragment",
            "wss://bad host/ws", "wss://example.org:65536/ws",
            "wss://example.org:bad/ws")
            .forEach { assertFalse(it, NetworkPolicy.isSecureHubUrl(it)) }
    }
}
