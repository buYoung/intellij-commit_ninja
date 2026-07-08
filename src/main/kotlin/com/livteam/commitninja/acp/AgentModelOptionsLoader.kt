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
            AgentProfile.OPENCODE -> AcpModelOptionsLoader.loadOpencodeModels(command, workingDirectory)
            AgentProfile.CODEX_ACP -> AcpModelOptionsLoader.loadCodexBundledModels(command, workingDirectory)
            AgentProfile.CLAUDE_AGENT_ACP -> AcpModelOptionsLoader.loadClaudeBuiltInModels()
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

    private val LOG = Logger.getInstance(AgentModelOptionsLoader::class.java)
}
