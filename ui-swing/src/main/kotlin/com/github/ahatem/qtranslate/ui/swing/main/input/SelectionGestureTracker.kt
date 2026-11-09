package com.github.ahatem.qtranslate.ui.swing.main.input

import java.awt.Point

/**
 * Press/drag/release and double/triple-click bookkeeping for automatic selection.
 *
 * A gesture counts only once the pointer has travelled [minDistancePx] while the primary
 * button is held, or consecutive nearby clicks fall within the double-click interval.
 * A single click alone is not a selection. The release location is owned by the
 * caller (it reports where the pointer was released for popup placement); this tracker only
 * answers whether the press turned into a drag.
 */
class SelectionGestureTracker(
    private val minDistancePx: Double = DEFAULT_MIN_DISTANCE_PX,
    private val multiClickIntervalMs: Long = 500L,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private var start: Point? = null
    private var dragged = false
    private var previousClick: Point? = null
    private var previousClickAt: Long = Long.MIN_VALUE
    var lastReleaseWasMultiClick: Boolean = false
        private set

    fun onPressed(point: Point) {
        start = point
        dragged = false
    }

    fun onMoved(point: Point) {
        val origin = start ?: return
        if (origin.distance(point) >= minDistancePx) dragged = true
    }

    /**
     * Ends the gesture. Returns true when the press had become a drag; always resets, so a
     * press without a matching release cannot leak held state into the next gesture.
     */
    fun onReleased(): Boolean {
        lastReleaseWasMultiClick = false
        val origin = start
        val wasDrag = origin != null && dragged
        start = null
        dragged = false
        if (origin == null) return false
        if (wasDrag) {
            previousClick = null
            return true
        }
        val now = clockMs()
        val elapsed = now - previousClickAt
        val isMultiClick = previousClick?.let {
            elapsed in 0..multiClickIntervalMs && it.distance(origin) <= minDistancePx
        } ?: false
        previousClick = Point(origin)
        previousClickAt = now
        lastReleaseWasMultiClick = isMultiClick
        return isMultiClick
    }

    /** Invalidates both an in-progress gesture and the click chain on focus/configuration changes. */
    fun reset() {
        start = null
        dragged = false
        previousClick = null
        previousClickAt = Long.MIN_VALUE
        lastReleaseWasMultiClick = false
    }

    companion object {
        const val DEFAULT_MIN_DISTANCE_PX = 5.0
    }
}
