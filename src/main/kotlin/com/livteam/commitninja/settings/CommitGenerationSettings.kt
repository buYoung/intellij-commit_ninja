package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.OptionTag
import com.livteam.commitninja.acp.profile.AcpAgentProfile
import com.livteam.commitninja.acp.profile.AcpBuiltInProfiles
import com.livteam.commitninja.acp.profile.AcpProfileRegistry
import com.livteam.commitninja.acp.profile.LegacyProfileIds

@Service
@State(
    name = "CommitNinjaCommitGenerationSettings",
    storages = [Storage(value = "commit-ninja-commit-generation.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS,
)
class CommitGenerationSettings : SimplePersistentStateComponent<CommitGenerationSettings.State>(State()) {
    class State : BaseState() {
        var profileName by string(AcpBuiltInProfiles.NONE.id)
        var command by string("")
        var arguments by string("")
        var model by string("")
        var languageRegionName by string("")
        var userPrompt by string("")
        var confirmBeforeReplace by property(true)

        @get:OptionTag("promptInitialized")
        var isPromptInitialized by property(false)
    }

    val profile: AgentProfile
        get() = AgentProfile.fromStoredName(state.profileName)

    val profileId: String
        get() = LegacyProfileIds.toStableId(state.profileName).orEmpty()

    val profileDefinition: AcpAgentProfile?
        get() = AcpProfileRegistry.findByStoredId(state.profileName)

    val resolvedUserPrompt: String
        get() = CommitPromptSettings.getInstance().resolvedUserPrompt

    val resolvedCommand: String
        get() = state.command.orEmpty().ifBlank { profileDefinition?.generationCommand.orEmpty() }

    val resolvedArguments: String
        get() = state.arguments.orEmpty().ifBlank { profileDefinition?.generationArguments.orEmpty() }

    val languageRegion: CommitLanguageRegion?
        get() = CommitLanguageRegion.fromStoredName(state.languageRegionName)

    val languagePromptInstruction: String?
        get() = languageRegion?.promptInstruction

    fun isConfigured(): Boolean =
        configurationDiagnostic().isConfigured

    fun configurationDiagnostic(): SettingsConfigurationDiagnostic {
        val currentProfile = profileDefinition
        val currentProfileId = profileId
        val hasGenerationCommand = resolvedCommand.isNotBlank()
        val hasModelLoadCommand = state.command.orEmpty().isNotBlank() || currentProfile?.modelCommand.orEmpty().isNotBlank()
        val reason = when {
            currentProfile == null || currentProfile.id == AcpBuiltInProfiles.NONE.id -> SettingsConfigurationReason.PROFILE_MISSING
            !hasGenerationCommand -> SettingsConfigurationReason.GENERATION_COMMAND_MISSING
            else -> null
        }
        return SettingsConfigurationDiagnostic(
            isConfigured = reason == null,
            reason = reason,
            profileId = currentProfileId,
            profileDisplayName = currentProfile?.displayName ?: "Missing profile: $currentProfileId",
            profile = profile,
            hasGenerationCommand = hasGenerationCommand,
            hasModelLoadCommand = hasModelLoadCommand,
            canListModels = currentProfile?.modelProvider?.canListModels == true,
            hasSelectedModel = state.model.orEmpty().isNotBlank(),
        )
    }

    fun applyProfileDefaults(profile: AgentProfile) {
        state.profileName = profile.profileId
    }

    fun ensurePromptInitialized() {
        CommitPromptSettings.getInstance().ensurePromptInitialized()
    }

    companion object {
        fun getInstance(): CommitGenerationSettings =
            ApplicationManager.getApplication().getService(CommitGenerationSettings::class.java)
    }
}

data class SettingsConfigurationDiagnostic(
    val isConfigured: Boolean,
    val reason: SettingsConfigurationReason?,
    val profileId: String,
    val profileDisplayName: String,
    val profile: AgentProfile,
    val hasGenerationCommand: Boolean,
    val hasModelLoadCommand: Boolean,
    val canListModels: Boolean,
    val hasSelectedModel: Boolean,
)

enum class SettingsConfigurationReason {
    PROFILE_MISSING,
    GENERATION_COMMAND_MISSING,
}
