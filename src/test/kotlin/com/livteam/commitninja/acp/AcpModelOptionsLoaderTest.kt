package com.livteam.commitninja.acp

import com.livteam.commitninja.settings.AgentCommandLine
import com.livteam.commitninja.settings.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AcpModelOptionsLoaderTest {
    @Test
    fun `opencode profile loads models with models argument and parses line output`() {
        val invocationFile = Files.createTempFile("fake-opencode-invocation", ".txt")
        val fakeOpencode = fakeCommand(
            """
            #!/bin/sh
            printf '%s\n' "$*" > '${invocationFile.toAbsolutePath()}'
            if [ "$#" -ne 1 ] || [ "$1" != "models" ]; then
              printf 'unexpected arguments: %s\n' "$*" >&2
              exit 2
            fi
            printf 'ollama-cloud/deepseek-v4-pro\n\nollama-cloud/deepseek-v4-flash\nollama-cloud/deepseek-v4-pro\n'
            """.trimIndent(),
        )

        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.OPENCODE,
            command = fakeOpencode.toAbsolutePath().toString(),
            arguments = listOf("acp"),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
        assertEquals("models", invocationFile.readText().trim())
    }

    @Test
    fun `codex profile loads bundled debug models and filters gpt slugs`() {
        val invocationFile = Files.createTempFile("fake-codex-invocation", ".txt")
        val fakeCodex = fakeCommand(
            """
            #!/bin/sh
            printf '%s\n' "$*" > '${invocationFile.toAbsolutePath()}'
            if [ "$#" -ne 3 ] || [ "$1" != "debug" ] || [ "$2" != "models" ] || [ "$3" != "--bundled" ]; then
              printf 'unexpected arguments: %s\n' "$*" >&2
              exit 2
            fi
            cat <<'JSON'
            [
              {"slug":"gpt-5.1-codex"},
              {"id":"codex-auto-review"},
              {"slug":"gpt-codex-auto-review"},
              {"id":"gpt-4.1"},
              {"slug":"claude-sonnet-4-5"},
              {"models":[{"slug":"gpt-5.1-codex-mini"}]}
            ]
            JSON
            """.trimIndent(),
        )

        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CODEX_ACP,
            command = fakeCodex.toAbsolutePath().toString(),
            arguments = listOf("acp"),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(listOf("gpt-5.1-codex", "gpt-4.1", "gpt-5.1-codex-mini"), result.getOrThrow())
        assertEquals("debug models --bundled", invocationFile.readText().trim())
    }

    @Test
    fun `claude profile returns built in commit message model choices`() {
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            command = "",
            arguments = emptyList(),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(listOf("default", "opus", "sonnet", "haiku"), result.getOrThrow())
    }

    @Test
    fun `none profile returns empty model choices`() {
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.NONE,
            command = "",
            arguments = emptyList(),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(emptyList<String>(), result.getOrThrow())
    }

    @Test
    fun `junie profile returns empty model choices`() {
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.JUNIE_ACP,
            command = "junie",
            arguments = listOf("--acp", "true"),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(emptyList<String>(), result.getOrThrow())
    }

    @Test
    fun `opencode profile uses ACP command defaults for generation`() {
        assertEquals("opencode", AgentProfile.OPENCODE.defaultCommand)
        assertEquals(listOf("acp"), AgentCommandLine.splitArguments(AgentProfile.OPENCODE.defaultArguments))
        assertEquals("opencode", AgentProfile.OPENCODE.defaultModelCommand)
        assertEquals(listOf("models"), AgentCommandLine.splitArguments(AgentProfile.OPENCODE.defaultModelArguments))
    }

    @Test
    fun `codex profile uses adapter command default for generation and CLI debug command for models`() {
        assertEquals("npx", AgentProfile.CODEX_ACP.defaultCommand)
        assertEquals(
            listOf("-y", "@zed-industries/codex-acp"),
            AgentCommandLine.splitArguments(AgentProfile.CODEX_ACP.defaultArguments),
        )
        assertEquals("codex", AgentProfile.CODEX_ACP.defaultModelCommand)
        assertEquals(
            listOf("debug", "models", "--bundled"),
            AgentCommandLine.splitArguments(AgentProfile.CODEX_ACP.defaultModelArguments),
        )
    }

    @Test
    fun `claude profile uses adapter command default for generation`() {
        assertEquals("npx", AgentProfile.CLAUDE_AGENT_ACP.defaultCommand)
        assertEquals(
            listOf("-y", "@zed-industries/claude-agent-acp"),
            AgentCommandLine.splitArguments(AgentProfile.CLAUDE_AGENT_ACP.defaultArguments),
        )
    }

    @Test
    fun `junie profile uses cli acp mode defaults for generation`() {
        assertEquals("junie", AgentProfile.JUNIE_ACP.defaultCommand)
        assertEquals(
            listOf("--acp", "true"),
            AgentCommandLine.splitArguments(AgentProfile.JUNIE_ACP.defaultArguments),
        )
        assertEquals("junie", AgentProfile.JUNIE_ACP.defaultModelCommand)
        assertEquals(emptyList<String>(), AgentCommandLine.splitArguments(AgentProfile.JUNIE_ACP.defaultModelArguments))
    }

    @Test
    fun `model command failure reports command and stderr diagnostic`() {
        val failingCommand = fakeCommand(
            """
            #!/bin/sh
            printf 'adapter missing: install cli first\n' >&2
            exit 127
            """.trimIndent(),
        )

        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.OPENCODE,
            command = failingCommand.toAbsolutePath().toString(),
            arguments = listOf("acp"),
            workingDirectory = null,
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, failingCommand.fileName.toString() in message)
        assertTrue(message, "adapter missing" in message)
    }

    private fun fakeCommand(contents: String): Path {
        val command = Files.createTempFile("fake-model-command", ".sh")
        command.writeText(contents)
        command.toFile().setExecutable(true)
        return command
    }
}
