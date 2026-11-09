package com.github.ahatem.qtranslate.ui.swing.main

import com.github.ahatem.qtranslate.ui.swing.main.input.*
import com.github.ahatem.qtranslate.ui.swing.shared.clipboard.*
import java.awt.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainAutomaticSelectionTest {
    private class Harness(scope: TestScope, pointer: () -> Point? = { Point(50, 50) }, queued: Boolean = false) {
        val backend = FakeGlobalInputBackend()
        val clipboard = RecordingClipboard("original")
        var copies = 0
        var selections = 0
        val deliveries = mutableListOf<() -> Unit>()
        val listener = MainGlobalKeyListener(
            scope, RecordingLogger(), {}, {}, {}, {}, onReplaceWithTranslation = {}, onCycleTargetLanguage = {},
            onSelectionDetected = { _, _ -> selections++ }, backendFactory = { backend },
            pointerLocation = pointer,
            onValidatedSelection = if (queued) { _, _, valid -> deliveries += { if (valid()) selections++ } } else null,
            selectionCaptureFactory = { _, _, simulate, logger ->
                val monitor = FakeChangeMonitor()
                SelectionCapture(clipboard, monitor, {
                    val injected = simulate()
                    if (injected) { monitor.current++; clipboard.simulateExternalCopy("selected") }
                    injected
                }, logger)
            }, isMac = false,
        )
        init {
            listener.updateRuntimeState(InputRuntimeState(selectionCaptureEnabled = true))
            listener.initialize()
            listener.copyInjector = CopyInjector { copies++; true }
        }
        fun key(code: Int, down: Boolean, kind: KeyClass = KeyClass.OTHER, injected: Boolean = false) =
            backend.emit(GlobalInputEvent.Key(kind, code, down, injected, false, timestampMs = 0L,
                selectionKey = WindowsSelectionKeys.classify(code)))
        fun selectAll() {
            key(0xa2, true, KeyClass.CONTROL); key(0x41, true)
            key(0x41, false); key(0xa2, false, KeyClass.CONTROL)
        }
        fun click() {
            backend.emit(GlobalInputEvent.MouseButton(MouseButtonId.LEFT, true, Point(50, 50)))
            backend.emit(GlobalInputEvent.MouseButton(MouseButtonId.LEFT, false, Point(50, 50)))
        }
    }

    @Test fun `double and triple click coalesce while single click does nothing`() = runTest {
        val h = Harness(this)
        h.click(); advanceUntilIdle(); assertEquals(0, h.copies)
        h.click(); h.click(); advanceUntilIdle()
        assertEquals(1, h.copies); assertEquals(1, h.selections)
        assertEquals("original", h.clipboard.text)
        h.listener.shutdown()
    }

    @Test fun `keyboard selection captures but ordinary typing and injection do not`() = runTest {
        val h = Harness(this)
        h.selectAll(); advanceUntilIdle(); assertEquals(1, h.selections)
        h.key(0xa0, true, KeyClass.SHIFT); h.key(0x27, true); h.key(0x27, false)
        h.key(0xa0, false, KeyClass.SHIFT); advanceUntilIdle(); assertEquals(2, h.selections)
        h.key(0x42, true); h.key(0x42, false)
        h.key(0xa2, true, KeyClass.CONTROL, true); h.key(0x41, true, injected = true)
        h.key(0x41, false, injected = true); h.key(0xa2, false, KeyClass.CONTROL, true)
        advanceUntilIdle(); assertEquals(2, h.copies)
        h.listener.shutdown()
    }

    @Test fun `new physical input cancels capture and queued UI delivery`() = runTest {
        val h = Harness(this, queued = true)
        h.selectAll(); h.key(0x42, true); advanceUntilIdle(); assertEquals(0, h.copies)
        h.key(0x42, false); h.selectAll(); advanceUntilIdle(); assertEquals(1, h.deliveries.size)
        h.key(0x43, true); h.deliveries.forEach { it() }; assertEquals(0, h.selections)
        h.listener.shutdown()
    }

    @Test fun `event delayed across disable and reenable cannot resurrect selection`() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val h = Harness(this, pointer = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); Point(50, 50) })
        val emitter = thread { h.selectAll() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            h.listener.updateRuntimeState(InputRuntimeState(selectionCaptureEnabled = false))
            h.listener.updateRuntimeState(InputRuntimeState(selectionCaptureEnabled = true))
        } finally { release.countDown(); emitter.join(5000) }
        assertFalse(emitter.isAlive)
        advanceUntilIdle(); assertEquals(0, h.copies); assertEquals(0, h.selections)
        h.listener.shutdown()
    }
}
