package com.livteam.commitninja.acp

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.livteam.commitninja.settings.AgentProfile
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object AgentModelOptionsLoader {
    fun load(
        profile: AgentProfile,
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> {
        LOG.info(
            "Starting ACP model load: profile=${profile.name}, command=$command, arguments=${arguments.joinToString(" ")}, workingDirectory=${workingDirectory.orEmpty()}",
        )
        val result = when (profile) {
            AgentProfile.OPENCODE -> loadOpencodeModels(command, arguments, workingDirectory)
            AgentProfile.CODEX_ACP -> loadCodexModels(command, workingDirectory)
            AgentProfile.CLAUDE_AGENT_ACP -> Result.success(CLAUDE_MODEL_CHOICES)
            AgentProfile.NONE -> Result.success(emptyList())
        }
        result.fold(
            onSuccess = { models ->
                LOG.info("Finished ACP model load: profile=${profile.name}, count=${models.size}")
            },
            onFailure = { exception ->
                LOG.warn(
                    "Failed ACP model load: profile=${profile.name}, command=$command, arguments=${arguments.joinToString(" ")}, workingDirectory=${workingDirectory.orEmpty()}",
                    exception,
                )
            },
        )
        return result
    }

    private fun loadOpencodeModels(command: String, arguments: List<String>, workingDirectory: String?): Result<List<String>> {
        val acpModels = AcpModelOptionsLoader.load(
            command,
            arguments,
            workingDirectory,
            AcpModelOptionsLoader.Transport.NEWLINE_JSON,
        )
        if (acpModels.isSuccess && acpModels.getOrThrow().isNotEmpty()) {
            return acpModels
        }
        val cliModels = loadCommandModels(command, listOf("models"), workingDirectory)
        if (cliModels.isSuccess && cliModels.getOrThrow().isNotEmpty()) {
            return cliModels
        }
        if (cliModels.isFailure) {
            return cliModels
        }
        return acpModels.recoverCatching { emptyList() }
    }

    private fun loadCodexModels(command: String, workingDirectory: String?): Result<List<String>> {
        val catalog = runCommand(codexModelDiscoveryCommand(command), listOf("debug", "models", "--bundled"), workingDirectory)
        return catalog.mapCatching(::extractCodexModelSlugs)
    }

    fun codexModelDiscoveryCommand(generationCommand: String): String {
        val command = generationCommand.trim()
        if (command == AgentProfile.CODEX_ACP.defaultCommand) {
            return CODEX_MODEL_DISCOVERY_COMMAND
        }
        val commandFile = File(command)
        if (commandFile.name == AgentProfile.CODEX_ACP.defaultCommand) {
            return commandFile.parentFile
                ?.resolve(CODEX_MODEL_DISCOVERY_COMMAND)
                ?.path
                ?: CODEX_MODEL_DISCOVERY_COMMAND
        }
        return command
    }

    private fun loadCommandModels(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> =
        runCommand(command, arguments, workingDirectory).map { output ->
            output.lineSequence()
                .map(String::trim)
                .filter(::isModelLine)
                .distinct()
                .toList()
        }

    private fun runCommand(command: String, arguments: List<String>, workingDirectory: String?): Result<String> {
        LOG.info("Starting ACP model command: command=${formatCommand(command, arguments)}, workingDirectory=${workingDirectory.orEmpty()}")
        val process = try {
            ProcessBuilder(listOf(command) + arguments)
                .directory(workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            LOG.warn("Could not start ACP model command: command=${formatCommand(command, arguments)}", exception)
            return Result.failure(exception)
        }

        val stdoutReader = process.readStreamAsync(process.inputStream)
        val stderrReader = process.readStreamAsync(process.errorStream)
        return try {
            val finished = process.waitFor(MODEL_LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutReader.join(MODEL_STREAM_JOIN_TIMEOUT_MILLIS)
                stderrReader.join(MODEL_STREAM_JOIN_TIMEOUT_MILLIS)
                LOG.warn("ACP model command timed out: command=${formatCommand(command, arguments)}, stderr=${stderrReader.output.trim().take(MAX_DIAGNOSTIC_CHARS)}")
                return Result.failure(TimeoutException("Model list command timed out: ${formatCommand(command, arguments)}"))
            }
            stdoutReader.join(MODEL_STREAM_JOIN_TIMEOUT_MILLIS)
            stderrReader.join(MODEL_STREAM_JOIN_TIMEOUT_MILLIS)
            val stderr = stderrReader.output.trim()
            if (process.exitValue() != 0) {
                LOG.warn(
                    "ACP model command failed: command=${formatCommand(command, arguments)}, exitCode=${process.exitValue()}, stderr=${stderr.take(MAX_DIAGNOSTIC_CHARS)}",
                )
                return Result.failure(
                    IllegalStateException(
                        buildString {
                            append("Model list command failed with exit code ${process.exitValue()}: ")
                            append(formatCommand(command, arguments))
                            if (stderr.isNotBlank()) {
                                append(". stderr: ")
                                append(stderr.take(MAX_DIAGNOSTIC_CHARS))
                            }
                        },
                    ),
                )
            }
            stdoutReader.exception?.let { return Result.failure(it) }
            stderrReader.exception?.let { return Result.failure(it) }
            LOG.info("ACP model command succeeded: command=${formatCommand(command, arguments)}, stdoutChars=${stdoutReader.output.length}")
            Result.success(stdoutReader.output)
        } finally {
            process.destroy()
        }
    }

    private fun Process.readStreamAsync(inputStream: InputStream): StreamReadTask {
        val task = StreamReadTask(inputStream)
        task.start()
        return task
    }

    private fun extractCodexModelSlugs(output: String): List<String> {
        val root = JsonParser.parseString(output)
        return findJsonObjects(root)
            .asSequence()
            .mapNotNull { jsonObject -> jsonObject["slug"]?.asString ?: jsonObject["id"]?.asString }
            .filter { it.startsWith("gpt-") }
            .filterNot { it == CODEX_AUTO_REVIEW_MODEL }
            .distinct()
            .toList()
    }

    private fun findJsonObjects(element: JsonElement?): List<com.google.gson.JsonObject> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonObject) {
            val jsonObject = element.asJsonObject
            return listOf(jsonObject) + jsonObject.entrySet().flatMap { findJsonObjects(it.value) }
        }
        if (element.isJsonArray) {
            return element.asJsonArray.flatMap { findJsonObjects(it) }
        }
        return emptyList()
    }

    private fun isModelLine(line: String): Boolean =
        line.isNotBlank() && "/" in line && !line.startsWith("#")

    private fun formatCommand(command: String, arguments: List<String>): String =
        (listOf(command) + arguments).joinToString(" ")

    private class StreamReadTask(private val inputStream: InputStream) : Thread("commit-ninja-model-stream-reader") {
        private val content = StringBuilder()
        @Volatile
        var exception: Exception? = null
            private set

        val output: String
            get() = content.toString()

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    val buffer = CharArray(STREAM_BUFFER_CHARS)
                    while (true) {
                        val readCount = reader.read(buffer)
                        if (readCount < 0) break
                        content.append(buffer, 0, readCount)
                    }
                }
            } catch (caught: Exception) {
                exception = caught
            }
        }
    }

    private val CLAUDE_MODEL_CHOICES = listOf("default", "opus", "sonnet", "haiku")

    private val LOG = Logger.getInstance(AgentModelOptionsLoader::class.java)

    private const val CODEX_MODEL_DISCOVERY_COMMAND = "codex"
    private const val CODEX_AUTO_REVIEW_MODEL = "codex-auto-review"
    private const val MODEL_LIST_TIMEOUT_SECONDS = 15L
    private const val MODEL_STREAM_JOIN_TIMEOUT_MILLIS = 1_000L
    private const val MAX_DIAGNOSTIC_CHARS = 1_000
    private const val STREAM_BUFFER_CHARS = 8_192
}
