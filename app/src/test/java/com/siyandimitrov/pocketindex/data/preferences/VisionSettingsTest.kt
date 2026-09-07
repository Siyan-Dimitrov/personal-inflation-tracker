package com.siyandimitrov.pocketindex.data.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionSettingsTest {
    @Test
    fun `Claude is the default but sends nothing until a key is entered`() {
        val defaults = VisionSettings()
        assertEquals(VisionProvider.CLAUDE, defaults.provider)
        assertFalse(defaults.isEnabled)
        assertTrue(defaults.copy(claudeApiKey = "sk-ant-test").isEnabled)
        assertEquals(VisionSettings.CLAUDE_MODEL, defaults.activeModel)
    }

    @Test
    fun `Ollama cloud needs its own key, a home server none, and a cleared address is off`() {
        val ollama = VisionSettings(provider = VisionProvider.OLLAMA)
        assertTrue(ollama.isCloud)
        assertFalse(ollama.isEnabled)
        assertTrue(ollama.copy(apiKey = "key").isEnabled)
        assertTrue(ollama.copy(serverUrl = "http://192.168.0.9:11434").isEnabled)
        assertFalse(ollama.copy(serverUrl = "").isEnabled)
        assertEquals(VisionSettings.DEFAULT_MODEL, ollama.activeModel)
        // A Claude key alone does not switch Ollama on.
        assertFalse(ollama.copy(claudeApiKey = "sk-ant-test").isEnabled)
    }
}
