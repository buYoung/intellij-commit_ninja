package com.livteam.commitninja.generation

import com.livteam.commitninja.settings.AgentProfile

data class CheckedChangeContext(
    val path: String,
    val status: String,
    val detail: String,
    val isDetailOmitted: Boolean = false,
)

data class CommitMessageGenerationRequest(
    val profileId: String,
    val profileDisplayName: String,
    val command: String,
    val arguments: List<String>,
    val model: String?,
    val userPrompt: String,
    val branchName: String?,
    val changes: List<CheckedChangeContext>,
    val workingDirectory: String?,
    val languagePromptInstruction: String? = null,
    val maxCommitListSize: Int? = null,
) {
    constructor(
        profile: AgentProfile,
        command: String,
        arguments: List<String>,
        model: String?,
        userPrompt: String,
        branchName: String?,
        changes: List<CheckedChangeContext>,
        workingDirectory: String?,
        languagePromptInstruction: String? = null,
        maxCommitListSize: Int? = null,
    ) : this(
        profileId = profile.profileId,
        profileDisplayName = profile.displayName,
        command = command,
        arguments = arguments,
        model = model,
        userPrompt = userPrompt,
        branchName = branchName,
        changes = changes,
        workingDirectory = workingDirectory,
        languagePromptInstruction = languagePromptInstruction,
        maxCommitListSize = maxCommitListSize,
    )

    @Deprecated("Use profileId and profileDisplayName for generation diagnostics.")
    val profile: AgentProfile
        get() = AgentProfile.fromStoredName(profileId)
}

sealed class CommitMessageGenerationResult {
    data class Success(val message: String) : CommitMessageGenerationResult()
    data class Failure(val diagnostic: GenerationDiagnostic) : CommitMessageGenerationResult()
}

data class GenerationDiagnostic(
    val type: GenerationFailureType,
    val message: String,
)

enum class GenerationFailureType {
    SETTINGS_MISSING,
    NO_CHECKED_CHANGES,
    COMMIT_LIST_TOO_LARGE,
    LAUNCH_FAILED,
    PROTOCOL_FAILED,
    TIMEOUT,
    CANCELLED,
    PARSE_FAILED,
    APPLY_FAILED,
}
