package com.livteam.commitninja.vcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckedCommitChangesProviderTest {
    @Test
    fun `collects included commit workflow changes when action changes data is absent`() {
        val provider = CheckedCommitChangesProvider()
        val includedChange = change("src/Included.kt", "before", "after")

        val contexts = provider.collectFromSources(
            actionChanges = null,
            includedChanges = listOf(includedChange),
        )

        assertEquals(1, contexts.size)
        assertEquals("src/Included.kt", contexts.single().path)
        assertTrue(contexts.single().detail.contains("before"))
        assertTrue(contexts.single().detail.contains("after"))
    }

    @Test
    fun `prefers included commit workflow changes over broad action changes`() {
        val provider = CheckedCommitChangesProvider()
        val uncheckedActionChange = change("src/Unchecked.kt", "old", "new")
        val includedChange = change("src/Included.kt", "before", "after")

        val contexts = provider.collectFromSources(
            actionChanges = arrayOf(uncheckedActionChange),
            includedChanges = listOf(includedChange),
        )

        assertEquals(listOf("src/Included.kt"), contexts.map { it.path })
        assertEquals(
            1,
            provider.checkedChangeCountFromSources(
                actionChanges = arrayOf(uncheckedActionChange),
                includedChanges = listOf(includedChange),
            ),
        )
    }

    @Test
    fun `empty included commit workflow changes do not fall back to broad action changes`() {
        val provider = CheckedCommitChangesProvider()
        val uncheckedActionChange = change("src/Unchecked.kt", "old", "new")

        val contexts = provider.collectFromSources(
            actionChanges = arrayOf(uncheckedActionChange),
            includedChanges = emptyList(),
        )

        assertTrue(contexts.isEmpty())
        assertEquals(
            false,
            provider.hasCheckedChangesFromSources(
                actionChanges = arrayOf(uncheckedActionChange),
                includedChanges = emptyList(),
            ),
        )
        assertEquals(
            0,
            provider.checkedChangeCountFromSources(
                actionChanges = arrayOf(uncheckedActionChange),
                includedChanges = emptyList(),
            ),
        )
    }

    @Test
    fun `checked change count is absent when action and workflow data are absent`() {
        val provider = CheckedCommitChangesProvider()

        assertEquals(
            null,
            provider.checkedChangeCountFromSources(
                actionChanges = null,
                includedChanges = null,
            ),
        )
    }

    @Test
    fun `collects included unversioned commit workflow files`() {
        val provider = CheckedCommitChangesProvider()
        val unversionedFile = Files.createTempFile("commit-ninja-unversioned", ".txt")
        Files.writeString(unversionedFile, "new file content")

        val contexts = provider.collectFromSources(
            actionChanges = null,
            includedChanges = emptyList(),
            includedUnversionedFiles = listOf(TestFilePath(unversionedFile.toString())),
        )

        assertEquals(1, contexts.size)
        assertEquals(unversionedFile.toString(), contexts.single().path)
        assertEquals("NEW", contexts.single().status)
        assertTrue(contexts.single().detail.contains("new file content"))
    }

    @Test
    fun `smart document budgeting collects source changes before large document changes`() {
        val provider = CheckedCommitChangesProvider(
            smartDocumentBudgetingEnabledProvider = { true },
        )
        val largeDocumentChange = change("docs/guide.md", "", "d".repeat(199_980))
        val sourceChange = change("src/App.kt", "fun main() = Unit", "fun main() = println(\"new\")")

        val contexts = provider.collectFromSources(
            actionChanges = arrayOf(largeDocumentChange, sourceChange),
            includedChanges = null,
        )

        assertEquals(listOf("src/App.kt", "docs/guide.md"), contexts.map { it.path })
        assertTrue(contexts[0].detail.contains("println(\"new\")"))
        assertEquals(true, contexts[1].isDetailOmitted)
    }

    @Test
    fun `disabled smart document budgeting preserves selected order`() {
        val provider = CheckedCommitChangesProvider(
            smartDocumentBudgetingEnabledProvider = { false },
        )
        val largeDocumentChange = change("docs/guide.md", "", "d".repeat(199_980))
        val sourceChange = change("src/App.kt", "fun main() = Unit", "fun main() = println(\"new\")")

        val contexts = provider.collectFromSources(
            actionChanges = arrayOf(largeDocumentChange, sourceChange),
            includedChanges = null,
        )

        assertEquals(listOf("docs/guide.md", "src/App.kt"), contexts.map { it.path })
        assertEquals(true, contexts[0].isDetailOmitted)
        assertEquals(true, contexts[1].isDetailOmitted)
        assertTrue(contexts[1].detail.contains("collection detail budget was exhausted"))
    }

    private fun change(path: String, before: String, after: String): Change =
        Change(StringRevision(path, before), StringRevision(path, after))

    private class StringRevision(
        private val path: String,
        private val text: String,
    ) : ContentRevision {
        override fun getContent(): String = text

        override fun getFile(): FilePath = TestFilePath(path)

        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
    }

    private class TestFilePath(private val path: String) : FilePath {
        override fun getPath(): String = path

        override fun isDirectory(): Boolean = false

        override fun isUnder(parent: FilePath, strict: Boolean): Boolean = false

        override fun getParentPath(): FilePath? = null

        override fun getVirtualFile(): VirtualFile? = null

        override fun getVirtualFileParent(): VirtualFile? = null

        override fun getIOFile(): File = File(path)

        override fun getName(): String = path.substringAfterLast('/')

        override fun getPresentableUrl(): String = path

        override fun getCharset(): Charset = Charsets.UTF_8

        override fun getCharset(project: Project?): Charset = Charsets.UTF_8

        override fun getFileType(): FileType = PlainTextFileType.INSTANCE

        override fun isNonLocal(): Boolean = false
    }
}
