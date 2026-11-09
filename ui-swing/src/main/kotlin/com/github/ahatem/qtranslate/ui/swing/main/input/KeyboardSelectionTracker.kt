package com.github.ahatem.qtranslate.ui.swing.main.input

/** Editing keys normalized at the native adapter boundary; ordinary text is never retained. */
enum class SelectionKey { OTHER, SELECT_ALL, NAVIGATION, COPY }

/**
 * Windows virtual keys used by selection. Other backends must not interpret their native
 * keycodes as Win32 values. QInput supplies no portable raw-key usage in ABI v1.
 */
internal object WindowsSelectionKeys {
    fun classify(code: Int): SelectionKey = when (code) {
        0x41 -> SelectionKey.SELECT_ALL
        0x43 -> SelectionKey.COPY
        in 0x21..0x28 -> SelectionKey.NAVIGATION // Page Up/Down, End/Home, arrows
        else -> SelectionKey.OTHER
    }
}

/**
 * Recognizes completed keyboard selections without consuming any editing key. Modifier and
 * selecting-key releases complete a gesture; repeat presses extend it. QInput-owned injection
 * is ignored; external remappers remain ordinary editing input.
 */
internal class KeyboardSelectionTracker {
    private val held = mutableMapOf<Int, Pair<KeyClass, SelectionKey>>()
    private var pending = false
    val isPending: Boolean get() = pending
    val heldKeyCount: Int get() = held.size

    fun onKey(event: GlobalInputEvent.Key): Boolean {
        if (event.selfInjected) return false
        if (event.pressed) {
            held[event.nativeCode] = event.keyClass to event.selectionKey
            val shift = held.values.any { it.first == KeyClass.SHIFT }
            val control = held.values.any { it.first == KeyClass.CONTROL }
            val forbidden = held.values.any { it.first == KeyClass.ALT || it.first == KeyClass.META }
            when {
                forbidden -> pending = false
                event.selectionKey == SelectionKey.NAVIGATION && shift -> pending = true
                event.selectionKey == SelectionKey.SELECT_ALL && control && !shift -> pending = true
                event.keyClass == KeyClass.OTHER -> pending = false
            }
            return false
        }
        held.remove(event.nativeCode)
        if (!pending || held.isNotEmpty()) return false
        pending = false
        return true
    }

    fun reset() {
        held.clear()
        pending = false
    }
}
