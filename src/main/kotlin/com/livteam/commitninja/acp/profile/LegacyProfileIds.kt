package com.livteam.commitninja.acp.profile

object LegacyProfileIds {
    private val enumNameToStableId = mapOf(
        "NONE" to AcpBuiltInProfiles.NONE.id,
        "OPENCODE" to AcpBuiltInProfiles.OPENCODE.id,
        "CLAUDE_AGENT_ACP" to AcpBuiltInProfiles.CLAUDE_AGENT_ACP.id,
        "CODEX_ACP" to AcpBuiltInProfiles.CODEX_ACP.id,
        "JUNIE_ACP" to AcpBuiltInProfiles.JUNIE_ACP.id,
    )

    fun toStableId(storedId: String?): String? {
        val value = storedId?.takeIf { it.isNotBlank() } ?: return AcpBuiltInProfiles.NONE.id
        return enumNameToStableId[value] ?: value
    }
}
