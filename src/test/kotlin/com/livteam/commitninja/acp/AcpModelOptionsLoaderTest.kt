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
    fun `opencode ACP model loader uses newline JSON and sends client version`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AcpModelOptionsLoader.load(
            javaExecutable,
            listOf("-cp", classpath, FakeOpencodeAcpServer::class.java.name),
            null,
            AcpModelOptionsLoader.Transport.NEWLINE_JSON,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `opencode ACP model loader preserves UTF-8 newline JSON option values`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AcpModelOptionsLoader.load(
            javaExecutable,
            listOf("-cp", classpath, FakeUtf8OpencodeAcpServer::class.java.name),
            null,
            AcpModelOptionsLoader.Transport.NEWLINE_JSON,
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
    fun `opencode model loader tries ACP newline JSON before cli models`() {
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
    fun `opencode model loader falls back to cli models when ACP config options are empty`() {
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

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("ollama-cloud/deepseek-v4-pro", "ollama-cloud/deepseek-v4-flash"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `codex model loader uses explicit ACP command configuration`() {
        val javaExecutable = javaExecutable()
        val classpath = System.getProperty("java.class.path")
        val result = AgentModelOptionsLoader.load(
            profile = AgentProfile.CODEX_ACP,
            command = javaExecutable,
            arguments = listOf("-cp", classpath, FakeAcpServer::class.java.name),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("openai/gpt-5.1", "anthropic/claude-sonnet-4-5"),
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
            arguments = listOf("-cp", classpath, FakeAcpServer::class.java.name),
            workingDirectory = null,
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString().orEmpty(), result.isSuccess)
        assertEquals(
            listOf("openai/gpt-5.1", "anthropic/claude-sonnet-4-5"),
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
                when {
                    "\"id\":1" in message -> {
                        check("\"clientInfo\"" in message && "\"version\"" in message) {
                            "initialize request must include clientInfo.version"
                        }
                        writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":1}}
                            """.trimIndent(),
                        )
                    }
                    "\"id\":2" in message -> {
                        writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":2,"result":{"sessionId":"test","configOptions":[{"id":"model","title":"Model","options":[{"value":"ollama-cloud/deepseek-v4-pro"},{"value":"ollama-cloud/deepseek-v4-flash"}]}]}}
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
    }

    object FakeUtf8OpencodeAcpServer {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = BufferedInputStream(System.`in`)
            val output = BufferedOutputStream(System.out)
            while (true) {
                val message = FakeOpencodeAcpServer.readJsonLine(input) ?: return
                when {
                    "\"id\":1" in message -> FakeOpencodeAcpServer.writeJsonLine(
                        output,
                        """
                        {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":1}}
                        """.trimIndent(),
                    )
                    "\"id\":2" in message -> {
                        FakeOpencodeAcpServer.writeJsonLine(
                            output,
                            """
                            {"jsonrpc":"2.0","id":2,"result":{"sessionId":"test","configOptions":[{"id":"model","title":"모델","options":[{"value":"ollama-cloud/한글-모델"},{"value":"로컬/테스트"}]}]}}
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
                val message = readMessage(input) ?: return
                when {
                    "\"id\":1" in message -> writeMessage(
                        output,
                        """
                        {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":1}}
                        """.trimIndent(),
                    )
                    "\"id\":2" in message -> {
                        writeMessage(
                            output,
                            """
                            {"jsonrpc":"2.0","id":2,"result":{"sessionId":"test","configOptions":[{"id":"model","title":"Model","options":[{"value":"openai/gpt-5.1"},{"value":"anthropic/claude-sonnet-4-5"}]}]}}
                            """.trimIndent(),
                        )
                        return
                    }
                }
            }
        }

        private fun readMessage(input: BufferedInputStream): String? {
            var contentLength: Int? = null
            while (true) {
                val line = readAsciiLine(input) ?: return null
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(separator + 1).trim().toIntOrNull()
                }
            }
            val bytes = input.readNBytes(contentLength ?: return null)
            return String(bytes, StandardCharsets.UTF_8)
        }

        private fun readAsciiLine(input: BufferedInputStream): String? {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val next = input.read()
                if (next == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
                if (next == '\n'.code) {
                    if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                    return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
                }
                bytes.add(next.toByte())
            }
        }

        private fun writeMessage(output: BufferedOutputStream, json: String) {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }
    }
}
