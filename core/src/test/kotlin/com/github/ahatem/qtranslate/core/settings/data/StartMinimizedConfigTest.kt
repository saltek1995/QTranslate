package com.github.ahatem.qtranslate.core.settings.data

import com.github.ahatem.qtranslate.api.core.Logger
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StartMinimizedConfigTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = object : Logger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, error: Throwable?) = Unit
    }

    @Test fun `older settings keep their visibility and unrelated choices after migration`() {
        val older = json.decodeFromString<Configuration>("""{"configVersion":9,"launchOnSystemStartup":true,"autoCheckForUpdates":false}""")
        val migrated = ConfigMigrator.migrate(older, logger)
        assertFalse(migrated.startMinimized)
        assertEquals(older.copy(configVersion = ConfigMigrator.CURRENT_VERSION), migrated)
        assertEquals(migrated, ConfigMigrator.migrate(migrated, logger))
    }

    @Test fun `both visibility choices persist independently of autostart`() {
        for (minimized in listOf(false, true)) for (startup in listOf(false, true)) {
            val config = Configuration.DEFAULT.copy(startMinimized = minimized, launchOnSystemStartup = startup)
            val decoded = json.decodeFromString<Configuration>(json.encodeToString(Configuration.serializer(), config))
            assertEquals(config, decoded)
        }
    }

    @Test fun `migration preserves an explicit minimized choice`() {
        val previous = Configuration.DEFAULT.copy(configVersion = 9, startMinimized = true)
        val migrated = ConfigMigrator.migrate(previous, logger)
        assertEquals(previous.copy(configVersion = ConfigMigrator.CURRENT_VERSION), migrated)
    }
}
