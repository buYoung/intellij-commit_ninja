package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.OptionTag

@Service
@State(
    name = "CommitNinjaCommitPromptSettings",
    storages = [Storage(value = "commit-ninja-commit-prompt.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS,
)
class CommitPromptSettings : SimplePersistentStateComponent<CommitPromptSettings.State>(State()) {
    class State : BaseState(), PromptState {
        override var userPrompt by string("")

        @get:OptionTag("promptInitialized")
        override var isPromptInitialized by property(false)

        var isPromptSyncEnabled by property(false)
    }

    var isPromptSyncEnabled: Boolean
        get() = state.isPromptSyncEnabled
        set(enabled) = updatePromptSyncEnabled(enabled)

    val resolvedUserPrompt: String
        get() {
            ensurePromptInitialized()
            return activePromptState().userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
        }

    fun setUserPrompt(prompt: String) {
        ensurePromptInitialized()
        val promptState = activePromptState()
        promptState.userPrompt = prompt
        promptState.isPromptInitialized = true
    }

    fun ensurePromptInitialized() {
        ensureLocalPromptInitialized()
        if (state.isPromptSyncEnabled) {
            val syncedState = CommitPromptSyncSettings.getInstance().state
            if (!syncedState.isPromptInitialized || syncedState.userPrompt.isNullOrBlank()) {
                syncedState.userPrompt = state.userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
                syncedState.isPromptInitialized = true
            }
        }
    }

    private fun updatePromptSyncEnabled(enabled: Boolean) {
        if (state.isPromptSyncEnabled == enabled) {
            ensurePromptInitialized()
            return
        }
        ensurePromptInitialized()
        if (enabled) {
            val syncedState = CommitPromptSyncSettings.getInstance().state
            syncedState.userPrompt = state.userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
            syncedState.isPromptInitialized = true
        } else {
            val syncedState = CommitPromptSyncSettings.getInstance().state
            state.userPrompt = syncedState.userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
            state.isPromptInitialized = true
        }
        state.isPromptSyncEnabled = enabled
    }

    private fun ensureLocalPromptInitialized() {
        if (state.isPromptInitialized && !state.userPrompt.isNullOrBlank()) return

        val legacyState = CommitGenerationSettings.getInstance().state
        state.userPrompt = legacyState.userPrompt.orEmpty().ifBlank { DefaultCommitPrompt.load() }
        state.isPromptInitialized = true
    }

    private fun activePromptState(): PromptState =
        if (state.isPromptSyncEnabled) CommitPromptSyncSettings.getInstance().state else state

    companion object {
        fun getInstance(): CommitPromptSettings =
            ApplicationManager.getApplication().getService(CommitPromptSettings::class.java)
    }
}

internal interface PromptState {
    var userPrompt: String?
    var isPromptInitialized: Boolean
}

@State(
    name = "CommitNinjaCommitPromptSyncSettings",
    storages = [Storage("commit-ninja-commit-prompt-sync.xml")],
    category = SettingsCategory.PLUGINS,
)
class CommitPromptSyncSettings : SimplePersistentStateComponent<CommitPromptSyncSettings.State>(State()) {
    class State : BaseState(), PromptState {
        override var userPrompt by string("")

        @get:OptionTag("promptInitialized")
        override var isPromptInitialized by property(false)
    }

    companion object {
        fun getInstance(): CommitPromptSyncSettings =
            ApplicationManager.getApplication().getService(CommitPromptSyncSettings::class.java)
    }
}
