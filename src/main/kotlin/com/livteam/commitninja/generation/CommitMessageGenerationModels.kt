package com.livteam.commitninja.generation

import com.livteam.commitninja.settings.AgentProfile

data class CheckedChangeContext(
    val path: String,
    val status: String,
    val detail: String,
    val isDetailOmitted: Boolean = false,
)

data class CommitMessageGenerationRequest(
    val profile: AgentProfile,
    val command: String,
    val arguments: List<String>,
    val model: String?,
    val userPrompt: String,
    val branchName: String?,
    val changes: List<CheckedChangeContext>,
    val workingDirectory: String?,
)

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
    LAUNCH_FAILED,
    PROTOCOL_FAILED,
    TIMEOUT,
    CANCELLED,
    PARSE_FAILED,
    APPLY_FAILED,
}
