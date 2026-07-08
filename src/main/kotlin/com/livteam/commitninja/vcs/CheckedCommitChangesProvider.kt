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
import com.livteam.commitninja.settings.CommitChangeCollectionSettings
import com.livteam.commitninja.settings.CommitChangeCollectionSettings.PatchExcludedFileRegex
import java.io.IOException
import java.nio.file.Files

class CheckedCommitChangesProvider(
    private val patchExcludedFilePatternsProvider: () -> List<PatchExcludedFileRegex> = {
        CommitChangeCollectionSettings.getInstance().resolvedPatchExcludedFilePatterns
    },
) {
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
        val detail = buildPatchContext(path, remainingDetailChars)
        return CheckedChangeContext(path = path, status = status, detail = detail.text, isDetailOmitted = detail.isOmitted)
    }

    private fun Change.buildPatchContext(path: String, detailBudgetChars: Int): CollectedDetail {
        val patchExclusionReason = patchExclusionReason(path)
        if (patchExclusionReason != null) {
            return CollectedDetail(text = "<patch omitted: $patchExclusionReason>", isOmitted = false)
        }

        val beforeContent = beforeRevision?.readContentForPatch()
        val afterContent = afterRevision?.readContentForPatch()
        if (beforeContent == null && beforeRevision != null) {
            return CollectedDetail(text = "<patch omitted: before content is binary or unavailable>", isOmitted = false)
        }
        if (afterContent == null && afterRevision != null) {
            return CollectedDetail(text = "<patch omitted: after content is binary or unavailable>", isOmitted = false)
        }

        val beforeText = beforeContent.orEmpty()
        val afterText = afterContent.orEmpty()
        val sourceChars = beforeText.length + afterText.length
        if (sourceChars > MAX_PATCH_SOURCE_CHARS) {
            return CollectedDetail(
                text = "<patch omitted: combined before/after content is $sourceChars chars, limit is $MAX_PATCH_SOURCE_CHARS>",
                isOmitted = false,
            )
        }

        val beforeLines = beforeText.patchLines()
        val afterLines = afterText.patchLines()
        val sourceLines = beforeLines.size + afterLines.size
        if (sourceLines > MAX_PATCH_SOURCE_LINES) {
            return CollectedDetail(
                text = "<patch omitted: combined before/after content is $sourceLines lines, limit is $MAX_PATCH_SOURCE_LINES>",
                isOmitted = false,
            )
        }

        return buildBoundedPatchContext(
            patch = buildUnifiedPatch(
                path = path,
                beforeExists = beforeRevision != null,
                afterExists = afterRevision != null,
                beforeLines = beforeLines,
                afterLines = afterLines,
            ),
            detailBudgetChars = detailBudgetChars,
        )
    }

    private fun buildBoundedPatchContext(patch: String, detailBudgetChars: Int): CollectedDetail {
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

        appendWithinBudget(patch)

        if (isOmitted) {
            detail.appendLine()
            detail.appendLine(DETAIL_OMITTED_MESSAGE)
        }
        return CollectedDetail(text = detail.toString(), isOmitted = isOmitted)
    }

    private fun ContentRevision.readContentForPatch(): String? {
        return try {
            content
        } catch (_: VcsException) {
            null
        }
    }

    private fun String.patchLines(): List<String> {
        val normalized = replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isEmpty()) return emptyList()
        val lines = normalized.split('\n')
        return if (normalized.endsWith('\n')) lines.dropLast(1) else lines
    }

    private fun buildUnifiedPatch(
        path: String,
        beforeExists: Boolean,
        afterExists: Boolean,
        beforeLines: List<String>,
        afterLines: List<String>,
    ): String {
        val oldPath = if (beforeExists) "a/$path" else "/dev/null"
        val newPath = if (afterExists) "b/$path" else "/dev/null"
        val operations = diffOperations(beforeLines, afterLines)
        return buildString {
            appendLine("--- $oldPath")
            appendLine("+++ $newPath")
            appendLine("@@ -1,${beforeLines.size} +1,${afterLines.size} @@")
            for (operation in operations) {
                append(operation.prefix)
                appendLine(operation.line)
            }
        }
    }

    private fun diffOperations(beforeLines: List<String>, afterLines: List<String>): List<DiffOperation> {
        val lcsLengths = Array(beforeLines.size + 1) { IntArray(afterLines.size + 1) }
        for (beforeIndex in beforeLines.indices.reversed()) {
            for (afterIndex in afterLines.indices.reversed()) {
                lcsLengths[beforeIndex][afterIndex] = if (beforeLines[beforeIndex] == afterLines[afterIndex]) {
                    lcsLengths[beforeIndex + 1][afterIndex + 1] + 1
                } else {
                    maxOf(lcsLengths[beforeIndex + 1][afterIndex], lcsLengths[beforeIndex][afterIndex + 1])
                }
            }
        }

        val operations = mutableListOf<DiffOperation>()
        var beforeIndex = 0
        var afterIndex = 0
        while (beforeIndex < beforeLines.size && afterIndex < afterLines.size) {
            when {
                beforeLines[beforeIndex] == afterLines[afterIndex] -> {
                    operations += DiffOperation(' ', beforeLines[beforeIndex])
                    beforeIndex++
                    afterIndex++
                }
                lcsLengths[beforeIndex + 1][afterIndex] >= lcsLengths[beforeIndex][afterIndex + 1] -> {
                    operations += DiffOperation('-', beforeLines[beforeIndex])
                    beforeIndex++
                }
                else -> {
                    operations += DiffOperation('+', afterLines[afterIndex])
                    afterIndex++
                }
            }
        }
        while (beforeIndex < beforeLines.size) {
            operations += DiffOperation('-', beforeLines[beforeIndex])
            beforeIndex++
        }
        while (afterIndex < afterLines.size) {
            operations += DiffOperation('+', afterLines[afterIndex])
            afterIndex++
        }
        return operations
    }

    private fun patchExclusionReason(path: String): String? {
        val normalizedPath = path.replace('\\', '/')
        val fileName = normalizedPath.substringAfterLast('/')
        val matchedPattern = patchExcludedFilePatternsProvider().firstOrNull { pattern ->
            pattern.regex.matches(fileName)
        }
        return matchedPattern?.let { "file name matches excluded patch regex '${it.pattern}'" }
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
        const val MAX_COLLECTED_CHANGE_DETAIL_CHARS = 200_000
        const val MAX_PATCH_SOURCE_CHARS = 200_000
        const val MAX_PATCH_SOURCE_LINES = 20_000
        const val DETAIL_OMITTED_MESSAGE = "<checked-change detail omitted because the collection detail budget was exhausted>"
    }

    private data class CollectedDetail(
        val text: String,
        val isOmitted: Boolean,
    )

    private data class DiffOperation(
        val prefix: Char,
        val line: String,
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
