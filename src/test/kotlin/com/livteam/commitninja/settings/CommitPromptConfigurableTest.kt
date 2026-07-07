package com.livteam.commitninja.settings

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container

class CommitPromptConfigurableTest : BasePlatformTestCase() {
    fun testPromptSettingsUseMarkdownAwareEditor() {
        val configurable = CommitPromptConfigurable()

        val component = configurable.createComponent()
        val editors = descendantsOfType(component, EditorTextField::class.java)
        val textAreas = descendantsOfType(component, JBTextArea::class.java)

        assertTrue("Prompt settings should render an editor component.", editors.isNotEmpty())
        assertTrue("Prompt settings should not use a plain text area.", textAreas.isEmpty())
        val markdownFileType = FileTypeManager.getInstance().getFileTypeByExtension("md")
        val editor = editors.single()

        assertSame(markdownFileType, editor.fileType)
        assertNotSame("Markdown prompt editor must not resolve to plain text.", PlainTextFileType.INSTANCE, editor.fileType)
        assertSame("Prompt editor must be project-aware so Markdown highlighting can be installed.", project, editor.project)
        val markdownEditor = editor as? CommitPromptMarkdownEditorField
        assertNotNull("Prompt editor must use the Markdown-aware editor field.", markdownEditor)
        val createdEditor = markdownEditor!!.createEditorForInspection()
        try {
            assertNotNull(
                "Prompt editor must install a syntax highlighter on the actual editor component.",
                createdEditor.highlighter,
            )
            assertTrue(
                "Prompt editor must show line numbers.",
                createdEditor.settings.isLineNumbersShown,
            )
            assertTrue(
                "Prompt editor must keep the gutter visible for IDE editor affordances.",
                createdEditor.settings.isLineMarkerAreaShown,
            )
            assertTrue(
                "Prompt editor must show the vertical scrollbar.",
                createdEditor.scrollPane.verticalScrollBarPolicy != javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            )
            assertFalse(
                "Prompt editor must be a multiline editor, not a one-line field.",
                createdEditor.isOneLineMode,
            )
            assertSame(
                "Prompt editor must create the actual EditorEx with project context.",
                project,
                createdEditor.project,
            )
        } finally {
            EditorFactory.getInstance().releaseEditor(createdEditor)
        }

        configurable.disposeUIResources()
    }

    private fun <T> descendantsOfType(root: Component, expectedClass: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        fun visit(component: Component) {
            if (expectedClass.isInstance(component)) {
                matches += expectedClass.cast(component)
            }
            if (component is Container) {
                component.components.forEach(::visit)
            }
        }
        visit(root)
        return matches
    }
}
