package com.github.ahatem.qtranslate.core.settings.system

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsLoginTaskRegistrationTest {
    private val command = "\"C:\\Tools O'Brien\\QTranslate\\QTranslate.exe\" --startup"

    @Test
    fun `login task passes paths safely through encoded powershell and scopes to current SID`() {
        var argv = emptyList<String>()
        val registration = WindowsLoginTaskRegistration(commandRunner = {
            argv = it
            WindowsStartupRegistration.CommandResult(0, "")
        }, osName = "Windows 11")
        assertTrue(registration.reconcile(true, command))
        assertEquals(listOf("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand"), argv.dropLast(1))
        val script = String(Base64.getDecoder().decode(argv.last()), StandardCharsets.UTF_16LE)
        assertTrue(script.contains("'C:\\Tools O''Brien\\QTranslate\\QTranslate.exe'"))
        assertTrue(script.contains("'--startup'"))
        assertTrue(script.contains("WorkingDirectory ="))
        assertTrue(script.contains("-AtLogOn -User ${'$'}sid"))
        assertTrue(script.contains("-LogonType Interactive"))
        assertTrue(script.contains("IsInRole"))
        assertTrue(script.contains("'Highest'"))
        assertTrue(script.contains("'Limited'"))
        assertTrue(script.contains("ExecutionTimeLimit ([TimeSpan]::Zero)"))
        assertFalse(script.contains("-Password"))
    }

    @Test
    fun `disable removes only the app owned task and needs no launcher`() {
        val script = WindowsLoginTaskRegistration.script(false, null)
        assertTrue(script.contains("Description -ne ${'$'}marker"))
        assertTrue(script.contains("Unregister-ScheduledTask -TaskName ${'$'}name"))
        assertFalse(script.contains("Register-ScheduledTask -TaskName"))
        assertFalse(script.contains("Stop-ScheduledTask")) // disabling startup does not close the app
    }

    @Test
    fun `failed registration returns false while malformed command runs nothing`() {
        var calls = 0
        val registration = WindowsLoginTaskRegistration(commandRunner = {
            calls++
            WindowsStartupRegistration.CommandResult(1, "")
        }, osName = "Windows 10")
        assertFalse(registration.reconcile(true, null))
        assertFalse(registration.reconcile(true, "unquoted command"))
        assertEquals(0, calls)
        assertFalse(registration.reconcile(true, command))
        assertEquals(1, calls)
    }

    @Test
    fun `off Windows task registration is inert`() {
        val registration = WindowsLoginTaskRegistration(commandRunner = { error("must not run") }, osName = "Linux")
        assertTrue(registration.reconcile(true, command))
        assertTrue(registration.reconcile(false, null))
    }

    @Test
    fun `successful task migration removes the Run entry and does not add a duplicate`() {
        val calls = mutableListOf<List<String>>()
        val registration = WindowsStartupRegistration(
            commandRunner = {
                calls += it
                if (it[1] == "query") WindowsStartupRegistration.CommandResult(0, "QTranslate REG_SZ $command")
                else WindowsStartupRegistration.CommandResult(0, "")
            },
            osName = "Windows 11", launcherCommand = { command }, loginTask = { enabled, actual ->
                assertTrue(enabled)
                assertEquals(command, actual)
                true
            },
        )
        assertTrue(registration.reconcile(true))
        assertTrue(calls.any { it[0] == "powershell.exe" })
        assertFalse(calls.any { it[1] == "add" })
    }

    @Test
    fun `scheduler refusal falls back only after task removal`() {
        val calls = mutableListOf<List<String>>()
        val registration = WindowsStartupRegistration(
            commandRunner = {
                calls += it
                WindowsStartupRegistration.CommandResult(if (it[1] == "query") 1 else 0, "")
            }, osName = "Windows 11", launcherCommand = { command }, loginTask = { enabled, _ -> !enabled },
            loginTaskAbsent = { true },
        )
        assertTrue(registration.reconcile(true))
        assertTrue(calls.any { it[1] == "add" })
        assertTrue(registration.reconcile(false))
        val blocked = WindowsStartupRegistration(
            commandRunner = { WindowsStartupRegistration.CommandResult(1, "") },
            osName = "Windows 11", launcherCommand = { command }, loginTask = { _, _ -> false },
        )
        assertFalse(blocked.reconcile(true))
        assertFalse(blocked.reconcile(false))
    }
}
