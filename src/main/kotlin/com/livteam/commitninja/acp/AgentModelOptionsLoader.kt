package com.livteam.commitninja.acp

import com.intellij.openapi.diagnostic.Logger
import com.livteam.commitninja.settings.AgentProfile

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
            AgentProfile.OPENCODE,
            AgentProfile.CODEX_ACP,
            AgentProfile.CLAUDE_AGENT_ACP,
            -> loadConfiguredAcpModels(command, arguments, workingDirectory)
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

    private fun loadConfiguredAcpModels(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> {
        if (command.isBlank()) {
            return Result.failure(IllegalStateException("Explicit ACP command configuration is required for this profile."))
        }
        return AcpModelOptionsLoader.load(command, arguments, workingDirectory)
    }

    private fun formatCommand(command: String, arguments: List<String>): String =
        (listOf(command) + arguments).joinToString(" ")

    private val LOG = Logger.getInstance(AgentModelOptionsLoader::class.java)
}
