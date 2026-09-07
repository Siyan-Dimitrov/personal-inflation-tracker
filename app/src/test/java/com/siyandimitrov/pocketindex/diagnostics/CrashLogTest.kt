package com.siyandimitrov.pocketindex.diagnostics

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogTest {
    private val file = File.createTempFile("crash", ".log").apply { delete() }

    @Test
    fun `entries accumulate and are counted`() {
        CrashLog.append(file, "=== first\ntrace\n")
        CrashLog.append(file, "=== second\ntrace\n")

        val text = file.readText()
        assertEquals("=== first\ntrace\n=== second\ntrace\n", text)
        assertEquals(2, CrashLog.countEntries(text))
    }

    @Test
    fun `oldest whole entries are dropped once the file is too large`() {
        val big = "=== old\n" + "x".repeat(CrashLog.MAX_BYTES) + "\n"
        CrashLog.append(file, big)
        CrashLog.append(file, "=== new\ntrace\n")

        val text = file.readText()
        assertTrue(text.length <= CrashLog.MAX_BYTES)
        assertTrue(text.startsWith("=== new\n"))
        assertEquals(1, CrashLog.countEntries(text))
    }
}
