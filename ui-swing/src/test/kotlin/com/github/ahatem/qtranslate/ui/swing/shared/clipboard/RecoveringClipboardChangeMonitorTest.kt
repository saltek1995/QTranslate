package com.github.ahatem.qtranslate.ui.swing.shared.clipboard

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveringClipboardChangeMonitorTest {
    @Test
    fun `native failure during capture fails closed and next capture uses fallback`() {
        val native = SwitchableMonitor(10L)
        val fallback = FakeChangeMonitor(20L)
        val monitor = RecoveringClipboardChangeMonitor(native, fallback, RecordingLogger())
        val token = assertNotNull(monitor.mark())
        native.value = null
        fallback.current++
        assertFalse(monitor.hasChangedSince(token))
        val next = assertNotNull(monitor.mark())
        assertFalse(monitor.hasChangedSince(next))
        fallback.current++
        assertTrue(monitor.hasChangedSince(next))
    }
    private class SwitchableMonitor(var value: Long? = null) : ClipboardChangeMonitor {
        override fun mark(): Long? = value
        override fun hasChangedSince(token: Long): Boolean = value?.let { it != token } ?: false
    }

    @Test
    fun `native recovery cannot switch observer in the middle of a fallback capture`() {
        val native = SwitchableMonitor()
        val fallback = FakeChangeMonitor(10)
        val monitor = RecoveringClipboardChangeMonitor(native, fallback, RecordingLogger())
        val token = assertNotNull(monitor.mark())
        native.value = 99L
        assertFalse(monitor.hasChangedSince(token))
        fallback.current++
        assertTrue(monitor.hasChangedSince(token))
        val recovered = assertNotNull(monitor.mark())
        assertFalse(monitor.hasChangedSince(recovered))
        assertFalse(monitor.hasChangedSince(token)) // superseded token
        native.value = 100L
        assertTrue(monitor.hasChangedSince(recovered))
    }

    @Test
    fun `neither observer available fails closed and recovers on the next mark`() {
        val native = SwitchableMonitor()
        val fallback = SwitchableMonitor()
        val monitor = RecoveringClipboardChangeMonitor(native, fallback, RecordingLogger())
        assertNull(monitor.mark())
        assertFalse(monitor.hasChangedSince(1L))
        fallback.value = 0L // empty clipboard signature is valid
        assertNotNull(monitor.mark())
    }

    @Test
    fun `first capture after login needs no manual clipboard initialization`() = runTest {
        val clipboard = RecordingClipboard().apply { signatureProvider = { text?.hashCode()?.toLong() ?: 0L } }
        val monitor = RecoveringClipboardChangeMonitor(NullChangeMonitor(),
            SignatureClipboardChangeMonitor(clipboard), RecordingLogger())
        val capture = SelectionCapture(clipboard, monitor, {
            clipboard.simulateExternalCopy("selected word")
            true
        }, RecordingLogger())
        var result: CaptureResult? = null
        capture.capture { result = it }
        assertEquals("selected word", assertIs<CaptureResult.Success>(result).text)
        assertNull(clipboard.text)
        assertEquals(1, clipboard.restoreCount)
    }

    @Test
    fun `fallback never treats unchanged preexisting text as a new selection`() = runTest {
        val clipboard = RecordingClipboard("stale text").apply { signatureProvider = { 10L } }
        val monitor = RecoveringClipboardChangeMonitor(NullChangeMonitor(),
            SignatureClipboardChangeMonitor(clipboard), RecordingLogger())
        val capture = SelectionCapture(clipboard, monitor, { true }, RecordingLogger())
        var result: CaptureResult? = null
        capture.capture { result = it }
        assertEquals(CaptureFailure.COPY_UNCONFIRMED, assertIs<CaptureResult.Failed>(result).reason)
        assertEquals("stale text", clipboard.text)
    }

    @Test
    fun `native diagnostics log reason once and exclude exception text`() {
        val logger = RecordingLogger()
        val native = object : WindowsClipboardChangeMonitor.User32 {
            override fun GetClipboardSequenceNumber(): Int = throw IllegalStateException("PRIVATE_SENTINEL")
        }
        val monitor = WindowsClipboardChangeMonitor(native, logger)
        assertNull(monitor.mark())
        assertNull(monitor.mark())
        assertEquals(1, logger.warns.size)
        assertTrue(logger.warns.single().contains("IllegalStateException"))
        assertFalse(logger.warns.single().contains("PRIVATE_SENTINEL"))
    }
}
