package com.github.ahatem.qtranslate.ui.swing.settings.panels

import com.github.ahatem.qtranslate.api.core.Logger
import com.github.ahatem.qtranslate.core.localization.LanguageTomlParser
import com.github.ahatem.qtranslate.core.localization.LocalizationManager
import com.github.ahatem.qtranslate.core.settings.data.Configuration
import com.github.ahatem.qtranslate.core.settings.data.SettingsRepository
import com.github.ahatem.qtranslate.core.settings.mvi.SettingsIntent
import com.github.ahatem.qtranslate.core.settings.mvi.SettingsStore
import java.nio.file.Files
import javax.swing.JCheckBox
import javax.swing.SwingUtilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralPanelStartMinimizedTest {
    private val logger = object : Logger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, error: Throwable?) = Unit
    }

    @Test fun `minimized checkbox follows startup and respects cancel and save`() = runTest {
        val directory = Files.createTempDirectory("qtranslate-minimized-test").toFile()
        val repository = SettingsRepository(directory, Json { ignoreUnknownKeys = true }, logger, storageScope = backgroundScope)
        repository.updateConfiguration(Configuration.DEFAULT)
        val store = SettingsStore(repository, logger, backgroundScope, Configuration.DEFAULT)
        val localizer = LocalizationManager(directory, LanguageTomlParser(), logger)
        lateinit var panel: GeneralPanel
        lateinit var checkbox: JCheckBox
        SwingUtilities.invokeAndWait {
            panel = GeneralPanel(store, localizer)
            panel.render(store.state.value)
            val startupIndex = panel.searchEntries.indexOfFirst { it.label == localizer.getString("settings_general.launch_on_startup") }
            val minimizedIndex = panel.searchEntries.indexOfFirst { it.label == localizer.getString("settings_general.start_minimized") }
            assertEquals(startupIndex + 1, minimizedIndex)
            checkbox = panel.searchEntries[minimizedIndex].anchor as JCheckBox
            checkbox.doClick()
        }
        runCurrent()
        assertTrue(store.state.value.workingConfiguration.startMinimized)
        assertFalse(store.state.value.originalConfiguration.startMinimized)
        assertFalse(repository.loadInitialConfiguration().startMinimized)

        store.dispatch(SettingsIntent.CancelChanges)
        runCurrent()
        SwingUtilities.invokeAndWait {
            panel.render(store.state.value)
            assertFalse(checkbox.isSelected)
            checkbox.doClick()
        }
        runCurrent()
        store.dispatch(SettingsIntent.SaveChanges)
        withContext(Dispatchers.Default) {
            withTimeout(5000) { store.state.first { !it.isDirty && it.originalConfiguration.startMinimized } }
        }
        assertTrue(repository.loadInitialConfiguration().startMinimized)
    }
}
