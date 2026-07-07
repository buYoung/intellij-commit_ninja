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
        var userPrompt by string("")
        var confirmBeforeReplace by property(true)

        @get:OptionTag("promptInitialized")
        var isPromptInitialized by property(false)
    }

    val profile: AgentProfile
        get() = AgentProfile.fromStoredName(state.profileName)

    val resolvedUserPrompt: String
        get() {
            ensurePromptInitialized()
            return state.userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
        }

    val resolvedCommand: String
        get() = state.command.orEmpty().ifBlank { profile.defaultCommand }

    val resolvedArguments: String
        get() = state.arguments.orEmpty().ifBlank { profile.defaultArguments }

    fun isConfigured(): Boolean =
        profile != AgentProfile.NONE && resolvedCommand.isNotBlank()

    fun applyProfileDefaults(profile: AgentProfile) {
        state.profileName = profile.name
    }

    fun ensurePromptInitialized() {
        if (!state.isPromptInitialized || state.userPrompt.isNullOrBlank()) {
            state.userPrompt = DefaultCommitPrompt.load()
            state.isPromptInitialized = true
        }
    }

    companion object {
        fun getInstance(): CommitGenerationSettings =
            ApplicationManager.getApplication().getService(CommitGenerationSettings::class.java)
    }
}
