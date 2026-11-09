package com.github.ahatem.qtranslate.ui.swing.shared.clipboard

import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AwtClipboardSignatureTest {
    @Test
    fun `transient text failure cannot manufacture a content change`() {
        var failing = false
        val clipboard = Clipboard("test")
        clipboard.setContents(object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.stringFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.stringFlavor
            override fun getTransferData(flavor: DataFlavor): Any {
                if (failing) throw IOException("synthetic failure")
                return "unchanged private sentinel"
            }
        }, null)
        val system = AwtSystemClipboard { clipboard }
        val monitor = SignatureClipboardChangeMonitor(system)
        val baseline = assertNotNull(monitor.mark())
        failing = true
        assertNull(monitor.mark())
        assertFalse(monitor.hasChangedSince(baseline))
        failing = false
        assertFalse(monitor.hasChangedSince(baseline))
    }
}
