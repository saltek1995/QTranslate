package com.github.ahatem.qtranslate.ui.swing.main.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardSelectionTrackerTest {
    @Test
    fun `ctrl shift navigation supports both modifier sides and waits for all releases`() {
        for ((shift, control) in listOf(0xa0 to 0xa2, 0xa1 to 0xa3)) {
            val tracker = KeyboardSelectionTracker()
            tracker.onKey(key(control, true, KeyClass.CONTROL))
            tracker.onKey(key(shift, true, KeyClass.SHIFT))
            tracker.onKey(key(0x24, true))
            assertFalse(tracker.onKey(key(0x24, false)))
            assertFalse(tracker.onKey(key(control, false, KeyClass.CONTROL)))
            assertTrue(tracker.onKey(key(shift, false, KeyClass.SHIFT)))
        }
    }

    @Test
    fun `typing after a pending selection cancels and a later selection works`() {
        val tracker = KeyboardSelectionTracker()
        tracker.onKey(key(0xa0, true, KeyClass.SHIFT))
        tracker.onKey(key(0x25, true))
        tracker.onKey(key(0x25, false))
        tracker.onKey(key(0x42, true))
        tracker.onKey(key(0x42, false))
        assertFalse(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
        tracker.onKey(key(0xa0, true, KeyClass.SHIFT))
        tracker.onKey(key(0x25, true))
        tracker.onKey(key(0x25, false))
        assertTrue(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
    }
    private fun key(code: Int, pressed: Boolean, keyClass: KeyClass = KeyClass.OTHER,
                    injected: Boolean = false) = GlobalInputEvent.Key(
        keyClass, code, pressed, injected, repeat = false, timestampMs = 0L,
        selectionKey = WindowsSelectionKeys.classify(code),
    )

    @Test
    fun `shift navigation captures after both selecting key and shift are released`() {
        for (code in 0x21..0x28) {
            val tracker = KeyboardSelectionTracker()
            assertFalse(tracker.onKey(key(0xa0, true, KeyClass.SHIFT)))
            assertFalse(tracker.onKey(key(code, true)))
            assertFalse(tracker.onKey(key(code, true))) // auto-repeat
            assertFalse(tracker.onKey(key(code, false)))
            assertTrue(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
            assertFalse(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
        }
    }

    @Test
    fun `ctrl a captures exactly once regardless of release order`() {
        for (controlFirst in listOf(true, false)) {
            val tracker = KeyboardSelectionTracker()
            tracker.onKey(key(0xa2, true, KeyClass.CONTROL))
            tracker.onKey(key(0x41, true))
            val controlUp = key(0xa2, false, KeyClass.CONTROL)
            val aUp = key(0x41, false)
            assertFalse(tracker.onKey(if (controlFirst) controlUp else aUp))
            assertTrue(tracker.onKey(if (controlFirst) aUp else controlUp))
        }
    }

    @Test
    fun `typing navigation without shift and ctrl c never select`() {
        val tracker = KeyboardSelectionTracker()
        for (code in listOf(0x41, 0x43, 0x25, 0x27)) {
            assertFalse(tracker.onKey(key(code, true)))
            assertFalse(tracker.onKey(key(code, false)))
        }
        tracker.onKey(key(0xa2, true, KeyClass.CONTROL))
        tracker.onKey(key(0x43, true))
        assertFalse(tracker.onKey(key(0x43, false)))
        assertFalse(tracker.onKey(key(0xa2, false, KeyClass.CONTROL)))
    }

    @Test
    fun `alt injection reset and typing cancel selection`() {
        val tracker = KeyboardSelectionTracker()
        tracker.onKey(key(0xa0, true, KeyClass.SHIFT))
        tracker.onKey(key(0x25, true))
        tracker.onKey(key(0xa4, true, KeyClass.ALT))
        tracker.onKey(key(0x25, false))
        tracker.onKey(key(0xa4, false, KeyClass.ALT))
        assertFalse(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
        tracker.onKey(key(0xa2, true, KeyClass.CONTROL, injected = true))
        tracker.onKey(key(0x41, true, injected = true))
        tracker.onKey(key(0x41, false, injected = true))
        assertFalse(tracker.onKey(key(0xa2, false, KeyClass.CONTROL, injected = true)))
        tracker.onKey(key(0xa0, true, KeyClass.SHIFT))
        tracker.onKey(key(0x25, true))
        tracker.reset()
        assertFalse(tracker.onKey(key(0x25, false)))
        assertFalse(tracker.onKey(key(0xa0, false, KeyClass.SHIFT)))
    }
}
