package com.livteam.commitninja.settings

import com.livteam.commitninja.acp.profile.AcpAgentProfile
import com.livteam.commitninja.acp.profile.AcpBuiltInProfiles
import com.livteam.commitninja.acp.profile.AcpProfileRegistry
import com.livteam.commitninja.acp.profile.LegacyProfileIds

enum class AgentProfile(val profileId: String) {
    NONE(AcpBuiltInProfiles.NONE.id),
    OPENCODE(AcpBuiltInProfiles.OPENCODE.id),
    CLAUDE_AGENT_ACP(AcpBuiltInProfiles.CLAUDE_AGENT_ACP.id),
    CODEX_ACP(AcpBuiltInProfiles.CODEX_ACP.id),
    JUNIE_ACP(AcpBuiltInProfiles.JUNIE_ACP.id);

    val profileDefinition: AcpAgentProfile
        get() = AcpProfileRegistry.requireById(profileId)

    val displayName: String
        get() = profileDefinition.displayName

    val defaultCommand: String
        get() = profileDefinition.generationCommand

    val defaultArguments: String
        get() = profileDefinition.generationArguments

    val defaultModelCommand: String
        get() = profileDefinition.modelCommand

    val defaultModelArguments: String
        get() = profileDefinition.modelArguments

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): AgentProfile =
            entries.firstOrNull { it.name == name || it.profileId == LegacyProfileIds.toStableId(name) } ?: NONE
    }
}
