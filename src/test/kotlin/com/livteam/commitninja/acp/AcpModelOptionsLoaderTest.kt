package com.livteam.commitninja.acp

import com.livteam.commitninja.settings.AgentCommandLine
import com.livteam.commitninja.settings.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AcpModelOptionsLoaderTest {
    @Test
    fun `opencode ACP model loader uses SDK newline JSON and sends client version`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AcpModelOptionsLoader.load(
            javaExecutable,
            listOf("-cp", classpath, FakeOpencodeAcpServer::class.java.name),
            null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `opencode ACP model loader preserves UTF-8 SDK option values`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AcpModelOptionsLoader.load(
            javaExecutable,
            listOf("-cp", classpath, FakeUtf8OpencodeAcpServer::class.java.name),
            null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/한글-모델", "로컬/테스트"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `loads model choices from ACP session config options`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AcpModelOptionsLoader.load(
            javaExecutable,
            listOf("-cp", classpath, FakeAcpServer::class.java.name),
            null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("openai/gpt-5.1", "anthropic/claude-sonnet-4-5"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `opencode profile uses ACP command defaults without user command override`() {
        assertEquals("opencode", AgentProfile.OPENCODE.defaultCommand)
        assertEquals(listOf("acp"), AgentCommandLine.splitArguments(AgentProfile.OPENCODE.defaultArguments))
    }

    @Test
    fun `codex profile has no unconfirmed ACP command default`() {
        assertEquals("", AgentProfile.CODEX_ACP.defaultCommand)
        assertEquals(emptyList<String>(), AgentCommandLine.splitArguments(AgentProfile.CODEX_ACP.defaultArguments))
    }

    @Test
    fun `claude profile has no unconfirmed ACP command default`() {
        assertEquals("", AgentProfile.CLAUDE_AGENT_ACP.defaultCommand)
        assertEquals(emptyList<String>(), AgentCommandLine.splitArguments(AgentProfile.CLAUDE_AGENT_ACP.defaultArguments))
    }

    @Test
    fun `opencode model loader uses ACP SDK path instead of cli models`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.OPENCODE,
            command = javaExecutable,
            arguments = listOf("-cp", classpath, FakeOpencodeAcpServer::class.java.name),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `opencode model loader does not use cli models when ACP config options are empty`() {
        val fakeOpencode = Files.createTempFile("fake-opencode", ".sh")
        fakeOpencode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "acp" ]; then
              exit 0
            elif [ "${'$'}1" = "models" ]; then
              printf 'ollama-cloud/deepseek-v4-pro\nollama-cloud/deepseek-v4-flash\n'
              exit 0
            fi
            exit 1
            """.trimIndent(),
        )
        fakeOpencode.toFile().setExecutable(true)

        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.OPENCODE,
            command = fakeOpencode.toAbsolutePath().toString(),
            arguments = listOf("acp"),
            workingDirectory = null,
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, "closed" in message || "exited" in message)
        assertTrue(message, "ollama-cloud/deepseek-v4-pro" !in message)
    }

    @Test
    fun `codex model loader uses explicit ACP command configuration`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CODEX_ACP,
            command = javaExecutable,
            arguments = listOf("-cp", classpath, FakeOpencodeAcpServer::class.java.name),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `claude model loader uses explicit ACP command configuration`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            command = javaExecutable,
            arguments = listOf("-cp", classpath, FakeOpencodeAcpServer::class.java.name),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `codex model loader requires explicit ACP command configuration`() {
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CODEX_ACP,
            command = AgentProfile.CODEX_ACP.defaultCommand,
            arguments = emptyList(),
            workingDirectory = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), "Explicit ACP command configuration is required" in result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun `model command failure reports command and stderr diagnostic`() {
        val failingCommand = Files.createTempFile("fake-model-list-failure", ".sh")
        failingCommand.writeText(
            """
            #!/bin/sh
            printf 'adapter missing: install cli first\n' >&2
            exit 127
            """.trimIndent(),
        )
        failingCommand.toFile().setExecutable(true)

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

    @Test
    fun `claude model loader requires explicit ACP command configuration`() {
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            command = AgentProfile.CLAUDE_AGENT_ACP.defaultCommand,
            arguments = emptyList(),
            workingDirectory = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), "Explicit ACP command configuration is required" in result.exceptionOrNull()?.message.orEmpty())
    }

    private fun javaExecutable(): String {
        val javaHomeExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java")
        return if (javaHomeExecutable.exists()) javaHomeExecutable.toString() else "java"
    }

    object FakeOpencodeAcpServer {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = BufferedInputStream(System.`in`)
            val output = BufferedOutputStream(System.out)
            while (true) {
                val message = readJsonLine(input) ?: return
                check(!message.startsWith("Content-Length", ignoreCase = true)) {
                    "model loading must use SDK newline JSON transport"
                }
                when {
                    "\"method\":\"initialize\"" in message -> {
                        check("\"clientInfo\"" in message && "\"version\"" in message) {
                            "initialize request must include clientInfo.version"
                        }
                        writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":${extractId(message)},"result":{"protocolVersion":1,"agentCapabilities":{},"agentInfo":{"name":"fake-agent","version":"0.0.1"}}}
                            """.trimIndent(),
                        )
                    }
                    "\"method\":\"session/new\"" in message -> {
                        writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":${extractId(message)},"result":{"sessionId":"test","configOptions":[{"type":"select","id":"model","name":"Model","category":"model","currentValue":"ollama-cloud/deepseek-v4-pro","options":[{"value":"ollama-cloud/deepseek-v4-pro","name":"DeepSeek V4 Pro"},{"value":"ollama-cloud/deepseek-v4-flash","name":"DeepSeek V4 Flash"}]}]}}
                            """.trimIndent(),
                        )
                        return
                    }
                }
            }
        }

        fun readJsonLine(input: BufferedInputStream): String? {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val next = input.read()
                if (next == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.UTF_8)
                if (next == '\n'.code) return String(bytes.toByteArray(), StandardCharsets.UTF_8)
                bytes.add(next.toByte())
            }
        }

        fun writeJsonLine(output: BufferedOutputStream, json: String) {
            output.write(json.toByteArray(StandardCharsets.UTF_8))
            output.write('\n'.code)
            output.flush()
        }

        fun extractId(message: String): String {
            val idStart = message.indexOf("\"id\":")
            check(idStart >= 0) { "request must include id" }
            val valueStart = idStart + "\"id\":".length
            var index = valueStart
            var inString = false
            while (index < message.length) {
                val char = message[index]
                if (char == '"' && (index == valueStart || message[index - 1] != '\\')) {
                    inString = !inString
                } else if (!inString && (char == ',' || char == '}')) {
                    return message.substring(valueStart, index).trim()
                }
                index += 1
            }
            return message.substring(valueStart).trim()
        }
    }

    object FakeUtf8OpencodeAcpServer {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = BufferedInputStream(System.`in`)
            val output = BufferedOutputStream(System.out)
            while (true) {
                val message = FakeOpencodeAcpServer.readJsonLine(input) ?: return
                check(!message.startsWith("Content-Length", ignoreCase = true)) {
                    "model loading must use SDK newline JSON transport"
                }
                when {
                    "\"method\":\"initialize\"" in message -> FakeOpencodeAcpServer.writeJsonLine(
                        output,
                        """
                        {"jsonrpc":"2.0","id":${FakeOpencodeAcpServer.extractId(message)},"result":{"protocolVersion":1,"agentCapabilities":{},"agentInfo":{"name":"fake-agent","version":"0.0.1"}}}
                        """.trimIndent(),
                    )
                    "\"method\":\"session/new\"" in message -> {
                        FakeOpencodeAcpServer.writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":${FakeOpencodeAcpServer.extractId(message)},"result":{"sessionId":"test","configOptions":[{"type":"select","id":"model","name":"모델","category":"model","currentValue":"ollama-cloud/한글-모델","options":[{"value":"ollama-cloud/한글-모델","name":"한글 모델"},{"value":"로컬/테스트","name":"로컬 테스트"}]}]}}
                            """.trimIndent(),
                        )
                        return
                    }
                }
            }
        }
    }

    object FakeAcpServer {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = BufferedInputStream(System.`in`)
            val output = BufferedOutputStream(System.out)
            while (true) {
                val message = FakeOpencodeAcpServer.readJsonLine(input) ?: return
                check(!message.startsWith("Content-Length", ignoreCase = true)) {
                    "model loading must use SDK newline JSON transport"
                }
                when {
                    "\"method\":\"initialize\"" in message -> FakeOpencodeAcpServer.writeJsonLine(
                        output,
                        """
                        {"jsonrpc":"2.0","id":${FakeOpencodeAcpServer.extractId(message)},"result":{"protocolVersion":1,"agentCapabilities":{},"agentInfo":{"name":"fake-agent","version":"0.0.1"}}}
                        """.trimIndent(),
                    )
                    "\"method\":\"session/new\"" in message -> {
                        FakeOpencodeAcpServer.writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":${FakeOpencodeAcpServer.extractId(message)},"result":{"sessionId":"test","configOptions":[{"type":"select","id":"model","name":"Model","category":"model","currentValue":"openai/gpt-5.1","options":[{"value":"openai/gpt-5.1","name":"GPT 5.1"},{"value":"anthropic/claude-sonnet-4-5","name":"Claude Sonnet 4.5"}]}]}}
                            """.trimIndent(),
                        )
                        return
                    }
                }
            }
        }

    }
}
