package com.github.ahatem.qtranslate.ui.swing.main

import com.github.ahatem.qtranslate.api.core.Logger
import java.awt.AWTException
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseListener
import java.awt.image.BaseMultiResolutionImage

/** Owns the native tray icon for the lifetime of the main window. */
internal class TrayIconBinding(
    private val logger: Logger,
    private val installer: ((List<Image>, MouseListener) -> TrayIcon?)? = null,
) {
    private var icon: TrayIcon? = null
    val isInstalled: Boolean get() = icon != null

    fun install(images: List<Image>, listener: MouseListener) {
        icon = try {
            if (installer != null) installer.invoke(images, listener) else installNative(images, listener)
        } catch (error: AWTException) {
            logger.error("Failed to add tray icon", error)
            null
        }
    }

    private fun installNative(images: List<Image>, listener: MouseListener): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        val tray = SystemTray.getSystemTray()
        if (images.isEmpty()) {
            logger.error("Failed to load any tray icons")
            return null
        }
        val candidate = TrayIcon(BaseMultiResolutionImage(*images.toTypedArray()), "QTranslate").apply {
            isImageAutoSize = true
            toolTip = "QTranslate"
            addMouseListener(listener)
        }
        tray.add(candidate)
        return candidate
    }

    fun dispose() {
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }
}

/** A hidden-start preference must not undo an explicit restore or strand a process without a tray. */
internal fun presentInitialWindow(initiallyHidden: Boolean, trayInstalled: Boolean, show: () -> Unit) {
    if (!initiallyHidden || !trayInstalled) show()
}
