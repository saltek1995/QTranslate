package com.github.ahatem.qtranslate.ui.swing.main.input

/**
 * Suppresses only the four events emitted by the Windows Robot Copy fallback.
 *
 * Events are buffered until the sequence is proven to be ours. Any interruption replays the
 * buffered prefix, so an application never loses a real or third-party injected key.
 */
internal class OwnedCopyEventFilter(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMs: Long = 2_000,
) {
    private data class Attempt(val startedAt: Long, val buffered: MutableList<GlobalInputEvent.Key> = mutableListOf())
    private val attempts = java.util.ArrayDeque<Attempt>()
    private val deferred = mutableListOf<GlobalInputEvent.Key>()

    init {
        require(timeoutMs >= 0) { "timeoutMs must not be negative" }
    }

    @Synchronized
    fun begin() {
        val now = clockMs()
        expire(now, deferred)
        attempts.addLast(Attempt(now))
    }

    @Synchronized
    fun abort() {
        attempts.pollLast()?.let { deferred += it.buffered }
    }

    /** Lifecycle teardown deliberately retires input from the old listener generation. */
    @Synchronized
    fun clear() {
        attempts.clear()
        deferred.clear()
    }

    /**
     * Returns the events that must continue through normal input handling. The caller receives a
     * separate list, so it processes replayed input after this monitor lock has been released.
     */
    @Synchronized
    fun filter(event: GlobalInputEvent.Key): List<GlobalInputEvent.Key> {
        val forwarded = deferred.toMutableList()
        deferred.clear()
        expire(clockMs(), forwarded)
        if (event.selfInjected) return forwarded
        val current = attempts.peekFirst() ?: return forwarded + event
        if (!event.injected || !matchesExpected(event, current.buffered)) {
            forwarded += current.buffered
            current.buffered.clear()
            // This may be genuine input interleaved before the already-sent Copy reaches
            // the dispatcher. Replay the user gesture but retain outstanding Copy ownership.
            return forwarded + event
        }
        current.buffered += event
        if (current.buffered.size == OWNED_COPY_EVENT_COUNT) attempts.removeFirst()
        return forwarded
    }

    private fun matchesExpected(event: GlobalInputEvent.Key, buffered: List<GlobalInputEvent.Key>): Boolean = when (buffered.size) {
        0 -> event.keyClass == KeyClass.CONTROL && event.pressed
        1 -> event.selectionKey == SelectionKey.COPY && event.pressed
        2 -> event.selectionKey == SelectionKey.COPY && !event.pressed
        3 -> event.keyClass == KeyClass.CONTROL && !event.pressed && event.nativeCode == buffered.first().nativeCode
        else -> false
    }

    private fun expire(now: Long, replay: MutableList<GlobalInputEvent.Key>) {
        while (true) {
            val first = attempts.peekFirst() ?: return
            val elapsed = now - first.startedAt
            if (elapsed >= 0 && elapsed < timeoutMs) return
            replay += attempts.removeFirst().buffered
        }
    }

    private companion object {
        const val OWNED_COPY_EVENT_COUNT = 4
    }
}
