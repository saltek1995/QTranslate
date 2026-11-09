package com.github.ahatem.qtranslate.ui.swing.main

import com.github.ahatem.qtranslate.ui.swing.shared.clipboard.RecordingLogger
import java.awt.AWTException
import java.awt.event.MouseAdapter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitialWindowVisibilityTest {
    @Test fun `failed tray installation keeps a hidden-start window reachable`() {
        val tray = TrayIconBinding(RecordingLogger(), installer = { _, _ -> throw AWTException("synthetic tray failure") })
        tray.install(emptyList(), object : MouseAdapter() {})
        assertFalse(tray.isInstalled)
        var visible = false
        presentInitialWindow(initiallyHidden = true, trayInstalled = tray.isInstalled) { visible = true }
        assertTrue(visible)
    }

    @Test fun `hidden initialization never undoes an earlier explicit restore`() {
        var visible = true // a second launch restored the frame before deferred initialization
        presentInitialWindow(initiallyHidden = true, trayInstalled = true) { visible = true }
        assertTrue(visible)
        visible = false
        presentInitialWindow(initiallyHidden = true, trayInstalled = true) { visible = true }
        assertFalse(visible)
    }
}
