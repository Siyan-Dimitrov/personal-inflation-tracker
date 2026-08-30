package com.siyandimitrov.pocketindex.data.preferences

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionSettingsTest {
    @Test
    fun `the hosted service is the default but sends nothing until a key is entered`() {
        val defaults = VisionSettings()
        assertTrue(defaults.isCloud)
        assertFalse(defaults.isEnabled)
        assertTrue(defaults.copy(apiKey = "sk-test").isEnabled)
    }

    @Test
    fun `a home server needs no key and a cleared address is off`() {
        assertTrue(VisionSettings(serverUrl = "http://192.168.0.9:11434").isEnabled)
        assertFalse(VisionSettings(serverUrl = "").isEnabled)
    }
}
