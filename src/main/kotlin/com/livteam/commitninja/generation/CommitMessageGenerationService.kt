package com.livteam.commitninja.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.livteam.commitninja.acp.AcpClient
import com.livteam.commitninja.settings.AgentCommandLine
import com.livteam.commitninja.settings.CommitChangeCollectionSettings
import com.livteam.commitninja.settings.CommitGenerationSettings
import com.livteam.commitninja.settings.CommitPromptSettings

@Service(Service.Level.PROJECT)
class CommitMessageGenerationService(private val project: Project) {
    private var acpGenerate: (CommitMessageGenerationRequest, String) -> CommitMessageGenerationResult =
        { request, prompt -> AcpClient(project).generate(request, prompt) }

    internal constructor(
        project: Project,
        acpGenerate: (CommitMessageGenerationRequest, String) -> CommitMessageGenerationResult,
    ) : this(project) {
        this.acpGenerate = acpGenerate
    }

    fun generate(request: CommitMessageGenerationRequest): CommitMessageGenerationResult {
        if (request.changes.isEmpty()) {
            LOG.warn("Commit message generation rejected: no checked changes")
            return CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.NO_CHECKED_CHANGES, "No checked changes were supplied."),
            )
        }
        val maxCommitListSize = request.maxCommitListSize
        if (maxCommitListSize != null && request.changes.size > maxCommitListSize) {
            val diagnosticMessage =
                "Checked change count ${request.changes.size} exceeds maximum checked changes $maxCommitListSize."
            LOG.warn(
                "Commit message generation rejected: type=${GenerationFailureType.COMMIT_LIST_TOO_LARGE}, " +
                    "checkedChangeCount=${request.changes.size}, maxCommitListSize=$maxCommitListSize",
            )
            return CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.COMMIT_LIST_TOO_LARGE, diagnosticMessage),
            )
        }
        LOG.info(
            "Starting commit message generation: profile=${request.profileId}, model=${request.model.orEmpty()}, branch=${request.branchName.orEmpty()}, checkedChangeCount=${request.changes.size}, maxCommitListSize=${request.maxCommitListSize ?: "unlimited"}",
        )
        val prompt = buildPrompt(request)
        LOG.info(
            "Built commit generation prompt: promptChars=${prompt.length}, changePaths=${request.changes.joinToString(",") { it.path }}",
        )
        LOG.debug("Commit generation input prompt:\n$prompt")
        val result = acpGenerate(request, prompt)
        when (result) {
            is CommitMessageGenerationResult.Success -> LOG.info("Commit message generation succeeded: messageChars=${result.message.length}")
            is CommitMessageGenerationResult.Failure -> LOG.warn(
                "Commit message generation failed: type=${result.diagnostic.type}, diagnostic=${result.diagnostic.message}, ${failureInputDiagnostic(request, prompt)}",
            )
        }
        return result
    }

    fun requestFromSettings(changes: List<CheckedChangeContext>, branchName: String?): CommitMessageGenerationResult? {
        val settings = CommitGenerationSettings.getInstance()
        val changeCollectionSettings = CommitChangeCollectionSettings.getInstance()
        val promptSettings = CommitPromptSettings.getInstance()
        val settingsDiagnostic = settings.configurationDiagnostic()
        if (!settingsDiagnostic.isConfigured) {
            LOG.warn(
                    "Commit message generation settings missing: " +
                    "reason=${settingsDiagnostic.reason}, profile=${settingsDiagnostic.profileId}, " +
                    "hasGenerationCommand=${settingsDiagnostic.hasGenerationCommand}, " +
                    "hasModelLoadCommand=${settingsDiagnostic.hasModelLoadCommand}, " +
                    "hasSelectedModel=${settingsDiagnostic.hasSelectedModel}",
            )
            return CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.SETTINGS_MISSING, "ACP agent settings are incomplete."),
            )
        }
        val request = CommitMessageGenerationRequest(
            profileId = settingsDiagnostic.profileId,
            profileDisplayName = settingsDiagnostic.profileDisplayName,
            command = settings.resolvedCommand,
            arguments = AgentCommandLine.splitArguments(settings.resolvedArguments),
            model = settings.state.model?.takeIf { it.isNotBlank() },
            userPrompt = promptSettings.resolvedUserPrompt,
            languagePromptInstruction = settings.languagePromptInstruction,
            branchName = branchName,
            changes = changes,
            workingDirectory = project.basePath,
            maxCommitListSize = changeCollectionSettings.resolvedMaxCommitListSize,
        )
        LOG.info(
            "Created commit generation request from settings: profile=${request.profileId}, model=${request.model.orEmpty()}, branch=${branchName.orEmpty()}, checkedChangeCount=${changes.size}, maxCommitListSize=${request.maxCommitListSize ?: "unlimited"}",
        )
        return generate(request)
    }

    private fun buildPrompt(request: CommitMessageGenerationRequest): String {
        val prompt = StringBuilder(MAX_COMMIT_PROMPT_CHARS.coerceAtMost(16_384))
        prompt.appendBoundedLine(request.userPrompt.withPromptPlaceholders(request))
        prompt.appendBoundedLine()
        prompt.appendBoundedLine("Return only the final commit message. Do not include analysis, reasoning, alternatives, labels, or markdown fences.")
        prompt.appendBoundedLine(
            "The first line must be a Conventional Commit header, for example: feat(scope): concise summary or fix(scope)",
        )
        prompt.appendBoundedLine("Always include a blank line and at least one numbered body item after the header.")
        prompt.appendBoundedLine("Never return only the Conventional Commit header.")
        prompt.appendBoundedLine()
        prompt.appendBoundedLine("## Commit Message")
        for (change in request.changes) {
            if (prompt.length >= MAX_COMMIT_PROMPT_CHARS) break
            prompt.appendBoundedLine("---")
            prompt.appendBoundedLine("Path: ${change.path}")
            prompt.appendBoundedLine("Status: ${change.status}")
            prompt.appendBoundedLine(change.detail.limitToPromptDetail())
        }
        return prompt.toString()
    }

    private fun String.withPromptPlaceholders(request: CommitMessageGenerationRequest): String {
        val languageInstruction = request.languagePromptInstruction?.trim().orEmpty()
        return replace(COMMIT_LANGUAGE_INSTRUCTION_PLACEHOLDER, languageInstruction)
            .replace(GIT_BRANCH_NAME_PLACEHOLDER, request.branchName.orEmpty())
            .replace(TICKET_ID_PLACEHOLDER, request.branchName.derivedTicketId().orEmpty())
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun StringBuilder.appendBoundedLine(line: String = "") {
        val remainingChars = MAX_COMMIT_PROMPT_CHARS - length
        if (remainingChars <= 0) return
        append("$line\n".take(remainingChars))
    }

    private fun String.limitToPromptDetail(): String {
        if (length <= MAX_CHANGE_DETAIL_CHARS) return this
        return take(MAX_CHANGE_DETAIL_CHARS) + "\n<change detail truncated at $MAX_CHANGE_DETAIL_CHARS chars>"
    }

    private fun failureInputDiagnostic(request: CommitMessageGenerationRequest, prompt: String): String = buildString {
        append("profile=")
        append(request.profileId)
        append(", model=")
        append(request.model.orEmpty())
        append(", branch=")
        append(request.branchName.orEmpty())
        append(", checkedChangeCount=")
        append(request.changes.size)
        append(", promptChars=")
        append(prompt.length)
        append(", changePaths=")
        append(request.changes.joinToString(",") { it.path }.take(MAX_FAILURE_DIAGNOSTIC_CHARS))
        append("\ninput prompt:\n")
        append(prompt.boundedFailurePreview())
    }

    private fun String?.derivedTicketId(): String? {
        val branchName = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (branchName.lowercase() in RESERVED_BRANCH_NAMES) return null
        return branchName
    }

    private fun String.boundedFailurePreview(): String {
        val normalized = replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        val preview = normalized.take(MAX_FAILURE_DIAGNOSTIC_CHARS)
        val suffix = if (normalized.length > MAX_FAILURE_DIAGNOSTIC_CHARS) "...<truncated>" else ""
        return preview + suffix
    }

    private companion object {
        val LOG = Logger.getInstance(CommitMessageGenerationService::class.java)
        const val MAX_COMMIT_PROMPT_CHARS = 200_000
        const val MAX_CHANGE_DETAIL_CHARS = 200_000
        const val MAX_FAILURE_DIAGNOSTIC_CHARS = 2_000
        const val COMMIT_LANGUAGE_INSTRUCTION_PLACEHOLDER = "\$COMMIT_LANGUAGE_INSTRUCTION"
        const val GIT_BRANCH_NAME_PLACEHOLDER = "\$GIT_BRANCH_NAME"
        const val TICKET_ID_PLACEHOLDER = "\$TICKET_ID"
        val RESERVED_BRANCH_NAMES = setOf("main", "develop", "master", "staging")
    }
}
