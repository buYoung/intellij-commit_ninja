package com.livteam.commitninja.settings

enum class AgentProfile(
    val displayName: String,
    val defaultCommand: String,
    val defaultArguments: String,
    val defaultModelCommand: String = defaultCommand,
    val defaultModelArguments: String = "",
) {
    NONE("Not configured", "", ""),
    OPENCODE("opencode", "opencode", "acp", defaultModelArguments = "models"),
    CLAUDE_AGENT_ACP("Claude", "npx", "-y @zed-industries/claude-agent-acp"),
    CODEX_ACP("Codex", "npx", "-y @zed-industries/codex-acp", defaultModelCommand = "codex", defaultModelArguments = "debug models --bundled"),
    JUNIE_ACP("Junie", "junie", "--acp true");

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): AgentProfile =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
