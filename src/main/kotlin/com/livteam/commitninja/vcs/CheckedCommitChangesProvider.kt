package com.livteam.commitninja.vcs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.livteam.commitninja.generation.CheckedChangeContext
import java.io.IOException
import java.nio.file.Files

class CheckedCommitChangesProvider {
    fun hasCheckedChanges(event: AnActionEvent): Boolean =
        hasCheckedChangesFromSources(
            actionChanges = event.getData(VcsDataKeys.CHANGES),
            includedChanges = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedChanges(),
            includedUnversionedFiles = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedUnversionedFiles(),
        )

    fun checkedChangeCount(event: AnActionEvent): Int? =
        checkedChangeCountFromSources(
            actionChanges = event.getData(VcsDataKeys.CHANGES),
            includedChanges = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedChanges(),
            includedUnversionedFiles = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedUnversionedFiles(),
        )

    fun collect(event: AnActionEvent): List<CheckedChangeContext> =
        collectFromSources(
            actionChanges = event.getData(VcsDataKeys.CHANGES),
            includedChanges = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedChanges(),
            includedUnversionedFiles = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)?.getIncludedUnversionedFiles(),
        )

    fun collectFromSources(
        actionChanges: Array<Change>?,
        includedChanges: List<Change>?,
        includedUnversionedFiles: List<FilePath>? = null,
    ): List<CheckedChangeContext> {
        val changes = selectedChanges(actionChanges, includedChanges, includedUnversionedFiles)
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

    fun hasCheckedChangesFromSources(
        actionChanges: Array<Change>?,
        includedChanges: List<Change>?,
        includedUnversionedFiles: List<FilePath>? = null,
    ): Boolean = !selectedChanges(actionChanges, includedChanges, includedUnversionedFiles).isNullOrEmpty()

    fun checkedChangeCountFromSources(
        actionChanges: Array<Change>?,
        includedChanges: List<Change>?,
        includedUnversionedFiles: List<FilePath>? = null,
    ): Int? = selectedChanges(actionChanges, includedChanges, includedUnversionedFiles)?.size

    private fun selectedChanges(
        actionChanges: Array<Change>?,
        includedChanges: List<Change>?,
        includedUnversionedFiles: List<FilePath>?,
    ): List<Change>? {
        if (includedChanges != null || includedUnversionedFiles != null) {
            return includedChanges.orEmpty() + includedUnversionedFiles.orEmpty().map { it.toNewChange() }
        }
        return actionChanges?.toList()
    }

    private fun FilePath.toNewChange(): Change =
        Change(null, UnversionedContentRevision(this))

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

    private class UnversionedContentRevision(private val filePath: FilePath) : ContentRevision {
        override fun getContent(): String =
            try {
                Files.readString(filePath.ioFile.toPath(), filePath.charset)
            } catch (exception: IOException) {
                throw VcsException(exception)
            }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
    }
}
