package com.livteam.commitninja.acp

import com.livteam.commitninja.settings.AgentProfile
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
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
        return when (profile) {
            AgentProfile.OPENCODE -> loadOpencodeModels(command, arguments, workingDirectory)
            AgentProfile.CODEX_ACP -> loadCodexModels(command, workingDirectory)
            AgentProfile.CLAUDE_AGENT_ACP -> Result.success(CLAUDE_MODEL_CHOICES)
            AgentProfile.NONE -> Result.success(emptyList())
        }
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
        return acpModels.recoverCatching { emptyList() }
    }

    private fun loadCodexModels(command: String, workingDirectory: String?): Result<List<String>> {
        val catalog = runCommand(codexModelDiscoveryCommand(command), listOf("debug", "models"), workingDirectory)
        return catalog.mapCatching(::extractCodexModelSlugs)
    }

    fun codexModelDiscoveryCommand(generationCommand: String): String =
        if (generationCommand == AgentProfile.CODEX_ACP.defaultCommand) CODEX_MODEL_DISCOVERY_COMMAND else generationCommand

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
        val process = try {
            ProcessBuilder(listOf(command) + arguments)
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
            Result.success(process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
        } finally {
            process.destroy()
        }
    }

    private fun extractCodexModelSlugs(output: String): List<String> {
        val root = JsonParser.parseString(output)
        return findJsonObjects(root)
            .asSequence()
            .mapNotNull { jsonObject -> jsonObject["slug"]?.asString ?: jsonObject["id"]?.asString }
            .filter { it.startsWith("gpt-") }
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

    private val CLAUDE_MODEL_CHOICES = listOf("default", "opus", "sonnet", "haiku")

    private const val CODEX_MODEL_DISCOVERY_COMMAND = "codex"
    private const val MODEL_LIST_TIMEOUT_SECONDS = 15L
}
