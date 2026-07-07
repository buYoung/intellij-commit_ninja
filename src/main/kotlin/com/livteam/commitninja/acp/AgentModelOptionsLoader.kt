package com.livteam.commitninja.acp

import com.livteam.commitninja.settings.AgentProfile
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object AgentModelOptionsLoader {
    fun load(
        profile: AgentProfile,
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> {
        if (profile == AgentProfile.OPENCODE) {
            val cliModels = loadOpencodeModels(command, workingDirectory)
            if (cliModels.isSuccess && cliModels.getOrThrow().isNotEmpty()) {
                return cliModels
            }
        }

        val acpModels = AcpModelOptionsLoader.load(command, arguments, workingDirectory)
        if (acpModels.isSuccess && acpModels.getOrThrow().isNotEmpty()) {
            return acpModels
        }

        return acpModels.recoverCatching {
            emptyList()
        }
    }

    private fun loadOpencodeModels(command: String, workingDirectory: String?): Result<List<String>> {
        val process = try {
            ProcessBuilder(command, "models")
                .directory(workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            return Result.failure(exception)
        }

        return try {
            val finished = process.waitFor(MODEL_LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result.failure(TimeoutException("Model list command timed out."))
            }
            if (process.exitValue() != 0) {
                return Result.failure(IllegalStateException("Model list command failed."))
            }
            Result.success(
                process.inputStream.bufferedReader()
                    .readLines()
                    .asSequence()
                    .map(String::trim)
                    .filter(::isModelLine)
                    .distinct()
                    .toList(),
            )
        } finally {
            process.destroy()
        }
    }

    private fun isModelLine(line: String): Boolean =
        line.isNotBlank() && "/" in line && !line.startsWith("#")

    private const val MODEL_LIST_TIMEOUT_SECONDS = 15L
}
