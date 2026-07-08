package com.livteam.commitninja.acp.profile

import com.livteam.commitninja.acp.AcpModelOptionsLoader

object AcpBuiltInProfiles {
    val NONE = AcpAgentProfile(
        id = "none",
        displayName = "Not configured",
        generationCommand = "",
        generationArguments = "",
        modelProvider = AcpModelProvider.None,
    )

    val OPENCODE = AcpAgentProfile(
        id = "opencode",
        displayName = "opencode",
        generationCommand = "opencode",
        generationArguments = "acp",
        modelArguments = "models",
        modelProvider = AcpModelProvider.Command(AcpModelOptionsLoader::loadOpencodeModels),
    )

    val CLAUDE_AGENT_ACP = AcpAgentProfile(
        id = "claude-agent-acp",
        displayName = "Claude",
        generationCommand = "npx",
        generationArguments = "-y @zed-industries/claude-agent-acp",
        modelProvider = AcpModelProvider.BuiltIn(listOf("default", "opus", "sonnet", "haiku")),
    )

    val CODEX_ACP = AcpAgentProfile(
        id = "codex-acp",
        displayName = "Codex",
        generationCommand = "npx",
        generationArguments = "-y @zed-industries/codex-acp",
        modelCommand = "codex",
        modelArguments = "debug models --bundled",
        modelProvider = AcpModelProvider.Command(AcpModelOptionsLoader::loadCodexBundledModels),
    )

    val JUNIE_ACP = AcpAgentProfile(
        id = "junie-acp",
        displayName = "Junie",
        generationCommand = "junie",
        generationArguments = "--acp true",
        modelProvider = AcpModelProvider.None,
    )
}
