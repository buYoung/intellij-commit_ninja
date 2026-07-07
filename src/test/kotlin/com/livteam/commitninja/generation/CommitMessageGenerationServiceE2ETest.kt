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
            model = "ollama-cloud/deepseek-v4-pro",
            expectedMessage = "feat: update app output",
        )
    }

    fun testOpencodeNewlineJsonKeepsUtf8CommitMessage() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.OPENCODE,
            model = "ollama-cloud/deepseek-v4-pro",
            expectedMessage = "feat: 한글 커밋 메시지",
        )
    }

    fun testGeneratesCommitMessageThroughCodexLocalAcpBoundary() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.CODEX_ACP,
            model = "gpt-5.4-mini",
            expectedMessage = "feat: update app output",
        )
    }

    fun testGeneratesCommitMessageThroughClaudeLocalAcpBoundary() {
        assertGeneratesCommitMessageWithModel(
            profile = AgentProfile.CLAUDE_AGENT_ACP,
            model = "haiku",
            expectedMessage = "feat: update app output",
        )
    }

    fun testExplicitClaudeAcpLaunchFailureReportsUsefulDiagnostic() {
        val failingClaudeAdapter = Files.createTempFile("fake-custom-claude-acp", ".sh")
        failingClaudeAdapter.writeText(
            """
            #!/bin/sh
            printf 'custom Claude ACP adapter is not installed\n' >&2
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
        assertTrue(diagnostic.message, "custom Claude ACP adapter is not installed" in diagnostic.message)
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
            val expectedModel = args.getOrNull(0) ?: error("expected model argument is required")
            val commitMessage = args.getOrNull(1) ?: "feat: update app output"
            var sawCheckedChange = false
            while (true) {
                val message = readJsonLine(input) ?: return
                val id = requestId(message) ?: continue
                when (requestMethod(message)) {
                    "initialize" -> writeMessage(
                        output,
                        """
                        {"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":1,"agentCapabilities":{}}}
                        """.trimIndent(),
                    )
                    "session/new" -> writeMessage(
                        output,
                        """
                        {"jsonrpc":"2.0","id":$id,"result":{"sessionId":"commit-e2e","configOptions":[{"type":"select","id":"model","name":"Model","category":"model","currentValue":"$expectedModel","options":[{"value":"ollama-cloud/deepseek-v4-pro","name":"ollama-cloud/deepseek-v4-pro"},{"value":"gpt-5.4-mini","name":"gpt-5.4-mini"},{"value":"haiku","name":"haiku"}]}]}}
                        """.trimIndent(),
                    )
                    "session/set_config_option" -> {
                        check("\"method\":\"session/set_config_option\"" in message) {
                            "generation must set the requested model through session/set_config_option"
                        }
                        check("\"value\":\"$expectedModel\"" in message) {
                            "generation must pass requested model $expectedModel"
                        }
                        writeMessage(
                            output,
                            """
                            {"jsonrpc":"2.0","id":$id,"result":{"configOptions":[]}}
                            """.trimIndent(),
                        )
                    }
                    "session/prompt" -> {
                        sawCheckedChange = "src/main/kotlin/App.kt" in message &&
                            "Write a concise Conventional Commit message." in message
                        writeMessage(
                            output,
                            if (sawCheckedChange) {
                                """
                                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"commit-e2e","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"${jsonString(commitMessage)}"}}}}
                                """.trimIndent()
                            } else {
                                """
                                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"commit-e2e","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"invalid"}}}}
                                """.trimIndent()
                            },
                        )
                        writeMessage(
                            output,
                            """
                            {"jsonrpc":"2.0","id":$id,"result":{"stopReason":"end_turn"}}
                            """.trimIndent(),
                        )
                        return
                    }
                }
            }
        }

        private fun requestMethod(message: String): String? =
            Regex(""""method":"([^"]+)"""").find(message)?.groupValues?.get(1)

        private fun requestId(message: String): String? =
            Regex(""""id":("[^"]+"|\d+)""").find(message)?.groupValues?.get(1)

        private fun jsonString(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")

        private fun readJsonLine(input: BufferedInputStream): String? {
            val bytes = mutableListOf<Byte>()
            while (true) {
                val next = input.read()
                if (next == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.UTF_8)
                if (next == '\n'.code) {
                    val line = String(bytes.toByteArray(), StandardCharsets.UTF_8)
                    check(!line.startsWith("Content-Length", ignoreCase = true)) {
                        "all ACP generation profiles must use the shared SDK stdio transport"
                    }
                    return line
                }
                bytes.add(next.toByte())
            }
        }

        private fun writeMessage(output: BufferedOutputStream, json: String) {
            output.write(json.toByteArray(StandardCharsets.UTF_8))
            output.write("\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }
    }
}
