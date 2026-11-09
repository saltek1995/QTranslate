package com.github.ahatem.qtranslate.ui.swing.shared.clipboard

import com.github.ahatem.qtranslate.api.core.Logger

/**
 * Detects whether the clipboard has been touched since a token was taken.
 *
 * A synthesized copy is not detected by comparing text: copying a selection equal to the
 * clipboard contents leaves the text unchanged while the platform still records a new
 * clipboard generation. Tokens therefore track the generation, not the data.
 *
 * [mark] returns null when the implementation cannot observe the platform. Callers must
 * treat a null token as "no verifiable copy is possible" and fail the capture instead of
 * accepting clipboard text: waiting and then reading would risk dispatching stale
 * pre-existing clipboard content as a fresh selection.
 */
interface ClipboardChangeMonitor {

    /** Opaque token for the clipboard generation right now, or null when unsupported. */
    fun mark(): Long?

    /** True when the clipboard generation advanced past [token]. */
    fun hasChangedSince(token: Long): Boolean
}

/** Picks the best change monitor for the current platform. */
object ClipboardChangeMonitors {

    fun create(clipboard: SystemClipboard, logger: Logger): ClipboardChangeMonitor {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            WindowsClipboardChangeMonitor.create(logger)?.let {
                return RecoveringClipboardChangeMonitor(it, SignatureClipboardChangeMonitor(clipboard), logger)
            }
        }
        return SignatureClipboardChangeMonitor(clipboard)
    }
}

/**
 * Chooses an observer at the start of a capture and pins it until the next mark. SelectionCapture
 * serializes these transactions. If Windows cannot yet supply a native generation (including
 * before the first clipboard use after login), a signature can still prove a content change.
 * Native availability is retried on every new capture, never switched mid-transaction.
 */
internal class RecoveringClipboardChangeMonitor(
    private val primary: ClipboardChangeMonitor,
    private val fallback: ClipboardChangeMonitor,
    private val logger: Logger,
) : ClipboardChangeMonitor {
    private data class Observation(val id: Long, val monitor: ClipboardChangeMonitor, val token: Long)
    private var observation: Observation? = null
    private var nextId = 0L
    private var usingFallback = false

    override fun mark(): Long? {
        observation = null
        val primaryToken = primary.mark()
        val monitor = if (primaryToken != null) primary else fallback
        val token = primaryToken ?: fallback.mark() ?: return null
        val fallbackNow = monitor === fallback
        if (fallbackNow != usingFallback) {
            if (fallbackNow) logger.warn("Native clipboard counter unavailable; using content-change detection")
            else logger.info("Native clipboard counter recovered")
            usingFallback = fallbackNow
        }
        val id = ++nextId
        observation = Observation(id, monitor, token)
        return id
    }

    override fun hasChangedSince(token: Long): Boolean {
        val current = observation ?: return false
        return current.id == token && current.monitor.hasChangedSince(current.token)
    }
}

/**
 * Fallback for platforms without a clipboard generation counter.
 *
 * It hashes the available flavors and text, so most changes are seen, but a copy whose data
 * is identical to what is already on the clipboard cannot be detected. Windows, the primary
 * target, uses the native sequence number instead.
 */
internal class SignatureClipboardChangeMonitor(
    private val clipboard: SystemClipboard,
) : ClipboardChangeMonitor {

    override fun mark(): Long? = clipboard.signature()

    /**
     * A failed signature read is not a change. Signature reads fail exactly when the
     * clipboard cannot be observed, and treating that as a confirmed change would let the
     * caller accept stale clipboard text as a fresh selection.
     */
    override fun hasChangedSince(token: Long): Boolean {
        val current = clipboard.signature() ?: return false
        return current != token
    }
}
