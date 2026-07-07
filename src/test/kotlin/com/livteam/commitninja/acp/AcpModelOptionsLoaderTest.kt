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
import kotlin.io.path.writeText

class AcpModelOptionsLoaderTest {
    @Test
    fun `loads model choices from ACP session config options`() {
        val javaExecutable = "${System.getProperty("java.home")}/bin/java"
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
    fun `opencode model loader falls back to cli models when ACP config options are empty`() {
        val fakeOpencode = Files.createTempFile("fake-opencode", ".sh")
        fakeOpencode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "models" ]; then
              printf 'openai/gpt-5.1\nanthropic/claude-sonnet-4-5\n'
              exit 0
            fi
            exit 0
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
            listOf("openai/gpt-5.1", "anthropic/claude-sonnet-4-5"),
            result.getOrThrow(),
        )
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
