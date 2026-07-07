package com.livteam.commitninja.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.livteam.commitninja.acp.AcpClient
import com.livteam.commitninja.settings.AgentCommandLine
import com.livteam.commitninja.settings.CommitGenerationSettings

@Service(Service.Level.PROJECT)
class CommitMessageGenerationService(private val project: Project) {
    fun generate(request: CommitMessageGenerationRequest): CommitMessageGenerationResult {
        if (request.changes.isEmpty()) {
            return CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.NO_CHECKED_CHANGES, "No checked changes were supplied."),
            )
        }
        val prompt = buildPrompt(request)
        return AcpClient(project).generate(request, prompt)
    }

    fun requestFromSettings(changes: List<CheckedChangeContext>, branchName: String?): CommitMessageGenerationResult? {
        val settings = CommitGenerationSettings.getInstance()
        if (!settings.isConfigured()) {
            return CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.SETTINGS_MISSING, "ACP agent settings are incomplete."),
            )
        }
        val request = CommitMessageGenerationRequest(
            profile = settings.profile,
            command = settings.resolvedCommand,
            arguments = AgentCommandLine.splitArguments(settings.resolvedArguments),
            model = settings.state.model?.takeIf { it.isNotBlank() },
            userPrompt = settings.resolvedUserPrompt,
            branchName = branchName,
            changes = changes,
            workingDirectory = project.basePath,
        )
        return generate(request)
    }

    private fun buildPrompt(request: CommitMessageGenerationRequest): String {
        val prompt = StringBuilder(MAX_COMMIT_PROMPT_CHARS.coerceAtMost(16_384))
        prompt.appendBoundedLine(request.userPrompt.trim())
        prompt.appendBoundedLine()
        prompt.appendBoundedLine("Return only the final commit message. Do not include analysis, reasoning, alternatives, labels, or markdown fences.")
        prompt.appendBoundedLine("The first line must be a Conventional Commit header, for example: feat(scope): concise summary")
        prompt.appendBoundedLine()
        prompt.appendBoundedLine("GIT_BRANCH_NAME=${request.branchName.orEmpty()}")
        request.model?.let {
            prompt.appendBoundedLine("Preferred model: $it")
        }
        prompt.appendBoundedLine()
        prompt.appendBoundedLine("Checked changes:")
        val omittedDetailCount = request.changes.count { it.isDetailOmitted }
        if (omittedDetailCount > 0) {
            prompt.appendBoundedLine(
                "Checked-change detail omitted for $omittedDetailCount file(s) because the collection detail budget was exhausted.",
            )
        }
        for (change in request.changes) {
            if (prompt.length >= MAX_COMMIT_PROMPT_CHARS) break
            prompt.appendBoundedLine("---")
            prompt.appendBoundedLine("Path: ${change.path}")
            prompt.appendBoundedLine("Status: ${change.status}")
            prompt.appendBoundedLine(change.detail.limitToPromptDetail())
        }
        return prompt.toString()
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

    private companion object {
        const val MAX_COMMIT_PROMPT_CHARS = 80_000
        const val MAX_CHANGE_DETAIL_CHARS = 12_000
    }
}
