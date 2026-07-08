package com.livteam.commitninja.acp

import com.intellij.openapi.diagnostic.Logger
import com.livteam.commitninja.acp.profile.AcpAgentProfile
import com.livteam.commitninja.acp.profile.AcpModelProvider
import com.livteam.commitninja.settings.AgentProfile

object AgentModelOptionsLoader {
    fun load(
        profile: AcpAgentProfile,
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> {
        LOG.info(
            "Starting ACP model load: profile=${profile.id}, command=$command, arguments=${arguments.joinToString(" ")}, workingDirectory=${workingDirectory.orEmpty()}",
        )
        val result = when (val provider = profile.modelProvider) {
            is AcpModelProvider.Command -> {
                // Preserve the existing production contract: Opencode and Codex helpers keep their hard-coded model arguments.
                provider.load(command, workingDirectory)
            }
            is AcpModelProvider.BuiltIn -> Result.success(provider.models)
            AcpModelProvider.None -> Result.success(emptyList())
        }
        result.fold(
            onSuccess = { models ->
                LOG.info("Finished ACP model load: profile=${profile.id}, count=${models.size}")
            },
            onFailure = { exception ->
                LOG.warn(
                    "Failed ACP model load: profile=${profile.id}, command=$command, arguments=${arguments.joinToString(" ")}, workingDirectory=${workingDirectory.orEmpty()}",
                    exception,
                )
            },
        )
        return result
    }

    fun load(
        profile: AgentProfile,
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> = load(profile.profileDefinition, command, arguments, workingDirectory)

    private val LOG = Logger.getInstance(AgentModelOptionsLoader::class.java)
}
