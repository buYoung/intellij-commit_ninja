package com.livteam.commitninja.settings

enum class AgentProfile(val displayName: String, val defaultCommand: String, val defaultArguments: String) {
    NONE("Not configured", "", ""),
    OPENCODE("opencode", "opencode", "acp"),
    CLAUDE_AGENT_ACP("Claude", "", ""),
    CODEX_ACP("Codex", "", "");

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): AgentProfile =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
