package com.github.ahatem.qtranslate.ui.swing.main.input

import kotlin.test.Test
import kotlin.test.assertEquals

class InputRequirementsTest {

    private fun state(
        hotkeysEnabled: Boolean,
        doubleCtrlEnabled: Boolean,
        selectionCaptureEnabled: Boolean,
        dismissOnOutsideClickEnabled: Boolean,
    ) = InputRuntimeState(
        globalHotkeysEnabled = hotkeysEnabled,
        doubleCtrlEnabled = doubleCtrlEnabled,
        selectionCaptureEnabled = selectionCaptureEnabled,
        dismissOnOutsideClickEnabled = dismissOnOutsideClickEnabled
    )

    @Test
    fun `everything on requests everything`() {
        assertEquals(
            ResolvedInput(registerHotkeys = true, keyboard = true, mouseButtons = true, mouseMotion = false),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = true,
                    doubleCtrlEnabled = true,
                    selectionCaptureEnabled = false,
                    dismissOnOutsideClickEnabled = true
                )
            )
        )
    }

    @Test
    fun `disabled hotkeys keep keyboard and mouse for selection capture`() {
        assertEquals(
            ResolvedInput(registerHotkeys = false, keyboard = true, mouseButtons = true, mouseMotion = true),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = false,
                    doubleCtrlEnabled = true,
                    selectionCaptureEnabled = true,
                    dismissOnOutsideClickEnabled = false
                )
            )
        )
    }

    @Test
    fun `disabled double ctrl drops keyboard while hotkeys stay registered`() {
        assertEquals(
            ResolvedInput(registerHotkeys = true, keyboard = false, mouseButtons = true, mouseMotion = false),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = true,
                    doubleCtrlEnabled = false,
                    selectionCaptureEnabled = false,
                    dismissOnOutsideClickEnabled = true
                )
            )
        )
    }

    @Test
    fun `selection off and dismissal off request no mouse observation`() {
        assertEquals(
            ResolvedInput(registerHotkeys = true, keyboard = true, mouseButtons = false, mouseMotion = false),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = true,
                    doubleCtrlEnabled = true,
                    selectionCaptureEnabled = false,
                    dismissOnOutsideClickEnabled = false
                )
            )
        )
    }

    @Test
    fun `all features off requests nothing`() {
        assertEquals(
            ResolvedInput(registerHotkeys = false, keyboard = false, mouseButtons = false, mouseMotion = false),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = false,
                    doubleCtrlEnabled = false,
                    selectionCaptureEnabled = false,
                    dismissOnOutsideClickEnabled = false
                )
            )
        )
    }

    @Test
    fun `selection capture alone requests keyboard buttons and motion`() {
        assertEquals(
            ResolvedInput(registerHotkeys = false, keyboard = true, mouseButtons = true, mouseMotion = true),
            InputRequirements.resolve(
                state(
                    hotkeysEnabled = false,
                    doubleCtrlEnabled = false,
                    selectionCaptureEnabled = true,
                    dismissOnOutsideClickEnabled = false
                )
            )
        )
    }
}
