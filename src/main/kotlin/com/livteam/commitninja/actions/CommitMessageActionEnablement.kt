package com.livteam.commitninja.actions

import com.livteam.commitninja.settings.SettingsConfigurationReason

data class CommitMessageActionEnablementInput(
    val hasProject: Boolean,
    val isProjectDisposed: Boolean,
    val hasCommitMessageControl: Boolean,
    val hasCommitWorkflowUi: Boolean?,
    val isConfigured: Boolean,
    val settingsConfigurationReason: SettingsConfigurationReason?,
    val settingsProfileName: String,
    val settingsHasGenerationCommand: Boolean,
    val settingsHasModelLoadCommand: Boolean,
    val settingsHasSelectedModel: Boolean,
    val isGenerating: Boolean,
    val checkedChangeCount: Int?,
)

data class CommitMessageActionEnablementState(
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val disabledReason: CommitMessageActionDisabledReason?,
)

enum class CommitMessageActionDisabledReason {
    PROJECT_MISSING,
    PROJECT_DISPOSED,
    COMMIT_MESSAGE_CONTROL_MISSING,
    SETTINGS_MISSING,
    GENERATION_IN_PROGRESS,
}

object CommitMessageActionEnablement {
    fun evaluate(input: CommitMessageActionEnablementInput): CommitMessageActionEnablementState {
        val disabledReason = when {
            !input.hasProject -> CommitMessageActionDisabledReason.PROJECT_MISSING
            input.isProjectDisposed -> CommitMessageActionDisabledReason.PROJECT_DISPOSED
            !input.hasCommitMessageControl -> CommitMessageActionDisabledReason.COMMIT_MESSAGE_CONTROL_MISSING
            !input.isConfigured -> CommitMessageActionDisabledReason.SETTINGS_MISSING
            input.isGenerating -> CommitMessageActionDisabledReason.GENERATION_IN_PROGRESS
            else -> null
        }
        return CommitMessageActionEnablementState(
            isVisible = input.hasCommitMessageControl,
            isEnabled = disabledReason == null,
            disabledReason = disabledReason,
        )
    }
}

internal object CommitMessageActionCheckedChangeDiagnostics {
    fun forUpdate(): Int? = null

    fun forInvocation(collectedChangeCount: Int): Int = collectedChangeCount
}

object CommitMessageActionDiagnostics {
    fun formatState(
        entryPoint: String,
        input: CommitMessageActionEnablementInput,
        state: CommitMessageActionEnablementState,
    ): String =
        "Commit Ninja diagnostic: actionState " +
            "entryPoint=$entryPoint " +
            "projectPresent=${input.hasProject} projectDisposed=${input.isProjectDisposed} " +
            "settingsConfigured=${input.isConfigured} " +
            "settingsConfigurationReason=${input.settingsConfigurationReason ?: "NONE"} " +
            "settingsProfile=${input.settingsProfileName} " +
            "settingsHasGenerationCommand=${input.settingsHasGenerationCommand} " +
            "settingsHasModelLoadCommand=${input.settingsHasModelLoadCommand} " +
            "settingsHasSelectedModel=${input.settingsHasSelectedModel} " +
            "checkedChangeCount=${input.checkedChangeCount} " +
            "commitWorkflowUiMissing=${input.hasCommitWorkflowUi?.not() ?: "UNKNOWN"} " +
            "commitMessageControlMissing=${!input.hasCommitMessageControl} " +
            "visible=${state.isVisible} enabled=${state.isEnabled} " +
            "disabledReason=${state.disabledReason ?: "NONE"}"
}

class CommitMessageActionDiagnosticThrottle(
    private val minimumIntervalMs: Long,
) {
    private var lastMessage: String? = null
    private var lastLoggedAtMs: Long = Long.MIN_VALUE

    fun shouldLog(message: String, nowMs: Long): Boolean {
        val previousMessage = lastMessage
        if (previousMessage != message || nowMs - lastLoggedAtMs >= minimumIntervalMs) {
            lastMessage = message
            lastLoggedAtMs = nowMs
            return true
        }
        return false
    }
}
