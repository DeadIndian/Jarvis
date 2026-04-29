package com.jarvis.app

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmBackendModeTest {
    @Test
    fun legacyLocalOnlyMapsToLitert() {
        val mode = LlmBackendMode.fromLegacyConfig("local-only")

        assertEquals(LlmBackendMode.JARVIS_LITERT, mode)
    }

    @Test
    fun legacyGeminiMapsToCloudOnly() {
        val mode = LlmBackendMode.fromLegacyConfig("gemini")

        assertEquals(LlmBackendMode.GEMINI_CLOUD, mode)
    }

    @Test
    fun idParsingHandlesKnownModes() {
        val mode = LlmBackendMode.fromId("google-aicore")

        assertEquals(LlmBackendMode.GOOGLE_AICORE, mode)
    }

    @Test
    fun configParsingSupportsLegacyAndNewValues() {
        val legacyMode = LlmBackendMode.fromConfig("cloud-only")
        val newMode = LlmBackendMode.fromConfig("google-aicore")

        assertEquals(LlmBackendMode.GEMINI_CLOUD, legacyMode)
        assertEquals(LlmBackendMode.GOOGLE_AICORE, newMode)
    }

    @Test
    fun legacyHybridMapsToHybridMode() {
        val mode = LlmBackendMode.fromLegacyConfig("hybrid")

        assertEquals(LlmBackendMode.HYBRID, mode)
    }
}
