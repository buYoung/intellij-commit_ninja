package com.livteam.commitninja.vcs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.livteam.commitninja.generation.CheckedChangeContext

class CheckedCommitChangesProvider {
    fun hasCheckedChanges(event: AnActionEvent): Boolean =
        !event.getData(VcsDataKeys.CHANGES).isNullOrEmpty()

    fun collect(event: AnActionEvent): List<CheckedChangeContext> {
        val changes = event.getData(VcsDataKeys.CHANGES)
        if (!changes.isNullOrEmpty()) {
            val contexts = mutableListOf<CheckedChangeContext>()
            var remainingDetailChars = MAX_COLLECTED_CHANGE_DETAIL_CHARS
            for (change in changes) {
                val context = change.toContext(remainingDetailChars)
                contexts += context
                if (!context.isDetailOmitted) {
                    remainingDetailChars = (remainingDetailChars - context.detail.length).coerceAtLeast(0)
                } else {
                    remainingDetailChars = 0
                }
            }
            return contexts
        }
        return emptyList()
    }

    fun currentBranchName(project: Project): String? =
        currentGitBranchName(project)
            ?: ProjectLevelVcsManager.getInstance(project).allActiveVcss.firstOrNull()?.displayName

    private fun Change.toContext(remainingDetailChars: Int): CheckedChangeContext {
        val filePath = afterRevision?.file ?: beforeRevision?.file
        val path = filePath?.path ?: virtualFile?.path ?: toString()
        val status = type.name
        if (remainingDetailChars <= 0) {
            return CheckedChangeContext(path = path, status = status, detail = DETAIL_OMITTED_MESSAGE, isDetailOmitted = true)
        }
        val detail = buildContentContext(remainingDetailChars)
        return CheckedChangeContext(path = path, status = status, detail = detail.text, isDetailOmitted = detail.isOmitted)
    }

    private fun Change.buildContentContext(detailBudgetChars: Int): CollectedDetail {
        val detail = StringBuilder()
        var remainingChars = detailBudgetChars
        var isOmitted = false

        fun appendWithinBudget(text: String) {
            if (remainingChars <= 0) {
                isOmitted = true
                return
            }
            val boundedText = text.take(remainingChars)
            detail.append(boundedText)
            remainingChars -= boundedText.length
            if (boundedText.length < text.length) {
                isOmitted = true
            }
        }

        fun appendLineWithinBudget(line: String = "") {
            appendWithinBudget("$line\n")
        }

        appendLineWithinBudget("Before content:")
        if (remainingChars > 0) {
            appendLineWithinBudget(beforeRevision.readContentOrPlaceholder(remainingChars))
        } else {
            isOmitted = true
        }
        appendLineWithinBudget()
        appendLineWithinBudget("After content:")
        if (remainingChars > 0) {
            appendLineWithinBudget(afterRevision.readContentOrPlaceholder(remainingChars))
        } else {
            isOmitted = true
        }

        if (isOmitted) {
            detail.appendLine()
            detail.appendLine(DETAIL_OMITTED_MESSAGE)
        }
        return CollectedDetail(text = detail.toString(), isOmitted = isOmitted)
    }

    private fun ContentRevision?.readContentOrPlaceholder(remainingDetailChars: Int): String {
        if (this == null) return "<not available>"
        return try {
            content?.limitRevisionContent(remainingDetailChars) ?: "<binary or empty content>"
        } catch (_: VcsException) {
            "<content unavailable>"
        }
    }

    private fun String.limitRevisionContent(remainingDetailChars: Int): String {
        val revisionLimit = minOf(MAX_REVISION_CONTENT_CHARS, remainingDetailChars)
        if (length <= revisionLimit) return this
        return take(revisionLimit) + "\n<revision content truncated at $revisionLimit chars>"
    }

    private fun currentGitBranchName(project: Project): String? {
        return try {
            val managerClass = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstance = managerClass.methods.firstOrNull {
                it.name == "getInstance" && it.parameterTypes.contentEquals(arrayOf(Project::class.java))
            } ?: return null
            val manager = getInstance.invoke(null, project)
            val repositories = managerClass.methods.firstOrNull { it.name == "getRepositories" && it.parameterCount == 0 }
                ?.invoke(manager) as? Collection<*>
            val repository = repositories?.firstOrNull() ?: return null
            repository.javaClass.methods.firstOrNull { it.name == "getCurrentBranchName" && it.parameterCount == 0 }
                ?.invoke(repository)
                ?.toString()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private companion object {
        const val MAX_REVISION_CONTENT_CHARS = 12_000
        const val MAX_COLLECTED_CHANGE_DETAIL_CHARS = 60_000
        const val DETAIL_OMITTED_MESSAGE = "<checked-change detail omitted because the collection detail budget was exhausted>"
    }

    private data class CollectedDetail(
        val text: String,
        val isOmitted: Boolean,
    )
}
