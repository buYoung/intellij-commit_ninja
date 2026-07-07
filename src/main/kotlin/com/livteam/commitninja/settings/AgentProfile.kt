package com.livteam.commitninja.settings

enum class AgentProfile(val displayName: String, val defaultCommand: String, val defaultArguments: String) {
    NONE("Not configured", "", ""),
    OPENCODE("opencode", "opencode", "acp"),
    CLAUDE_AGENT_ACP("Claude Agent ACP adapter", "claude-agent-acp", ""),
    CODEX_ACP("Codex ACP adapter", "codex-acp", "");

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): AgentProfile =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
