package com.livteam.commitninja.generation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.commitninja.settings.AgentProfile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText

class CommitMessageGenerationServiceE2ETest : BasePlatformTestCase() {
    fun testGeneratesCommitMessageThroughOpencodeLocalAcpBoundary() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.OPENCODE,
            transport = "newline-json",
            model = "ollama-cloud/deepseek-v4-pro",
            expectedMessage = "feat: update app output",
        )
    }

    fun testOpencodeNewlineJsonKeepsUtf8CommitMessage() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.OPENCODE,
            transport = "newline-json",
            model = "ollama-cloud/deepseek-v4-pro",
            expectedMessage = "feat: 한글 커밋 메시지",
        )
    }

    fun testGeneratesCommitMessageThroughCodexLocalAcpBoundary() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.CODEX_ACP,
            transport = "content-length",
            model = "gpt-5.4-mini",
            expectedMessage = "feat: update app output",
        )
    }

    fun testGeneratesCommitMessageThroughClaudeLocalAcpBoundary() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            transport = "content-length",
            model = "haiku",
            expectedMessage = "feat: update app output",
        )
    }

    fun testClaudeAdapterLaunchFailureReportsUsefulDiagnostic() {
        val failingClaudeAdapter = Files.createTempFile("fake-claude-agent-acp", ".sh")
        failingClaudeAdapter.writeText(
            """
            #!/bin/sh
            printf 'claude-agent-acp adapter is not installed\n' >&2
            exit 127
            """.trimIndent(),
        )
        failingClaudeAdapter.toFile().setExecutable(true)
        val request = CommitMessageGenerationRequest(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            command = failingClaudeAdapter.toAbsolutePath().toString(),
            arguments = emptyList(),
            model = "sonnet",
            userPrompt = "Write a concise Conventional Commit message.",
            branchName = "feature/acp-e2e",
            changes = listOf(
                CheckedChangeContext(
                    path = "src/main/kotlin/App.kt",
                    status = "MODIFIED",
                    detail = "+println(\"new\")",
                ),
            ),
            workingDirectory = System.getProperty("user.dir"),
        )

        val result = CommitMessageGenerationService(project).generate(request)

        assertTrue(result.toString(), result is CommitMessageGenerationResult.Failure)
        val diagnostic = (result as CommitMessageGenerationResult.Failure).diagnostic
        assertEquals(GenerationFailureType.PROTOCOL_FAILED, diagnostic.type)
        assertTrue(diagnostic.message, "claude-agent-acp adapter is not installed" in diagnostic.message)
        assertTrue(diagnostic.message, failingClaudeAdapter.fileName.toString() in diagnostic.message)
    }

    fun testAcpLaunchFailureReportsCommandDiagnostic() {
        val missingCommand = "/tmp/commit-ninja-missing-acp-${System.nanoTime()}"
        val request = CommitMessageGenerationRequest(
            profile = AgentProfile.CODEX_ACP,
            command = missingCommand,
            arguments = listOf("--stdio"),
            model = "gpt-5.4-mini",
            userPrompt = "Write a concise Conventional Commit message.",
            branchName = "feature/acp-e2e",
            changes = listOf(
                CheckedChangeContext(
                    path = "src/main/kotlin/App.kt",
                    status = "MODIFIED",
                    detail = "+println(\"new\")",
                ),
            ),
            workingDirectory = System.getProperty("user.dir"),
        )

        val result = CommitMessageGenerationService(project).generate(request)

        assertTrue(result.toString(), result is CommitMessageGenerationResult.Failure)
        val diagnostic = (result as CommitMessageGenerationResult.Failure).diagnostic
        assertEquals(GenerationFailureType.LAUNCH_FAILED, diagnostic.type)
        assertTrue(diagnostic.message, missingCommand in diagnostic.message)
        assertTrue(diagnostic.message, "--stdio" in diagnostic.message)
    }

    fun testAcpParseFailureReportsBoundedRawOutputDiagnostic() {
        val rawOutput = "I inspected the diff but cannot produce a conventional commit.\n" +
            "Reason: the response contains analysis instead of a final message.\n" +
            "diagnostic-marker-${"x".repeat(5_000)}"
        val request = CommitMessageGenerationRequest(
            profile = AgentProfile.CODEX_ACP,
            command = javaExecutable(),
            arguments = listOf(
                "-cp",
                System.getProperty("java.class.path"),
                FakeCommitMessageAcpServer::class.java.name,
                "content-length",
                "gpt-5.4-mini",
                rawOutput,
            ),
            model = "gpt-5.4-mini",
            userPrompt = "Write a concise Conventional Commit message.",
            branchName = "feature/acp-e2e",
            changes = listOf(
                CheckedChangeContext(
                    path = "src/main/kotlin/App.kt",
                    status = "MODIFIED",
                    detail = "+println(\"new\")",
                ),
            ),
            workingDirectory = System.getProperty("user.dir"),
        )

        val result = CommitMessageGenerationService(project).generate(request)

        assertTrue(result.toString(), result is CommitMessageGenerationResult.Failure)
        val diagnostic = (result as CommitMessageGenerationResult.Failure).diagnostic
        assertEquals(GenerationFailureType.PARSE_FAILED, diagnostic.type)
        assertTrue(diagnostic.message, "outputChars=" in diagnostic.message)
        assertTrue(diagnostic.message, "rawOutputPreview=" in diagnostic.message)
        assertTrue(diagnostic.message, "cannot produce a conventional commit" in diagnostic.message)
        assertTrue(diagnostic.message, "diagnostic-marker-" in diagnostic.message)
        assertTrue(diagnostic.message, diagnostic.message.length < 2_500)
    }

    private fun assertGeneratesCommitMessageWithModel(
        profile: AgentProfile,
        transport: String,
        model: String,
        expectedMessage: String,
    ) {
        val request = CommitMessageGenerationRequest(
            profile = profile,
            command = javaExecutable(),
            arguments = listOf(
                "-cp",
                System.getProperty("java.class.path"),
                FakeCommitMessageAcpServer::class.java.name,
                transport,
                model,
                expectedMessage,
            ),
            model = model,
            userPrompt = "Write a concise Conventional Commit message.",
            branchName = "feature/acp-e2e",
            changes = listOf(
                CheckedChangeContext(
                    path = "src/main/kotlin/App.kt",
                    status = "MODIFIED",
                    detail = """
                    @@
                    -println("old")
                    +println("new")
                    """.trimIndent(),
                ),
            ),
            workingDirectory = System.getProperty("user.dir"),
        )

        val result = CommitMessageGenerationService(project).generate(request)

        assertTrue(result.toString(), result is CommitMessageGenerationResult.Success)
        assertEquals(expectedMessage, (result as CommitMessageGenerationResult.Success).message)
    }

    private fun javaExecutable(): String {
        val javaHomeExecutable = System.getenv("JAVA_HOME")
            ?.let { Path.of(it, "bin", "java") }
            ?.takeIf(::isStableJavaExecutable)
        if (javaHomeExecutable != null) return javaHomeExecutable.toString()

        val currentProcessExecutable = ProcessHandle.current().info().command()
            .map(Path::of)
            .filter(::isStableJavaExecutable)
        if (currentProcessExecutable.isPresent) return currentProcessExecutable.get().toString()

        val systemJavaExecutable = Path.of("/usr/bin/java").takeIf(::isStableJavaExecutable)
        if (systemJavaExecutable != null) return systemJavaExecutable.toString()

        return "java"
    }

    private fun isStableJavaExecutable(path: Path): Boolean =
        path.exists() && ".gradle/caches" !in path.toString()

    object FakeCommitMessageAcpServer {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = BufferedInputStream(System.`in`)
            val output = BufferedOutputStream(System.out)
            val transport = args.getOrNull(0) ?: "content-length"
            val expectedModel = args.getOrNull(1) ?: error("expected model argument is required")
            val commitMessage = args.getOrNull(2) ?: "feat: update app output"
            var sawCheckedChange = false
            while (true) {
                val message = readMessage(input, transport) ?: return
                when {
                    "\"id\":1" in message -> writeMessage(
                        output,
                        """
                        {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":1,"configOptions":[{"id":"model","title":"Model","options":[{"value":"ollama-cloud/deepseek-v4-pro"},{"value":"gpt-5.4-mini"},{"value":"haiku"}]}]}}
                        """.trimIndent(),
                        transport,
                    )
                    "\"id\":2" in message -> writeMessage(
                        output,
                        """
                        {"jsonrpc":"2.0","id":2,"result":{"sessionId":"commit-e2e"}}
                        """.trimIndent(),
                        transport,
                    )
                    "\"id\":3" in message -> {
                        check("\"method\":\"session/set_config_option\"" in message) {
                            "generation must set the requested model through session/set_config_option"
                        }
                        check("\"value\":\"$expectedModel\"" in message) {
                            "generation must pass requested model $expectedModel"
                        }
                        writeMessage(
                            output,
                            """
                            {"jsonrpc":"2.0","id":3,"result":{}}
                            """.trimIndent(),
                            transport,
                        )
                    }
                    "\"id\":4" in message -> {
                        sawCheckedChange = "src/main/kotlin/App.kt" in message &&
                            "Write a concise Conventional Commit message." in message
                        writeMessage(
                            output,
                            if (sawCheckedChange) {
                                """
                                {"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"$commitMessage"}]}}
                                """.trimIndent()
                            } else {
                                """
                                {"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"invalid"}]}}
                                """.trimIndent()
                            },
                            transport,
                        )
                        return
                    }
                }
            }
        }

        private fun readMessage(input: BufferedInputStream, transport: String): String? {
            var contentLength: Int? = null
            while (true) {
                val line = readAsciiLine(input) ?: return null
                if (line.startsWith("Content-Length", ignoreCase = true)) {
                    check(transport == "content-length") {
                        "opencode generation must use newline-delimited JSON"
                    }
                }
                if (line.startsWith("{")) {
                    check(transport == "newline-json") {
                        "non-opencode generation must preserve Content-Length framing"
                    }
                    return line
                }
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(separator + 1).trim().toIntOrNull()
                }
            }
            check(transport == "content-length") {
                "opencode generation must use newline-delimited JSON"
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

        private fun writeMessage(output: BufferedOutputStream, json: String, transport: String) {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            if (transport == "newline-json") {
                output.write(bytes)
                output.write("\n".toByteArray(StandardCharsets.US_ASCII))
            } else {
                output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write(bytes)
            }
            output.flush()
        }
    }
}
