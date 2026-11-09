package com.github.ahatem.qtranslate.core.settings.system

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Executes the generated task scheduler script against PowerShell functions, never the real
 * scheduler. These cases are deliberately Windows-only because they exercise Windows identity
 * translation and the scheduler object shape rather than a Kotlin reimplementation of the script.
 */
class WindowsLoginTaskScriptTest {
    private val command = "\"C:\\Program Files\\O'Brien\\QTranslate.exe\" --startup --quiet"
    private val executable = "C:\\Program Files\\O'Brien\\QTranslate.exe"
    private val directory = "C:\\Program Files\\O'Brien"

    @Test
    fun `matching task owned by current account name is left alone`() = windowsOnly {
        val invocation = reconcile(true, matchingTask(userId = "${'$'}([Security.Principal.WindowsIdentity]::GetCurrent().Name)"))

        assertTrue(invocation.value, invocation.result.output)
        assertEquals(0, invocation.result.exitCode, invocation.result.output)
        assertFalse(invocation.result.output.contains("REGISTER|"), invocation.result.output)
    }

    @Test
    fun `ordinary process preserves a matching highest task`() = windowsOnly {
        if (isElevated()) return@windowsOnly

        val invocation = reconcile(true, matchingTask(runLevel = "Highest"))

        assertTrue(invocation.value, invocation.result.output)
        assertEquals(0, invocation.result.exitCode, invocation.result.output)
        assertFalse(invocation.result.output.contains("REGISTER|"), invocation.result.output)
    }

    @Test
    fun `scheduler read failure returns false without mutating`() = windowsOnly {
        val invocation = reconcile(true, """
            function Get-ScheduledTask { throw 'READ_FAILURE' }
            ${mutationGuards()}
        """)

        assertFalse(invocation.value)
        assertEquals(1, invocation.result.exitCode, invocation.result.output)
    }

    @Test
    fun `missing task is registered with startup arguments and working directory`() = windowsOnly {
        val invocation = reconcile(true, """
            function Get-ScheduledTask { @() }
            function Test-Path { param([string] ${'$'}LiteralPath, [string] ${'$'}PathType) ${'$'}true }
            function New-ScheduledTaskAction {
                param([string] ${'$'}Execute, [string] ${'$'}WorkingDirectory, [string] ${'$'}Argument)
                [pscustomobject]@{ Execute = ${'$'}Execute; WorkingDirectory = ${'$'}WorkingDirectory; Arguments = ${'$'}Argument }
            }
            function New-ScheduledTaskTrigger { param([switch] ${'$'}AtLogOn, [string] ${'$'}User) [pscustomobject]@{ UserId = ${'$'}User } }
            function New-ScheduledTaskPrincipal { param([string] ${'$'}UserId, [string] ${'$'}LogonType, [string] ${'$'}RunLevel) [pscustomobject]@{ UserId = ${'$'}UserId; LogonType = ${'$'}LogonType; RunLevel = ${'$'}RunLevel } }
            function New-ScheduledTaskSettingsSet { [pscustomobject]@{} }
            function Register-ScheduledTask {
                param(${ '$' }TaskName, ${ '$' }TaskPath, ${ '$' }Description, ${ '$' }Action, ${ '$' }Trigger, ${ '$' }Principal, ${ '$' }Settings, [switch] ${ '$' }Force)
                [Console]::WriteLine(("REGISTER|{0}|{1}|{2}|{3}|{4}|{5}|{6}" -f ${ '$' }TaskName, ${ '$' }Action.Execute, ${ '$' }Action.Arguments, ${ '$' }Action.WorkingDirectory, ${ '$' }Trigger.UserId, ${ '$' }Principal.LogonType, ${ '$' }Settings.GetType().FullName))
            }
            function Unregister-ScheduledTask { throw 'UNEXPECTED_UNREGISTER' }
        """)

        assertTrue(invocation.value, invocation.result.output)
        assertEquals(0, invocation.result.exitCode, invocation.result.output)
        val registration = invocation.result.output.lineSequence().single { it.startsWith("REGISTER|") }.split('|')
        assertEquals(executable, registration[2])
        assertEquals("--startup --quiet", registration[3])
        assertEquals(directory, registration[4])
        assertEquals("Interactive", registration[6])
        assertTrue(registration[5].startsWith("S-1-"), invocation.result.output)
    }

