package com.livteam.commitninja.acp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AcpModelOptionsLoader {
    fun loadOpencodeModels(
        command: String,
        workingDirectory: String?,
    ): Result<List<String>> = loadCommandModels(
        command = command,
        arguments = listOf("models"),
        workingDirectory = workingDirectory,
        parser = ::parseLineModels,
    )

    fun loadCodexBundledModels(
        command: String,
        workingDirectory: String?,
    ): Result<List<String>> = loadCommandModels(
        command = command,
        arguments = listOf("debug", "models", "--bundled"),
        workingDirectory = workingDirectory,
        parser = ::parseCodexBundledModels,
    )

    fun loadClaudeBuiltInModels(): Result<List<String>> = Result.success(CLAUDE_BUILT_IN_MODELS)

    private fun loadCommandModels(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
        parser: (String) -> List<String>,
    ): Result<List<String>> {
        if (command.isBlank()) {
            return Result.failure(IllegalStateException("Explicit model list command configuration is required for this profile."))
        }

        val fullCommand = listOf(command) + arguments
        synchronized(MODEL_LOAD_LOCK) {
            repeat(MODEL_LOAD_ATTEMPTS) { attemptIndex ->
                val result = runCommandModels(fullCommand, workingDirectory, parser)
                if (result.isSuccess || attemptIndex == MODEL_LOAD_ATTEMPTS - 1 || !result.isTransientDatabaseLockFailure()) {
                    return result
                }
                LOG.warn("Retrying ACP profile model load after transient database lock: command=${formatCommand(fullCommand)}")
                Thread.sleep(MODEL_LOAD_RETRY_DELAY_MILLIS)
            }
        }
        return Result.failure(IllegalStateException("ACP profile model load failed without a result: command=${formatCommand(fullCommand)}"))
    }

    private fun runCommandModels(
        fullCommand: List<String>,
        workingDirectory: String?,
        parser: (String) -> List<String>,
    ): Result<List<String>> {
        LOG.info(
            "Starting ACP profile model load: command=${formatCommand(fullCommand)}, workingDirectory=${workingDirectory.orEmpty()}",
        )
        val process = try {
            ProcessBuilder(fullCommand)
                .directory(workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            LOG.warn("Could not start ACP profile model load: command=${formatCommand(fullCommand)}", exception)
            return Result.failure(exception)
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        val stdoutFuture = executor.submit<String> {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.joinToString("\n")
            }
        }
        val stderrFuture = executor.submit<String> {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.joinToString("\n").take(MAX_DIAGNOSTIC_CHARS)
            }
        }

        return try {
            if (!process.waitFor(MODEL_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw TimeoutException("model command timed out")
            }

            val stderr = stderrFuture.diagnosticText()
            val stdout = stdoutFuture.get(1, TimeUnit.SECONDS)
            if (process.exitValue() != 0) {
                val diagnostic = commandFailureDiagnostic(fullCommand, process.exitValue(), stderr)
                LOG.warn(diagnostic)
                return Result.failure(IllegalStateException(diagnostic))
            }

            val models = parser(stdout)
            LOG.info("Loaded ACP profile model options: command=${formatCommand(fullCommand)}, count=${models.size}")
            Result.success(models)
        } catch (exception: TimeoutException) {
            process.destroyForcibly()
            val diagnostic = "ACP profile model load timed out: command=${formatCommand(fullCommand)}, stderr=${stderrFuture.diagnosticText()}"
            LOG.warn(diagnostic, exception)
            Result.failure(IllegalStateException(diagnostic, exception))
        } catch (exception: Exception) {
            val cause = exception.cause ?: exception
            val diagnostic = buildString {
                append("ACP profile model load failed: command=")
                append(formatCommand(fullCommand))
                append(", exitCode=")
                append(exitCodeIfAvailable(process))
                val stderr = stderrFuture.diagnosticText()
                if (stderr.isNotBlank()) {
                    append(", stderr=")
                    append(stderr)
                }
                append(". ")
                append(cause.message ?: cause.javaClass.simpleName)
            }
            LOG.warn(diagnostic, cause)
            Result.failure(IllegalStateException(diagnostic, cause))
        } finally {
            process.destroy()
            process.waitFor(1, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun Result<List<String>>.isTransientDatabaseLockFailure(): Boolean =
        exceptionOrNull()?.message?.contains("database is locked", ignoreCase = true) == true

    private fun parseLineModels(output: String): List<String> =
        output.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    private fun parseCodexBundledModels(output: String): List<String> {
        val root = Json.parseToJsonElement(output)
        return collectSlugOrIdValues(root)
            .map(String::trim)
            .filter { model -> model.startsWith("gpt-") }
            .filterNot(::isCodexAutoReviewModel)
            .distinct()
            .toList()
    }

    private fun isCodexAutoReviewModel(model: String): Boolean =
        model == "codex-auto-review" || model.endsWith("-codex-auto-review")

    private fun collectSlugOrIdValues(element: JsonElement): Sequence<String> = sequence {
        when (element) {
            is JsonObject -> {
                val slug = (element["slug"] as? JsonPrimitive)?.contentOrNull
                val id = (element["id"] as? JsonPrimitive)?.contentOrNull
                if (slug != null) yield(slug)
                if (id != null) yield(id)
                element.values.forEach { child -> yieldAll(collectSlugOrIdValues(child)) }
            }
            is JsonArray -> element.forEach { child -> yieldAll(collectSlugOrIdValues(child)) }
            else -> Unit
        }
    }

    private fun commandFailureDiagnostic(command: List<String>, exitCode: Int, stderr: String): String =
        buildString {
            append("ACP profile model load failed: command=")
            append(formatCommand(command))
            append(", exitCode=")
            append(exitCode)
            if (stderr.isNotBlank()) {
                append(", stderr=")
                append(stderr)
            }
        }

    private fun java.util.concurrent.Future<String>.diagnosticText(): String =
        runCatching { get(1, TimeUnit.SECONDS).trim().take(MAX_DIAGNOSTIC_CHARS) }.getOrDefault("")

    private fun exitCodeIfAvailable(process: Process): String =
        runCatching {
            if (process.isAlive) "running" else process.exitValue().toString()
        }.getOrDefault("unknown")

    private fun formatCommand(command: List<String>): String = command.joinToString(" ")

    private val LOG = Logger.getInstance(AcpModelOptionsLoader::class.java)
    private val MODEL_LOAD_LOCK = Any()
    private val CLAUDE_BUILT_IN_MODELS = listOf("default", "opus", "sonnet", "haiku")
    private const val MAX_DIAGNOSTIC_CHARS = 1_000
    private const val MODEL_LOAD_TIMEOUT_SECONDS = 15L
    private const val MODEL_LOAD_ATTEMPTS = 2
    private const val MODEL_LOAD_RETRY_DELAY_MILLIS = 250L
}
