package com.livteam.commitninja.acp.profile

object AcpProfileRegistry {
    val profiles: List<AcpAgentProfile> = listOf(
        AcpBuiltInProfiles.NONE,
        AcpBuiltInProfiles.OPENCODE,
        AcpBuiltInProfiles.CLAUDE_AGENT_ACP,
        AcpBuiltInProfiles.CODEX_ACP,
        AcpBuiltInProfiles.JUNIE_ACP,
    )

    fun findById(id: String?): AcpAgentProfile? =
        profiles.firstOrNull { profile -> profile.id == id }

    fun findByStoredId(storedId: String?): AcpAgentProfile? =
        findById(LegacyProfileIds.toStableId(storedId))

    fun requireById(id: String): AcpAgentProfile =
        findById(id) ?: error("Unknown ACP profile id: $id")
}