    @Test
    fun `disable succeeds when absent and refuses a foreign task`() = windowsOnly {
        val absent = reconcile(false, """
            function Get-ScheduledTask { @() }
            ${mutationGuards()}
        """)
        val foreign = reconcile(false, """
            function Get-ScheduledTask { [pscustomobject]@{ TaskPath = '\'; TaskName = "QTranslate-${'$'}([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)"; Description = 'another application' } }
            ${mutationGuards()}
        """)

        assertTrue(absent.value)
        assertEquals(0, absent.result.exitCode, absent.result.output)
        assertFalse(foreign.value)
        assertEquals(2, foreign.result.exitCode, foreign.result.output)
    }

    @Test
    fun `probe reports existing task as present and missing task as absent`() = windowsOnly {
        val existing = probe("""
            function Get-ScheduledTask { [pscustomobject]@{ TaskPath = '\'; TaskName = "QTranslate-${'$'}([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)"; Description = ${psLiteral(WindowsLoginTaskRegistration.OWNER_MARKER)} } }
            ${mutationGuards()}
        """)
        val missing = probe("""
            function Get-ScheduledTask { @() }
            ${mutationGuards()}
        """)

        assertFalse(existing)
        assertTrue(missing)
    }

    private fun reconcile(enabled: Boolean, prefix: String): Invocation {
        var result: ProcessResult? = null
        val registration = WindowsLoginTaskRegistration(commandRunner = { argv ->
            result = runEncoded(prefix + decode(argv.last()))
            WindowsStartupRegistration.CommandResult(result!!.exitCode, result!!.output)
        }, osName = "Windows 11")
        return Invocation(registration.reconcile(enabled, if (enabled) command else null), requireNotNull(result))
    }

    private fun probe(prefix: String): Boolean {
        var result: ProcessResult? = null
        val registration = WindowsLoginTaskRegistration(commandRunner = { argv ->
            result = runEncoded(prefix + decode(argv.last()))
            WindowsStartupRegistration.CommandResult(result!!.exitCode, result!!.output)
        }, osName = "Windows 11")
        val absent = registration.isAbsent()
        assertTrue(result != null)
        return absent
    }

    private fun runEncoded(script: String): ProcessResult {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
        val process = ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
            return ProcessResult(-1, "PowerShell timed out")
        }
        val output = process.inputStream.bufferedReader().use { it.readText().take(4_000) }
        return ProcessResult(process.exitValue(), output)
    }

    private fun decode(encoded: String): String = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE)

    private fun matchingTask(
        userId: String = "${'$'}([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)",
        runLevel: String = if (isElevated()) "Highest" else "Limited",
    ) = """
        function Get-ScheduledTask {
            [pscustomobject]@{
                TaskPath = '\'; TaskName = "QTranslate-${'$'}([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)"; Description = ${psLiteral(WindowsLoginTaskRegistration.OWNER_MARKER)}
                Actions = @([pscustomobject]@{ Execute = ${psLiteral(executable)}; Arguments = '--startup --quiet'; WorkingDirectory = ${psLiteral(directory)} })
                Principal = [pscustomobject]@{ UserId = $userId; LogonType = 'Interactive'; RunLevel = '$runLevel' }
                Triggers = @([pscustomobject]@{ UserId = $userId })
                State = 'Ready'
                Settings = [pscustomobject]@{ ExecutionTimeLimit = 'PT0S'; DisallowStartIfOnBatteries = ${'$'}false; StopIfGoingOnBatteries = ${'$'}false }
            }
        }
        ${mutationGuards()}
        function Test-Path { param([string] ${'$'}LiteralPath, [string] ${'$'}PathType) ${'$'}true }
    """

    private fun mutationGuards() = """
        function Register-ScheduledTask { throw 'UNEXPECTED_REGISTER' }
        function Unregister-ScheduledTask { throw 'UNEXPECTED_UNREGISTER' }
        function New-ScheduledTaskAction { throw 'UNEXPECTED_ACTION' }
        function New-ScheduledTaskTrigger { throw 'UNEXPECTED_TRIGGER' }
        function New-ScheduledTaskPrincipal { throw 'UNEXPECTED_PRINCIPAL' }
        function New-ScheduledTaskSettingsSet { throw 'UNEXPECTED_SETTINGS' }
        function Test-Path { throw 'UNEXPECTED_PATH_CHECK' }
    """

    private fun isElevated(): Boolean = runEncoded("""
        ${'$'}identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        ${'$'}principal = New-Object Security.Principal.WindowsPrincipal(${'$'}identity)
        if (${'$'}principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { exit 0 } else { exit 1 }
    """.trimIndent()).exitCode == 0

    private fun psLiteral(value: String) = "'${value.replace("'", "''")}'"

    private fun windowsOnly(block: () -> Unit) {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) block()
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private data class Invocation(val value: Boolean, val result: ProcessResult)
}
