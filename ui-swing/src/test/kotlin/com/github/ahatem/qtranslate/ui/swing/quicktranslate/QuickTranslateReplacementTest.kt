package com.github.ahatem.qtranslate.ui.swing.quicktranslate

import com.github.ahatem.qtranslate.api.core.Logger
import com.github.ahatem.qtranslate.core.localization.LanguageTomlParser
import com.github.ahatem.qtranslate.core.localization.LocalizationManager
import com.github.ahatem.qtranslate.core.main.mvi.MainState
import com.github.ahatem.qtranslate.core.settings.data.Configuration
import com.github.ahatem.qtranslate.ui.swing.shared.TestIcons
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assumptions.assumeFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickTranslateReplacementTest {
    private val logger = object : Logger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, error: Throwable?) = Unit
    }

    @Test
    fun `replacement uses the displayed result once and dismisses before paste`() {
        withDialog { dialog, state, events ->
            dialog.render(state.copy(translatedText = "first result"))
            val button = replaceButton(dialog, state.strings.replaceTooltip)
            assertTrue(button.isEnabled)
            dialog.render(state.copy(translatedText = "latest result"))
            button.doClick(0)
            assertFalse(dialog.isVisible, "the popup must release focus before paste")
            assertFalse(button.isEnabled, "a double click must not paste twice")
            button.doClick(0)
            assertEquals(listOf("dismiss"), events)
        }
    }

    @Test
    fun `replacement is unavailable for empty results and while retranslating`() {
        withDialog { dialog, state, events ->
            dialog.render(state)
            val button = replaceButton(dialog, state.strings.replaceTooltip)
            dialog.render(state.copy(isLoading = true))
            assertFalse(button.isEnabled)
            button.doClick(0)
            dialog.render(state.copy(translatedText = "  "))
            assertFalse(button.isEnabled)
            button.doClick(0)
            assertTrue(events.isEmpty())
            dialog.render(state)
            assertTrue(button.isEnabled)
        }
    }

    private fun withDialog(block: (QuickTranslateDialog, QuickTranslateDialogState, MutableList<String>) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "A dialog needs a display")
        val directory = Files.createTempDirectory("quick-replacement").toFile()
        val events = mutableListOf<String>()
        var dialog: QuickTranslateDialog? = null
        var owner: JFrame? = null
        try {
            SwingUtilities.invokeAndWait {
                val localizer = LocalizationManager(directory, LanguageTomlParser(), logger)
                val state = buildQuickTranslateDialogState(
                    MainState(translatedText = "result", isQuickTranslateDialogVisible = true,
                        isQuickTranslateDialogPinned = true),
                    Configuration(isPopupAutoSizeEnabled = false), localizer
                )
                owner = JFrame()
                dialog = QuickTranslateDialog(
                    owner = owner!!, iconManager = TestIcons.iconManager(), localizationManager = localizer,
                    onDismiss = { events += "dismiss" }, onTranslatorSelected = {}, onListen = {},
                    onStopListening = {}, onCopy = {}, onSavePosition = {}, onSaveSize = {},
                    onPinToggled = {}, onReplaceSelection = { text ->
                        assertFalse(dialog!!.isVisible)
                        events += "paste:$text"
                    }
                )
                block(dialog!!, state, events)
            }
            // Drain the queued replacement after the synchronous hide/dismiss turn.
            SwingUtilities.invokeAndWait {
                if (events.isNotEmpty()) assertEquals(listOf("dismiss", "paste:latest result"), events)
            }
        } finally {
            SwingUtilities.invokeAndWait { dialog?.dispose(); owner?.dispose() }
            directory.deleteRecursively()
        }
    }

    private fun replaceButton(container: Container, label: String): JButton {
        val buttons = container.components.flatMap { component ->
            when (component) {
                is JButton -> listOf(component)
                is Container -> descendants(component)
                else -> emptyList()
            }
        }
        return buttons.single { it.accessibleContext.accessibleName == label }
    }

    private fun descendants(container: Container): List<JButton> = container.components.flatMap {
        when (it) {
            is JButton -> listOf(it)
            is Container -> descendants(it)
            else -> emptyList()
        }
    }
}
