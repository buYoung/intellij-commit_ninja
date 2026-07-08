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

@Service
@State(
    name = "CommitNinjaCommitGenerationSettings",
    storages = [Storage(value = "commit-ninja-commit-generation.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS,
)
class CommitGenerationSettings : SimplePersistentStateComponent<CommitGenerationSettings.State>(State()) {
    class State : BaseState() {
        var profileName by string(AgentProfile.NONE.name)
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

    val resolvedUserPrompt: String
        get() = CommitPromptSettings.getInstance().resolvedUserPrompt

    val resolvedCommand: String
        get() = state.command.orEmpty().ifBlank { profile.defaultCommand }

    val resolvedArguments: String
        get() = state.arguments.orEmpty().ifBlank { profile.defaultArguments }

    val languageRegion: CommitLanguageRegion?
        get() = CommitLanguageRegion.fromStoredName(state.languageRegionName)

    val languagePromptInstruction: String?
        get() = languageRegion?.promptInstruction

    fun isConfigured(): Boolean =
        configurationDiagnostic().isConfigured

    fun configurationDiagnostic(): SettingsConfigurationDiagnostic {
        val currentProfile = profile
        val hasGenerationCommand = resolvedCommand.isNotBlank()
        val hasModelLoadCommand = state.command.orEmpty().isNotBlank() || currentProfile.defaultModelCommand.isNotBlank()
        val reason = when {
            currentProfile == AgentProfile.NONE -> SettingsConfigurationReason.PROFILE_MISSING
            !hasGenerationCommand -> SettingsConfigurationReason.GENERATION_COMMAND_MISSING
            else -> null
        }
        return SettingsConfigurationDiagnostic(
            isConfigured = reason == null,
            reason = reason,
            profile = currentProfile,
            hasGenerationCommand = hasGenerationCommand,
            hasModelLoadCommand = hasModelLoadCommand,
            hasSelectedModel = state.model.orEmpty().isNotBlank(),
        )
    }

    fun applyProfileDefaults(profile: AgentProfile) {
        state.profileName = profile.name
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
    val profile: AgentProfile,
    val hasGenerationCommand: Boolean,
    val hasModelLoadCommand: Boolean,
    val hasSelectedModel: Boolean,
)

enum class SettingsConfigurationReason {
    PROFILE_MISSING,
    GENERATION_COMMAND_MISSING,
}
