package com.github.ahatem.qtranslate.ui.swing.main.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnedCopyEventFilterTest {
    @Test
    fun `owned control repeat while physically held is still consumed`() {
        val filter = filter()
        filter.begin()
        assertTrue(filter.filter(control(true, injected = true).copy(repeat = true)).isEmpty())
        assertTrue(filter.filter(copy(true)).isEmpty())
        assertTrue(filter.filter(copy(false)).isEmpty())
        assertTrue(filter.filter(control(false, injected = true)).isEmpty())
    }
    private var now = 0L

    @Test
    fun `exact owned copy chord is consumed and next user ctrl a is forwarded`() {
        val filter = filter()
        val owned = listOf(control(true, injected = true), copy(true), copy(false), control(false, injected = true))

        filter.begin()
        owned.forEach { assertTrue(filter.filter(it).isEmpty()) }

        val userControl = control(true, injected = false)
        val userSelectAll = key(0x41, true, injected = false, selectionKey = SelectionKey.SELECT_ALL)
        assertEquals(listOf(userControl), filter.filter(userControl))
        assertEquals(listOf(userSelectAll), filter.filter(userSelectAll))
    }

    @Test
    fun `injected ctrl down then a mismatch replays both events`() {
        val filter = filter()
        val control = control(true, injected = true)
        val selectAll = key(0x41, true, injected = true, selectionKey = SelectionKey.SELECT_ALL)

        filter.begin()
        assertTrue(filter.filter(control).isEmpty())
        assertEquals(listOf(control, selectAll), filter.filter(selectAll))
    }

    @Test
    fun `physical mismatch replays buffered prefix`() {
        val filter = filter()
        val ownedControl = control(true, injected = true)
        val physicalCopy = copy(true, injected = false)

        filter.begin()
        assertTrue(filter.filter(ownedControl).isEmpty())
        assertEquals(listOf(ownedControl, physicalCopy), filter.filter(physicalCopy))
    }

    @Test
    fun `timeout replays buffered prefix and current event`() {
        val filter = filter(timeoutMs = 20)
        val ownedControl = control(true, injected = true)
        val copy = copy(true)

        filter.begin()
        assertTrue(filter.filter(ownedControl).isEmpty())
        now = 20
        assertEquals(listOf(ownedControl, copy), filter.filter(copy))
    }

    @Test
    fun `unarmed third party event passes through`() {
        val filter = filter()
        val event = key(0x42, true, injected = true, selectionKey = SelectionKey.OTHER)

        assertEquals(listOf(event), filter.filter(event))
    }

    @Test
    fun `abort preserves an unproven control prefix for the next user selection`() {
        val filter = filter()
        val oldPrefix = control(true, injected = true)
        val selectAll = key(0x41, true, injected = true, selectionKey = SelectionKey.SELECT_ALL)

        filter.begin()
        assertTrue(filter.filter(oldPrefix).isEmpty())
        filter.abort()
        assertEquals(listOf(oldPrefix, selectAll), filter.filter(selectAll))

        filter.begin()
        assertTrue(filter.filter(oldPrefix).isEmpty())
        filter.begin()
        assertEquals(listOf(oldPrefix, selectAll), filter.filter(selectAll))
    }

    @Test fun `two delayed owned attempts are both consumed after their callbacks return`() {
        val filter = filter()
        filter.begin()
        filter.begin()
        repeat(2) {
            listOf(control(true, true), copy(true), copy(false), control(false, true)).forEach {
                assertTrue(filter.filter(it).isEmpty())
            }
        }
        val user = key(0x41, true, injected = true, selectionKey = SelectionKey.SELECT_ALL)
        assertEquals(listOf(user), filter.filter(user))
    }

    @Test fun `shutdown clear retires buffered events without replaying old lifecycle input`() {
        val filter = filter()
        filter.begin()
        filter.filter(control(true, true))
        filter.clear()
        val user = key(0x41, true, injected = true, selectionKey = SelectionKey.SELECT_ALL)
        assertEquals(listOf(user), filter.filter(user))
    }

    private fun filter(timeoutMs: Long = 2_000) = OwnedCopyEventFilter(clockMs = { now }, timeoutMs = timeoutMs)

    private fun control(pressed: Boolean, injected: Boolean) = key(
        nativeCode = 0xA2,
        pressed = pressed,
        injected = injected,
        keyClass = KeyClass.CONTROL,
    )

    private fun copy(pressed: Boolean, injected: Boolean = true) = key(
        nativeCode = 0x43,
        pressed = pressed,
        injected = injected,
        selectionKey = SelectionKey.COPY,
    )

    private fun key(
        nativeCode: Int,
        pressed: Boolean,
        injected: Boolean,
        keyClass: KeyClass = KeyClass.OTHER,
        selectionKey: SelectionKey = SelectionKey.OTHER,
    ) = GlobalInputEvent.Key(
        keyClass = keyClass,
        nativeCode = nativeCode,
        pressed = pressed,
        injected = injected,
        repeat = false,
        timestampMs = now,
        selectionKey = selectionKey,
    )
}
