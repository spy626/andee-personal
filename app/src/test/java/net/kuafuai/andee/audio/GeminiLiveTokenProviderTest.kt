package net.kuafuai.andee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiLiveTokenProviderTest {
    private val provider = GeminiLiveTokenProvider()

    @Test
    fun rejectsBlankApiKeyBeforeNetworkAccess() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            provider.create("  ")
        }
        assertEquals("Gemini Live API key is missing", error.message)
    }

    @Test
    fun rejectsInvalidModelNameBeforeNetworkAccess() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            provider.create("test-key", "models/gemini-3.8-live?bad=true")
        }
        assertEquals("Invalid Gemini Live model", error.message)
    }
}